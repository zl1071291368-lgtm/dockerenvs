package org.dockerenvs.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.DatabaseConfig;
import org.dockerenvs.dto.ExperimentMetadata;
import org.dockerenvs.dto.HealthCheckConfig;
import org.dockerenvs.dto.VolumeConfig;
import org.dockerenvs.provider.DatabaseProvider;
import org.dockerenvs.provider.ProviderManager;
import org.dockerenvs.provider.RuntimeStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模板管理服务
 */
@Slf4j
@Service
public class TemplateManagerService {
    
    @Value("${env.server.host:localhost}")
    private String serverHost;
    
    @Value("${env.apps.base-path:/opt/apps}")
    private String appsBasePath;
    
    @Autowired
    private ProviderManager providerManager;
    
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    
    /**
     * 生成docker-compose.yml文件（使用Mustache模板）
     */
    public void generateComposeFile(String envDir, String programPath, ExperimentMetadata metadata, 
                                       String envId, String userId, Integer hostPort) {
        try {
            // 读取Mustache模板
            Mustache mustache = mustacheFactory.compile("templates/docker-compose.mustache");
            
            // 获取运行时策略
            String runtimeType = metadata.getEffectiveRuntimeType();
            RuntimeStrategy strategy = providerManager.getRuntimeStrategy(runtimeType);
            
            // 构建模板上下文
            Map<String, Object> context = buildMustacheContext(metadata, envId, userId, hostPort, envDir, programPath, strategy);
            
            // 渲染模板
            StringWriter writer = new StringWriter();
            mustache.execute(writer, context);
            String content = writer.toString();
            
            // 写入文件
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            Files.write(composeFile, content.getBytes(StandardCharsets.UTF_8));
            
            log.info("生成docker-compose.yml: {}", composeFile);
            
        } catch (IOException e) {
            log.error("生成docker-compose.yml失败", e);
            throw new RuntimeException("生成docker-compose.yml失败", e);
        }
    }
    
