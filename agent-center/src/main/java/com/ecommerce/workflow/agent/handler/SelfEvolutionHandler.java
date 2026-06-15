package com.ecommerce.workflow.agent.handler;

import java.util.HashMap;
import java.util.Map;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.agent.impl.SelfEvolutionAgent;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.service.evolution.SkillConfigService;

public class SelfEvolutionHandler extends AbstractIntentHandler {

    private SelfEvolutionAgent selfEvolutionAgent;
    private SkillConfigService skillConfigService;

    public void setSelfEvolutionAgent(SelfEvolutionAgent selfEvolutionAgent) {
        this.selfEvolutionAgent = selfEvolutionAgent;
    }

    public void setSkillConfigService(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理自进化/系统配置请求: {}", request.getMessage());

        try {
            AgentResponse evolutionResponse = selfEvolutionAgent.process(request);
            sessionService.saveMessage(session.getSessionId(), "assistant", evolutionResponse.getMessage());
            return evolutionResponse;
        } catch (Exception e) {
            log.error("自进化Agent处理失败", e);

            Map<String, Object> currentSkills = new HashMap<>();
            for (var skill : skillConfigService.getAllActiveSkills()) {
                currentSkills.put(skill.getSkillCode(), Map.of(
                        "name", skill.getSkillName(),
                        "version", skill.getVersion(),
                        "successRate", skill.getSuccessRate() != null ? skill.getSuccessRate().toString() : "--",
                        "usageCount", skill.getUsageCount()
                ));
            }

            String fallbackReply = String.format("""
                    自进化系统
                    
                    当前系统状态:
                    %s
                    
                    你可以通过以下方式与我交互:
                    - 配置Skill: "帮我调整选品策略，重点看家居类目"
                    - 查看表现: "系统最近表现如何"
                    - 触发进化: "启动一次进化"
                    - 记录数据: "记录投放数据: 播放10w+ GMV 5000"
                    - 查看记忆: "看看最近的案例"
                    
                    我会自动帮你配置最优的Skill参数
                    """,
                    currentSkills.entrySet().stream()
                            .map(entry -> String.format("- `%s` v%s (成功率%s, 使用%d次)",
                                    entry.getKey(),
                                    ((Map<String, Object>)entry.getValue()).get("version"),
                                    ((Map<String, Object>)entry.getValue()).get("successRate"),
                                    ((Map<String, Object>)entry.getValue()).get("usageCount")))
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("- 暂无Skill配置")
            );

            sessionService.saveMessage(session.getSessionId(), "assistant", fallbackReply);
            return AgentResponse.success(fallbackReply, currentSkills);
        }
    }

    @Override
    public String getIntentCode() {
        return "self_evolution";
    }
}
