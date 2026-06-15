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
        product_id = '1729450215760696320'
        print(f"--- Task Info for Product {product_id} ---")
        cursor.execute("SELECT * FROM pod_illustration_task WHERE product_id = %s ORDER BY created_at DESC", (product_id,))
        tasks = cursor.fetchall()
        if not tasks:
            print("No tasks found in pod_illustration_task.")
        for task in tasks:
            print(f"Task ID: {task.get('task_id')}, Status: {task.get('status')}, Created: {task.get('created_at')}, Updated: {task.get('updated_at')}, Error: {task.get('error_msg')}")
        
        print(f"\n--- Product Info for Product {product_id} ---")
        cursor.execute("SELECT product_id, illustration_status, illustration_result_url, illustration_extractable, illustration_extract_reason FROM pod_cross_category_product WHERE product_id = %s", (product_id,))
        products = cursor.fetchall()
        if not products:
            print("No products found in pod_cross_category_product.")
        for p in products:
            print(f"Product ID: {p.get('product_id')}, extractable: {p.get('illustration_extractable')}, status: {p.get('illustration_status')}, result: {p.get('illustration_result_url')}, reason: {p.get('illustration_extract_reason')}")

finally:
    if 'conn' in locals():
        conn.close()
