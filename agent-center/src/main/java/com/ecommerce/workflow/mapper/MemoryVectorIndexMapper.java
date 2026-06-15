package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.MemoryVectorIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryVectorIndexMapper extends BaseMapper<MemoryVectorIndex> {
    
    @Select("SELECT * FROM memory_vector_index WHERE vector_status = #{status}")
    List<MemoryVectorIndex> findByStatus(@Param("status") String status);
    
    @Select("SELECT * FROM memory_vector_index WHERE memory_id = #{memoryId}")
    MemoryVectorIndex findByMemoryId(@Param("memoryId") Long memoryId);
}
