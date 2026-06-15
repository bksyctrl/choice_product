package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.AiRouteRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AiRouteRuleMapper extends BaseMapper<AiRouteRule> {
    
    @Select("SELECT * FROM ai_route_rule WHERE task_type = #{taskType} AND enabled = 1 AND deleted = 0 ORDER BY priority ASC LIMIT 1")
    AiRouteRule findBestRuleByTaskType(String taskType);
    
    @Select("SELECT * FROM ai_route_rule WHERE enabled = 1 AND deleted = 0 ORDER BY priority ASC")
    List<AiRouteRule> findAllEnabledRules();
}
