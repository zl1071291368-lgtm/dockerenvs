package org.dockerenvs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dockerenvs.dao.mapper.PortUsageMapper;
import org.dockerenvs.entity.PortUsage;
import org.dockerenvs.exception.PortException;
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
     * 分配端口（使用数据库锁保证并发安全）
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer assignPort(String envId) {
        // 1. 获取已使用的端口（在事务中查询，保证一致性）
        Set<Integer> usedPorts = getUsedPorts();
        
        // 2. 从最小端口开始查找可用端口
        for (int port = minPort; port <= maxPort; port++) {
            // 跳过已使用的端口
            if (usedPorts.contains(port)) {
                continue;
            }
            
            // 检查端口是否被系统占用
            if (!isPortAvailable(port)) {
                continue;
            }
            
            // 3. 使用数据库锁机制分配端口（防止并发冲突）
            try {
                PortUsage existingPortUsage = portUsageMapper.selectById(port);
                if (existingPortUsage != null) {
                    // 如果存在但状态是FREE，尝试更新为USED（使用乐观锁）
                    if ("FREE".equals(existingPortUsage.getStatus())) {
                        // 使用UPDATE ... WHERE status='FREE' 保证原子性
                        LambdaUpdateWrapper<PortUsage> updateWrapper = new LambdaUpdateWrapper<>();
                        updateWrapper.eq(PortUsage::getPort, port)
                                     .eq(PortUsage::getStatus, "FREE")
                                     .set(PortUsage::getEnvId, envId)
                                     .set(PortUsage::getStatus, "USED")
                                     .set(PortUsage::getAllocatedTime, java.time.LocalDateTime.now());
                        
                        int updated = portUsageMapper.update(null, updateWrapper);
                        if (updated > 0) {
                            log.info("重新分配端口: {} 给环境: {}", port, envId);
                            return port;
                        }
                        // 如果更新失败，说明端口已被其他线程占用，继续查找下一个
                        continue;
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
                    
                    try {
                        portUsageMapper.insert(portUsage);
                        log.info("分配端口: {} 给环境: {}", port, envId);
                        return port;
                    } catch (Exception e) {
                        // 如果插入失败（可能是唯一约束冲突），继续查找下一个端口
                        log.debug("端口 {} 插入失败，可能已被占用，继续查找: {}", port, e.getMessage());
                        continue;
                    }
                }
            } catch (Exception e) {
                log.warn("分配端口 {} 时出错，继续查找: {}", port, e.getMessage());
                continue;
            }
        }
        
        throw new PortException(PortException.ERROR_CODE_NO_AVAILABLE_PORT,
            String.format("没有可用的端口，端口范围 [%d-%d] 已用完", minPort, maxPort));
    }
    
    /**
     * 释放端口
     */
    @Transactional(rollbackFor = Exception.class)
    public void releasePort(Integer port) {
        if (port == null) {
            log.warn("端口号为空，无法释放");
            return;
        }
        
        try {
            PortUsage portUsage = portUsageMapper.selectById(port);
            if (portUsage != null) {
                portUsage.setStatus("FREE");
                portUsage.setEnvId(null); // 清空环境ID
                portUsageMapper.updateById(portUsage);
                log.info("释放端口: {}", port);
            } else {
                log.warn("端口记录不存在，无法释放: {}", port);
            }
        } catch (Exception e) {
            log.error("释放端口失败: port={}", port, e);
            throw new PortException(PortException.ERROR_CODE_PORT_RELEASE_FAILED,
                "释放端口失败: " + port, e);
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

