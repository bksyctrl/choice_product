package com.ecommerce.workflow.skill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.skill.entity.SkillMarket;
import com.ecommerce.workflow.skill.entity.SkillDependency;
import com.ecommerce.workflow.skill.mapper.SkillMarketMapper;
import com.ecommerce.workflow.skill.mapper.SkillDependencyMapper;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.ecommerce.workflow.entity.SkillConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SkillMarketService {
    
    private static final Logger log = LoggerFactory.getLogger(SkillMarketService.class);
    
    @Autowired
    private SkillMarketMapper skillMarketMapper;
    
    @Autowired
    private SkillDependencyMapper skillDependencyMapper;
    
    @Autowired
    private SkillConfigService skillConfigService;
    
    @Autowired
    private SkillDependencyValidator skillDependencyValidator;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public List<SkillMarket> getAvailableSkills(String category) {
        return skillMarketMapper.selectList(
            new LambdaQueryWrapper<SkillMarket>()
                .eq(SkillMarket::getStatus, "active")
                .eq(category != null, SkillMarket::getCategory, category)
                .orderByDesc(SkillMarket::getDownloads)
        );
    }
    
    public SkillMarket getSkillDetails(String skillId) {
        return skillMarketMapper.selectOne(
            new LambdaQueryWrapper<SkillMarket>()
                .eq(SkillMarket::getSkillId, skillId)
                .eq(SkillMarket::getStatus, "active")
        );
    }
    
    public void publishSkill(String skillId, String skillName, String description, 
                             String category, String author, String version, 
                             Map<String, Object> configTemplate) {
        SkillMarket skill = new SkillMarket();
        skill.setSkillId(skillId);
        skill.setSkillName(skillName);
        skill.setDescription(description);
        skill.setCategory(category);
        skill.setAuthor(author);
        skill.setVersion(version);
        skill.setDownloads(0);
        skill.setRating(new java.math.BigDecimal("0.00"));
        skill.setStatus("active");
        
        try {
            skill.setConfigTemplate(objectMapper.writeValueAsString(configTemplate));
        } catch (Exception e) {
            log.warn("配置模板序列化失败", e);
            skill.setConfigTemplate("{}");
        }
        
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        
        skillMarketMapper.insert(skill);
        log.info("技能发布成功: skillId={}, name={}", skillId, skillName);
    }
    
    @Transactional
    public SkillConfig installSkill(String skillId, Long userId) {
        SkillMarket marketSkill = getSkillDetails(skillId);
        if (marketSkill == null) {
            throw new RuntimeException("技能不存在: " + skillId);
        }
        
        List<SkillDependency> dependencies = getSkillDependencies(skillId);
        for (SkillDependency dep : dependencies) {
            if (!skillDependencyValidator.isDependencySatisfied(dep.getDependencyName())) {
                throw new RuntimeException("缺少依赖项: " + dep.getDependencyName());
            }
        }
        
        Map<String, Object> configTemplate;
        try {
            configTemplate = objectMapper.readValue(marketSkill.getConfigTemplate(), Map.class);
        } catch (Exception e) {
            log.warn("配置模板反序列化失败", e);
            configTemplate = Map.of();
        }
        
        SkillConfig installedSkill = skillConfigService.createOrUpdateSkill(
            skillId,
            marketSkill.getSkillName(),
            marketSkill.getCategory(),
            configTemplate,
            "市场技能安装"
        );
        
        incrementDownloads(skillId);
        
        log.info("技能安装成功: skillId={}, userId={}", skillId, userId);
        
        return installedSkill;
    }
    
    @Transactional
    public void uninstallSkill(String skillId, Long userId) {
        SkillConfig skill = skillConfigService.getActiveSkill(skillId);
        if (skill == null) {
            throw new RuntimeException("技能未安装: " + skillId);
        }
        
        List<SkillDependency> dependents = getDependentSkills(skillId);
        if (!dependents.isEmpty()) {
            StringBuilder depNames = new StringBuilder();
            for (SkillDependency dep : dependents) {
                if (depNames.length() > 0) depNames.append(", ");
                depNames.append(dep.getSkillId());
            }
            throw new RuntimeException("依赖项不满足: " + depNames);
    }
    
    skill.setStatus("UNINSTALLED");
    skill.setUpdatedAt(LocalDateTime.now());
    
    log.info("技能卸载成功: skillId={}, userId={}", skillId, userId);
    }
    
    @Transactional
    public SkillConfig updateSkill(String skillId, Long userId) {
        SkillMarket marketSkill = getSkillDetails(skillId);
        if (marketSkill == null) {
            throw new RuntimeException("技能不存在: " + skillId);
    }
    
    SkillConfig installedSkill = skillConfigService.getActiveSkill(skillId);
    if (installedSkill == null) {
        return installSkill(skillId, userId);
    }
    
    if (marketSkill.getVersion().equals(installedSkill.getVersion())) {
        log.info("技能已是最新版本: skillId={}, version={}", skillId, installedSkill.getVersion());
        return installedSkill;
    }
    
    Map<String, Object> newConfig;
    try {
        newConfig = objectMapper.readValue(marketSkill.getConfigTemplate(), Map.class);
    } catch (Exception e) {
        log.warn("配置模板反序列化失败", e);
        newConfig = Map.of();
    }
    
    SkillConfig updatedSkill = skillConfigService.upgradeSkill(
        installedSkill, 
        newConfig, 
        "市场技能更新至版本" + marketSkill.getVersion()
    );
    
    log.info("技能更新成功: skillId={}, oldVersion={}, newVersion={}", 
        skillId, installedSkill.getVersion(), updatedSkill.getVersion());
        
        return updatedSkill;
    }
    
    public List<SkillDependency> getSkillDependencies(String skillId) {
        return skillDependencyMapper.selectList(
            new LambdaQueryWrapper<SkillDependency>()
                .eq(SkillDependency::getSkillId, skillId)
        );
    }
    
    public List<SkillDependency> getDependentSkills(String skillId) {
        return skillDependencyMapper.selectList(
            new LambdaQueryWrapper<SkillDependency>()
                .eq(SkillDependency::getDependencyName, skillId)
        );
    }
    
    public void addDependency(String skillId, String dependencySkillId, String versionConstraint) {
        SkillDependency dependency = new SkillDependency();
        dependency.setSkillId(skillId);
        dependency.setDependencyName(dependencySkillId);
        dependency.setDependencyType("skill");
        dependency.setVersionConstraint(versionConstraint);
        dependency.setIsRequired(true);
        dependency.setCreatedAt(LocalDateTime.now());
        
        skillDependencyMapper.insert(dependency);
        log.info("添加技能依赖: {} -> {}", skillId, dependencySkillId);
    }
    
    public void removeDependency(String skillId, String dependencySkillId) {
        skillDependencyMapper.delete(
            new LambdaQueryWrapper<SkillDependency>()
                .eq(SkillDependency::getSkillId, skillId)
                .eq(SkillDependency::getDependencyName, dependencySkillId)
        );
        log.info("移除技能依赖: {} -> {}", skillId, dependencySkillId);
    }
    
    public void incrementDownloads(String skillId) {
        SkillMarket skill = getSkillDetails(skillId);
        if (skill != null) {
            skill.setDownloads(skill.getDownloads() + 1);
            skill.setUpdatedAt(LocalDateTime.now());
            skillMarketMapper.updateById(skill);
        }
    }
    
    public void updateRating(String skillId, java.math.BigDecimal rating) {
        SkillMarket skill = getSkillDetails(skillId);
        if (skill != null) {
            skill.setRating(rating);
            skill.setUpdatedAt(LocalDateTime.now());
            skillMarketMapper.updateById(skill);
        }
    }
    
    public void deactivateSkill(String skillId) {
        SkillMarket skill = getSkillDetails(skillId);
        if (skill != null) {
            skill.setStatus("inactive");
            skill.setUpdatedAt(LocalDateTime.now());
            skillMarketMapper.updateById(skill);
        }
    }
    
    public boolean isSkillInstalled(String skillId) {
        return skillConfigService.getActiveSkill(skillId) != null;
    }
    
    public boolean isSkillUpgradable(String skillId) {
        SkillMarket marketSkill = getSkillDetails(skillId);
        if (marketSkill == null) {
            return false;
        }
        
        SkillConfig installedSkill = skillConfigService.getActiveSkill(skillId);
        if (installedSkill == null) {
            return false;
        }
        
        return !marketSkill.getVersion().equals(installedSkill.getVersion());
    }
    
    public List<SkillMarket> getInstalledSkills(Long userId) {
        List<SkillConfig> installedConfigs = skillConfigService.getAllActiveSkills();
        List<SkillMarket> installedSkills = new java.util.ArrayList<>();
        
        for (SkillConfig config : installedConfigs) {
            SkillMarket marketSkill = getSkillDetails(config.getSkillCode());
            if (marketSkill != null) {
                installedSkills.add(marketSkill);
            } else {
                SkillMarket localSkill = new SkillMarket();
                localSkill.setSkillId(config.getSkillCode());
                localSkill.setSkillName(config.getSkillName());
                localSkill.setCategory(config.getSkillCategory());
                localSkill.setVersion(config.getVersion());
                localSkill.setStatus("local");
                installedSkills.add(localSkill);
            }
        }
        
        return installedSkills;
    }
    
    public List<SkillMarket> getAvailableUpdates(Long userId) {
        List<SkillMarket> updates = new java.util.ArrayList<>();
        List<SkillMarket> allSkills = getAvailableSkills(null);
        
        for (SkillMarket skill : allSkills) {
            if (isSkillUpgradable(skill.getSkillId())) {
                updates.add(skill);
            }
        }
        
        return updates;
    }
}
