package com.ecommerce.workflow.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    
    @Delete("DELETE FROM sys_chat_message WHERE session_id = #{sessionId}")
    int hardDeleteBySessionId(@Param("sessionId") String sessionId);
}
