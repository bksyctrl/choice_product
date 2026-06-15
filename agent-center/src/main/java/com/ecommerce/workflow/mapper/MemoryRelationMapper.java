package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.MemoryRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryRelationMapper extends BaseMapper<MemoryRelation> {
    
    @Select("SELECT * FROM memory_relation WHERE source_memory_id = #{memoryId} OR target_memory_id = #{memoryId} ORDER BY strength DESC")
    List<MemoryRelation> findByMemoryId(@Param("memoryId") Long memoryId);
    
    @Select("SELECT * FROM memory_relation WHERE source_memory_id = #{memoryId} ORDER BY strength DESC LIMIT #{limit}")
    List<MemoryRelation> findRelatedFromMemory(@Param("memoryId") Long memoryId, @Param("limit") int limit);
    
    @Select("SELECT * FROM memory_relation WHERE relation_type = #{relationType} ORDER BY strength DESC LIMIT #{limit}")
    List<MemoryRelation> findByRelationType(@Param("relationType") String relationType, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM memory_relation WHERE source_memory_id = #{memoryId} OR target_memory_id = #{memoryId}")
    int countRelationsByMemoryId(@Param("memoryId") Long memoryId);
}
