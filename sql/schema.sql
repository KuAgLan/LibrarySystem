-- 图书管理系统 MySQL 建表语句（与 Schema.java 一致）
-- 适用：MySQL 8.0+ / MariaDB 10.6+
CREATE DATABASE IF NOT EXISTS library DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE library;

CREATE TABLE IF NOT EXISTS books (
            id        BIGINT       NOT NULL AUTO_INCREMENT,
            isbn      VARCHAR(20)  NOT NULL,
            title     VARCHAR(120) NOT NULL,
            author    VARCHAR(80)  NOT NULL,
            category  VARCHAR(30)  NOT NULL,
            total     INT          NOT NULL,
            available INT          NOT NULL,
            deleted   TINYINT      NOT NULL DEFAULT 0,
            PRIMARY KEY (id),
            UNIQUE KEY uk_books_isbn (isbn),
            KEY idx_books_category (category),
            CONSTRAINT ck_books_stock CHECK (available >= 0 AND available <= total)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS readers (
            id         BIGINT      NOT NULL AUTO_INCREMENT,
            card_no    VARCHAR(20) NOT NULL,
            name       VARCHAR(40) NOT NULL,
            phone      VARCHAR(20) DEFAULT NULL,
            max_loans  INT         NOT NULL DEFAULT 5,
            balance    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
            enabled    TINYINT     NOT NULL DEFAULT 1,
            created_at DATETIME    NOT NULL,
            PRIMARY KEY (id),
            UNIQUE KEY uk_readers_card (card_no)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS loans (
            id          BIGINT   NOT NULL AUTO_INCREMENT,
            book_id     BIGINT   NOT NULL,
            reader_id   BIGINT   NOT NULL,
            borrowed_at DATETIME NOT NULL,
            due_at      DATE     NOT NULL,
            returned_at DATETIME DEFAULT NULL,
            fine        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
            PRIMARY KEY (id),
            KEY idx_loans_reader_date (reader_id, borrowed_at),
            KEY idx_loans_book (book_id),
            KEY idx_loans_open (reader_id, returned_at),
            CONSTRAINT fk_loans_book   FOREIGN KEY (book_id)   REFERENCES books(id),
            CONSTRAINT fk_loans_reader FOREIGN KEY (reader_id) REFERENCES readers(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservations (
            id          BIGINT   NOT NULL AUTO_INCREMENT,
            book_id     BIGINT   NOT NULL,
            reader_id   BIGINT   NOT NULL,
            created_at  DATETIME NOT NULL,
            notified_at DATETIME DEFAULT NULL,
            cancelled   TINYINT  NOT NULL DEFAULT 0,
            PRIMARY KEY (id),
            KEY idx_res_book (book_id, cancelled, created_at),
            CONSTRAINT fk_res_book   FOREIGN KEY (book_id)   REFERENCES books(id),
            CONSTRAINT fk_res_reader FOREIGN KEY (reader_id) REFERENCES readers(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
            id     BIGINT      NOT NULL AUTO_INCREMENT,
            at     DATETIME    NOT NULL,
            action VARCHAR(30) NOT NULL,
            detail VARCHAR(200) DEFAULT NULL,
            PRIMARY KEY (id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分布式锁表（MysqlLock 也会在启动时自动创建，这里只是留档）
CREATE TABLE IF NOT EXISTS lock_guard (
    name        VARCHAR(64)  NOT NULL,
    owner       VARCHAR(64)  NOT NULL,
    acquired_at DATETIME(3)  NOT NULL,
    expires_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
