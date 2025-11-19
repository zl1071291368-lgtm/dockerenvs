package org.dockerenvs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 共享MySQL容器管理服务
 * 负责创建和管理一个全局共享的MySQL容器，供所有实验环境使用
 */
@Slf4j
@Service
public class SharedMysqlService {
    
    private static final String SHARED_MYSQL_CONTAINER_NAME = "shared-mysql";
    private static final String SHARED_MYSQL_NETWORK_NAME = "shared-mysql-net";
    private static final String SHARED_MYSQL_VOLUME_NAME = "shared-mysql-data";
    private static final String MYSQL_IMAGE = "mysql:8.0";
    
    @Value("${shared.mysql.root.password:123456}")
    private String rootPassword;
    
    @Value("${shared.mysql.auto-create:false}")
    private boolean autoCreate;
    
    @Autowired
    private DockerOpsService dockerOpsService;
    
    /**
     * 检查共享MySQL容器是否可用（不自动创建）
     * 用于环境创建时的检查，确保数据库容器已就绪
     * 
     * @throws RuntimeException 如果容器不存在或不可用
     */
    public void checkSharedMysqlAvailable() {
        log.info("检查共享MySQL容器是否可用...");
        
        // 检查容器是否存在
        if (!dockerOpsService.containerExistsByName(SHARED_MYSQL_CONTAINER_NAME)) {
            throw new RuntimeException(
                "共享MySQL容器不存在。请先通过管理界面创建共享数据库容器。\n" +
                "访问 /shared-mysql.html 进行管理，或调用 POST /api/shared-mysql/ensure 接口。"
            );
        }
        
        // 检查容器是否运行
        if (!dockerOpsService.isContainerRunningByName(SHARED_MYSQL_CONTAINER_NAME)) {
            throw new RuntimeException(
                "共享MySQL容器已停止。请先启动容器。\n" +
                "访问 /shared-mysql.html 进行管理，或调用 POST /api/shared-mysql/ensure 接口。"
            );
        }
        
        // 验证MySQL是否真的可用
        if (!isMysqlReady()) {
            // MySQL可能正在启动中，先等待一段时间再重试
            log.info("MySQL未就绪，等待10秒后重试...");
            waitForMysqlReady(10);
            
            // 重试检查
            if (!isMysqlReady()) {
                throw new RuntimeException(
                    "共享MySQL容器运行但MySQL服务未就绪。请检查容器日志或稍后重试。\n" +
                    "访问 /shared-mysql.html 查看详细状态。"
                );
            }
        }
        
        log.info("共享MySQL容器可用");
    }
    
    /**
     * 确保共享MySQL容器存在并运行
     * 如果不存在则创建，如果已停止则启动
     * 注意：此方法会根据配置决定是否自动创建容器
     */
    public void ensureSharedMysqlExists() {
        log.info("检查共享MySQL容器是否存在...");
        
        // 检查容器是否存在
        if (dockerOpsService.containerExistsByName(SHARED_MYSQL_CONTAINER_NAME)) {
            log.info("共享MySQL容器已存在: {}", SHARED_MYSQL_CONTAINER_NAME);
            // 检查容器是否运行
            if (dockerOpsService.isContainerRunningByName(SHARED_MYSQL_CONTAINER_NAME)) {
                log.info("共享MySQL容器正在运行");
                // 验证MySQL是否真的可用
                if (isMysqlReady()) {
                    log.info("共享MySQL容器运行正常");
                    return;
                } else {
                    // MySQL可能正在启动中，先等待一段时间再重试
                    log.info("MySQL未就绪，等待10秒后重试...");
                    waitForMysqlReady(10);
                    
                    // 重试检查
                    if (isMysqlReady()) {
                        log.info("MySQL已就绪（重试后）");
                        return;
                    } else {
                        // 重试后仍未就绪，说明可能真的有问题，需要重启
                        log.warn("共享MySQL容器运行但MySQL未就绪（重试后仍失败），尝试重启...");
                        removeContainer(SHARED_MYSQL_CONTAINER_NAME);
                        createSharedMysql();
                        return;
                    }
                }
            } else {
                log.info("共享MySQL容器已停止，正在启动...");
                startContainer(SHARED_MYSQL_CONTAINER_NAME);
                return;
            }
        }
        
        // 容器不存在，根据配置决定是否自动创建
        if (autoCreate) {
            log.info("共享MySQL容器不存在，根据配置自动创建...");
            createSharedMysql();
        } else {
            throw new RuntimeException(
                "共享MySQL容器不存在，且自动创建已禁用。\n" +
                "请先通过管理界面创建共享数据库容器：\n" +
                "1. 访问 /shared-mysql.html 进行管理\n" +
                "2. 或调用 POST /api/shared-mysql/ensure 接口\n" +
                "3. 或在配置文件中设置 shared.mysql.auto-create=true 启用自动创建"
            );
        }
    }
    
