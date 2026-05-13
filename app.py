# -*- coding: utf-8 -*-
"""
智能选品系统 - 核心决策引擎
功能：动态 SQL 编译、多维度加权排序、生命周期引擎
"""
import os
import re
import json
import uuid
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
import pymysql
from docx import Document
import openpyxl

app = Flask(__name__, static_folder='static', static_url_path='')
CORS(app)

DB_CONFIG = {
    'host': '192.168.0.168',
    'port': 3306,
    'user': 'ITaimysql',
    'password': 'Ai12345678@',
    'database': 'ecommerce_workflow',
    'charset': 'utf8mb4'
}

# 数据源白名单：仅这两张表字段完整兼容引擎 schema
DATA_SOURCE_REGISTRY = {
    'fastmoss_product':       {'stage': 'NEW_ARRIVAL', 'weight': 1.2, 'label': '商品热推榜'},
    'fastmoss_sales_product': {'stage': 'MATURITY',    'weight': 1.0, 'label': '商品热销榜'},
}

UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), 'uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def get_db_connection():
    return pymysql.connect(**DB_CONFIG)

def ensure_schema():
    """启动时自动补齐 data_sources 列，幂等可重复执行。"""
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA=%s AND TABLE_NAME='product_strategies' AND COLUMN_NAME='data_sources'
            """, (DB_CONFIG['database'],))
            if cur.fetchone()[0] == 0:
                cur.execute(
                    "ALTER TABLE product_strategies "
                    "ADD COLUMN data_sources JSON COMMENT '数据源表名 JSON 数组' AFTER match_fields"
                )
                conn.commit()
                print("[schema] product_strategies.data_sources 列已补齐")
    finally:
        conn.close()

def filter_data_sources(raw):
    """白名单过滤；空/全部非法时回退到全量。"""
    valid = [t for t in (raw or []) if t in DATA_SOURCE_REGISTRY]
    return valid if valid else list(DATA_SOURCE_REGISTRY.keys())

def decimal_to_float(obj):
    from decimal import Decimal
    return float(obj) if isinstance(obj, Decimal) else obj

def extract_text_from_file(file_path, file_type):
    if file_type == 'word':
        doc = Document(file_path)
        return '\n'.join([p.text for p in doc.paragraphs if p.text.strip()])
    elif file_type == 'excel':
        wb = openpyxl.load_workbook(file_path)
        texts = []
        for sheet in wb.worksheets:
            for row in sheet.iter_rows(values_only=True):
                texts.extend([str(cell) for cell in row if cell])
        return '\n'.join(texts)
    else: 
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read()

def extract_keywords(text):
    text = re.sub(r'[^\w\s一-鿿,.]', ' ', text)
    parts = re.split(r'[,\n。]', text)
    keywords = [p.strip() for p in parts if 2 <= len(p.strip()) <= 20]
    return list(set(keywords))[:50]

def parse_strategy_keywords(keywords_list):
    text_keywords = []
    conditions = []
    numeric_fields = {
        'sold_count': ['销量', '销售数量', '已售', '成交数量'],
        'sale_amount': ['销售额', '成交金额', 'GMV'],
        'base_price': ['价格', '原价', '标价'],
        'real_price': ['实价', '实际价格', '售价'],
        'rating': ['评分', '星级', '分数'],
        'review_count': ['评论数', '评价数量', '评价']
    }
    operators = {
        '大于': '>', '超过': '>', '多于': '>', '以上': '>=', '不低于': '>=',
        '小于': '<', '低于': '<', '少于': '<', '不超过': '<=', '不高于': '<=',
        '等于': '=', '是': '=', '为': '='
    }

    if not keywords_list: return text_keywords, conditions

    for kw in keywords_list:
        if not kw: continue
        kw_str = str(kw).strip()
        parsed = False

        for field_en, field_cn_list in numeric_fields.items():
            for field_cn in field_cn_list:
                if field_cn in kw_str:
                    for op_cn, op_sym in operators.items():
                        if op_cn in kw_str:
                            parts = kw_str.split(op_cn)
                            if len(parts) == 2:
                                try:
                                    value_str = parts[1].strip().lower()
                                    if '万' in value_str or value_str.endswith('w'):
                                        value = float(re.sub(r'[万 w]', '', value_str)) * 10000
                                    elif '千' in value_str or value_str.endswith('k'):
                                        value = float(re.sub(r'[千 k]', '', value_str)) * 1000
                                    else:
                                        value = float(re.sub(r'[^0-9.]', '', value_str))
                                    conditions.append({'field': field_en, 'operator': op_sym, 'value': value})
                                    parsed = True; break
                                except Exception: pass
                    if parsed: break
            if parsed: break

        if not parsed:
            for op in ['>=', '<=', '>', '<', '=']:
                if op in kw_str:
                    parts = kw_str.split(op)
                    if len(parts) == 2:
                        field_part, value_part = parts[0].strip().lower(), parts[1].strip().lower()
                        if field_part in numeric_fields.keys():
                            try:
                                value = float(re.sub(r'[^0-9.]', '', value_part))
                                if '万' in value_part or value_part.endswith('w'): value *= 10000
                                elif '千' in value_part or value_part.endswith('k'): value *= 1000
                                conditions.append({'field': field_part, 'operator': op, 'value': value})
                                parsed = True; break
                            except Exception: pass

        if not parsed and kw_str.strip():
            text_keywords.append(kw_str)

    return text_keywords, conditions

def build_dynamic_sql(conditions, text_keywords, match_fields, data_sources, limit, offset):
    """
    数据驱动引擎：编译 WHERE 过滤与 SELECT 动态权重，注入生命周期乘数。
    data_sources: 白名单表名列表，决定参与 UNION 的子查询。
    """
    where_clauses = []
    params = []

    # 1. 严格数值边界拦截 (WHERE)
    allowed_fields = ['sold_count', 'sale_amount', 'base_price', 'real_price', 'rating', 'review_count']
    allowed_ops = ['>', '>=', '<', '<=', '=']

    for cond in conditions:
        field, operator, value = cond['field'], cond['operator'], cond['value']
        if field in allowed_fields and operator in allowed_ops:
            where_clauses.append(f"{field} {operator} %s")
            params.append(value)

    where_sql = " AND ".join(where_clauses) if where_clauses else "1=1"

    # 2. 文本权重矩阵编译
    score_exprs = []
    score_exprs.append("(IFNULL(sold_count,0) * 0.01 + IFNULL(rating,0) * 5)")

    if text_keywords:
        search_all = not match_fields or len(match_fields) == 0
        for kw in text_keywords:
            safe_kw = f"%{kw}%"
            if search_all or 'title' in match_fields:
                score_exprs.append("(IF(title LIKE %s, 60, 0))")
                params.append(safe_kw)
            if search_all or 'category' in match_fields:
                score_exprs.append("(IF(CONCAT_WS(',', category_l1, category_l2, category_l3) LIKE %s, 30, 0))")
                params.append(safe_kw)
            if search_all or 'other' in match_fields:
                # 必须确保外层 combined_data 中存在 comments 字段
                score_exprs.append("(IF(IFNULL(comments,'') LIKE %s, 10, 0))")
                params.append(safe_kw)

    if not score_exprs: score_exprs.append("100")
    base_score_sql = " + ".join(score_exprs)

    # 3. 联合表构建与生命周期加权（按 data_sources 动态拼装）
    # 表名来自服务端白名单 DATA_SOURCE_REGISTRY，非用户输入，拼接安全
    valid_tables = filter_data_sources(data_sources)
    union_parts = []
    for tbl in valid_tables:
        meta = DATA_SOURCE_REGISTRY[tbl]
        union_parts.append(
            "SELECT product_id, title, category_l1, category_l2, category_l3, "
            "base_price, sold_count, rating, review_count, comments, "
            f"'{meta['stage']}' AS lifecycle_stage, {meta['weight']} AS lifecycle_weight "
            f"FROM {tbl}"
        )
    base_select = " UNION ALL ".join(union_parts)
    
    # 核心聚合：基础得分 * 生命周期系数
    query = f"""
        SELECT *, (({base_score_sql}) * lifecycle_weight) AS dynamic_match_score
        FROM ({base_select}) AS combined_data
        WHERE {where_sql}
        HAVING dynamic_match_score > 0
        ORDER BY dynamic_match_score DESC, sold_count DESC
        LIMIT %s OFFSET %s
    """
    
    count_query = f"""
        SELECT COUNT(*) as total
        FROM (
            SELECT *, (({base_score_sql}) * lifecycle_weight) AS dynamic_match_score
            FROM ({base_select}) AS combined_data
            WHERE {where_sql}
            HAVING dynamic_match_score > 0
        ) AS filtered_data
    """
    
    count_params = params.copy()
    params.extend([limit, offset])
    
    return query, count_query, tuple(params), tuple(count_params)

@app.route('/api/strategies', methods=['GET'])
def get_strategies():
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("SELECT * FROM product_strategies ORDER BY created_at DESC")
            strategies = cursor.fetchall()
            for s in strategies:
                for fld in ('keywords', 'match_fields', 'data_sources'):
                    val = s.get(fld)
                    if isinstance(val, str):
                        try: s[fld] = json.loads(val)
                        except Exception: s[fld] = []
                    elif val is None:
                        s[fld] = []
            return jsonify({'code': 0, 'data': strategies})
    finally: conn.close()

@app.route('/api/strategies/export', methods=['GET'])
def export_strategies():
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("SELECT * FROM product_strategies ORDER BY created_at DESC")
            strategies = cursor.fetchall()
            
            from io import BytesIO
            from flask import send_file
            
            wb = openpyxl.Workbook()
            ws = wb.active
            ws.title = "大盘策略看板"
            
            headers = ['架构代号', '语义约束/目标', '运行状态', '创建时间']
            ws.append(headers)
            
            for s in strategies:
                ws.append([
                    s['name'],
                    s['description'] or '',
                    '引擎在线' if s.get('is_active', 1) else '节点休眠',
                    str(s['created_at'])
                ])
            
            output = BytesIO()
            wb.save(output)
            output.seek(0)
            
            return send_file(
                output,
                mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                as_attachment=True,
                download_name='strategies_export.xlsx'
            )
    finally: conn.close()

def _process_strategy_payload():
    """从 request.form/files 抽取并加工策略字段。返回 dict（可能含 None 表示不变）。"""
    name = request.form.get('name', '').strip()
    description = request.form.get('description', '')
    keywords_text = request.form.get('keywords_text', '')
    match_fields_str = request.form.get('match_fields', '[]')
    data_sources_str = request.form.get('data_sources', '[]')

    try: match_fields = json.loads(match_fields_str) if match_fields_str else []
    except Exception: match_fields = []
    try: data_sources_raw = json.loads(data_sources_str) if data_sources_str else []
    except Exception: data_sources_raw = []
    data_sources = [t for t in data_sources_raw if t in DATA_SOURCE_REGISTRY]

    file_path, file_type = None, None
    keywords = None
    content = None
    has_new_file = 'file' in request.files and request.files['file'].filename

    if has_new_file:
        file = request.files['file']
        ext = os.path.splitext(file.filename)[1].lower()
        file_type = 'word' if ext in ['.docx', '.doc'] else ('excel' if ext in ['.xlsx', '.xls'] else 'text')
        unique_name = f"{uuid.uuid4().hex[:8]}_{file.filename}"
        file_path = os.path.join(UPLOAD_FOLDER, unique_name)
        file.save(file_path)
        extracted_text = extract_text_from_file(file_path, file_type)
        keywords = extract_keywords(extracted_text)
        content = extracted_text
    elif keywords_text:
        extracted = extract_keywords(keywords_text)
        text_kws, conditions = parse_strategy_keywords(extracted)
        keywords = text_kws + [f"{c['field']}{c['operator']}{c['value']}" for c in conditions]
        content = keywords_text

    if keywords is not None and not keywords and keywords_text:
        keywords = [keywords_text]

    return {
        'name': name,
        'description': description,
        'keywords_text': keywords_text,
        'match_fields': match_fields,
        'data_sources': data_sources,
        'keywords': keywords,
        'content': content,
        'file_path': file_path,
        'file_type': file_type,
        'has_new_file': has_new_file,
    }

@app.route('/api/strategies', methods=['POST'])
def create_strategy():
    payload = _process_strategy_payload()
    if not payload['name']:
        return jsonify({'code': 1, 'message': '策略名称不能为空'}), 400

    keywords = payload['keywords'] or []
    content = payload['content'] if payload['content'] is not None else payload['keywords_text']

    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute("""
                INSERT INTO product_strategies (name, description, keywords, content, file_path, file_type, match_fields, data_sources)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                payload['name'], payload['description'],
                json.dumps(keywords, ensure_ascii=False), content,
                payload['file_path'], payload['file_type'],
                json.dumps(payload['match_fields']),
                json.dumps(payload['data_sources']),
            ))
            conn.commit()
            return jsonify({'code': 0, 'message': '策略创建成功'})
    finally: conn.close()

