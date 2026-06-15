package com.ecommerce.workflow.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.analytics.entity.ApiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ApiCallLogMapper extends BaseMapper<ApiCallLog> {

    @Select("SELECT " +
            "  DATE(created_at) as date, " +
            "  COUNT(*) as total_requests, " +
            "  SUM(total_tokens) as total_tokens, " +
            "  SUM(cost) as total_cost, " +
            "  AVG(latency_ms) as avg_latency, " +
            "  SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as success_rate " +
            "FROM api_call_log " +
            "WHERE user_id = #{userId} " +
            "  AND created_at >= #{startTime} " +
            "  AND created_at <= #{endTime} " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date DESC")
    List<Map<String, Object>> getDailyStats(@Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " +
            "  provider, " +
            "  model, " +
            "  COUNT(*) as call_count, " +
            "  SUM(total_tokens) as total_tokens, " +
            "  SUM(cost) as total_cost " +
            "FROM api_call_log " +
            "WHERE user_id = #{userId} " +
            "  AND created_at >= #{startTime} " +
            "GROUP BY provider, model " +
            "ORDER BY call_count DESC")
    List<Map<String, Object>> getModelUsageStats(@Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime);

    @Select("SELECT " +
            "  COUNT(*) as total_requests, " +
            "  SUM(total_tokens) as total_tokens, " +
            "  SUM(cost) as total_cost, " +
            "  AVG(latency_ms) as avg_latency, " +
            "  SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as success_rate " +
            "FROM api_call_log " +
            "WHERE user_id = #{userId} " +
            "  AND created_at >= #{startTime}")
    Map<String, Object> getSummaryStats(@Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime);
}
