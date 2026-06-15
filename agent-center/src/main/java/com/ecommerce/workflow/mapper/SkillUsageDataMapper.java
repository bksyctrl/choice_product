package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.SkillUsageData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SkillUsageDataMapper extends BaseMapper<SkillUsageData> {
    
    @Select("SELECT skill_code, COUNT(*) as count, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count, " +
            "AVG(execution_time_ms) as avg_time, " +
            "AVG(user_rating) as avg_rating " +
            "FROM skill_usage_data " +
            "WHERE created_at >= #{startTime} " +
            "GROUP BY skill_code")
    List<Map<String, Object>> getUsageStatsBySkill(@Param("startTime") LocalDateTime startTime);
    
    @Select("SELECT intent_type, COUNT(*) as count, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count " +
            "FROM skill_usage_data " +
            "WHERE created_at >= #{startTime} " +
            "GROUP BY intent_type")
    List<Map<String, Object>> getUsageStatsByIntent(@Param("startTime") LocalDateTime startTime);
    
    @Select("SELECT * FROM skill_usage_data WHERE skill_code = #{skillCode} ORDER BY created_at DESC LIMIT #{limit}")
    List<SkillUsageData> getRecentUsageBySkill(@Param("skillCode") String skillCode, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM skill_usage_data WHERE skill_code = #{skillCode} AND success = 1")
    int countSuccessfulUsage(@Param("skillCode") String skillCode);
    
    @Select("SELECT COUNT(*) FROM skill_usage_data WHERE skill_code = #{skillCode}")
    int countTotalUsage(@Param("skillCode") String skillCode);
}
