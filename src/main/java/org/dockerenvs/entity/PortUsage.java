package org.dockerenvs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 端口使用记录
 */
@Data
@TableName("port_usage")
public class PortUsage {
    
    @TableId(type = IdType.INPUT)
    private Integer port;
    
    private String envId;
    
    /**
     * 状态: USED / FREE
     */
    private String status;
    
    private LocalDateTime allocatedTime;
}

