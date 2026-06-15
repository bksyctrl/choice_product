package com.ecommerce.workflow.node;

import com.ecommerce.workflow.engine.*;
import com.ecommerce.workflow.service.ai.VeoVideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class VeoCallNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(VeoCallNode.class);
    private final VeoVideoService veoVideoService;

    public VeoCallNode(VeoVideoService veoVideoService) {
        this.veoVideoService = veoVideoService;
    }

    @Override
    public String getNodeCode() {
        return "veo_call";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("开始调用VEO视频生成API");

        Map<String, Object> veoPromptData = context.getNodeOutput("veo_prompt_gen");
        if (veoPromptData == null) {
            return NodeResult.failure("缺少VEO提示词生成节点的输出数据");
        }

        String prompt = veoPromptData.containsKey("prompt") ?
                veoPromptData.get("prompt").toString() :
                veoPromptData.toString();

        String model = context.getVariable("veoModel") != null ?
                context.getVariable("veoModel").toString() : "veo3.1-fast";

        String aspectRatio = context.getVariable("aspectRatio") != null ?
                context.getVariable("aspectRatio").toString() : "9:16";

        java.util.List<String> images = context.getVariable("images") != null ?
                (java.util.List<String>) context.getVariable("images") : null;

        log.info("调用VEO API: model={}, aspectRatio={}", model, aspectRatio);

        VeoVideoService.VeoCreateRequest request = new VeoVideoService.VeoCreateRequest();
        request.setModel(model);
        request.setPrompt(prompt);
        request.setEnhancePrompt(true);
        request.setEnableUpsample(true);
        request.setImages(images);
        request.setAspectRatio(aspectRatio);

        VeoVideoService.VeoCreateResponse createResponse = veoVideoService.createVideo(request);

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", createResponse.getId());
        result.put("status", createResponse.getStatus());
        result.put("providerName", createResponse.getProviderName()); // 保存提供商名称
        context.setNodeOutput(getNodeCode(), result);

        log.info("VEO任务创建成功: taskId={}, status={}, provider={}", createResponse.getId(), createResponse.getStatus(), createResponse.getProviderName());

        if ("completed".equals(createResponse.getStatus())) {
            result.put("videoUrl", "");
            return NodeResult.success(result);
        } else {
            pollForCompletion(createResponse.getId(), createResponse.getProviderName(), result);
            return NodeResult.success(result);
        }
    }

    private void pollForCompletion(String taskId, String providerName, Map<String, Object> result) {
        try {
            veoVideoService.pollUntilComplete(taskId, providerName, new VeoVideoService.VeoStatusCallback() {
                @Override
                public void onStatusUpdate(VeoVideoService.VeoTaskStatus status) {
                    result.put("currentStatus", status.getStatus());
                    result.put("progress", status.getProgress());
                    log.info("VEO任务执行中: taskId={}, status={}, progress={}%",
                        taskId, status.getStatus(), status.getProgress() * 100);
                }

                @Override
                public void onSuccess(VeoVideoService.VeoTaskStatus status) {
                    result.put("finalStatus", "completed");
                    result.put("videoUrl", status.getVideoUrl());
                    result.put("completedAt", LocalDateTime.now().toString());
                    log.info("VEO视频生成成功: taskId={}, videoUrl={}", taskId, status.getVideoUrl());
                }

                @Override
                public void onFailure(Exception error) {
                    result.put("finalStatus", "failed");
                    result.put("error", error.getMessage());
                    log.error("VEO视频生成失败: taskId={}, error={}", taskId, error.getMessage());
                }
            }, 5000L, 120);
        } catch (Exception e) {
            log.error("轮询VEO任务状态异常", e);
            result.put("error", e.getMessage());
        }
    }
}
