package org.dockerenvs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dao.mapper.VirtualEnvMapper;
import org.dockerenvs.dto.DatabaseConfig;
import org.dockerenvs.dto.EnvInfo;
import org.dockerenvs.dto.ExperimentMetadata;
import org.dockerenvs.dto.StartEnvRequest;
import org.dockerenvs.entity.VirtualEnv;
import org.dockerenvs.exception.ContainerException;
import org.dockerenvs.exception.DatabaseException;
import org.dockerenvs.exception.EnvNotFoundException;
import org.dockerenvs.provider.DatabaseProvider;
import org.dockerenvs.provider.ProviderManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 环境管理服务（核心服务）
 */
@Slf4j
@Service
public class EnvManagerService {
    
    @Autowired
    private VirtualEnvMapper virtualEnvMapper;
    
    @Autowired
    private PortManagerService portManagerService;
    
    @Autowired
    private FileManagerService fileManagerService;
    
    @Autowired
    private TemplateManagerService templateManagerService;
    
    @Autowired
    private DockerOpsService dockerOpsService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 注意：此字段已不再直接使用，数据库初始化已统一使用 DatabaseProvider
    // 保留此字段以保持向后兼容，但实际已通过 getEffectiveDatabaseConfig() 统一处理
    @Autowired(required = false)
    @SuppressWarnings("unused")
    private SharedMysqlService sharedMysqlService;
    
    @Autowired
    private ProviderManager providerManager;
    
    @Value("${env.server.host:localhost}")
    private String serverHost;
    
