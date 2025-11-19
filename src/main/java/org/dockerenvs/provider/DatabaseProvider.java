package org.dockerenvs.provider;

import org.dockerenvs.dto.DatabaseConfig;

import java.util.Map;

/**
 * 数据库提供者接口
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
     */
    String getServiceConfig();
    
    /**
     * 获取docker-compose中需要添加的数据卷配置
     */
    String getVolumeConfig();
    
    /**
     * 获取环境变量（用于传递给应用容器）
     */
    Map<String, String> getEnvironmentVariables(DatabaseConfig config);
}

