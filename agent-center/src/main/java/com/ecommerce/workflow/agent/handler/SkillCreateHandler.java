package com.ecommerce.workflow.agent.handler;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.service.evolution.SkillConfigService;

public class SkillCreateHandler extends AbstractIntentHandler {

    private SkillConfigService skillConfigService;
    private ObjectMapper objectMapper;

    public void setSkillConfigService(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理Skill创建请求: {}", request.getMessage());

        try {
            String skillName = extractSkillName(request.getMessage());
            String description = extractDescription(request.getMessage());
            Map<String, Object> configParams = extractConfigParams(request.getMessage());

            String skillCode = skillName.toLowerCase().replace(" ", "_").replace("-", "_");

            // 将description添加到configParams中，以便在创建时一起保存
            if (description != null && !description.isEmpty()) {
                configParams.put("description", description);
            }

            skillConfigService.createOrUpdateSkill(skillCode, skillName, "custom", configParams, "用户创建");

            String reply = String.format("已创建新Skill: %s (%s)\n\n配置参数: %s",
                    skillName, skillCode, configParams);
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("创建Skill失败", e);
            String reply = "创建Skill失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.failure(e);
        }
    }

    private String extractSkillName(String message) {
        String[] patterns = { "名称[：:]", "name[：:]", "叫[做]?" };
        for (String pattern : patterns) {
            int idx = message.toLowerCase().indexOf(pattern.toLowerCase());
            if (idx != -1) {
                int start = idx + pattern.length();
                int end = message.indexOf("\n", start);
                if (end == -1)
                    end = message.length();
                return message.substring(start, end).trim();
            }
        }
        return "自定义Skill_" + System.currentTimeMillis();
    }

    private String extractDescription(String message) {
        String[] patterns = { "描述[：:]", "description[：:]", "说明[：:]" };
        for (String pattern : patterns) {
            int idx = message.toLowerCase().indexOf(pattern.toLowerCase());
            if (idx != -1) {
                int start = idx + pattern.length();
                int end = message.indexOf("\n", start);
                if (end == -1)
                    end = message.length();
                return message.substring(start, end).trim();
            }
        }
        return "";
    }

    private Map<String, Object> extractConfigParams(String message) {
        Map<String, Object> params = new HashMap<>();
        try {
            int start = message.indexOf("{");
            int end = message.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                String json = message.substring(start, end + 1);
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            log.warn("解析配置参数失败", e);
        }
        return params;
    }

    @Override
    public String getIntentCode() {
        return "skill_create";
    }
}