    /**
     * 创建共享MySQL容器
     */
    private void createSharedMysql() {
        try {
            // 1. 创建网络（如果不存在）
            createNetworkIfNotExists(SHARED_MYSQL_NETWORK_NAME);
            
            // 2. 创建MySQL容器
            // 注意：不映射端口到主机，因为应用容器通过Docker网络直接连接
            // 这样可以避免与主机上可能运行的MySQL服务冲突
            // 显式配置MySQL监听所有接口的3306端口
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", SHARED_MYSQL_CONTAINER_NAME,
                "--network", SHARED_MYSQL_NETWORK_NAME,
                "--restart", "unless-stopped",
                "-e", "MYSQL_ROOT_PASSWORD=" + rootPassword,
                "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=no",
                "-v", SHARED_MYSQL_VOLUME_NAME + ":/var/lib/mysql",
                MYSQL_IMAGE,
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_unicode_ci",
                "--default-authentication-plugin=mysql_native_password",
                "--bind-address=0.0.0.0",
                "--port=3306"
            );
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("Docker: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // 读取错误输出
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    StringBuilder errorOutput = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    log.error("创建共享MySQL容器失败，退出码: {}, 错误: {}", exitCode, errorOutput.toString());
                }
                throw new RuntimeException("创建共享MySQL容器失败，退出码: " + exitCode);
            }
            
            log.info("共享MySQL容器创建成功: {}", SHARED_MYSQL_CONTAINER_NAME);
            
