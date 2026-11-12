-- =====================================================================
-- V3：演示种子数据（随 jar 一起分发，首次启动就能看到内容）
--
-- 为什么日期都用 NOW() 相对生成：
--   写死绝对时间的种子放几周后，所有在借记录都会变成逾期，
--   首页、罚款页全是"C 逾期 N 天"，演示效果失真。
--   用相对偏移后，无论哪天第一次启动，都是
--   「正常在借 / 即将到期 / 已逾期 / 已归还」四种状态齐全。
--
-- 为什么只在 loans 为空时灌入：
--   开发库已有业务数据时不应被演示数据污染；而空库必然满足该条件。
--
-- 重置演示数据（会丢掉当前借阅与预约）：
--   DELETE FROM loans;
--   DELETE FROM reservations;
--   DELETE FROM flyway_schema_history WHERE version = '3';
--   重启应用，V3 会重新执行。
--   只删业务数据不删 history 是没用的，Flyway 认为 V3 已完成。
--
-- 为什么用固定主键 + INSERT IGNORE：
--   兼容"先导入过旧备份再启动"的情况，重复执行不会报主键冲突。
-- =====================================================================

SET @seed := (SELECT COUNT(*) = 0 FROM loans);

-- ---------------------------------------------------------------- 图书
-- 册数按「当前在借」扣减：三体 1/1 借出、红楼梦 5 册借出 1 等
INSERT IGNORE INTO books (isbn, title, total_copies, available_copies, price_cents)
SELECT * FROM (
              SELECT '978-7-5366-9293-0' AS isbn, '三体' AS title, 1 AS total_copies, 0 AS available_copies, 2300 AS price_cents
    UNION ALL SELECT '978-7-5366-9292-3', '三体Ⅱ·黑暗森林', 1, 1, 3200
    UNION ALL SELECT '978-7-02-000220-7', '红楼梦', 5, 4, 5980
    UNION ALL SELECT '978-7-02-000873-5', '三国演义', 4, 4, 4980
    UNION ALL SELECT '978-7-5063-6168-8', '活着', 3, 3, 3500
    UNION ALL SELECT '978-7-5442-6059-9', '百年孤独', 3, 2, 5500
    UNION ALL SELECT '978-7-115-52223-9', '深入理解Java虚拟机（第3版）', 3, 2, 9900
    UNION ALL SELECT '978-7-115-44667-1', '代码整洁之道', 3, 3, 6900
    UNION ALL SELECT '978-7-111-54493-7', '数据库系统概念（第7版）', 2, 1, 12800
    UNION ALL SELECT '978-7-04-039663-8', '高等数学（上册）', 6, 6, 4200
    UNION ALL SELECT '978-7-04-028887-2', '线性代数（第6版）', 6, 6, 2600
    UNION ALL SELECT '978-7-301-21569-2', '费曼物理学讲义（第一卷）', 2, 2, 6800
) AS seed_books
WHERE @seed = 1;

-- ---------------------------------------------------------------- 读者
-- 口令统一 123456（散列与种子账号一致，可直接登录体验"我的借阅"）
INSERT IGNORE INTO readers (name, phone, status)
SELECT * FROM (
              SELECT '张伟' AS name, '13922220001' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '李娜', '13922220002', 'ACTIVE'
    UNION ALL SELECT '王芳', '13922220003', 'ACTIVE'
    UNION ALL SELECT '刘洋', '13922220004', 'ACTIVE'
    UNION ALL SELECT '陈静', '13922220005', 'ACTIVE'
    UNION ALL SELECT '赵磊', '13922220006', 'ACTIVE'
) AS seed_readers
WHERE @seed = 1;

-- ---------------------------------------------------------------- 账号
INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT 'zhangwei', '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r WHERE r.phone = '13922220001' AND @seed = 1;

INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT 'lina', '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r WHERE r.phone = '13922220002' AND @seed = 1;

INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT 'wangfang', '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r WHERE r.phone = '13922220003' AND @seed = 1;

INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT 'liuyang', '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r WHERE r.phone = '13922220004' AND @seed = 1;

INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT 'chenjing', '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r WHERE r.phone = '13922220005' AND @seed = 1;

INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT 'zhaolei', '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r WHERE r.phone = '13922220006' AND @seed = 1;

-- ---------------------------------------------------------------- 借阅
-- 在借：正常 / 即将到期 / 已逾期三种状态各若干条
INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1001, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_ADD(NOW(3), INTERVAL 25 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220001'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1002, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 12 DAY), DATE_ADD(NOW(3), INTERVAL 18 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220002'
WHERE b.isbn = '978-7-02-000220-7' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1003, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 28 DAY), DATE_ADD(NOW(3), INTERVAL 2 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220003'
WHERE b.isbn = '978-7-115-52223-9' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1004, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 40 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220004'
WHERE b.isbn = '978-7-5442-6059-9' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1005, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 45 DAY), DATE_SUB(NOW(3), INTERVAL 15 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220005'
WHERE b.isbn = '978-7-111-54493-7' AND @seed = 1;

-- 已归还：近 30 天内的几条用来撑热门榜的借阅次数
INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1006, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 25 DAY), DATE_ADD(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 20 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220002'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1007, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 22 DAY), DATE_ADD(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 18 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220003'
WHERE b.isbn = '978-7-5366-9292-3' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1008, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 20 DAY), DATE_ADD(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 16 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220006'
WHERE b.isbn = '978-7-115-44667-1' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1009, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 15 DAY), DATE_ADD(NOW(3), INTERVAL 15 DAY), DATE_SUB(NOW(3), INTERVAL 13 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220005'
WHERE b.isbn = '978-7-115-44667-1' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1010, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 18 DAY), DATE_ADD(NOW(3), INTERVAL 12 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.phone = '13922220006'
WHERE b.isbn = '978-7-02-000220-7' AND @seed = 1;

-- 已归还且逾期：覆盖罚款的「已缴 / 待缴」两种状态
INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1011, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 70 DAY), DATE_SUB(NOW(3), INTERVAL 40 DAY), DATE_SUB(NOW(3), INTERVAL 35 DAY), 'RETURNED', 250, 'PAID'
FROM books b JOIN readers r ON r.phone = '13922220003'
WHERE b.isbn = '978-7-5063-6168-8' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1012, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 80 DAY), DATE_SUB(NOW(3), INTERVAL 50 DAY), DATE_SUB(NOW(3), INTERVAL 30 DAY), 'RETURNED', 1000, 'UNPAID'
FROM books b JOIN readers r ON r.phone = '13922220006'
WHERE b.isbn = '978-7-04-028887-2' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1013, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 90 DAY), DATE_SUB(NOW(3), INTERVAL 60 DAY), DATE_SUB(NOW(3), INTERVAL 58 DAY), 'RETURNED', 100, 'PAID'
FROM books b JOIN readers r ON r.phone = '13922220004'
WHERE b.isbn = '978-7-04-039663-8' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1014, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 60 DAY), DATE_SUB(NOW(3), INTERVAL 30 DAY), DATE_SUB(NOW(3), INTERVAL 28 DAY), 'RETURNED', 100, 'PAID'
FROM books b JOIN readers r ON r.phone = '13922220001'
WHERE b.isbn = '978-7-301-21569-2' AND @seed = 1;

-- ---------------------------------------------------------------- 预约
-- 《三体》仅 1 册且已借出，两位读者排队，用来演示预约队列与到书顺延
INSERT IGNORE INTO reservations (id, book_id, reader_id, status, created_at)
SELECT 2001, b.id, r.id, 'WAITING', DATE_SUB(NOW(3), INTERVAL 2 DAY)
FROM books b JOIN readers r ON r.phone = '13922220002'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

INSERT IGNORE INTO reservations (id, book_id, reader_id, status, created_at)
SELECT 2002, b.id, r.id, 'WAITING', DATE_SUB(NOW(3), INTERVAL 1 DAY)
FROM books b JOIN readers r ON r.phone = '13922220003'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;
