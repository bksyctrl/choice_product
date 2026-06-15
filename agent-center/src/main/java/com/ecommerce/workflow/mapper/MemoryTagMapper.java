package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.MemoryTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryTagMapper extends BaseMapper<MemoryTag> {
    
    @Select("SELECT * FROM memory_tag WHERE memory_id = #{memoryId}")
    List<MemoryTag> findByMemoryId(@Param("memoryId") Long memoryId);
    
    @Select("SELECT DISTINCT tag_name FROM memory_tag WHERE tag_category = #{category}")
    List<String> findTagsByCategory(@Param("category") String category);
    
    @Select("SELECT DISTINCT tag_category FROM memory_tag")
    List<String> findAllCategories();
}
