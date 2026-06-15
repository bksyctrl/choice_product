package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.ABTestConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ABTestConfigMapper extends BaseMapper<ABTestConfig> {
    
    @Select("SELECT * FROM ab_test_config WHERE test_code = #{testCode}")
    ABTestConfig selectByTestCode(@Param("testCode") String testCode);
}
