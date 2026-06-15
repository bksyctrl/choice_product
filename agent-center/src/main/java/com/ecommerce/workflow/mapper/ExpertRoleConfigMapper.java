package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.ExpertRoleConfig;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExpertRoleConfigMapper extends BaseMapper<ExpertRoleConfig> {
    
    @Select("SELECT * FROM expert_role_config WHERE role_code = #{roleCode}")
    ExpertRoleConfig selectByRoleCode(@Param("roleCode") String roleCode);
}
