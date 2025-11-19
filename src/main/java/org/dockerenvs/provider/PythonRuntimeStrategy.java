package org.dockerenvs.provider;

import org.dockerenvs.dto.ExperimentMetadata;
import org.dockerenvs.dto.HealthCheckConfig;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Python CLI 运行策略
 */
@Component
public class PythonRuntimeStrategy extends AbstractRuntimeStrategy {

    private static final String WORK_DIR = "/app/program";
    private static final String KEEP_ALIVE_COMMAND = "sh -c \"tail -f /dev/null\"";

    @Override
    public String getRuntimeType() {
        return "python";
    }

    @Override
    public Integer getDefaultContainerPort() {
        return 8000;
    }

    @Override
    public String getDefaultMountPath() {
        return WORK_DIR;
    }

    @Override
    public String getDefaultMountOptions() {
        return "";
    }

    @Override
    protected String getDefaultStartCommand() {
        return KEEP_ALIVE_COMMAND;
    }

    @Override
    public String buildStartCommand(String originalCommand, Map<String, String> variables) {
        return KEEP_ALIVE_COMMAND;
    }

    @Override
    public Map<String, String> getDefaultEnvironment(ExperimentMetadata metadata, Integer hostPort) {
        Map<String, String> env = new HashMap<>();
        env.put("PYTHONUNBUFFERED", "1");
        env.put("WORKDIR", WORK_DIR);
        if (hostPort != null) {
            env.put("APP_PORT", String.valueOf(hostPort));
        }
        return env;
    }

    @Override
    public HealthCheckConfig getDefaultHealthCheck(Integer containerPort) {
        // 命令行环境无需健康检查
        return null;
    }

    @Override
    public boolean enableTty(ExperimentMetadata metadata) {
        return true;
    }

    @Override
    public boolean enableStdinOpen(ExperimentMetadata metadata) {
        return true;
    }

    @Override
    public String getWorkingDirectory(ExperimentMetadata metadata) {
        return WORK_DIR;
    }
}

