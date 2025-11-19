package org.dockerenvs.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 环境信息响应
 */
@Data
public class EnvInfo {
    
    private String envId;
    
    private String userId;
    
    private String systemId;
    
    private String expId;
    
    private Integer port;
    
    private String containerId;
    
    private String containerName;
    
    private String envDir;
    
    private String status;
    
    private String url;
    
    private LocalDateTime createdTime;
    
    private LocalDateTime updatedTime;
}

