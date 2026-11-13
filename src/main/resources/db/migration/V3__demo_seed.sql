-- =====================================================================
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

-- ---------------------------------------------------------------- 图书
INSERT IGNORE INTO books (isbn, title, total_copies, available_copies, price_cents)
SELECT * FROM (
    SELECT '978-7-02-000220-7' AS isbn, '红楼梦' AS title, 5 AS total_copies, 5 AS available_copies, 5980 AS price_cents
    UNION ALL SELECT '978-7-02-000873-5' AS isbn, '三国演义' AS title, 4 AS total_copies, 4 AS available_copies, 4980 AS price_cents
    UNION ALL SELECT '978-7-02-000874-2' AS isbn, '西游记' AS title, 4 AS total_copies, 4 AS available_copies, 5580 AS price_cents
    UNION ALL SELECT '978-7-02-000875-9' AS isbn, '水浒传' AS title, 4 AS total_copies, 4 AS available_copies, 5580 AS price_cents
    UNION ALL SELECT '978-7-02-001526-9' AS isbn, '呐喊' AS title, 2 AS total_copies, 2 AS available_copies, 2800 AS price_cents
    UNION ALL SELECT '978-7-02-004123-7' AS isbn, '边城' AS title, 2 AS total_copies, 2 AS available_copies, 2600 AS price_cents
    UNION ALL SELECT '978-7-02-002552-7' AS isbn, '平凡的世界' AS title, 6 AS total_copies, 6 AS available_copies, 10800 AS price_cents
    UNION ALL SELECT '978-7-5063-6168-8' AS isbn, '活着' AS title, 3 AS total_copies, 3 AS available_copies, 3500 AS price_cents
    UNION ALL SELECT '978-7-5442-5455-4' AS isbn, '围城' AS title, 3 AS total_copies, 3 AS available_copies, 3900 AS price_cents
    UNION ALL SELECT '978-7-5442-6059-9' AS isbn, '百年孤独' AS title, 3 AS total_copies, 3 AS available_copies, 5500 AS price_cents
    UNION ALL SELECT '978-7-5327-7453-9' AS isbn, '战争与和平' AS title, 4 AS total_copies, 4 AS available_copies, 9800 AS price_cents
    UNION ALL SELECT '978-7-5327-7462-1' AS isbn, '安娜·卡列尼娜' AS title, 3 AS total_copies, 3 AS available_copies, 7800 AS price_cents
    UNION ALL SELECT '978-7-5327-7000-5' AS isbn, '罪与罚' AS title, 3 AS total_copies, 3 AS available_copies, 6800 AS price_cents
    UNION ALL SELECT '978-7-02-015311-4' AS isbn, '老人与海' AS title, 3 AS total_copies, 3 AS available_copies, 2500 AS price_cents
    UNION ALL SELECT '978-7-5442-6025-4' AS isbn, '霍乱时期的爱情' AS title, 2 AS total_copies, 2 AS available_copies, 4900 AS price_cents
    UNION ALL SELECT '978-7-5594-1975-3' AS isbn, '局外人' AS title, 2 AS total_copies, 2 AS available_copies, 2900 AS price_cents
    UNION ALL SELECT '978-7-5327-8080-6' AS isbn, '悲惨世界' AS title, 4 AS total_copies, 4 AS available_copies, 12800 AS price_cents
    UNION ALL SELECT '978-7-02-014437-0' AS isbn, '傲慢与偏见' AS title, 3 AS total_copies, 3 AS available_copies, 3600 AS price_cents
    UNION ALL SELECT '978-7-5442-5426-4' AS isbn, '追风筝的人' AS title, 3 AS total_copies, 3 AS available_copies, 3900 AS price_cents
    UNION ALL SELECT '978-7-5366-9293-0' AS isbn, '三体' AS title, 1 AS total_copies, 1 AS available_copies, 2300 AS price_cents
    UNION ALL SELECT '978-7-5366-9292-3' AS isbn, '三体Ⅱ·黑暗森林' AS title, 1 AS total_copies, 1 AS available_copies, 3200 AS price_cents
    UNION ALL SELECT '978-7-5366-9291-6' AS isbn, '三体Ⅲ·死神永生' AS title, 1 AS total_copies, 1 AS available_copies, 3800 AS price_cents
    UNION ALL SELECT '978-7-5086-7453-1' AS isbn, '枪炮、病菌与钢铁' AS title, 2 AS total_copies, 2 AS available_copies, 5600 AS price_cents
    UNION ALL SELECT '978-7-301-25520-9' AS isbn, '中国哲学简史' AS title, 2 AS total_copies, 2 AS available_copies, 4500 AS price_cents
    UNION ALL SELECT '978-7-111-40701-0' AS isbn, '计算机网络：自顶向下方法（第6版）' AS title, 3 AS total_copies, 3 AS available_copies, 8900 AS price_cents
    UNION ALL SELECT '978-7-111-45382-1' AS isbn, '操作系统概念（第9版）' AS title, 2 AS total_copies, 2 AS available_copies, 9900 AS price_cents
    UNION ALL SELECT '978-7-111-54493-7' AS isbn, '数据库系统概念（第7版）' AS title, 2 AS total_copies, 2 AS available_copies, 12800 AS price_cents
    UNION ALL SELECT '978-7-115-44667-1' AS isbn, '代码整洁之道' AS title, 3 AS total_copies, 3 AS available_copies, 6900 AS price_cents
    UNION ALL SELECT '978-7-115-52941-2' AS isbn, '重构：改善既有代码的设计（第2版）' AS title, 2 AS total_copies, 2 AS available_copies, 9900 AS price_cents
    UNION ALL SELECT '978-7-111-49974-9' AS isbn, '编译原理（原书第2版）' AS title, 2 AS total_copies, 2 AS available_copies, 12800 AS price_cents
    UNION ALL SELECT '978-7-115-52223-9' AS isbn, '深入理解Java虚拟机（第3版）' AS title, 3 AS total_copies, 3 AS available_copies, 9900 AS price_cents
    UNION ALL SELECT '978-7-115-54752-3' AS isbn, '流畅的Python（第2版）' AS title, 2 AS total_copies, 2 AS available_copies, 13900 AS price_cents
    UNION ALL SELECT '978-7-111-67458-7' AS isbn, '机器学习' AS title, 3 AS total_copies, 3 AS available_copies, 8800 AS price_cents
    UNION ALL SELECT '978-7-04-039663-8' AS isbn, '高等数学（上册）' AS title, 6 AS total_copies, 6 AS available_copies, 4200 AS price_cents
    UNION ALL SELECT '978-7-04-039664-5' AS isbn, '高等数学（下册）' AS title, 6 AS total_copies, 6 AS available_copies, 3900 AS price_cents
    UNION ALL SELECT '978-7-04-028887-2' AS isbn, '线性代数（第6版）' AS title, 6 AS total_copies, 6 AS available_copies, 2600 AS price_cents
    UNION ALL SELECT '978-7-04-039661-4' AS isbn, '概率论与数理统计（第4版）' AS title, 5 AS total_copies, 5 AS available_copies, 3400 AS price_cents
    UNION ALL SELECT '978-7-111-36218-5' AS isbn, '具体数学：计算机科学基础' AS title, 2 AS total_copies, 2 AS available_copies, 9900 AS price_cents
    UNION ALL SELECT '978-7-301-21569-2' AS isbn, '费曼物理学讲义（第一卷）' AS title, 2 AS total_copies, 2 AS available_copies, 6800 AS price_cents
    UNION ALL SELECT '978-7-03-028620-7' AS isbn, '大学物理（上册）' AS title, 5 AS total_copies, 5 AS available_copies, 4600 AS price_cents
    UNION ALL SELECT '978-7-04-050695-7' AS isbn, '有机化学（第9版）' AS title, 3 AS total_copies, 3 AS available_copies, 6800 AS price_cents
    UNION ALL SELECT '978-7-5086-7192-3' AS isbn, '上帝掷骰子吗：量子物理史话' AS title, 3 AS total_copies, 3 AS available_copies, 4200 AS price_cents
    UNION ALL SELECT '978-7-111-62017-2' AS isbn, '工程力学（第4版）' AS title, 4 AS total_copies, 4 AS available_copies, 5600 AS price_cents
    UNION ALL SELECT '978-7-5123-9843-7' AS isbn, '电路（第5版）' AS title, 4 AS total_copies, 4 AS available_copies, 5200 AS price_cents
    UNION ALL SELECT '978-7-111-59629-1' AS isbn, '自动控制原理（第7版）' AS title, 3 AS total_copies, 3 AS available_copies, 5800 AS price_cents
    UNION ALL SELECT '978-7-04-044523-7' AS isbn, '结构力学（上册）' AS title, 3 AS total_copies, 3 AS available_copies, 4500 AS price_cents
) AS seed_books
WHERE @seed = 1;

