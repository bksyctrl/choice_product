package com.ecommerce.workflow.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.engine.NodeExecutionStatus;
import com.ecommerce.workflow.engine.WorkflowEngine;
import com.ecommerce.workflow.entity.WorkflowInstance;
import com.ecommerce.workflow.entity.WorkflowNodeExecution;
import com.ecommerce.workflow.mapper.WorkflowInstanceMapper;
import com.ecommerce.workflow.mapper.WorkflowNodeExecutionMapper;
import com.ecommerce.workflow.service.ai.VeoVideoService;
import com.ecommerce.workflow.util.UserContext;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);
    private final WorkflowEngine workflowEngine;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowNodeExecutionMapper nodeExecutionMapper;
    private final VeoVideoService veoVideoService;

    public WorkflowController(WorkflowEngine workflowEngine, 
                            WorkflowInstanceMapper workflowInstanceMapper, 
                            WorkflowNodeExecutionMapper nodeExecutionMapper, 
                            VeoVideoService veoVideoService) {
        this.workflowEngine = workflowEngine;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.veoVideoService = veoVideoService;
    }

    @PostMapping("/create")
    public ApiResponse<WorkflowInstance> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        try {
            Long userId = request.getUserId() != null && request.getUserId() > 0 ? 
                request.getUserId() : UserContext.getCurrentUserId();
            log.info("创建工作流实例: workflowId={}, triggerType={}, userId={}", request.getWorkflowId(), request.getTriggerType(), userId);

            WorkflowInstance instance = workflowEngine.createInstance(
                    request.getWorkflowId(),
                    request.getTriggerType(),
                    userId,
                    request.getInputParams()
            );

            if (request.isAutoStart()) {
                workflowEngine.startExecution(instance.getId());
            }

            return ApiResponse.success(instance);
        } catch (Exception e) {
            log.error("创建工作流实例失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{instanceId}/start")
    public ApiResponse<Void> startWorkflow(@PathVariable Long instanceId) {
        try {
            workflowEngine.startExecution(instanceId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("启动工作流失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{instanceId}/pause")
    public ApiResponse<Void> pauseWorkflow(@PathVariable Long instanceId, @RequestParam(required = false) String reason) {
        try {
            if (reason != null && !reason.isEmpty()) {
                workflowEngine.pauseWorkflow(instanceId, reason);
            } else {
                workflowEngine.pauseWorkflow(instanceId);
            }
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("暂停工作流实例失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{instanceId}/resume")
    public ApiResponse<Void> resumeWorkflow(@PathVariable Long instanceId) {
        try {
            workflowEngine.resumeWorkflow(instanceId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("恢复工作流实例失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{instanceId}/cancel")
    public ApiResponse<Void> cancelWorkflow(@PathVariable Long instanceId) {
        try {
            workflowEngine.cancelWorkflow(instanceId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("取消工作流实例失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<WorkflowInstance>> listWorkflows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        try {
            QueryWrapper<WorkflowInstance> wrapper = new QueryWrapper<>();
            wrapper.orderByDesc("created_at");
            if (status != null && !status.isEmpty()) {
                wrapper.eq("status", status);
            }
            List<WorkflowInstance> instances = workflowInstanceMapper.selectList(wrapper);
            return ApiResponse.success(instances);
        } catch (Exception e) {
            log.error("查询工作流实例列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/templates")
    public ApiResponse<List<Map<String, Object>>> getWorkflowTemplates() {
        try {
            List<Map<String, Object>> templates = new java.util.ArrayList<>();
            
            Map<String, Object> template1 = new java.util.HashMap<>();
            template1.put("id", 1);
            template1.put("name", "视频生成工作流模板");
            template1.put("description", "完整的AI视频生成工作流模板，包含产品识别、脚本生成、视频生成、质量检查等节点，用于自动生成高质量的产品宣传视频");
            template1.put("category", "VIDEO_GENERATION");
            template1.put("nodes", java.util.Arrays.asList(
                "产品识别", "脚本生成", "视频生成", "质量检查", "完成"
            ));
            templates.add(template1);
            
            Map<String, Object> template2 = new java.util.HashMap<>();
            template2.put("id", 2);
            template2.put("name", "知识学习工作流模板");
            template2.put("description", "用于自动学习和处理知识的工作流模板，包含数据获取、知识提取、知识存储等节点");
            template2.put("category", "KNOWLEDGE_LEARNING");
            template2.put("nodes", java.util.Arrays.asList(
                "内容获取", "知识提取", "向量生成", "知识存储"
            ));
            templates.add(template2);
            
            Map<String, Object> template3 = new java.util.HashMap<>();
            template3.put("id", 3);
            template3.put("name", "图像分析工作流模板");
            template3.put("description", "用于分析图像并生成视频推荐参数的工作流模板");
            template3.put("category", "IMAGE_ANALYSIS");
            template3.put("nodes", java.util.Arrays.asList(
                "图像上传", "AI分析", "参数生成", "用户确认"
            ));
            templates.add(template3);
            
            return ApiResponse.success(templates);
        } catch (Exception e) {
            log.error("获取工作流模板列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{instanceId}")
    public ApiResponse<WorkflowInstance> getWorkflowStatus(@PathVariable Long instanceId) {
        try {
            WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
            if (instance == null) {
                return ApiResponse.error("工作流实例不存在");
            }
            return ApiResponse.success(instance);
        } catch (Exception e) {
            log.error("查询工作流实例详情失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{instanceId}/nodes")
    public ApiResponse<List<WorkflowNodeExecution>> getWorkflowNodes(@PathVariable Long instanceId) {
        try {
            List<WorkflowNodeExecution> nodes = nodeExecutionMapper.selectList(
                    new QueryWrapper<WorkflowNodeExecution>()
                            .eq("instance_id", instanceId)
                            .orderByAsc("exec_order")
            );
            return ApiResponse.success(nodes);
        } catch (Exception e) {
            log.error("查询工作流节点执行列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{instanceId}/nodes/{nodeExecId}/review")
    public ApiResponse<Void> reviewNode(
            @PathVariable Long instanceId,
            @PathVariable Long nodeExecId,
            @Valid @RequestBody ReviewRequest request) {
        try {
            log.info("人工审核节点: instanceId={}, nodeExecId={}, approved={}", instanceId, nodeExecId, request.isApproved());

            WorkflowNodeExecution execution = nodeExecutionMapper.selectById(nodeExecId);
            if (execution == null) {
                return ApiResponse.error("节点执行记录不存在");
            }

            if (!"WAITING_REVIEW".equals(execution.getStatus())) {
                return ApiResponse.error("当前节点不在待审核状态，无法执行审核操作");
            }

            if (request.isApproved()) {
                execution.setStatus(NodeExecutionStatus.SUCCESS.name());
            } else {
                execution.setStatus(NodeExecutionStatus.FAILED.name());
                execution.setErrorMessage(request.getComment());
            }

            execution.setReviewerId(request.getReviewerId());
            execution.setReviewComment(request.getComment());
            execution.setReviewTime(java.time.LocalDateTime.now());

            nodeExecutionMapper.updateById(execution);

            if (request.isApproved()) {
                workflowEngine.executeNextNodes(instanceId);
            } else {
                WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
                if (instance != null) {
                    instance.setStatus("FAILED");
                    instance.setErrorMessage("人工审核未通过: " + request.getComment());
                    instance.setEndTime(java.time.LocalDateTime.now());
                    workflowInstanceMapper.updateById(instance);
                }
            }

            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("人工审核失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    public static class CreateWorkflowRequest {
        private Long workflowId;
        private String triggerType = "manual";
        private Long userId;
        private Map<String, Object> inputParams;
        private boolean autoStart = true;

        // Getters
        public Long getWorkflowId() {
            return workflowId;
        }

        public String getTriggerType() {
            return triggerType;
        }

        public Long getUserId() {
            return userId;
        }

        public Map<String, Object> getInputParams() {
            return inputParams;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        // Setters
        public void setWorkflowId(Long workflowId) {
            this.workflowId = workflowId;
        }

        public void setTriggerType(String triggerType) {
            this.triggerType = triggerType;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public void setInputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }
    }

    public static class ReviewRequest {
        private boolean approved;
        private Long reviewerId;
        private String comment;

        // Getters
        public boolean isApproved() {
            return approved;
        }

        public Long getReviewerId() {
            return reviewerId;
        }

        public String getComment() {
            return comment;
        }

        // Setters
        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public void setReviewerId(Long reviewerId) {
            this.reviewerId = reviewerId;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }
}
