package org.dockerenvs.provider;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.ExperimentMetadata;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Node.js运行时策略
 */
@Slf4j
@Component
public class NodeRuntimeStrategy extends AbstractRuntimeStrategy {
    
    @Override
    public String getRuntimeType() {
        return "node";
    }
    
    @Override
    public Integer getDefaultContainerPort() {
        return 3000;
    }
    
    @Override
    public String getDefaultMountPath() {
        return "/app/program";
    }
    
    @Override
    public String getDefaultMountOptions() {
        return ""; // 可写挂载，允许安装依赖
    }
    
    @Override
    protected String getDefaultStartCommand() {
        return "cd /app/program && npm install --production && node server.js";
    }
    
    @Override
    public Map<String, String> getDefaultEnvironment(ExperimentMetadata metadata, Integer hostPort) {
        Map<String, String> env = new HashMap<>();
        env.put("APP_PORT", String.valueOf(hostPort));
        env.put("CONTAINER_PORT", String.valueOf(metadata.getEffectiveContainerPort()));
        env.put("NODE_ENV", "production");
        return env;
    }
}