            // 等待MySQL启动
            waitForMysqlReady(30);
            
        } catch (IOException | InterruptedException e) {
            log.error("创建共享MySQL容器失败", e);
            throw new RuntimeException("创建共享MySQL容器失败", e);
        }
    }
    
    /**
     * 确保数据库存在，如果不存在则创建
     */
    public void ensureDatabaseExists(String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            log.warn("数据库名称为空，跳过创建");
            return;
        }
        
        try {
            log.info("检查数据库是否存在: {}", databaseName);
            
            // 检查数据库是否存在
            ProcessBuilder checkBuilder = new ProcessBuilder(
                "docker", "exec", SHARED_MYSQL_CONTAINER_NAME,
                "mysql", "-uroot", "-p" + rootPassword, "-e",
                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '" + databaseName + "';",
                "-h", "localhost", "-s", "-N"
            );
            Process checkProcess = checkBuilder.start();
            
            StringBuilder checkOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(checkProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    checkOutput.append(line);
                }
            }
            checkProcess.waitFor();
            
            // 如果数据库已存在
            if (checkOutput.toString().trim().equals(databaseName)) {
                log.info("数据库已存在: {}", databaseName);
                return;
            }
            
            // 数据库不存在，创建它
            log.info("数据库不存在，正在创建: {}", databaseName);
            ProcessBuilder createBuilder = new ProcessBuilder(
                "docker", "exec", SHARED_MYSQL_CONTAINER_NAME,
                "mysql", "-uroot", "-p" + rootPassword, "-e",
                "CREATE DATABASE IF NOT EXISTS `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
                "-h", "localhost"
            );
            Process createProcess = createBuilder.start();
            
            int exitCode = createProcess.waitFor();
            if (exitCode != 0) {
                // 读取错误输出
                StringBuilder errorOutput = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(createProcess.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                }
                log.error("创建数据库失败: {}, 退出码: {}, 错误: {}", databaseName, exitCode, errorOutput.toString());
                throw new RuntimeException("创建数据库失败: " + databaseName);
            }
            
            log.info("数据库创建成功: {}", databaseName);
            
        } catch (Exception e) {
            log.error("确保数据库存在失败: {}", databaseName, e);
            throw new RuntimeException("确保数据库存在失败: " + databaseName, e);
        }
    }
    
    /**
     * 创建网络（如果不存在）
     */
    private void createNetworkIfNotExists(String networkName) {
        try {
            // 检查网络是否存在
            ProcessBuilder checkBuilder = new ProcessBuilder(
                "docker", "network", "ls", "--filter", "name=" + networkName, "--format", "{{.Name}}"
            );
            Process checkProcess = checkBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(checkProcess.getInputStream()))) {
                String result = reader.readLine();
                checkProcess.waitFor();
                
                if (result != null && result.trim().equals(networkName)) {
                    log.info("网络已存在: {}", networkName);
                    return;
                }
            }
            
            // 网络不存在，创建它
            log.info("创建网络: {}", networkName);
            ProcessBuilder createBuilder = new ProcessBuilder(
                "docker", "network", "create", networkName
            );
            Process createProcess = createBuilder.start();
            
            int exitCode = createProcess.waitFor();
            if (exitCode != 0) {
                log.error("创建网络失败: {}, 退出码: {}", networkName, exitCode);
                throw new RuntimeException("创建网络失败: " + networkName);
            }
            
            log.info("网络创建成功: {}", networkName);
            
        } catch (IOException | InterruptedException e) {
            log.error("创建网络失败: {}", networkName, e);
            throw new RuntimeException("创建网络失败: " + networkName, e);
        }
    }
    
    /**
     * 启动容器
     */
    private void startContainer(String containerName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "start", containerName
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // 读取输出和错误
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("Docker start: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = output.toString();
                log.error("启动容器失败: {}, 退出码: {}, 错误: {}", containerName, exitCode, errorMsg);
                
                // 如果启动失败，尝试删除容器并重新创建
                log.warn("容器启动失败，尝试删除并重新创建: {}", containerName);
                removeContainer(containerName);
                createSharedMysql();
                return;
            }
            
            log.info("容器启动成功: {}", containerName);
            
            // 等待MySQL启动
            waitForMysqlReady(30);
            
        } catch (IOException | InterruptedException e) {
            log.error("启动容器失败: {}", containerName, e);
            // 如果启动失败，尝试删除容器并重新创建
            try {
                log.warn("容器启动异常，尝试删除并重新创建: {}", containerName);
                removeContainer(containerName);
                createSharedMysql();
            } catch (Exception ex) {
                log.error("删除并重新创建容器失败", ex);
                throw new RuntimeException("启动容器失败: " + containerName, e);
            }
        }
    }
    
    /**
     * 删除容器
     */
    private void removeContainer(String containerName) {
        try {
            log.info("删除容器: {}", containerName);
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "rm", "-f", containerName
            );
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("删除容器失败: {}, 退出码: {}", containerName, exitCode);
            } else {
                log.info("容器删除成功: {}", containerName);
            }
        } catch (Exception e) {
            log.warn("删除容器异常: {}", containerName, e);
        }
    }
    
    /**
     * 等待MySQL就绪
     */
    private void waitForMysqlReady(int timeoutSeconds) {
        log.info("等待MySQL就绪...");
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            if (isMysqlReady()) {
                log.info("MySQL已就绪");
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        log.warn("MySQL就绪检查超时");
    }
    
    /**
     * 检查MySQL是否就绪
     */
    private boolean isMysqlReady() {
        try {
            // 使用 mysqladmin ping 检查MySQL服务是否响应
            // 这是最可靠的检查方式，如果 ping 成功说明 MySQL 已经就绪
            ProcessBuilder pingBuilder = new ProcessBuilder(
                "docker", "exec", SHARED_MYSQL_CONTAINER_NAME,
                "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p" + rootPassword
            );
            // 合并错误流到标准输出，避免错误信息干扰
            pingBuilder.redirectErrorStream(true);
            Process pingProcess = pingBuilder.start();
            
            // 读取输出（包括错误输出）
            StringBuilder pingOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pingProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    pingOutput.append(line).append("\n");
                }
            }
            
            int pingExitCode = pingProcess.waitFor();
            
            if (pingExitCode != 0) {
                log.debug("MySQL ping 失败，退出码: {}, 输出: {}", pingExitCode, pingOutput.toString());
                return false;
            }
            
            // mysqladmin ping 成功说明 MySQL 已经就绪
            log.debug("MySQL已就绪（mysqladmin ping 成功）");
            return true;
            
        } catch (Exception e) {
            log.debug("检查MySQL就绪状态失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证MySQL容器初始化是否成功
     * 返回详细的验证信息
     */
    public String verifyMysqlInitialization() {
        StringBuilder result = new StringBuilder();
        
        try {
            // 1. 检查容器是否存在
            if (!dockerOpsService.containerExistsByName(SHARED_MYSQL_CONTAINER_NAME)) {
                return "❌ 容器不存在: " + SHARED_MYSQL_CONTAINER_NAME;
            }
            result.append("✅ 容器存在: ").append(SHARED_MYSQL_CONTAINER_NAME).append("\n");
            
            // 2. 检查容器是否运行
            if (!dockerOpsService.isContainerRunningByName(SHARED_MYSQL_CONTAINER_NAME)) {
                return result.toString() + "❌ 容器未运行";
            }
            result.append("✅ 容器正在运行\n");
            
            // 3. 检查网络
            ProcessBuilder networkBuilder = new ProcessBuilder(
                "docker", "network", "inspect", SHARED_MYSQL_NETWORK_NAME, "--format", "{{.Name}}"
            );
            Process networkProcess = networkBuilder.start();
            int networkExitCode = networkProcess.waitFor();
            if (networkExitCode == 0) {
                result.append("✅ 网络存在: ").append(SHARED_MYSQL_NETWORK_NAME).append("\n");
            } else {
                result.append("❌ 网络不存在: ").append(SHARED_MYSQL_NETWORK_NAME).append("\n");
            }
            
            // 4. 检查MySQL服务
            if (isMysqlReady()) {
                result.append("✅ MySQL服务就绪\n");
            } else {
                result.append("❌ MySQL服务未就绪\n");
            }
            
            // 5. 检查端口监听（通过MySQL命令）
            ProcessBuilder portBuilder = new ProcessBuilder(
                "docker", "exec", SHARED_MYSQL_CONTAINER_NAME,
                "mysql", "-uroot", "-p" + rootPassword, "-e", "SHOW VARIABLES LIKE 'port';", "-h", "localhost", "-s", "-N"
            );
            Process portProcess = portBuilder.start();
            StringBuilder portOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(portProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    portOutput.append(line);
                }
            }
            portProcess.waitFor();
            
            if (portOutput.toString().contains("3306")) {
                result.append("✅ MySQL监听端口: 3306\n");
            } else {
                result.append("⚠️  无法确认端口配置\n");
            }
            
            // 6. 测试数据库连接
            ProcessBuilder testBuilder = new ProcessBuilder(
                "docker", "exec", SHARED_MYSQL_CONTAINER_NAME,
                "mysql", "-uroot", "-p" + rootPassword, "-e", "SELECT 1;", "-h", "localhost"
            );
            Process testProcess = testBuilder.start();
            int testExitCode = testProcess.waitFor();
            if (testExitCode == 0) {
                result.append("✅ 数据库连接测试成功\n");
            } else {
                result.append("❌ 数据库连接测试失败\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            return result.toString() + "❌ 验证过程出错: " + e.getMessage();
        }
    }
    
    /**
     * 获取共享MySQL的网络名称
     */
    public String getNetworkName() {
        return SHARED_MYSQL_NETWORK_NAME;
    }
    
    /**
     * 获取共享MySQL的容器名称（作为主机名）
     */
    public String getContainerName() {
        return SHARED_MYSQL_CONTAINER_NAME;
    }
    
    /**
     * 获取MySQL根密码
     */
    public String getRootPassword() {
        return rootPassword;
    }
    
    /**
     * 获取共享MySQL的volume名称
     */
    public String getVolumeName() {
        return SHARED_MYSQL_VOLUME_NAME;
    }
    
    /**
     * 停止共享MySQL容器（但不删除容器和volume）
     */
    public void stopSharedMysql() {
        if (!dockerOpsService.containerExistsByName(SHARED_MYSQL_CONTAINER_NAME)) {
            log.info("共享MySQL容器不存在，无需停止");
            return;
        }
        
        if (!dockerOpsService.isContainerRunningByName(SHARED_MYSQL_CONTAINER_NAME)) {
            log.info("共享MySQL容器已停止");
            return;
        }
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "stop", SHARED_MYSQL_CONTAINER_NAME
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("Docker stop: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("共享MySQL容器已停止");
            } else {
                log.warn("停止共享MySQL容器失败，退出码: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("停止共享MySQL容器失败", e);
            throw new RuntimeException("停止共享MySQL容器失败", e);
        }
    }
    
    /**
     * 删除共享MySQL容器和volume（完全清理）
     * 注意：此操作会删除所有数据，请谨慎使用
     */
    public void destroySharedMysql() {
        log.warn("开始删除共享MySQL容器和volume，这将删除所有数据！");
        
        // 1. 停止并删除容器
        if (dockerOpsService.containerExistsByName(SHARED_MYSQL_CONTAINER_NAME)) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "rm", "-f", "-v", SHARED_MYSQL_CONTAINER_NAME
                );
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("Docker rm: {}", line);
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("共享MySQL容器已删除");
                } else {
                    log.warn("删除共享MySQL容器失败，退出码: {}", exitCode);
                }
            } catch (Exception e) {
                log.error("删除共享MySQL容器失败", e);
            }
        }
        
        // 2. 删除volume（如果容器删除时没有自动删除）
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "volume", "rm", SHARED_MYSQL_VOLUME_NAME
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("Docker volume rm: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("共享MySQL volume已删除: {}", SHARED_MYSQL_VOLUME_NAME);
            } else {
                // volume可能不存在或已被删除，这是正常的
                log.debug("删除共享MySQL volume失败或volume不存在，退出码: {}", exitCode);
            }
        } catch (Exception e) {
            log.warn("删除共享MySQL volume失败: {}", e.getMessage());
        }
        
        // 3. 删除网络（可选，因为网络可能被其他容器使用）
        // 这里不删除网络，因为可能有其他容器在使用
        
        log.info("共享MySQL容器和volume删除完成");
    }
}

