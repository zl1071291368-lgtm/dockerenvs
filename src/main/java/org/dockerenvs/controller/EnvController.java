package org.dockerenvs.controller;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.EnvInfo;
import org.dockerenvs.dto.StartEnvRequest;
import org.dockerenvs.service.EnvManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境管理 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/env")
public class EnvController {
    
    @Autowired
    private EnvManagerService envManagerService;
    
    /**
     * 启动/创建环境
     * POST /api/env/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startEnv(@RequestBody StartEnvRequest request) {
        log.info("启动环境请求: userId={}, systemId={}, expId={}", 
            request.getUserId(), request.getSystemId(), request.getExpId());
        try {
            EnvInfo envInfo = envManagerService.createEnv(request);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", envInfo);
            response.put("message", "环境启动成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动环境失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            // 添加详细的错误信息
            if (e.getCause() != null) {
                response.put("detail", e.getCause().getMessage());
            }
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 停止环境
     * POST /api/env/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopEnv(@RequestParam String envId) {
        log.info("停止环境: envId={}", envId);
        
        try {
            envManagerService.stopEnv(envId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "环境停止成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止环境失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 重置环境
     * POST /api/env/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetEnv(@RequestParam String envId) {
        log.info("重置环境: envId={}", envId);
        
        try {
            envManagerService.resetEnv(envId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "环境重置成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("重置环境失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 销毁环境
     * DELETE /api/env/{envId}
     */
    @DeleteMapping("/{envId}")
    public ResponseEntity<Map<String, Object>> destroyEnv(@PathVariable String envId) {
        log.info("销毁环境: envId={}", envId);
        
        try {
            envManagerService.destroyEnv(envId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "环境销毁成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("销毁环境失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            if (e.getCause() != null) {
                response.put("detail", e.getCause().getMessage());
            }
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 销毁环境（支持通过查询参数或请求体传递envId）
     * DELETE /api/env/delete?envId=xxx
     * 或 DELETE /api/env/delete (请求体: {"envId": "xxx"})
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> destroyEnvFlexible(
            @RequestParam(required = false) String envId,
            @RequestBody(required = false) Map<String, String> body) {
        
        // 从查询参数或请求体中获取envId
        String targetEnvId = envId;
        if (targetEnvId == null && body != null) {
            targetEnvId = body.get("envId");
        }
        
        if (targetEnvId == null || targetEnvId.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "envId参数不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        
        log.info("销毁环境: envId={}", targetEnvId);
        
        try {
            envManagerService.destroyEnv(targetEnvId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "环境销毁成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("销毁环境失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 查询环境状态
     * GET /api/env/{envId}/status
     */
    @GetMapping("/{envId}/status")
    public ResponseEntity<Map<String, Object>> getEnvStatus(@PathVariable String envId) {
        log.info("查询环境状态: envId={}", envId);
        
        EnvInfo envInfo = envManagerService.getEnvStatus(envId);
        
        Map<String, Object> response = new HashMap<>();
        if (envInfo != null) {
            response.put("success", true);
            response.put("data", envInfo);
        } else {
            response.put("success", false);
            response.put("message", "环境不存在");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查询用户所有环境
     * GET /api/env/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserEnvs(@PathVariable String userId) {
        log.info("查询用户环境: userId={}", userId);
        
        List<EnvInfo> envs = envManagerService.getUserEnvs(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", envs);
        response.put("total", envs.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查询所有环境（管理用）
     * GET /api/env/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllEnvs() {
        log.info("查询所有环境");
        
        List<EnvInfo> envs = envManagerService.getAllEnvs();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", envs);
        response.put("total", envs.size());
        
        return ResponseEntity.ok(response);
    }
}