    /**
     * 创建/启动环境
     */
    @Transactional(rollbackFor = Exception.class)
    public EnvInfo createEnv(StartEnvRequest request) {
        log.info("创建环境请求: userId={}, systemId={}, expId={}", 
            request.getUserId(), request.getSystemId(), request.getExpId());
        // 1. 检查环境是否已存在
        VirtualEnv existingEnv = findExistingEnv(request.getUserId(), request.getSystemId(), request.getExpId());
        if (existingEnv != null) {
            log.info("找到已存在的环境: envId={}, userId={}, systemId={}, expId={}, status={}", 
                existingEnv.getEnvId(), existingEnv.getUserId(), existingEnv.getSystemId(), 
                existingEnv.getExpId(), existingEnv.getStatus());
        } else {
            log.info("未找到已存在的环境，将创建新环境");
        }
        
        if (existingEnv != null && "RUNNING".equals(existingEnv.getStatus())) {
            log.info("环境已存在且运行中: {}", existingEnv.getEnvId());
            return convertToEnvInfo(existingEnv);
        }
        
        // 2. 如果环境存在但已停止，直接启动它
        if (existingEnv != null && "STOPPED".equals(existingEnv.getStatus())) {
            log.info("环境已存在但已停止，启动已存在的环境: {}", existingEnv.getEnvId());
            try {
                startEnv(existingEnv.getEnvId());
                // 重新查询环境信息（状态已更新为RUNNING）
                VirtualEnv updatedEnv = virtualEnvMapper.selectById(existingEnv.getEnvId());
                return convertToEnvInfo(updatedEnv);
            } catch (Exception e) {
                log.error("启动已停止的环境失败: envId={}", existingEnv.getEnvId(), e);
                // 如果启动失败（例如容器已被删除），继续创建新环境
                log.info("启动失败，将销毁旧环境并创建新环境");
                // 先释放端口，避免端口冲突
                if (existingEnv.getPort() != null) {
                    try {
                        portManagerService.releasePort(existingEnv.getPort());
                        log.info("释放旧环境端口: port={}", existingEnv.getPort());
                    } catch (Exception ex) {
                        log.warn("释放旧环境端口失败，继续销毁: port={}", existingEnv.getPort(), ex);
                    }
                }
                // 然后销毁环境
                destroyEnv(existingEnv.getEnvId());
            }
        }
        
        // 3. 分配端口
        String envId = generateEnvId();
        log.info("生成新环境ID: {}", envId);
        Integer port = portManagerService.assignPort(envId);
        log.info("分配端口: {} 给环境: {}", port, envId);
        
        // 4. 生成环境目录
        String envDir = fileManagerService.generateEnvDir(
            request.getUserId(), request.getSystemId(), request.getExpId());
        log.info("生成环境目录: {}", envDir);
        
        // 5. 读取实验元数据
        ExperimentMetadata metadata = readExperimentMetadata(request.getExpId());
        String runtimeType = metadata.getEffectiveRuntimeType();
        
        // 5.1 获取实验程序包共享路径
        String programPath = fileManagerService.getAppSourcePath(request.getExpId());
        log.info("使用共享程序目录: {}", programPath);
        
        // 6.5. 处理数据库配置（统一使用新的配置化方式，getEffectiveDatabaseConfig已处理向后兼容）
        DatabaseConfig dbConfig = metadata.getEffectiveDatabaseConfig();
        DatabaseProvider dbProvider = null;
        
        if (dbConfig != null && dbConfig.getEnabled()) {
            log.info("实验需要数据库，配置: provider={}, type={}, name={}", 
                dbConfig.getProvider(), dbConfig.getType(), dbConfig.getName());
            try {
                dbProvider = providerManager.getDatabaseProvider(dbConfig);
                if (dbProvider != null) {
                    dbProvider.ensureDatabaseReady(dbConfig);
                    log.info("数据库已就绪");
                } else {
                    log.warn("未找到数据库提供者，跳过数据库初始化");
                }
            } catch (Exception e) {
                log.error("数据库初始化失败", e);
                // 释放端口
                cleanupResources(null, port, null);
                throw new DatabaseException(DatabaseException.ERROR_CODE_INIT_FAILED, 
                    "数据库初始化失败: " + e.getMessage(), e);
            }
        }
        
        // 决定是否等待健康检查（由数据库提供者和运行时类型决定）
        boolean waitForHealth = (runtimeType == null || !runtimeType.equalsIgnoreCase("python"));
        if (dbProvider != null) {
            waitForHealth = waitForHealth && dbProvider.shouldWaitForAppHealthCheck();
        }
        
        // 7. 生成docker-compose.yml文件
        String containerName = "env-" + envId;
        log.info("生成容器名称: {}", containerName);
        
        // 使用配置化方式生成docker-compose.yml
        templateManagerService.generateComposeFile(envDir, programPath, metadata, envId, request.getUserId(), port);
        
        // 8. 启动容器
        log.info("开始启动容器: envDir={}, containerName={}", envDir, containerName);
        String containerId = null;
        try {
            containerId = dockerOpsService.startContainer(envDir, waitForHealth);
            log.info("容器启动完成，容器ID: {}", containerId);
        } catch (Exception e) {
            log.error("容器启动失败: envId={}, envDir={}", envId, envDir, e);
            // 清理已创建的资源
            cleanupResources(envDir, port, containerId);
            // 抛出更详细的错误信息
            if (e instanceof ContainerException) {
                throw e;
            }
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                "容器启动失败: " + e.getMessage(), e);
        }
        
        // 9. 验证容器存在（由数据库提供者决定是否需要验证）
        boolean shouldVerify = dbProvider == null || dbProvider.shouldVerifyContainerExists();
        if (shouldVerify) {
            if (!dockerOpsService.containerExists(containerId)) {
                log.error("容器验证失败，容器不存在: envId={}, containerId={}", envId, containerId);
                // 清理资源
                cleanupResources(envDir, port, containerId);
                throw new ContainerException(ContainerException.ERROR_CODE_NOT_FOUND,
                    "容器验证失败，容器不存在: " + containerId);
            }
        } else {
            log.info("数据库提供者配置跳过容器存在验证");
        }
        