-- ---------------------------------------------------------------- 读者
-- 口令统一 123456，可直接登录体验「我的借阅」
INSERT IGNORE INTO readers (name, phone, status)
SELECT * FROM (
    SELECT '宋江' AS name, '13911110001' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '卢俊义' AS name, '13911110002' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '吴用' AS name, '13911110003' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '公孙胜' AS name, '13911110004' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '关胜' AS name, '13911110005' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '林冲' AS name, '13911110006' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '秦明' AS name, '13911110007' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '呼延灼' AS name, '13911110008' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '花荣' AS name, '13911110009' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '柴进' AS name, '13911110010' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '李应' AS name, '13911110011' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '朱仝' AS name, '13911110012' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '鲁智深' AS name, '13911110013' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '武松' AS name, '13911110014' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '董平' AS name, '13911110015' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '张清' AS name, '13911110016' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '杨志' AS name, '13911110017' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '徐宁' AS name, '13911110018' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '索超' AS name, '13911110019' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '戴宗' AS name, '13911110020' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '刘唐' AS name, '13911110021' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '李逵' AS name, '13911110022' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '李俊' AS name, '13911110023' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '阮小二' AS name, '13911110024' AS phone, 'ACTIVE' AS status
    UNION ALL SELECT '燕青' AS name, '13911110025' AS phone, 'ACTIVE' AS status
) AS seed_readers
WHERE @seed = 1;

