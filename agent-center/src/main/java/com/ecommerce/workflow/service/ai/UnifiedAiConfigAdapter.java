package com.ecommerce.workflow.service.ai;

import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UnifiedAiConfigAdapter {

    private static final Logger log = LoggerFactory.getLogger(UnifiedAiConfigAdapter.class);

    public AiProviderConfig convertToLegacy(UnifiedAiConfig unified) {
        if (unified == null)
            return null;

        AiProviderConfig legacy = new AiProviderConfig();
        legacy.setId(unified.getId());
        legacy.setProviderName(unified.getConfigName());
        legacy.setProviderType(unified.getProviderType());
        legacy.setBaseUrl(unified.getBaseUrl());
        legacy.setApiKey(unified.getApiKey());
        legacy.setModels(unified.getModels());
        legacy.setDefaultModel(unified.getDefaultModel());
        legacy.setPriority(unified.getPriority());
        legacy.setEnabled(unified.getEnabled());

        legacy.setRequestConfig(unified.getRequestTemplate());
        legacy.setResponseConfig(unified.getResponseTemplate());
        legacy.setAuthConfig(unified.getAuthTemplate());

        legacy.setCapabilities(unified.getCapabilities());
        legacy.setQualityScore(unified.getQualityScore());
        legacy.setSpeedScore(unified.getSpeedScore());
        legacy.setCostRate(unified.getCostRate());

        legacy.setDailyQuota(unified.getDailyQuota());
        legacy.setDailyUsed(unified.getDailyUsed());
        legacy.setFailCount(unified.getFailCount());
        legacy.setLastSuccessAt(unified.getLastSuccessAt());
        legacy.setLastFailAt(unified.getLastFailAt());

        if (unified.getTaskTypes() != null && unified.getTaskTypes().contains("video")) {
            legacy.setTimeoutMs(120000L);
        } else {
            legacy.setTimeoutMs(60000L);
        }
        legacy.setMaxRetries(2);

        // 构建extraParams，合并extended_params
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode extraParamsNode = mapper.createObjectNode();

            // 添加基础参数
            extraParamsNode.put("supportsVision",
                    unified.getTaskTypes() != null && unified.getTaskTypes().contains("vision"));
            extraParamsNode.put("routingStrategy",
                    unified.getRoutingStrategy() != null ? unified.getRoutingStrategy() : "balanced");

            // 合并extended_params（如果存在）
            if (unified.getExtendedParams() != null && !unified.getExtendedParams().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode extendedNode = mapper.readTree(unified.getExtendedParams());
                    if (extendedNode.isObject()) {
                        extendedNode.fields().forEachRemaining(entry -> {
                            extraParamsNode.set(entry.getKey(), entry.getValue());
                        });
                    }
                } catch (Exception e) {
                    // 如果解析失败，忽略extended_params
                }
            }

            legacy.setExtraParams(mapper.writeValueAsString(extraParamsNode));
        } catch (Exception e) {
            // 降级处理：使用原始逻辑
            String extraParams = String.format("{\"supportsVision\":%s,\"routingStrategy\":\"%s\"}",
                    unified.getTaskTypes() != null && unified.getTaskTypes().contains("vision"),
                    unified.getRoutingStrategy() != null ? unified.getRoutingStrategy() : "balanced");
            legacy.setExtraParams(extraParams);
        }

        legacy.setCreatedBy(unified.getCreatedBy());
        legacy.setUpdatedBy(unified.getUpdatedBy());
        legacy.setCreatedAt(unified.getCreatedAt());
        legacy.setUpdatedAt(unified.getUpdatedAt());
        legacy.setDeleted(unified.getDeleted());

        return legacy;
    }

    public List<AiProviderConfig> convertToLegacyList(List<UnifiedAiConfig> unifiedList) {
        if (unifiedList == null || unifiedList.isEmpty()) {
            return List.of();
        }
        return unifiedList.stream()
                .map(this::convertToLegacy)
                .collect(Collectors.toList());
    }
}
