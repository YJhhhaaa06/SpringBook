-- =====================================================================
-- 图书信息管理系统 V1：初始建表
-- 约定：金额一律以「分」为整数存储（price_cents / fine_cents）
-- 引擎 InnoDB，字符集 utf8mb4，排序规则 utf8mb4_0900_ai_ci
-- =====================================================================

CREATE TABLE books (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    isbn             VARCHAR(20)  NOT NULL COMMENT '书号，唯一',
    title            VARCHAR(200) NOT NULL COMMENT '书名',
    total_copies     INT          NOT NULL COMMENT '总册数',
    available_copies INT          NOT NULL COMMENT '可借阅数量',
    price_cents      INT          NOT NULL COMMENT '价格（分）',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_books_isbn (isbn),
    KEY idx_books_title (title),
    CONSTRAINT chk_books_available CHECK (available_copies BETWEEN 0 AND total_copies),
    CONSTRAINT chk_books_price CHECK (price_cents >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '图书';

CREATE TABLE readers (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL COMMENT '姓名',
    phone      VARCHAR(20)  NOT NULL COMMENT '手机号，唯一',
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / FROZEN',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_readers_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '借阅人';

CREATE TABLE accounts (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 口令散列',
    role          VARCHAR(16)  NOT NULL COMMENT '角色：READER / ADMIN',
    reader_id     BIGINT UNSIGNED NULL COMMENT 'READER 账号关联 readers.id；ADMIN 为 NULL',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounts_username (username),
    UNIQUE KEY uk_accounts_reader (reader_id),
    CONSTRAINT fk_accounts_reader FOREIGN KEY (reader_id) REFERENCES readers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '登录账号';

CREATE TABLE loans (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    book_id     BIGINT UNSIGNED NOT NULL COMMENT '图书',
    reader_id   BIGINT UNSIGNED NOT NULL COMMENT '借阅人',
    borrowed_at DATETIME(3)  NOT NULL COMMENT '借出时间',
    due_at      DATETIME(3)  NOT NULL COMMENT '应还时间（借出时确定，不做动态计算）',
    returned_at DATETIME(3)  NULL COMMENT '实际归还时间',
    status      VARCHAR(16)  NOT NULL COMMENT '状态：BORROWED / RETURNED（逾期由 due_at 派生，不落库）',
    fine_cents  INT          NOT NULL DEFAULT 0 COMMENT '罚款（分），归还时结算',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_loans_reader (reader_id, status),
    KEY idx_loans_book_due (book_id, due_at),
    CONSTRAINT fk_loans_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_loans_reader FOREIGN KEY (reader_id) REFERENCES readers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '借阅记录';

CREATE TABLE reservations (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    book_id    BIGINT UNSIGNED NOT NULL COMMENT '图书',
    reader_id  BIGINT UNSIGNED NOT NULL COMMENT '预约人',
    status     VARCHAR(16)  NOT NULL COMMENT '状态：WAITING / READY / FULFILLED / CANCELLED / EXPIRED',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '预约时间（FIFO 排队依据）',
    ready_at   DATETIME(3)  NULL COMMENT '轮到取书的时间',
    hold_until DATETIME(3)  NULL COMMENT 'READY 保留截止时间（超时置 EXPIRED 并顺延）',
    PRIMARY KEY (id),
    KEY idx_res_queue (book_id, status, created_at),
    CONSTRAINT fk_res_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_res_reader FOREIGN KEY (reader_id) REFERENCES readers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预约队列';
