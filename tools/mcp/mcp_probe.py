import http.client
import json
import threading
import time

HOST, PORT = "127.0.0.1", 8081

conn = http.client.HTTPConnection(HOST, PORT, timeout=30)
conn.request("GET", "/sse", headers={"Accept": "text/event-stream"})
resp = conn.getresponse()

endpoint = None
while True:
    line = resp.readline().decode("utf-8", "ignore").strip()
    if line.startswith("data:"):
        endpoint = line[5:].strip()
        break
print("endpoint:", endpoint)

responses = {}

def reader():
    buf = []
    while True:
        raw = resp.readline()
        if not raw:
            break
        text = raw.decode("utf-8", "ignore").rstrip("\r\n")
        if text == "":
            data = "\n".join(item[5:].strip() for item in buf if item.startswith("data:"))
            buf = []
            if data:
                try:
                    obj = json.loads(data)
                    responses[obj.get("id")] = obj
                except Exception:
                    pass
        else:
            buf.append(text)

threading.Thread(target=reader, daemon=True).start()

def post(payload):
    c = http.client.HTTPConnection(HOST, PORT, timeout=15)
    c.request("POST", endpoint, body=json.dumps(payload),
              headers={"Content-Type": "application/json"})
    r = c.getresponse()
    r.read()
    c.close()
    return r.status

print("initialize:", post({
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {"protocolVersion": "2024-11-05", "capabilities": {},
               "clientInfo": {"name": "zhimi-test", "version": "1.0"}}
}))
time.sleep(1.2)
post({"jsonrpc": "2.0", "method": "notifications/initialized"})
time.sleep(0.5)
print("tools/list:", post({"jsonrpc": "2.0", "id": 2, "method": "tools/list"}))

for _ in range(30):
    if 2 in responses:
        break
    time.sleep(0.3)

result = responses.get(2, {})
tools = result.get("result", {}).get("tools", [])
print("tool count:", len(tools))
for tool in tools:
    print(" -", tool["name"], "|", (tool.get("description") or "")[:36])

if 1 in responses:
    info = responses[1].get("result", {}).get("serverInfo", {})
    print("server:", info)

print("tools/call:", post({
    "jsonrpc": "2.0", "id": 3, "method": "tools/call",
    "params": {"name": "get_shop_detail", "arguments": {"shopId": 1}}
}))
for _ in range(30):
    if 3 in responses:
        break
    time.sleep(0.3)
content = responses.get(3, {}).get("result", {}).get("content", [])
for item in content:
    print("call result:", (item.get("text") or "")[:180])
