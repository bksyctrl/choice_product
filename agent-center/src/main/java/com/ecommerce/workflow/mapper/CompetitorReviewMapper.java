package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.CompetitorReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CompetitorReviewMapper extends BaseMapper<CompetitorReview> {
    
    @Select("SELECT * FROM biz_competitor_review WHERE competitor_id = #{competitorId} AND deleted = 0 ORDER BY review_date DESC")
    List<CompetitorReview> selectByCompetitorId(@Param("competitorId") String competitorId);
}
