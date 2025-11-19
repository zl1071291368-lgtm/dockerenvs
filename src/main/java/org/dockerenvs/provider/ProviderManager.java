package org.dockerenvs.provider;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.DatabaseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供者管理器
 * 负责管理和查找数据库提供者和运行时策略
 */
@Slf4j
@Component
public class ProviderManager {
    
    @Autowired(required = false)
    private List<DatabaseProvider> databaseProviders;
    
    @Autowired(required = false)
    private List<RuntimeStrategy> runtimeStrategies;
    
    private Map<String, DatabaseProvider> databaseProviderMap = new HashMap<>();
    private Map<String, RuntimeStrategy> runtimeStrategyMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // 初始化数据库提供者映射
        if (databaseProviders != null) {
            for (DatabaseProvider provider : databaseProviders) {
                String key = provider.getProviderType() + ":" + provider.getDatabaseType();
                databaseProviderMap.put(key, provider);
                log.info("注册数据库提供者: {}", key);
            }
        }
        
        // 初始化运行时策略映射
        if (runtimeStrategies != null) {
            for (RuntimeStrategy strategy : runtimeStrategies) {
                runtimeStrategyMap.put(strategy.getRuntimeType(), strategy);
                log.info("注册运行时策略: {}", strategy.getRuntimeType());
            }
        }
    }
    
    /**
     * 获取数据库提供者
     */
    public DatabaseProvider getDatabaseProvider(DatabaseConfig config) {
        if (config == null || !config.getEnabled()) {
            return null;
        }
        
        String key = config.getProvider() + ":" + config.getType();
        DatabaseProvider provider = databaseProviderMap.get(key);
        if (provider == null) {
            log.warn("未找到数据库提供者: {}, 使用默认的shared:mysql", key);
            key = "shared:mysql";
            provider = databaseProviderMap.get(key);
        }
        return provider;
    }
    
    /**
     * 获取运行时策略
     */
    public RuntimeStrategy getRuntimeStrategy(String runtimeType) {
        if (runtimeType == null) {
            runtimeType = "java"; // 默认
        }
        
        RuntimeStrategy strategy = runtimeStrategyMap.get(runtimeType);
        if (strategy == null) {
            log.warn("未找到运行时策略: {}, 使用默认的java策略", runtimeType);
            strategy = runtimeStrategyMap.get("java");
        }
        return strategy;
    }
}

