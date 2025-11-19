package org.dockerenvs.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 附加服务配置（用于docker-compose中的services）
 */
@Data
public class ServiceConfig {
    
    /**
     * 服务名称
     */
    private String name;
    
    /**
     * 镜像名称
     */
    private String image;
    
    /**
     * 启动命令
     */
    private String command;
    
    /**
     * 端口映射（格式: "hostPort:containerPort"）
     */
    private List<String> ports;
    
    /**
     * 环境变量
     */
    private Map<String, String> environment;
    
    /**
     * 数据卷
     */
    private List<String> volumes;
    
    /**
     * 依赖的服务
     */
    private List<String> dependsOn;
    
    /**
     * 网络
     */
    private List<String> networks;
}

