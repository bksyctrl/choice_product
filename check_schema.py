import pymysql
import os
import json
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

def check_schema():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            tables = ['fastmoss_product_aggregate', 'fastmoss_product_rank_aggregate', 'kalodata_youwei_product']
            for t in tables:
                print(f"--- {t} ---")
                cursor.execute(f"DESCRIBE {t}")
                for row in cursor.fetchall():
                    print(f"{row['Field']}: {row['Type']}")
                print("\n")
    finally:
        conn.close()

if __name__ == "__main__":
    check_schema()
