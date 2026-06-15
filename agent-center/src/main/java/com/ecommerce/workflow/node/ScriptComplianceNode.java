package com.ecommerce.workflow.node;

import com.ecommerce.workflow.engine.*;
import com.ecommerce.workflow.agent.impl.ComplianceControlAgent;
import com.ecommerce.workflow.agent.AgentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ScriptComplianceNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptComplianceNode.class);
    private final ComplianceControlAgent complianceAgent;

    public ScriptComplianceNode(ComplianceControlAgent complianceAgent) {
        this.complianceAgent = complianceAgent;
    }

    @Override
    public String getNodeCode() {
        return "script_compliance";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("执行脚本合规检查节点");

        Map<String, Object> scriptData = context.getNodeOutput("script_generate");
        if (scriptData == null) {
            return NodeResult.failure("缺少脚本生成节点的输出数据");
        }

        String scriptContent = scriptData.containsKey("content") ?
                scriptData.get("content").toString() :
                scriptData.toString();

        String platform = context.getVariable("platform") != null ?
                context.getVariable("platform").toString() : "douyin";

        log.info("开始合规检查: 脚本长度={}, 平台={}", scriptContent.length(), platform);

        AgentRequest request = AgentRequest.builder()
                .message(scriptContent)
                .parameter("action", "check_compliance")
                .parameter("platform", platform)
                .context(Map.of("content", scriptContent))
                .build();

        var response = complianceAgent.process(request);

        Map<String, Object> result = new HashMap<>();
        result.put("originalScript", scriptContent);
        result.put("platform", platform);
        result.put("compliancePassed", response.isSuccess());
        result.put("requiresReview", response.isRequiresHumanReview());

        if (response.getData() != null) {
            result.putAll(response.getData());
        }

        if (!response.isSuccess()) {
            log.warn("合规检查未通过: {}", response.getReviewReason());

            AgentRequest fixRequest = AgentRequest.builder()
                    .message(scriptContent)
                    .parameter("action", "fix_violations")
                    .parameter("platform", platform)
                    .context(Map.of(
                            "content", scriptContent,
                            "complianceResult", response.getData() != null ?
                                    response.getData().get("complianceResult") : null
                    ))
                    .build();

            var fixResponse = complianceAgent.process(fixRequest);

            if (fixResponse.getData() != null && fixResponse.getData().containsKey("fixedContent")) {
                result.put("fixedScript", fixResponse.getData().get("fixedContent"));
                result.put("autoFixed", true);
                log.info("自动修复合规问题完成");
            } else {
                result.put("autoFixed", false);
                context.setNodeOutput(getNodeCode(), result);
                return NodeResult.failure("合规检查未通过且无法自动修复: " + response.getReviewReason());
            }
        } else {
            result.put("autoFixed", false);
            log.info("合规检查通过");
        }

        context.setNodeOutput(getNodeCode(), result);

        return NodeResult.success(result);
    }
}
