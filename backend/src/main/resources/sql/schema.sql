CREATE TABLE IF NOT EXISTS room (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    building_id VARCHAR(64) NOT NULL COMMENT '楼栋ID',
    building_name VARCHAR(128) NOT NULL COMMENT '楼栋名称',
    room_id VARCHAR(64) NOT NULL COMMENT '外部接口房间ID/roomid',
    room_name VARCHAR(64) NOT NULL COMMENT '房间号',
    alert_email VARCHAR(128) DEFAULT NULL COMMENT '告警接收邮箱',
    threshold DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '预警阈值',
    total DECIMAL(10, 2) DEFAULT NULL COMMENT '总电量',
    remain DECIMAL(10, 2) DEFAULT NULL COMMENT '当前剩余电量',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1警告 2告警',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
    update_time DATETIME DEFAULT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_building_room (building_id, room_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间信息表';

CREATE TABLE IF NOT EXISTS electricity_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id BIGINT NOT NULL COMMENT '房间主键ID',
    remain_snapshot DECIMAL(10, 2) NOT NULL COMMENT '拉取时快照电量',
    fetch_time DATETIME NOT NULL COMMENT '拉取时间',
    PRIMARY KEY (id),
    KEY idx_room_fetch_time (room_id, fetch_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电量流水表';

CREATE TABLE IF NOT EXISTS email_sender_pool (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    email_account VARCHAR(128) NOT NULL COMMENT '发件邮箱账号',
    auth_code VARCHAR(128) NOT NULL COMMENT '邮箱授权码',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_account (email_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱发件号池';