        // 10. 保存环境信息
        VirtualEnv virtualEnv = new VirtualEnv();
        virtualEnv.setEnvId(envId);
        virtualEnv.setUserId(request.getUserId());
        virtualEnv.setSystemId(request.getSystemId());
        virtualEnv.setExpId(request.getExpId());
        virtualEnv.setPort(port);
        virtualEnv.setContainerId(containerId);
        virtualEnv.setEnvDir(envDir);
        virtualEnv.setStatus("RUNNING");
        virtualEnv.setUrl(String.format("http://%s:%d", serverHost, port));
        virtualEnv.setCreatedTime(LocalDateTime.now());
        virtualEnv.setUpdatedTime(LocalDateTime.now());
        
        virtualEnvMapper.insert(virtualEnv);
        
        log.info("环境创建成功: envId={}, port={}, containerId={}, url={}", 
            envId, port, containerId, virtualEnv.getUrl());
        
        return convertToEnvInfo(virtualEnv);
    }
    
    /**
     * 停止环境（保留容器，不删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void stopEnv(String envId) {
        VirtualEnv env = virtualEnvMapper.selectById(envId);
        if (env == null) {
            throw new EnvNotFoundException(envId);
        }
        
        if ("STOPPED".equals(env.getStatus())) {
            log.info("环境已停止: {}", envId);
            return;
        }
        
        // 只停止容器，不删除（保留容器以便快速恢复）
        dockerOpsService.stopContainerOnly(env.getEnvDir());
        
        // 更新状态
        env.setStatus("STOPPED");
        env.setUpdatedTime(LocalDateTime.now());
        virtualEnvMapper.updateById(env);
        
        log.info("环境停止成功（容器已保留）: {}", envId);
    }
    
    /**
     * 启动环境（启动已存在的容器）
     */
    @Transactional(rollbackFor = Exception.class)
    public void startEnv(String envId) {
        VirtualEnv env = virtualEnvMapper.selectById(envId);
        if (env == null) {
            throw new EnvNotFoundException(envId);
        }
        
        if ("RUNNING".equals(env.getStatus())) {
            log.info("环境已运行: {}", envId);
            return;
        }
        
        // 启动已存在的容器（如果容器不存在，会抛出异常）
        String containerId = dockerOpsService.startContainerOnly(env.getEnvDir());
        
        // 验证容器ID是否有效
        if (containerId == null || containerId.trim().isEmpty()) {
            log.error("容器启动失败，无法获取容器ID: envId={}, envDir={}", envId, env.getEnvDir());
            env.setStatus("STOPPED");
            env.setUpdatedTime(LocalDateTime.now());
            virtualEnvMapper.updateById(env);
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                "容器启动失败，无法获取容器ID。如果容器不存在，请使用重置功能。");
        }
        
        // 验证容器是否真的存在
        if (!dockerOpsService.containerExists(containerId)) {
            log.error("容器启动后不存在: envId={}, containerId={}", envId, containerId);
            env.setStatus("STOPPED");
            env.setUpdatedTime(LocalDateTime.now());
            virtualEnvMapper.updateById(env);
            throw new ContainerException(ContainerException.ERROR_CODE_NOT_FOUND,
                "容器启动后不存在。如果容器已被删除，请使用重置功能。");
        }
        
        // 更新状态（容器ID保持不变）
        env.setStatus("RUNNING");
        env.setUpdatedTime(LocalDateTime.now());
        virtualEnvMapper.updateById(env);
        
        log.info("环境启动成功（使用已存在的容器）: {}", envId);
    }
    
    /**
     * 重置环境（删除并重建容器，确保配置变更生效）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetEnv(String envId) {
        VirtualEnv env = virtualEnvMapper.selectById(envId);
        if (env == null) {
            throw new EnvNotFoundException(envId);
        }
        
        // 删除容器（使用 down，确保配置变更能生效）
        dockerOpsService.stopContainer(env.getEnvDir());
        
        // 重新创建并启动容器（使用 up，创建新容器）
        ExperimentMetadata metadata = readExperimentMetadata(env.getExpId());
        String runtimeType = metadata.getEffectiveRuntimeType();
        boolean waitForHealth = runtimeType == null || !runtimeType.equalsIgnoreCase("python");
        String containerId = dockerOpsService.startContainer(env.getEnvDir(), waitForHealth);
        
        // 验证容器ID是否有效
        if (containerId == null || containerId.trim().isEmpty()) {
            log.error("容器启动失败，无法获取容器ID: envId={}, envDir={}", envId, env.getEnvDir());
            env.setStatus("STOPPED");
            env.setUpdatedTime(LocalDateTime.now());
            virtualEnvMapper.updateById(env);
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                "容器启动失败，无法获取容器ID");
        }
        
        // 验证容器是否真的存在
        if (!dockerOpsService.containerExists(containerId)) {
            log.error("容器启动后不存在: envId={}, containerId={}", envId, containerId);
            env.setStatus("STOPPED");
            env.setUpdatedTime(LocalDateTime.now());
            virtualEnvMapper.updateById(env);
            throw new ContainerException(ContainerException.ERROR_CODE_NOT_FOUND,
                "容器启动后不存在");
        }
        
        // 更新状态
        env.setContainerId(containerId);
        env.setStatus("RUNNING");
        env.setUpdatedTime(LocalDateTime.now());
        virtualEnvMapper.updateById(env);
        
        log.info("环境重置成功: {}", envId);
    }
    
    /**
     * 销毁环境
     */
    @Transactional(rollbackFor = Exception.class)
    public void destroyEnv(String envId) {
        VirtualEnv env = virtualEnvMapper.selectById(envId);
        if (env == null) {
            log.warn("环境不存在: {}", envId);
            throw new EnvNotFoundException(envId);
        }
        
        log.info("开始销毁环境: envId={}, containerId={}, envDir={}", 
            envId, env.getContainerId(), env.getEnvDir());
        
        // 记录容器ID，用于后续验证
        String containerId = env.getContainerId();
        
        // 停止容器并删除命名volume（销毁环境时需要完全清理）
        if (env.getEnvDir() != null && !env.getEnvDir().isEmpty()) {
            try {
                dockerOpsService.stopContainer(env.getEnvDir(), true); // true表示删除volume
                log.info("容器停止并删除volume成功: {}", envId);
            } catch (Exception e) {
                log.error("停止容器失败，继续销毁: {}", envId, e);
                // 不抛出异常，继续执行删除操作
            }
        }
        
        // 释放端口（如果还未释放）
        if (env.getPort() != null) {
            try {
                portManagerService.releasePort(env.getPort());
                log.info("端口释放成功: port={}", env.getPort());
            } catch (Exception e) {
                log.error("释放端口失败: port={}", env.getPort(), e);
            }
        }
        
        // 删除环境目录
        if (env.getEnvDir() != null && !env.getEnvDir().isEmpty()) {
            try {
                java.nio.file.Path envPath = java.nio.file.Paths.get(env.getEnvDir());
                if (java.nio.file.Files.exists(envPath)) {
                    fileManagerService.deleteDirectory(envPath);
                    log.info("环境目录删除成功: {}", env.getEnvDir());
                } else {
                    log.warn("环境目录不存在，跳过删除: {}", env.getEnvDir());
                }
            } catch (Exception e) {
                log.error("删除环境目录失败: {}", env.getEnvDir(), e);
                // 记录详细错误信息
                log.error("删除目录异常详情", e);
                // 不抛出异常，继续更新数据库状态
            }
        }
        
        // 验证容器是否真的被删除
        if (containerId != null && !containerId.trim().isEmpty()) {
            try {
                if (dockerOpsService.containerExists(containerId)) {
                    log.warn("容器仍然存在，尝试强制删除: containerId={}", containerId);
                    // 尝试强制删除容器
                    try {
                        ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", containerId);
                        Process p = pb.start();
                        p.waitFor();
                        log.info("强制删除容器: {}", containerId);
                    } catch (Exception e) {
                        log.warn("强制删除容器失败: {}", containerId, e);
                    }
                } else {
                    log.info("容器已成功删除: containerId={}", containerId);
                }
            } catch (Exception e) {
                log.warn("验证容器删除状态时出错: {}", containerId, e);
            }
        }
        
        // 更新状态（必须执行，即使删除目录失败）
        try {
            env.setStatus("DESTROYED");
            env.setUpdatedTime(LocalDateTime.now());
            int updateCount = virtualEnvMapper.updateById(env);
            if (updateCount > 0) {
                log.info("数据库状态更新成功: envId={}, status=DESTROYED", envId);
            } else {
                log.warn("数据库状态更新失败，可能环境不存在: envId={}", envId);
            }
        } catch (Exception e) {
            log.error("更新数据库状态失败: envId={}", envId, e);
            // 销毁操作中的数据库更新失败不应该阻止销毁流程，只记录日志
            log.warn("数据库状态更新失败，但环境已销毁: envId={}", envId);
        }
        
        log.info("环境销毁完成: envId={}, containerId={}", envId, containerId);
    }
    
    /**
     * 查询环境状态
     */
    public EnvInfo getEnvStatus(String envId) {
        VirtualEnv env = virtualEnvMapper.selectById(envId);
        if (env == null) {
            return null;
        }
        
        // 检查容器实际状态
        if ("RUNNING".equals(env.getStatus()) && env.getContainerId() != null) {
            if (!dockerOpsService.containerExists(env.getContainerId())) {
                env.setStatus("STOPPED");
                env.setUpdatedTime(LocalDateTime.now());
                virtualEnvMapper.updateById(env);
            }
        }
        
        return convertToEnvInfo(env);
    }
    
    /**
     * 查询用户的所有环境
     */
    public List<EnvInfo> getUserEnvs(String userId) {
        LambdaQueryWrapper<VirtualEnv> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VirtualEnv::getUserId, userId);
        queryWrapper.ne(VirtualEnv::getStatus, "DESTROYED");
        queryWrapper.orderByDesc(VirtualEnv::getCreatedTime);
        
        List<VirtualEnv> envs = virtualEnvMapper.selectList(queryWrapper);
        return envs.stream()
            .map(this::convertToEnvInfo)
            .collect(Collectors.toList());
    }
    
    /**
     * 查询所有环境（管理用）
     */
    public List<EnvInfo> getAllEnvs() {
        LambdaQueryWrapper<VirtualEnv> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(VirtualEnv::getStatus, "DESTROYED");
        queryWrapper.orderByDesc(VirtualEnv::getCreatedTime);
        
        List<VirtualEnv> envs = virtualEnvMapper.selectList(queryWrapper);
        return envs.stream()
            .map(this::convertToEnvInfo)
            .collect(Collectors.toList());
    }
    
    /**
     * 查找已存在的环境
     */
    private VirtualEnv findExistingEnv(String userId, String systemId, String expId) {
        LambdaQueryWrapper<VirtualEnv> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VirtualEnv::getUserId, userId);
        
        // 正确处理 systemId 为 null 或空字符串的情况
        // 将空字符串视为 null，统一处理
        String normalizedSystemId = (systemId != null && !systemId.trim().isEmpty()) ? systemId.trim() : null;
        
        if (normalizedSystemId != null) {
            queryWrapper.eq(VirtualEnv::getSystemId, normalizedSystemId);
        } else {
            // 查询 systemId 为 null 或空字符串的记录
            queryWrapper.and(wrapper -> wrapper
                .isNull(VirtualEnv::getSystemId)
                .or()
                .eq(VirtualEnv::getSystemId, "")
            );
        }
        
        queryWrapper.eq(VirtualEnv::getExpId, expId);
        queryWrapper.ne(VirtualEnv::getStatus, "DESTROYED");
        queryWrapper.orderByDesc(VirtualEnv::getCreatedTime);
        queryWrapper.last("LIMIT 1");
        
        VirtualEnv result = virtualEnvMapper.selectOne(queryWrapper);
        
        if (result != null) {
            log.debug("查询到已存在的环境: envId={}, userId={}, systemId={}, expId={}, status={}", 
                result.getEnvId(), result.getUserId(), result.getSystemId(), 
                result.getExpId(), result.getStatus());
        }
        
        return result;
    }
    
    /**
     * 读取实验元数据
     */
    private ExperimentMetadata readExperimentMetadata(String expId) {
        try {
            String metadataJson = fileManagerService.readExperimentMetadata(expId);
            if (metadataJson != null) {
                ExperimentMetadata metadata = objectMapper.readValue(metadataJson, ExperimentMetadata.class);
                // 确保数据库相关字段有默认值（如果 JSON 中没有这些字段，Jackson 会使用 DTO 中的默认值）
                if (metadata.getNeedsDatabase() == null) {
                    metadata.setNeedsDatabase(false);
                }
                if (metadata.getDatabaseName() == null || metadata.getDatabaseName().trim().isEmpty()) {
                    metadata.setDatabaseName("test_db");
                }
                if (metadata.getDatabasePassword() == null || metadata.getDatabasePassword().trim().isEmpty()) {
                    metadata.setDatabasePassword("123456");
                }
                return metadata;
            }
        } catch (Exception e) {
            log.warn("读取实验元数据失败，使用默认值: {}", expId, e);
        }
        
        // 默认元数据
        ExperimentMetadata metadata = new ExperimentMetadata();
        metadata.setExpId(expId);
        metadata.setType("java");
        metadata.setBaseImage("java-base:latest");
        metadata.setStartCommand("java -jar /app/program/app.jar");
        metadata.setPort(8080);
        metadata.setNeedsDatabase(false);
        metadata.setDatabaseName("test_db");
        metadata.setDatabasePassword("123456");
        return metadata;
    }
    
    /**
     * 生成环境ID
     */
    private String generateEnvId() {
        return "env-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    /**
     * 转换为EnvInfo
     */
    private EnvInfo convertToEnvInfo(VirtualEnv env) {
        EnvInfo info = new EnvInfo();
        BeanUtils.copyProperties(env, info);
        info.setContainerName("env-" + env.getEnvId());
        return info;
    }
    
    /**
     * 清理资源（停止容器、释放端口）
     * 用于错误处理时的资源清理
     * @param envDir 环境目录
     * @param port 端口号
     */
    /**
     * 清理资源（容器、端口、目录）
     * @param envDir 环境目录
     * @param port 端口号
     * @param containerId 容器ID（可选，用于日志）
     */
    private void cleanupResources(String envDir, Integer port, String containerId) {
        log.info("开始清理资源: envDir={}, port={}, containerId={}", envDir, port, containerId);
        
        // 1. 停止并删除容器（如果存在）
        if (envDir != null && !envDir.isEmpty()) {
            try {
                // 先尝试停止容器（保留volume，因为可能只是启动失败）
                dockerOpsService.stopContainer(envDir, false);
                log.info("容器停止成功: envDir={}", envDir);
            } catch (Exception ex) {
                log.warn("停止容器失败: envDir={}", envDir, ex);
                // 继续清理其他资源
            }
        }
        
        // 2. 释放端口
        if (port != null) {
            try {
                portManagerService.releasePort(port);
                log.info("端口释放成功: port={}", port);
            } catch (Exception ex) {
                log.warn("释放端口失败: port={}", port, ex);
                // 继续清理其他资源
            }
        }
        
        // 3. 清理环境目录（可选，根据需求决定是否删除）
        // 注意：通常不删除目录，保留日志和配置文件用于排查问题
        // 如果需要完全清理，可以调用 fileManagerService.deleteDirectory()
        
        log.info("资源清理完成: envDir={}, port={}", envDir, port);
    }
}

