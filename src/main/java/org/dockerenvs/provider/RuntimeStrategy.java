package org.dockerenvs.provider;

import org.dockerenvs.dto.ExperimentMetadata;
import org.dockerenvs.dto.HealthCheckConfig;
import org.dockerenvs.dto.VolumeConfig;

import java.util.List;
import java.util.Map;

/**
 * 运行时策略接口
 * 负责根据运行时类型设置默认配置
 */
public interface RuntimeStrategy {
    
    /**
     * 获取策略支持的运行时类型
     */
    String getRuntimeType();
    
    /**
     * 获取默认容器端口
     */
    Integer getDefaultContainerPort();
    
    /**
     * 获取默认挂载路径
     */
    String getDefaultMountPath();
    
    /**
     * 获取默认挂载选项（如:ro表示只读）
     */
    String getDefaultMountOptions();
    
    /**
     * 获取默认健康检查配置
     */
    HealthCheckConfig getDefaultHealthCheck(Integer containerPort);
    
    /**
     * 构建启动命令（处理环境变量替换等）
     */
    String buildStartCommand(String originalCommand, Map<String, String> variables);
    
    /**
     * 获取默认数据卷配置
     */
    List<VolumeConfig> getDefaultVolumes(String envDir, String programPath);
    
    /**
     * 获取默认环境变量
     */
    Map<String, String> getDefaultEnvironment(ExperimentMetadata metadata, Integer hostPort);

    /**
     * 是否为容器启用TTY
     */
    default boolean enableTty(ExperimentMetadata metadata) {
        return false;
    }

    /**
     * 是否保持stdin打开
     */
    default boolean enableStdinOpen(ExperimentMetadata metadata) {
        return false;
    }

    /**
     * 获取默认工作目录
     */
    default String getWorkingDirectory(ExperimentMetadata metadata) {
        return null;
    }
}

