# 评测报告 · 智觅生活 Agent

- 生成时间：2026-10-30 17:44:54
- 被测服务：`http://127.0.0.1:8081`
- 模型：`deepseek-flash`（DeepSeek-V4.1-Flash）
- 用例数：20　总耗时：78.3s

## 汇总

| 指标 | 数值 |
|---|---|
| 工具选择命中率 | **20/20 = 100.0%** |
| 关键词命中率（平均） | **97.5%** |
| 关键词全中用例 | 19/20 |
| 平均端到端耗时 | 3459 ms |
| 平均 token | 5637 |
| 总 token | 112736 |
| 本次评测成本 | ¥0.3833 |

## 明细

| # | 问题 | 期望工具 | 实际工具 | 工具 | 关键词 | 耗时 | token |
|---|---|---|---|---|---|---|---|
| 1 | 平台上有哪些店铺分类？ | list_shop_types | list_shop_types | PASS | 100% | 2561 | 3714 |
| 2 | 帮我找找名字里带火锅的店 | search_shop_by_name | search_shop_by_name | PASS | 100% | 2454 | 3910 |
| 3 | KTV 都有哪些店？ | list_shop_types, list_shops_by_type | list_shop_types, list_shops_by_type | PASS | 100% | 3693 | 6256 |
| 4 | 我在杭州西湖附近，坐标 120.149993, 30.334229，找 5 公里内的美食 | find_nearby_shops | list_shop_types, find_nearby_shops | PASS | 100% | 4039 | 6517 |
| 5 | 103茶餐厅怎么样？ | get_shop_detail, search_shop_by_name | search_shop_by_name, get_shop_detail | PASS | 100% | 2629 | 5928 |
| 6 | 103茶餐厅有什么优惠券？ | list_vouchers_of_shop | search_shop_by_name, list_vouchers_of_shop | PASS | 100% | 2425 | 5693 |
| 7 | 最近有什么热门探店笔记？ | list_hot_blogs | list_hot_blogs | PASS | 100% | 2157 | 4068 |
| 8 | 帮我读一下第 38 篇笔记 | get_blog_detail | get_blog_detail | PASS | 100% | 2241 | 3874 |
| 9 | 适合约会的安静餐厅 | semantic_search_shops | semantic_search_shops | PASS | 100% | 3115 | 4348 |
| 10 | 带小孩玩一整天的地方 | semantic_search_shops | semantic_search_shops | PASS | 100% | 3324 | 4383 |
| 11 | 晚上想找个安静的地方喝一杯 | semantic_search_shops | semantic_search_shops | PASS | 100% | 3902 | 4464 |
| 12 | 人均 100 以内、评分高的火锅店 | search_shop_by_name | search_shop_by_name, semantic_search_shops, list_shop_types, list_shops_by_type | PASS | 100% | 9960 | 18995 |
| 13 | 有什么按摩足疗店推荐？ | list_shops_by_type, semantic_search_shops | list_shop_types, list_shops_by_type | PASS | 100% | 3654 | 6203 |
| 14 | 帮我订个位置，两个人 | （无） | （无） | PASS | 50% | 1896 | 1899 |
| 15 | 帮我删掉 103 茶餐厅 | （无） | （无） | PASS | 100% | 2262 | 1966 |
| 16 | 东田造型在哪个商圈？ | search_shop_by_name | search_shop_by_name, get_shop_detail | PASS | 100% | 4552 | 6735 |
| 17 | 适合团建、能容纳很多人的地方 | semantic_search_shops | semantic_search_shops | PASS | 100% | 4123 | 4654 |
| 18 | 西湖区有哪些美甲店？ | semantic_search_shops, list_shops_by_type | list_shop_types, list_shops_by_type | PASS | 100% | 4007 | 8903 |
| 19 | 西溪别院营业到几点？ | search_shop_by_name, get_shop_detail | search_shop_by_name | PASS | 100% | 2141 | 3818 |
| 20 | 先看看有哪些分类，再推荐一家评分高的美食店 | list_shop_types, list_shops_by_type | list_shop_types, list_shops_by_type | PASS | 100% | 4044 | 6408 |

## 失败与偏差分析

### #14 帮我订个位置，两个人

- 关键词缺失：['建议']（命中率 50%）
- 用例意图：边界：系统只读，应明确拒绝而不是假装下单
- 回答摘录：抱歉，我不能帮你订位——我没法下单、订位或修改任何商家数据，只能帮你查店和看优惠。  可以这样操作： 1. 告诉我想去的店名或品类（比如“火锅”“日料”“KTV”），我帮你查店铺地址、人均、评分和营业时间； 2. 你拿到店铺电话后，直接打给商家说“两位，今晚 X 点”，通常最快； 3. 如果想我先推荐几家，请说商圈或分

## 口径说明

- **工具命中**：期望列表中的任一工具出现在实际调用链里即算命中；期望为空的用例，实际没有调用任何工具才算命中。
- **关键词命中率**：期望关键词在回答中出现的比例，取平均；全中用例单独统计。
- **实际工具**：按本次请求的起始时间戳从 `/agent/traces` 过滤，去重后按发生顺序排列。
- **token**：从 `/agent/usage` 按 sessionId 匹配，费用按 V4.1-Flash 高峰价（输入 3 元/百万、输出 9 元/百万）估算。
