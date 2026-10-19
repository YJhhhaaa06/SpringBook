# -*- coding: utf-8 -*-
"""冒烟链路（第二版）：
第一版链路：登录 -> 建书/建读者 -> 借 -> 预约 -> 还 -> 队首 READY -> 履约借出 -> 逾期罚款封顶。
第二版追加：逾期自动冻结 -> 未缴清禁借 -> 缴纳罚款自动解冻 -> 自助注册 -> 数据归属 403
-> 借阅上限 -> 入库/下架 -> 修改密码。
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

# =====================================================================
# 第二版追加链路
# =====================================================================

# 12. 乙逾期 400 天归还后应被自动冻结，罚款待缴
st, r = call("GET", "/api/readers/%s" % rid_b, None, token)
check("17 overdue return auto-freeze", r["code"] == 0 and r["data"]["status"] == "FROZEN")
check("18 unpaid fine summary", r["data"]["unpaidFineCents"] == 1000 and r["data"]["unpaidFine"] == 10.0,
      json.dumps(r["data"], ensure_ascii=False))

st, r = call("GET", "/api/loans?readerId=%s&status=RETURNED" % rid_b, None, token)
check("19 loan fineStatus UNPAID", r["data"][0]["fineStatus"] == "UNPAID")

# 13. 冻结读者借书被拒
st, r = call("POST", "/api/books",
             {"isbn": "SMOKE-V2-" + TS, "title": "冒烟二版书", "totalCopies": 2, "priceCents": 500}, token)
bid2 = r["data"]["id"]
st, r = call("POST", "/api/loans", {"bookId": bid2, "readerId": rid_b}, token)
check("20 frozen borrow -> 40905", r["code"] == 40905, json.dumps(r, ensure_ascii=False))

# 14. 缴纳罚款 -> PAID -> 自动解冻 -> 可再借
st, r = call("POST", "/api/loans/%s/fine/pay" % loan_b, None, token)
check("21 pay fine -> PAID", r["code"] == 0 and r["data"]["fineStatus"] == "PAID",
      json.dumps(r, ensure_ascii=False))
st, r = call("GET", "/api/readers/%s" % rid_b, None, token)
check("22 auto unfreeze after paid", r["data"]["status"] == "ACTIVE")
st, r = call("POST", "/api/loans", {"bookId": bid2, "readerId": rid_b}, token)
check("23 borrow after settle ok", r["code"] == 0)

# 15. 自助注册：即发 token，且归属校验生效（READER 不能替他人借）
reg_user = "smoke_u_" + TS
reg_phone = "137" + TS[-8:]  # 每轮唯一，避免残留数据导致 keyword 查询歧义
st, r = call("POST", "/api/auth/register",
             {"username": reg_user, "password": "smoke-123456",
              "name": "冒烟丙", "phone": reg_phone})
check("24 register issues token", st == 200 and r["code"] == 0
      and bool(r["data"].get("token")), json.dumps(r, ensure_ascii=False))
reg_token = r["data"]["token"]
st, r = call("GET", "/api/readers?keyword=" + reg_phone, None, token)
rid_c = r["data"]["records"][0]["id"]

st, r = call("POST", "/api/loans", {"bookId": bid2, "readerId": rid_a}, reg_token)
check("25 reader borrow for others -> 40300", r["code"] == 40300, json.dumps(r, ensure_ascii=False))

# 16. 借阅上限（默认 5）：丙从 0 借到 5 本，第 6 次拒
st, r = call("POST", "/api/books",
             {"isbn": "SMOKE-LIM-" + TS, "title": "冒烟上限书", "totalCopies": 6, "priceCents": 100}, token)
bid3 = r["data"]["id"]
for i in range(5):
    st, r = call("POST", "/api/loans", {"bookId": bid3, "readerId": rid_c}, reg_token)
    if r["code"] != 0:
        break
st, r = call("POST", "/api/loans", {"bookId": bid3, "readerId": rid_c}, reg_token)
limit_hit = (r["code"] == 40913)
check("26 borrow limit -> 40913", limit_hit, json.dumps(r, ensure_ascii=False))

# 17. 入库：总册数 6 -> 8（丙在借 5），可借数应为 3
st, r = call("PATCH", "/api/books/%s/copies" % bid3, {"totalCopies": 8}, token)
check("27 adjust copies up", r["code"] == 0 and r["data"]["availableCopies"] == 3,
      json.dumps(r, ensure_ascii=False))

# 18. 修改密码：改后旧口令失效、新口令可登录
st, r = call("PUT", "/api/auth/password",
             {"oldPassword": "smoke-123456", "newPassword": "smoke-654321"}, reg_token)
check("28 change password", r["code"] == 0)
st, r = call("POST", "/api/auth/login", {"username": reg_user, "password": "old-pass-wrong"})
check("29 old password -> 40101", r["code"] == 40101)
st, r = call("POST", "/api/auth/login", {"username": reg_user, "password": "smoke-654321"})
check("30 new password login ok", r["code"] == 0)

print()
print("SMOKE %s: %d/%d passed" % ("PASSED" if all(results) else "FAILED", sum(results), len(results)))
sys.exit(0 if all(results) else 1)
