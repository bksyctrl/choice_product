package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.entity.VideoTask;
import com.ecommerce.workflow.mapper.UnifiedAiConfigMapper;
import com.ecommerce.workflow.mapper.VideoTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.service.ai.UnifiedAiConfigAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 视频生成诊断服务
 * 帮助排查视频生成失败原因
 */
@Service
public class VideoGenerationDiagnosticsService {
    
    private static final Logger log = LoggerFactory.getLogger(VideoGenerationDiagnosticsService.class);
    
    @Autowired
    private VideoTaskMapper videoTaskMapper;
    
    @Autowired
    private UnifiedAiConfigMapper unifiedAiConfigMapper;
    
    @Autowired
    private UnifiedAiConfigAdapter configAdapter;
    
    /**
     * 诊断视频生成失败原因
     * @param taskId 任务ID
     * @return 诊断结果
     */
    public Map<String, Object> diagnoseVideoFailure(String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 1. 查询任务信息
        VideoTask task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            result.put("status", "error");
            result.put("message", "任务不存在: " + taskId);
            return result;
        }
        
        result.put("taskId", taskId);
        result.put("taskStatus", task.getStatus());
        result.put("errorMessage", task.getErrorMessage());
        result.put("videoModel", task.getVideoModel());
        result.put("createdAt", task.getCreatedAt());
        result.put("updatedAt", task.getUpdatedAt());
        
        // 2. 分析失败原因
        List<String> possibleCauses = analyzeFailureCause(task);
        result.put("possibleCauses", possibleCauses);
        
        // 3. 检查提供商状态
        List<Map<String, Object>> providerStatus = checkProviderStatus(task.getVideoModel());
        result.put("providerStatus", providerStatus);
        
        // 4. 提供解决方案
        List<String> solutions = suggestSolutions(task, possibleCauses);
        result.put("suggestedSolutions", solutions);
        
