package com.ecommerce.workflow.service.workflow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.entity.WorkflowDefinition;
import com.ecommerce.workflow.mapper.WorkflowDefinitionMapper;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WorkflowAutoGenerator {
    private static final Logger log = LoggerFactory.getLogger(WorkflowAutoGenerator.class);
    private final SkillConfigService skillConfigService;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final GptChatService gptChatService;
    private final ObjectMapper objectMapper;

    public WorkflowAutoGenerator(SkillConfigService skillConfigService,
                                  WorkflowDefinitionMapper workflowDefinitionMapper,
                                  GptChatService gptChatService,
                                  ObjectMapper objectMapper) {
        this.skillConfigService = skillConfigService;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.gptChatService = gptChatService;
        this.objectMapper = objectMapper;
    }

    public WorkflowDefinition generateFromDescription(String description) {
        log.info("开始生成工作流定义: {}", description);
        String systemPrompt = buildWorkflowGenerationPrompt();
        String userPrompt = "请根据以下描述生成工作流定义:\n\n" + description;
        String response = gptChatService.chatWithThinking(systemPrompt, userPrompt);

        try {
            Map<String, Object> workflowDef = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            return createWorkflowFromDefinition(workflowDef);
        } catch (Exception e) {
            log.warn("AI生成工作流失败，使用默认工作流", e);
            return createDefaultWorkflow(description);
        }
    }

    public WorkflowDefinition generateForSkill(SkillConfig skill) {
        log.info("为技能生成工作流: skillCode={}", skill.getSkillCode());

        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setWorkflowCode("WF_" + skill.getSkillCode());
        workflow.setWorkflowName(skill.getSkillName() + "_工作流");
        workflow.setDescription(skill.getDescription());
        workflow.setVersion(1);
        workflow.setStatus(1);
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        workflow.setDeleted(0);

        try {
            List<Map<String, Object>> nodeList = generateNodeListForSkill(skill);
            List<Map<String, Object>> edgeList = generateEdgeListForNodes(nodeList);
            workflow.setNodes(objectMapper.writeValueAsString(nodeList));
            workflow.setEdges(objectMapper.writeValueAsString(edgeList));
        } catch (Exception e) {
            log.warn("生成节点失败，使用空节点", e);
            workflow.setNodes("[]");
            workflow.setEdges("[]");
        }

        workflowDefinitionMapper.insert(workflow);

        skill.setWorkflowId(workflow.getWorkflowCode());
        Map<String, Object> params = skillConfigService.getSkillParams(skill.getSkillCode());
        skillConfigService.createOrUpdateSkill(skill.getSkillCode(), skill.getSkillName(),
                skill.getSkillCategory(), params, "技能工作流");

        return workflow;
    }

    private List<Map<String, Object>> generateNodeListForSkill(SkillConfig skill) {
        List<Map<String, Object>> nodeList = new ArrayList<>();
        String executorType = skill.getExecutorType();

        if ("WORKFLOW".equals(executorType)) {
            nodeList.add(createNode("start", "开始", "control", 100, 50));
            nodeList.add(createNode("data_fetch", "数据获取", "data", 100, 150));
            nodeList.add(createNode("process", "数据处理", "ai", 100, 250));
            nodeList.add(createNode("output", "输出结果", "output", 100, 350));
            nodeList.add(createNode("end", "结束", "control", 100, 450));
        } else if ("SCRIPT".equals(executorType)) {
            nodeList.add(createNode("start", "开始", "control", 100, 50));
            nodeList.add(createNode("script_gen", "脚本生成", "ai", 100, 150));
            nodeList.add(createNode("script_review", "脚本审核", "control", 100, 250));
            nodeList.add(createNode("end", "结束", "control", 100, 350));
        } else if ("API".equals(executorType)) {
            nodeList.add(createNode("start", "开始", "control", 100, 50));
            nodeList.add(createNode("api_call", "API调用", "data", 100, 150));
            nodeList.add(createNode("response_process", "响应处理", "ai", 100, 250));
            nodeList.add(createNode("end", "结束", "control", 100, 350));
        } else {
            nodeList.add(createNode("start", "开始", "control", 100, 50));
            nodeList.add(createNode("agent_process", "Agent处理", "ai", 100, 150));
            nodeList.add(createNode("end", "结束", "control", 100, 250));
        }
        return nodeList;
    }

    private List<Map<String, Object>> generateEdgeListForNodes(List<Map<String, Object>> nodeList) {
        List<Map<String, Object>> edgeList = new ArrayList<>();
        if (nodeList != null && nodeList.size() > 1) {
            for (int i = 0; i < nodeList.size() - 1; i++) {
                Map<String, Object> current = nodeList.get(i);
                Map<String, Object> next = nodeList.get(i + 1);
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", current.get("id") + "_" + next.get("id"));
                edge.put("source", current.get("id"));
                edge.put("target", next.get("id"));
                edge.put("type", "smoothstep");
                edge.put("animated", false);
                edgeList.add(edge);
            }
        }
        return edgeList;
    }

    private Map<String, Object> createNode(String id, String label, String category, int x, int y) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("type", category);
        node.put("position", Map.of("x", x, "y", y));
        node.put("data", Map.of(
                "label", label,
                "category", category,
                "status", "idle"
        ));
        return node;
    }

    private String buildWorkflowGenerationPrompt() {
        return """
                你是一个工作流自动生成助手。请根据用户描述生成工作流定义。
                节点类型说明:
                - 节点类型: control(控制节点), data(数据节点), ai(AI节点), output(输出节点)
                - 边: 连接两个节点的有向边
                请生成工作流定义JSON格式:
                {
                  "name": "工作流名称",
                  "description": "工作流描述",
                  "nodes": [
                    {
                      "id": "节点ID",
                      "type": "节点类型",
                      "label": "节点标签",
                      "config": {}
                    }
                  ],
                  "edges": [
                    {
                      "source": "源节点ID",
                      "target": "目标节点ID"
                    }
                  ]
                }

                注意:
                1. 节点ID必须唯一且有意义
                2. 必须包含开始和结束节点
                3. 每个节点必须有类型和标签
                4. 边必须连接存在的节点ID""";
    }

    private WorkflowDefinition createWorkflowFromDefinition(Map<String, Object> definition) {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setWorkflowCode("WF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        workflow.setWorkflowName((String) definition.getOrDefault("name", "自动生成工作流"));
        workflow.setDescription((String) definition.getOrDefault("description", ""));
        workflow.setVersion(1);
        workflow.setStatus(1);
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        workflow.setDeleted(0);

        try {
            workflow.setNodes(objectMapper.writeValueAsString(definition.get("nodes")));
            workflow.setEdges(objectMapper.writeValueAsString(definition.get("edges")));
        } catch (Exception e) {
            log.warn("序列化工作流节点失败", e);
            workflow.setNodes("[]");
            workflow.setEdges("[]");
        }

        workflowDefinitionMapper.insert(workflow);
        return workflow;
    }

    private WorkflowDefinition createDefaultWorkflow(String description) {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setWorkflowCode("WF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        workflow.setWorkflowName("默认工作流");
        workflow.setDescription(description);
        workflow.setVersion(1);
        workflow.setStatus(1);
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        workflow.setDeleted(0);

        try {
            List<Map<String, Object>> defaultNodes = new ArrayList<>();
            defaultNodes.add(createNode("start", "开始", "control", 100, 50));
            defaultNodes.add(createNode("process", "处理", "ai", 100, 150));
            defaultNodes.add(createNode("end", "结束", "control", 100, 250));
            workflow.setNodes(objectMapper.writeValueAsString(defaultNodes));
            workflow.setEdges(objectMapper.writeValueAsString(generateEdgeListForNodes(defaultNodes)));
        } catch (Exception e) {
            workflow.setNodes("[]");
            workflow.setEdges("[]");
        }

        workflowDefinitionMapper.insert(workflow);
        return workflow;
    }

    public Map<String, Object> generateFlowchartData(WorkflowDefinition workflow) {
        Map<String, Object> flowchart = new HashMap<>();
        try {
            String nodesJson = workflow.getNodes();
            String edgesJson = workflow.getEdges();

            List<Map<String, Object>> nodesList = new ArrayList<>();
            List<Map<String, Object>> edgesList = new ArrayList<>();

            if (nodesJson != null && !nodesJson.isEmpty() && !nodesJson.equals("[]")) {
                try {
                    nodesList = objectMapper.readValue(nodesJson, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    log.warn("解析nodes失败: {}", e.getMessage());
                }
            }

            if (edgesJson != null && !edgesJson.isEmpty() && !edgesJson.equals("[]")) {
                try {
                    edgesList = objectMapper.readValue(edgesJson, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    log.warn("解析edges失败: {}", e.getMessage());
                }
            }

            flowchart.put("nodes", nodesList);
            flowchart.put("edges", edgesList);
            flowchart.put("viewport", Map.of("x", 0, "y", 0, "zoom", 1));
        } catch (Exception e) {
            log.warn("生成工作流图表数据失败", e);
            flowchart.put("nodes", List.of());
            flowchart.put("edges", List.of());
        }
        return flowchart;
    }
}
