package com.ecommerce.workflow.config;

import com.ecommerce.workflow.entity.VideoPromptTemplate;
import com.ecommerce.workflow.mapper.VideoPromptTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class VideoPromptTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptTemplateInitializer.class);
    private final VideoPromptTemplateMapper templateMapper;

    public VideoPromptTemplateInitializer(VideoPromptTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化视频提示词模板...");
        initDefaultTemplates();
        log.info("视频提示词模板初始化完成");
    }

    private void initDefaultTemplates() {
        try {
            Long count = templateMapper.selectCount(null);
            if (count > 0) {
                log.info("视频提示词模板已存在，跳过初始化 (数量: {})", count);
                return;
            }

            createDefaultTemplate();
            createClothingTemplate();
            createFoodTemplate();
            createDigitalTemplate();

            log.info("默认视频提示词模板创建完成");
        } catch (Exception e) {
            log.error("初始化视频提示词模板失败", e);
        }
    }

    private void createDefaultTemplate() {
        VideoPromptTemplate template = new VideoPromptTemplate();
        template.setTemplateCode("default_general");
        template.setTemplateName("通用视频模板");
        template.setCategory("通用");
        template.setRules(getDefaultRules());
        template.setSystemPrefix("你是世界顶级的视频内容创作专家，擅长使用纯文本自然语言描述生成高质量视频提示词。");
        template.setDescription("通用场景默认模板");
        template.setVersion(1);
        template.setSuccessRate(java.math.BigDecimal.valueOf(0.5));
        template.setUsageCount(0);
        template.setAvgQualityScore(java.math.BigDecimal.valueOf(0.5));
        template.setLearnedFromCases(0);
        template.setIsActive(true);
        template.setDeleted(0);

        templateMapper.insert(template);
        log.info("创建默认模板: code={}", template.getTemplateCode());
    }

    private void createClothingTemplate() {
        VideoPromptTemplate template = new VideoPromptTemplate();
        template.setTemplateCode("clothing_fashion");
        template.setTemplateName("服装穿搭模板");
        template.setCategory("服装");
        template.setRules(getDefaultRules());
        template.setSystemPrefix("你是专业的服装视频内容创作专家，擅长使用纯文本自然语言描述展示服装穿搭和时尚风格。");
        template.setDescription("服装穿搭类视频专用模板");
        template.setVersion(1);
        template.setSuccessRate(java.math.BigDecimal.valueOf(0.5));
        template.setUsageCount(0);
        template.setAvgQualityScore(java.math.BigDecimal.valueOf(0.5));
        template.setLearnedFromCases(0);
        template.setIsActive(true);
        template.setDeleted(0);

        templateMapper.insert(template);
        log.info("创建服装模板: code={}", template.getTemplateCode());
    }

    private void createFoodTemplate() {
        VideoPromptTemplate template = new VideoPromptTemplate();
        template.setTemplateCode("food_cuisine");
        template.setTemplateName("美食餐饮模板");
        template.setCategory("食品");
        template.setRules(getDefaultRules());
        template.setSystemPrefix("你是专业的美食视频内容创作专家，擅长使用纯文本自然语言描述展示美食制作和品尝过程。");
        template.setDescription("美食餐饮类视频专用模板");
        template.setVersion(1);
        template.setSuccessRate(java.math.BigDecimal.valueOf(0.5));
        template.setUsageCount(0);
        template.setAvgQualityScore(java.math.BigDecimal.valueOf(0.5));
        template.setLearnedFromCases(0);
        template.setIsActive(true);
        template.setDeleted(0);

        templateMapper.insert(template);
        log.info("创建食品模板: code={}", template.getTemplateCode());
    }

    private void createDigitalTemplate() {
        VideoPromptTemplate template = new VideoPromptTemplate();
        template.setTemplateCode("digital_tech");
        template.setTemplateName("数码科技模板");
        template.setCategory("数码");
        template.setRules(getDefaultRules());
        template.setSystemPrefix("你是专业的数码科技视频内容创作专家，擅长使用纯文本自然语言描述展示产品功能和科技魅力。");
        template.setDescription("数码科技类视频专用模板");
        template.setVersion(1);
        template.setSuccessRate(java.math.BigDecimal.valueOf(0.5));
        template.setUsageCount(0);
        template.setAvgQualityScore(java.math.BigDecimal.valueOf(0.5));
        template.setLearnedFromCases(0);
        template.setIsActive(true);
        template.setDeleted(0);

        templateMapper.insert(template);
        log.info("创建数码模板: code={}", template.getTemplateCode());
    }

    private String getDefaultRules() {
        return """
            重要规则:
            1. 输出格式必须是纯JSON格式，包含scenes数组！
            2. 提示词总长度必须严格控制在800字符以内！
            3. 每个分镜的description字段限制在150字符以内
            4. 视频中禁止出现任何文字（包括字幕、标题、标签、水印文字等）
            5. 每个分镜必须保持人物、场景、光线的连贯性和一致性
            6. 人物形象必须严格贴合参考图，杜绝陌生面孔和网红脸
            7. 追求原生写实主义，不磨皮、不美颜、无滤镜
            8. 产品展示必须真实，禁止穿模、悬浮、变形、比例失真等物理错误
            9. 产品与人物手部、桌面等接触面必须自然贴合，禁止穿透或悬浮
            """;
    }
}
