-- 选品策略表
CREATE TABLE IF NOT EXISTS product_strategies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '策略名称',
    description TEXT COMMENT '策略描述',
    keywords TEXT COMMENT '关键词列表，JSON 格式',
    content TEXT COMMENT '策略文本内容',
    file_path VARCHAR(500) COMMENT '上传文件路径',
    file_type VARCHAR(50) COMMENT '文件类型：word/excel/text',
    match_fields JSON COMMENT '匹配字段：["title", "category_l1", "category_l2", "category_l3"]',
    data_sources JSON COMMENT '数据源表名 JSON 数组：["fastmoss_product","fastmoss_sales_product"]',
    weight_config JSON COMMENT '权重配置：{"title": 0.5, "category": 0.3, "price": 0.2}',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选品策略表';

-- 选品结果缓存表
CREATE TABLE IF NOT EXISTS product_ranking_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    match_score DECIMAL(10,4) COMMENT '匹配度分数 0-100',
    rank_position INT COMMENT '排名位置',
    product_data JSON COMMENT '产品完整数据快照',
    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_strategy (strategy_id),
    INDEX idx_product (product_id),
    INDEX idx_score (strategy_id, match_score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选品排名缓存表';