-- ---------------------------------------------------------------- 账号
INSERT IGNORE INTO accounts (username, password_hash, role, reader_id)
SELECT x.username, '$2a$10$AAVycOtsjcHvlmVk54yMYOs9yu4FPrThSmuh9lqFvoil6vPrisGHm', 'READER', r.id
FROM readers r JOIN (
    SELECT '及时雨' AS username, '13911110001' AS phone
    UNION ALL SELECT '玉麒麟' AS username, '13911110002' AS phone
    UNION ALL SELECT '智多星' AS username, '13911110003' AS phone
    UNION ALL SELECT '入云龙' AS username, '13911110004' AS phone
    UNION ALL SELECT '大刀关胜' AS username, '13911110005' AS phone
    UNION ALL SELECT '豹子头' AS username, '13911110006' AS phone
    UNION ALL SELECT '霹雳火' AS username, '13911110007' AS phone
    UNION ALL SELECT '双鞭呼延灼' AS username, '13911110008' AS phone
    UNION ALL SELECT '小李广' AS username, '13911110009' AS phone
    UNION ALL SELECT '小旋风' AS username, '13911110010' AS phone
    UNION ALL SELECT '扑天雕' AS username, '13911110011' AS phone
    UNION ALL SELECT '美髯公' AS username, '13911110012' AS phone
    UNION ALL SELECT '花和尚' AS username, '13911110013' AS phone
    UNION ALL SELECT '行者武松' AS username, '13911110014' AS phone
    UNION ALL SELECT '双枪将' AS username, '13911110015' AS phone
    UNION ALL SELECT '没羽箭' AS username, '13911110016' AS phone
    UNION ALL SELECT '青面兽' AS username, '13911110017' AS phone
    UNION ALL SELECT '金枪手' AS username, '13911110018' AS phone
    UNION ALL SELECT '急先锋' AS username, '13911110019' AS phone
    UNION ALL SELECT '神行太保' AS username, '13911110020' AS phone
    UNION ALL SELECT '赤发鬼' AS username, '13911110021' AS phone
    UNION ALL SELECT '黑旋风' AS username, '13911110022' AS phone
    UNION ALL SELECT '混江龙' AS username, '13911110023' AS phone
    UNION ALL SELECT '立地太岁' AS username, '13911110024' AS phone
    UNION ALL SELECT '浪子燕青' AS username, '13911110025' AS phone
) AS x ON x.phone = r.phone
WHERE @seed = 1;

