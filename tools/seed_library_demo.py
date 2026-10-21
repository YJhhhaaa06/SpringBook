# -*- coding: utf-8 -*-
"""为演示环境灌入「水浒好汉读者 + 中外文学/理工科图书」的种子数据。

设计目标
--------
1. 清掉历次冒烟测试残留（SMOKE-* 图书、冒烟甲/乙/丙 读者、smoke_u_* 账号及其借贷/预约记录）；
2. 读者走 **HTTP 自助注册**（保证 BCrypt 加盐散列，口令统一 123456），用户名取好汉绰号；
3. 图书走 **后台 ADMIN 接口** 新增（保证书名联想索引同步，不用重启应用）；
4. 少量借阅/预约，让热门榜、预约排队、逾期罚款这些页面有东西可看；
5. 幂等：脚本可重复执行，已存在的账号/图书/在借记录会跳过。

前置：应用运行在 127.0.0.1:8080，MySQL 容器 springbook-mysql 可用，admin 口令 admin123。
"""
import json
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8080"
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

READER_PASSWORD = "123456"
ADMIN_PASSWORD = "admin123"

# (登录名, 姓名, 手机号)
# 登录名默认用绰号；「大刀」「双鞭」「行者」「浪子」只有两字，注册接口要求 ≥3 字符，
# 故这四位用「绰号+姓名」作为登录名（姓名列仍是本名）。
HEROES = [
    ("及时雨", "宋江", "13911110001"),
    ("玉麒麟", "卢俊义", "13911110002"),
    ("智多星", "吴用", "13911110003"),
    ("入云龙", "公孙胜", "13911110004"),
    ("大刀关胜", "关胜", "13911110005"),
    ("豹子头", "林冲", "13911110006"),
    ("霹雳火", "秦明", "13911110007"),
    ("双鞭呼延灼", "呼延灼", "13911110008"),
    ("小李广", "花荣", "13911110009"),
    ("小旋风", "柴进", "13911110010"),
    ("扑天雕", "李应", "13911110011"),
    ("美髯公", "朱仝", "13911110012"),
    ("花和尚", "鲁智深", "13911110013"),
    ("行者武松", "武松", "13911110014"),
    ("双枪将", "董平", "13911110015"),
    ("没羽箭", "张清", "13911110016"),
    ("青面兽", "杨志", "13911110017"),
    ("金枪手", "徐宁", "13911110018"),
    ("急先锋", "索超", "13911110019"),
    ("神行太保", "戴宗", "13911110020"),
    ("赤发鬼", "刘唐", "13911110021"),
    ("黑旋风", "李逵", "13911110022"),
    ("混江龙", "李俊", "13911110023"),
    ("立地太岁", "阮小二", "13911110024"),
    ("浪子燕青", "燕青", "13911110025"),
]

# (isbn, 书名, 总册数, 价格分)
BOOKS = [
    # 中国文学
    ("978-7-02-000220-7", "红楼梦", 5, 5980),
    ("978-7-02-000873-5", "三国演义", 4, 4980),
    ("978-7-02-000874-2", "西游记", 4, 5580),
    ("978-7-02-000875-9", "水浒传", 4, 5580),
    ("978-7-02-001526-9", "呐喊", 2, 2800),
    ("978-7-02-004123-7", "边城", 2, 2600),
    ("978-7-02-002552-7", "平凡的世界", 6, 10800),
    ("978-7-5063-6168-8", "活着", 3, 3500),
    ("978-7-5442-5455-4", "围城", 3, 3900),
    # 外国文学
    ("978-7-5442-6059-9", "百年孤独", 3, 5500),
    ("978-7-5327-7453-9", "战争与和平", 4, 9800),
    ("978-7-5327-7462-1", "安娜·卡列尼娜", 3, 7800),
    ("978-7-5327-7000-5", "罪与罚", 3, 6800),
    ("978-7-02-015311-4", "老人与海", 3, 2500),
    ("978-7-5442-6025-4", "霍乱时期的爱情", 2, 4900),
    ("978-7-5594-1975-3", "局外人", 2, 2900),
    ("978-7-5327-8080-6", "悲惨世界", 4, 12800),
    ("978-7-02-014437-0", "傲慢与偏见", 3, 3600),
    ("978-7-5442-5426-4", "追风筝的人", 3, 3900),
    # 科幻
    ("978-7-5366-9293-0", "三体", 1, 2300),
    ("978-7-5366-9292-3", "三体Ⅱ·黑暗森林", 1, 3200),
    ("978-7-5366-9291-6", "三体Ⅲ·死神永生", 1, 3800),
    # 社科
    ("978-7-5086-7453-1", "枪炮、病菌与钢铁", 2, 5600),
    ("978-7-301-25520-9", "中国哲学简史", 2, 4500),
    # 计算机
    ("978-7-111-40701-0", "计算机网络：自顶向下方法（第6版）", 3, 8900),
    ("978-7-111-45382-1", "操作系统概念（第9版）", 2, 9900),
    ("978-7-111-54493-7", "数据库系统概念（第7版）", 2, 12800),
    ("978-7-115-44667-1", "代码整洁之道", 3, 6900),
    ("978-7-115-52941-2", "重构：改善既有代码的设计（第2版）", 2, 9900),
    ("978-7-111-49974-9", "编译原理（原书第2版）", 2, 12800),
    ("978-7-115-52223-9", "深入理解Java虚拟机（第3版）", 3, 9900),
    ("978-7-115-54752-3", "流畅的Python（第2版）", 2, 13900),
    ("978-7-111-67458-7", "机器学习", 3, 8800),
    # 数学
    ("978-7-04-039663-8", "高等数学（上册）", 6, 4200),
    ("978-7-04-039664-5", "高等数学（下册）", 6, 3900),
    ("978-7-04-028887-2", "线性代数（第6版）", 6, 2600),
    ("978-7-04-039661-4", "概率论与数理统计（第4版）", 5, 3400),
    ("978-7-111-36218-5", "具体数学：计算机科学基础", 2, 9900),
    # 物理 / 化学
    ("978-7-301-21569-2", "费曼物理学讲义（第一卷）", 2, 6800),
    ("978-7-03-028620-7", "大学物理（上册）", 5, 4600),
    ("978-7-04-050695-7", "有机化学（第9版）", 3, 6800),
    ("978-7-5086-7192-3", "上帝掷骰子吗：量子物理史话", 3, 4200),
    # 工科
    ("978-7-111-62017-2", "工程力学（第4版）", 4, 5600),
    ("978-7-5123-9843-7", "电路（第5版）", 4, 5200),
    ("978-7-111-59629-1", "自动控制原理（第7版）", 3, 5800),
    ("978-7-04-044523-7", "结构力学（上册）", 3, 4500),
]

