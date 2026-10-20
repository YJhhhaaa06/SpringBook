# -*- coding: utf-8 -*-
"""为前端验收准备一批接近真实的演示数据（幂等：已存在则跳过）。

- 7 本书，其中《算法导论》只剩 1 册且被借走，供预约排队演示；
- 读者：张三（reader01，自助注册）、李四（逾期归还 -> 冻结 + 待缴罚款）、王五（在借）；
- 借阅：张三借 Effective Java；李四借《人类简史》后逾期 10 天归还；王五借走《算法导论》；
- 预约：张三排队等《算法导论》。

前置：应用运行在 127.0.0.1:8080，MySQL 容器 springbook-mysql 可用。
登录口令：reader01 / pass123456（读者端演示账号）。
"""
import base64
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SESSIONS = ROOT / "temp-script" / "ui_sessions.json"
BASE = "http://127.0.0.1:8080"
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

BOOKS = [
    ("978-7-111-11111-1", "深入理解计算机系统（原书第3版）", 3, 13900),
    ("978-7-111-22222-2", "Effective Java（第3版）", 2, 8900),
    ("978-7-111-33333-3", "算法导论（原书第3版）", 1, 12800),
    ("978-7-101-44444-4", "万历十五年", 2, 3200),
    ("978-7-508-55555-5", "人类简史：从动物到上帝", 1, 6800),
    ("978-7-115-66666-6", "编程珠玑（第2版）", 1, 4500),
    ("978-7-121-77777-7", "设计数据密集型应用", 2, 9900),
]


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    try:
        with OPENER.open(req, data=data, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode("utf-8"))


def sql(statement):
    subprocess.run(
        ["docker", "exec", "springbook-mysql", "mysql", "-uspringbook", "-pspringbook",
         "springbook", "-e", statement],
        capture_output=True, text=True, check=True)


def claims(token):
    seg = token.split(".")[1]
    seg += "=" * (-len(seg) % 4)
    return json.loads(base64.urlsafe_b64decode(seg))


def ensure_book(token, isbn, title, total, price):
    found = call("GET", "/api/books?keyword=%s&page=1&size=20" % isbn, token=token)
    for record in found["data"]["records"]:
        if record["isbn"] == isbn:
            return record
    created = call("POST", "/api/books",
                   {"isbn": isbn, "title": title, "totalCopies": total, "priceCents": price}, token)
    print("  + 图书 %s" % title)
    return created["data"]


def ensure_reader(token, name, phone):
    found = call("GET", "/api/readers?keyword=%s&page=1&size=20" % phone, token=token)
    for record in found["data"]["records"]:
        if record["phone"] == phone:
            return record
    created = call("POST", "/api/readers", {"name": name, "phone": phone}, token)
    print("  + 读者 %s" % name)
    return created["data"]


def open_loans(token, reader_id):
    records = call("GET", "/api/loans?readerId=%s" % reader_id, token=token)["data"]
    return [r for r in records if r["status"] == "BORROWED"]


def main():
    admin = call("POST", "/api/auth/login", {"username": "admin", "password": "admin123"})
    if admin.get("code") != 0:
        print("管理员登录失败：", admin)
        return 1
    token = admin["data"]["token"]

    # 1. 读者账号：reader01 / pass123456（自助注册，若已存在则登录）
    reg = call("POST", "/api/auth/register", {
        "username": "reader01", "password": "pass123456", "name": "张三", "phone": "13800001111",
    })
    if reg.get("code") == 0:
        reader_token = reg["data"]["token"]
        print("  + 读者账号 reader01")
    else:
        reader_token = call("POST", "/api/auth/login",
                            {"username": "reader01", "password": "pass123456"})["data"]["token"]
    reader_id = claims(reader_token).get("readerId")

    # 2. 图书
    books = {isbn: ensure_book(token, isbn, title, total, price) for isbn, title, total, price in BOOKS}
    java = books["978-7-111-22222-2"]
    algo = books["978-7-111-33333-3"]
    history = books["978-7-508-55555-5"]

    # 3. 其余读者
    lisi = ensure_reader(token, "李四", "13900002222")
    wangwu = ensure_reader(token, "王五", "13700003333")

    # 4. 张三借 Effective Java
    if not any(r["bookId"] == java["id"] for r in open_loans(token, reader_id)):
        result = call("POST", "/api/loans", {"bookId": java["id"], "readerId": reader_id}, token)
        print("  + 张三借出《%s》 -> %s" % (java["title"], result["code"]))

    # 5. 王五借走最后一本《算法导论》
    if not any(r["bookId"] == algo["id"] for r in open_loans(token, wangwu["id"])):
        result = call("POST", "/api/loans", {"bookId": algo["id"], "readerId": wangwu["id"]}, token)
        print("  + 王五借出《%s》 -> %s" % (algo["title"], result["code"]))

    # 6. 张三排队预约《算法导论》
    queue = call("GET", "/api/books/%s/reservations" % algo["id"], token=token)["data"]
    if not any(r["readerId"] == reader_id and r["status"] in ("WAITING", "READY") for r in queue):
        result = call("POST", "/api/reservations",
                      {"bookId": algo["id"], "readerId": reader_id}, token)
        print("  + 张三预约《%s》 -> %s" % (algo["title"], result["code"]))

    # 7. 李四：借《人类简史》-> 逾期 10 天 -> 归还（罚款 5.00，自动冻结）
    loans = open_loans(token, lisi["id"])
    target = next((r for r in loans if r["bookId"] == history["id"]), None)
    if target:
        sql("UPDATE loans SET due_at = DATE_SUB(NOW(), INTERVAL 10 DAY) WHERE id = %s" % target["id"])
        result = call("POST", "/api/loans/%s/return" % target["id"], token=token)
        print("  + 李四逾期归还 -> 逾期 %s 天，罚款 %s 分"
              % (result["data"]["overdueDays"], result["data"]["fineCents"]))
    elif not any(r["bookId"] == history["id"] for r in
                 call("GET", "/api/loans?readerId=%s" % lisi["id"], token=token)["data"]):
        created = call("POST", "/api/loans", {"bookId": history["id"], "readerId": lisi["id"]}, token)
        if created.get("code") == 0:
            loan_id = created["data"]["id"]
            sql("UPDATE loans SET due_at = DATE_SUB(NOW(), INTERVAL 10 DAY) WHERE id = %s" % loan_id)
            result = call("POST", "/api/loans/%s/return" % loan_id, token=token)
            if result.get("code") == 0:
                print("  + 李四逾期归还 -> 逾期 %s 天，罚款 %s 分"
                      % (result["data"]["overdueDays"], result["data"]["fineCents"]))

    detail = call("GET", "/api/readers/%s" % lisi["id"], token=token)["data"]
    print("  = 李四状态 %s，待缴 %s 分" % (detail["status"], detail["unpaidFineCents"]))
    print("  = reader01 的 readerId = %s" % reader_id)

    # 8. 供 UI 检查脚本复用：写一份会话文件
    time.sleep(0.2)
    SESSIONS.parent.mkdir(parents=True, exist_ok=True)
    with open(SESSIONS, "w", encoding="utf-8") as handle:
        json.dump({"admin": token, "reader": reader_token, "readerId": reader_id}, handle)
    print("演示数据就绪")
    return 0


if __name__ == "__main__":
    sys.exit(main())
