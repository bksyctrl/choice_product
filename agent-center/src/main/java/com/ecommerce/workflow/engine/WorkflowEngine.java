package com.ecommerce.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.WorkflowDefinition;
import com.ecommerce.workflow.entity.WorkflowInstance;
import com.ecommerce.workflow.entity.WorkflowNode;
import com.ecommerce.workflow.entity.WorkflowNodeExecution;
import com.ecommerce.workflow.mapper.WorkflowDefinitionMapper;
import com.ecommerce.workflow.mapper.WorkflowInstanceMapper;
import com.ecommerce.workflow.mapper.WorkflowNodeExecutionMapper;
import com.ecommerce.workflow.mapper.WorkflowNodeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowNodeExecutionMapper nodeExecutionMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final NodeExecutorFactory nodeExecutorFactory;
    private final ObjectMapper objectMapper;
    private final WorkflowEventPublisher eventPublisher;
    private final com.ecommerce.workflow.service.ai.GptChatService gptChatService;

    public WorkflowEngine(WorkflowInstanceMapper workflowInstanceMapper, 
                        WorkflowNodeExecutionMapper nodeExecutionMapper, 
                        WorkflowDefinitionMapper workflowDefinitionMapper, 
                        WorkflowNodeMapper workflowNodeMapper, 
                        NodeExecutorFactory nodeExecutorFactory, 
                        ObjectMapper objectMapper, 
                        WorkflowEventPublisher eventPublisher,
                        com.ecommerce.workflow.service.ai.GptChatService gptChatService) {
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.nodeExecutorFactory = nodeExecutorFactory;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.gptChatService = gptChatService;
    }

    @Transactional
    public WorkflowInstance createInstance(Long workflowId, String triggerType, Long userId, Map<String, Object> inputParams) {
        log.info("创建工作流实例: workflowId={}, triggerType={}, userId={}", workflowId, triggerType, userId);

        WorkflowDefinition definition = workflowDefinitionMapper.selectById(workflowId);
        if (definition == null) {
            throw new RuntimeException("工作流定义不存在: " + workflowId);
        }

        String instanceNo = generateInstanceNo();

        WorkflowInstance instance = new WorkflowInstance();
        instance.setInstanceNo(instanceNo);
        instance.setWorkflowId(workflowId);
        instance.setWorkflowName(definition.getWorkflowName());
        instance.setStatus(WorkflowStatus.PENDING.name());
        instance.setTriggerType(triggerType);
        instance.setTriggerUserId(userId);

        try {
            instance.setInputParams(objectMapper.writeValueAsString(inputParams));
        } catch (JsonProcessingException e) {
            log.error("序列化输入参数失败", e);
            instance.setInputParams("{}");
        }

        instance.setProgress(java.math.BigDecimal.ZERO);
        instance.setStartTime(LocalDateTime.now());

        workflowInstanceMapper.insert(instance);

        initializeNodeExecutions(instance, definition.getDagConfig());

        eventPublisher.publishWorkflowCreated(instance);

        log.info("工作流实例创建成功: instanceNo={}", instanceNo);
        return instance;
    }

    private void initializeNodeExecutions(WorkflowInstance instance, String dagConfigJson) {
        try {
            JsonNode dagConfig = objectMapper.readTree(dagConfigJson);
            JsonNode nodes = dagConfig.get("nodes");
            int order = 0;

            if (nodes != null && nodes.isArray()) {
                for (JsonNode node : nodes) {
                    WorkflowNodeExecution execution = new WorkflowNodeExecution();
                    execution.setInstanceId(instance.getId());
                    execution.setNodeId(node.has("id") ? node.get("id").asLong() : (long) order);
                    execution.setNodeCode(node.has("code") ? node.get("code").asText() : "node_" + order);
                    execution.setNodeName(node.has("name") ? node.get("name").asText() : "节点" + order);
                    execution.setExecOrder(order++);
                    execution.setStatus(NodeExecutionStatus.PENDING.name());

                    nodeExecutionMapper.insert(execution);
                }
            }
        } catch (Exception e) {
            log.error("初始化节点执行记录失败", e);
        }
    }

    public void startExecution(Long instanceId) {
        log.info("开始执行工作流实例: instanceId={}", instanceId);

        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        validateStatusTransition(instance, WorkflowStatus.RUNNING);

        instance.setStatus(WorkflowStatus.RUNNING.name());
        workflowInstanceMapper.updateById(instance);

        executeNextNodes(instanceId);
    }

    public void executeNextNodes(Long instanceId) {
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);

        List<WorkflowNodeExecution> pendingNodes = getExecutableNodes(instance);

        for (WorkflowNodeExecution nodeExec : pendingNodes) {
            executeNodeAsync(instance, nodeExec);
        }
    }

    private List<WorkflowNodeExecution> getExecutableNodes(WorkflowInstance instance) {
        WorkflowDefinition definition = workflowDefinitionMapper.selectById(instance.getWorkflowId());
        if (definition == null || definition.getDagConfig() == null) {
            return new ArrayList<>();
        }

        try {
            JsonNode dagConfig = objectMapper.readTree(definition.getDagConfig());

            List<WorkflowNodeExecution> allNodes = nodeExecutionMapper.selectList(
                    new QueryWrapper<WorkflowNodeExecution>()
                            .eq("instance_id", instance.getId())
                            .eq("status", NodeExecutionStatus.PENDING.name())
                            .orderByAsc("exec_order")
            );

            List<WorkflowNodeExecution> executableNodes = new ArrayList<>();

            for (WorkflowNodeExecution node : allNodes) {
                if (arePredecessorsCompleted(node.getNodeId(), instance.getId(), dagConfig)) {
                    executableNodes.add(node);
                }
            }

            return executableNodes;
        } catch (Exception e) {
            log.error("获取可执行节点失败", e);
            return new ArrayList<>();
        }
    }

    private boolean arePredecessorsCompleted(Long nodeId, Long instanceId, JsonNode dagConfig) {
        JsonNode edges = dagConfig.get("edges");
        if (edges == null || !edges.isArray()) {
            return true;
        }

        for (JsonNode edge : edges) {
            if (edge.has("target") && edge.get("target").asLong() == nodeId) {
                Long sourceNodeId = edge.get("source").asLong();
                WorkflowNodeExecution predExec = nodeExecutionMapper.selectOne(
                        new QueryWrapper<WorkflowNodeExecution>()
                                .eq("instance_id", instanceId)
                                .eq("node_id", sourceNodeId)
                );

                if (predExec == null || !NodeExecutionStatus.SUCCESS.name().equals(predExec.getStatus())) {
                    return false;
                }
            }
        }

        return true;
    }

    private void executeNodeAsync(WorkflowInstance instance, WorkflowNodeExecution nodeExec) {
        CompletableFuture.runAsync(() -> {
            try {
                executeNode(instance, nodeExec);
            } catch (Exception e) {
                log.error("节点执行异常: nodeCode={}", nodeExec.getNodeCode(), e);
                handleNodeFailure(instance, nodeExec, e);
            }
        });
    }

    private void executeNode(WorkflowInstance instance, WorkflowNodeExecution nodeExec) throws Exception {
        log.info("开始执行节点: instanceId={}, nodeCode={}", instance.getId(), nodeExec.getNodeCode());

        updateNodeStatus(nodeExec, NodeExecutionStatus.RUNNING);

        NodeExecutor executor = nodeExecutorFactory.getExecutor(nodeExec.getNodeCode());
        ExecutionContext context = buildExecutionContext(instance);

        NodeResult result = executor.execute(context);

        updateNodeSuccess(nodeExec, result);

        eventPublisher.publishNodeCompleted(instance, nodeExec, result);

        checkAndProceedWorkflow(instance);
    }

    private void handleNodeFailure(WorkflowInstance instance, WorkflowNodeExecution nodeExec, Exception e) {
        log.error("节点执行失败: nodeCode={}, error={}", nodeExec.getNodeCode(), e.getMessage());

        if (nodeExec.getRetryCount() < getMaxRetryCount(nodeExec)) {
            scheduleRetry(instance, nodeExec);
        } else {
            updateNodeFailed(nodeExec, e.getMessage());
            eventPublisher.publishNodeFailed(instance, nodeExec, e);

            WorkflowNode nodeDef = workflowNodeMapper.selectOne(
                    new QueryWrapper<WorkflowNode>().eq("node_code", nodeExec.getNodeCode())
            );

            if (nodeDef != null && nodeDef.getIsManualReview() != null && nodeDef.getIsManualReview() == 1) {
                pauseWorkflowForReview(instance, nodeExec);
            } else {
                failWorkflow(instance, "节点执行失败: " + nodeExec.getNodeCode());
            }
        }
    }

    private void checkAndProceedWorkflow(WorkflowInstance instance) {
        boolean hasPendingOrRunning = nodeExecutionMapper.exists(
                new QueryWrapper<WorkflowNodeExecution>()
                        .eq("instance_id", instance.getId())
                        .in("status", NodeExecutionStatus.PENDING.name(), NodeExecutionStatus.RUNNING.name())
        );

        if (!hasPendingOrRunning) {
            completeWorkflow(instance);
        } else {
            executeNextNodes(instance.getId());
        }
    }

    private void completeWorkflow(WorkflowInstance instance) {
        log.info("工作流执行完成: instanceId={}", instance.getId());

        instance.setStatus(WorkflowStatus.SUCCESS.name());
        instance.setEndTime(LocalDateTime.now());
        if (instance.getStartTime() != null) {
            instance.setDurationMs(java.time.Duration.between(instance.getStartTime(), instance.getEndTime()).toMillis());
        }
        instance.setProgress(new java.math.BigDecimal("100.00"));

        collectOutputResults(instance);

        workflowInstanceMapper.updateById(instance);

        eventPublisher.publishWorkflowCompleted(instance);
    }

    private void failWorkflow(WorkflowInstance instance, String reason) {
        log.error("工作流执行失败: instanceId={}, reason={}", instance.getId(), reason);

        instance.setStatus(WorkflowStatus.FAILED.name());
        instance.setErrorMessage(reason);
        instance.setEndTime(LocalDateTime.now());

        workflowInstanceMapper.updateById(instance);

        eventPublisher.publishWorkflowFailed(instance, reason);
    }

    public void pauseWorkflow(Long instanceId) {
        pauseWorkflow(instanceId, "用户手动暂停");
    }

    public void pauseWorkflow(Long instanceId, String reason) {
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        validateStatusTransition(instance, WorkflowStatus.PAUSED);

        instance.setStatus(WorkflowStatus.PAUSED.name());
        workflowInstanceMapper.updateById(instance);

        eventPublisher.publishWorkflowPaused(instance, reason);
    }

    public void resumeWorkflow(Long instanceId) {
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);

        if (!WorkflowStatus.PAUSED.name().equals(instance.getStatus())) {
            throw new RuntimeException("只有暂停状态的工作流才能恢复执行");
        }

        instance.setStatus(WorkflowStatus.RUNNING.name());
        workflowInstanceMapper.updateById(instance);

        executeNextNodes(instanceId);
    }

    public void cancelWorkflow(Long instanceId) {
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        validateStatusTransition(instance, WorkflowStatus.CANCELLED);

        instance.setStatus(WorkflowStatus.CANCELLED.name());
        instance.setEndTime(LocalDateTime.now());
        workflowInstanceMapper.updateById(instance);

        cancelRunningNodes(instance.getId());

        eventPublisher.publishWorkflowCancelled(instance);
    }
    
    public void executeConditionalBranch(Long instanceId, String conditionExpression, 
                                         Long trueBranchNodeId, Long falseBranchNodeId) {
        log.info("开始执行条件分支: instanceId={}, condition={}", instanceId, conditionExpression);
        
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        boolean conditionResult = evaluateCondition(conditionExpression, instance);
        
        Long nextNodeId = conditionResult ? trueBranchNodeId : falseBranchNodeId;
        
        log.info("条件分支评估结果: condition={}, result={}, nextNode={}", 
            conditionExpression, conditionResult, nextNodeId);
        
        WorkflowNodeExecution branchNode = nodeExecutionMapper.selectOne(
            new QueryWrapper<WorkflowNodeExecution>()
                .eq("instance_id", instanceId)
                .eq("node_id", nextNodeId)
        );
        
        if (branchNode != null && NodeExecutionStatus.PENDING.name().equals(branchNode.getStatus())) {
            executeNodeAsync(instance, branchNode);
        }
    }
    
    private boolean evaluateCondition(String expression, WorkflowInstance instance) {
        try {
            Map<String, Object> context = new HashMap<>();
            if (instance.getInputParams() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> inputParams = objectMapper.readValue(
                    instance.getInputParams(), Map.class);
                context.putAll(inputParams);
            }
            
            context.put("instanceId", instance.getId());
            context.put("workflowId", instance.getWorkflowId());
            context.put("status", instance.getStatus());
            
            String evaluationPrompt = String.format("""
                你是条件表达式评估专家。请根据以下上下文数据评估条件表达式的真假值。
                
                条件表达式: %s
                
                上下文数据:
                %s
                
                请只回答 "true" 或 "false"，不要包含其他内容。
                """, expression, objectMapper.writeValueAsString(context));
            
            String result = gptChatService.chat(evaluationPrompt).trim().toLowerCase();
            return "true".equals(result);
            
        } catch (Exception e) {
            log.warn("条件表达式评估失败，默认返回false: {}", e.getMessage());
            return false;
        }
    }
    
    public Long generateAndExecuteSubWorkflow(Long parentInstanceId, 
                                              String subWorkflowType,
                                              Map<String, Object> params) {
        log.info("生成子工作流: parentInstanceId={}, type={}", parentInstanceId, subWorkflowType);
        
        try {
            WorkflowDefinition subWorkflowDef = createDynamicWorkflowDefinition(
                subWorkflowType, params);
            
            workflowDefinitionMapper.insert(subWorkflowDef);
            
            WorkflowInstance subInstance = createInstance(
                subWorkflowDef.getId(), 
                "SUB_WORKFLOW", 
                1L,
                params
            );
            
            workflowInstanceMapper.updateById(subInstance);
            
            startExecution(subInstance.getId());
            
            log.info("子工作流已创建并开始执行: subInstanceId={}, parentId={}", 
                subInstance.getId(), parentInstanceId);
            
            return subInstance.getId();
            
        } catch (Exception e) {
            log.error("生成子工作流失败", e);
            throw new RuntimeException("子工作流创建失败: " + e.getMessage(), e);
        }
    }
    
    private WorkflowDefinition createDynamicWorkflowDefinition(String workflowType, 
                                                               Map<String, Object> params) 
        throws JsonProcessingException {
        
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setWorkflowCode("DYNAMIC_" + workflowType + "_" + System.currentTimeMillis());
        definition.setWorkflowName("动态" + workflowType + "工作流");
        definition.setBusinessType(workflowType);
        definition.setStatus(1);
        
        String dagConfig = buildDynamicDagConfig(workflowType, params);
        definition.setDagConfig(dagConfig);
        
        definition.setNodeCount(countNodesInDag(dagConfig));
        definition.setDescription("根据业务需求动态生成的工作流");
        definition.setCreatedBy(1L);
        definition.setCreatedAt(LocalDateTime.now());
        
        return definition;
    }
    
    private String buildDynamicDagConfig(String workflowType, Map<String, Object> params) 
        throws JsonProcessingException {
        
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode edges = objectMapper.createArrayNode();
        
        switch (workflowType.toLowerCase()) {
            case "video_production":
                buildVideoProductionDAG(nodes, edges, params);
                break;
            case "product_analysis":
                buildProductAnalysisDAG(nodes, edges, params);
                break;
            case "content_review":
                buildContentReviewDAG(nodes, edges, params);
                break;
            default:
                buildGenericDAG(nodes, edges, params);
        }
        
        ObjectNode dagConfig = objectMapper.createObjectNode();
        dagConfig.set("nodes", nodes);
        dagConfig.set("edges", edges);
        
        return objectMapper.writeValueAsString(dagConfig);
    }
    
    private void buildVideoProductionDAG(ArrayNode nodes, ArrayNode edges, Map<String, Object> params) {
        addNode(nodes, 1, "script_generation", "脚本生成", "ai_node");
        addNode(nodes, 2, "prompt_engineering", "提示词工程", "ai_node");
        addNode(nodes, 3, "video_generation", "视频生成", "external_api");
        addNode(nodes, 4, "quality_check", "质量检查", "review_node");
        addNode(nodes, 5, "compliance_check", "合规检查", "rule_node");
        addNode(nodes, 6, "final_output", "最终输出", "output_node");
        
        addEdge(edges, 1, 2);
        addEdge(edges, 2, 3);
        addEdge(edges, 3, 4);
        addEdge(edges, 4, 5);
        addEdge(edges, 5, 6);
    }
    
    private void buildProductAnalysisDAG(ArrayNode nodes, ArrayNode edges, Map<String, Object> params) {
        addNode(nodes, 1, "data_collection", "数据采集", "data_node");
        addNode(nodes, 2, "market_analysis", "市场分析", "analysis_node");
        addNode(nodes, 3, "competitor_analysis", "竞品分析", "analysis_node");
        addNode(nodes, 4, "scoring_6d", "6维评分", "scoring_node");
        addNode(nodes, 5, "risk_assessment", "风险评估", "evaluation_node");
        addNode(nodes, 6, "recommendation_output", "推荐输出", "output_node");
        
        addEdge(edges, 1, 2);
        addEdge(edges, 1, 3);
        addEdge(edges, 2, 4);
        addEdge(edges, 3, 4);
        addEdge(edges, 4, 5);
        addEdge(edges, 5, 6);
    }
    
    private void buildContentReviewDAG(ArrayNode nodes, ArrayNode edges, Map<String, Object> params) {
        addNode(nodes, 1, "content_fetch", "内容获取", "data_node");
        addNode(nodes, 2, "ai_review", "AI审核", "ai_node");
        addNode(nodes, 3, "rule_check", "规则检查", "rule_node");
        addNode(nodes, 4, "human_review", "人工审核", "manual_node");
        addNode(nodes, 5, "review_report", "审核报告", "output_node");
        
        addEdge(edges, 1, 2);
        addEdge(edges, 1, 3);
        addEdge(edges, 2, 4);
        addEdge(edges, 3, 4);
        addEdge(edges, 4, 5);
    }
    
    private void buildGenericDAG(ArrayNode nodes, ArrayNode edges, Map<String, Object> params) {
        int nodeCount = params.containsKey("nodeCount") ? 
            ((Number) params.get("nodeCount")).intValue() : 3;
        
        for (int i = 1; i <= nodeCount; i++) {
            addNode(nodes, i, "task_" + i, "任务" + i, "generic_node");
        }
        
        for (int i = 1; i < nodeCount; i++) {
            addEdge(edges, i, i + 1);
        }
    }
    
    private void addNode(ArrayNode nodes, long id, String code, String name, String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("code", code);
        node.put("name", name);
        node.put("type", type);
        nodes.add(node);
    }
    
    private void addEdge(ArrayNode edges, long source, long target) {
        ObjectNode edge = objectMapper.createObjectNode();
        edge.put("source", source);
        edge.put("target", target);
        edges.add(edge);
    }
    
    private int countNodesInDag(String dagConfig) throws JsonProcessingException {
        JsonNode config = objectMapper.readTree(dagConfig);
        JsonNode nodes = config.get("nodes");
        return nodes != null && nodes.isArray() ? nodes.size() : 0;
    }

    private void validateStatusTransition(WorkflowInstance instance, WorkflowStatus targetStatus) {
        if (instance == null) {
            throw new RuntimeException("工作流实例不存在");
        }
        WorkflowStatus currentStatus = WorkflowStatus.valueOf(instance.getStatus());
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new RuntimeException(
                    String.format("不允许的状态转换: %s -> %s", currentStatus, targetStatus)
            );
        }
    }

    private ExecutionContext buildExecutionContext(WorkflowInstance instance) {
        ExecutionContext context = new ExecutionContext();
        context.setInstanceId(instance.getId());
        context.setInstanceNo(instance.getInstanceNo());
        context.setWorkflowId(instance.getWorkflowId());

        try {
            if (instance.getInputParams() != null && !instance.getInputParams().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> inputParams = objectMapper.readValue(instance.getInputParams(), Map.class);
                context.getVariables().putAll(inputParams);
            }
            if (instance.getContextData() != null && !instance.getContextData().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> contextData = objectMapper.readValue(instance.getContextData(), Map.class);
                context.getVariables().putAll(contextData);
            }
        } catch (Exception e) {
            log.warn("构建执行上下文失败", e);
        }

        return context;
    }

    private void updateNodeStatus(WorkflowNodeExecution nodeExec, NodeExecutionStatus status) {
        nodeExec.setStatus(status.name());
        if (status == NodeExecutionStatus.RUNNING) {
            nodeExec.setStartTime(LocalDateTime.now());
        }
        nodeExecutionMapper.updateById(nodeExec);
    }

    private void updateNodeSuccess(WorkflowNodeExecution nodeExec, NodeResult result) {
        nodeExec.setStatus(NodeExecutionStatus.SUCCESS.name());
        nodeExec.setEndTime(LocalDateTime.now());

        if (nodeExec.getStartTime() != null) {
            nodeExec.setDurationMs(java.time.Duration.between(nodeExec.getStartTime(), LocalDateTime.now()).toMillis());
        }

        try {
            if (result != null && result.getOutput() != null) {
                nodeExec.setOutputResult(objectMapper.writeValueAsString(result.getOutput()));
            }
        } catch (Exception e) {
            log.error("序列化节点输出结果失败", e);
            nodeExec.setOutputResult(result != null ? result.toString() : "{}");
        }

        nodeExecutionMapper.updateById(nodeExec);
    }

    private void updateNodeFailed(WorkflowNodeExecution nodeExec, String errorMessage) {
        nodeExec.setStatus(NodeExecutionStatus.FAILED.name());
        nodeExec.setEndTime(LocalDateTime.now());
        nodeExec.setErrorMessage(errorMessage);
        nodeExecutionMapper.updateById(nodeExec);
    }

    private void pauseWorkflowForReview(WorkflowInstance instance, WorkflowNodeExecution nodeExec) {
        log.info("等待人工审核节点: instanceId={}, nodeCode={}", instance.getId(), nodeExec.getNodeCode());

        nodeExec.setStatus(NodeExecutionStatus.WAITING_REVIEW.name());
        nodeExecutionMapper.updateById(nodeExec);

        instance.setStatus(WorkflowStatus.PAUSED.name());
        instance.setErrorMessage("等待审核: " + nodeExec.getNodeCode());
        workflowInstanceMapper.updateById(instance);

        eventPublisher.publishWorkflowPaused(instance, "等待审核: " + nodeExec.getNodeName());
    }

    private void collectOutputResults(WorkflowInstance instance) {
        try {
            List<WorkflowNodeExecution> completedNodes = nodeExecutionMapper.selectList(
                    new QueryWrapper<WorkflowNodeExecution>()
                            .eq("instance_id", instance.getId())
                            .eq("status", NodeExecutionStatus.SUCCESS.name())
            );

            Map<String, Object> outputResults = new java.util.HashMap<>();
            for (WorkflowNodeExecution node : completedNodes) {
                if (node.getOutputResult() != null) {
                    outputResults.put(node.getNodeCode(), node.getOutputResult());
                }
            }

            if (!outputResults.isEmpty()) {
                instance.setOutputResult(objectMapper.writeValueAsString(outputResults));
            }
        } catch (Exception e) {
            log.error("收集输出结果失败", e);
        }
    }

    private void cancelRunningNodes(Long instanceId) {
        List<WorkflowNodeExecution> runningNodes = nodeExecutionMapper.selectList(
                new QueryWrapper<WorkflowNodeExecution>()
                        .eq("instance_id", instanceId)
                        .in("status", NodeExecutionStatus.PENDING.name(), NodeExecutionStatus.RUNNING.name())
        );

        for (WorkflowNodeExecution node : runningNodes) {
            node.setStatus(NodeExecutionStatus.CANCELLED.name());
            node.setErrorMessage("工作流已取消");
            nodeExecutionMapper.updateById(node);
        }

        log.info("已取消{}个运行中的节点", runningNodes.size());
    }

    private String generateInstanceNo() {
        return "WF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private int getMaxRetryCount(WorkflowNodeExecution nodeExec) {
        WorkflowNode nodeDef = workflowNodeMapper.selectOne(
                new QueryWrapper<WorkflowNode>().eq("node_code", nodeExec.getNodeCode())
        );
        return nodeDef != null && nodeDef.getRetryCount() != null ? nodeDef.getRetryCount() : 3;
    }

    private void scheduleRetry(WorkflowInstance instance, WorkflowNodeExecution nodeExec) {
        nodeExec.setRetryCount(nodeExec.getRetryCount() + 1);
        nodeExecutionMapper.updateById(nodeExec);

        log.info("安排节点重试: nodeCode={}, retryCount={}", nodeExec.getNodeCode(), nodeExec.getRetryCount());

        try {
            Thread.sleep(getRetryInterval(nodeExec));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executeNodeAsync(instance, nodeExec);
    }

    private long getRetryInterval(WorkflowNodeExecution nodeExec) {
        WorkflowNode nodeDef = workflowNodeMapper.selectOne(
                new QueryWrapper<WorkflowNode>().eq("node_code", nodeExec.getNodeCode())
        );
        return nodeDef != null && nodeDef.getRetryInterval() != null ? nodeDef.getRetryInterval() : 5000L;
    }
}
