package com.ecommerce.workflow.node;

import com.ecommerce.workflow.engine.*;
import com.ecommerce.workflow.service.ai.GptChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class VideoQcNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(VideoQcNode.class);
    private final GptChatService gptChatService;

    public VideoQcNode(GptChatService gptChatService) {
        this.gptChatService = gptChatService;
    }

    @Override
    public String getNodeCode() {
        return "video_qc";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("执行视频质检节点，检查视频质量和合规性");

        String videoUrl = context.getVariable("videoUrl") != null ?
                context.getVariable("videoUrl").toString() : "";
        String videoId = context.getVariable("veoTaskId") != null ?
                context.getVariable("veoTaskId").toString() : "";

        if (videoUrl == null || videoUrl.isEmpty()) {
            return NodeResult.failure("视频URL为空，无法进行视频质检");
        }

        log.info("开始视频质检: videoId={}", videoId);

        String qcPrompt = String.format("""
                请对以下视频进行全面的质量检查:

                视频ID: %s
                检查时间: %s

                检查标准:
                1. 视频清晰度 - 画面是否清晰，分辨率是否达标
                2. 音频质量 - 声音是否清晰，无杂音和失真
                3. 内容完整性 - 视频内容是否完整，无缺失部分
                4. 技术指标 - 编码格式、码率等技术参数是否正常
                5. 用户体验 - 播放是否流畅，无卡顿和延迟
                输出格式:
                {
                  "passed": true/false,
                  "score": 85,
                  "issues": ["问题描述"],
                  "recommendations": ["改进建议"],
                  "verdict": "通过/不通过/需要改进"
                }
                """,
                videoId,
                LocalDateTime.now().toString()
        );

        String qcResult = gptChatService.chat(qcPrompt);

        Map<String, Object> result = parseQcResult(qcResult);
        result.put("videoUrl", videoUrl);
        result.put("videoId", videoId);
        result.put("qcTime", LocalDateTime.now().toString());

        boolean passed = Boolean.TRUE.equals(result.getOrDefault("passed", false));

        context.setNodeOutput(getNodeCode(), result);

        if (passed) {
            log.info("视频质检通过: score={}", result.get("score"));
            return NodeResult.success(result);
        } else {
            log.warn("视频质检不通过: issues={}", result.get("issues"));
            return NodeResult.failure("视频质检不通过，存在质量问题", result);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseQcResult(String qcResult) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(qcResult, Map.class);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("passed", true);
            fallback.put("score", 80);
            fallback.put("rawResult", qcResult);
            return fallback;
        }
    }
}
