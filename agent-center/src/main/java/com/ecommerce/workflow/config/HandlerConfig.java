package com.ecommerce.workflow.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ecommerce.workflow.agent.handler.AbstractIntentHandler;
import com.ecommerce.workflow.agent.handler.ComplianceCheckHandler;
import com.ecommerce.workflow.agent.handler.DataAnalysisHandler;
import com.ecommerce.workflow.agent.handler.GeneralChatHandler;
import com.ecommerce.workflow.agent.handler.ProductSelectionHandler;
import com.ecommerce.workflow.agent.handler.ScriptGenerationHandler;
import com.ecommerce.workflow.agent.handler.SelfEvolutionHandler;
import com.ecommerce.workflow.agent.handler.SkillCreateHandler;
import com.ecommerce.workflow.agent.handler.VideoGenerationHandler;
import com.ecommerce.workflow.agent.handler.WorkflowQueryHandler;
import com.ecommerce.workflow.agent.impl.SelfEvolutionAgent;
import com.ecommerce.workflow.engine.WorkflowEngine;
import com.ecommerce.workflow.mapper.WorkflowDefinitionMapper;
import com.ecommerce.workflow.mapper.WorkflowInstanceMapper;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.ecommerce.workflow.service.session.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class HandlerConfig {

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private GptChatService gptChatService;

    @Autowired
    private WorkflowInstanceMapper workflowInstanceMapper;

    @Autowired
    private SelfEvolutionAgent selfEvolutionAgent;

    @Autowired
    private SkillConfigService skillConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    private void injectDependencies(AbstractIntentHandler handler) {
        handler.setWorkflowEngine(workflowEngine);
        handler.setSessionService(sessionService);
        handler.setWorkflowDefinitionMapper(workflowDefinitionMapper);
        handler.setGptChatService(gptChatService);
    }

    @Bean
    public ProductSelectionHandler productSelectionHandler() {
        ProductSelectionHandler handler = new ProductSelectionHandler();
        injectDependencies(handler);
        return handler;
    }

    @Bean
    public ScriptGenerationHandler scriptGenerationHandler() {
        ScriptGenerationHandler handler = new ScriptGenerationHandler();
        injectDependencies(handler);
        return handler;
    }

    @Bean
    public VideoGenerationHandler videoGenerationHandler() {
        VideoGenerationHandler handler = new VideoGenerationHandler();
        injectDependencies(handler);
        return handler;
    }

    @Bean
    public DataAnalysisHandler dataAnalysisHandler() {
        DataAnalysisHandler handler = new DataAnalysisHandler();
        injectDependencies(handler);
        return handler;
    }

    @Bean
    public ComplianceCheckHandler complianceCheckHandler() {
        ComplianceCheckHandler handler = new ComplianceCheckHandler();
        injectDependencies(handler);
        return handler;
    }

    @Bean
    public WorkflowQueryHandler workflowQueryHandler() {
        WorkflowQueryHandler handler = new WorkflowQueryHandler();
        injectDependencies(handler);
        handler.setWorkflowInstanceMapper(workflowInstanceMapper);
        return handler;
    }

    @Bean
    public SelfEvolutionHandler selfEvolutionHandler() {
        SelfEvolutionHandler handler = new SelfEvolutionHandler();
        injectDependencies(handler);
        handler.setSelfEvolutionAgent(selfEvolutionAgent);
        handler.setSkillConfigService(skillConfigService);
        return handler;
    }

    @Bean
    public SkillCreateHandler skillCreateHandler() {
        SkillCreateHandler handler = new SkillCreateHandler();
        injectDependencies(handler);
        handler.setSkillConfigService(skillConfigService);
        handler.setObjectMapper(objectMapper);
        return handler;
    }

    @Bean
    public GeneralChatHandler generalChatHandler() {
        GeneralChatHandler handler = new GeneralChatHandler();
        injectDependencies(handler);
        return handler;
    }
}
