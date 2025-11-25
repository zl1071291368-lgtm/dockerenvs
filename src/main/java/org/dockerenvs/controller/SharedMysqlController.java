package org.dockerenvs.controller;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.service.SharedMysqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享MySQL数据库管理 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/shared-mysql")
public class SharedMysqlController {
    
    @Autowired(required = false)
    private SharedMysqlService sharedMysqlService;
    /**
     * 获取共享MySQL状态
     * GET /api/shared-mysql/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.ok(response);
        }
        
        try {
            String verification = sharedMysqlService.verifyMysqlInitialization();
            Map<String, Object> data = new HashMap<>();
            data.put("containerName", sharedMysqlService.getContainerName());
            data.put("networkName", sharedMysqlService.getNetworkName());
            data.put("volumeName", sharedMysqlService.getVolumeName());
            data.put("verification", verification);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取共享MySQL状态失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 确保共享MySQL存在并运行
     * POST /api/shared-mysql/ensure
     */
    @PostMapping("/ensure")
    public ResponseEntity<Map<String, Object>> ensureExists() {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            sharedMysqlService.ensureSharedMysqlExists();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "共享MySQL已就绪");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("确保共享MySQL存在失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 停止共享MySQL
     * POST /api/shared-mysql/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            sharedMysqlService.stopSharedMysql();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "共享MySQL已停止");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止共享MySQL失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 销毁共享MySQL（包括所有数据）
     * DELETE /api/shared-mysql
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> destroy() {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            sharedMysqlService.destroySharedMysql();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "共享MySQL已完全删除（包括所有数据）");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("销毁共享MySQL失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取所有数据库列表
     * GET /api/shared-mysql/databases
     */
    @GetMapping("/databases")
    public ResponseEntity<Map<String, Object>> getDatabases() {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            List<String> databases = sharedMysqlService.getDatabases();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", databases);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取数据库列表失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取指定数据库的所有表
     * GET /api/shared-mysql/databases/{databaseName}/tables
     */
    @GetMapping("/databases/{databaseName}/tables")
    public ResponseEntity<Map<String, Object>> getTables(@PathVariable String databaseName) {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            List<String> tables = sharedMysqlService.getTables(databaseName);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", tables);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取表列表失败: {}", databaseName, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取表结构
     * GET /api/shared-mysql/databases/{databaseName}/tables/{tableName}/structure
     */
    @GetMapping("/databases/{databaseName}/tables/{tableName}/structure")
    public ResponseEntity<Map<String, Object>> getTableStructure(
            @PathVariable String databaseName,
            @PathVariable String tableName) {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            List<Map<String, String>> structure = sharedMysqlService.getTableStructure(databaseName, tableName);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", structure);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取表结构失败: {}.{}", databaseName, tableName, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取表数据（分页）
     * GET /api/shared-mysql/databases/{databaseName}/tables/{tableName}/data?page=1&pageSize=20
     */
    @GetMapping("/databases/{databaseName}/tables/{tableName}/data")
    public ResponseEntity<Map<String, Object>> getTableData(
            @PathVariable String databaseName,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (sharedMysqlService == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "SharedMysqlService未配置");
            return ResponseEntity.status(500).body(response);
        }
        
        try {
            Map<String, Object> data = sharedMysqlService.getTableData(databaseName, tableName, page, pageSize);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取表数据失败: {}.{}", databaseName, tableName, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}

