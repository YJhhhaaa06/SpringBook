# -*- coding: utf-8 -*-
"""第一版冒烟链路：登录 -> 建书/建读者 -> 借 -> 预约 -> 还 -> 队首 READY -> 履约借出 -> 逾期罚款封顶。
前置：MySQL 容器(springbook-mysql) healthy、应用运行在 127.0.0.1:8080（JWT_SECRET 已注入）。
"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))  # 绕过本机代理
TS = str(int(time.time()))

results = []


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    try:
        with OPENER.open(req, data=data, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def check(name, cond, detail=""):
    results.append(cond)
    print(("PASS " if cond else "FAIL ") + name + ((" | " + detail) if detail else ""))


def db(sql):
    subprocess.run(
        ["docker", "exec", "springbook-mysql", "mysql", "-uspringbook", "-pspringbook", "springbook", "-e", sql],
        capture_output=True, text=True, check=True)


# 1. 登录
st, r = call("POST", "/api/auth/login", {"username": "admin", "password": "admin123"})
token = r.get("data", {}).get("token", "")
check("01 login ok", st == 200 and r["code"] == 0)

# 2. 错误口令
st, r = call("POST", "/api/auth/login", {"username": "admin", "password": "bad-password"})
check("02 wrong password -> 40101", st == 401 and r["code"] == 40101, json.dumps(r, ensure_ascii=False))

# 3. 匿名访问
st, r = call("GET", "/api/books")
check("03 anonymous -> 40100", st == 401 and r["code"] == 40100, json.dumps(r, ensure_ascii=False))

# 4. 建书（单册）
st, r = call("POST", "/api/books",
             {"isbn": "SMOKE-" + TS, "title": "冒烟测试书", "totalCopies": 1, "priceCents": 1000}, token)
book = r["data"]
bid = book["id"]
check("04 create book", st == 200 and r["code"] == 0 and book["availableCopies"] == 1
      and book["price"] == 10.0, "id=%s" % bid)

# 5. 建读者甲、乙
st, r = call("POST", "/api/readers", {"name": "冒烟甲", "phone": "139" + TS[-8:]}, token)
rid_a = r["data"]["id"]
st2, r2 = call("POST", "/api/readers", {"name": "冒烟乙", "phone": "138" + TS[-8:]}, token)
rid_b = r2["data"]["id"]
check("05 create readers", r["code"] == 0 and r2["code"] == 0)

# 6. 甲借走最后一本
st, r = call("POST", "/api/loans", {"bookId": bid, "readerId": rid_a}, token)
loan_a = r["data"]["id"]
check("06 borrow by A", r["code"] == 0 and r["data"]["status"] == "BORROWED", "loan=%s" % loan_a)
st, r = call("GET", "/api/books/%s" % bid, None, token)
check("07 stock now 0", r["data"]["availableCopies"] == 0)

# 7. 乙预约（库存为 0 才允许）
st, r = call("POST", "/api/reservations", {"bookId": bid, "readerId": rid_b}, token)
check("08 reserve by B -> WAITING", r["code"] == 0 and r["data"]["status"] == "WAITING", json.dumps(r, ensure_ascii=False))

# 8. 甲归还 -> 乙变 READY 且占用名额
st, r = call("POST", "/api/loans/%s/return" % loan_a, None, token)
check("09 return by A, fine=0", r["code"] == 0 and r["data"]["fineCents"] == 0, json.dumps(r, ensure_ascii=False))
st, r = call("GET", "/api/books/%s/reservations" % bid, None, token)
queue = r["data"]
check("10 queue head READY", queue[0]["status"] == "READY" and queue[0]["readerId"] == rid_b)
st, r = call("GET", "/api/books/%s" % bid, None, token)
check("11 stock held 0 by READY", r["data"]["availableCopies"] == 0)

# 9. 乙履约借出（不扣库存）
st, r = call("POST", "/api/loans", {"bookId": bid, "readerId": rid_b}, token)
loan_b = r["data"]["id"]
check("12 fulfill by B", r["code"] == 0, "loan=%s" % loan_b)
st, r = call("GET", "/api/books/%s/reservations" % bid, None, token)
check("13 reservation FULFILLED", r["data"][0]["status"] == "FULFILLED")

# 10. 逾期 400 天后归还 -> 向上取整 400 天 × 50 分 = 20000 分，封顶书价 1000 分
db("UPDATE loans SET due_at = DATE_SUB(NOW(3), INTERVAL 400 DAY) WHERE id = %s" % loan_b)
st, r = call("POST", "/api/loans/%s/return" % loan_b, None, token)
check("14 overdue fine capped at price", r["code"] == 0 and r["data"]["overdueDays"] == 400
      and r["data"]["fineCents"] == 1000 and r["data"]["fine"] == 10.0, json.dumps(r, ensure_ascii=False))
st, r = call("GET", "/api/books/%s" % bid, None, token)
check("15 stock restored to 1", r["data"]["availableCopies"] == 1)

# 11. 借阅记录查询
st, r = call("GET", "/api/loans?readerId=%s&status=RETURNED" % rid_b, None, token)
check("16 loan history query", r["code"] == 0 and len(r["data"]) == 1)

print()
print("SMOKE %s: %d/%d passed" % ("PASSED" if all(results) else "FAILED", sum(results), len(results)))
sys.exit(0 if all(results) else 1)
