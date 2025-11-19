package org.dockerenvs.dto;

import lombok.Data;

/**
 * 启动环境请求
 */
@Data
public class StartEnvRequest {
    
    private String userId;
    
    private String systemId;
    
    private String expId;
}

