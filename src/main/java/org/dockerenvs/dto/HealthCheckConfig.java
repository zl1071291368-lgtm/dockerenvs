package org.dockerenvs.dto;

import lombok.Data;

/**
 * 健康检查配置
 */
@Data
public class HealthCheckConfig {
    
    /**
     * 健康检查测试命令（JSON数组格式）
     * 例如: ["CMD", "curl", "-f", "http://localhost:8080/health"]
     */
    private String test;
    
    /**
     * 检查间隔（默认30s）
     */
    private String interval = "30s";
    
    /**
     * 超时时间（默认10s）
     */
    private String timeout = "10s";
    
    /**
     * 重试次数（默认3）
     */
    private Integer retries = 3;
    
    /**
     * 启动等待时间（默认40s）
     */
    private String startPeriod = "40s";
}

