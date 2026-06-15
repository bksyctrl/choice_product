package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
    
    @Select("SELECT provider_name, COUNT(*) as count, SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count, AVG(latency_ms) as avg_latency FROM ai_call_log WHERE created_at >= #{startTime} GROUP BY provider_name")
    List<Map<String, Object>> getProviderStats(LocalDateTime startTime);
    
    @Select("SELECT model_name, COUNT(*) as count, SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count FROM ai_call_log WHERE created_at >= #{startTime} GROUP BY model_name ORDER BY count DESC")
    List<Map<String, Object>> getModelUsageStats(LocalDateTime startTime);
    
    @Select("SELECT task_type, COUNT(*) as count, AVG(latency_ms) as avg_latency FROM ai_call_log WHERE created_at >= #{startTime} GROUP BY task_type")
    List<Map<String, Object>> getTaskTypeStats(LocalDateTime startTime);

    Double getTotalCost(LocalDateTime startTime);
}