@app.route('/api/strategies/<int:strategy_id>', methods=['PUT', 'DELETE'])
def modify_strategy(strategy_id):
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            if request.method == 'DELETE':
                cursor.execute("SELECT file_path FROM product_strategies WHERE id = %s", (strategy_id,))
                res = cursor.fetchone()
                cursor.execute("DELETE FROM product_strategies WHERE id = %s", (strategy_id,))
                conn.commit()
                if res and res.get('file_path') and os.path.exists(res['file_path']):
                    os.remove(res['file_path'])
                return jsonify({'code': 0, 'message': '策略删除成功'})

            # PUT：编辑现有策略
            payload = _process_strategy_payload()
            if not payload['name']:
                return jsonify({'code': 1, 'message': '策略名称不能为空'}), 400

            cursor.execute("SELECT keywords, content, file_path, file_type FROM product_strategies WHERE id = %s", (strategy_id,))
            old = cursor.fetchone()
            if not old:
                return jsonify({'code': 1, 'message': '策略不存在'}), 404

            # 合并字段：新文件 > 新关键词文本 > 保留旧值
            if payload['has_new_file']:
                new_keywords = payload['keywords']
                new_content = payload['content']
                new_file_path = payload['file_path']
                new_file_type = payload['file_type']
                # 删除旧文件
                if old.get('file_path') and os.path.exists(old['file_path']):
                    try: os.remove(old['file_path'])
                    except Exception: pass
            elif payload['keywords_text']:
                new_keywords = payload['keywords']
                new_content = payload['content']
                new_file_path = old.get('file_path')
                new_file_type = old.get('file_type')
            else:
                # 既没传新文件也没填关键词文本：保留旧的 keywords/content/file
                new_keywords = None
                new_content = old.get('content')
                new_file_path = old.get('file_path')
                new_file_type = old.get('file_type')

            keywords_json = (json.dumps(new_keywords, ensure_ascii=False)
                             if new_keywords is not None else old.get('keywords'))

            cursor.execute("""
                UPDATE product_strategies
                SET name=%s, description=%s, keywords=%s, content=%s,
                    file_path=%s, file_type=%s, match_fields=%s, data_sources=%s
                WHERE id=%s
            """, (
                payload['name'], payload['description'],
                keywords_json, new_content,
                new_file_path, new_file_type,
                json.dumps(payload['match_fields']),
                json.dumps(payload['data_sources']),
                strategy_id,
            ))
            conn.commit()
            return jsonify({'code': 0, 'message': '策略更新成功'})
    finally: conn.close()