-- ---------------------------------------------------------------- 借阅

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1001, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_ADD(NOW(3), INTERVAL 25 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '宋江'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1002, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 12 DAY), DATE_ADD(NOW(3), INTERVAL 18 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '卢俊义'
WHERE b.isbn = '978-7-02-000220-7' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1003, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 28 DAY), DATE_ADD(NOW(3), INTERVAL 2 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '吴用'
WHERE b.isbn = '978-7-115-52223-9' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1004, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 40 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '林冲'
WHERE b.isbn = '978-7-5442-6059-9' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1005, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 45 DAY), DATE_SUB(NOW(3), INTERVAL 15 DAY), NULL, 'BORROWED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '鲁智深'
WHERE b.isbn = '978-7-111-54493-7' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1006, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 25 DAY), DATE_ADD(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 20 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '武松'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1007, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 22 DAY), DATE_ADD(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 18 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '李逵'
WHERE b.isbn = '978-7-5366-9292-3' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1008, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 20 DAY), DATE_ADD(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 16 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '杨志'
WHERE b.isbn = '978-7-115-44667-1' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1009, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 15 DAY), DATE_ADD(NOW(3), INTERVAL 15 DAY), DATE_SUB(NOW(3), INTERVAL 13 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '戴宗'
WHERE b.isbn = '978-7-115-44667-1' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1010, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 18 DAY), DATE_ADD(NOW(3), INTERVAL 12 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), 'RETURNED', 0, 'NONE'
FROM books b JOIN readers r ON r.name = '花荣'
WHERE b.isbn = '978-7-02-000220-7' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1011, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 70 DAY), DATE_SUB(NOW(3), INTERVAL 40 DAY), DATE_SUB(NOW(3), INTERVAL 35 DAY), 'RETURNED', 250, 'PAID'
FROM books b JOIN readers r ON r.name = '柴进'
WHERE b.isbn = '978-7-5063-6168-8' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1012, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 80 DAY), DATE_SUB(NOW(3), INTERVAL 50 DAY), DATE_SUB(NOW(3), INTERVAL 30 DAY), 'RETURNED', 1000, 'UNPAID'
FROM books b JOIN readers r ON r.name = '朱仝'
WHERE b.isbn = '978-7-04-028887-2' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1013, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 90 DAY), DATE_SUB(NOW(3), INTERVAL 60 DAY), DATE_SUB(NOW(3), INTERVAL 58 DAY), 'RETURNED', 100, 'PAID'
FROM books b JOIN readers r ON r.name = '秦明'
WHERE b.isbn = '978-7-04-039663-8' AND @seed = 1;

INSERT IGNORE INTO loans (id, book_id, reader_id, borrowed_at, due_at, returned_at, status, fine_cents, fine_status)
SELECT 1014, b.id, r.id, DATE_SUB(NOW(3), INTERVAL 60 DAY), DATE_SUB(NOW(3), INTERVAL 30 DAY), DATE_SUB(NOW(3), INTERVAL 28 DAY), 'RETURNED', 100, 'PAID'
FROM books b JOIN readers r ON r.name = '公孙胜'
WHERE b.isbn = '978-7-301-21569-2' AND @seed = 1;

-- ---------------------------------------------------------------- 预约

-- 《三体》仅 1 册且已借出，两位读者排队，用来演示预约队列与到书顺延

INSERT IGNORE INTO reservations (id, book_id, reader_id, status, created_at)
SELECT 2001, b.id, r.id, 'WAITING', DATE_SUB(NOW(3), INTERVAL 2 DAY)
FROM books b JOIN readers r ON r.name = '吴用'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

INSERT IGNORE INTO reservations (id, book_id, reader_id, status, created_at)
SELECT 2002, b.id, r.id, 'WAITING', DATE_SUB(NOW(3), INTERVAL 1 DAY)
FROM books b JOIN readers r ON r.name = '卢俊义'
WHERE b.isbn = '978-7-5366-9293-0' AND @seed = 1;

-- ---------------------------------------------------------------- 库存反算
-- 放在最后统一算，前面增删借阅都不必再手改 available_copies
UPDATE books b
SET b.available_copies = b.total_copies - (
    SELECT COUNT(*) FROM loans l WHERE l.book_id = b.id AND l.status = 'BORROWED'
)
WHERE @seed = 1;
