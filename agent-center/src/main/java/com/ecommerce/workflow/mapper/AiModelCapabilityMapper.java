package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.AiModelCapability;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AiModelCapabilityMapper extends BaseMapper<AiModelCapability> {
    
    @Select("SELECT * FROM ai_model_capability WHERE model_name = #{modelName}")
    List<AiModelCapability> findByModelName(String modelName);
    
    @Select("SELECT * FROM ai_model_capability WHERE capability_type = #{capabilityType} ORDER BY score DESC")
    List<AiModelCapability> findByCapabilityType(String capabilityType);
    
    @Select("SELECT score FROM ai_model_capability WHERE model_name = #{modelName} AND capability_type = #{capabilityType}")
    BigDecimal getScore(String modelName, String capabilityType);
}
