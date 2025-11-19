package org.dockerenvs.provider;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.ExperimentMetadata;
import org.dockerenvs.dto.HealthCheckConfig;
import org.dockerenvs.dto.VolumeConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nginx/Docker运行时策略（静态文件服务）
 */
@Slf4j
@Component
public class NginxRuntimeStrategy extends AbstractRuntimeStrategy {
    
    @Override
    public String getRuntimeType() {
        return "nginx";
    }
    
    @Override
    public Integer getDefaultContainerPort() {
        return 80;
    }
    
    @Override
    public String getDefaultMountPath() {
        return "/usr/share/nginx/html";
    }
    
    @Override
    public String getDefaultMountOptions() {
        return ":ro"; // 只读挂载
    }
    
    @Override
    protected String getDefaultStartCommand() {
        return "nginx -g 'daemon off;'";
    }
    
    @Override
    public HealthCheckConfig getDefaultHealthCheck(Integer containerPort) {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setTest("[\"CMD\", \"wget\", \"--quiet\", \"--tries=1\", \"--spider\", \"http://localhost/\"]");
        config.setInterval("30s");
        config.setTimeout("10s");
        config.setRetries(3);
        config.setStartPeriod("10s");
        return config;
    }
    
    @Override
    public List<VolumeConfig> getDefaultVolumes(String envDir, String programPath) {
        List<VolumeConfig> volumes = new ArrayList<>();
        
        VolumeConfig programVolume = new VolumeConfig();
        programVolume.setHostPath(programPath);
        programVolume.setContainerPath("/usr/share/nginx/html");
        programVolume.setOptions(":ro"); // 只读
        volumes.add(programVolume);
        
        return volumes;
    }
    
    @Override
    public Map<String, String> getDefaultEnvironment(ExperimentMetadata metadata, Integer hostPort) {
        return new HashMap<>();
    }
}

