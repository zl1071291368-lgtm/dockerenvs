package org.dockerenvs.provider;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.DatabaseConfig;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MySQL独立提供者（为每个环境创建独立的MySQL容器）
 */
@Slf4j
@Component
public class MySqlStandaloneProvider implements DatabaseProvider {
    
    @Override
    public String getProviderType() {
        return "standalone";
    }
    
    @Override
    public String getDatabaseType() {
        return "mysql";
    }
    
    @Override
    public void ensureDatabaseReady(DatabaseConfig config) {
        // standalone模式：数据库容器会在docker-compose中自动创建
        // 这里只需要记录日志，实际创建由docker-compose负责
        log.info("使用独立MySQL容器，将在docker-compose中创建数据库服务");
    }
    
    @Override
    public String getNetworkConfig() {
        // standalone模式：数据库和应用在同一网络中（env-xxx-net）
        // 不需要额外的网络配置
        return "";
    }
    
    @Override
    public String getNetworkDefinition() {
        // standalone模式：使用默认网络，不需要额外定义
        return "";
    }
    
    @Override
    public String getServiceConfig(DatabaseConfig config, Map<String, Object> context) {
        // 独立数据库模式：返回MySQL服务的docker-compose配置
        String containerName = (String) context.get("containerName");
        String networkName = (String) context.get("networkName");
        String envDir = (String) context.get("envDir");
        String initSqlPath = (String) context.get("initSqlPath");
        
        String dbPassword = config.getPassword() != null ? config.getPassword() : "123456";
        String dbName = config.getName() != null ? config.getName() : "test_db";
        String envDirNormalized = envDir.replace("\\", "/");
        
        String initSqlVolume = "";
        if (initSqlPath != null && !initSqlPath.isEmpty()) {
            initSqlVolume = "\n      - " + initSqlPath + ":/docker-entrypoint-initdb.d/init.sql:ro";
        }
        
        return "  db:\n" +
               "    image: mysql:8.0\n" +
               "    container_name: " + containerName + "-db\n" +
               "    environment:\n" +
               "      - MYSQL_ROOT_PASSWORD=" + dbPassword + "\n" +
               "      - MYSQL_DATABASE=" + dbName + "\n" +
               "    volumes:\n" +
               "      - " + envDirNormalized + "/mysql-data:/var/lib/mysql" + initSqlVolume + "\n" +
               "    networks:\n" +
               "      - " + networkName + "\n" +
               "    restart: unless-stopped\n" +
               "    healthcheck:\n" +
               "      test: [\"CMD\", \"mysqladmin\", \"ping\", \"-h\", \"localhost\", \"-u\", \"root\", \"-p" + dbPassword + "\"]\n" +
               "      interval: 5s\n" +
               "      timeout: 3s\n" +
               "      retries: 3\n" +
               "      start_period: 15s";
    }
    
    @Override
    public boolean shouldWaitForAppHealthCheck() {
        // 独立数据库模式：不等待应用健康检查，应用会自动重试连接数据库
        return false;
    }
    
    @Override
    public boolean shouldVerifyContainerExists() {
        // 独立数据库模式：跳过容器存在验证，docker compose已保证
        return false;
    }
    
    @Override
    public String getVolumeConfig() {
        // standalone模式：数据卷在服务配置中已定义，这里不需要额外配置
        return "";
    }
    
    @Override
    public Map<String, String> getEnvironmentVariables(DatabaseConfig config) {
        Map<String, String> env = new HashMap<>();
        // standalone模式：数据库容器名为 "db"（在docker-compose中定义）
        env.put("DB_HOST", "db");
        env.put("DB_PORT", "3306");
        env.put("DB_NAME", config.getName() != null ? config.getName() : "test_db");
        env.put("DB_USER", config.getUsername() != null ? config.getUsername() : "root");
        env.put("DB_PASSWORD", config.getPassword() != null ? config.getPassword() : "123456");
        env.put("DB_URL", String.format("jdbc:mysql://db:3306/%s?serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true",
                config.getName() != null ? config.getName() : "test_db"));
        return env;
    }
}

