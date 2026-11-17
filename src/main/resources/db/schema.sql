-- =====================================================================
-- 图书管理系统 · 表结构（MySQL 8 / utf8mb4）
-- 金额统一 DECIMAL(10,2)，时间 DATETIME
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(40)  NOT NULL,
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  name          VARCHAR(40)  NOT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'LIBRARIAN' COMMENT 'ADMIN/LIBRARIAN',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='馆员账号';

CREATE TABLE IF NOT EXISTS book (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  isbn             VARCHAR(20)  NOT NULL,
  title            VARCHAR(200) NOT NULL,
  author           VARCHAR(100) NOT NULL DEFAULT '',
  publisher        VARCHAR(100) NOT NULL DEFAULT '',
  category         VARCHAR(40)  NOT NULL DEFAULT '综合',
  location         VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '馆藏位置',
  total_copies     INT          NOT NULL DEFAULT 1 COMMENT '馆藏总数',
  available_copies INT          NOT NULL DEFAULT 1 COMMENT '可借数',
  price            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  publish_year     INT          NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/OFF_SHELF',
  remark           VARCHAR(300) NOT NULL DEFAULT '',
  deleted          TINYINT      NOT NULL DEFAULT 0,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_book_isbn (isbn),
  KEY idx_book_title (title),
  KEY idx_book_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书';

CREATE TABLE IF NOT EXISTS reader (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  card_no      VARCHAR(20)  NOT NULL COMMENT '借书证号',
  name         VARCHAR(40)  NOT NULL,
  phone        VARCHAR(20)  NOT NULL DEFAULT '',
  email        VARCHAR(80)  NOT NULL DEFAULT '',
  type         VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/TEACHER/PUBLIC',
  department   VARCHAR(60)  NOT NULL DEFAULT '',
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED',
  unpaid_fine  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '未缴罚金',
  borrowed_now INT          NOT NULL DEFAULT 0 COMMENT '当前在借数',
  total_borrow INT          NOT NULL DEFAULT 0 COMMENT '累计借阅数',
  remark       VARCHAR(300) NOT NULL DEFAULT '',
  deleted      TINYINT      NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reader_card (card_no),
  KEY idx_reader_name (name),
  KEY idx_reader_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='读者';

CREATE TABLE IF NOT EXISTS loan (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  book_id       BIGINT       NOT NULL,
  reader_id     BIGINT       NOT NULL,
  borrowed_at   DATETIME     NOT NULL,
  due_at        DATETIME     NOT NULL,
  returned_at   DATETIME     NULL,
  renew_count   INT          NOT NULL DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'BORROWED' COMMENT 'BORROWED/RETURNED/LOST',
  fine_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本次逾期罚金',
  fine_paid     TINYINT      NOT NULL DEFAULT 0,
  remark        VARCHAR(200) NOT NULL DEFAULT '',
  operator      VARCHAR(40)  NOT NULL DEFAULT '',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_loan_book (book_id),
  KEY idx_loan_reader (reader_id, status),
  KEY idx_loan_due (due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借阅记录';

CREATE TABLE IF NOT EXISTS reservation (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  book_id      BIGINT       NOT NULL,
  reader_id    BIGINT       NOT NULL,
  status       VARCHAR(16)  NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/NOTIFIED/FULFILLED/CANCELLED',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notified_at  DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_res_book (book_id, status),
  KEY idx_res_reader (reader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约队列';

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NULL,
  user_name   VARCHAR(40)  NOT NULL DEFAULT '',
  action      VARCHAR(40)  NOT NULL,
  target_type VARCHAR(20)  NOT NULL DEFAULT '',
  target_id   BIGINT       NULL,
  detail      VARCHAR(500) NOT NULL DEFAULT '',
  ip          VARCHAR(64)  NOT NULL DEFAULT '',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计';

CREATE TABLE IF NOT EXISTS setting (
  id    BIGINT       NOT NULL AUTO_INCREMENT,
  k     VARCHAR(64)  NOT NULL,
  v     VARCHAR(500) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_setting_k (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置';
