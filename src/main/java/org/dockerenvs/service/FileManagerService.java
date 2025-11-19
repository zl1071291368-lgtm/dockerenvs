package org.dockerenvs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 文件管理服务
 */
@Slf4j
@Service
public class FileManagerService {
    
    @Value("${env.apps.base-path:/opt/apps}")
    private String appsBasePath;
    
    @Value("${env.user-envs.base-path:/opt/user_envs}")
    private String userEnvsBasePath;
    
    /**
     * 生成环境目录
     */
    public String generateEnvDir(String userId, String systemId, String expId) {
        String envDir = String.format("%s/%s/%s/%s", 
            userEnvsBasePath, userId, systemId, expId);
        
        Path envPath = Paths.get(envDir);
        try {
            Files.createDirectories(envPath);
            Files.createDirectories(envPath.resolve("logs"));
            log.info("创建环境目录: {}", envDir);
        } catch (IOException e) {
            log.error("创建环境目录失败: {}", envDir, e);
            throw new RuntimeException("创建环境目录失败", e);
        }
        
        return envDir;
    }
    
    /**
     * 获取实验程序源目录（用于共享挂载）
     */
    public String getAppSourcePath(String expId) {
        Path sourcePath = Paths.get(appsBasePath, expId);
        if (!Files.exists(sourcePath)) {
            throw new RuntimeException("实验程序包不存在: " + sourcePath);
        }
        return sourcePath.toString();
    }
    
    /**
     * 删除目录（支持Windows系统，带重试机制）
     */
    public void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                // Windows系统可能需要重试删除
                int maxRetries = 3;
                int retryCount = 0;
                boolean deleted = false;
                
                while (retryCount < maxRetries && !deleted) {
                    try {
                        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                try {
                                    // Windows系统：确保文件可写
                                    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                                        try {
                                            java.nio.file.attribute.DosFileAttributeView dosView = 
                                                Files.getFileAttributeView(file, java.nio.file.attribute.DosFileAttributeView.class);
                                            if (dosView != null) {
                                                dosView.setReadOnly(false);
                                            }
                                        } catch (Exception e) {
                                            // 忽略属性设置失败
                                        }
                                    }
                                    Files.delete(file);
                                } catch (IOException e) {
                                    log.warn("删除文件失败: {}", file, e);
                                    throw e;
                                }
                                return FileVisitResult.CONTINUE;
                            }
                            
                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                if (exc != null) {
                                    throw exc;
                                }
                                try {
                                    Files.delete(dir);
                                } catch (IOException e) {
                                    log.warn("删除目录失败: {}", dir, e);
                                    throw e;
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                        deleted = true;
                        log.info("删除目录成功: {}", directory);
                    } catch (IOException e) {
                        retryCount++;
                        if (retryCount < maxRetries) {
                            log.warn("删除目录失败，第{}次重试: {}", retryCount, directory);
                            try {
                                Thread.sleep(500); // 等待500ms后重试
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("删除目录被中断", ie);
                            }
                        } else {
                            log.error("删除目录失败，已达到最大重试次数: {}", directory, e);
                            throw new RuntimeException("删除目录失败: " + directory, e);
                        }
                    }
                }
            } else {
                log.info("目录不存在，跳过删除: {}", directory);
            }
        } catch (Exception e) {
            log.error("删除目录异常: {}", directory, e);
            throw new RuntimeException("删除目录失败: " + directory, e);
        }
    }
    
    /**
     * 读取实验元数据
     */
    public String readExperimentMetadata(String expId) {
        String metadataPath = appsBasePath + "/" + expId + "/metadata.json";
        Path path = Paths.get(metadataPath);
        
        try {
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path));
            }
        } catch (IOException e) {
            log.warn("读取实验元数据失败: {}", metadataPath, e);
        }
        
        return null;
    }
}

