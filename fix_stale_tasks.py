import pymysql
import os
from dotenv import load_dotenv

load_dotenv('D:/choice_product/.env')

DB_CONFIG = {
    'host': os.getenv('DB_HOST', '192.168.0.168'),
    'port': int(os.getenv('DB_PORT', '3306')),
    'user': os.getenv('DB_USER', 'ITaimysql'),
    'password': os.getenv('DB_PASSWORD', 'Ai12345678@'),
    'database': os.getenv('DB_NAME', 'ecommerce_workflow'),
    'charset': 'utf8mb4',
    'cursorclass': pymysql.cursors.DictCursor,
}

try:
    conn = pymysql.connect(**DB_CONFIG)
    with conn.cursor() as cursor:
        cursor.execute("""
            UPDATE pod_illustration_task
            SET status = 3, error_msg = '图生图任务超时未完成，已由系统修复，可重新提取'
            WHERE status IN (0, 1)
        """)
        changed1 = cursor.rowcount
        cursor.execute("""
            UPDATE pod_cross_category_product p
            JOIN pod_illustration_task t ON t.product_id = p.product_id
            SET p.illustration_status = 3
            WHERE p.illustration_status IN (0, 1) AND t.status = 3
        """)
        changed2 = cursor.rowcount
        conn.commit()
        print(f"Fixed {changed1} tasks and {changed2} products.")
finally:
    if 'conn' in locals():
        conn.close()
