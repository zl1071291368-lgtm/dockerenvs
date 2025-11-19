package org.dockerenvs.provider;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.DatabaseConfig;
import org.dockerenvs.service.SharedMysqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MySQL共享提供者（使用共享MySQL容器）
 */
@Slf4j
@Component
public class MySqlSharedProvider implements DatabaseProvider {
    
    @Autowired(required = false)
    private SharedMysqlService sharedMysqlService;
    
    @Override
    public String getProviderType() {
        return "shared";
    }
    
    @Override
    public String getDatabaseType() {
        return "mysql";
    }
    
    @Override
    public void ensureDatabaseReady(DatabaseConfig config) {
        if (sharedMysqlService == null) {
            throw new RuntimeException("SharedMysqlService未配置，无法使用共享MySQL");
        }
        
        log.info("使用共享MySQL容器，检查容器和数据库就绪...");
        
        // 只检查数据库容器是否可用，不自动创建（生命周期独立管理）
        // 如果容器不存在，会抛出明确的错误提示，引导用户手动创建
        try {
            sharedMysqlService.checkSharedMysqlAvailable();
        } catch (RuntimeException e) {
            // 如果检查失败，尝试自动创建（如果配置允许）
            log.warn("共享MySQL容器检查失败: {}", e.getMessage());
            log.info("尝试自动创建共享MySQL容器...");
            try {
                sharedMysqlService.ensureSharedMysqlExists();
            } catch (RuntimeException createException) {
                // 如果自动创建也失败，返回更友好的错误信息
                throw new RuntimeException(
                    "共享MySQL容器不可用，且无法自动创建。\n" +
                    "错误详情: " + createException.getMessage() + "\n\n" +
                    "请手动创建共享数据库容器：\n" +
                    "1. 访问 /shared-mysql.html 进行管理\n" +
                    "2. 或调用 POST /api/shared-mysql/ensure 接口",
                    createException
                );
            }
        }
        
        // 确保具体的数据库（schema）存在（这是环境相关的，可以自动创建）
        String databaseName = config.getName();
        if (databaseName != null && !databaseName.trim().isEmpty()) {
            log.info("确保数据库存在: {}", databaseName);
            sharedMysqlService.ensureDatabaseExists(databaseName);
        }
    }
    
    @Override
    public String getNetworkConfig() {
        if (sharedMysqlService == null) {
            return "";
        }
        String networkName = sharedMysqlService.getNetworkName();
        return "      - " + networkName;
    }
    
    @Override
    public String getNetworkDefinition() {
        if (sharedMysqlService == null) {
            return "";
        }
        String networkName = sharedMysqlService.getNetworkName();
        return "  " + networkName + ":\n" +
               "    external: true";
    }
    
    @Override
    public String getServiceConfig() {
        // 共享模式不需要在docker-compose中定义服务
        return "";
    }
    
    @Override
    public String getVolumeConfig() {
        // 共享模式不需要在docker-compose中定义数据卷
        return "";
    }
    
    @Override
    public Map<String, String> getEnvironmentVariables(DatabaseConfig config) {
        Map<String, String> env = new HashMap<>();
        if (sharedMysqlService != null) {
            env.put("DB_HOST", sharedMysqlService.getContainerName());
            env.put("DB_PORT", "3306");
            env.put("DB_NAME", config.getName());
            env.put("DB_USER", config.getUsername());
            env.put("DB_PASSWORD", config.getPassword());
            env.put("DB_URL", String.format("jdbc:mysql://%s:3306/%s?serverTimezone=UTC&characterEncoding=UTF-8",
                    sharedMysqlService.getContainerName(), config.getName()));
        }
        return env;
    }
}

