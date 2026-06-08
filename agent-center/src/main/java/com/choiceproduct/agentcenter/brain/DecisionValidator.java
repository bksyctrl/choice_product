package com.choiceproduct.agentcenter.brain;

import com.choiceproduct.agentcenter.registry.AgentIntentDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DecisionValidator {
    public void validate(DispatchDecision decision, AgentIntentDefinition definition) {
        List<String> missing = new ArrayList<>();
        for (String requiredParam : definition.getRequiredParams()) {
            if (!decision.getEntities().containsKey(requiredParam)
                    || decision.getEntities().get(requiredParam) == null
                    || String.valueOf(decision.getEntities().get(requiredParam)).isBlank()) {
                missing.add(requiredParam);
            }
        }

        decision.setMissingParams(missing);
        if (!missing.isEmpty()) {
            decision.setCanExecuteNow(false);
            decision.setMode(ExecutionMode.ASK_USER);
            decision.setNextQuestion("还缺少这些信息：" + String.join("、", missing) + "。你补充后我再派给对应专家。");
        }
    }
}
