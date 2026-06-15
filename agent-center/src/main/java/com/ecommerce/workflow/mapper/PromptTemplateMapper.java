package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.PromptTemplate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
    
    @Select("SELECT * FROM prompt_template WHERE template_code = #{templateCode}")
    PromptTemplate selectByTemplateCode(@Param("templateCode") String templateCode);
    
    @Select("SELECT * FROM prompt_template WHERE template_code = #{templateId}")
    PromptTemplate selectByTemplateId(@Param("templateId") String templateId);
}
