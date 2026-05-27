import pymysql
import os
from dotenv import load_dotenv

load_dotenv()

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "192.168.0.168"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "ITaimysql"),
    "password": os.getenv("DB_PASSWORD", "Ai12345678@"),
    "database": os.getenv("DB_NAME", "ecommerce_workflow"),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}

def get_samples():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            # unified (fastmoss_product_aggregate)
            print("--- Sample for unified ---")
            cursor.execute("SELECT real_price, rating, commission_rate, author_count, rank_sold_count, distribution_7d FROM fastmoss_product_aggregate LIMIT 1")
            print(cursor.fetchone())
            
            # kalodata
            print("\n--- Sample for kalodata ---")
            cursor.execute("SELECT 价格, 商品评分, 佣金比例, 关联达人数, 总销量, `近28天视频占比`, `近28天商品卡占比` FROM kalodata_youwei_product LIMIT 1")
            print(cursor.fetchone())
    finally:
        conn.close()

if __name__ == "__main__":
    get_samples()
