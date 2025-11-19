package org.dockerenvs.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 实验元数据
 */
@Data
public class ExperimentMetadata {
    
    private String expId;
    
    private String name;
    
    /**
     * 类型: java / node / python / docker（向后兼容，建议使用runtimeType）
     */
    private String type;
    
    /**
     * 运行时类型: java / node / python / docker / springboot-jsp
     * 用于选择对应的运行时策略
     */
    private String runtimeType;
    
    /**
     * 基础镜像名称
     */
    private String baseImage;
    
    /**
     * 启动命令
     */
    private String startCommand;
    
    /**
     * 应用端口（容器内端口）（向后兼容）
     */
    private Integer port;
    
    /**
     * 容器内端口（新字段，优先使用）
     */
    private Integer containerPort;
    
    /**
     * 主机端口映射（格式: ["8080:8080", "9090:9090"]）
     * 如果不指定，系统会自动分配
     */
    private List<String> hostPorts;
    
    /**
     * 环境变量
     */
    private Map<String, String> env;
    
    /**
     * 数据卷配置
     */
    private List<VolumeConfig> volumes;
    
    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheck;
    
    /**
     * 附加服务配置（用于docker-compose中的services）
     */
    private List<ServiceConfig> services;
    
    // ========== 数据库配置（向后兼容字段） ==========
    
    /**
     * 是否需要MySQL数据库（默认false）（向后兼容）
     */
    private Boolean needsDatabase = false;
    
    /**
     * MySQL数据库名称（默认test_db）（向后兼容）
     */
    private String databaseName = "test_db";
    
    /**
     * MySQL root密码（默认123456）（向后兼容）
     */
    private String databasePassword = "123456";
    
    // ========== 新的数据库配置 ==========
    
    /**
     * 数据库配置（新字段，优先使用）
     */
    private DatabaseConfig database;
    
    /**
     * 获取运行时类型（兼容旧字段）
     */
    public String getEffectiveRuntimeType() {
        return runtimeType != null ? runtimeType : type;
    }
    
    /**
     * 获取容器端口（兼容旧字段）
     */
    public Integer getEffectiveContainerPort() {
        return containerPort != null ? containerPort : port;
    }
    
    /**
     * 获取数据库配置（兼容旧字段）
     */
    public DatabaseConfig getEffectiveDatabaseConfig() {
        if (database != null) {
            return database;
        }
        // 向后兼容：从旧字段构建DatabaseConfig
        DatabaseConfig config = new DatabaseConfig();
        config.setEnabled(needsDatabase != null && needsDatabase);
        config.setProvider("shared");
        config.setType("mysql");
        config.setName(databaseName);
        config.setPassword(databasePassword);
        return config;
    }
}

