-- 虚拟实验环境框架数据库初始化脚本

CREATE DATABASE IF NOT EXISTS virtual_env DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE virtual_env;

-- 虚拟环境表
CREATE TABLE IF NOT EXISTS virtual_env (
    env_id VARCHAR(50) PRIMARY KEY COMMENT '环境ID',
    user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
    system_id VARCHAR(50) COMMENT '系统ID',
    exp_id VARCHAR(50) NOT NULL COMMENT '实验ID',
    port INT NOT NULL COMMENT '分配的端口',
    container_id VARCHAR(100) COMMENT '容器ID',
    env_dir VARCHAR(500) COMMENT '环境目录路径',
    status VARCHAR(20) DEFAULT 'CREATED' COMMENT '状态：CREATED/RUNNING/STOPPED/DESTROYED',
    url VARCHAR(200) COMMENT '访问URL',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_exp_id (exp_id),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='虚拟环境表';

-- 端口使用表
CREATE TABLE IF NOT EXISTS port_usage (
    port INT PRIMARY KEY COMMENT '端口号',
    env_id VARCHAR(50) COMMENT '环境ID',
    status VARCHAR(20) DEFAULT 'USED' COMMENT '状态：USED/FREE',
    allocated_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    INDEX idx_env_id (env_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='端口使用表';

