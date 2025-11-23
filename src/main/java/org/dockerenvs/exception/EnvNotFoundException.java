package org.dockerenvs.exception;

/**
 * 环境不存在异常
 */
public class EnvNotFoundException extends EnvException {
    
    public static final String ERROR_CODE = "ENV_NOT_FOUND";
    
    public EnvNotFoundException(String envId) {
        super(ERROR_CODE, "环境不存在: " + envId);
    }
    
    public EnvNotFoundException(String envId, Throwable cause) {
        super(ERROR_CODE, "环境不存在: " + envId, cause);
    }
}

