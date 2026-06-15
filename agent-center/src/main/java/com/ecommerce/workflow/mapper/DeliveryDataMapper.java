package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.DeliveryData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeliveryDataMapper extends BaseMapper<DeliveryData> {
    
    @Select("SELECT * FROM biz_delivery_data WHERE video_task_id = #{videoTaskId} AND deleted = 0 ORDER BY data_date DESC")
    List<DeliveryData> selectByVideoTaskId(@Param("videoTaskId") String videoTaskId);
}
