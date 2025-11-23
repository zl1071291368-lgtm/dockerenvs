package org.dockerenvs.controller;

import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dto.ApiResponse;
import org.dockerenvs.dto.EnvInfo;
import org.dockerenvs.dto.StartEnvRequest;
import org.dockerenvs.service.EnvManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<ApiResponse<EnvInfo>> startEnv(@RequestBody StartEnvRequest request) {
        log.info("启动环境请求: userId={}, systemId={}, expId={}", 
            request.getUserId(), request.getSystemId(), request.getExpId());
        EnvInfo envInfo = envManagerService.createEnv(request);
        return ResponseEntity.ok(ApiResponse.success(envInfo, "环境启动成功"));
    }
    
    /**
     * 停止环境
     * POST /api/env/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Object>> stopEnv(@RequestParam String envId) {
        log.info("停止环境: envId={}", envId);
        envManagerService.stopEnv(envId);
        return ResponseEntity.ok(ApiResponse.success(null, "环境停止成功"));
    }
    
    /**
     * 启动已停止的环境（启动已存在的容器）
     * POST /api/env/start-existing
     */
    @PostMapping("/start-existing")
    public ResponseEntity<ApiResponse<Object>> startEnv(@RequestParam String envId) {
        log.info("启动环境: envId={}", envId);
        envManagerService.startEnv(envId);
        return ResponseEntity.ok(ApiResponse.success(null, "环境启动成功"));
    }
    
    /**
     * 重置环境
     * POST /api/env/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Object>> resetEnv(@RequestParam String envId) {
        log.info("重置环境: envId={}", envId);
        envManagerService.resetEnv(envId);
        return ResponseEntity.ok(ApiResponse.success(null, "环境重置成功"));
    }
    
    /**
     * 销毁环境
     * DELETE /api/env/{envId}
     */
    @DeleteMapping("/{envId}")
    public ResponseEntity<ApiResponse<Object>> destroyEnv(@PathVariable String envId) {
        log.info("销毁环境: envId={}", envId);
        envManagerService.destroyEnv(envId);
        return ResponseEntity.ok(ApiResponse.success(null, "环境销毁成功"));
    }
    
    /**
     * 销毁环境（支持通过查询参数或请求体传递envId）
     * DELETE /api/env/delete?envId=xxx
     * 或 DELETE /api/env/delete (请求体: {"envId": "xxx"})
     */
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<Object>> destroyEnvFlexible(
            @RequestParam(required = false) String envId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        
        // 从查询参数或请求体中获取envId
        String targetEnvId = envId;
        if (targetEnvId == null && body != null) {
            targetEnvId = body.get("envId");
        }
        
        if (targetEnvId == null || targetEnvId.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "envId参数不能为空"));
        }
        
        log.info("销毁环境: envId={}", targetEnvId);
        envManagerService.destroyEnv(targetEnvId);
        return ResponseEntity.ok(ApiResponse.success(null, "环境销毁成功"));
    }
    
    /**
     * 查询环境状态
     * GET /api/env/{envId}/status
     */
    @GetMapping("/{envId}/status")
    public ResponseEntity<ApiResponse<EnvInfo>> getEnvStatus(@PathVariable String envId) {
        log.info("查询环境状态: envId={}", envId);
        EnvInfo envInfo = envManagerService.getEnvStatus(envId);
        if (envInfo != null) {
            return ResponseEntity.ok(ApiResponse.success(envInfo));
        } else {
            return ResponseEntity.ok(ApiResponse.error("ENV_NOT_FOUND", "环境不存在"));
        }
    }
    
    /**
     * 查询用户所有环境
     * GET /api/env/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<EnvInfo>>> getUserEnvs(@PathVariable String userId) {
        log.info("查询用户环境: userId={}", userId);
        List<EnvInfo> envs = envManagerService.getUserEnvs(userId);
        return ResponseEntity.ok(ApiResponse.success(envs));
    }
    
    /**
     * 查询所有环境（管理用）
     * GET /api/env/all
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<EnvInfo>>> getAllEnvs() {
        log.info("查询所有环境");
        List<EnvInfo> envs = envManagerService.getAllEnvs();
        return ResponseEntity.ok(ApiResponse.success(envs));
    }
}

