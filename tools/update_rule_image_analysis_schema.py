import pymysql


DB_CONFIG = {
    "host": "192.168.0.168",
    "port": 3306,
    "user": "ITaimysql",
    "password": "Ai12345678@",
    "database": "ecommerce_workflow",
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}


COLUMNS = [
    ("rule_type", "varchar(20) DEFAULT NULL COMMENT 'IP=IP规则图片识别, MATERIAL=工厂材质规则图片识别'"),
    ("image_name", "varchar(255) DEFAULT NULL COMMENT '图片名称/抽取后的文件名'"),
    ("image_path", "varchar(1000) DEFAULT NULL COMMENT '图片本地路径'"),
    ("image_sha256", "char(64) DEFAULT NULL COMMENT '图片SHA256'"),
    ("ocr_confidence", "decimal(5,2) DEFAULT NULL COMMENT 'OCR置信度'"),
    ("logo_names", "longtext DEFAULT NULL COMMENT '识别到的Logo/品牌名称JSON数组'"),
    ("brand_clues", "longtext DEFAULT NULL COMMENT '品牌相关线索JSON'"),
    ("ip_names", "longtext DEFAULT NULL COMMENT '疑似IP/角色/品牌名称JSON数组'"),
    ("ip_risk_grade", "varchar(10) DEFAULT NULL COMMENT 'IP风险等级S/A/B/C/D/E'"),
    ("ip_type", "varchar(100) DEFAULT NULL COMMENT 'IP类型, 如奢侈品大牌/美国本地IP/动漫IP/潮牌/擦边插画'"),
    ("ip_evidence", "longtext DEFAULT NULL COMMENT 'IP判断证据JSON'"),
    ("material_type", "varchar(100) DEFAULT NULL COMMENT '材质大类, 如工厂材质/非工厂材质/疑似材质'"),
    ("material_category", "varchar(100) DEFAULT NULL COMMENT '材质细分类, 如TPU/硅胶/亚克力/金属等'"),
    ("material_evidence", "longtext DEFAULT NULL COMMENT '材质判断证据JSON'"),
    ("style_clues", "longtext DEFAULT NULL COMMENT '风格、颜色、形状、图案等视觉线索JSON'"),
    ("confidence", "decimal(5,2) DEFAULT NULL COMMENT '整体识别置信度'"),
    ("needs_human_review", "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否需要人工复核'"),
]


def main():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            cursor.execute("SHOW COLUMNS FROM `ai_rule_image_analysis`")
            existing = {row["Field"] for row in cursor.fetchall()}
            for column_name, ddl in COLUMNS:
                if column_name not in existing:
                    cursor.execute(
                        f"ALTER TABLE `ai_rule_image_analysis` ADD COLUMN `{column_name}` {ddl}"
                    )
            conn.commit()
            print("ai_rule_image_analysis schema updated")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
