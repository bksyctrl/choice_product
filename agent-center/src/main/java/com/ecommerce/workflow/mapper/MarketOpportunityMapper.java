package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.MarketOpportunity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MarketOpportunityMapper extends BaseMapper<MarketOpportunity> {
    
    @Select("SELECT * FROM biz_market_opportunity " +
            "WHERE opportunity_type = 'BLUE_OCEAN' AND status = 'ACTIVE' AND deleted = 0 " +
            "AND (#{category} IS NULL OR category = #{category}) " +
            "ORDER BY opportunity_score DESC LIMIT #{limit}")
    List<MarketOpportunity> selectBlueOceanOpportunities(@Param("category") String category, @Param("limit") int limit);
    
    @Select("SELECT * FROM biz_market_opportunity " +
            "WHERE opportunity_type = 'TREND' AND status = 'ACTIVE' AND deleted = 0 " +
            "AND (#{category} IS NULL OR category = #{category}) " +
            "ORDER BY growth_rate DESC LIMIT #{limit}")
    List<MarketOpportunity> selectTrendOpportunities(@Param("category") String category, @Param("limit") int limit);
    
    @Select("SELECT * FROM biz_market_opportunity WHERE opportunity_id = #{opportunityId} AND deleted = 0")
    MarketOpportunity selectByOpportunityId(@Param("opportunityId") String opportunityId);
    
    @Update("UPDATE biz_market_opportunity SET status = #{status}, updated_at = #{updatedAt} " +
            "WHERE opportunity_id = #{opportunityId} AND deleted = 0")
    int updateStatus(@Param("opportunityId") String opportunityId, 
                     @Param("status") String status, 
                     @Param("updatedAt") LocalDateTime updatedAt);
    
    @Select("SELECT COUNT(*) FROM biz_market_opportunity " +
            "WHERE deleted = 0 AND (#{category} IS NULL OR category = #{category})")
    int countByCategory(@Param("category") String category);
    
    @Select("SELECT COUNT(*) FROM biz_market_opportunity " +
            "WHERE opportunity_type = #{type} AND deleted = 0 AND (#{category} IS NULL OR category = #{category})")
    int countByType(@Param("category") String category, @Param("type") String type);
}
