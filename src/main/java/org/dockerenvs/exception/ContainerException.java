package org.dockerenvs.exception;

/**
 * 容器操作异常
 */
public class ContainerException extends EnvException {
    
    public static final String ERROR_CODE_START_FAILED = "CONTAINER_START_FAILED";
    public static final String ERROR_CODE_STOP_FAILED = "CONTAINER_STOP_FAILED";
    public static final String ERROR_CODE_NOT_FOUND = "CONTAINER_NOT_FOUND";
    public static final String ERROR_CODE_NOT_RUNNING = "CONTAINER_NOT_RUNNING";
    public static final String ERROR_CODE_HEALTH_CHECK_TIMEOUT = "CONTAINER_HEALTH_CHECK_TIMEOUT";
    
    public ContainerException(String message) {
        super(message);
    }
    
    public ContainerException(String errorCode, String message) {
        super(errorCode, message);
    }
    
    public ContainerException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ContainerException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

