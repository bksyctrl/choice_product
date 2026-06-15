package com.ecommerce.workflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.service.evolution.SkillConfigService;

@Component
@Order(2)
public class EvolutionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvolutionInitializer.class);
    private final SkillConfigService skillConfigService;
    private final JdbcTemplate jdbcTemplate;

    public EvolutionInitializer(SkillConfigService skillConfigService, JdbcTemplate jdbcTemplate) {
        this.skillConfigService = skillConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureTablesExist();
            log.info("开始初始化Skill配置默认数据...");
            skillConfigService.initDefaultSkills();
            log.info("Skill配置默认数据初始化完成");
        } catch (Exception e) {
            log.error("初始化过程发生异常", e);
        }
    }

    private void ensureTablesExist() {
        String[] tables = {
                "sys_skill_config",
                "biz_case_memory",
                "biz_delivery_data",
                "sys_skill_evolution_log"
        };

        for (String table : tables) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '" + table + "'",
                        Integer.class);
                if (count != null && count > 0) continue;
            } catch (Exception e) {
                log.debug("检查表{}是否存在时出错，将尝试创建", table);
            }

            switch (table) {
                case "sys_skill_config" -> createSkillConfigTable();
                case "biz_case_memory" -> createCaseMemoryTable();
                case "biz_delivery_data" -> createDeliveryDataTable();
                case "sys_skill_evolution_log" -> createEvolutionLogTable();
            }
            log.info("表{}创建成功", table);
        }
    }

    private void createSkillConfigTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_skill_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    skill_code VARCHAR(50) NOT NULL COMMENT '技能编码',
                    skill_name VARCHAR(100) NOT NULL COMMENT '技能名称',
                    skill_category VARCHAR(50) NOT NULL COMMENT '分类: selection/content_generation/video_production/compliance/attribution',
                    version VARCHAR(20) NOT NULL DEFAULT '1.0' COMMENT '版本',
                    config_params JSON COMMENT '配置参数(JSON)',
                    success_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '成功率(%)',
                    usage_count INT DEFAULT 0 COMMENT '使用次数',
                    success_count INT DEFAULT 0 COMMENT '成功次数',
                    fail_count INT DEFAULT 0 COMMENT '失败次数',
                    avg_cvr DOUBLE DEFAULT 0.0 COMMENT '平均转化率',
                    avg_gmv DECIMAL(12,2) DEFAULT 0.00 COMMENT '平均GMV',
                    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/SUPERSEDED/ROLLED_BACK',
                    previous_version VARCHAR(20) COMMENT '上一版本号',
                    upgrade_reason TEXT COMMENT '升级原因',
                    created_by BIGINT,
                    updated_by BIGINT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0,
                    UNIQUE INDEX uk_skill_code_version (skill_code, version),
                    INDEX idx_category (skill_category),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill配置表'
                """);
    }

    private void createCaseMemoryTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS biz_case_memory (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    case_no VARCHAR(64) NOT NULL UNIQUE COMMENT '案例编号',
                    case_type VARCHAR(20) NOT NULL COMMENT '类型: success/fail/ab_test',
                    product_id BIGINT COMMENT '产品ID',
                    product_name VARCHAR(300) COMMENT '产品名称',
                    category VARCHAR(100) COMMENT '品类',
                    platform VARCHAR(50) COMMENT '平台',
                    workflow_instance_id BIGINT COMMENT '工作流实例ID',
                    video_task_id BIGINT COMMENT '视频任务ID',
                    skill_version_snapshot VARCHAR(100) COMMENT '使用Skill版本快照',
                    input_params TEXT COMMENT '输入参数',
                    output_result TEXT COMMENT '输出结果',
                    play_count DECIMAL(15,0) COMMENT '播放量',
                    like_count DECIMAL(15,0) COMMENT '点赞数',
                    share_count DECIMAL(15,0) COMMENT '分享数',
                    comment_count DECIMAL(15,0) COMMENT '评论数',
                    collect_count DECIMAL(15,0) COMMENT '收藏数',
                    gmv DECIMAL(12,2) COMMENT 'GMV',
                    cvr DOUBLE COMMENT '转化率',
                    conversion_count INT DEFAULT 0 COMMENT '转化数',
                    quality_tag VARCHAR(20) COMMENT '质量标签: success/fail/excellent/poor',
                    failure_reason TEXT COMMENT '失败原因',
                    lesson_learned TEXT COMMENT '经验教训',
                    vector_score DOUBLE COMMENT '向量匹配分数',
                    learned INT DEFAULT 0 COMMENT '是否已学习',
                    created_by BIGINT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0,
                    INDEX idx_case_type (case_type),
                    INDEX idx_quality_tag (quality_tag),
                    INDEX idx_product_id (product_id),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案例记忆表'
                """);
    }

    private void createDeliveryDataTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS biz_delivery_data (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    task_no VARCHAR(64) NOT NULL COMMENT '任务编号',
                    video_task_id BIGINT COMMENT '视频任务ID',
                    platform VARCHAR(50) COMMENT '平台',
                    platform_video_id VARCHAR(200) COMMENT '平台视频ID',
                    play_count DECIMAL(15,0) COMMENT '播放量',
                    like_count DECIMAL(15,0) COMMENT '点赞数',
                    comment_count DECIMAL(15,0) COMMENT '评论数',
                    share_count DECIMAL(15,0) COMMENT '分享数',
                    collect_count DECIMAL(15,0) COMMENT '收藏数',
                    follow_count DECIMAL(15,0) COMMENT '关注数',
                    gmv DECIMAL(12,2) COMMENT 'GMV',
                    order_count INT DEFAULT 0 COMMENT '订单数',
                    cvr DOUBLE COMMENT '转化率',
                    video_duration INT COMMENT '视频时长(秒)',
                    publish_time VARCHAR(30) COMMENT '发布时间',
                    data_date DATE COMMENT '数据日期',
                    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
                    raw_data_json JSON COMMENT '原始数据流',
                    case_memory_id BIGINT COMMENT '关联案例ID',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0,
                    INDEX idx_task_no (task_no),
                    INDEX idx_video_task_id (video_task_id),
                    INDEX idx_platform (platform),
                    INDEX idx_data_date (data_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投递数据表'
                """);
    }

    private void createEvolutionLogTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_skill_evolution_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    skill_code VARCHAR(50) NOT NULL COMMENT '技能编码',
                    from_version VARCHAR(20) COMMENT '原版本',
                    to_version VARCHAR(20) COMMENT '新版本',
                    evolution_type VARCHAR(30) COMMENT '类型: AUTO_OPTIMIZE/MANUAL/AB_WINNER/ROLLBACK',
                    param_key VARCHAR(100) COMMENT '参数名',
                    from_value VARCHAR(500) COMMENT '原值',
                    to_value VARCHAR(500) COMMENT '新值',
                    change_reason TEXT COMMENT '变更原因',
                    evidence_data JSON COMMENT '证据数据',
                    metric_before DOUBLE COMMENT '变更前指标',
                    metric_after DOUBLE COMMENT '变更后指标',
                    improvement_pct DOUBLE COMMENT '提升百分比',
                    status VARCHAR(20) DEFAULT 'PENDING_VALIDATION' COMMENT '状态',
                    is_rolled_back TINYINT DEFAULT 0 COMMENT '是否已回滚',
                    rollback_reason TEXT COMMENT '回滚原因',
                    triggered_by BIGINT COMMENT '触发者',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_skill_code (skill_code),
                    INDEX idx_evolution_type (evolution_type),
                    INDEX idx_status (status),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill演进日志表'
                """);
    }
}
