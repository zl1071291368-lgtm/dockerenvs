package org.dockerenvs.dto;

import lombok.Data;

/**
 * 数据卷配置
 */
@Data
public class VolumeConfig {
    
    /**
     * 主机路径
     */
    private String hostPath;
    
    /**
     * 容器内路径
     */
    private String containerPath;
    
    /**
     * 挂载选项（如 :ro 表示只读）
     */
    private String options;
}

