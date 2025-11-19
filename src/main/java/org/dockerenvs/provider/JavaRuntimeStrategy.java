package org.dockerenvs.provider;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.ExperimentMetadata;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Java运行时策略
 */
@Slf4j
@Component
public class JavaRuntimeStrategy extends AbstractRuntimeStrategy {
    
    @Override
    public String getRuntimeType() {
        return "java";
    }
    
    @Override
    public Integer getDefaultContainerPort() {
        return 8080;
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
        return "java -jar /app/program/app.jar";
    }
    
    @Override
    public Map<String, String> getDefaultEnvironment(ExperimentMetadata metadata, Integer hostPort) {
        Map<String, String> env = new HashMap<>();
        env.put("APP_PORT", String.valueOf(hostPort));
        env.put("CONTAINER_PORT", String.valueOf(metadata.getEffectiveContainerPort()));
        env.put("USER_ID", ""); // 由调用方设置
        env.put("EXP_ID", metadata.getExpId());
        env.put("ENV_ID", ""); // 由调用方设置
        return env;
    }
}

