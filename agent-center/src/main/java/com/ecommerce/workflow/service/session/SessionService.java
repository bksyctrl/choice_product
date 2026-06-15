package com.ecommerce.workflow.service.session;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.ChatMessage;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.mapper.ChatMessageMapper;
import com.ecommerce.workflow.mapper.ChatSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    public SessionService(ChatSessionMapper sessionMapper,
                         ChatMessageMapper messageMapper,
                         ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }
    @Transactional
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setSessionId("SESSION_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新会话");
        session.setStatus("ACTIVE");
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);
        log.info("创建会话: sessionId={}, userId={}", session.getSessionId(), userId);
        return session;
    }
    public ChatSession getSession(String sessionId) {
        return sessionMapper.selectOne(
                new QueryWrapper<ChatSession>()
                        .eq("session_id", sessionId)
                        .eq("deleted", 0)
        );
    }
    public List<ChatSession> getUserSessions(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        return sessionMapper.selectList(
                new QueryWrapper<ChatSession>()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .orderByDesc("updated_at")
                        .last("LIMIT " + size + " OFFSET " + offset)
        );
    }
    public List<ChatSession> searchSessions(Long userId, String keyword) {
        return sessionMapper.selectList(
                new QueryWrapper<ChatSession>()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .like("title", keyword)
                        .or().like("summary", keyword)
                        .orderByDesc("updated_at")
        );
    }
    @Transactional
    public void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setMessageId("MSG_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        message.setDeleted(0);
        messageMapper.insert(message);
        
        sessionMapper.incrementMessageCount(sessionId);
        log.debug("保存消息: sessionId={}, role={}", sessionId, role);
    }
    @Transactional
    public void saveMessageWithMetadata(String sessionId, String role, String content,
                                          List<String> referencedKnowledge, List<String> triggeredSkills) {
        ChatMessage message = new ChatMessage();
        message.setMessageId("MSG_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        try {
            if (referencedKnowledge != null && !referencedKnowledge.isEmpty()) {
                message.setReferencedKnowledge(objectMapper.writeValueAsString(referencedKnowledge));
            }
            if (triggeredSkills != null && !triggeredSkills.isEmpty()) {
                message.setTriggeredSkills(objectMapper.writeValueAsString(triggeredSkills));
            }
        } catch (Exception e) {
            log.warn("序列化引用知识或触发技能失败", e);
        }
        message.setCreatedAt(LocalDateTime.now());
        message.setDeleted(0);
        messageMapper.insert(message);
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setMessageCount(session.getMessageCount() + 1);
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .eq("deleted", 0)
                        .orderByAsc("created_at")
        );
    }
    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        return messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit)
        );
    }
    
    public List<ChatMessage> getRecentMessagesAsc(String sessionId, int limit) {
        return messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit)
        ).stream()
         .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
         .collect(java.util.stream.Collectors.toList());
    }
    @Transactional
    public void updateSessionSummary(String sessionId, String summary) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setSummary(summary);
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            log.info("更新会话摘要: sessionId={}", sessionId);
        }
    }
    @Transactional
    public void archiveSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setStatus("ARCHIVED");
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            log.info("瑜版帗銆傛导姘崇樈: sessionId={}", sessionId);
        }
    }
    @Transactional
    public void deleteSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            // 删除会话相关消息
            messageMapper.hardDeleteBySessionId(sessionId);
            
            // 删除会话记录
            sessionMapper.hardDeleteBySessionId(sessionId);
            
            log.info("会话删除成功: sessionId={}", sessionId);
        } else {
            log.warn("删除会话失败: sessionId={}", sessionId);
        }
    }
    public List<Map<String, Object>> getSessionContext(String sessionId, int lastN) {
        List<ChatMessage> messages = getRecentMessages(sessionId, lastN);
        return messages.stream()
                .map(msg -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("role", msg.getRole());
                    map.put("content", msg.getContent());
                    map.put("timestamp", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
                    return map;
                })
                .collect(Collectors.toList());
    }
    public int getSessionCount(Long userId) {
        Long count = sessionMapper.selectCount(
                new QueryWrapper<ChatSession>()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        return count != null ? count.intValue() : 0;
    }
}
