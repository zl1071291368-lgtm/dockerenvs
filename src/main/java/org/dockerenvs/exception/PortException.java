package org.dockerenvs.exception;

/**
 * 端口管理异常
 */
public class PortException extends EnvException {
    
    public static final String ERROR_CODE_NO_AVAILABLE_PORT = "NO_AVAILABLE_PORT";
    public static final String ERROR_CODE_PORT_ALREADY_IN_USE = "PORT_ALREADY_IN_USE";
    public static final String ERROR_CODE_PORT_RELEASE_FAILED = "PORT_RELEASE_FAILED";
    
    public PortException(String message) {
        super(message);
    }
    
    public PortException(String errorCode, String message) {
        super(errorCode, message);
    }
    
    public PortException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PortException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

