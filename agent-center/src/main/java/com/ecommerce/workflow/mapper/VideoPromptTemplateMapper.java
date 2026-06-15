package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.VideoPromptTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface VideoPromptTemplateMapper extends BaseMapper<VideoPromptTemplate> {

    @Select("SELECT * FROM video_prompt_template WHERE template_code = #{templateCode} AND deleted = 0")
    VideoPromptTemplate selectByTemplateCode(@Param("templateCode") String templateCode);

    @Select("SELECT * FROM video_prompt_template WHERE category = #{category} AND deleted = 0 AND is_active = 1 ORDER BY success_rate DESC")
    List<VideoPromptTemplate> selectByCategory(@Param("category") String category);

    @Select("SELECT * FROM video_prompt_template WHERE category = #{category} AND sub_category = #{subCategory} AND deleted = 0 AND is_active = 1 ORDER BY success_rate DESC")
    List<VideoPromptTemplate> selectByCategoryAndSubCategory(@Param("category") String category, @Param("subCategory") String subCategory);

    @Select("SELECT * FROM video_prompt_template WHERE deleted = 0 AND is_active = 1 ORDER BY usage_count DESC")
    List<VideoPromptTemplate> selectAllActive();

    @Update("UPDATE video_prompt_template SET usage_count = usage_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementUsageCount(@Param("id") Long id);

    @Update("UPDATE video_prompt_template SET success_rate = #{successRate}, avg_quality_score = #{avgQualityScore}, learned_from_cases = learned_from_cases + 1, updated_at = NOW() WHERE id = #{id}")
    int updateLearningMetrics(@Param("id") Long id, @Param("successRate") Double successRate, @Param("avgQualityScore") Double avgQualityScore);
}
