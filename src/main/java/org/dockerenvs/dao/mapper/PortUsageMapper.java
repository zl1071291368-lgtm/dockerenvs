package org.dockerenvs.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dockerenvs.entity.PortUsage;

/**
 * 端口使用 Mapper
 */
@Mapper
public interface PortUsageMapper extends BaseMapper<PortUsage> {
}

