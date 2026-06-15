package com.ecommerce.workflow.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.ExpertAnalysisDimension;
import com.ecommerce.workflow.entity.ExpertTriggerKeyword;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.mapper.ExpertAnalysisDimensionMapper;
import com.ecommerce.workflow.mapper.ExpertTriggerKeywordMapper;
import com.ecommerce.workflow.mapper.ExpertRoleConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExpertConfigCacheManager {
    private static final Logger log = LoggerFactory.getLogger(ExpertConfigCacheManager.class);

    private static final long CACHE_EXPIRE_MS = 5 * 60 * 1000; // 5分钟过期

    @Autowired
    private ExpertRoleConfigMapper expertRoleConfigMapper;

    @Autowired
    private ExpertAnalysisDimensionMapper dimensionMapper;

    @Autowired
    private ExpertTriggerKeywordMapper keywordMapper;

    private final Map<String, ExpertRoleConfig> roleConfigCache = new ConcurrentHashMap<>();
    private final Map<String, List<ExpertAnalysisDimension>> dimensionCache = new ConcurrentHashMap<>();
    private final Map<String, List<ExpertTriggerKeyword>> keywordCache = new ConcurrentHashMap<>();
    private volatile long lastLoadTime = 0;

    @PostConstruct
    public void init() {
        log.info("专家配置缓存管理器初始化");
        loadAllConfigsFromDatabase();
    }

    public synchronized void loadAllConfigsFromDatabase() {
        try {
            log.info("开始从数据库加载所有专家配置");

            LambdaQueryWrapper<ExpertRoleConfig> roleQuery = new LambdaQueryWrapper<>();
            roleQuery.eq(ExpertRoleConfig::getStatus, "ACTIVE")
                    .eq(ExpertRoleConfig::getDeleted, 0);
            List<ExpertRoleConfig> roles = expertRoleConfigMapper.selectList(roleQuery);

            roleConfigCache.clear();
            for (ExpertRoleConfig role : roles) {
                roleConfigCache.put(role.getRoleCode(), role);

                LambdaQueryWrapper<ExpertAnalysisDimension> dimQuery = new LambdaQueryWrapper<>();
                dimQuery.eq(ExpertAnalysisDimension::getRoleCode, role.getRoleCode())
                        .eq(ExpertAnalysisDimension::getIsActive, true)
                        .eq(ExpertAnalysisDimension::getDeleted, 0)
                        .orderByAsc(ExpertAnalysisDimension::getSortOrder);
                List<ExpertAnalysisDimension> dimensions = dimensionMapper.selectList(dimQuery);
                dimensionCache.put(role.getRoleCode(), dimensions);

                LambdaQueryWrapper<ExpertTriggerKeyword> kwQuery = new LambdaQueryWrapper<>();
                kwQuery.eq(ExpertTriggerKeyword::getRoleCode, role.getRoleCode())
                        .eq(ExpertTriggerKeyword::getIsActive, true)
                        .eq(ExpertTriggerKeyword::getDeleted, 0)
                        .orderByDesc(ExpertTriggerKeyword::getWeight);
                List<ExpertTriggerKeyword> keywords = keywordMapper.selectList(kwQuery);
                keywordCache.put(role.getRoleCode(), keywords);
            }

            lastLoadTime = System.currentTimeMillis();
            log.info("专家配置加载完成: 角色数={}, 总维度数={}, 总关键词数={}",
                    roleConfigCache.size(),
                    dimensionCache.values().stream().mapToInt(List::size).sum(),
                    keywordCache.values().stream().mapToInt(List::size).sum());

        } catch (Exception e) {
            log.error("从数据库加载专家配置失败: {}", e.getMessage(), e);
        }
    }

    public synchronized void refreshCache() {
        log.info("手动刷新专家配置缓存");
        loadAllConfigsFromDatabase();
    }

    public boolean isCacheExpired() {
        return System.currentTimeMillis() - lastLoadTime > CACHE_EXPIRE_MS;
    }

    public ExpertRoleConfig getRoleConfig(String roleCode) {
        if (isCacheExpired()) {
            log.info("缓存已过期，自动重新加载");
            loadAllConfigsFromDatabase();
        }
        return roleConfigCache.get(roleCode);
    }

    public List<ExpertRoleConfig> getAllRoleConfigs() {
        if (isCacheExpired()) {
            loadAllConfigsFromDatabase();
        }
        return new ArrayList<>(roleConfigCache.values());
    }

    public List<ExpertAnalysisDimension> getDimensions(String roleCode) {
        if (isCacheExpired()) {
            loadAllConfigsFromDatabase();
        }
        return dimensionCache.getOrDefault(roleCode, Collections.emptyList());
    }

    public List<ExpertTriggerKeyword> getKeywords(String roleCode) {
        if (isCacheExpired()) {
            loadAllConfigsFromDatabase();
        }
        return keywordCache.getOrDefault(roleCode, Collections.emptyList());
    }

    public double calculateMatchScore(String roleCode, String caseContent) {
        List<ExpertTriggerKeyword> keywords = getKeywords(roleCode);
        if (keywords.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        for (ExpertTriggerKeyword kw : keywords) {
            if (caseContent.contains(kw.getKeyword())) {
                totalScore += kw.getWeight().doubleValue();

                kw.setMatchCount(kw.getMatchCount() + 1);
                kw.setLastMatchedAt(java.time.LocalDateTime.now());
                keywordMapper.updateById(kw);
            }
        }

        return totalScore;
    }

    public void addKeyword(String roleCode, String keyword, double weight) {
        try {
            // 先检查关键词是否已存在
            LambdaQueryWrapper<ExpertTriggerKeyword> query = new LambdaQueryWrapper<>();
            query.eq(ExpertTriggerKeyword::getRoleCode, roleCode)
                    .eq(ExpertTriggerKeyword::getKeyword, keyword)
                    .eq(ExpertTriggerKeyword::getDeleted, 0);
            ExpertTriggerKeyword existing = keywordMapper.selectOne(query);
            
            if (existing != null) {
                log.debug("关键词已存在，跳过插入: roleCode={}, keyword={}", roleCode, keyword);
                return;
            }

            ExpertTriggerKeyword newKw = new ExpertTriggerKeyword();
            newKw.setRoleCode(roleCode);
            newKw.setKeyword(keyword);
            newKw.setWeight(BigDecimal.valueOf(weight));
            newKw.setMatchCount(0);
            newKw.setIsActive(true);
            newKw.setDeleted(0);

            keywordMapper.insert(newKw);
            log.info("新增触发关键词: roleCode={}, keyword={}, weight={}", roleCode, keyword, weight);

            List<ExpertTriggerKeyword> keywords = keywordCache.get(roleCode);
            if (keywords != null) {
                keywords.add(newKw);
            }
        } catch (Exception e) {
            log.error("新增触发关键词失败: {}", e.getMessage(), e);
        }
    }

    public void addDimension(String roleCode, String dimCode, String dimName, String description, int sortOrder) {
        try {
            ExpertAnalysisDimension newDim = new ExpertAnalysisDimension();
            newDim.setRoleCode(roleCode);
            newDim.setDimensionCode(dimCode);
            newDim.setDimensionName(dimName);
            newDim.setDimensionDescription(description);
            newDim.setSortOrder(sortOrder);
            newDim.setIsActive(true);
            newDim.setVersion(1);
            newDim.setDeleted(0);

            dimensionMapper.insert(newDim);
            log.info("新增分析维度: roleCode={}, dimCode={}, dimName={}", roleCode, dimCode, dimName);

            List<ExpertAnalysisDimension> dimensions = dimensionCache.get(roleCode);
            if (dimensions != null) {
                dimensions.add(newDim);
                dimensions.sort(Comparator.comparingInt(ExpertAnalysisDimension::getSortOrder));
            }
        } catch (Exception e) {
            log.error("新增分析维度失败: {}", e.getMessage(), e);
        }
    }

    public void updateRoleTemplate(String roleCode, String newTemplate) {
        try {
            LambdaQueryWrapper<ExpertRoleConfig> query = new LambdaQueryWrapper<>();
            query.eq(ExpertRoleConfig::getRoleCode, roleCode);

            ExpertRoleConfig updateEntity = new ExpertRoleConfig();
            updateEntity.setPromptTemplate(newTemplate);

            expertRoleConfigMapper.update(updateEntity, query);

            ExpertRoleConfig cached = roleConfigCache.get(roleCode);
            if (cached != null) {
                cached.setPromptTemplate(newTemplate);
            }

            log.info("更新专家提示词模板: roleCode={}", roleCode);
        } catch (Exception e) {
            log.error("更新专家提示词模板失败: {}", e.getMessage(), e);
        }
    }
}
