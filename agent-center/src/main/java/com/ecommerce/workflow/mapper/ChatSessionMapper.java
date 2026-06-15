package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.ChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Update("UPDATE sys_chat_session SET message_count = message_count + 1, updated_at = NOW() WHERE session_id = #{sessionId} AND deleted = 0")
    int incrementMessageCount(@Param("sessionId") String sessionId);
    
    @Delete("DELETE FROM sys_chat_session WHERE session_id = #{sessionId}")
    int hardDeleteBySessionId(@Param("sessionId") String sessionId);
}