        return result;
    }
    
    /**
     * 分析失败原因
     */
    private List<String> analyzeFailureCause(VideoTask task) {
        List<String> causes = new ArrayList<>();
        String errorMsg = task.getErrorMessage();
        
        if (errorMsg == null || errorMsg.isEmpty()) {
            causes.add("未知错误 - 未记录具体错误信息");
            return causes;
        }
        
        errorMsg = errorMsg.toLowerCase();
        
        // API/服务相关
        if (errorMsg.contains("provider") || errorMsg.contains("服务") || 
            errorMsg.contains("所有视频生成服务") || errorMsg.contains("没有可用的")) {
            causes.add("1. AI服务提供商问题 - 所有提供商都不可用或返回错误");
        }
        
        if (errorMsg.contains("timeout") || errorMsg.contains("超时")) {
            causes.add("2. 请求超时 - 服务响应时间过长");
        }
        
        if (errorMsg.contains("connection") || errorMsg.contains("连接")) {
            causes.add("3. 网络连接问题 - 无法连接到AI服务");
        }
        
        if (errorMsg.contains("401") || errorMsg.contains("403") || 
            errorMsg.contains("unauthorized") || errorMsg.contains("api key")) {
            causes.add("4. API密钥无效 - 密钥过期或权限不足");
        }
        
        if (errorMsg.contains("429") || errorMsg.contains("rate limit") || 
            errorMsg.contains("配额") || errorMsg.contains("quota")) {
            causes.add("5. 请求频率限制 - 超出配额或QPS限制");
        }
        
        // 模型相关
        if (errorMsg.contains("model") || errorMsg.contains("模型")) {
            causes.add("6. 模型不可用 - 指定的模型不在可用列表中或已下线");
        }
        
        // 场景图相关
        if (errorMsg.contains("场景") || errorMsg.contains("scene") || 
            errorMsg.contains("图片") || errorMsg.contains("image")) {
            causes.add("7. 场景图生成失败 - 前置的图片生成步骤失败");
        }
        
        // VEO特定
        if (errorMsg.contains("veo") || errorMsg.contains("task") || 
            errorMsg.contains("任务") || errorMsg.contains("polling")) {
            causes.add("8. VEO任务执行失败 - 异步任务返回失败状态或轮询超时");
        }
        
        if (errorMsg.contains("null") || errorMsg.contains("空")) {
            causes.add("9. 空响应 - 服务返回null或空数据");
        }
        
        if (causes.isEmpty()) {
            causes.add("未分类错误: " + task.getErrorMessage());
        }
        
        return causes;
    }
    
    /**
     * 检查提供商状态
     */
    private List<Map<String, Object>> checkProviderStatus(String videoModel) {
        List<Map<String, Object>> status = new ArrayList<>();
        
        LambdaQueryWrapper<UnifiedAiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedAiConfig::getDeleted, 0)
               .eq(UnifiedAiConfig::getProviderType, "VIDEO")
               .orderByAsc(UnifiedAiConfig::getPriority);
        
        List<UnifiedAiConfig> unifiedProviders = unifiedAiConfigMapper.selectList(wrapper);
        List<AiProviderConfig> providers = configAdapter.convertToLegacyList(unifiedProviders);
        
        for (AiProviderConfig provider : providers) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", provider.getProviderName());
            info.put("enabled", provider.getEnabled() == 1 ? "✓ 启用" : "✗ 禁用");
            info.put("failCount", provider.getFailCount());
            info.put("models", provider.getModels());
            
            // 检查是否包含用户指定的模型
            boolean hasModel = provider.getModels() != null && 
                             provider.getModels().contains(videoModel);
            info.put("hasRequestedModel", hasModel ? "✓ 包含" : "✗ 不包含");
            
            status.add(info);
        }
        
        return status;
    }
    
    /**
     * 提供解决方案
     */
    private List<String> suggestSolutions(VideoTask task, List<String> causes) {
        List<String> solutions = new ArrayList<>();
        
        solutions.add("=== 建议解决方案 ===");
        
        for (String cause : causes) {
            if (cause.contains("API密钥")) {
                solutions.add("• 检查API密钥是否过期，在 'AI模型配置中心' 页面更新密钥");
                solutions.add("• 确认密钥权限是否包含视频生成功能");
            }
            else if (cause.contains("服务提供商")) {
                solutions.add("• 在 'AI模型配置中心' 检查视频提供商是否已启用");
                solutions.add("• 点击 '测试连接' 按钮验证提供商可用性");
                solutions.add("• 考虑添加备用提供商实现自动故障转移");
            }
            else if (cause.contains("超时")) {
                solutions.add("• 增加超时时间：在提供商配置中将超时时间从120秒增加到300秒");
                solutions.add("• 检查网络连接是否稳定");
            }
            else if (cause.contains("模型")) {
                solutions.add("• 在 '智能路由配置' → '模型能力' 中添加该模型配置");
                solutions.add("• 检查提供商的 '可用模型列表' 是否包含该模型");
            }
            else if (cause.contains("场景图")) {
                solutions.add("• 检查图片生成提供商配置是否正确");
                solutions.add("• 在 '智能路由配置' 中启用备用图片生成模型");
            }
            else if (cause.contains("配额") || cause.contains("频率")) {
                solutions.add("• 等待配额重置或增加每日配额");
                solutions.add("• 降低请求频率，或添加多个提供商分散负载");
            }
        }
        
        solutions.add("\n=== 快速排查步骤 ===");
        solutions.add("1. 访问 'AI模型配置中心' 页面");
        solutions.add("2. 切换到 'VIDEO 视频模型' 标签");
        solutions.add("3. 对每个提供商点击 '测试连接' 按钮");
        solutions.add("4. 如果测试失败，检查API密钥和网络配置");
        
        return solutions;
    }
    
    /**
     * 获取最近失败的视频任务统计
     */
    public Map<String, Object> getRecentFailureStats(int hours) {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        LambdaQueryWrapper<VideoTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoTask::getStatus, "failed")
               .ge(VideoTask::getCreatedAt, since)
               .orderByDesc(VideoTask::getCreatedAt);
        
        List<VideoTask> failures = videoTaskMapper.selectList(wrapper);
        
        stats.put("totalFailures", failures.size());
        stats.put("timeRange", "最近" + hours + "小时");
        
        // 按错误类型统计
        Map<String, Integer> errorTypes = new HashMap<>();
        for (VideoTask task : failures) {
            String error = task.getErrorMessage();
            if (error == null) error = "未知错误";
            // 简化错误信息用于统计
            String key = error.length() > 50 ? error.substring(0, 50) + "..." : error;
            errorTypes.merge(key, 1, Integer::sum);
        }
        
        stats.put("errorBreakdown", errorTypes);
        
        return stats;
    }
}