    /**
     * 构建Mustache模板上下文
     */
    private Map<String, Object> buildMustacheContext(ExperimentMetadata metadata, 
                                                     String envId, String userId, 
                                                     Integer hostPort, String envDir,
                                                     String programPath,
                                                     RuntimeStrategy strategy) {
        Map<String, Object> context = new HashMap<>();
        
        // 基本信息
        context.put("baseImage", metadata.getBaseImage());
        context.put("containerName", "env-" + envId);
        
        // 启动命令
        Map<String, String> variables = new HashMap<>();
        variables.put("APP_PORT", String.valueOf(hostPort));
        variables.put("CONTAINER_PORT", String.valueOf(metadata.getEffectiveContainerPort()));
        variables.put("USER_ID", userId);
        variables.put("EXP_ID", metadata.getExpId());
        variables.put("ENV_ID", envId);
        
        String startCommand = strategy.buildStartCommand(metadata.getStartCommand(), variables);
        if (startCommand != null && !startCommand.trim().isEmpty()) {
            context.put("startCommand", startCommand);
        }
        
        // 端口映射
        Integer containerPort = metadata.getEffectiveContainerPort();
        if (metadata.getHostPorts() != null && !metadata.getHostPorts().isEmpty()) {
            context.put("ports", metadata.getHostPorts());
        } else {
            context.put("hostPort", String.valueOf(hostPort));
            context.put("containerPort", String.valueOf(containerPort));
        }
        
        // 数据卷
        List<VolumeConfig> volumes = metadata.getVolumes();
        if (volumes == null || volumes.isEmpty()) {
            volumes = strategy.getDefaultVolumes(envDir, programPath);
        }
        List<Map<String, String>> volumeList = volumes.stream().map(v -> {
            Map<String, String> vol = new HashMap<>();
            vol.put("hostPath", v.getHostPath());
            vol.put("containerPath", v.getContainerPath());
            vol.put("options", v.getOptions() != null ? v.getOptions() : "");
            return vol;
        }).collect(Collectors.toList());
        context.put("volumes", volumeList);
        
        // 环境变量
        Map<String, String> env = strategy.getDefaultEnvironment(metadata, hostPort);
        if (metadata.getEnv() != null) {
            // 安全地合并环境变量，确保所有值都是字符串
            // 即使 ExperimentMetadata.env 声明为 Map<String, String>，
            // Jackson 在反序列化时如果遇到对象值，可能会将其反序列化为 Map 或其他类型
            @SuppressWarnings("unchecked")
            Map<String, Object> envMap = (Map<String, Object>) (Map<?, ?>) metadata.getEnv();
            for (Map.Entry<String, Object> entry : envMap.entrySet()) {
                env.put(entry.getKey(), convertEnvValueToString(entry.getValue(), entry.getKey()));
            }
        }
        
        // 数据库环境变量
        DatabaseConfig dbConfig = metadata.getEffectiveDatabaseConfig();
        if (dbConfig != null && dbConfig.getEnabled()) {
            DatabaseProvider dbProvider = providerManager.getDatabaseProvider(dbConfig);
            if (dbProvider != null) {
                env.putAll(dbProvider.getEnvironmentVariables(dbConfig));
            }
        }
        
        // 构建环境变量列表，确保所有值都是字符串（双重保险）
        List<Map<String, String>> envList = env.entrySet().stream().map(e -> {
            Map<String, String> envVar = new HashMap<>();
            envVar.put("key", e.getKey());
            // 确保值始终是字符串，防止类型不一致导致的模板渲染错误
            Object value = e.getValue();
            String valueStr = convertEnvValueToString(value, e.getKey());
            envVar.put("value", valueStr);
            return envVar;
        }).collect(Collectors.toList());
        // 只有当环境变量列表不为空时才设置到上下文中
        // 这样模板中的 {{^environment}} 分支可以正确处理空列表的情况
        if (!envList.isEmpty()) {
            context.put("environment", envList);
        }
        
        // 网络
        String networkName = "env-" + envId + "-net";
        context.put("networkName", networkName);
        
        // 数据库网络配置
        if (dbConfig != null && dbConfig.getEnabled()) {
            DatabaseProvider dbProvider = providerManager.getDatabaseProvider(dbConfig);
            if (dbProvider != null) {
                String dbNetwork = dbProvider.getNetworkConfig();
                if (dbNetwork != null && !dbNetwork.trim().isEmpty()) {
                    // 提取网络名称（从 "      - network-name" 格式中提取）
                    String networkNameFromConfig = dbNetwork.replaceAll("^\\s*-\\s*", "").trim();
                    context.put("databaseNetwork", networkNameFromConfig);
                }
                String dbNetworkDef = dbProvider.getNetworkDefinition();
                if (dbNetworkDef != null && !dbNetworkDef.trim().isEmpty()) {
                    context.put("databaseNetworkDef", dbNetworkDef);
                }
            }
        }
        
        // 依赖关系
        // 注意：对于独立数据库模式，我们不设置 depends_on，让应用和数据库并行启动
        // 应用会自动重试连接数据库（已配置 initialization-fail-timeout=-1），这样可以最大化启动速度
        List<String> dependsOnList = new ArrayList<>();
        // 只对共享数据库模式设置依赖（如果需要）
        // standalone模式：不设置依赖，并行启动以加快速度
        if (!dependsOnList.isEmpty()) {
            context.put("dependsOn", true);
            context.put("dependsOnList", dependsOnList);
        }

        if (strategy.enableTty(metadata)) {
            context.put("ttyEnabled", true);
        }
        if (strategy.enableStdinOpen(metadata)) {
            context.put("stdinOpen", true);
        }
        String workingDir = strategy.getWorkingDirectory(metadata);
        if (StringUtils.hasText(workingDir)) {
            context.put("workingDir", workingDir);
        }
        
        // 健康检查
        HealthCheckConfig healthCheck = metadata.getHealthCheck();
        if (healthCheck == null) {
            healthCheck = strategy.getDefaultHealthCheck(containerPort);
        }
        if (healthCheck != null) {
            context.put("healthCheck", true);
            context.put("healthCheckTest", healthCheck.getTest());
            context.put("healthCheckInterval", healthCheck.getInterval());
            context.put("healthCheckTimeout", healthCheck.getTimeout());
            context.put("healthCheckRetries", String.valueOf(healthCheck.getRetries()));
            context.put("healthCheckStartPeriod", healthCheck.getStartPeriod());
        }
        
        // 附加服务
        List<String> additionalServices = new ArrayList<>();
        
        // 添加用户定义的服务
        if (metadata.getServices() != null && !metadata.getServices().isEmpty()) {
            for (org.dockerenvs.dto.ServiceConfig service : metadata.getServices()) {
                // 这里可以构建服务配置的YAML字符串
                // 简化处理，直接添加服务名称
                additionalServices.add("  " + service.getName() + ":\n    image: " + service.getImage());
            }
        }
        
        // 添加数据库提供者定义的服务（由提供者自己决定是否需要）
        if (dbConfig != null && dbConfig.getEnabled()) {
            DatabaseProvider dbProvider = providerManager.getDatabaseProvider(dbConfig);
            if (dbProvider != null) {
                // 构建模板上下文
                Map<String, Object> dbContext = new HashMap<>();
                dbContext.put("containerName", "env-" + envId);
                dbContext.put("networkName", networkName);
                dbContext.put("envDir", envDir);
                dbContext.put("initSqlPath", findInitSqlPath(metadata.getExpId(), envDir));
                
                String serviceConfig = dbProvider.getServiceConfig(dbConfig, dbContext);
                if (serviceConfig != null && !serviceConfig.trim().isEmpty()) {
                    additionalServices.add(serviceConfig);
                }
            }
        }
        
        if (!additionalServices.isEmpty()) {
            context.put("additionalServices", additionalServices);
        }
        
        // 附加数据卷（由数据库提供者决定是否需要）
        if (dbConfig != null && dbConfig.getEnabled()) {
            DatabaseProvider dbProvider = providerManager.getDatabaseProvider(dbConfig);
            if (dbProvider != null) {
                String volumeConfig = dbProvider.getVolumeConfig();
                if (volumeConfig != null && !volumeConfig.trim().isEmpty()) {
                    context.put("additionalVolumes", Collections.singletonList(volumeConfig));
                }
            }
        }
        
        return context;
    }
    
