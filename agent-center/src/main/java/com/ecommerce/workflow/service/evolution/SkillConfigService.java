package com.ecommerce.workflow.service.evolution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.entity.DeliveryData;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;
import com.ecommerce.workflow.mapper.DeliveryDataMapper;
import com.ecommerce.workflow.mapper.SkillConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SkillConfigService {

    private static final Logger log = LoggerFactory.getLogger(SkillConfigService.class);
    private final SkillConfigMapper skillConfigMapper;
    private final CaseMemoryMapper caseMemoryMapper;
    private final DeliveryDataMapper deliveryDataMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillConfigService(SkillConfigMapper skillConfigMapper, CaseMemoryMapper caseMemoryMapper,
            DeliveryDataMapper deliveryDataMapper) {
        this.skillConfigMapper = skillConfigMapper;
        this.caseMemoryMapper = caseMemoryMapper;
        this.deliveryDataMapper = deliveryDataMapper;
    }

    @Cacheable(value = "skillConfig", key = "#skillCode", unless = "#result == null")
    public SkillConfig getActiveSkill(String skillCode) {
        return skillConfigMapper.selectOne(
                new QueryWrapper<SkillConfig>()
                        .eq("skill_code", skillCode)
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0)
                        .orderByDesc("version")
                        .last("LIMIT 1"));
    }

    @Cacheable(value = "skillConfig", key = "'category:' + #category")
    public List<SkillConfig> getSkillsByCategory(String category) {
        return skillConfigMapper.selectList(
                new QueryWrapper<SkillConfig>()
                        .eq("skill_category", category)
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0)
                        .orderByDesc("version"));
    }

    @Cacheable(value = "skillConfig", key = "'all:active'")
    public List<SkillConfig> getAllActiveSkills() {
        return skillConfigMapper.selectList(
                new QueryWrapper<SkillConfig>()
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0));
    }

    @Cacheable(value = "skillConfig", key = "'params:' + #skillCode")
    public Map<String, Object> getSkillParams(String skillCode) {
        SkillConfig skill = getActiveSkill(skillCode);
        if (skill == null || skill.getConfigParams() == null) {
            return getDefaultParams(skillCode);
        }
        try {
            return objectMapper.readValue(skill.getConfigParams(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析Skill配置失败: {}, 使用默认配置", skillCode, e);
            return getDefaultParams(skillCode);
        }
    }

    @CacheEvict(value = "skillConfig", allEntries = true)
    public void clearSkillCache() {
        log.info("清除Skill配置缓存");
    }

    public Map<String, Object> getDefaultParams(String skillCode) {
        Map<String, Object> defaults = new HashMap<>();
        switch (skillCode) {
            case "scorer_6d" -> {
                defaults.put("weights", new double[] { 0.25, 0.20, 0.15, 0.15, 0.15, 0.10 });
                defaults.put("dimensions", new String[] { "salesVolume", "growthRate", "profitMargin",
                        "competition", "marketDemand", "supplyStability" });
                defaults.put("sThreshold", 85.0);
                defaults.put("aThreshold", 75.0);
                defaults.put("bThreshold", 65.0);
            }
            case "script_generator" -> {
                defaults.put("style", "conversion_focused");
                defaults.put("maxLength", 500);
                defaults.put("includeCallToAction", true);
                defaults.put("platform", "douyin");
            }
            case "video_generator" -> {
                defaults.put("model", "veo3.1-fast");
                defaults.put("enhancePrompt", true);
                defaults.put("enableUpsample", true);
                defaults.put("aspectRatio", "9:16");
            }
            default -> {
            }
        }
        return defaults;
    }

    public SkillConfig createOrUpdateSkill(String skillCode, String skillName, String category,
            Map<String, Object> params, String reason) {
        SkillConfig existing = getActiveSkill(skillCode);

        if (existing != null) {
            return upgradeSkill(existing, params, reason);
        }

        SkillConfig skill = new SkillConfig();
        skill.setSkillCode(skillCode);
        skill.setSkillName(skillName);
        skill.setSkillCategory(category);
        skill.setVersion("1.0");
        try {
            skill.setConfigParams(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            skill.setConfigParams("{}");
        }
        skill.setSuccessRate(BigDecimal.ZERO);
        skill.setUsageCount(0);
        skill.setSuccessCount(0);
        skill.setFailCount(0);
        skill.setAvgCvr(0.0);
        skill.setAvgGmv(0.0);
        skill.setStatus("ACTIVE");
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        skill.setDeleted(0);

        skillConfigMapper.insert(skill);
        log.info("创建新Skill: code={}, name={}, version={}", skillCode, skillName, skill.getVersion());
        return skill;
    }

    public SkillConfig upgradeSkill(SkillConfig current, Map<String, Object> newParams, String reason) {
        String nextVersion = incrementVersion(current.getVersion());

        SkillConfig newVersion = new SkillConfig();
        newVersion.setSkillCode(current.getSkillCode());
        newVersion.setSkillName(current.getSkillName());
        newVersion.setSkillCategory(current.getSkillCategory());
        newVersion.setVersion(nextVersion);
        try {
            newVersion.setConfigParams(objectMapper.writeValueAsString(newParams));
        } catch (Exception e) {
            newVersion.setConfigParams(current.getConfigParams());
        }
        newVersion.setSuccessRate(BigDecimal.ZERO);
        newVersion.setUsageCount(0);
        newVersion.setSuccessCount(0);
        newVersion.setFailCount(0);
        newVersion.setAvgCvr(0.0);
        newVersion.setAvgGmv(0.0);
        newVersion.setStatus("ACTIVE");
        newVersion.setPreviousVersion(current.getVersion());
        newVersion.setUpgradeReason(reason);
        newVersion.setCreatedAt(LocalDateTime.now());
        newVersion.setUpdatedAt(LocalDateTime.now());
        newVersion.setDeleted(0);

        current.setStatus("SUPERSEDED");
        skillConfigMapper.updateById(current);

        skillConfigMapper.insert(newVersion);
        log.info("Skill升级: {} {} → {}, 原因: {}", current.getSkillCode(), current.getVersion(),
                nextVersion, reason);
        return newVersion;
    }

    public void recordUsage(String skillCode, boolean success, Double cvr, BigDecimal gmv) {
        SkillConfig skill = getActiveSkill(skillCode);
        if (skill == null)
            return;

        skill.setUsageCount(skill.getUsageCount() + 1);
        if (success) {
            skill.setSuccessCount(skill.getSuccessCount() + 1);
        } else {
            skill.setFailCount(skill.getFailCount() + 1);
        }

        int total = skill.getUsageCount();
        if (total > 0) {
            BigDecimal rate = BigDecimal.valueOf(skill.getSuccessCount())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            skill.setSuccessRate(rate);
        }

        if (cvr != null && cvr > 0) {
            double oldAvg = skill.getAvgCvr() != null ? skill.getAvgCvr() : 0.0;
            skill.setAvgCvr(oldAvg + (cvr - oldAvg) / total);
        }
        if (gmv != null && gmv.doubleValue() > 0) {
            double oldGmv = skill.getAvgGmv() != null ? skill.getAvgGmv() : 0.0;
            skill.setAvgGmv(oldGmv + (gmv.doubleValue() - oldGmv) / total);
        }

        skill.setUpdatedAt(LocalDateTime.now());
        skillConfigMapper.updateById(skill);
    }

    public void rollbackSkill(Long skillId, String reason) {
        SkillConfig skill = skillConfigMapper.selectById(skillId);
        if (skill == null || !"ACTIVE".equals(skill.getStatus()))
            return;

        String prevVersion = skill.getPreviousVersion();
        if (prevVersion == null)
            return;

        SkillConfig previous = skillConfigMapper.selectOne(
                new QueryWrapper<SkillConfig>()
                        .eq("skill_code", skill.getSkillCode())
                        .eq("version", prevVersion)
                        .eq("deleted", 0));

        if (previous != null) {
            skill.setStatus("ROLLED_BACK");
            skillConfigMapper.updateById(skill);

            previous.setStatus("ACTIVE");
            previous.setUpdatedAt(LocalDateTime.now());
            skillConfigMapper.updateById(previous);
            log.info("Skill回滚: {} {} → {}", skill.getSkillCode(), skill.getVersion(), prevVersion);
        }
    }

    private String incrementVersion(String version) {
        try {
            double v = Double.parseDouble(version);
            return String.format("%.1f", v + 0.1);
        } catch (Exception e) {
            return "1.1";
        }
    }

    public void initDefaultSkills() {
        Long count = skillConfigMapper.selectCount(
                new QueryWrapper<SkillConfig>().eq("deleted", 0));
        if (count > 0) {
            log.info("Skill配置已存在，跳过初始化");
            return;
        }

        log.info("初始化默认Skill配置...");

        createOrUpdateSkill("scorer_6d", "6维爆品评分器", "selection",
                getDefaultParams("scorer_6d"), "初始版本");

        createOrUpdateSkill("script_generator", "AI脚本生成器", "content_generation",
                getDefaultParams("script_generator"), "初始版本");

        createOrUpdateSkill("video_generator", "VEO视频生成器", "video_production",
                getDefaultParams("video_generator"), "初始版本");

        createOrUpdateSkill("compliance_checker", "合规检测器", "compliance",
                Map.of("strictLevel", "standard", "checkAdsLaw", true,
                        "checkPlatformRules", true, "checkIPRisk", true),
                "初始版本");

        createOrUpdateSkill("causal_analyzer", "因果归因分析器", "attribution",
                Map.of("method", "control_variable", "topFactors", 5,
                        "confidenceLevel", 0.95),
                "初始版本");

        log.info("默认Skill配置初始化完成");
    }

    public List<CaseMemory> getRecentCases(int limit) {
        return caseMemoryMapper.selectList(
                new QueryWrapper<CaseMemory>()
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit));
    }

    public List<CaseMemory> getCasesByType(String type, int limit) {
        return caseMemoryMapper.selectList(
                new QueryWrapper<CaseMemory>()
                        .eq("case_type", type)
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit));
    }

    public CaseMemory recordCase(Map<String, Object> data) {
        CaseMemory cm = new CaseMemory();
        cm.setCaseNo(data.containsKey("caseNo") ? (String) data.get("caseNo")
                : "CASE_" + System.currentTimeMillis());
        cm.setCaseType((String) data.getOrDefault("caseType", "success"));
        cm.setProductId(data.containsKey("productId")
                ? ((Number) data.get("productId")).longValue()
                : null);
        cm.setProductName((String) data.getOrDefault("productName", ""));
        cm.setCategory((String) data.getOrDefault("category", ""));
        cm.setPlatform((String) data.getOrDefault("platform", "douyin"));
        cm.setSkillVersionSnapshot((String) data.getOrDefault("skillVersion", "unknown"));
        try {
            cm.setInputParams(objectMapper.writeValueAsString(
                    data.getOrDefault("inputParams", new HashMap<>())));
            cm.setOutputResult(objectMapper.writeValueAsString(
                    data.getOrDefault("outputResult", new HashMap<>())));
        } catch (Exception e) {
            cm.setInputParams("{}");
            cm.setOutputResult("{}");
        }
        cm.setPlayCount(data.containsKey("playCount")
                ? new BigDecimal(data.get("playCount").toString())
                : null);
        cm.setGmv(data.containsKey("gmv")
                ? new BigDecimal(data.get("gmv").toString())
                : null);
        cm.setCvr(data.containsKey("cvr") ? ((Number) data.get("cvr")).doubleValue() : null);
        cm.setQualityTag((String) data.getOrDefault("qualityTag", "success"));
        cm.setFailureReason((String) data.get("failureReason"));
        cm.setLessonLearned((String) data.get("lessonLearned"));
        cm.setCreatedAt(LocalDateTime.now());
        cm.setDeleted(0);

        caseMemoryMapper.insert(cm);

        String skillCode = (String) data.getOrDefault("skillVersion", "scorer_6d");
        boolean success = "success".equals(cm.getQualityTag());
        recordUsage(skillCode, success, cm.getCvr(), cm.getGmv());

        return cm;
    }

    public DeliveryData ingestDeliveryData(Map<String, Object> data) {
        DeliveryData dd = new DeliveryData();
        dd.setTaskNo((String) data.getOrDefault("taskNo",
                "DELIVERY_" + System.currentTimeMillis()));
        dd.setVideoTaskId(data.containsKey("videoTaskId")
                ? ((Number) data.get("videoTaskId")).longValue()
                : null);
        dd.setPlatform((String) data.getOrDefault("platform", "douyin"));
        dd.setPlayCount(data.containsKey("playCount")
                ? new BigDecimal(data.get("playCount").toString())
                : null);
        dd.setLikeCount(data.containsKey("likeCount")
                ? new BigDecimal(data.get("likeCount").toString())
                : null);
        dd.setCommentCount(data.containsKey("commentCount")
                ? new BigDecimal(data.get("commentCount").toString())
                : null);
        dd.setShareCount(data.containsKey("shareCount")
                ? new BigDecimal(data.get("shareCount").toString())
                : null);
        dd.setGmv(data.containsKey("gmv")
                ? new BigDecimal(data.get("gmv").toString())
                : null);
        dd.setOrderCount(data.containsKey("orderCount")
                ? new BigDecimal(data.get("orderCount").toString())
                : null);
        dd.setCvr(data.containsKey("cvr") ? ((Number) data.get("cvr")).doubleValue() : null);
        dd.setStatus((String) data.getOrDefault("status", "COMPLETED"));
        try {
            dd.setRawDataJson(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            dd.setRawDataJson("{}");
        }
        dd.setDataDate(java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        dd.setCreatedAt(LocalDateTime.now());
        dd.setDeleted(0);

        deliveryDataMapper.insert(dd);
        return dd;
    }

    public List<DeliveryData> getRecentDeliveries(int limit) {
        return deliveryDataMapper.selectList(
                new QueryWrapper<DeliveryData>()
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit));
    }

    public SkillConfig getBestPerformingSkill(String queryHint) {
        List<SkillConfig> allSkills = getAllActiveSkills();
        return allSkills.stream()
                .filter(s -> s.getUsageCount() != null && s.getUsageCount() > 0)
                .sorted((a, b) -> {
                    double scoreA = (a.getSuccessRate() != null ? a.getSuccessRate().doubleValue() : 0)
                            + (a.getAvgCvr() != null ? a.getAvgCvr() * 100 : 0);
                    double scoreB = (b.getSuccessRate() != null ? b.getSuccessRate().doubleValue() : 0)
                            + (b.getAvgCvr() != null ? b.getAvgCvr() * 100 : 0);
                    return Double.compare(scoreB, scoreA);
                })
                .findFirst()
                .orElse(null);
    }
}
