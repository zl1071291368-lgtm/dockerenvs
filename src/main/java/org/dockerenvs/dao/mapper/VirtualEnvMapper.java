package org.dockerenvs.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dockerenvs.entity.VirtualEnv;

/**
 * 虚拟环境 Mapper
 */
@Mapper
public interface VirtualEnvMapper extends BaseMapper<VirtualEnv> {
}

