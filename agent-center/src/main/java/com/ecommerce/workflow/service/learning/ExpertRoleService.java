package com.ecommerce.workflow.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.ExpertAnalysisDimension;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.entity.ExpertTriggerKeyword;
import com.ecommerce.workflow.mapper.ExpertAnalysisDimensionMapper;
import com.ecommerce.workflow.mapper.ExpertRoleConfigMapper;
import com.ecommerce.workflow.mapper.ExpertTriggerKeywordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 专家角色管理服务
 * 负责专家角色配置的加载、缓存和查询
 */
@Service
public class ExpertRoleService {
    private static final Logger log = LoggerFactory.getLogger(ExpertRoleService.class);

    private final ExpertRoleConfigMapper expertRoleConfigMapper;
    private final ExpertAnalysisDimensionMapper expertAnalysisDimensionMapper;
    private final ExpertTriggerKeywordMapper expertTriggerKeywordMapper;
    private final ObjectMapper objectMapper;

    // 内存缓存
    private final Map<String, ExpertRoleConfig> expertRoleRegistry = new ConcurrentHashMap<>();
    private final Map<String, List<String>> expertDimensionsCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> expertKeywordsCache = new ConcurrentHashMap<>();

    @Autowired
    public ExpertRoleService(ExpertRoleConfigMapper expertRoleConfigMapper,
                             ExpertAnalysisDimensionMapper expertAnalysisDimensionMapper,
                             ExpertTriggerKeywordMapper expertTriggerKeywordMapper,
                             ObjectMapper objectMapper) {
        this.expertRoleConfigMapper = expertRoleConfigMapper;
        this.expertAnalysisDimensionMapper = expertAnalysisDimensionMapper;
        this.expertTriggerKeywordMapper = expertTriggerKeywordMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadExpertRolesFromDatabase();
    }

