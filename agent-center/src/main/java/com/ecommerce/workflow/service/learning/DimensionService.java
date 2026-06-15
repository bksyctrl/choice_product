package com.ecommerce.workflow.service.learning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DimensionService {
    private static final Logger log = LoggerFactory.getLogger(DimensionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, DimensionCategory> DIMENSION_CATEGORIES = new LinkedHashMap<>();

    static {
        DIMENSION_CATEGORIES.put("PRODUCT", new DimensionCategory("产品维度", Arrays.asList(
            new DimensionDef("PROD_COMPETITOR_COUNT", "竞品数量", "number", 0.08, 8),
            new DimensionDef("PROD_COMPETITOR_PRICE_RANGE", "竞品价格区间", "range", 0.06, 7),
            new DimensionDef("PROD_MARKET_SIZE", "市场规模", "enum:小/中/大/超大", 0.07, 8),
            new DimensionDef("PROD_MARKET_TREND", "市场趋势", "enum:上升趋势/下降趋势/平稳", 0.09, 9),
            new DimensionDef("PROD_CATEGORY_LEVEL", "品类级别", "enum:一级/二级/三级/四级", 0.05, 6),
            new DimensionDef("PROD_CATEGORY_SEASONALITY", "品类季节性", "enum:强/弱/中/无", 0.08, 8),
            new DimensionDef("PROD_TARGET_AGE", "目标年龄", "range", 0.06, 7),
            new DimensionDef("PROD_TARGET_GENDER", "目标性别", "enum:男/女/中性", 0.05, 6),
            new DimensionDef("PROD_TARGET_REGION", "目标地区", "multi:一线/二线/三线/四线/海外", 0.06, 7),
            new DimensionDef("PROD_TARGET_CONSUMPTION", "目标消费力", "enum:低/中/高/超高", 0.07, 7),
            new DimensionDef("PROD_MAIN_IMAGE_COUNT", "主图数量", "number", 0.04, 5),
            new DimensionDef("PROD_DETAIL_LENGTH", "详情页长度", "number", 0.05, 6),
            new DimensionDef("PROD_VIDEO_COUNT", "视频数量", "number", 0.06, 7),
            new DimensionDef("PROD_ADD_TO_CART_RATE", "加购率", "percentage", 0.09, 9),
            new DimensionDef("PROD_FAVORITE_RATE", "收藏率", "percentage", 0.07, 8),
            new DimensionDef("PROD_RETURN_RATE", "退货率", "percentage", 0.08, 8),
            new DimensionDef("PROD_SEASON_TAG", "季节标签", "enum:春季/夏季/秋季/冬季", 0.07, 7),
            new DimensionDef("PROD_LISTING_DAYS", "上架天数", "number", 0.05, 6),
            new DimensionDef("PROD_PRICE_STRATEGY", "价格策略", "enum:低价/中价/高价/奢华", 0.06, 7),
            new DimensionDef("PROD_PROMO_FREQUENCY", "促销频率", "enum:少/中/多/频繁", 0.06, 7),
            new DimensionDef("PROD_DISCOUNT_DEPTH", "折扣深度", "percentage", 0.07, 7)
        )));

        DIMENSION_CATEGORIES.put("VIDEO", new DimensionCategory("视频维度", Arrays.asList(
            new DimensionDef("VIDEO_OPENING_TYPE", "开头类型", "enum:悬念/反转/冲突/惊喜/数据", 0.09, 9),
            new DimensionDef("VIDEO_MIDDLE_STRUCTURE", "中间结构", "enum:递进/并列/转折/对比", 0.08, 8),
            new DimensionDef("VIDEO_ENDING_TYPE", "结尾类型", "enum:CTA/悬念/惊喜/互动", 0.08, 8),
            new DimensionDef("VIDEO_NARRATIVE_FRAME", "叙事框架", "enum:英雄/悲剧/喜剧/对比/成长", 0.09, 9),
            new DimensionDef("VIDEO_EMOTION_FRAME", "情感框架", "enum:恐惧/焦虑/好奇/惊喜/愤怒", 0.10, 10),
            new DimensionDef("VIDEO_TRANSITION_TYPE", "转场类型", "enum:硬切/淡入淡出/溶解/缩放", 0.05, 6),
            new DimensionDef("VIDEO_PACE", "节奏速度", "enum:慢/中/快/极快", 0.07, 7),
            new DimensionDef("VIDEO_EFFECT_USAGE", "特效使用", "enum:无/少/中/多", 0.05, 6),
            new DimensionDef("VIDEO_COLOR_TONE", "色调", "enum:暖色/冷色/中性/高饱和", 0.06, 7),
            new DimensionDef("VIDEO_FILTER_STYLE", "滤镜风格", "enum:无/复古/胶片/电影/清新", 0.05, 6),
            new DimensionDef("VIDEO_SUBTITLE_STYLE", "字幕风格", "enum:无/简约/动态/创意", 0.05, 6),
            new DimensionDef("VIDEO_BG_MUSIC_TYPE", "背景音乐类型", "enum:无/轻音乐/摇滚/电子/清新", 0.07, 7),
            new DimensionDef("VIDEO_SOUND_EFFECT", "音效使用", "enum:无/少/中/多", 0.06, 7),
            new DimensionDef("VOICE_STYLE", "配音风格", "enum:无/专业配音/真人/机器/清新", 0.08, 8),
            new DimensionDef("VIDEO_PERSON_COUNT", "人物数量", "number", 0.05, 6),
            new DimensionDef("VIDEO_PERSON_TYPE", "人物类型", "enum:无/明星/网红/模特/素人", 0.07, 7),
            new DimensionDef("VIDEO_ACTING_STYLE", "表演风格", "enum:无/夸张/幽默/专业/自然", 0.06, 7),
            new DimensionDef("VIDEO_HOOK_POSITION", "钩子位置", "enum:开头/中间/结尾/全程", 0.09, 9),
            new DimensionDef("VIDEO_HOOK_TYPE", "钩子类型", "enum:数据/故事/问题/挑战/冲突", 0.10, 10),
            new DimensionDef("VIDEO_HOOK_MOTIVATION", "钩子动机", "enum:愤怒/恐惧/焦虑/好奇/惊喜", 0.10, 10),
            new DimensionDef("VIDEO_TARGET_AUDIENCE", "目标受众匹配度", "multi:男性/女性/老人/儿童/青少年", 0.08, 8),
            new DimensionDef("VIDEO_PAIN_POINT_MATCH", "痛点匹配度", "percentage", 0.09, 9),
            new DimensionDef("VIDEO_DURATION", "视频时长", "number:秒", 0.06, 7),
            new DimensionDef("VIDEO_ASPECT_RATIO", "视频比例", "enum:16:9/9:16/1:1/4:5", 0.05, 6)
        )));

        DIMENSION_CATEGORIES.put("IMAGE_TEXT", new DimensionCategory("图文维度", Arrays.asList(
            new DimensionDef("TEXT_TITLE_TYPE", "标题类型", "enum:疑问/感叹/数据/冲突/惊喜", 0.09, 9),
            new DimensionDef("TEXT_BODY_STRUCTURE", "正文结构", "enum:总分/并列/递进/转折", 0.08, 8),
            new DimensionDef("TEXT_LAYOUT_STYLE", "排版风格", "enum:简约/现代/复古/奢华", 0.06, 7),
            new DimensionDef("TEXT_COLOR_SCHEME", "配色方案", "enum:暖色/冷色/中性/对比", 0.06, 7),
            new DimensionDef("TEXT_FONT_CHOICE", "字体选择", "enum:黑体/宋体/手写/艺术", 0.05, 6),
            new DimensionDef("TEXT_IMAGE_STYLE", "图片风格", "enum:实景/插画/3D/扁平", 0.07, 7),
            new DimensionDef("TEXT_COPY_STYLE", "文案风格", "enum:感性/理性/幽默/专业", 0.08, 8),
            new DimensionDef("TEXT_KEYWORD_DENSITY", "关键词密度", "percentage", 0.07, 7),
            new DimensionDef("TEXT_EMOTION_TENDENCY", "情感倾向", "enum:积极/消极/中性/对比", 0.07, 7),
            new DimensionDef("TEXT_QUESTION_TYPE", "提问类型", "enum:是否/选择/开放/反问", 0.06, 7),
            new DimensionDef("TEXT_GUIDE_LANGUAGE", "引导语", "enum:无/命令/建议/请求", 0.07, 7),
            new DimensionDef("TEXT_CTA_TYPE", "CTA类型", "enum:无/立即购买/关注/分享/咨询", 0.08, 8)
        )));

        DIMENSION_CATEGORIES.put("CONTEXT", new DimensionCategory("场景维度", Arrays.asList(
            new DimensionDef("CTX_PLATFORM", "平台", "enum:抖音/快手/小红书/淘宝/微信", 0.08, 8),
            new DimensionDef("CTX_ACCOUNT_TYPE", "账号类型", "enum:个人/企业/品牌/官方", 0.07, 7),
            new DimensionDef("CTX_ACCOUNT_LEVEL", "账号等级", "enum:普通/银牌/金牌/钻石", 0.07, 7),
            new DimensionDef("CTX_PUBLISH_TIME", "发布时间", "time", 0.06, 7),
            new DimensionDef("CTX_PUBLISH_DAY", "发布日期", "enum:工作日/周末/节假日", 0.05, 6),
            new DimensionDef("CTX_CONTENT_FREQUENCY", "发布频率", "enum:低/中/高/超高", 0.06, 7),
            new DimensionDef("CTX_HOTSPOT_RELEVANCE", "热点关联度", "enum:高/低/中/无", 0.07, 7),
            new DimensionDef("CTX_COMPETITION_INTENSITY", "竞争强度", "enum:低/中/高/超高", 0.08, 8)
        )));
    }

    public static class DimensionCategory {
        private String categoryName;
        private List<DimensionDef> dimensions;

        public DimensionCategory(String categoryName, List<DimensionDef> dimensions) {
            this.categoryName = categoryName;
            this.dimensions = dimensions;
        }

        public String getCategoryName() { return categoryName; }
        public List<DimensionDef> getDimensions() { return dimensions; }
    }

    public static class DimensionDef {
        private String code;
        private String name;
        private String valueType;
        private double defaultWeight;
        private int importance;

        public DimensionDef(String code, String name, String valueType, double defaultWeight, int importance) {
            this.code = code;
            this.name = name;
            this.valueType = valueType;
            this.defaultWeight = defaultWeight;
            this.importance = importance;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getValueType() { return valueType; }
        public double getDefaultWeight() { return defaultWeight; }
        public int getImportance() { return importance; }
    }

    public DimensionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initTables();
        initDimensionDefinitions();
    }

    private void initTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS biz_dimension_definition (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                dimension_code VARCHAR(50) UNIQUE NOT NULL,
                dimension_name VARCHAR(100) NOT NULL,
                dimension_category VARCHAR(50) NOT NULL,
                dimension_type VARCHAR(20) NOT NULL,
                value_type VARCHAR(1500),
                value_options TEXT,
                default_weight DECIMAL(5,4) DEFAULT 0.05,
                importance INT DEFAULT 5,
                description TEXT,
                enabled INT DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted INT DEFAULT 0,
                INDEX idx_category (dimension_category),
                INDEX idx_code (dimension_code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS biz_case_dimension (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                case_id BIGINT NOT NULL,
                dimension_code VARCHAR(50) NOT NULL,
                dimension_value TEXT,
                dimension_score DECIMAL(5,2),
                weight DECIMAL(5,4),
                dimension_note TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                deleted INT DEFAULT 0,
                INDEX idx_case (case_id),
                INDEX idx_dimension (dimension_code),
                UNIQUE KEY uk_case_dimension (case_id, dimension_code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS biz_dimension_weight (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                dimension_code VARCHAR(50) NOT NULL,
                scenario_type VARCHAR(50) NOT NULL,
                learned_weight DECIMAL(5,4),
                success_count INT DEFAULT 0,
                fail_count INT DEFAULT 0,
                last_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_scenario (scenario_type),
                UNIQUE KEY uk_dimension_scenario (dimension_code, scenario_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS biz_dimension_correlation (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                dimension_code1 VARCHAR(50) NOT NULL,
                dimension_code2 VARCHAR(50) NOT NULL,
                scenario_type VARCHAR(50),
                correlation_score DECIMAL(5,4),
                sample_count INT DEFAULT 0,
                last_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_dim1 (dimension_code1),
                INDEX idx_dim2 (dimension_code2),
                UNIQUE KEY uk_dimension_pair (dimension_code1, dimension_code2, scenario_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS biz_pattern_library (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                pattern_type VARCHAR(50) NOT NULL,
                pattern_name VARCHAR(100) NOT NULL,
                dimension_config TEXT NOT NULL,
                success_rate DECIMAL(5,4),
                sample_count INT DEFAULT 0,
                avg_cvr DECIMAL(8,4),
                avg_gmv DECIMAL(12,2),
                applicable_scenarios TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted INT DEFAULT 0,
                INDEX idx_type (pattern_type),
                INDEX idx_success (success_rate DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        log.info("维度服务初始化完成");
    }

    private void initDimensionDefinitions() {
        int count = 0;
        for (Map.Entry<String, DimensionCategory> entry : DIMENSION_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            DimensionCategory dimCategory = entry.getValue();

            for (DimensionDef dim : dimCategory.getDimensions()) {
                try {
                    int exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM biz_dimension_definition WHERE dimension_code = ?",
                        Integer.class, dim.getCode());

                    if (exists == 0) {
                        jdbcTemplate.update("""
                            INSERT INTO biz_dimension_definition 
                            (dimension_code, dimension_name, dimension_category, dimension_type, 
                             value_type, default_weight, importance, enabled)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                            """, dim.getCode(), dim.getName(), category, 
                            category.equals("PRODUCT") ? "PRODUCT" : 
                            category.equals("VIDEO") ? "VIDEO" : 
                            category.equals("IMAGE_TEXT") ? "IMAGE_TEXT" : "CONTEXT",
                            dim.getValueType(), dim.getDefaultWeight(), dim.getImportance());
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("维度定义初始化失败: {}", dim.getCode(), e);
                }
            }
        }

        log.info("维度定义初始化完成, 新增{}个维度", count);
    }

    @Transactional
    public void recordCaseDimensions(Long caseId, Map<String, String> dimensionValues, 
                                      String scenarioType, boolean isSuccess) {
        for (Map.Entry<String, String> entry : dimensionValues.entrySet()) {
            String dimCode = entry.getKey();
            String dimValue = entry.getValue();

            try {
                jdbcTemplate.update("""
                    INSERT INTO biz_case_dimension 
                    (case_id, dimension_code, dimension_value, weight)
                    VALUES (?, ?, ?, 
                        (SELECT default_weight FROM biz_dimension_definition WHERE dimension_code = ?))
                    ON DUPLICATE KEY UPDATE 
                    dimension_value = VALUES(dimension_value),
                    weight = VALUES(weight)
                    """, caseId, dimCode, dimValue, dimCode);

                updateDimensionWeight(dimCode, scenarioType, isSuccess);
            } catch (Exception e) {
                log.warn("记录案例维度数据异常: caseId={}, dimCode={}", caseId, dimCode, e);
            }
        }

        updateDimensionCorrelations(dimensionValues.keySet(), scenarioType, isSuccess);

        log.info("记录案例维度完成: caseId={}, 维度数={}", caseId, dimensionValues.size());
    }

    private void updateDimensionWeight(String dimCode, String scenarioType, boolean isSuccess) {
        try {
            int exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM biz_dimension_weight WHERE dimension_code = ? AND scenario_type = ?",
                Integer.class, dimCode, scenarioType);

            if (exists == 0) {
                jdbcTemplate.update("""
                    INSERT INTO biz_dimension_weight 
                    (dimension_code, scenario_type, learned_weight, success_count, fail_count)
                    VALUES (?, ?, 
                        (SELECT default_weight FROM biz_dimension_definition WHERE dimension_code = ?),
                        ?, ?)
                    """, dimCode, scenarioType, dimCode, isSuccess ? 1 : 0, isSuccess ? 0 : 1);
            } else {
                if (isSuccess) {
                    jdbcTemplate.update("""
                        UPDATE biz_dimension_weight 
                        SET success_count = success_count + 1,
                            learned_weight = LEARNED_WEIGHT * 1.05
                        WHERE dimension_code = ? AND scenario_type = ?
                        """, dimCode, scenarioType);
                } else {
                    jdbcTemplate.update("""
                        UPDATE biz_dimension_weight 
                        SET fail_count = fail_count + 1,
                            learned_weight = LEARNED_WEIGHT * 0.95
                        WHERE dimension_code = ? AND scenario_type = ?
                        """, dimCode, scenarioType);
                }
            }
        } catch (Exception e) {
            log.warn("更新维度权重异常: dimCode={}", dimCode, e);
        }
    }

    private void updateDimensionCorrelations(Set<String> dimCodes, String scenarioType, boolean isSuccess) {
        List<String> codes = new ArrayList<>(dimCodes);
        for (int i = 0; i < codes.size(); i++) {
            for (int j = i + 1; j < codes.size(); j++) {
                String code1 = codes.get(i);
                String code2 = codes.get(j);

                try {
                    double correlationDelta = isSuccess ? 0.01 : -0.01;

                    jdbcTemplate.update("""
                        INSERT INTO biz_dimension_correlation 
                        (dimension_code1, dimension_code2, scenario_type, correlation_score, sample_count)
                        VALUES (?, ?, ?, ?, 1)
                        ON DUPLICATE KEY UPDATE 
                        correlation_score = correlation_score + ?,
                        sample_count = sample_count + 1
                        """, code1, code2, scenarioType, correlationDelta, correlationDelta);
                } catch (Exception e) {
                    log.debug("更新维度关联异常: {}-{}", code1, code2);
                }
            }
        }
    }

    public List<Map<String, Object>> getTopCorrelatedDimensions(String dimCode, String scenarioType, int limit) {
        return jdbcTemplate.queryForList("""
            SELECT dimension_code1, dimension_code2, correlation_score, sample_count
            FROM biz_dimension_correlation
            WHERE (dimension_code1 = ? OR dimension_code2 = ?)
            AND (? IS NULL OR scenario_type = ?)
            ORDER BY ABS(correlation_score) DESC
            LIMIT ?
            """, dimCode, dimCode, scenarioType, scenarioType, limit);
    }

    public Map<String, DimensionCategory> getDimensionCategories() {
        return new HashMap<>(DIMENSION_CATEGORIES);
    }

    public List<DimensionDef> getDimensionsByCategory(String category) {
        DimensionCategory dimCategory = DIMENSION_CATEGORIES.get(category);
        return dimCategory != null ? dimCategory.getDimensions() : new ArrayList<>();
    }

    public List<Map<String, Object>> getDimensionDefinitions() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM biz_dimension_definition WHERE enabled = 1 AND deleted = 0 ORDER BY dimension_category, importance DESC");
    }

    public List<Map<String, Object>> getLearnedWeights(String scenarioType) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT dimension_code as dimensionCode, dimension_code as dimensionName, " +
                "learned_weight as learnedWeight, success_count as successCount, fail_count as failCount " +
                "FROM biz_dimension_weight WHERE scenario_type = ? ORDER BY learned_weight DESC", 
                scenarioType);
        } catch (Exception e) {
            log.error("获取学习权重失败", e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getDimensionCorrelations(String scenarioType) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT dimension_code1 as dimensionCode1, dimension_code2 as dimensionCode2, " +
                "correlation_score as correlationScore, sample_count as sampleCount, " +
                "scenario_type as scenarioType " +
                "FROM biz_dimension_correlation " +
                "WHERE scenario_type = ? OR ? IS NULL " +
                "ORDER BY ABS(correlation_score) DESC LIMIT 100",
                scenarioType, scenarioType);
        } catch (Exception e) {
            log.error("获取维度关联异常", e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getSuccessPatterns() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, pattern_type as patternType, pattern_name as patternName, " +
                "dimension_config as dimensionConfig, success_rate as successRate, " +
                "sample_count as sampleCount, avg_cvr as avgCvr, avg_gmv as avgGmv, " +
                "applicable_scenarios as applicableScenarios " +
                "FROM biz_pattern_library WHERE deleted = 0 ORDER BY success_rate DESC, sample_count DESC");
        } catch (Exception e) {
            log.error("获取成功模式失败", e);
            return new ArrayList<>();
        }
    }

    public Map<String, String> extractFromVideo(String videoTitle, String videoDescription, 
                                                  String videoTags, String videoContent) {
        Map<String, String> dimensions = new HashMap<>();

        dimensions.putAll(extractVideoStructure(videoTitle, videoDescription));
        dimensions.putAll(extractVideoEmotion(videoTitle, videoDescription, videoTags));
        dimensions.putAll(extractVideoHook(videoTitle, videoContent));
        dimensions.putAll(extractVideoAudience(videoTags, videoDescription));
        dimensions.putAll(extractVideoTechnical(videoContent));

        log.debug("视频维度提取完成，共提取 {} 个维度", dimensions.size());
        return dimensions;
    }

    private Map<String, String> extractVideoStructure(String title, String description) {
        Map<String, String> dims = new HashMap<>();
        dims.put("VIDEO_OPENING_TYPE", detectOpeningType(title));
        dims.put("VIDEO_MIDDLE_STRUCTURE", detectMiddleStructure(description));
        dims.put("VIDEO_ENDING_TYPE", detectEndingType(description));
        dims.put("VIDEO_NARRATIVE_FRAME", detectNarrativeFrame(title, description));
        return dims;
    }

    private Map<String, String> extractVideoEmotion(String title, String description, String tags) {
        Map<String, String> dims = new HashMap<>();
        dims.put("VIDEO_EMOTION_FRAME", detectEmotionFrame(title + " " + description + " " + tags));
        return dims;
    }

    private Map<String, String> extractVideoHook(String title, String content) {
        Map<String, String> dims = new HashMap<>();
        dims.put("VIDEO_HOOK_TYPE", detectHookType(title));
        dims.put("VIDEO_HOOK_POSITION", detectHookPosition(content));
        dims.put("VIDEO_HOOK_MOTIVATION", detectHookMotivation(title));
        return dims;
    }

    private Map<String, String> extractVideoAudience(String tags, String description) {
        Map<String, String> dims = new HashMap<>();
        dims.put("VIDEO_TARGET_AUDIENCE", detectTargetAudience(tags, description));
        return dims;
    }

    private Map<String, String> extractVideoTechnical(String content) {
        Map<String, String> dims = new HashMap<>();
        dims.put("VIDEO_PACE", detectPace(content));
        dims.put("VIDEO_EFFECT_USAGE", detectEffectUsage(content));
        return dims;
    }

    public Map<String, String> extractFromImageText(String title, String content, 
                                                      String images, String layout) {
        Map<String, String> dimensions = new HashMap<>();

        dimensions.putAll(extractTextTitle(title));
        dimensions.putAll(extractTextBody(content));
        dimensions.putAll(extractTextVisual(images, layout));
        dimensions.putAll(extractTextInteraction(content));

        log.debug("图文维度提取完成，共提取 {} 个维度", dimensions.size());
        return dimensions;
    }

    private Map<String, String> extractTextTitle(String title) {
        Map<String, String> dims = new HashMap<>();
        dims.put("TEXT_TITLE_TYPE", detectTitleType(title));
        return dims;
    }

    private Map<String, String> extractTextBody(String content) {
        Map<String, String> dims = new HashMap<>();
        dims.put("TEXT_BODY_STRUCTURE", detectBodyStructure(content));
        dims.put("TEXT_COPY_STYLE", detectCopyStyle(content));
        dims.put("TEXT_EMOTION_TENDENCY", detectEmotionTendency(content));
        return dims;
    }

    private Map<String, String> extractTextVisual(String images, String layout) {
        Map<String, String> dims = new HashMap<>();
        dims.put("TEXT_LAYOUT_STYLE", detectLayoutStyle(layout));
        dims.put("TEXT_IMAGE_STYLE", detectImageStyle(images));
        return dims;
    }

    private Map<String, String> extractTextInteraction(String content) {
        Map<String, String> dims = new HashMap<>();
        dims.put("TEXT_CTA_TYPE", detectCtaType(content));
        dims.put("TEXT_QUESTION_TYPE", detectQuestionType(content));
        return dims;
    }

    public Map<String, String> extractFromProduct(String productName, String category,
                                                    String description, String price,
                                                    String targetAudience) {
        Map<String, String> dimensions = new HashMap<>();

        dimensions.putAll(extractProductBasic(productName, category, price));
        dimensions.putAll(extractProductTarget(targetAudience, description));
        dimensions.putAll(extractProductStrategy(price, description));

        log.debug("产品维度提取完成，共提取 {} 个维度", dimensions.size());
        return dimensions;
    }

    private Map<String, String> extractProductBasic(String name, String category, String price) {
        Map<String, String> dims = new HashMap<>();
        dims.put("PROD_CATEGORY_LEVEL", detectCategoryLevel(category));
        dims.put("PROD_PRICE_STRATEGY", detectPriceStrategy(price));
        return dims;
    }

    private Map<String, String> extractProductTarget(String audience, String description) {
        Map<String, String> dims = new HashMap<>();
        dims.put("PROD_TARGET_AGE", detectTargetAge(audience, description));
        dims.put("PROD_TARGET_GENDER", detectTargetGender(audience, description));
        dims.put("PROD_TARGET_REGION", detectTargetRegion(audience, description));
        dims.put("PROD_TARGET_CONSUMPTION", detectConsumption(audience, description));
        return dims;
    }

    private Map<String, String> extractProductStrategy(String price, String description) {
        Map<String, String> dims = new HashMap<>();
        dims.put("PROD_PROMO_FREQUENCY", detectPromoFrequency(description));
        return dims;
    }

    public Map<String, Object> recommendDimensions(String scenarioType,
                                                     Map<String, String> currentDimensions,
                                                     double minSuccessRate) {
        Map<String, Object> recommendation = new HashMap<>();

        List<Map<String, Object>> similarPatterns = findSimilarPatterns(scenarioType, currentDimensions, minSuccessRate);
        List<Map<String, Object>> suggestedDimensions = suggestMissingDimensions(scenarioType, currentDimensions, similarPatterns);
        List<Map<String, Object>> avoidDimensions = findAvoidDimensions(scenarioType, currentDimensions);
        List<Map<String, Object>> optimalCombinations = findOptimalCombinations(scenarioType, currentDimensions);

        recommendation.put("similarPatterns", similarPatterns);
        recommendation.put("suggestedDimensions", suggestedDimensions);
        recommendation.put("avoidDimensions", avoidDimensions);
        recommendation.put("optimalCombinations", optimalCombinations);
        recommendation.put("confidence", calculateConfidence(similarPatterns.size()));
        recommendation.put("recommendation", generateRecommendationText(similarPatterns, suggestedDimensions, avoidDimensions));

        return recommendation;
    }

    private List<Map<String, Object>> findSimilarPatterns(String scenarioType,
                                                           Map<String, String> currentDimensions,
                                                           double minSuccessRate) {
        try {
            String sql = "SELECT * FROM biz_pattern_library " +
                        "WHERE pattern_type = ? AND success_rate >= ? AND deleted = 0 " +
                        "ORDER BY success_rate DESC, sample_count DESC LIMIT 5";

            List<Map<String, Object>> patterns = jdbcTemplate.queryForList(sql, scenarioType, minSuccessRate);

            return patterns.stream()
                .map(pattern -> {
                    Map<String, Object> result = new HashMap<>(pattern);

                    try {
                        String configStr = (String) pattern.get("dimension_config");
                        Map<String, String> patternDims = objectMapper.readValue(configStr, Map.class);

                        double similarity = calculateSimilarity(currentDimensions, patternDims);
                        result.put("similarity", similarity);
                        result.put("matchingDimensions", findMatchingDimensions(currentDimensions, patternDims));
                    } catch (Exception e) {
                        log.error("解析模式配置失败", e);
                        result.put("similarity", 0.0);
                    }

                    return result;
                })
                .filter(p -> (double) p.get("similarity") > 0.3)
                .sorted((a, b) -> Double.compare((double) b.get("similarity"), (double) a.get("similarity")))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查找相似模式失败", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> suggestMissingDimensions(String scenarioType,
                                                                Map<String, String> currentDimensions,
                                                                List<Map<String, Object>> similarPatterns) {
        Map<String, Map<String, Object>> suggestionMap = new HashMap<>();

        for (Map<String, Object> pattern : similarPatterns) {
            try {
                String configStr = (String) pattern.get("dimension_config");
                Map<String, String> patternDims = objectMapper.readValue(configStr, Map.class);

                patternDims.forEach((dimCode, dimValue) -> {
                    if (!currentDimensions.containsKey(dimCode)) {
                        if (!suggestionMap.containsKey(dimCode)) {
                            Map<String, Object> suggestion = new HashMap<>();
                            suggestion.put("dimensionCode", dimCode);
                            suggestion.put("suggestedValue", dimValue);
                            suggestion.put("frequency", 1);
                            suggestion.put("avgSuccessRate", pattern.get("success_rate"));
                            suggestionMap.put(dimCode, suggestion);
                        } else {
                            Map<String, Object> existing = suggestionMap.get(dimCode);
                            existing.put("frequency", (int) existing.get("frequency") + 1);

                            double currentRate = ((BigDecimal) existing.get("avgSuccessRate")).doubleValue();
                            double newRate = ((BigDecimal) pattern.get("success_rate")).doubleValue();
                            double avgRate = (currentRate + newRate) / 2;
                            existing.put("avgSuccessRate", BigDecimal.valueOf(avgRate).setScale(4, RoundingMode.HALF_UP));
                        }
                    }
                });
            } catch (Exception e) {
                log.error("解析模式配置失败", e);
            }
        }

        return suggestionMap.values().stream()
            .sorted((a, b) -> {
                int freqCompare = Integer.compare((int) b.get("frequency"), (int) a.get("frequency"));
                if (freqCompare != 0) return freqCompare;
                return ((BigDecimal) b.get("avgSuccessRate")).compareTo((BigDecimal) a.get("avgSuccessRate"));
            })
            .limit(10)
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> findAvoidDimensions(String scenarioType,
                                                           Map<String, String> currentDimensions) {
        try {
            String sql = "SELECT dimension_code, dimension_value, " +
                        "COUNT(*) as fail_count, " +
                        "AVG(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_rate " +
                        "FROM biz_case_dimension cd " +
                        "JOIN biz_learning_case lc ON cd.case_id = lc.id " +
                        "WHERE lc.scenario_type = ? AND lc.success = 0 " +
                        "GROUP BY dimension_code, dimension_value " +
                        "HAVING fail_count >= 5 AND success_rate < 0.3 " +
                        "ORDER BY fail_count DESC, success_rate ASC " +
                        "LIMIT 10";

            List<Map<String, Object>> avoidList = jdbcTemplate.queryForList(sql, scenarioType);

            return avoidList.stream()
                .filter(item -> {
                    String dimCode = (String) item.get("dimension_code");
                    String dimValue = (String) item.get("dimension_value");
                    return currentDimensions.containsKey(dimCode) &&
                           currentDimensions.get(dimCode).equals(dimValue);
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查找避免维度失败", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> findOptimalCombinations(String scenarioType,
                                                               Map<String, String> currentDimensions) {
        try {
            String sql = "SELECT dc1.dimension_code as dim1_code, " +
                        "dc1.dimension_value as dim1_value, " +
                        "dc2.dimension_code as dim2_code, " +
                        "dc2.dimension_value as dim2_value, " +
                        "COUNT(*) as sample_count, " +
                        "AVG(CASE WHEN lc.success = 1 THEN 1 ELSE 0 END) as success_rate " +
                        "FROM biz_case_dimension dc1 " +
                        "JOIN biz_case_dimension dc2 ON dc1.case_id = dc2.case_id AND dc1.dimension_code < dc2.dimension_code " +
                        "JOIN biz_learning_case lc ON dc1.case_id = lc.id " +
                        "WHERE lc.scenario_type = ? AND lc.success = 1 " +
                        "GROUP BY dc1.dimension_code, dc1.dimension_value, dc2.dimension_code, dc2.dimension_value " +
                        "HAVING sample_count >= 10 AND success_rate >= 0.7 " +
                        "ORDER BY success_rate DESC, sample_count DESC " +
                        "LIMIT 5";

            return jdbcTemplate.queryForList(sql, scenarioType);
        } catch (Exception e) {
            log.error("查找最优组合失败", e);
            return new ArrayList<>();
        }
    }

    public Map<String, Object> predictSuccessRate(String scenarioType,
                                                   Map<String, String> dimensions) {
        Map<String, Object> prediction = new HashMap<>();

        List<Map<String, Object>> similarCases = findSimilarCases(scenarioType, dimensions);

        if (similarCases.isEmpty()) {
            prediction.put("successRate", 0.5);
            prediction.put("confidence", 0.0);
            prediction.put("message", "缺少足够历史数据，无法准确预测成功率");
            return prediction;
        }

        double totalWeight = 0.0;
        double weightedSuccess = 0.0;

        for (Map<String, Object> caseData : similarCases) {
            double similarity = (double) caseData.get("similarity");
            boolean success = (boolean) caseData.get("success");

            totalWeight += similarity;
            weightedSuccess += similarity * (success ? 1.0 : 0.0);
        }

        double predictedRate = totalWeight > 0 ? weightedSuccess / totalWeight : 0.5;
        double confidence = Math.min(totalWeight / 10.0, 1.0);

        prediction.put("successRate", BigDecimal.valueOf(predictedRate).setScale(4, RoundingMode.HALF_UP));
        prediction.put("confidence", BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP));
        prediction.put("sampleSize", similarCases.size());
        prediction.put("message", generatePredictionMessage(predictedRate, confidence, similarCases.size()));

        return prediction;
    }

    private List<Map<String, Object>> findSimilarCases(String scenarioType,
                                                        Map<String, String> dimensions) {
        try {
            String sql = "SELECT lc.*, " +
                        "(SELECT COUNT(*) FROM biz_case_dimension cd " +
                        " WHERE cd.case_id = lc.id AND cd.dimension_code IN (?) " +
                        " AND cd.dimension_value IN (?)) as match_count " +
                        "FROM biz_learning_case lc " +
                        "WHERE lc.scenario_type = ? " +
                        "ORDER BY match_count DESC " +
                        "LIMIT 20";

            List<Map<String, Object>> cases = jdbcTemplate.queryForList(sql,
                String.join(",", dimensions.keySet()),
                String.join(",", dimensions.values()),
                scenarioType);

            return cases.stream()
                .map(caseData -> {
                    Map<String, Object> result = new HashMap<>(caseData);
                    int matchCount = (int) caseData.get("match_count");
                    int totalDims = dimensions.size();
                    double similarity = totalDims > 0 ? (double) matchCount / totalDims : 0.0;
                    result.put("similarity", similarity);
                    return result;
                })
                .filter(c -> (double) c.get("similarity") > 0.3)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查找相似案例失败", e);
            return new ArrayList<>();
        }
    }

    private double calculateSimilarity(Map<String, String> dims1, Map<String, String> dims2) {
        if (dims1.isEmpty() || dims2.isEmpty()) return 0.0;

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(dims1.keySet());
        allKeys.addAll(dims2.keySet());

        int matchCount = 0;
        for (String key : allKeys) {
            if (dims1.containsKey(key) && dims2.containsKey(key)) {
                if (dims1.get(key).equals(dims2.get(key))) {
                    matchCount++;
                }
            }
        }

        return (double) matchCount / allKeys.size();
    }

    private List<String> findMatchingDimensions(Map<String, String> dims1, Map<String, String> dims2) {
        List<String> matching = new ArrayList<>();

        dims1.forEach((key, value) -> {
            if (dims2.containsKey(key) && dims2.get(key).equals(value)) {
                matching.add(key + "=" + value);
            }
        });

        return matching;
    }

    private double calculateConfidence(int sampleSize) {
        if (sampleSize == 0) return 0.0;
        if (sampleSize >= 10) return 0.9;
        if (sampleSize >= 5) return 0.7;
        if (sampleSize >= 2) return 0.5;
        return 0.3;
    }

    private String generateRecommendationText(List<Map<String, Object>> similarPatterns,
                                               List<Map<String, Object>> suggestedDimensions,
                                               List<Map<String, Object>> avoidDimensions) {
        StringBuilder sb = new StringBuilder();

        if (!similarPatterns.isEmpty()) {
            sb.append("发现").append(similarPatterns.size()).append(" 个相似的成功模式");

            Map<String, Object> bestPattern = similarPatterns.get(0);
            double similarity = (double) bestPattern.get("similarity");
            double successRate = ((BigDecimal) bestPattern.get("success_rate")).doubleValue();

            sb.append("，最高相似度 ").append((similarity * 100)).append("%");
            sb.append("，成功率 ").append((successRate * 100)).append("%");
        }

        if (!suggestedDimensions.isEmpty()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("建议新增 ").append(suggestedDimensions.size()).append(" 个维度");

            Map<String, Object> topSuggestion = suggestedDimensions.get(0);
            sb.append("，首选: ").append(topSuggestion.get("dimensionCode"));
            sb.append("=").append(topSuggestion.get("suggestedValue"));
        }

        if (!avoidDimensions.isEmpty()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("避免 ").append(avoidDimensions.size()).append(" 个低成功率维度组合");
        }

        if (sb.length() == 0) {
            sb.append("暂无足够数据支持维度推荐，建议先积累更多案例数据");
        }

        return sb.toString();
    }

    private String generatePredictionMessage(double successRate, double confidence, int sampleSize) {
        if (confidence < 0.3) {
            return "数据不足，预测结果仅供参考";
        }

        if (successRate >= 0.8) {
            return "该维度组合成功率很高，建议直接使用";
        } else if (successRate >= 0.6) {
            return "该维度组合成功率较高，可以尝试使用";
        } else if (successRate >= 0.4) {
            return "该维度组合成功率一般，建议优化后使用";
        } else {
            return "该维度组合成功率较低，建议调整维度组合";
        }
    }

    private String detectOpeningType(String title) {
        if (title == null) return "悬念";
        
        if (title.contains("?") || title.contains("？") || title.contains("揭秘") || title.contains("竟然")) {
            return "悬念";
        }
        if (title.contains("反转") || title.contains("没想到") || title.contains("结果")) {
            return "反转";
        }
        if (title.contains("冲突") || title.contains("争议") || title.contains("对立")) {
            return "冲突";
        }
        if (title.contains("惊喜") || title.contains("福利") || title.contains("免费")) {
            return "惊喜";
        }
        return "悬念";
    }

    private String detectMiddleStructure(String description) {
        if (description == null) return "并列";
        
        int length = description.length();
        if (length > 500) return "递进";
        if (description.contains("对比") || description.contains("vs") || description.contains("VS")) {
            return "对比";
        }
        if (description.contains("递进") || description.contains("逐步")) {
            return "递进";
        }
        return "并列";
    }

    private String detectEndingType(String description) {
        if (description == null) return "常规";
        
        if (description.contains("购买") || description.contains("下单") || description.contains("点击")) {
            return "CTA";
        }
        if (description.contains("反转结局") || description.contains("意外")) {
            return "反转";
        }
        if (description.contains("惊喜") || description.contains("彩蛋")) {
            return "惊喜";
        }
        return "常规";
    }

    private String detectNarrativeFrame(String title, String description) {
        String text = (title != null ? title : "") + " " + (description != null ? description : "");
        
        if (text.contains("故事") || text.contains("经历") || text.contains("讲述")) {
            return "故事叙述";
        }
        if (text.contains("对比") || text.contains("vs") || text.contains("区别")) {
            return "对比";
        }
        if (text.contains("递进") || text.contains("逐步") || text.contains("深入")) {
            return "递进";
        }
        if (text.contains("并列") || text.contains("同时") || text.contains("还有")) {
            return "并列";
        }
        return "故事叙述";
    }

    private String detectEmotionFrame(String text) {
        if (text == null) return "中性";
        
        if (text.contains("恐惧") || text.contains("可怕") || text.contains("危险")) {
            return "恐惧";
        }
        if (text.contains("好奇") || text.contains("揭秘") || text.contains("真相")) {
            return "好奇";
        }
        if (text.contains("愤怒") || text.contains("不公") || text.contains("气愤")) {
            return "愤怒";
        }
        if (text.contains("悲伤") || text.contains("感动") || text.contains("心酸")) {
            return "悲伤";
        }
        return "中性";
    }

    private String detectHookType(String title) {
        if (title == null) return "无";
        
        if (title.contains("反转") || title.contains("竟然") || title.contains("居然")) {
            return "悬念";
        }
        if (title.contains("冲突") || title.contains("争议") || title.contains("对立")) {
            return "冲突";
        }
        if (title.contains("数据") || title.contains("排名") || title.contains("对比")) {
            return "数据";
        }
        if (title.contains("惊喜") || title.contains("福利") || title.contains("免费")) {
            return "惊喜";
        }
        return "无";
    }

    private String detectHookPosition(String content) {
        if (content == null) return "开头";
        
        if (content.contains("开头") || content.contains("前3秒")) {
            return "开头";
        }
        if (content.contains("中间") || content.contains("中段")) {
            return "中间";
        }
        if (content.contains("结尾") || content.length() > 1000) {
            return "结尾";
        }
        return "开头";
    }

    private String detectHookMotivation(String title) {
        if (title == null) return "好奇";
        
        if (title.contains("?") || title.contains("？") || title.contains("揭秘")) {
            return "好奇";
        }
        if (title.contains("恐惧") || title.contains("危险")) {
            return "恐惧";
        }
        if (title.contains("争议") || title.contains("冲突")) {
            return "愤怒";
        }
        if (title.contains("感动") || title.contains("心酸")) {
            return "悲伤";
        }
        return "好奇";
    }

    private String detectTargetAudience(String tags, String description) {
        String text = (tags != null ? tags : "") + " " + (description != null ? description : "");
        
        if (text.contains("学生") || text.contains("校园")) {
            return "学生";
        }
        if (text.contains("白领")) {
            return "白领";
        }
        if (text.contains("宝妈") || text.contains("育儿")) {
            return "宝妈";
        }
        if (text.contains("男性")) {
            return "男性";
        }
        if (text.contains("老年") || text.contains("养生")) {
            return "老年";
        }
        return "宝妈";
    }

    private String detectPace(String content) {
        if (content == null) return "中";
        
        int length = content.length();
        if (length < 200) return "快";
        if (length > 800) return "慢";
        return "中";
    }

    private String detectEffectUsage(String content) {
        if (content == null) return "无";
        
        int effectCount = countKeywords(content, "特效", "转场", "滤镜", "动画");
        if (effectCount == 0) return "无";
        if (effectCount < 3) return "少";
        if (effectCount < 6) return "中";
        return "多";
    }

    private String detectTitleType(String title) {
        if (title == null) return "常规";
        
        if (title.contains("?") || title.contains("？")) {
            return "疑问";
        }
        if (title.matches(".*\\d+.*")) {
            return "数据";
        }
        if (title.contains("vs") || title.contains("VS") || title.contains("对比")) {
            return "对比";
        }
        if (title.contains("冲突") || title.contains("争议")) {
            return "冲突";
        }
        if (title.contains("惊喜") || title.contains("福利")) {
            return "惊喜";
        }
        return "常规";
    }

    private String detectBodyStructure(String content) {
        if (content == null) return "并列";
        
        if (content.contains("首先") || content.contains("其次") || content.contains("最后")) {
            return "递进";
        }
        if (content.contains("对比") || content.contains("vs")) {
            return "对比";
        }
        if (content.contains("总之") || content.contains("总而言之")) {
            return "总分";
        }
        return "并列";
    }

    private String detectCopyStyle(String content) {
        if (content == null) return "口语";
        
        int emotionalWords = countKeywords(content, "感动", "惊喜", "震撼", "心动");
        int rationalWords = countKeywords(content, "数据", "分析", "证明", "研究");
        
        if (emotionalWords > rationalWords) return "情感";
        if (content.contains("幽默") || content.contains("搞笑")) return "幽默";
        if (content.contains("正式") || content.contains("官方")) return "正式";
        return "口语";
    }

    private String detectEmotionTendency(String content) {
        if (content == null) return "中性";
        
        int positiveWords = countKeywords(content, "好", "棒", "优秀", "推荐", "喜欢");
        int negativeWords = countKeywords(content, "差", "烂", "失望", "糟糕", "后悔");
        
        if (positiveWords > negativeWords * 2) return "积极";
        if (negativeWords > positiveWords * 2) return "消极";
        if (positiveWords > 0 && negativeWords > 0) return "混合";
        return "中性";
    }

    private String detectLayoutStyle(String layout) {
        if (layout == null) return "默认";
        
        if (layout.contains("简约") || layout.contains("极简")) {
            return "简约";
        }
        if (layout.contains("复杂")) {
            return "复杂";
        }
        if (layout.contains("图文") || layout.contains("排版")) {
            return "图文";
        }
        return "默认";
    }

    private String detectImageStyle(String images) {
        if (images == null) return "默认";
        
        if (images.contains("实拍") || images.contains("照片")) {
            return "实拍";
        }
        if (images.contains("混合")) {
            return "混合";
        }
        if (images.contains("插画")) {
            return "插画";
        }
        return "默认";
    }

    private String detectCtaType(String content) {
        if (content == null) return "无";
        
        if (content.contains("点击") || content.contains("购买") || content.contains("下单")) {
            return "购买";
        }
        if (content.contains("关注") || content.contains("订阅")) {
            return "关注";
        }
        if (content.contains("分享") || content.contains("转发")) {
            return "分享";
        }
        if (content.contains("评论") || content.contains("留言")) {
            return "评论";
        }
        return "无";
    }

    private String detectQuestionType(String content) {
        if (content == null) return "无";
        
        if (content.contains("?") || content.contains("？")) {
            if (content.contains("怎么") || content.contains("如何") || content.contains("为什么")) {
                return "开放式";
            }
            return "封闭式";
        }
        if (content.contains("选择") || content.contains("A还是B")) {
            return "选择题";
        }
        return "无";
    }

    private String detectCategoryLevel(String category) {
        if (category == null) return "三级";
        
        int level = category.split("/").length;
        if (level == 1) return "一级";
        if (level == 2) return "二级";
        if (level == 3) return "三级";
        return "四级";
    }

    private String detectPriceStrategy(String price) {
        if (price == null) return "中价";
        
        try {
            double priceValue = Double.parseDouble(price.replaceAll("[^0-9.]", ""));
            if (priceValue < 50) return "低价";
            if (priceValue < 200) return "中价";
            if (priceValue < 1000) return "高价";
            return "奢华";
        } catch (Exception e) {
            return "中价";
        }
    }

    private String detectTargetAge(String audience, String description) {
        String text = (audience != null ? audience : "") + " " + (description != null ? description : "");
        
        if (text.contains("学生") || text.contains("年轻人")) {
            return "18-25";
        }
        if (text.contains("白领") || text.contains("职场")) {
            return "25-35";
        }
        if (text.contains("中年") || text.contains("家庭")) {
            return "35-50";
        }
        if (text.contains("老年") || text.contains("养生")) {
            return "50+";
        }
        return "25-35";
    }

    private String detectTargetGender(String audience, String description) {
        String text = (audience != null ? audience : "") + " " + (description != null ? description : "");
        
        if (text.contains("女性") || text.contains("女士") || text.contains("白领")) {
            return "女";
        }
        if (text.contains("男性") || text.contains("男士")) {
            return "男";
        }
        return "通用";
    }

    private String detectTargetRegion(String audience, String description) {
        String text = (audience != null ? audience : "") + " " + (description != null ? description : "");
        
        if (text.contains("一线城市") || text.contains("北京") || text.contains("上海") || text.contains("深圳")) {
            return "一线";
        }
        if (text.contains("二线城市") || text.contains("杭州") || text.contains("成都")) {
            return "二线";
        }
        if (text.contains("三线城市") || text.contains("三四线")) {
            return "三线";
        }
        if (text.contains("海外") || text.contains("出口")) {
            return "海外";
        }
        return "二线";
    }

    private String detectConsumption(String audience, String description) {
        String text = (audience != null ? audience : "") + " " + (description != null ? description : "");
        
        if (text.contains("高消费") || text.contains("高端") || text.contains("奢侈")) {
            return "超高";
        }
        if (text.contains("中等消费") || text.contains("中产")) {
            return "高";
        }
        if (text.contains("性价比") || text.contains("实惠")) {
            return "中";
        }
        if (text.contains("低价") || text.contains("便宜") || text.contains("经济")) {
            return "低";
        }
        return "中";
    }

    private String detectPromoFrequency(String description) {
        if (description == null) return "中";
        
        int promoCount = countKeywords(description, "促销", "折扣", "优惠", "活动", "特价");
        if (promoCount == 0) return "少";
        if (promoCount < 3) return "中";
        return "多";
    }

    private int countKeywords(String text, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }
        return count;
    }
}
