package org.dockerenvs.exception;

/**
 * 数据库操作异常
 */
public class DatabaseException extends EnvException {
    
    public static final String ERROR_CODE_INIT_FAILED = "DATABASE_INIT_FAILED";
    public static final String ERROR_CODE_CONNECTION_FAILED = "DATABASE_CONNECTION_FAILED";
    
    public DatabaseException(String message) {
        super(message);
    }
    
    public DatabaseException(String errorCode, String message) {
        super(errorCode, message);
    }
    
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DatabaseException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

