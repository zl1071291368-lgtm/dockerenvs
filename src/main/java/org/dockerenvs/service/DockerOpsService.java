package org.dockerenvs.service;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.exception.ContainerException;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Docker操作服务
 */
@Slf4j
@Service
public class DockerOpsService {
    
    /**
     * 检查 Docker 是否可用
     */
    public boolean isDockerAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "ps");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("检查 Docker 可用性失败", e);
            return false;
        }
    }
    
    /**
     * 解析 Docker 错误信息，提供友好的错误提示
     */
    private String parseDockerError(String errorMsg) {
        if (errorMsg == null) {
            return "未知错误";
        }
        
        String lowerError = errorMsg.toLowerCase();
        
        // Docker Desktop 未启动
        if (lowerError.contains("dockerdesktoplinuxengine") || 
            lowerError.contains("cannot find the file specified") ||
            lowerError.contains("connection refused") ||
            lowerError.contains("error during connect")) {
            return "Docker Desktop 未启动或 Docker 守护进程未运行。\n" +
                   "请执行以下操作：\n" +
                   "1. 启动 Docker Desktop 应用程序\n" +
                   "2. 等待 Docker Desktop 完全启动（系统托盘图标不再显示启动动画）\n" +
                   "3. 在命令行运行 'docker ps' 验证 Docker 是否正常工作\n" +
                   "4. 然后重试创建环境\n\n" +
                   "原始错误: " + errorMsg;
        }
        
        // Docker 镜像不存在
        if (lowerError.contains("unable to get image") || 
            lowerError.contains("pull access denied") ||
            lowerError.contains("image not found")) {
            return "Docker 镜像不存在或无法拉取。\n" +
                   "请检查：\n" +
                   "1. 网络连接是否正常\n" +
                   "2. Docker Hub 是否可以访问\n" +
                   "3. 镜像名称是否正确\n\n" +
                   "原始错误: " + errorMsg;
        }
        
        // 端口被占用
        if (lowerError.contains("port is already allocated") || 
            lowerError.contains("bind: address already in use")) {
            return "端口已被占用。\n" +
                   "请检查：\n" +
                   "1. 是否有其他容器正在使用该端口\n" +
                   "2. 是否有其他应用程序占用了该端口\n\n" +
                   "原始错误: " + errorMsg;
        }
        
        // 权限问题
        if (lowerError.contains("permission denied") || 
            lowerError.contains("access denied")) {
            return "Docker 权限不足。\n" +
                   "请检查：\n" +
                   "1. 当前用户是否有权限访问 Docker\n" +
                   "2. 是否以管理员权限运行\n\n" +
                   "原始错误: " + errorMsg;
        }
        
        return errorMsg;
    }
    
    /**
     * 启动容器（使用docker-compose）
     */
    public String startContainer(String envDir) {
        return startContainer(envDir, true);
    }

    /**
     * 启动容器（使用docker-compose）
     * @param envDir 环境目录
     * @param waitForHealthy 是否等待健康检查完成
     */
    /**
     * 启动容器（使用docker-compose）
     * @param envDir 环境目录
     * @param waitForHealthy 是否等待健康检查完成
     */
    public String startContainer(String envDir, boolean waitForHealthy) {
        // 先检查 Docker 是否可用
        if (!isDockerAvailable()) {
            String errorMsg = "Docker 不可用。请确保 Docker Desktop 已启动并正在运行。";
            log.error(errorMsg);
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                parseDockerError(errorMsg));
        }
        
        try {
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            Path composeDir = Paths.get(envDir);
            String projectName = extractProjectNameFromEnvDir(envDir);
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "-f", composeFile.toString(), 
                "-p", projectName, "up", "-d"
            );
            processBuilder.directory(composeDir.toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("Docker Compose: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = output.toString();
                log.error("Docker Compose启动失败，退出码: {}, 输出: {}", exitCode, errorMsg);
                String friendlyError = parseDockerError(errorMsg);
                throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                    "启动容器失败，退出码: " + exitCode + "\n" + friendlyError);
            }
            
            // 获取容器ID（应用容器）
            String containerId = getContainerId(envDir);
            if (containerId == null || containerId.trim().isEmpty()) {
                log.error("无法获取容器ID，Docker Compose输出: {}", output.toString());
                throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                    "无法获取容器ID，请检查Docker Compose输出: " + output.toString());
            }
            
            log.info("容器启动成功，容器ID: {}", containerId);
            
            if (waitForHealthy) {
                // 等待容器健康检查
                waitForContainerHealthy(containerId, 30);
            } else {
                log.info("跳过健康检查等待: containerId={}", containerId);
            }
            
            return containerId;
            
        } catch (ContainerException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("启动容器失败", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                "启动容器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 停止容器
     * @param envDir 环境目录
     * @param removeVolumes 是否删除命名volume（true=删除，false=保留）
     */
    public void stopContainer(String envDir, boolean removeVolumes) {
        try {
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            Path composeDir = Paths.get(envDir);
            
            String projectName = extractProjectNameFromEnvDir(envDir);
            
            ProcessBuilder processBuilder;
            if (removeVolumes) {
                // 删除容器和命名volume（只删除项目相关的volume，不会删除共享数据库的volume）
                // 因为共享数据库是独立创建的，不在docker-compose项目中
                processBuilder = new ProcessBuilder(
                    "docker", "compose", "-f", composeFile.toString(), 
                    "-p", projectName, "down", "-v"
                );
                log.info("停止容器并删除项目相关的volume: envDir={}, projectName={}", envDir, projectName);
                log.debug("注意：共享数据库的volume（shared-mysql-data）不会被删除，因为它是独立管理的");
            } else {
                // 只删除容器，保留volume
                processBuilder = new ProcessBuilder(
                    "docker", "compose", "-f", composeFile.toString(), 
                    "-p", projectName, "down"
                );
                log.info("停止容器（保留volume）: envDir={}", envDir);
            }
            
            processBuilder.directory(composeDir.toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("Docker Compose: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("停止容器可能失败，退出码: {}, 输出: {}", exitCode, output.toString());
            } else {
                log.info("容器停止成功");
            }
            
        } catch (IOException | InterruptedException e) {
            log.error("停止容器失败", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ContainerException(ContainerException.ERROR_CODE_STOP_FAILED,
                "停止容器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 停止容器（保留volume，向后兼容）
     */
    public void stopContainer(String envDir) {
        stopContainer(envDir, false);
    }
    
    /**
     * 只停止容器，不删除（用于停止/启动操作）
     * @param envDir 环境目录
     */
    public void stopContainerOnly(String envDir) {
        try {
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            Path composeDir = Paths.get(envDir);
            
            String projectName = extractProjectNameFromEnvDir(envDir);
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "-f", composeFile.toString(), 
                "-p", projectName, "stop"
            );
            processBuilder.directory(composeDir.toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("Docker Compose: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("停止容器可能失败，退出码: {}, 输出: {}", exitCode, output.toString());
            } else {
                log.info("容器停止成功（保留容器）");
            }
            
        } catch (IOException | InterruptedException e) {
            log.error("停止容器失败", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ContainerException(ContainerException.ERROR_CODE_STOP_FAILED,
                "停止容器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 启动已存在的容器（用于停止/启动操作）
     * @param envDir 环境目录
     * @return 容器ID
     */
    public String startContainerOnly(String envDir) {
        if (!isDockerAvailable()) {
            String errorMsg = "Docker 不可用。请确保 Docker Desktop 已启动并正在运行。";
            log.error(errorMsg);
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                parseDockerError(errorMsg));
        }
        
        try {
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            Path composeDir = Paths.get(envDir);
            
            String projectName = extractProjectNameFromEnvDir(envDir);
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "-f", composeFile.toString(), 
                "-p", projectName, "start"
            );
            processBuilder.directory(composeDir.toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("Docker Compose: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = output.toString();
                log.error("Docker Compose启动失败，退出码: {}, 输出: {}", exitCode, errorMsg);
                String friendlyError = parseDockerError(errorMsg);
                throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                    "启动容器失败，退出码: " + exitCode + "\n" + friendlyError);
            }
            
            // 获取容器ID
            String containerId = getContainerId(envDir);
            if (containerId == null || containerId.trim().isEmpty()) {
                log.error("无法获取容器ID，Docker Compose输出: {}", output.toString());
                throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                    "无法获取容器ID，请检查Docker Compose输出: " + output.toString());
            }
            
            log.info("容器启动成功（使用已存在的容器），容器ID: {}", containerId);
            
            // 验证容器是否真的存在
            if (!containerExists(containerId)) {
                log.error("容器启动后不存在: containerId={}", containerId);
                throw new ContainerException(ContainerException.ERROR_CODE_NOT_FOUND,
                    "容器启动后不存在，容器ID: " + containerId);
            }
            
            return containerId;
            
        } catch (ContainerException e) {
            // 直接抛出容器异常
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("启动容器失败", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ContainerException(ContainerException.ERROR_CODE_START_FAILED,
                "启动容器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取容器ID（返回第一个容器，通常是应用容器）
     */
    private String getContainerId(String envDir) {
        try {
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            Path composeDir = Paths.get(envDir);
            
            String projectName = extractProjectNameFromEnvDir(envDir);
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "-f", composeFile.toString(), 
                "-p", projectName, "ps", "-q"
            );
            processBuilder.directory(composeDir.toFile());
            
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String containerId = reader.readLine();
                if (containerId != null && !containerId.trim().isEmpty()) {
                    return containerId.trim();
                }
            }
            
            process.waitFor();
            return null;
            
        } catch (Exception e) {
            log.warn("获取容器ID失败", e);
            return null;
        }
    }
    
    
    /**
     * 等待容器健康
     */
    private void waitForContainerHealthy(String containerId, int timeoutSeconds) {
        if (containerId == null) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            if (isContainerHealthy(containerId)) {
                log.info("容器健康检查通过: {}", containerId);
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("容器健康检查超时: {}", containerId);
    }
    
    /**
     * 检查容器是否健康
     */
    private boolean isContainerHealthy(String containerId) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "inspect", "--format", "{{.State.Health.Status}}", containerId
            );
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String status = reader.readLine();
                process.waitFor();
                return "healthy".equals(status) || "starting".equals(status);
            }
        } catch (Exception e) {
            // 如果没有健康检查配置，检查容器是否运行
            return isContainerRunning(containerId);
        }
    }
    
    /**
     * 检查容器是否运行
     */
    private boolean isContainerRunning(String containerId) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "--filter", "id=" + containerId, "--format", "{{.ID}}"
            );
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String id = reader.readLine();
                process.waitFor();
                return id != null && !id.trim().isEmpty();
            }
        } catch (Exception e) {
            log.warn("检查容器运行状态失败", e);
            return false;
        }
    }
    
    /**
     * 检查容器是否存在（通过容器ID）
     */
    public boolean containerExists(String containerId) {
        if (containerId == null) {
            return false;
        }
        return isContainerRunning(containerId);
    }
    
    /**
     * 检查容器是否存在（通过容器名称）
     */
    public boolean containerExistsByName(String containerName) {
        if (containerName == null || containerName.trim().isEmpty()) {
            return false;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "-a", "--filter", "name=" + containerName, "--format", "{{.Names}}"
            );
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                process.waitFor();
                return result != null && result.trim().equals(containerName);
            }
        } catch (Exception e) {
            log.warn("检查容器是否存在失败: {}", containerName, e);
            return false;
        }
    }
    
    /**
     * 检查容器是否运行（通过容器名称）
     */
    public boolean isContainerRunningByName(String containerName) {
        if (containerName == null || containerName.trim().isEmpty()) {
            return false;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}"
            );
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                process.waitFor();
                return result != null && result.trim().equals(containerName);
            }
        } catch (Exception e) {
            log.warn("检查容器运行状态失败: {}", containerName, e);
            return false;
        }
    }
    
    /**
     * 从环境目录中提取项目名称（envId）
     * 优先从 docker-compose.yml 中解析 container_name，确保每个环境都有唯一的项目名称
     * Docker Compose 项目名称只能包含小写字母、数字、下划线和连字符
     * 
     * 重要：项目名称必须唯一，否则会导致不同环境的容器互相覆盖
     */
    private String extractProjectNameFromEnvDir(String envDir) {
        // 方案1：从 docker-compose.yml 解析 container_name
        try {
            Path composeFile = Paths.get(envDir, "docker-compose.yml");
            if (Files.exists(composeFile)) {
                String containerName = extractContainerNameFromCompose(composeFile);
                if (containerName != null && containerName.startsWith("env-")) {
                    // container_name 格式是 "env-{envId}"，提取 envId 作为项目名称
                    String projectName = containerName.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                    log.debug("从 docker-compose.yml 解析 container_name 作为项目名称: {}", projectName);
                    return projectName;
                }
            }
        } catch (Exception e) {
            log.warn("从 docker-compose.yml 解析失败，尝试后备方案: {}", envDir, e);
        }
        
        // 方案2：从 .env 文件读取（向后兼容，如果存在）
        try {
            Path envFile = Paths.get(envDir, ".env");
            if (Files.exists(envFile)) {
                List<String> lines = Files.readAllLines(envFile);
                for (String line : lines) {
                    if (line.startsWith("ENV_ID=")) {
                        String envId = line.substring("ENV_ID=".length()).trim();
                        if (!envId.isEmpty()) {
                            String projectName = "env-" + envId.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                            log.debug("从 .env 文件读取 ENV_ID 作为项目名称: {}", projectName);
                            return projectName;
                        }
                    }
                    if (line.startsWith("CONTAINER_NAME=")) {
                        String containerName = line.substring("CONTAINER_NAME=".length()).trim();
                        if (containerName.startsWith("env-")) {
                            String projectName = containerName.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                            log.debug("从 .env 文件读取 CONTAINER_NAME 作为项目名称: {}", projectName);
                            return projectName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 .env 文件失败: {}", envDir, e);
        }
        
        // 方案3：使用完整路径的hash值确保唯一性（最后的后备方案）
        try {
            Path path = Paths.get(envDir);
            String absolutePath = path.toAbsolutePath().toString();
            int hashCode = absolutePath.hashCode();
            String uniqueName = "env_" + Math.abs(hashCode);
            log.warn("无法从文件提取项目名称，使用路径hash值: {} (路径: {})", uniqueName, absolutePath);
            return uniqueName.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        } catch (Exception e) {
            log.error("从目录路径提取项目名称失败: {}", envDir, e);
            // 最后的后备方案：使用UUID确保唯一性
            String fallbackName = "env_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.error("使用UUID作为后备项目名称: {}", fallbackName);
            return fallbackName;
        }
    }
    
    /**
     * 从 docker-compose.yml 文件中提取 container_name
     */
    private String extractContainerNameFromCompose(Path composeFile) {
        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> compose = yaml.load(Files.newInputStream(composeFile));
            
            if (compose != null && compose.containsKey("services")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> services = (Map<String, Object>) compose.get("services");
                if (services != null && !services.isEmpty()) {
                    // 获取第一个服务（通常是 "app"）
                    Object firstService = services.values().iterator().next();
                    if (firstService instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> service = (Map<String, Object>) firstService;
                        Object containerName = service.get("container_name");
                        if (containerName instanceof String) {
                            return (String) containerName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 docker-compose.yml 失败: {}", composeFile, e);
        }
        return null;
    }
}

