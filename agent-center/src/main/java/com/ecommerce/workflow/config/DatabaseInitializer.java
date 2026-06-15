package com.ecommerce.workflow.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化数据库表结构...");

        createTableIfNotExists();
        addDeletedColumnIfNotExists();
        fixMissingColumns();
        fixDimensionDefinitionTable();
        updateAiProviderPriority();
        initExpertConfigTables();

        log.info("数据库表结构初始化完成");
    }

    private void updateAiProviderPriority() {
        try {
            Integer minimaxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM unified_ai_config WHERE config_name LIKE '%Minimax%' AND deleted=0",
                    Integer.class);

            if (minimaxCount != null && minimaxCount > 0) {
                jdbcTemplate.update(
                        "UPDATE unified_ai_config SET priority = 1 WHERE config_name LIKE '%Minimax%' AND deleted=0");
                jdbcTemplate.update(
                        "UPDATE unified_ai_config SET priority = 2 WHERE config_name LIKE '%VectorEngine AI%' AND provider_type = 'LLM' AND deleted=0");
                jdbcTemplate.update(
                        "UPDATE unified_ai_config SET priority = 3 WHERE config_name LIKE '%VectorEngine VEO%' AND deleted=0");
                log.info("AI提供商优先级更新完成 (新架构: unified_ai_config)，Minimax设为最高优先级");
            } else {
                log.info("未找到Minimax配置，跳过优先级更新");
            }
        } catch (Exception e) {
            log.warn("更新AI提供商优先级失败 (可能是表不存在): {}", e.getMessage());
        }
    }

    private void fixMissingColumns() {
        fixCaseMemoryColumns();
        fixVideoTaskColumns();
        dropChatMessageForeignKey();
    }

    private void fixCaseMemoryColumns() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_case_memory' AND column_name = 'collect_count'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_case_memory ADD COLUMN collect_count DECIMAL(15,0) DEFAULT 0 COMMENT '收藏数量' AFTER comment_count");
                log.info("表 biz_case_memory 添加字段 collect_count 完成");
            }
        } catch (Exception e) {
            log.warn("尝试为表 biz_case_memory 添加字段 collect_count 失败: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                    "ALTER TABLE biz_case_memory MODIFY COLUMN input_params TEXT COMMENT '输入参数'");
            jdbcTemplate.execute(
                    "ALTER TABLE biz_case_memory MODIFY COLUMN output_result TEXT NULL COMMENT '输出结果'");
            log.info("表 biz_case_memory 修改字段注释完成");
        } catch (Exception e) {
            log.warn("修改表 biz_case_memory 字段注释失败: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_case_memory' AND column_name = 'learned'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_case_memory ADD COLUMN learned INT DEFAULT 0 COMMENT '是否已学习' AFTER vector_score");
                log.info("表 biz_case_memory 添加字段 learned 完成");
            }
        } catch (Exception e) {
            log.warn("尝试为表 biz_case_memory 添加字段 learned 失败: {}", e.getMessage());
        }
    }

    private void fixVideoTaskColumns() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'task_id'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN task_id VARCHAR(64) COMMENT '任务ID' AFTER id");
                log.info("为 biz_video_task 表添加 task_id 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 task_id 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'config_id'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN config_id VARCHAR(64) COMMENT '配置ID' AFTER task_id");
                log.info("为 biz_video_task 表添加 config_id 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 config_id 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'status'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN status VARCHAR(20) DEFAULT 'pending' COMMENT '状态' AFTER config_id");
                log.info("为 biz_video_task 表添加 status 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 status 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'progress'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN progress INT DEFAULT 0 COMMENT '进度' AFTER status");
                log.info("为 biz_video_task 表添加 progress 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 progress 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'image_urls'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN image_urls TEXT COMMENT '图像URL列表' AFTER progress");
                log.info("为 biz_video_task 表添加 image_urls 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 image_urls 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'video_url'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN video_url VARCHAR(500) COMMENT '视频URL' AFTER image_urls");
                log.info("为 biz_video_task 表添加 video_url 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 video_url 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'error_message'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN error_message TEXT COMMENT '错误信息' AFTER video_url");
                log.info("为 biz_video_task 表添加 error_message 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 error_message 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'creator_id'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN creator_id BIGINT COMMENT '创建者ID' AFTER error_message");
                log.info("为 biz_video_task 表添加 creator_id 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 creator_id 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'created_at'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER creator_id");
                log.info("为 biz_video_task 表添加 created_at 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 created_at 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'updated_at'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER created_at");
                log.info("为 biz_video_task 表添加 updated_at 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 updated_at 时出错: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_video_task' AND column_name = 'deleted'",
                    Integer.class);

            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE biz_video_task ADD COLUMN deleted INT DEFAULT 0 COMMENT '是否删除' AFTER updated_at");
                log.info("为 biz_video_task 表添加 deleted 列成功");
            }
        } catch (Exception e) {
            log.warn("为表 biz_video_task 添加列 deleted 时出错: {}", e.getMessage());
        }
    }

    private void createTableIfNotExists() {
        String[] createTableSqls = {
                "CREATE TABLE IF NOT EXISTS sys_chat_session (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "session_id VARCHAR(64) NOT NULL COMMENT '会话ID'," +
                        "user_id BIGINT NOT NULL COMMENT '用户ID'," +
                        "agent_type VARCHAR(50) COMMENT 'Agent类型'," +
                        "title VARCHAR(200) COMMENT '会话标题'," +
                        "summary TEXT COMMENT 'AI生成的会话摘要'," +
                        "status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/ARCHIVED/DELETED'," +
                        "message_count INT DEFAULT 0 COMMENT '消息数量'," +
                        "context JSON COMMENT '会话上下文信息'," +
                        "workflow_instance_id BIGINT COMMENT '关联的工作流实例ID'," +
                        "context_data JSON COMMENT '会话上下文数据'," +
                        "metadata JSON COMMENT '扩展元数据存储各种自定义信息'," +
                        "extracted_knowledge JSON COMMENT '从会话中提取的知识点ID列表'," +
                        "last_message_at DATETIME COMMENT '最后一条消息的时间'," +
                        "expires_at DATETIME COMMENT '过期时间'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "UNIQUE INDEX uk_session_id (session_id)," +
                        "INDEX idx_user_id (user_id)," +
                        "INDEX idx_agent_type (agent_type)," +
                        "INDEX idx_status (status)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表'",

                "CREATE TABLE IF NOT EXISTS sys_chat_message (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "message_id VARCHAR(64) NOT NULL COMMENT '消息ID'," +
                        "session_id VARCHAR(64) NOT NULL COMMENT '会话ID'," +
                        "role VARCHAR(20) NOT NULL COMMENT '角色 USER/ASSISTANT/SYSTEM'," +
                        "content TEXT NOT NULL COMMENT '内容'," +
                        "image_urls TEXT COMMENT '图像URL列表(JSON)'," +
                        "user_id BIGINT COMMENT '用户ID'," +
                        "attachments JSON COMMENT '附件'," +
                        "metadata JSON COMMENT '元数据'," +
                        "referenced_knowledge JSON COMMENT '引用的知识点'," +
                        "triggered_skills JSON COMMENT '触发的Skill'," +
                        "tokens_used INT DEFAULT 0 COMMENT '消耗的token数'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "UNIQUE INDEX uk_message_id (message_id)," +
                        "INDEX idx_session_id (session_id)," +
                        "INDEX idx_user_id (user_id)," +
                        "INDEX idx_role (role)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表'",

                "CREATE TABLE IF NOT EXISTS sys_knowledge (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "knowledge_id VARCHAR(64) NOT NULL COMMENT '知识点唯一标识'," +
                        "type VARCHAR(30) NOT NULL COMMENT '类型'," +
                        "title VARCHAR(200) NOT NULL COMMENT '标题'," +
                        "content TEXT NOT NULL COMMENT '内容'," +
                        "tags JSON COMMENT '标签'," +
                        "embedding_id VARCHAR(64) COMMENT '向量ID'," +
                        "source VARCHAR(100) COMMENT '来源'," +
                        "source_id VARCHAR(64) COMMENT '来源ID'," +
                        "confidence DOUBLE DEFAULT 0.0 COMMENT '置信度'," +
                        "apply_count INT DEFAULT 0 COMMENT '应用次数'," +
                        "success_count INT DEFAULT 0 COMMENT '成功次数'," +
                        "status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态'," +
                        "skill_ids JSON COMMENT '关联的SkillID列表'," +
                        "created_by BIGINT COMMENT '创建者'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "UNIQUE INDEX uk_knowledge_id (knowledge_id)," +
                        "INDEX idx_type (type)," +
                        "INDEX idx_status (status)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表'",

                "CREATE TABLE IF NOT EXISTS sys_user_profile (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "user_id BIGINT NOT NULL COMMENT '用户ID'," +
                        "preferences JSON COMMENT '用户偏好设置'," +
                        "interests JSON COMMENT '用户兴趣'," +
                        "behavior_patterns JSON COMMENT '行为模式'," +
                        "frequently_used_skills JSON COMMENT '常用Skill'," +
                        "topic_expertise JSON COMMENT '擅长主题'," +
                        "communication_style VARCHAR(50) COMMENT '沟通风格'," +
                        "total_sessions INT DEFAULT 0 COMMENT '总会话数'," +
                        "total_messages INT DEFAULT 0 COMMENT '总消息数'," +
                        "last_active_at DATETIME COMMENT '最后活跃时间'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "UNIQUE INDEX uk_user_id (user_id)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像表'",

                "CREATE TABLE IF NOT EXISTS biz_viral_pattern (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "name VARCHAR(200) NOT NULL COMMENT '规律名称'," +
                        "category VARCHAR(50) COMMENT '分类'," +
                        "description TEXT COMMENT '描述'," +
                        "key_factors TEXT COMMENT '关键要素(JSON格式)'," +
                        "success_rate DOUBLE DEFAULT 0.0 COMMENT '成功率'," +
                        "apply_count INT DEFAULT 0 COMMENT '应用次数'," +
                        "success_count INT DEFAULT 0 COMMENT '成功次数'," +
                        "tags JSON COMMENT '标签'," +
                        "examples TEXT COMMENT '示例说明'," +
                        "created_by INT COMMENT '创建者'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "INDEX idx_category (category)," +
                        "INDEX idx_success_rate (success_rate)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='爆款规律表'",

                "CREATE TABLE IF NOT EXISTS biz_avoidance_rule (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "title VARCHAR(200) NOT NULL COMMENT '规则标题'," +
                        "category VARCHAR(50) COMMENT '分类'," +
                        "description TEXT COMMENT '描述'," +
                        "problem_pattern TEXT COMMENT '问题模式'," +
                        "solution TEXT COMMENT '解决方案'," +
                        "prevention TEXT COMMENT '预防措施'," +
                        "severity INT DEFAULT 1 COMMENT '严重程度(1-5)'," +
                        "apply_count INT DEFAULT 0 COMMENT '应用次数'," +
                        "effective_count INT DEFAULT 0 COMMENT '有效次数'," +
                        "tags JSON COMMENT '标签'," +
                        "related_cases TEXT COMMENT '相关案例ID列表'," +
                        "created_by INT COMMENT '创建者'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "INDEX idx_category (category)," +
                        "INDEX idx_severity (severity)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='避坑规则表'",

                "CREATE TABLE IF NOT EXISTS filesystem_snapshot (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "snapshot_id VARCHAR(64) NOT NULL UNIQUE COMMENT '快照ID'," +
                        "user_id BIGINT COMMENT '用户ID'," +
                        "name VARCHAR(200) COMMENT '快照名称'," +
                        "description VARCHAR(500) COMMENT '描述'," +
                        "snapshot_path TEXT COMMENT '快照路径'," +
                        "file_count INT DEFAULT 0 COMMENT '文件数量'," +
                        "total_size BIGINT DEFAULT 0 COMMENT '总大小'," +
                        "status VARCHAR(20) DEFAULT 'active' COMMENT '状态'," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "INDEX idx_user_id (user_id)," +
                        "INDEX idx_status (status)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件系统快照表'",

                "CREATE TABLE IF NOT EXISTS compliance_rule (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "rule_type VARCHAR(50) NOT NULL COMMENT '规则类型'," +
                        "platform VARCHAR(50) DEFAULT 'all' COMMENT '平台(all/抖音/快手/小红书等)'," +
                        "title VARCHAR(200) COMMENT '规则标题'," +
                        "description TEXT COMMENT '规则描述'," +
                        "rule_content TEXT COMMENT '规则内容'," +
                        "priority INT DEFAULT 0 COMMENT '优先级(越大越优先)'," +
                        "enabled INT DEFAULT 1 COMMENT '是否启用'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "INDEX idx_rule_type (rule_type)," +
                        "INDEX idx_platform (platform)," +
                        "INDEX idx_enabled (enabled)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规规则表'",

                "CREATE TABLE IF NOT EXISTS expert_role_config (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码'," +
                        "role_name VARCHAR(100) NOT NULL COMMENT '角色名称'," +
                        "description TEXT COMMENT '角色描述'," +
                        "prompt_template TEXT COMMENT '提示词模板'," +
                        "analysis_dimensions TEXT COMMENT '分析维度(JSON数组)'," +
                        "weight DECIMAL(3,2) DEFAULT 1.00 COMMENT '权重'," +
                        "status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态'," +
                        "version INT DEFAULT 1 COMMENT '版本号'," +
                        "cache_version INT DEFAULT 1 COMMENT '缓存版本号'," +
                        "last_loaded_at DATETIME COMMENT '最后加载时间'," +
                        "is_hot_reload TINYINT(1) DEFAULT 1 COMMENT '是否支持热加载'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "INDEX idx_role_code (role_code)," +
                        "INDEX idx_status (status)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家角色配置表'",

                "CREATE TABLE IF NOT EXISTS video_prompt_template (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'," +
                        "template_code VARCHAR(100) NOT NULL UNIQUE COMMENT '模板编码'," +
                        "template_name VARCHAR(200) NOT NULL COMMENT '模板名称'," +
                        "json_schema TEXT COMMENT 'JSON Schema模板'," +
                        "rules TEXT COMMENT '规则列表'," +
                        "system_prefix TEXT COMMENT '系统前缀'," +
                        "category VARCHAR(50) COMMENT '分类(如:服装/食品/数码)'," +
                        "sub_category VARCHAR(50) COMMENT '子分类'," +
                        "tags JSON COMMENT '标签'," +
                        "description TEXT COMMENT '描述'," +
                        "version INT DEFAULT 1 COMMENT '版本号'," +
                        "success_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '成功率'," +
                        "usage_count INT DEFAULT 0 COMMENT '使用次数'," +
                        "avg_quality_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '平均质量评分'," +
                        "learned_from_cases INT DEFAULT 0 COMMENT '学习案例数'," +
                        "is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用'," +
                        "deleted INT DEFAULT 0 COMMENT '是否删除'," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "INDEX idx_category (category)," +
                        "INDEX idx_sub_category (sub_category)," +
                        "INDEX idx_is_active (is_active)," +
                        "INDEX idx_success_rate (success_rate)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频提示词模板表'",

                "CREATE TABLE IF NOT EXISTS ai_video_config (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID'," +
                        "config_name VARCHAR(100) COMMENT '配置名称'," +
                        "race VARCHAR(50) COMMENT '人种'," +
                        "role VARCHAR(50) COMMENT '角色类型'," +
                        "topic VARCHAR(100) COMMENT '热点话题'," +
                        "scene_type VARCHAR(50) COMMENT '场景类型'," +
                        "scene VARCHAR(50) COMMENT '具体场景'," +
                        "frame_type VARCHAR(50) COMMENT '视频框架类型'," +
                        "video_name VARCHAR(100) COMMENT '视频名称'," +
                        "aspect_ratio VARCHAR(20) COMMENT '视频比例'," +
                        "resolution VARCHAR(20) COMMENT '分辨率'," +
                        "frame_rate INT COMMENT '帧率'," +
                        "duration INT COMMENT '视频时长（秒）'," +
                        "style_intensity INT COMMENT '风格强度'," +
                        "creativity INT COMMENT '创意程度'," +
                        "creator_id BIGINT COMMENT '创建者 ID'," +
                        "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                        +
                        "deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'," +
                        "INDEX idx_creator (creator_id)," +
                        "INDEX idx_created (created_at)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 视频配置表'",

                "CREATE TABLE IF NOT EXISTS ai_model_capability (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                        "model_name VARCHAR(100) NOT NULL COMMENT '模型名称'," +
                        "capability_type VARCHAR(50) NOT NULL COMMENT '能力类型'," +
                        "score DECIMAL(5,2) DEFAULT 0 COMMENT '能力评分(0-100)'," +
                        "description TEXT COMMENT '能力描述'," +
                        "display_name VARCHAR(100) COMMENT '显示名称'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                        "PRIMARY KEY (id)," +
                        "UNIQUE KEY uk_model_capability (model_name, capability_type)," +
                        "KEY idx_capability_type (capability_type)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型能力表'"
        };

        for (String sql : createTableSqls) {
            try {
                jdbcTemplate.execute(sql);
                log.debug("成功创建表或表已存在");
            } catch (Exception e) {
                log.warn("创建表失败: {}", e.getMessage());
            }
        }
    }

    private void addDeletedColumnIfNotExists() {
        String[] tables = {
                "sys_chat_session", "sys_chat_message",
                "sys_knowledge", "sys_user_profile"
        };

        for (String table : tables) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'deleted'",
                        Integer.class, table);

                if (count != null && count == 0) {
                    jdbcTemplate.execute(
                            "ALTER TABLE " + table + " ADD COLUMN deleted INT DEFAULT 0 COMMENT '是否删除'");
                    log.info("为 {} 表添加 deleted 列成功", table);
                }
            } catch (Exception e) {
                log.warn("为表 {} 添加列 deleted 时出错: {}", table, e.getMessage());
            }
        }
    }

    private void dropChatMessageForeignKey() {
        try {
            Integer fkCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                            "WHERE table_schema = DATABASE() AND table_name = 'sys_chat_message' " +
                            "AND constraint_type = 'FOREIGN KEY'",
                    Integer.class);

            if (fkCount != null && fkCount > 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE sys_chat_message DROP FOREIGN KEY sys_chat_message_ibfk_1");
                log.info("成功删除 sys_chat_message 表的外键约束");
            }
        } catch (Exception e) {
            try {
                var fkNames = jdbcTemplate.queryForList(
                        "SELECT constraint_name FROM information_schema.table_constraints " +
                                "WHERE table_schema = DATABASE() AND table_name = 'sys_chat_message' " +
                                "AND constraint_type = 'FOREIGN KEY'",
                        String.class);
                for (String fkName : fkNames) {
                    jdbcTemplate.execute(
                            "ALTER TABLE sys_chat_message DROP FOREIGN KEY " + fkName);
                    log.info("成功删除 sys_chat_message 表的外键约束: {}", fkName);
                }
            } catch (Exception e2) {
                log.warn("删除 sys_chat_message 表的外键约束时出错: {}", e2.getMessage());
            }
        }
    }

    private void fixDimensionDefinitionTable() {
        try {
            // 检查 value_type 列类型是否需要扩展
            String currentType = jdbcTemplate.queryForObject(
                    "SELECT COLUMN_TYPE FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'biz_dimension_definition' " +
                            "AND column_name = 'value_type'",
                    String.class);

            if (currentType != null && currentType.contains("varchar(100)")) {
                // 将 value_type 列从 VARCHAR(100) 扩展到 VARCHAR(200)
                jdbcTemplate.execute(
                        "ALTER TABLE biz_dimension_definition " +
                                "MODIFY COLUMN value_type VARCHAR(200) " +
                                "COMMENT '值类型 enum:单选 multi:多选 number:数值 percentage:百分比 range:范围 time:时间'");
                log.info("为 biz_dimension_definition 表 value_type 列扩展长度到 VARCHAR(200)");
            }
        } catch (Exception e) {
            log.warn("修改 biz_dimension_definition 表 value_type 列类型时出错: {}", e.getMessage());
        }
    }

    private void initExpertConfigTables() {
        log.info("开始初始化专家配置表结构...");

        try {
            // 创建专家分析维度表
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS expert_analysis_dimension (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                            "role_code VARCHAR(50) NOT NULL COMMENT '关联的专家角色编码'," +
                            "dimension_code VARCHAR(50) NOT NULL COMMENT '维度编码'," +
                            "dimension_name VARCHAR(100) NOT NULL COMMENT '维度名称'," +
                            "dimension_description TEXT COMMENT '维度详细说明'," +
                            "sort_order INT DEFAULT 0 COMMENT '排序号'," +
                            "is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用'," +
                            "version INT DEFAULT 1 COMMENT '版本号'," +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                            +
                            "deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标记'," +
                            "PRIMARY KEY (id)," +
                            "UNIQUE KEY uk_role_dimension (role_code, dimension_code)," +
                            "KEY idx_role_code (role_code)," +
                            "KEY idx_sort_order (sort_order)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家分析维度表'");
            log.info("专家分析维度表创建完成");
        } catch (Exception e) {
            log.warn("创建专家分析维度表失败: {}", e.getMessage());
        }

        try {
            // 创建专家触发关键词表
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS expert_trigger_keyword (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                            "role_code VARCHAR(50) NOT NULL COMMENT '关联的专家角色编码'," +
                            "keyword VARCHAR(100) NOT NULL COMMENT '触发关键词'," +
                            "weight DECIMAL(3,2) DEFAULT 1.00 COMMENT '关键词权重(0.01-9.99)'," +
                            "match_count INT DEFAULT 0 COMMENT '匹配次数统计'," +
                            "last_matched_at DATETIME COMMENT '最后匹配时间'," +
                            "is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用'," +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                            +
                            "deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标记'," +
                            "PRIMARY KEY (id)," +
                            "UNIQUE KEY uk_role_keyword (role_code, keyword)," +
                            "KEY idx_role_code (role_code)," +
                            "KEY idx_keyword (keyword)," +
                            "KEY idx_weight (weight)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家触发关键词表'");
            log.info("专家触发关键词表创建完成");
        } catch (Exception e) {
            log.warn("创建专家触发关键词表失败: {}", e.getMessage());
        }

        // 检查并添加expert_role_config表的缺失列
        fixExpertRoleConfigColumns();

        try {
            // 检查是否已有专家角色数据
            Integer roleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM expert_role_config WHERE deleted = 0",
                    Integer.class);

            if (roleCount == null || roleCount == 0) {
                log.info("数据库中无专家角色数据，开始初始化默认数据...");
                initDefaultExpertRoles();
            } else {
                log.info("专家角色数据已存在，跳过初始化 (数量: {})", roleCount);
            }
        } catch (Exception e) {
            log.warn("检查专家角色数据失败: {}", e.getMessage());
        }
    }

    private void fixExpertRoleConfigColumns() {
        String[] columnsToAdd = {
                "cache_version INT DEFAULT 1 COMMENT '缓存版本号'",
                "last_loaded_at DATETIME COMMENT '最后加载时间'",
                "is_hot_reload TINYINT(1) DEFAULT 1 COMMENT '是否支持热加载'"
        };

        for (String columnDef : columnsToAdd) {
            String columnName = columnDef.split(" ")[0];
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = DATABASE() AND table_name = 'expert_role_config' AND column_name = ?",
                        Integer.class, columnName);

                if (count != null && count == 0) {
                    jdbcTemplate.execute(
                            "ALTER TABLE expert_role_config ADD COLUMN " + columnDef);
                    log.info("为 expert_role_config 表添加字段 {} 成功", columnName);
                }
            } catch (Exception e) {
                log.warn("为 expert_role_config 表添加字段 {} 时出错: {}", columnName, e.getMessage());
            }
        }
    }

    private void initDefaultExpertRoles() {
        String[] insertSqls = {
                // 产品经理
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('product_manager', '产品经理', '世界顶级的产品经理，专注于商品定位、卖点提炼、用户需求洞察', '', '[\"商品定位\",\"卖点提炼\",\"竞品分析\",\"用户需求\",\"市场趋势\"]', 0.95, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('product_manager', 'product_positioning', '商品定位', '分析商品在市场中的定位是否清晰，目标用户群体是否明确', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('product_manager', 'selling_point', '卖点提炼', '提炼商品的核心卖点和差异化优势', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('product_manager', 'competitor_analysis', '竞品分析', '分析竞品的优劣势，找出差异化竞争策略', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('product_manager', 'user_need', '用户需求', '深度洞察用户的真实需求和痛点', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('product_manager', 'market_trend', '市场趋势', '分析市场发展趋势，预测未来机会', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '产品', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '商品', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '卖点', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '定位', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '竞品', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '用户需求', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '市场', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '功能', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '价格', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('product_manager', '性价比', 0.85, 1)",

                // 视觉设计师
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('visual_designer', '视觉设计师', '世界顶级的视觉设计师，专注于色彩搭配、构图美学、视觉冲击力', '', '[\"色彩搭配\",\"构图美学\",\"视觉冲击力\",\"品牌调性\",\"排版设计\"]', 0.90, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('visual_designer', 'color_scheme', '色彩搭配', '分析视频的色彩搭配是否和谐，是否符合品牌调性', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('visual_designer', 'composition', '构图美学', '评估画面构图是否美观，视觉焦点是否突出', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('visual_designer', 'visual_impact', '视觉冲击力', '分析视频的视觉冲击力和吸引力', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('visual_designer', 'brand_tone', '品牌调性', '评估视频是否符合品牌整体调性', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('visual_designer', 'typography', '排版设计', '分析文字排版是否美观易读', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '色彩', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '设计', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '视觉', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '构图', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '美学', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '排版', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '品牌', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '画面', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '风格', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('visual_designer', '美感', 0.90, 1)",

                // 视频剪辑师
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('video_editor', '视频剪辑师', '世界顶级的视频剪辑师，专注于节奏把控、转场设计、音画同步', '', '[\"节奏把控\",\"转场设计\",\"音画同步\",\"镜头语言\",\"剪辑技巧\"]', 0.92, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('video_editor', 'rhythm', '节奏把控', '分析视频的节奏是否流畅，快慢是否合理', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('video_editor', 'transition', '转场设计', '评估转场效果是否自然流畅', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('video_editor', 'audio_visual_sync', '音画同步', '分析音频和视频是否同步协调', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('video_editor', 'camera_language', '镜头语言', '评估镜头运用是否专业到位', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('video_editor', 'editing_skill', '剪辑技巧', '分析剪辑技巧是否娴熟', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '剪辑', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '节奏', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '转场', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '镜头', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '音画', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '视频', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '流畅', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '特效', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '配乐', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('video_editor', '画面', 0.75, 1)",

                // 图片分析师
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('image_analyst', '图片分析师', '世界顶级的图片分析师，专注于图像内容理解、视觉元素提取、情感分析', '', '[\"图像内容理解\",\"视觉元素提取\",\"情感分析\",\"构图分析\",\"色彩分析\"]', 0.88, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('image_analyst', 'image_content', '图像内容理解', '深度理解图像中的内容和场景', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('image_analyst', 'visual_elements', '视觉元素提取', '提取图像中的关键视觉元素', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('image_analyst', 'emotion_analysis', '情感分析', '分析图像传达的情感倾向', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('image_analyst', 'composition_analysis', '构图分析', '分析图像的构图方式', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('image_analyst', 'color_analysis', '色彩分析', '分析图像的色彩特征', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '图片', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '图像', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '视觉', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '元素', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '情感', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '构图', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '色彩', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '场景', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '内容', 0.65, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('image_analyst', '细节', 0.80, 1)",

                // 运营专家
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('operations_expert', '运营专家', '世界顶级的运营专家，专注于用户增长、转化优化、数据分析', '', '[\"用户增长\",\"转化优化\",\"数据分析\",\"运营策略\",\"用户留存\"]', 0.87, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('operations_expert', 'user_growth', '用户增长', '分析用户增长策略和效果', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('operations_expert', 'conversion_optimization', '转化优化', '优化转化路径和转化率', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('operations_expert', 'data_analysis', '数据分析', '深度分析运营数据', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('operations_expert', 'operation_strategy', '运营策略', '制定和优化运营策略', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('operations_expert', 'user_retention', '用户留存', '分析用户留存和活跃度', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '运营', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '用户', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '增长', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '转化', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '数据', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '留存', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '活跃', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '策略', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '推广', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('operations_expert', '营销', 0.75, 1)",

                // 数据分析师
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('data_analyst', '数据分析师', '世界顶级的数据分析师，专注于数据挖掘、趋势预测、指标分析', '', '[\"数据挖掘\",\"趋势预测\",\"指标分析\",\"数据可视化\",\"统计分析\"]', 0.85, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('data_analyst', 'data_mining', '数据挖掘', '深度挖掘数据中的价值和规律', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('data_analyst', 'trend_prediction', '趋势预测', '基于数据预测未来趋势', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('data_analyst', 'metric_analysis', '指标分析', '分析关键业务指标', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('data_analyst', 'data_visualization', '数据可视化', '将数据转化为直观的可视化图表', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('data_analyst', 'statistical_analysis', '统计分析', '运用统计学方法分析数据', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '数据', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '分析', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '趋势', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '指标', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '统计', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '预测', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '挖掘', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '可视化', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '规律', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('data_analyst', '对比', 0.65, 1)",

                // 文案策划师
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('copywriter', '文案策划师', '世界顶级的文案策划师，专注于文案创作、标题优化、情感共鸣', '', '[\"文案创作\",\"标题优化\",\"情感共鸣\",\"卖点表达\",\"语言风格\"]', 0.93, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('copywriter', 'copywriting', '文案创作', '创作吸引人的文案内容', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('copywriter', 'title_optimization', '标题优化', '优化标题提高点击率', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('copywriter', 'emotional_resonance', '情感共鸣', '创造与用户的情感共鸣', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('copywriter', 'selling_point_expression', '卖点表达', '清晰有效地表达产品卖点', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('copywriter', 'language_style', '语言风格', '选择合适的语言风格', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '文案', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '标题', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '策划', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '创作', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '情感', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '表达', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '语言', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '吸引力', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '卖点', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('copywriter', '点击率', 0.80, 1)",

                // 编导
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('director', '编导', '世界顶级的编导，专注于叙事结构、镜头调度、节奏控制', '', '[\"叙事结构\",\"镜头调度\",\"节奏控制\",\"故事线设计\",\"场景安排\"]', 0.91, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('director', 'narrative_structure', '叙事结构', '设计合理的叙事结构和故事线', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('director', 'camera_direction', '镜头调度', '合理安排镜头运动和视角', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('director', 'rhythm_control', '节奏控制', '控制视频的整体节奏', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('director', 'storyline_design', '故事线设计', '设计引人入胜的故事线', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('director', 'scene_arrangement', '场景安排', '合理安排场景顺序和过渡', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '编导', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '叙事', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '镜头', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '节奏', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '故事', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '场景', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '调度', 0.70, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '结构', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '剧情', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('director', '视角', 0.75, 1)",

                // 营销策划
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('marketing_planner', '营销策划', '世界顶级的营销策划，专注于营销策略、品牌推广、爆款打造', '', '[\"营销策略\",\"品牌推广\",\"爆款打造\",\"市场定位\",\"传播策略\"]', 0.89, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('marketing_planner', 'marketing_strategy', '营销策略', '制定有效的营销策略', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('marketing_planner', 'brand_promotion', '品牌推广', '设计品牌推广方案', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('marketing_planner', 'viral_creation', '爆款打造', '创造爆款内容和传播点', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('marketing_planner', 'market_positioning', '市场定位', '明确产品市场定位', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('marketing_planner', 'communication_strategy', '传播策略', '制定有效的传播策略', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '营销', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '策划', 0.95, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '品牌', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '爆款', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '推广', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '传播', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '定位', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '策略', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '市场', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('marketing_planner', '热点', 0.75, 1)",

                // 提示词工程师
                "INSERT IGNORE INTO expert_role_config (role_code, role_name, description, prompt_template, analysis_dimensions, weight, status, version, cache_version, is_hot_reload) VALUES "
                        +
                        "('prompt_engineer', '提示词工程师', '世界顶级的提示词工程师，专注于提示词优化、模型调优、输出质量控制', '', '[\"提示词优化\",\"模型调优\",\"输出质量控制\",\"模板设计\",\"效果评估\"]', 0.94, 'ACTIVE', 1, 1, 1)",

                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('prompt_engineer', 'prompt_optimization', '提示词优化', '优化提示词提高AI输出质量', 1, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('prompt_engineer', 'model_tuning', '模型调优', '调整模型参数获得最佳效果', 2, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('prompt_engineer', 'output_quality', '输出质量控制', '控制和评估AI输出质量', 3, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('prompt_engineer', 'template_design', '模板设计', '设计高效的提示词模板', 4, 1)",
                "INSERT IGNORE INTO expert_analysis_dimension (role_code, dimension_code, dimension_name, dimension_description, sort_order, is_active) VALUES "
                        +
                        "('prompt_engineer', 'effectiveness_evaluation', '效果评估', '评估提示词效果并持续优化', 5, 1)",

                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '提示词', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', 'prompt', 1.00, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '优化', 0.90, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '模板', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '模型', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '输出', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '质量', 0.85, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '调优', 0.80, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '效果', 0.75, 1)",
                "INSERT IGNORE INTO expert_trigger_keyword (role_code, keyword, weight, is_active) VALUES " +
                        "('prompt_engineer', '评估', 0.70, 1)"
        };

        for (String sql : insertSqls) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("执行SQL失败: {}", e.getMessage());
            }
        }

        log.info("默认专家角色数据初始化完成");
    }
}
