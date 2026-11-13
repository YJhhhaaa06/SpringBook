# -*- coding: utf-8 -*-
"""生成 V3__demo_seed.sql。

数据源直接复用 tools/seed_library_demo.py 的 BOOKS / HEROES，
避免两份清单漂移；生成结果覆盖 src/main/resources/db/migration/V3__demo_seed.sql。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from seed_library_demo import BOOKS, HEROES  # noqa: E402

OUT = ROOT / "src/main/resources/db/migration/V3__demo_seed.sql"
HASH = "$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm"

# (借阅 id, 读者姓名, ISBN, 借出天数前, 应还天数后/前, 归还天数前 or None, 罚款分, 罚款状态)
LOANS = [
    (1001, "宋江", "978-7-5366-9293-0", 5, 25, None, 0, "NONE"),
    (1002, "卢俊义", "978-7-02-000220-7", 12, 18, None, 0, "NONE"),
    (1003, "吴用", "978-7-115-52223-9", 28, 2, None, 0, "NONE"),
    (1004, "林冲", "978-7-5442-6059-9", 40, -10, None, 0, "NONE"),
    (1005, "鲁智深", "978-7-111-54493-7", 45, -15, None, 0, "NONE"),
    (1006, "武松", "978-7-5366-9293-0", 25, 5, 20, 0, "NONE"),
    (1007, "李逵", "978-7-5366-9292-3", 22, 8, 18, 0, "NONE"),
    (1008, "杨志", "978-7-115-44667-1", 20, 10, 16, 0, "NONE"),
    (1009, "戴宗", "978-7-115-44667-1", 15, 15, 13, 0, "NONE"),
    (1010, "花荣", "978-7-02-000220-7", 18, 12, 10, 0, "NONE"),
    (1011, "柴进", "978-7-5063-6168-8", 70, -40, 35, 250, "PAID"),
    (1012, "朱仝", "978-7-04-028887-2", 80, -50, 30, 1000, "UNPAID"),
    (1013, "秦明", "978-7-04-039663-8", 90, -60, 58, 100, "PAID"),
    (1014, "公孙胜", "978-7-301-21569-2", 60, -30, 28, 100, "PAID"),
]

# (预约 id, 读者姓名, ISBN, 天数前)
RESERVATIONS = [
    (2001, "吴用", "978-7-5366-9293-0", 2),
    (2002, "卢俊义", "978-7-5366-9293-0", 1),
]

HEAD = """-- =====================================================================
-- V3：演示种子数据（随 jar 一起分发，老师首次启动就能看到内容）
--
-- 为什么日期都用 NOW() 相对生成：
--   写死绝对时间的种子放几周后，所有在借记录都会变成逾期，
--   首页、罚款页全是"C 逾期 N 天"，演示效果失真。
--   用相对偏移后，无论哪天第一次启动，都是
--   「正常在借 / 即将到期 / 已逾期 / 已归还」四种状态齐全。
--
-- 为什么只在 loans 为空时灌入：
--   开发库已有业务数据时不应被演示数据污染；而老师拿到的空库
--   必然满足该条件。
--
-- 重置演示数据（会丢掉当前借阅与预约）：
--   DELETE FROM loans;
--   DELETE FROM reservations;
--   DELETE FROM flyway_schema_history WHERE version = '3';
--   重启应用，V3 会重新执行。
--   只删业务数据不删 history 是没用的，Flyway 认为 V3 已完成。
--
-- 为什么库存不写死：
--   available_copies 一律由 loans 反算，避免以后调整借阅数据时
--   与「总册数 - 在借数」对不上。
--
-- 为什么用固定主键 + INSERT IGNORE：
--   兼容"先导入过旧备份再启动"的情况，重复执行不会报主键冲突。
-- =====================================================================

