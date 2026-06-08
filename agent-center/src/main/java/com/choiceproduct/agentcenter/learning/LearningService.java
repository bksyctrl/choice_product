package com.choiceproduct.agentcenter.learning;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LearningService {
    private static final Logger log = LoggerFactory.getLogger(LearningService.class);

    public void recordTurn(AgentRequest request, DispatchDecision decision, AgentResponse response) {
        log.info(
                "agent turn learned session={} intent={} mode={} success={}",
                request.getSessionId(),
                decision.getIntentCode(),
                decision.getMode(),
                response.isSuccess());
    }
}
