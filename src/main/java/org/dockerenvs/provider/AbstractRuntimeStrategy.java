package org.dockerenvs.provider;

import org.dockerenvs.dto.HealthCheckConfig;
import org.dockerenvs.dto.VolumeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运行时策略抽象基类
 * 提供公共方法的默认实现
 */
public abstract class AbstractRuntimeStrategy implements RuntimeStrategy {
    
    /**
     * 构建启动命令（处理环境变量替换等）
     * 子类可以重写此方法以提供自定义的默认命令
     */
    @Override
    public String buildStartCommand(String originalCommand, Map<String, String> variables) {
        if (originalCommand == null || originalCommand.trim().isEmpty()) {
            return getDefaultStartCommand();
        }
        // 替换环境变量
        String command = originalCommand;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            command = command.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        // 使用sh -c执行命令，支持复杂命令
        return "sh -c \"" + command.replace("\"", "\\\"") + "\"";
    }
    
    /**
     * 获取默认启动命令（当originalCommand为空时使用）
     * 子类需要实现此方法
     */
    protected abstract String getDefaultStartCommand();
    
    /**
     * 获取默认数据卷配置（程序目录和日志目录）
     * 子类可以重写此方法以提供自定义的卷配置
     */
    @Override
    public List<VolumeConfig> getDefaultVolumes(String envDir, String programPath) {
        List<VolumeConfig> volumes = new ArrayList<>();
        
        // 程序目录
        VolumeConfig programVolume = new VolumeConfig();
        programVolume.setHostPath(programPath);
        programVolume.setContainerPath(getDefaultMountPath());
        programVolume.setOptions(getDefaultMountOptions());
        volumes.add(programVolume);
        
        // 日志目录（如果挂载路径不是只读，则添加日志目录）
        if (getDefaultMountOptions().isEmpty()) {
            VolumeConfig logVolume = new VolumeConfig();
            logVolume.setHostPath(envDir + "/logs");
            logVolume.setContainerPath("/app/logs");
            logVolume.setOptions(""); // 可写
            volumes.add(logVolume);
        }
        
        return volumes;
    }
    
    /**
     * 获取默认健康检查配置（基于curl的HTTP检查）
     * 子类可以重写此方法以提供自定义的健康检查
     */
    @Override
    public HealthCheckConfig getDefaultHealthCheck(Integer containerPort) {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setTest(String.format("[\"CMD\", \"curl\", \"-f\", \"http://localhost:%d/health\"]", containerPort));
        config.setInterval("30s");
        config.setTimeout("10s");
        config.setRetries(3);
        config.setStartPeriod("40s");
        return config;
    }
}

