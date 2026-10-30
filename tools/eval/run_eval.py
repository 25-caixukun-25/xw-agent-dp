"""智觅生活 Agent 评测脚本。

用法：
    python tools/eval/run_eval.py
    python tools/eval/run_eval.py --base http://127.0.0.1:8081 --limit 20

流程：逐条调 POST /agent/chat/sync → 轮询 GET /agent/traces 取本次实际工具 →
从 GET /agent/usage 取本次 token → 汇总成 markdown 报告。
"""

import argparse
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
EVALSET = os.path.join(ROOT, "server", "src", "test", "resources", "evalset.json")
REPORT = os.path.join(ROOT, "docs", "eval-report.md")

INPUT_PRICE_PER_MILLION = 3.0
OUTPUT_PRICE_PER_MILLION = 9.0


def post_json(base, path, payload, timeout=120):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        base + path, data=data, headers={"Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_json(base, path, timeout=30):
    req = urllib.request.Request(base + path, method="GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def normalize(text):
    return (text or "").replace(" ", "").replace("\n", "").lower()


def keyword_ratio(answer, keywords):
    """expectedKeywords 的每一项可以是字符串，也可以是同义词列表（命中任一即可）。"""
    if not keywords:
        return 1.0
    body = normalize(answer)
    hit = 0
    for keyword in keywords:
        options = keyword if isinstance(keyword, list) else [keyword]
        if any(normalize(option) in body for option in options):
            hit += 1
    return hit / len(keywords)


def missing_keywords(answer, keywords):
    body = normalize(answer)
    missing = []
    for keyword in keywords:
        options = keyword if isinstance(keyword, list) else [keyword]
        if not any(normalize(option) in body for option in options):
            missing.append(" / ".join(options))
    return missing


def collect_tools(base, since_ts):
    data = get_json(base, "/agent/traces?limit=100").get("data") or []
    tools = []
    for item in sorted(data, key=lambda x: x.get("ts", 0)):
        if item.get("ts", 0) >= since_ts:
            name = item.get("tool")
            if name and name not in tools:
                tools.append(name)
    return tools


def find_usage(base, session_id):
    data = get_json(base, "/agent/usage?limit=50").get("data") or {}
    for item in data.get("recent") or []:
        if item.get("sessionId") == session_id:
            return int(item.get("promptTokens") or 0), int(item.get("completionTokens") or 0)
    return 0, 0


def run_case(base, case, run_id):
    session_id = f"eval-{run_id}-{case['id']}"
    started = time.time()
    since = int(started * 1000) - 20
    result = {
        "id": case["id"],
        "question": case["question"],
        "expectedTools": case.get("expectedTools") or [],
        "expectedKeywords": case.get("expectedKeywords") or [],
        "note": case.get("note", ""),
        "actualTools": [],
        "answer": "",
        "toolHit": False,
        "keywordRatio": 0.0,
        "latencyMs": 0,
        "promptTokens": 0,
        "completionTokens": 0,
        "error": "",
    }
    try:
        response = post_json(base, "/agent/chat/sync", {"sessionId": session_id, "message": case["question"]})
        result["latencyMs"] = int((time.time() - started) * 1000)
        if not response.get("success"):
            result["error"] = response.get("errorMsg") or "接口返回失败"
        else:
            result["answer"] = ((response.get("data") or {}).get("answer") or "").strip()
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        result["latencyMs"] = int((time.time() - started) * 1000)
        result["error"] = f"请求异常: {exc}"

    time.sleep(0.4)
    try:
        result["actualTools"] = collect_tools(base, since)
        prompt, completion = find_usage(base, session_id)
        result["promptTokens"] = prompt
        result["completionTokens"] = completion
    except (urllib.error.URLError, TimeoutError, OSError):
        pass

    expected = result["expectedTools"]
    if expected:
        result["toolHit"] = any(tool in expected for tool in result["actualTools"])
    else:
        result["toolHit"] = len(result["actualTools"]) == 0
    result["keywordRatio"] = keyword_ratio(result["answer"], result["expectedKeywords"])
    return result


def build_report(results, elapsed, base):
    total = len(results)
    tool_hits = sum(1 for item in results if item["toolHit"])
    keyword_avg = sum(item["keywordRatio"] for item in results) / total if total else 0
    keyword_full = sum(1 for item in results if item["keywordRatio"] >= 1.0)
    avg_latency = sum(item["latencyMs"] for item in results) / total if total else 0
    total_tokens = sum(item["promptTokens"] + item["completionTokens"] for item in results)
    avg_tokens = total_tokens / total if total else 0
    cost = sum(
        item["promptTokens"] / 1_000_000 * INPUT_PRICE_PER_MILLION
        + item["completionTokens"] / 1_000_000 * OUTPUT_PRICE_PER_MILLION
        for item in results
    )

    lines = []
    lines.append("# 评测报告 · 智觅生活 Agent")
    lines.append("")
    lines.append(f"- 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- 被测服务：`{base}`")
    lines.append("- 模型：`deepseek-flash`（DeepSeek-V4.1-Flash）")
    lines.append(f"- 用例数：{total}　总耗时：{elapsed:.1f}s")
    lines.append("")
    lines.append("## 汇总")
    lines.append("")
    lines.append("| 指标 | 数值 |")
    lines.append("|---|---|")
    lines.append(f"| 工具选择命中率 | **{tool_hits}/{total} = {tool_hits / total:.1%}** |")
    lines.append(f"| 关键词命中率（平均） | **{keyword_avg:.1%}** |")
    lines.append(f"| 关键词全中用例 | {keyword_full}/{total} |")
    lines.append(f"| 平均端到端耗时 | {avg_latency:.0f} ms |")
    lines.append(f"| 平均 token | {avg_tokens:.0f} |")
    lines.append(f"| 总 token | {total_tokens} |")
    lines.append(f"| 本次评测成本 | ¥{cost:.4f} |")
    lines.append("")
    lines.append("## 明细")
    lines.append("")
    lines.append("| # | 问题 | 期望工具 | 实际工具 | 工具 | 关键词 | 耗时 | token |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for item in results:
        expected = ", ".join(item["expectedTools"]) or "（无）"
        actual = ", ".join(item["actualTools"]) or "（无）"
        tool_mark = "PASS" if item["toolHit"] else "FAIL"
        token_sum = item["promptTokens"] + item["completionTokens"]
        lines.append(
            f"| {item['id']} | {item['question']} | {expected} | {actual} | {tool_mark} "
            f"| {item['keywordRatio']:.0%} | {item['latencyMs']} | {token_sum} |"
        )
    lines.append("")

    failures = [item for item in results if not item["toolHit"] or item["keywordRatio"] < 1.0]
    lines.append("## 失败与偏差分析")
    lines.append("")
    if not failures:
        lines.append("全部用例通过。")
    else:
        for item in failures:
            lines.append(f"### #{item['id']} {item['question']}")
            lines.append("")
            if not item["toolHit"]:
                lines.append(f"- 工具未命中：期望 `{item['expectedTools']}`，实际 `{item['actualTools']}`")
            if item["keywordRatio"] < 1.0:
                missing = missing_keywords(item["answer"], item["expectedKeywords"])
                lines.append(f"- 关键词缺失：{missing}（命中率 {item['keywordRatio']:.0%}）")
            if item.get("note"):
                lines.append(f"- 用例意图：{item['note']}")
            answer = (item["answer"] or item["error"] or "").replace("\n", " ")
            lines.append(f"- 回答摘录：{answer[:160]}")
            lines.append("")

    lines.append("## 口径说明")
    lines.append("")
    lines.append("- **工具命中**：期望列表中的任一工具出现在实际调用链里即算命中；期望为空的用例，实际没有调用任何工具才算命中。")
    lines.append("- **关键词命中率**：期望关键词在回答中出现的比例，取平均；全中用例单独统计。")
    lines.append("- **实际工具**：按本次请求的起始时间戳从 `/agent/traces` 过滤，去重后按发生顺序排列。")
    lines.append("- **token**：从 `/agent/usage` 按 sessionId 匹配，费用按 V4.1-Flash 高峰价（输入 3 元/百万、输出 9 元/百万）估算。")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("ZHIMI_BASE", "http://127.0.0.1:8081"))
    parser.add_argument("--limit", type=int, default=0, help="只跑前 N 条，0 表示全部")
    args = parser.parse_args()

    with open(EVALSET, "r", encoding="utf-8") as handle:
        cases = json.load(handle)["cases"]
    if args.limit > 0:
        cases = cases[: args.limit]

    run_id = str(int(time.time()))
    results = []
    started = time.time()
    for index, case in enumerate(cases, start=1):
        print(f"[{index}/{len(cases)}] #{case['id']} {case['question']}")
        result = run_case(args.base, case, run_id)
        mark = "PASS" if result["toolHit"] else "FAIL"
        print(
            f"    tools={result['actualTools']} tool={mark} "
            f"kw={result['keywordRatio']:.0%} {result['latencyMs']}ms "
            f"tokens={result['promptTokens'] + result['completionTokens']}"
        )
        results.append(result)

    elapsed = time.time() - started
    report = build_report(results, elapsed, args.base)
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    with open(REPORT, "w", encoding="utf-8") as handle:
        handle.write(report)

    total = len(results)
    hits = sum(1 for item in results if item["toolHit"])
    keyword_avg = sum(item["keywordRatio"] for item in results) / total if total else 0
    print("")
    print(f"工具命中率 {hits}/{total} = {hits / total:.1%}")
    print(f"关键词命中率 {keyword_avg:.1%}")
    print(f"报告已写入 {REPORT}")


if __name__ == "__main__":
    main()
