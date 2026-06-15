package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.video.VideoGenerationDiagnosticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/video-diagnostics")
public class VideoDiagnosticsController {

    @Autowired
    private VideoGenerationDiagnosticsService diagnosticsService;

    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> diagnoseTask(@PathVariable String taskId) {
        Map<String, Object> result = diagnosticsService.diagnoseVideoFailure(taskId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recent-failures")
    public ResponseEntity<Map<String, Object>> getRecentFailures(
            @RequestParam(defaultValue = "24") int hours) {
        Map<String, Object> stats = diagnosticsService.getRecentFailureStats(hours);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/common-issues")
    public ResponseEntity<Map<String, Object>> getCommonIssues() {
        Map<String, Object> issues = new HashMap<>();

        issues.put("commonFailureReasons", new String[]{
                "1. API密钥无效或过期 - 请检查AI模型配置中心的密钥配置",
                "2. 提供商服务不可用 - 可能是网络问题或服务维护",
                "3. 请求超时 - 视频生成时间较长，默认120秒可能不够",
                "4. 模型不可用 - 指定的模型不在提供商的可用模型列表中",
                "5. 场景图生成失败 - 前置的图片生成步骤失败",
                "6. 配额用尽 - 超出每日调用配额限制",
                "7. 请求频率过高 - 超出QPS限制"
        });

        issues.put("troubleshootingSteps", new String[]{
                "步骤1: 访问 'AI模型配置中心' 页面",
                "步骤2: 切换到 'VIDEO 视频模型' 标签",
                "步骤3: 对每个提供商点击 '测试连接' 按钮验证可用性",
                "步骤4: 如果测试失败，检查API密钥和网络配置",
                "步骤5: 在 '智能路由配置' 中检查模型能力和路由规则",
                "步骤6: 查看后端日志获取详细错误信息"
        });

        issues.put("quickFixes", new HashMap<String, String>() {{
            put("API密钥问题", "在AI模型配置中心重新输入正确的API密钥");
            put("超时问题", "增加超时时间到300秒或更长");
            put("模型不可用", "在模型能力中添加该模型配置");
            put("配额不足", "添加多个提供商分散负载");
            put("网络问题", "检查服务器网络连接和防火墙设置");
        }});

        return ResponseEntity.ok(issues);
    }
}
