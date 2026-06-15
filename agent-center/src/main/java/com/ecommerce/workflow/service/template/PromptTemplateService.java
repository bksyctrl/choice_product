package com.ecommerce.workflow.service.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.PromptTemplate;
import com.ecommerce.workflow.mapper.PromptTemplateMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptTemplateService {
    
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    
    @Autowired
    private PromptTemplateMapper templateMapper;
    
    private final Configuration freemarkerConfig;
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();
    
    public PromptTemplateService() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_31);
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
    }
    
    @PostConstruct
    public void init() {
        loadTemplatesFromDatabase();
    }
    
    public void loadTemplatesFromDatabase() {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getIsActive, true);
        List<PromptTemplate> templates = templateMapper.selectList(wrapper);
        
        templateCache.clear();
        for (PromptTemplate template : templates) {
            templateCache.put(template.getTemplateCode(), template);
        }
        
        log.info("模板缓存加载完成，共加载 {} 个模板", templates.size());
    }
    
    public String renderTemplate(String templateCode, Map<String, Object> variables) {
        PromptTemplate template = getTemplate(templateCode);
        if (template == null) {
            log.warn("模板不存在: {}", templateCode);
            return "";
        }
        
        return renderTemplateContent(template.getTemplateContent(), variables);
    }
    
    public String renderTemplateContent(String templateContent, Map<String, Object> variables) {
        try {
            Template template = new Template(
                "dynamic", 
                new StringReader(templateContent), 
                freemarkerConfig
            );
            
            StringWriter writer = new StringWriter();
            template.process(variables != null ? variables : Map.of(), writer);
            
            return writer.toString();
            
        } catch (IOException | TemplateException e) {
            log.error("模板渲染失败", e);
            return templateContent;
        }
    }
    
    public PromptTemplate getTemplate(String templateCode) {
        PromptTemplate template = templateCache.get(templateCode);
        if (template == null) {
            template = templateMapper.selectByTemplateCode(templateCode);
            if (template != null) {
                templateCache.put(templateCode, template);
            }
        }
        return template;
    }
    
    public void registerTemplate(PromptTemplate template) {
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template.setIsActive(true);
        
        if (template.getVersion() == null) {
            template.setVersion(1);
        }
        
        templateMapper.insert(template);
        templateCache.put(template.getTemplateCode(), template);
        
        log.info("创建提示词模板: code={}, name={}", template.getTemplateCode(), template.getTemplateName());
    }
    
    public void updateTemplate(String templateCode, PromptTemplate template) {
        PromptTemplate existing = getTemplate(templateCode);
        if (existing != null) {
            template.setId(existing.getId());
            template.setTemplateCode(templateCode);
            template.setVersion(existing.getVersion() + 1);
            template.setUpdatedAt(LocalDateTime.now());
            
            templateMapper.updateById(template);
            templateCache.put(templateCode, template);
            
            log.info("更新模板版本: code={}, version={}", templateCode, template.getVersion());
        }
    }
    
    public void deleteTemplate(String templateCode) {
        PromptTemplate template = getTemplate(templateCode);
        if (template != null) {
            template.setIsActive(false);
            template.setUpdatedAt(LocalDateTime.now());
            templateMapper.updateById(template);
            templateCache.remove(templateCode);
            
            log.info("模板缓存更新: code={}", templateCode);
        }
    }
    
    public List<PromptTemplate> getAllTemplates() {
        return templateMapper.selectList(null);
    }
    
    public void clearCache() {
        templateCache.clear();
    }
    
    public void reloadCache() {
        loadTemplatesFromDatabase();
    }
}