@app.route('/api/strategies/<int:strategy_id>/rankings', methods=['GET'])
def get_rankings(strategy_id):
    page = request.args.get('page', 1, type=int)
    page_size = request.args.get('page_size', 20, type=int)
    offset = (page - 1) * page_size
    
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("SELECT * FROM product_strategies WHERE id = %s", (strategy_id,))
            strategy = cursor.fetchone()
            if not strategy: return jsonify({'code': 1, 'message': '策略不存在'}), 404

            keywords_raw = json.loads(strategy['keywords']) if isinstance(strategy['keywords'], str) else (strategy['keywords'] or [])
            match_fields = json.loads(strategy['match_fields']) if isinstance(strategy['match_fields'], str) else (strategy['match_fields'] or [])
            data_sources = json.loads(strategy['data_sources']) if isinstance(strategy.get('data_sources'), str) else (strategy.get('data_sources') or [])

            text_keywords, conditions = parse_strategy_keywords(keywords_raw)

            # 调用底层引擎
            query, count_query, params, count_params = build_dynamic_sql(
                conditions, text_keywords, match_fields, data_sources, page_size, offset
            )

            # 执行查询
            cursor.execute(count_query, count_params)
            total_count = cursor.fetchone()['total']

            cursor.execute(query, params)
            results = cursor.fetchall()

            rankings = []
            for i, row in enumerate(results):
                rankings.append({
                    # product_id 为 18 位 BIGINT，超过 JS Number.MAX_SAFE_INTEGER，必须字符串化避免前端精度丢失
                    'product_id': str(row['product_id']),
                    'match_score': round(float(row['dynamic_match_score']), 1),
                    'rank_position': offset + i + 1,
                    'lifecycle_stage': row['lifecycle_stage'],
                    'product_data': {
                        'title': row['title'],
                        'base_price': decimal_to_float(row['base_price']),
                        'sold_count': row['sold_count'],
                        'rating': decimal_to_float(row['rating']),
                        'category_l1': row['category_l1']
                    }
                })

            return jsonify({'code': 0, 'data': {'list': rankings, 'total': total_count, 'page': page}})
    except Exception as e:
        print(f"SQL Engine Error: {e}")
        return jsonify({'code': -1, 'message': f'底层引擎运算异常: {str(e)}'}), 500
    finally:
        conn.close()

