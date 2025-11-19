package org.dockerenvs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟环境实体
 */
@Data
@TableName("virtual_env")
public class VirtualEnv {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String envId;
    
    private String userId;
    
    private String systemId;
    
    private String expId;
    
    private Integer port;
    
    private String containerId;
    
    private String envDir;
    
    /**
     * 状态: CREATED / RUNNING / STOPPED / DESTROYED
     */
    private String status;
    
    private String url;
    
    private LocalDateTime createdTime;
    
    private LocalDateTime updatedTime;
}

