package org.dockerenvs.provider;

import org.dockerenvs.dto.DatabaseConfig;

import java.util.Map;

/**
 * 数据库提供者接口
 * 每个数据库提供者应该完全封装自己的逻辑，包括：
 * - 服务配置生成
 * - 启动策略（是否需要等待健康检查等）
 * - 验证策略（是否需要验证容器存在等）
 */
public interface DatabaseProvider {
    
    /**
     * 获取提供者类型（shared/standalone/custom）
     */
    String getProviderType();
    
    /**
     * 获取数据库类型（mysql/postgres等）
     */
    String getDatabaseType();
    
    /**
     * 确保数据库就绪（容器存在、数据库已创建等）
     */
    void ensureDatabaseReady(DatabaseConfig config);
    
    /**
     * 获取docker-compose中需要添加的网络配置
     */
    String getNetworkConfig();
    
    /**
     * 获取docker-compose中需要添加的网络定义
     */
    String getNetworkDefinition();
    
    /**
     * 获取docker-compose中需要添加的服务配置（如果是standalone模式）
     * @param config 数据库配置
     * @param context 模板上下文（包含 envId, envDir, networkName 等）
     * @return 服务配置的YAML字符串，如果不需要额外服务则返回null或空字符串
     */
    String getServiceConfig(DatabaseConfig config, Map<String, Object> context);
    
    /**
     * 获取docker-compose中需要添加的数据卷配置
     */
    String getVolumeConfig();
    
    /**
     * 获取环境变量（用于传递给应用容器）
     */
    Map<String, String> getEnvironmentVariables(DatabaseConfig config);
    
    /**
     * 是否需要等待应用容器健康检查
     * @return true=等待健康检查，false=不等待（应用会自动重试连接）
     */
    default boolean shouldWaitForAppHealthCheck() {
        return true; // 默认等待健康检查
    }
    
    /**
     * 是否需要验证容器存在
     * @return true=验证容器存在，false=跳过验证（docker compose已保证）
     */
    default boolean shouldVerifyContainerExists() {
        return true; // 默认验证容器存在
    }
}