# 借阅与预约一律用「姓名」定位读者，避免绰号/登录名混用
# 在借：姓名 -> 书名
BORROWING = [
    ("宋江", "红楼梦"),
    ("卢俊义", "战争与和平"),
    ("吴用", "深入理解Java虚拟机（第3版）"),
    ("林冲", "水浒传"),
    ("鲁智深", "三体"),
    ("武松", "百年孤独"),
    ("李逵", "三体Ⅱ·黑暗森林"),
    ("燕青", "三体Ⅲ·死神永生"),
    ("戴宗", "计算机网络：自顶向下方法（第6版）"),
    ("花荣", "活着"),
]

# 借了又还（当日归还，无罚款）
RETURNED = [
    ("柴进", "围城"),
    ("杨志", "罪与罚"),
    ("朱仝", "悲惨世界"),
]

# 预约排队：书名 -> [姓名...]（该书需已全部借出）
RESERVING = [
    ("三体", ["吴用", "杨志", "张清"]),
]

SMOKE_SQL = [
    "DELETE l FROM loans l JOIN books b ON b.id = l.book_id WHERE b.isbn LIKE 'SMOKE%'",
    "DELETE l FROM loans l JOIN readers r ON r.id = l.reader_id WHERE r.name LIKE '冒烟%'",
    "DELETE r FROM reservations r JOIN books b ON b.id = r.book_id WHERE b.isbn LIKE 'SMOKE%'",
    "DELETE r FROM reservations r JOIN readers rd ON rd.id = r.reader_id WHERE rd.name LIKE '冒烟%'",
    "DELETE a FROM accounts a JOIN readers r ON r.id = a.reader_id "
    "WHERE r.name LIKE '冒烟%' OR a.username LIKE 'smoke\\_u\\_%'",
    "DELETE FROM readers WHERE name LIKE '冒烟%'",
    "DELETE FROM books WHERE isbn LIKE 'SMOKE%'",
]


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    try:
        with OPENER.open(req, data=data, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode("utf-8"))


def sql(statement, fetch=False):
    cmd = ["docker", "exec", "springbook-mysql", "mysql", "--default-character-set=utf8mb4",
           "-uspringbook", "-pspringbook", "springbook", "-N", "-B", "-e", statement]
    done = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return done.stdout.strip() if fetch else None


def clean_smoke():
    print("[1/5] 清理冒烟测试数据")
    for statement in SMOKE_SQL:
        sql(statement)
    print("      图书 %s / 读者 %s / 账号 %s / 借阅 %s / 预约 %s"
          % (sql("SELECT COUNT(*) FROM books", True),
             sql("SELECT COUNT(*) FROM readers", True),
             sql("SELECT COUNT(*) FROM accounts", True),
             sql("SELECT COUNT(*) FROM loans", True),
             sql("SELECT COUNT(*) FROM reservations", True)))


