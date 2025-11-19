package org.dockerenvs.dto;

import lombok.Data;

/**
 * 数据库配置
 */
@Data
public class DatabaseConfig {
    
    /**
     * 是否需要数据库（默认false）
     */
    private Boolean enabled = false;
    
    /**
     * 数据库提供者类型: shared / standalone / custom
     * - shared: 使用共享数据库容器（如 shared-mysql）
     * - standalone: 为每个实验创建独立的数据库容器
     * - custom: 使用自定义连接（外部数据库）
     */
    private String provider = "shared";
    
    /**
     * 数据库类型: mysql / postgres / mongodb 等
     */
    private String type = "mysql";
    
    /**
     * 数据库名称
     */
    private String name = "test_db";
    
    /**
     * 数据库密码（root用户密码）
     */
    private String password = "123456";
    
    /**
     * 数据库用户名（默认root）
     */
    private String username = "root";
    
    /**
     * 自定义连接字符串（当provider为custom时使用）
     * 例如: jdbc:mysql://host:port/database
     */
    private String connectionString;
    
    /**
     * 数据库主机（当provider为custom时使用）
     */
    private String host;
    
    /**
     * 数据库端口（当provider为custom时使用）
     */
    private Integer port;
}

