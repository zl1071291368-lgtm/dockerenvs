package org.dockerenvs.controller;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.ApiResponse;
import org.dockerenvs.exception.ContainerException;
import org.dockerenvs.exception.DatabaseException;
import org.dockerenvs.exception.EnvException;
import org.dockerenvs.exception.EnvNotFoundException;
import org.dockerenvs.exception.PortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 全局异常处理器
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理环境不存在异常
     */
    @ExceptionHandler(EnvNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleEnvNotFoundException(EnvNotFoundException e) {
        log.warn("环境不存在: {}", e.getMessage());
        ApiResponse<Object> response = ApiResponse.error(
            e.getErrorCode() != null ? e.getErrorCode() : EnvNotFoundException.ERROR_CODE,
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 处理容器操作异常
     */
    @ExceptionHandler(ContainerException.class)
    public ResponseEntity<ApiResponse<Object>> handleContainerException(ContainerException e) {
        log.error("容器操作失败: {}", e.getMessage(), e);
        ApiResponse<Object> response = ApiResponse.error(
            e.getErrorCode() != null ? e.getErrorCode() : ContainerException.ERROR_CODE_START_FAILED,
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理端口管理异常
     */
    @ExceptionHandler(PortException.class)
    public ResponseEntity<ApiResponse<Object>> handlePortException(PortException e) {
        log.error("端口操作失败: {}", e.getMessage(), e);
        ApiResponse<Object> response = ApiResponse.error(
            e.getErrorCode() != null ? e.getErrorCode() : PortException.ERROR_CODE_NO_AVAILABLE_PORT,
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理数据库操作异常
     */
    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleDatabaseException(DatabaseException e) {
        log.error("数据库操作失败: {}", e.getMessage(), e);
        ApiResponse<Object> response = ApiResponse.error(
            e.getErrorCode() != null ? e.getErrorCode() : DatabaseException.ERROR_CODE_INIT_FAILED,
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理通用环境异常
     */
    @ExceptionHandler(EnvException.class)
    public ResponseEntity<ApiResponse<Object>> handleEnvException(EnvException e) {
        log.error("环境操作失败: {}", e.getMessage(), e);
        ApiResponse<Object> response = ApiResponse.error(
            e.getErrorCode() != null ? e.getErrorCode() : "ENV_OPERATION_FAILED",
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("未处理的异常: {}", e.getMessage(), e);
        ApiResponse<Object> response = ApiResponse.error(
            "INTERNAL_SERVER_ERROR",
            "系统内部错误: " + e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
