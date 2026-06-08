package com.choiceproduct.agentcenter.registry;

import com.choiceproduct.agentcenter.brain.ExecutionMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentIntentRegistry {
    private final List<AgentIntentDefinition> definitions = List.of(
            new AgentIntentDefinition(
                    "product_detail_query",
                    "product_detail_query",
                    "商品详情只读查询",
                    "查看商品详情、字段、回执、attributes、selling_points 等只读信息。",
                    ExecutionMode.READONLY,
                    List.of("商品", "详情", "字段", "回执", "attributes", "selling_points", "站内详情"),
                    List.of("product_id"),
                    List.of("readonly_db"),
                    List.of("system_architect", "data_method_lead"),
                    "LOW",
                    90),
            new AgentIntentDefinition(
                    "readonly_data_query",
                    "readonly_data_query",
                    "只读数据核查",
                    "查询数据库、规则库、历史经验库，帮助判断问题根因。",
                    ExecutionMode.READONLY,
                    List.of("数据库", "查表", "规则库", "历史经验", "字段", "为什么", "数据"),
                    List.of(),
                    List.of("readonly_db"),
                    List.of("system_architect", "data_method_lead"),
                    "LOW",
                    80),
            new AgentIntentDefinition(
                    "execution_handoff",
                    "execution_handoff",
                    "执行交接",
                    "当用户确认可以执行时，生成给业务执行者能听懂的派工单。",
                    ExecutionMode.HANDOFF,
                    List.of("执行", "开始", "部署", "修改", "交给", "可以执行", "动手"),
                    List.of(),
                    List.of("code_agent", "db_migration", "frontend_patch"),
                    List.of("project_planner", "system_architect", "ux_lead"),
                    "MEDIUM",
                    70),
            new AgentIntentDefinition(
                    "general_chat",
                    "general_chat",
                    "通用专家沟通",
                    "用于方案讨论、思路确认、专家团队协同说明。",
                    ExecutionMode.CHAT,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("team_manager"),
                    "LOW",
                    1));

    public List<AgentIntentDefinition> listDefinitions() {
        return definitions;
    }

    public Optional<AgentIntentDefinition> findByIntentCode(String intentCode) {
        return definitions.stream()
                .filter(item -> item.getIntentCode().equals(intentCode))
                .findFirst();
    }

    public AgentIntentDefinition defaultIntent() {
        return definitions.stream()
                .filter(item -> "general_chat".equals(item.getIntentCode()))
                .findFirst()
                .orElse(definitions.get(definitions.size() - 1));
    }

    public AgentIntentDefinition matchByKeyword(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return definitions.stream()
                .filter(item -> item.getTriggerKeywords().stream()
                        .anyMatch(keyword -> text.contains(keyword.toLowerCase())))
                .max(Comparator.comparingInt(AgentIntentDefinition::getPriority))
                .orElse(defaultIntent());
    }
}
