package org.dockerenvs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dao.mapper.PortUsageMapper;
import org.dockerenvs.entity.PortUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 端口管理服务
 */
@Slf4j
@Service
public class PortManagerService {
    
    @Autowired
    private PortUsageMapper portUsageMapper;
    
    @Value("${env.port.min:18000}")
    private Integer minPort;
    
    @Value("${env.port.max:19999}")
    private Integer maxPort;
    
    /**
     * 分配端口
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer assignPort(String envId) {
        // 1. 获取已使用的端口
        Set<Integer> usedPorts = getUsedPorts();
        
        // 2. 从最小端口开始查找可用端口
        for (int port = minPort; port <= maxPort; port++) {
            if (!usedPorts.contains(port) && isPortAvailable(port)) {
                // 3. 检查端口记录是否存在（可能状态是FREE）
                PortUsage existingPortUsage = portUsageMapper.selectById(port);
                if (existingPortUsage != null) {
                    // 如果存在但状态是FREE，更新为USED
                    if ("FREE".equals(existingPortUsage.getStatus())) {
                        existingPortUsage.setEnvId(envId);
                        existingPortUsage.setStatus("USED");
                        existingPortUsage.setAllocatedTime(java.time.LocalDateTime.now());
                        portUsageMapper.updateById(existingPortUsage);
                        log.info("重新分配端口: {} 给环境: {}", port, envId);
                        return port;
                    } else {
                        // 状态是USED，跳过
                        continue;
                    }
                } else {
                    // 4. 端口记录不存在，创建新记录
                    PortUsage portUsage = new PortUsage();
                    portUsage.setPort(port);
                    portUsage.setEnvId(envId);
                    portUsage.setStatus("USED");
                    portUsage.setAllocatedTime(java.time.LocalDateTime.now());
                    
                    portUsageMapper.insert(portUsage);
                    log.info("分配端口: {} 给环境: {}", port, envId);
                    return port;
                }
            }
        }
        
        throw new RuntimeException("没有可用的端口，端口范围已用完");
    }
    
    /**
     * 释放端口
     */
    @Transactional(rollbackFor = Exception.class)
    public void releasePort(Integer port) {
        PortUsage portUsage = portUsageMapper.selectById(port);
        if (portUsage != null) {
            portUsage.setStatus("FREE");
            portUsageMapper.updateById(portUsage);
            log.info("释放端口: {}", port);
        } else {
            log.warn("端口记录不存在，无法释放: {}", port);
        }
    }
    
    /**
     * 获取端口使用记录
     */
    public PortUsage getPortUsage(Integer port) {
        return portUsageMapper.selectById(port);
    }
    
    /**
     * 获取已使用的端口集合
     */
    private Set<Integer> getUsedPorts() {
        LambdaQueryWrapper<PortUsage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PortUsage::getStatus, "USED");
        List<PortUsage> portUsages = portUsageMapper.selectList(queryWrapper);
        
        return portUsages.stream()
            .map(PortUsage::getPort)
            .collect(Collectors.toSet());
    }
    
    /**
     * 检查端口是否可用（未被系统占用）
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 检查端口是否被Docker占用
     */
    public boolean isPortUsedByDocker(int port) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "--format", "{{.Ports}}"
            );
            Process process = processBuilder.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":" + port + "->")) {
                    return true;
                }
            }
            
            process.waitFor();
            return false;
        } catch (Exception e) {
            log.warn("检查Docker端口占用失败", e);
            return false;
        }
    }
}

