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
        cursor.execute("SELECT task_id, status, created_at FROM pod_illustration_task WHERE status = 0")
        tasks = cursor.fetchall()
        print(f"Found {len(tasks)} tasks with status 0.")
        for task in tasks:
            print(f"Task: {task['task_id']}, Status: {task['status']}, Created: {task['created_at']}")
finally:
    if 'conn' in locals():
        conn.close()
