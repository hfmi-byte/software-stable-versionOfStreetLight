-- ============================================================
-- 智慧路灯 - 数据库建表脚本（接口规范 §7.1）
-- 库：dream22（第 8 组），账号见 总览信息.md
-- 字符集：utf8mb4
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
  id            INT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  role          VARCHAR(16)  NOT NULL COMMENT 'municipal | admin',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 设备主表（绑定/策略，低频变化）
-- 与 §7.1 相比新增了 huawei_device_id：用于把前端友好 ID（light001）映射到华为云的实际设备 ID
CREATE TABLE IF NOT EXISTS devices (
  id                VARCHAR(32)  PRIMARY KEY COMMENT '设备 ID，如 light001',
  name              VARCHAR(64)  NOT NULL,
  location          VARCHAR(128),
  threshold         INT          NOT NULL DEFAULT 150 COMMENT '开灯阈值 lux',
  auto_mode         TINYINT(1)   NOT NULL DEFAULT 1   COMMENT '1=自动 0=手动',
  huawei_device_id  VARCHAR(128) COMMENT '华为云 IoT 平台上的真实设备 ID（含产品前缀）',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_huawei_device_id (huawei_device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 设备实时状态（覆盖式写入，与 devices 1:1）
CREATE TABLE IF NOT EXISTS device_status (
  device_id   VARCHAR(32) PRIMARY KEY,
  online      TINYINT(1)  NOT NULL DEFAULT 0,
  lamp        TINYINT(1)  NOT NULL DEFAULT 0,
  light       INT         NOT NULL DEFAULT 0,
  last_seen   DATETIME,
  CONSTRAINT fk_status_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 历史光照
CREATE TABLE IF NOT EXISTS history (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  device_id   VARCHAR(32) NOT NULL,
  time        DATETIME    NOT NULL,
  light       INT         NOT NULL,
  lamp        TINYINT(1)  NOT NULL,
  source      VARCHAR(16) NOT NULL COMMENT '设备上报来源，如 华为云推送|MQTT上报',
  INDEX idx_device_time (device_id, time),
  CONSTRAINT fk_history_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 告警
CREATE TABLE IF NOT EXISTS alarms (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  device_id   VARCHAR(32) NOT NULL,
  time        DATETIME    NOT NULL,
  type        VARCHAR(32) NOT NULL,
  content     VARCHAR(255),
  level       VARCHAR(8)  NOT NULL COMMENT '低|中|高',
  status      VARCHAR(8)  NOT NULL DEFAULT '未处理' COMMENT '未处理|已处理',
  INDEX idx_device_time (device_id, time),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 事件（与告警合并展示，单独建表方便后续接 RAG / 数据可视化）
CREATE TABLE IF NOT EXISTS events (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  time        DATETIME    NOT NULL,
  title       VARCHAR(128) NOT NULL,
  copy        VARCHAR(255),
  INDEX idx_time (time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 初始化 3 台演示设备（huawei_device_id 留空，绑定后由 admin 在前端补上）
-- ============================================================
INSERT IGNORE INTO devices (id, name, location, threshold, auto_mode) VALUES
  ('light001', '一号路灯', '校园东门',   150, 1),
  ('light002', '二号路灯', '操场南侧',   150, 1),
  ('light003', '三号路灯', '图书馆路口', 150, 1);

UPDATE devices SET threshold = 150 WHERE id IN ('light001', 'light002', 'light003') AND threshold = 300;

INSERT IGNORE INTO device_status (device_id, online, lamp, light, last_seen) VALUES
  ('light001', 0, 0, 0, NULL),
  ('light002', 0, 0, 0, NULL),
  ('light003', 0, 0, 0, NULL);
