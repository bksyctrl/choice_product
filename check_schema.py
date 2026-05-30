import os
from dotenv import load_dotenv
import pymysql
import json

load_dotenv()
conn = pymysql.connect(
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=int(os.getenv("DB_PORT", 3306)),
    user=os.getenv("DB_USER", "root"),
    password=os.getenv("DB_PASSWORD", ""),
    database=os.getenv("DB_NAME", "choice_product"),
    cursorclass=pymysql.cursors.DictCursor
)

with conn.cursor() as cursor:
    cursor.execute("SELECT overview_7d FROM cp_kalodata WHERE overview_7d IS NOT NULL LIMIT 1")
    row = cursor.fetchone()
    print("Kalodata:", row['overview_7d'] if row else "None")
    
    cursor.execute("SELECT sales_overview FROM cp_fastmoss WHERE sales_overview IS NOT NULL LIMIT 1")
    row = cursor.fetchone()
    print("Fastmoss:", row['sales_overview'] if row else "None")
