package com.ecommerce.workflow.agent.handler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

/**
 * 项目文件侦察员：负责读取本地项目文件内容（例如开发文档）
 */
@Component
public class ProjectFileScoutHandler extends AbstractIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(ProjectFileScoutHandler.class);
    // 限定只能读取当前项目目录，防止安全越权
    private static final String BASE_DIR = "D:\\choice_product";

    @Override
    public String getIntentCode() {
        return "project_file_scout";
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("执行 ProjectFileScoutHandler: 准备读取本地文件...");
        
        Map<String, Object> entities = intent.getEntities();
        String targetFile = null;
        
        if (entities != null && entities.containsKey("target_file")) {
            targetFile = String.valueOf(entities.get("target_file"));
        }
        
        if (targetFile == null || targetFile.trim().isEmpty()) {
            // 如果大模型没有显式提取出参数，尝试从原始 message 中提取路径
            String message = request.getMessage();
            int startIndex = message.indexOf("D:\\choice_product");
            if (startIndex != -1) {
                int endIndex = message.indexOf(" ", startIndex);
                if (endIndex == -1) endIndex = message.indexOf("，", startIndex);
                if (endIndex == -1) endIndex = message.length();
                targetFile = message.substring(startIndex, endIndex).trim();
            }
        }

        if (targetFile == null || targetFile.trim().isEmpty()) {
            return AgentResponse.failure("未指定需要读取的文件路径，无法执行侦察任务。请提供类似 D:\\choice_product\\... 的绝对路径。");
        }

        targetFile = targetFile.replace("/", "\\");
        if (!targetFile.startsWith(BASE_DIR)) {
             return AgentResponse.failure("安全拦截：不允许读取超出项目根目录 (" + BASE_DIR + ") 的文件。");
        }

        File file = new File(targetFile);
        if (!file.exists() || !file.isFile()) {
            return AgentResponse.failure("文件读取失败：目标路径不存在或不是一个有效的文件 -> " + targetFile);
        }

        try {
            String content = Files.readString(Paths.get(targetFile), StandardCharsets.UTF_8);
            // 如果文件过大，截断以防止撑爆大模型上下文
            if (content.length() > 15000) {
                content = content.substring(0, 15000) + "\n\n...(内容过长已截断)...";
            }
            
            String reply = String.format("已成功读取文件 [%s]，内容如下：\n\n%s", targetFile, content);
            return AgentResponse.success(reply);
            
        } catch (Exception e) {
            log.error("读取文件异常: {}", targetFile, e);
            return AgentResponse.failure("读取文件发生系统异常：" + e.getMessage());
        }
    }
}
