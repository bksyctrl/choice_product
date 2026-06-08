package com.choiceproduct.agentcenter.brain;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.rag.RagContext;
import com.choiceproduct.agentcenter.registry.AgentIntentDefinition;
import com.choiceproduct.agentcenter.registry.AgentIntentRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BrainService {
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("\\b\\d{10,}\\b");

    private final AgentIntentRegistry registry;
    private final DecisionValidator validator;
    private final double confidenceThreshold;

    public BrainService(
            AgentIntentRegistry registry,
            DecisionValidator validator,
            @Value("${agent.decision.confidence-threshold:0.6}") double confidenceThreshold) {
        this.registry = registry;
        this.validator = validator;
        this.confidenceThreshold = confidenceThreshold;
    }

    public DispatchDecision decide(AgentRequest request, RagContext ragContext) {
        AgentIntentDefinition definition = registry.matchByKeyword(request.getMessage());
        Map<String, Object> entities = extractEntities(request);

        DispatchDecision decision = new DispatchDecision();
        decision.setIntentCode(definition.getIntentCode());
        decision.setHandlerCode(definition.getHandlerCode());
        decision.setMode(definition.getDefaultMode());
        decision.setConfidence(calculateConfidence(definition, request.getMessage(), entities));
        decision.setEntities(entities);
        decision.setRequiredTools(definition.getRequiredTools());
        decision.setRequiredExperts(definition.getRequiredExperts());
        decision.setRiskLevel(definition.getRiskLevel());
        decision.setExecutor(definition.getHandlerCode());
        decision.setRequiresConfirmation(definition.getDefaultMode() == ExecutionMode.HANDOFF);
        decision.setReason(buildReason(definition, ragContext, decision.getConfidence()));

        if (decision.getConfidence() < confidenceThreshold) {
            decision.setIntentCode("general_chat");
            decision.setHandlerCode("general_chat");
            decision.setMode(ExecutionMode.CHAT);
            decision.setReason("策略大脑置信度低于阈值，切换到通用专家沟通，避免误派工。");
        }

        registry.findByIntentCode(decision.getIntentCode()).ifPresent(item -> validator.validate(decision, item));
        return decision;
    }

    private Map<String, Object> extractEntities(AgentRequest request) {
        Map<String, Object> entities = new HashMap<>();
        entities.putAll(request.getParameters());

        String message = request.getMessage() == null ? "" : request.getMessage();
        Matcher matcher = PRODUCT_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            entities.put("product_id", matcher.group());
        }
        if (message.toLowerCase().contains("fastmoss")) {
            entities.put("source", "fastmoss");
        } else if (message.toLowerCase().contains("kalodata")) {
            entities.put("source", "kalodata");
        } else if (message.contains("统一表")) {
            entities.put("source", "aggregate");
        }
        if (message.contains("attributes")) {
            entities.put("field", "attributes");
        } else if (message.contains("selling_points")) {
            entities.put("field", "selling_points");
        } else if (message.contains("IP") || message.contains("ip")) {
            entities.put("field", "ip_analysis");
        } else if (message.contains("材质")) {
            entities.put("field", "material_analysis");
        }
        Object images = request.getContext().get("images");
        if (images instanceof List<?> imageList && !imageList.isEmpty()) {
            entities.put("image_count", imageList.size());
        }
        return entities;
    }

    private double calculateConfidence(AgentIntentDefinition definition, String message, Map<String, Object> entities) {
        if ("general_chat".equals(definition.getIntentCode())) {
            return 0.65;
        }
        double confidence = 0.66;
        long hitCount = definition.getTriggerKeywords().stream()
                .filter(keyword -> message != null && message.toLowerCase().contains(keyword.toLowerCase()))
                .count();
        confidence += Math.min(0.24, hitCount * 0.08);
        if (entities.containsKey("product_id")) {
            confidence += 0.08;
        }
        return Math.min(0.98, confidence);
    }

    private String buildReason(AgentIntentDefinition definition, RagContext ragContext, double confidence) {
        return "命中意图：" + definition.getName()
                + "；执行模式：" + definition.getDefaultMode()
                + "；置信度：" + String.format("%.2f", confidence)
                + "；已注入经验：" + ragContext.getSuccessExperiences().size()
                + " 条。";
    }
}