@app.route('/api/strategies/<int:strategy_id>/export', methods=['GET'])
def export_rankings(strategy_id):
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("SELECT * FROM product_strategies WHERE id = %s", (strategy_id,))
            strategy = cursor.fetchone()
            if not strategy: return jsonify({'code': 1, 'message': '策略不存在'}), 404

            keywords_raw = json.loads(strategy['keywords']) if isinstance(strategy['keywords'], str) else (strategy['keywords'] or [])
            match_fields = json.loads(strategy['match_fields']) if isinstance(strategy['match_fields'], str) else (strategy['match_fields'] or [])
            data_sources = json.loads(strategy['data_sources']) if isinstance(strategy.get('data_sources'), str) else (strategy.get('data_sources') or [])

            text_keywords, conditions = parse_strategy_keywords(keywords_raw)

            # 导出全量数据，不分页 (或者设置一个较大的限制)
            query, _, params, _ = build_dynamic_sql(
                conditions, text_keywords, match_fields, data_sources, 1000, 0
            )

            cursor.execute(query, params)
            results = cursor.fetchall()

            from io import BytesIO
            from flask import send_file
            
            wb = openpyxl.Workbook()
            ws = wb.active
            ws.title = f"排名 - {strategy['name']}"
            
            headers = ['排名', '算法定级分', '生命周期阶段', '商品ID', '商品标题', '一级类目', '大盘基价', '销量', '评分', '评价数']
            ws.append(headers)
            
            for i, row in enumerate(results):
                ws.append([
                    i + 1,
                    round(float(row['dynamic_match_score']), 1),
                    '新品扶持期' if row['lifecycle_stage'] == 'NEW_ARRIVAL' else '成熟利润期',
                    str(row['product_id']),
                    row['title'],
                    row['category_l1'],
                    float(row['base_price']) if row['base_price'] else 0,
                    row['sold_count'],
                    float(row['rating']) if row['rating'] else 0,
                    row['review_count']
                ])
            
            output = BytesIO()
            wb.save(output)
            output.seek(0)
            
            filename = f"ranking_export_{strategy['name']}.xlsx"
            return send_file(
                output,
                mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                as_attachment=True,
                download_name=filename
            )
    except Exception as e:
        print(f"Export Error: {e}")
        return jsonify({'code': -1, 'message': f'导出异常: {str(e)}'}), 500
    finally:
        conn.close()

@app.route('/api/data-sources', methods=['GET'])
def list_data_sources():
    return jsonify({'code': 0, 'data': [
        {'table': k, 'label': v['label'], 'stage': v['stage'], 'weight': v['weight']}
        for k, v in DATA_SOURCE_REGISTRY.items()
    ]})

@app.route('/')
def index():
    return send_from_directory('static', 'index.html')

if __name__ == '__main__':
    print("AI 驱动：智能选品决策引擎已启动...")
    ensure_schema()
    app.run(host='0.0.0.0', port=5001, debug=True)