SET @seed := (SELECT COUNT(*) = 0 FROM loans);
"""


def books_sql():
    rows = []
    for index, (isbn, title, total, price) in enumerate(BOOKS):
        keyword = "SELECT" if index == 0 else "UNION ALL SELECT"
        rows.append(f"    {keyword} '{isbn}' AS isbn, '{title}' AS title, "
                    f"{total} AS total_copies, {total} AS available_copies, {price} AS price_cents")
    return (
        "\n-- ---------------------------------------------------------------- 图书\n"
        "INSERT IGNORE INTO books (isbn, title, total_copies, available_copies, price_cents)\n"
        "SELECT * FROM (\n"
        + "\n".join(rows)
        + "\n) AS seed_books\n"
        "WHERE @seed = 1;\n"
    )


def readers_sql():
    rows = []
    for index, (_, name, phone) in enumerate(HEROES):
        keyword = "SELECT" if index == 0 else "UNION ALL SELECT"
        rows.append(f"    {keyword} '{name}' AS name, '{phone}' AS phone, 'ACTIVE' AS status")
    return (
        "\n-- ---------------------------------------------------------------- 读者\n"
        "-- 口令统一 123456，可直接登录体验「我的借阅」\n"
        "INSERT IGNORE INTO readers (name, phone, status)\n"
        "SELECT * FROM (\n"
        + "\n".join(rows)
        + "\n) AS seed_readers\n"
        "WHERE @seed = 1;\n"
    )


def accounts_sql():
    rows = []
    for index, (username, _, phone) in enumerate(HEROES):
        keyword = "SELECT" if index == 0 else "UNION ALL SELECT"
        rows.append(f"    {keyword} '{username}' AS username, '{phone}' AS phone")
    return (
        "\n-- ---------------------------------------------------------------- 账号\n"
        "INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)\n"
        "SELECT x.username, '%s', 'READER', r.id\n" % HASH
        + "FROM readers r JOIN (\n"
        + "\n".join(rows)
        + "\n) AS x ON x.phone = r.phone\n"
        "WHERE @seed = 1;\n"
    )


def loans_sql():
    parts = ["\n-- ---------------------------------------------------------------- 借阅"]
    for loan_id, name, isbn, borrowed_ago, due_offset, returned_ago, fine, fine_status in LOANS:
        borrowed = f"DATE_SUB(NOW(3), INTERVAL {borrowed_ago} DAY)"
        if due_offset >= 0:
            due = f"DATE_ADD(NOW(3), INTERVAL {due_offset} DAY)"
        else:
            due = f"DATE_SUB(NOW(3), INTERVAL {-due_offset} DAY)"
        returned = "NULL" if returned_ago is None else f"DATE_SUB(NOW(3), INTERVAL {returned_ago} DAY)"
        status = "BORROWED" if returned_ago is None else "RETURNED"
        parts.append(
            f"INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)\n"
            f"SELECT {loan_id}, b.id, r.id, {borrowed}, {due}, {returned}, '{status}', {fine}, '{fine_status}'\n"
            f"FROM books b JOIN readers r ON r.name = '{name}'\n"
            f"WHERE b.isbn = '{isbn}' AND @seed = 1;"
        )
    return "\n\n".join(parts) + "\n"


def reservations_sql():
    parts = ["\n-- ---------------------------------------------------------------- 预约",
             "-- 《三体》仅 1 册且已借出，两位读者排队，用来演示预约队列与到书顺延"]
    for res_id, name, isbn, ago in RESERVATIONS:
        parts.append(
            f"INSERT IGNORE INTO reservations (id, book_id, reader_id, status, created_at)\n"
            f"SELECT {res_id}, b.id, r.id, 'WAITING', DATE_SUB(NOW(3), INTERVAL {ago} DAY)\n"
            f"FROM books b JOIN readers r ON r.name = '{name}'\n"
            f"WHERE b.isbn = '{isbn}' AND @seed = 1;"
        )
    return "\n\n".join(parts) + "\n"


TAIL = """
-- ---------------------------------------------------------------- 库存反算
-- 放在最后统一算，前面增删借阅都不必再手改 available_copies
UPDATE books b
SET b.available_copies = b.total_copies - (
    SELECT COUNT(*) FROM loans l WHERE l.book_id = b.id AND l.status = 'BORROWED'
)
WHERE @seed = 1;
"""


def main():
    sql = HEAD + books_sql() + readers_sql() + accounts_sql() + loans_sql() + reservations_sql() + TAIL
    OUT.write_text(sql, encoding="utf-8", newline="\n")
    print("写出 %s：%d 本书 / %d 位读者 / %d 条借阅 / %d 条预约"
          % (OUT.name, len(BOOKS), len(HEROES), len(LOANS), len(RESERVATIONS)))


if __name__ == "__main__":
    main()
