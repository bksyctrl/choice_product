package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.CompetitorData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CompetitorDataMapper extends BaseMapper<CompetitorData> {
    
    @Select("SELECT * FROM biz_competitor_data " +
            "WHERE category = #{category} AND deleted = 0 " +
            "AND (#{platform} IS NULL OR platform = #{platform}) " +
            "ORDER BY sales_volume DESC LIMIT #{limit}")
    List<CompetitorData> selectByCategoryAndPlatform(@Param("category") String category, 
                                                      @Param("platform") String platform, 
                                                      @Param("limit") int limit);
    
    @Select("SELECT * FROM biz_competitor_data WHERE competitor_id = #{competitorId} AND deleted = 0")
    CompetitorData selectByCompetitorId(@Param("competitorId") String competitorId);
    
    @Select("SELECT COUNT(*) FROM biz_competitor_data WHERE category = #{category} AND deleted = 0")
    int countByCategory(@Param("category") String category);
    
    @Select("SELECT COUNT(*) FROM biz_competitor_data WHERE category = #{category} AND status = 'ACTIVE' AND deleted = 0")
    int countActiveByCategory(@Param("category") String category);
}
