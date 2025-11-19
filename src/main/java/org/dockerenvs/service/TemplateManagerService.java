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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
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
    
    @Autowired(required = false)
    private SharedMysqlService sharedMysqlService;
    
    @Autowired
    private ProviderManager providerManager;
    
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    
    /**
     * 生成docker-compose.yml文件
     */
    public void generateComposeFile(String envDir, Map<String, String> variables) {
        try {
            // 读取模板
            String template = readTemplate("templates/docker-compose.yml");
            
            // 如果START_COMMAND为空，移除command行
            String startCommand = variables.get("START_COMMAND");
            if (startCommand == null || startCommand.trim().isEmpty()) {
                // 移除 command: ${START_COMMAND} 这一行
                template = template.replaceAll("(?m)^\\s*command:\\s*\\$\\{START_COMMAND\\}\\s*$\\n?", "");
            }
            
            // 处理VOLUME_MOUNT_OPTIONS：如果为空，需要移除:ro部分
            String volumeOptions = variables.get("VOLUME_MOUNT_OPTIONS");
            if (volumeOptions == null || volumeOptions.trim().isEmpty()) {
                // 如果为空，移除 ${VOLUME_MOUNT_OPTIONS} 部分
                template = template.replace("${VOLUME_MOUNT_OPTIONS}", "");
            }
            
            // 处理MySQL相关配置（共享MySQL使用外部网络，不需要创建MySQL服务）
            String sharedMysqlNetwork = variables.get("SHARED_MYSQL_NETWORK");
            String sharedMysqlNetworkDef = variables.get("SHARED_MYSQL_NETWORK_DEF");
            String mysqlDependsOn = variables.get("MYSQL_DEPENDS_ON");
            String mysqlVolume = variables.get("MYSQL_VOLUME");
            
            // 如果不需要共享MySQL网络，移除相关配置
            if (sharedMysqlNetwork == null || sharedMysqlNetwork.trim().isEmpty()) {
                template = template.replaceAll("(?m)^\\s*\\$\\{SHARED_MYSQL_NETWORK\\}\\s*\\n?", "");
            }
            if (sharedMysqlNetworkDef == null || sharedMysqlNetworkDef.trim().isEmpty()) {
                template = template.replaceAll("(?m)^\\s*\\$\\{SHARED_MYSQL_NETWORK_DEF\\}\\s*\\n?", "");
            }
            // 如果 depends_on 为空，移除整个 depends_on 块（包括空行）
            if (mysqlDependsOn == null || mysqlDependsOn.trim().isEmpty()) {
                // 匹配 depends_on: 关键字和后面的 ${MYSQL_DEPENDS_ON}，以及可能的空行
                template = template.replaceAll("(?m)^\\s*depends_on:\\s*\\n?\\s*\\$\\{MYSQL_DEPENDS_ON\\}\\s*\\n?", "");
                // 如果模板中没有 depends_on: 关键字，直接移除 ${MYSQL_DEPENDS_ON} 行（包括换行符）
                // 但保留后续行的缩进
                template = template.replaceAll("(?m)^\\s*\\$\\{MYSQL_DEPENDS_ON\\}\\s*\\n", "");
            }
            // 如果 volumes 为空，移除整个 volumes 块（包括空行）
            if (mysqlVolume == null || mysqlVolume.trim().isEmpty()) {
                // 匹配 volumes: 关键字和后面的 ${MYSQL_VOLUME}，以及可能的空行
                template = template.replaceAll("(?m)^\\s*volumes:\\s*\\n?\\s*\\$\\{MYSQL_VOLUME\\}\\s*\\n?", "");
                // 如果模板中没有 volumes: 关键字，直接移除 ${MYSQL_VOLUME} 行和后续空行
                template = template.replaceAll("(?m)^\\s*\\$\\{MYSQL_VOLUME\\}\\s*\\n+", "");
            }
            
            // 替换变量
            String content = replaceVariables(template, variables);
            
            // 修复可能的缩进问题：当占位符为空时，可能导致后续行缩进丢失
            // 修复 restart 行的缩进（如果它没有缩进，说明前面的占位符被移除了）
            content = content.replaceAll("(?m)^restart:", "    restart:");
            // 修复 healthcheck 行的缩进
            content = content.replaceAll("(?m)^healthcheck:", "    healthcheck:");
            
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
     * 生成.env文件
     */
    public void generateEnvFile(String envDir, Map<String, String> variables) {
        try {
            StringBuilder content = new StringBuilder();
            // 过滤掉不应该写入.env文件的变量（这些变量只用于docker-compose.yml模板替换）
            String[] excludeKeys = {"MYSQL_SERVICE", "MYSQL_VOLUME", "SHARED_MYSQL_NETWORK", "SHARED_MYSQL_NETWORK_DEF"};
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String key = entry.getKey();
                // 跳过不应该写入.env的变量
                boolean shouldExclude = false;
                for (String excludeKey : excludeKeys) {
                    if (excludeKey.equals(key)) {
                        shouldExclude = true;
                        break;
                    }
                }
                if (!shouldExclude) {
                    String value = entry.getValue();
                    // 如果值为空，跳过（避免生成空行）
                    if (value != null && !value.trim().isEmpty()) {
                        content.append(key).append("=").append(value).append("\n");
                    }
                }
            }
            
            Path envFile = Paths.get(envDir, ".env");
            Files.write(envFile, content.toString().getBytes(StandardCharsets.UTF_8));
            
            log.info("生成.env文件: {}", envFile);
            
        } catch (IOException e) {
            log.error("生成.env文件失败", e);
            throw new RuntimeException("生成.env文件失败", e);
        }
    }
    
    /**
     * 读取模板文件
     */
    private String readTemplate(String templatePath) throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 如果类路径中不存在，尝试从文件系统读取
            Path path = Paths.get(templatePath);
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
            throw new IOException("模板文件不存在: " + templatePath, e);
        }
    }
    
    /**
     * 替换模板变量
     */
    private String replaceVariables(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }
    
    /**
     * 构建环境变量映射
     */
    public Map<String, String> buildVariables(String envId, String userId, String expId, 
                                               Integer port, String envDir, String programPath, 
                                               String baseImage, String containerName,
                                               String expType, Integer containerPort, String startCommand,
                                               Boolean needsDatabase, String databaseName, String databasePassword) {
        Map<String, String> variables = new HashMap<>();
        variables.put("BASE_IMAGE", baseImage);
        variables.put("CONTAINER_NAME", containerName);
        variables.put("APP_PORT", String.valueOf(port));
        variables.put("USER_ID", userId);
        variables.put("EXP_ID", expId);
        variables.put("PROGRAM_PATH", programPath);
        variables.put("LOG_PATH", envDir + "/logs");
        variables.put("NETWORK_NAME", "env-" + envId + "-net");
        variables.put("ENV_ID", envId);  // 添加ENV_ID，用于提取项目名
        // 启动命令：如果有则使用，否则使用空字符串（docker-compose会忽略空command）
        if (startCommand != null && !startCommand.trim().isEmpty()) {
            // 使用sh -c执行命令，支持环境变量替换
            variables.put("START_COMMAND", "sh -c \"" + startCommand.replace("\"", "\\\"") + "\"");
        } else {
            variables.put("START_COMMAND", "");
        }
        
        // 根据实验类型设置容器端口和挂载路径
        if ("nginx".equalsIgnoreCase(expType) || "docker".equalsIgnoreCase(expType)) {
            // Nginx或Docker镜像通常使用80端口，挂载为只读
            variables.put("CONTAINER_PORT", String.valueOf(containerPort != null ? containerPort : 80));
            variables.put("VOLUME_MOUNT_PATH", "/usr/share/nginx/html");
            variables.put("VOLUME_MOUNT_OPTIONS", ":ro"); // 只读挂载
            variables.put("HEALTHCHECK_TEST", "[\"CMD\", \"wget\", \"--quiet\", \"--tries=1\", \"--spider\", \"http://localhost/\"]");
            variables.put("HEALTHCHECK_START_PERIOD", "10s");
        } else {
            // Java/Node/Python等使用动态端口，挂载为可写（支持安装依赖）
            variables.put("CONTAINER_PORT", String.valueOf(containerPort != null ? containerPort : port));
            variables.put("VOLUME_MOUNT_PATH", "/app/program");
            variables.put("VOLUME_MOUNT_OPTIONS", ""); // 可写挂载，允许安装依赖
            variables.put("HEALTHCHECK_TEST", "[\"CMD\", \"curl\", \"-f\", \"http://localhost:" + containerPort + "/health\"]");
            variables.put("HEALTHCHECK_START_PERIOD", "40s");
        }
        
        // MySQL配置 - 使用共享MySQL容器
        if (needsDatabase != null && needsDatabase && sharedMysqlService != null) {
            // 连接到共享MySQL网络
            String sharedNetworkName = sharedMysqlService.getNetworkName();
            // 在networks部分添加共享MySQL网络（外部网络）
            variables.put("SHARED_MYSQL_NETWORK", "      - " + sharedNetworkName);
            // 在networks定义部分添加外部网络引用
            variables.put("SHARED_MYSQL_NETWORK_DEF", "  " + sharedNetworkName + ":\n" +
                "    external: true");
            // 不需要depends_on，因为共享MySQL容器独立运行
            variables.put("MYSQL_DEPENDS_ON", "");
            // 不需要volumes，因为共享MySQL容器独立管理数据卷
            variables.put("MYSQL_VOLUME", "");
        } else {
            variables.put("SHARED_MYSQL_NETWORK", "");
            variables.put("SHARED_MYSQL_NETWORK_DEF", "");
            variables.put("MYSQL_DEPENDS_ON", "");
            variables.put("MYSQL_VOLUME", "");
        }
        
        return variables;
    }
    
    /**
     * 使用新的配置化方式生成docker-compose.yml文件（使用Mustache模板）
     */
    public void generateComposeFileV2(String envDir, String programPath, ExperimentMetadata metadata, 
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
            
            log.info("生成docker-compose.yml (V2): {}", composeFile);
            
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
            env.putAll(metadata.getEnv());
        }
        
        // 数据库环境变量
        DatabaseConfig dbConfig = metadata.getEffectiveDatabaseConfig();
        if (dbConfig != null && dbConfig.getEnabled()) {
            DatabaseProvider dbProvider = providerManager.getDatabaseProvider(dbConfig);
            if (dbProvider != null) {
                env.putAll(dbProvider.getEnvironmentVariables(dbConfig));
            }
        }
        
        List<Map<String, String>> envList = env.entrySet().stream().map(e -> {
            Map<String, String> envVar = new HashMap<>();
            envVar.put("key", e.getKey());
            envVar.put("value", e.getValue());
            return envVar;
        }).collect(Collectors.toList());
        context.put("environment", envList);
        
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
        List<String> dependsOnList = new ArrayList<>();
        if (dbConfig != null && dbConfig.getEnabled() && "standalone".equals(dbConfig.getProvider())) {
            dependsOnList.add("db"); // standalone模式需要依赖数据库服务
        }
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
        if (metadata.getServices() != null && !metadata.getServices().isEmpty()) {
            List<String> additionalServices = new ArrayList<>();
            for (org.dockerenvs.dto.ServiceConfig service : metadata.getServices()) {
                // 这里可以构建服务配置的YAML字符串
                // 简化处理，直接添加服务名称
                additionalServices.add("  " + service.getName() + ":\n    image: " + service.getImage());
            }
            context.put("additionalServices", additionalServices);
        }
        
        // 附加数据卷（standalone数据库等）
        if (dbConfig != null && dbConfig.getEnabled() && "standalone".equals(dbConfig.getProvider())) {
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
}

