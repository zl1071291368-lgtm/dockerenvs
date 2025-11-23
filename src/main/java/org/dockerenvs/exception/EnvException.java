package org.dockerenvs.exception;

/**
 * 环境管理异常基类
 */
public class EnvException extends RuntimeException {
    
    private String errorCode;
    
    public EnvException(String message) {
        super(message);
    }
    
    public EnvException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public EnvException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EnvException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