    /**
     * 从数据库加载专家角色配置
     */
    private void loadExpertRolesFromDatabase() {
        try {
            QueryWrapper<ExpertRoleConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0);
            List<ExpertRoleConfig> dbConfigs = expertRoleConfigMapper.selectList(wrapper);

            if (dbConfigs != null && !dbConfigs.isEmpty()) {
                expertRoleRegistry.clear();
                for (ExpertRoleConfig config : dbConfigs) {
                    expertRoleRegistry.put(config.getRoleCode(), config);
                }
                log.info("从数据库加载了{}个专家角色配置", dbConfigs.size());
            } else {
                log.warn("数据库中未找到专家角色配置");
            }
        } catch (Exception e) {
            log.error("从数据库加载专家角色配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取单个专家角色配置
     */
    public ExpertRoleConfig getExpertRole(String roleCode) {
        if (roleCode == null || roleCode.isEmpty()) {
            return null;
        }

        // 先从缓存获取
        ExpertRoleConfig config = expertRoleRegistry.get(roleCode);
        if (config != null) {
            return config;
        }

        // 缓存未命中，从数据库加载
        try {
            QueryWrapper<ExpertRoleConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("role_code", roleCode).eq("deleted", 0);
            ExpertRoleConfig dbConfig = expertRoleConfigMapper.selectOne(wrapper);
            if (dbConfig != null) {
                expertRoleRegistry.put(roleCode, dbConfig);
                return dbConfig;
            }
        } catch (Exception e) {
            log.error("从数据库获取专家角色失败: roleCode={}", roleCode, e);
        }

        return null;
    }

    /**
     * 获取所有专家角色配置
     */
    public List<ExpertRoleConfig> getAllExpertRoles() {
        if (expertRoleRegistry.isEmpty()) {
            loadExpertRolesFromDatabase();
        }

        return new ArrayList<>(expertRoleRegistry.values());
    }

    /**
     * 获取所有激活的专家角色
     */
    public List<ExpertRoleConfig> getActiveExpertRoles() {
        return getAllExpertRoles().stream()
                .filter(config -> "ACTIVE".equals(config.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 重新加载专家角色配置
     */
    public void reloadExpertRoles() {
        log.info("开始重新加载专家角色配置...");
        expertRoleRegistry.clear();
        expertDimensionsCache.clear();
        expertKeywordsCache.clear();
        loadExpertRolesFromDatabase();
        log.info("专家角色配置重新加载完成，共加载 {} 个角色", expertRoleRegistry.size());
    }

    /**
     * 注册新的专家角色
     */
    public boolean registerExpertRole(ExpertRoleConfig config) {
        if (config == null || config.getRoleCode() == null || config.getRoleCode().isEmpty()) {
            log.warn("注册专家角色失败: 配置或角色代码为空");
            return false;
        }

        try {
            config.setVersion(1);
            if (config.getStatus() == null) {
                config.setStatus("ACTIVE");
            }

            expertRoleConfigMapper.insert(config);
            expertRoleRegistry.put(config.getRoleCode(), config);

            log.info("成功注册专家角色: code={}, name={}", config.getRoleCode(), config.getRoleName());
            return true;
        } catch (Exception e) {
            log.error("注册专家角色失败: code={}, error={}", config.getRoleCode(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新专家角色
     */
    public boolean updateExpertRole(String roleCode, ExpertRoleConfig config) {
        if (roleCode == null || roleCode.isEmpty() || config == null) {
            log.warn("更新专家角色失败: 角色代码或配置为空");
            return false;
        }

        try {
            ExpertRoleConfig existing = expertRoleRegistry.get(roleCode);
            if (existing == null) {
                log.warn("更新专家角色失败: 角色不存在, code={}", roleCode);
                return false;
            }

            config.setId(existing.getId());
            config.setRoleCode(roleCode);

            // 版本号+1
            if (existing.getVersion() != null) {
                config.setVersion(existing.getVersion() + 1);
            } else {
                config.setVersion(1);
            }

            expertRoleConfigMapper.updateById(config);
            expertRoleRegistry.put(roleCode, config);

            log.info("成功更新专家角色: code={}", roleCode);
            return true;
        } catch (Exception e) {
            log.error("更新专家角色失败: code={}, error={}", roleCode, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除专家角色
     */
    public boolean removeExpertRole(String roleCode) {
        if (roleCode == null || roleCode.isEmpty()) {
            return false;
        }

        try {
            ExpertRoleConfig config = expertRoleRegistry.get(roleCode);
            if (config != null) {
                expertRoleConfigMapper.deleteById(config.getId());
                expertRoleRegistry.remove(roleCode);
                expertDimensionsCache.remove(roleCode);
                expertKeywordsCache.remove(roleCode);
                log.info("成功删除专家角色: code={}", roleCode);
                return true;
            }
        } catch (Exception e) {
            log.error("删除专家角色失败: code={}, error={}", roleCode, e.getMessage(), e);
        }
        return false;
    }

    /**
     * 判断专家角色是否激活
     */
    public boolean isExpertRoleActive(String roleCode) {
        ExpertRoleConfig config = getExpertRole(roleCode);
        return config != null && "ACTIVE".equals(config.getStatus());
    }

    /**
     * 获取专家角色的核心分析维度
     */
    public List<String> getExpertCoreDimensions(String roleCode) {
        // 先检查缓存
        List<String> cached = expertDimensionsCache.get(roleCode);
        if (cached != null) {
            return cached;
        }

        try {
            QueryWrapper<ExpertAnalysisDimension> wrapper = new QueryWrapper<>();
            wrapper.eq("role_code", roleCode)
                   .eq("is_active", true)
                   .eq("deleted", 0)
                   .orderByAsc("sort_order");
            List<ExpertAnalysisDimension> dimensions = expertAnalysisDimensionMapper.selectList(wrapper);

            List<String> dimensionNames = dimensions != null ?
                    dimensions.stream()
                            .map(ExpertAnalysisDimension::getDimensionName)
                            .collect(Collectors.toList()) : new ArrayList<>();

            expertDimensionsCache.put(roleCode, dimensionNames);
            return dimensionNames;
        } catch (Exception e) {
            log.warn("获取专家分析维度失败: roleCode={}", roleCode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取专家角色的触发关键词
     */
    public List<String> getExpertTriggerKeywords(String roleCode) {
        // 先检查缓存
        List<String> cached = expertKeywordsCache.get(roleCode);
        if (cached != null) {
            return cached;
        }

        try {
            QueryWrapper<ExpertTriggerKeyword> wrapper = new QueryWrapper<>();
            wrapper.eq("role_code", roleCode)
                   .eq("is_active", true)
                   .eq("deleted", 0);
            List<ExpertTriggerKeyword> keywords = expertTriggerKeywordMapper.selectList(wrapper);

            List<String> keywordList = keywords != null ?
                    keywords.stream()
                            .map(ExpertTriggerKeyword::getKeyword)
                            .collect(Collectors.toList()) : new ArrayList<>();

            expertKeywordsCache.put(roleCode, keywordList);
            return keywordList;
        } catch (Exception e) {
            log.warn("获取专家触发关键词失败: roleCode={}", roleCode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取系统状态
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("expertRolesCount", expertRoleRegistry.size());
        status.put("activeExperts", expertRoleRegistry.values().stream()
                .filter(config -> "ACTIVE".equals(config.getStatus()))
                .count());
        return status;
    }
}
