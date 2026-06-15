package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.SkillConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SkillConfigMapper extends BaseMapper<SkillConfig> {
    
    @Select("SELECT * FROM sys_skill_config WHERE skill_code = #{skillCode}")
    SkillConfig selectBySkillCode(@Param("skillCode") String skillCode);
}