    /**
     * 将环境变量值安全地转换为字符串
     * 
     * 即使 ExperimentMetadata.env 声明为 Map<String, String>，
     * Jackson 在反序列化 JSON 时，如果遇到对象值，可能会将其反序列化为 Map 或其他类型。
     * 此方法确保所有环境变量值都被转换为字符串，避免 Docker Compose 解析错误。
     * 
     * @param value 环境变量值（可能是 String、Map、List 或其他类型）
     * @param key 环境变量键（用于日志记录）
     * @return 字符串形式的环境变量值
     */
    private String convertEnvValueToString(Object value, String key) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        // 如果是 Map 或其他对象，转换为 JSON 字符串
        // 这样用户可以在环境变量中传递 JSON 对象（作为字符串）
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("环境变量值转换失败，key={}, value={}，使用 toString()", key, value, ex);
            return String.valueOf(value);
        }
    }
    
    /**
     * 查找数据库初始化SQL文件路径
     * 优先级：
     * 1. apps/{expId}/db/init.sql（实验目录下的初始化脚本）
     * 2. 如果不存在，返回空字符串（MySQL容器会跳过初始化）
     */
    private String findInitSqlPath(String expId, String envDir) {
        // 优先查找实验目录下的 db/init.sql
        String initSqlPath = appsBasePath + "/" + expId + "/db/init.sql";
        Path initSqlFile = Paths.get(initSqlPath);
        
        if (Files.exists(initSqlFile)) {
            String normalizedPath = initSqlFile.toAbsolutePath().toString().replace("\\", "/");
            log.info("找到数据库初始化脚本: {}", normalizedPath);
            return normalizedPath;
        } else {
            log.warn("未找到数据库初始化脚本: {}，MySQL容器将使用默认初始化", initSqlPath);
            // 返回空字符串，MySQL容器会跳过初始化脚本挂载
            return "";
        }
    }
}

