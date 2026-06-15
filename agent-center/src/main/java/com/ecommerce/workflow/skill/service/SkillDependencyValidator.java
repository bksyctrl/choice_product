package com.ecommerce.workflow.skill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.skill.entity.SkillDependency;
import com.ecommerce.workflow.skill.mapper.SkillDependencyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SkillDependencyValidator {
    
    private static final Logger log = LoggerFactory.getLogger(SkillDependencyValidator.class);
    
    @Autowired
    private SkillDependencyMapper skillDependencyMapper;
    
    public ValidationResult validateDependencies(String skillId) {
        ValidationResult result = new ValidationResult();
        result.setSkillId(skillId);
        result.setValid(true);
        
        List<SkillDependency> dependencies = getDependencies(skillId);
        
        for (SkillDependency dependency : dependencies) {
            DependencyCheckResult checkResult = checkDependency(dependency);
            
            if (!checkResult.isSatisfied() && dependency.getIsRequired()) {
                result.setValid(false);
                result.getMissingDependencies().add(dependency);
            } else if (!checkResult.isSatisfied()) {
                result.getOptionalMissing().add(dependency);
            } else {
                result.getSatisfiedDependencies().add(dependency);
            }
        }
        
        log.info("技能依赖验证完成: skillId={}, valid={}, missing={}", 
                skillId, result.isValid(), result.getMissingDependencies().size());
        
        return result;
    }
    
    public List<SkillDependency> getDependencies(String skillId) {
        LambdaQueryWrapper<SkillDependency> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillDependency::getSkillId, skillId);
        return skillDependencyMapper.selectList(wrapper);
    }
    
    public void addDependency(String skillId, String dependencyType, String dependencyName, 
                               Boolean isRequired, String versionConstraint) {
        SkillDependency dependency = new SkillDependency();
        dependency.setSkillId(skillId);
        dependency.setDependencyType(dependencyType);
        dependency.setDependencyName(dependencyName);
        dependency.setIsRequired(isRequired);
        dependency.setVersionConstraint(versionConstraint);
        dependency.setCreatedAt(LocalDateTime.now());
        
        skillDependencyMapper.insert(dependency);
        log.info("添加技能依赖: skillId={}, dependency={}", skillId, dependencyName);
    }
    
    public void removeDependency(Long dependencyId) {
        skillDependencyMapper.deleteById(dependencyId);
        log.info("移除技能依赖: id={}", dependencyId);
    }
    
    private DependencyCheckResult checkDependency(SkillDependency dependency) {
        DependencyCheckResult result = new DependencyCheckResult();
        result.setDependency(dependency);
        
        switch (dependency.getDependencyType()) {
            case "skill":
                result.setSatisfied(checkSkillDependency(dependency.getDependencyName()));
                break;
            case "service":
                result.setSatisfied(checkServiceDependency(dependency.getDependencyName()));
                break;
            case "config":
                result.setSatisfied(checkConfigDependency(dependency.getDependencyName()));
                break;
            default:
                result.setSatisfied(false);
        }
        
        return result;
    }
    
    private boolean checkSkillDependency(String skillCode) {
        return true;
    }
    
    private boolean checkServiceDependency(String serviceName) {
        return true;
    }
    
    private boolean checkConfigDependency(String configKey) {
        return true;
    }
    
    public boolean isDependencySatisfied(String dependencyName) {
        return checkSkillDependency(dependencyName);
    }
    
    public static class ValidationResult {
        private String skillId;
        private boolean valid;
        private List<SkillDependency> missingDependencies = new ArrayList<>();
        private List<SkillDependency> optionalMissing = new ArrayList<>();
        private List<SkillDependency> satisfiedDependencies = new ArrayList<>();
        
        public String getSkillId() {
            return skillId;
        }
        
        public void setSkillId(String skillId) {
            this.skillId = skillId;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public List<SkillDependency> getMissingDependencies() {
            return missingDependencies;
        }
        
        public void setMissingDependencies(List<SkillDependency> missingDependencies) {
            this.missingDependencies = missingDependencies;
        }
        
        public List<SkillDependency> getOptionalMissing() {
            return optionalMissing;
        }
        
        public void setOptionalMissing(List<SkillDependency> optionalMissing) {
            this.optionalMissing = optionalMissing;
        }
        
        public List<SkillDependency> getSatisfiedDependencies() {
            return satisfiedDependencies;
        }
        
        public void setSatisfiedDependencies(List<SkillDependency> satisfiedDependencies) {
            this.satisfiedDependencies = satisfiedDependencies;
        }
    }
    
    private static class DependencyCheckResult {
        private SkillDependency dependency;
        private boolean satisfied;
        
        public SkillDependency getDependency() {
            return dependency;
        }
        
        public void setDependency(SkillDependency dependency) {
            this.dependency = dependency;
        }
        
        public boolean isSatisfied() {
            return satisfied;
        }
        
        public void setSatisfied(boolean satisfied) {
            this.satisfied = satisfied;
        }
    }
}