def seed_readers():
    print("[2/5] 注册好汉读者（口令统一 %s）" % READER_PASSWORD)
    created = skipped = failed = 0
    for username, name, phone in HEROES:
        registered = call("POST", "/api/auth/register", {
            "username": username, "password": READER_PASSWORD, "name": name, "phone": phone,
        })
        if registered.get("code") == 0:
            created += 1
            print("      + %s（%s）" % (username, name))
            continue
        # 已存在：用统一口令回登一次，确认可登录
        login = call("POST", "/api/auth/login", {"username": username, "password": READER_PASSWORD})
        if login.get("code") == 0:
            skipped += 1
            print("      = %s 已存在，口令 %s 可登录" % (username, READER_PASSWORD))
        else:
            failed += 1
            print("      ! %s 注册失败且无法登录：%s" % (username, registered.get("message")))
    print("      新增 %s / 跳过 %s / 异常 %s" % (created, skipped, failed))
    return failed == 0


def seed_books(token):
    print("[3/5] 新增图书")
    existing = set()
    page = 1
    while True:
        result = call("GET", "/api/books?page=%s&size=100" % page, token=token)
        records = result["data"]["records"]
        existing.update(record["isbn"] for record in records)
        if len(records) < 100:
            break
        page += 1
    created = skipped = 0
    for isbn, title, total, price in BOOKS:
        if isbn in existing:
            skipped += 1
            continue
        result = call("POST", "/api/books",
                      {"isbn": isbn, "title": title, "totalCopies": total, "priceCents": price}, token)
        if result.get("code") == 0:
            created += 1
        else:
            print("      ! 《%s》失败：%s" % (title, result.get("message")))
    print("      新增 %s 本 / 已存在 %s 本" % (created, skipped))


def reader_id_by_name(token, name):
    result = call("GET", "/api/readers?keyword=%s&page=1&size=50" % quote(name), token=token)
    for record in result["data"]["records"]:
        if record["name"] == name:
            return record["id"]
    return None


def book_id_by_title(token, title):
    result = call("GET", "/api/books?keyword=%s&page=1&size=50" % quote(title), token=token)
    for record in result["data"]["records"]:
        if record["title"] == title:
            return record["id"]
    return None


def seed_loans(token):
    print("[4/5] 造借阅与预约")
    for name, title in BORROWING:
        reader_id = reader_id_by_name(token, name)
        book_id = book_id_by_title(token, title)
        open_loans = call("GET", "/api/loans?readerId=%s" % reader_id, token=token)["data"]
        if any(r["bookId"] == book_id and r["status"] == "BORROWED" for r in open_loans):
            continue
        result = call("POST", "/api/loans", {"bookId": book_id, "readerId": reader_id}, token)
        if result.get("code") == 0:
            print("      + %s 借出《%s》" % (name, title))
        else:
            print("      ! %s 借《%s》失败：%s" % (name, title, result.get("message")))

    for name, title in RETURNED:
        reader_id = reader_id_by_name(token, name)
        book_id = book_id_by_title(token, title)
        history = call("GET", "/api/loans?readerId=%s" % reader_id, token=token)["data"]
        if any(r["bookId"] == book_id for r in history):
            continue
        result = call("POST", "/api/loans", {"bookId": book_id, "readerId": reader_id}, token)
        if result.get("code") == 0:
            call("POST", "/api/loans/%s/return" % result["data"]["id"], None, token)
            print("      + %s 借阅并归还《%s》" % (name, title))

    for title, names in RESERVING:
        book_id = book_id_by_title(token, title)
        queue = call("GET", "/api/books/%s/reservations" % book_id, token=token)["data"]
        waiting = set(r["readerId"] for r in queue if r["status"] in ("WAITING", "READY"))
        for name in names:
            reader_id = reader_id_by_name(token, name)
            if reader_id in waiting:
                continue
            result = call("POST", "/api/reservations",
                          {"bookId": book_id, "readerId": reader_id}, token)
            if result.get("code") == 0:
                print("      + %s 预约《%s》" % (name, title))
            else:
                print("      ! %s 预约《%s》失败：%s" % (name, title, result.get("message")))


def report():
    print("[5/5] 数据概览")
    rows = sql(
        "SELECT '图书', COUNT(*) FROM books UNION ALL "
        "SELECT '读者', COUNT(*) FROM readers UNION ALL "
        "SELECT '账号', COUNT(*) FROM accounts UNION ALL "
        "SELECT '借阅', COUNT(*) FROM loans UNION ALL "
        "SELECT '预约', COUNT(*) FROM reservations", True)
    for line in rows.splitlines():
        print("      %s: %s" % tuple(line.split("\t")))


def main():
    admin = call("POST", "/api/auth/login", {"username": "admin", "password": ADMIN_PASSWORD})
    if admin.get("code") != 0:
        print("管理员登录失败：", admin)
        return 1
    token = admin["data"]["token"]

    clean_smoke()
    if not seed_readers():
        return 1
    seed_books(token)
    seed_loans(token)
    report()
    return 0


if __name__ == "__main__":
    sys.exit(main())
