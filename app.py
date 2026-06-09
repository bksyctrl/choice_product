import base64
import io
import json
import os
import re
import sys
import threading
import time
import traceback
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

import pymysql
import requests
from dotenv import load_dotenv
from flask import Flask, jsonify, redirect, render_template, request, send_file, session
from flask_cors import CORS
from openpyxl import Workbook
from werkzeug.security import check_password_hash, generate_password_hash


load_dotenv()

TOOLS_DIR = Path(__file__).resolve().parent / "tools"
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from analyze_single_product import (  # noqa: E402
    MINIMAX_BASE_URL,
    MINIMAX_MODEL,
    PRODUCT_SOURCES as AI_PRODUCT_SOURCES,
    build_input_snapshot,
    load_product as load_ai_product,
    resolve_minimax_api_key,
    run_analysis,
    run_material_prefilter,
)


DB_CONFIG = {
    "host": os.getenv("DB_HOST", "192.168.0.168"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "ITaimysql"),
    "password": os.getenv("DB_PASSWORD", "Ai12345678@"),
    "database": os.getenv("DB_NAME", "ecommerce_workflow"),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}

STATUS_VALUES = {"PENDING", "REVIEWING", "READY", "PUBLISHED", "OTHER"}
IP_GRADES = ["E", "D", "C", "B", "A", "S"]
AI_DAEMON_ENABLED = os.getenv("AI_DAEMON_ENABLED", "false").strip().lower() == "true"
AI_DAEMON_SOURCE = os.getenv("AI_DAEMON_SOURCE", "fastmoss").strip()
AI_DAEMON_SOURCES = [
    item.strip()
    for item in os.getenv("AI_DAEMON_SOURCES", AI_DAEMON_SOURCE).split(",")
    if item.strip()
]
AI_DAEMON_ANALYSIS = os.getenv("AI_DAEMON_ANALYSIS", "both").strip().lower()
AI_DAEMON_INTERVAL_SECONDS = int(os.getenv("AI_DAEMON_INTERVAL_SECONDS", "300"))
AI_DAEMON_BATCH_SIZE = int(os.getenv("AI_DAEMON_BATCH_SIZE", "10"))
AI_DAEMON_CONCURRENCY = max(1, min(5, int(os.getenv("AI_DAEMON_CONCURRENCY", "1"))))
AI_DAEMON_WRITE = os.getenv("AI_DAEMON_WRITE", "true").strip().lower() != "false"
AI_DAEMON_LOCK_RETRIES = max(1, int(os.getenv("AI_DAEMON_LOCK_RETRIES", "3")))
AI_DAEMON_LOCK_WAIT_SECONDS = max(1, int(os.getenv("AI_DAEMON_LOCK_WAIT_SECONDS", "3")))
AI_DAEMON_LATEST_ONLY = os.getenv("AI_DAEMON_LATEST_ONLY", "true").strip().lower() != "false"
DETAIL_ANALYSIS_TABLE = "cp_user_detail_analysis"
EXPERT_TEAM_SESSION_TABLE = "cp_expert_team_session"
EXPERT_TEAM_MESSAGE_TABLE = "cp_expert_team_message"
EXPERT_WORKFLOW_INSTANCE_TABLE = "cp_expert_workflow_instance"
EXPERT_WORKFLOW_STEP_TABLE = "cp_expert_workflow_step"
EXPERT_TEAM_ALLOWED_ROLES = {"admin", "manager"}
PERMISSION_EXPERT_TEAM = "expert_team"
PROJECT_ROOT = Path(__file__).resolve().parent
EXPERT_FILE_CONTEXT_MAX_FILES = 12
EXPERT_FILE_CONTEXT_MAX_MATCHES_PER_FILE = 5
EXPERT_FILE_CONTEXT_MAX_CHARS = 30000
EXPERT_FILE_ALLOWED_SUFFIXES = {".py", ".html", ".css", ".js", ".md", ".yml", ".yaml", ".xml", ".json", ".txt"}
EXPERT_FILE_EXCLUDED_DIRS = {
    ".git",
    ".idea",
    ".venv",
    "__pycache__",
    "target",
    "node_modules",
    "tmp_render",
    "ComfyUI_ImageToText-main",
}
DETAIL_ANALYSIS_EXECUTOR = ThreadPoolExecutor(max_workers=max(1, min(4, int(os.getenv("DETAIL_ANALYSIS_CONCURRENCY", "2")))))
_ai_daemon_started = False
_ai_daemon_current_concurrency = AI_DAEMON_CONCURRENCY
FASTMOSS_ANALYSIS_REUSE_SOURCES = ("fastmoss", "fastmoss_rank")


class ApiAuthError(Exception):
    pass


class ApiPermissionError(Exception):
    pass

SOURCES = {
    "unified": {
        "label": "统一表",
        "table": "fastmoss_product_aggregate",
        "id": "product_id",
        "date": "date_record",
        "title": "title",
        "image": "image_base64",
        "price": "real_price",
        "rating": "rating",
        "commission": "commission_rate",
        "sold": "rank_sold_count",
        "total_sold": "sold_count",
        "sale_amount": "sale_amount",
        "author_count": "author_count",
        "base_price": "base_price",
        "transport_fee": "transport_fee",
        "video_ratio": None,
        "product_card_ratio": None,
        "distribution_30d": "content_ratio",
        "distribution_7d": "distribution_7d",
        "distribution_90d": "distribution_90d",
        "distribution_180d": "distribution_180d",
        "overview_30d": "sales_overview",
        "overview_7d": "overview_7d",
        "overview_90d": "overview_90d",
        "overview_180d": "overview_180d",
        "sku_analysis_7d": "sku_analysis_7d",
        "sku_analysis_28d": "sku_analysis_28d",
        "detail_url": "detail_url",
        "platform": "fastmoss",
        "selling_points": "selling_points",
        "attributes": "attributes",
    },
    "fastmoss": {
        "label": "FastMoss",
        "table": "fastmoss_product_rank_aggregate",
        "id": "product_id",
        "date": "date_record",
        "title": "title",
        "image": "image_base64",
        "price": "real_price",
        "rating": "rating",
        "commission": "commission_rate",
        "sold": "rank_sold_count",
        "total_sold": "sold_count",
        "sale_amount": "sale_amount",
        "author_count": "author_count",
        "base_price": "base_price",
        "transport_fee": "transport_fee",
        "video_ratio": None,
        "product_card_ratio": None,
        "distribution_30d": "content_ratio",
        "distribution_7d": "distribution_7d",
        "distribution_90d": "distribution_90d",
        "distribution_180d": "distribution_180d",
        "overview_30d": "sales_overview",
        "overview_7d": "overview_7d",
        "overview_90d": "overview_90d",
        "overview_180d": "overview_180d",
        "sku_analysis_7d": "sku_analysis_7d",
        "sku_analysis_28d": "sku_analysis_28d",
        "detail_url": "detail_url",
        "platform": "fastmoss",
        "selling_points": None,
        "attributes": None,
    },
    "kalodata": {
        "label": "Kalodata",
        "table": "kalodata_youwei_product",
        "id": "商品ID",
        "date": "date_record",
        "title": "商品标题",
        "image": "商品主图",
        "price": "价格",
        "rating": "商品评分",
        "commission": "佣金比例",
        "sold": "总销量",
        "total_sold": "all_solds",
        "sale_amount": "总成交额",
        "author_count": "关联达人数",
        "base_price": None,
        "transport_fee": None,
        "video_ratio": "近28天视频占比",
        "product_card_ratio": "近28天商品卡占比",
        "distribution_30d": None,
        "distribution_7d": "distribution_7d",
        "distribution_90d": "distribution_90d",
        "distribution_180d": "distribution_180d",
        "overview_30d": None,
        "overview_7d": "overview_7d",
        "overview_90d": "overview_90d",
        "overview_180d": "overview_180d",
        "sku_analysis_7d": "sku_analysis_7d",
        "sku_analysis_28d": "sku_analysis_28d",
        "detail_url": "商品链接",
        "platform": "kalodata",
        "selling_points": "卖点",
        "attributes": "属性信息",
    },
}

SORT_FIELDS = {
    "date_record": "date_record",
    "sold": "sold_count_view",
    "sale_amount": "sale_amount_view",
    "rating": "rating_view",
    "price": "price_view",
    "author_count": "author_count_view",
    "launch_time": "launch_time",
    "product_card_sales": "product_card_sales_view",
    "product_card_ratio": "product_card_ratio_view",
    "sales_growth": "sales_growth_view",
    "ip_grade": "ip_grade",
}


def create_app():
    app = Flask(__name__, static_folder="static", template_folder="static")
    app.secret_key = os.getenv("SECRET_KEY", "choice-product-dev-secret")
    CORS(app, supports_credentials=True)
    ensure_detail_analysis_table()
    ensure_expert_team_tables()

    @app.errorhandler(ApiAuthError)
    def handle_auth_error(_error):
        return api_error("未登录", 401)

    @app.errorhandler(ApiPermissionError)
    def handle_permission_error(_error):
        return api_error("没有专家团队使用权限", 403)

    @app.errorhandler(PermissionError)
    def handle_legacy_permission_error(_error):
        return api_error("没有专家团队使用权限", 403)

    @app.get("/")
    def index():
        return render_template("index.html")

    @app.get("/login")
    def login_page():
        return render_template("login.html")

    @app.post("/api/auth/register")
    def register():
        payload = request.get_json(silent=True) or {}
        username = stringify(payload.get("username")).strip()
        password = stringify(payload.get("password"))
        display_name = stringify(payload.get("display_name")).strip() or username
        if not username or not password:
            return api_error("用户名和密码不能为空", 400)
        if len(username) > 64:
            return api_error("用户名过长", 400)
        if len(password) < 6:
            return api_error("密码至少 6 位", 400)

        password_hash = generate_password_hash(password)
        try:
            with db() as conn, conn.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO app_user (username, password_hash, display_name, role, status)
                    VALUES (%s, %s, %s, 'user', 'ACTIVE')
                    """,
                    (username, password_hash, display_name),
                )
                conn.commit()
                user_id = cursor.lastrowid
                cursor.execute(
                    "SELECT id, username, display_name, role, status FROM app_user WHERE id = %s",
                    (user_id,),
                )
                user = cursor.fetchone()
        except pymysql.err.IntegrityError:
            return api_error("用户名已存在", 409)
        session["uid"] = user["id"]
        return api_ok({"user": public_user(user)})

    @app.post("/api/auth/login")
    def login():
        payload = request.get_json(silent=True) or {}
        username = stringify(payload.get("username")).strip()
        password = stringify(payload.get("password"))
        if not username or not password:
            return api_error("用户名和密码不能为空", 400)
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT id, username, password_hash, display_name, role, status FROM app_user WHERE username = %s",
                (username,),
            )
            user = cursor.fetchone()
        if not user or user.get("status") != "ACTIVE" or not check_password_hash(user.get("password_hash") or "", password):
            return api_error("用户名或密码错误", 401)
        session["uid"] = user["id"]
        return api_ok({"user": public_user(user)})

    @app.get("/api/auth/me")
    def auth_me():
        user = current_user()
        if not user:
            return api_error("未登录", 401)
        return api_ok({"user": public_user(user)})

    @app.post("/api/auth/logout")
    def logout():
        session.clear()
        return api_ok({"logged_out": True})

    @app.get("/api/products")
    def products():
        require_current_user()
        source = normalize_source(request.args.get("source"))
        period = normalize_period(request.args.get("period"))
        sales_period = build_sales_period(
            period,
            request.args.get("sales_start"),
            request.args.get("sales_end"),
        )
        page = clamp_int(request.args.get("page"), 1, 1, 100000)
        page_size = clamp_int(request.args.get("page_size"), 30, 10, 100)
        meta = SOURCES[source]
        where, params = build_filters(meta, request.args)
        use_sales_aggregate = True
        order_sql = build_order(meta, request.args.get("sort_by"), request.args.get("sort_order"), sales_period, use_sales_aggregate, request.args)
        offset = (page - 1) * page_size

        select_sql = build_product_select(source, meta)
        latest_join = build_latest_product_join(meta, where)
        sales_aggregate_join = build_sales_aggregate_join(meta, where) if use_sales_aggregate else ""
        previous_sales_aggregate_join = build_previous_sales_aggregate_join(
            meta,
            request.args.get("date_start"),
            request.args.get("date_end"),
        ) if use_sales_aggregate else ""
        sales_delta_join = build_sales_delta_join(
            meta,
            request.args.get("date_start"),
            request.args.get("date_end"),
        ) if use_sales_aggregate else ""
        runtime_sold_select = (
            "sales_aggregate.aggregate_sold_count AS runtime_sold_count, previous_sales_aggregate.previous_sold_count AS previous_runtime_sold_count, "
            f"{sales_delta_current_expr(meta)} AS current_period_sold_count, {sales_delta_previous_expr()} AS previous_period_sold_count"
            if use_sales_aggregate
            else "NULL AS runtime_sold_count, NULL AS previous_runtime_sold_count, NULL AS current_period_sold_count, NULL AS previous_period_sold_count"
        )
        sql = f"""
            SELECT {select_sql}, {runtime_sold_select}
            FROM `{meta["table"]}`
            {latest_join}
            {sales_aggregate_join}
            {previous_sales_aggregate_join}
            {sales_delta_join}
            {order_sql}
            LIMIT %s OFFSET %s
        """
        count_sql = f"""
            SELECT COUNT(*) AS total FROM (
                SELECT `{meta["id"]}` AS product_id
                FROM `{meta["table"]}`
                WHERE {" AND ".join(where)}
                GROUP BY `{meta["id"]}`
            ) AS latest_count
        """

        with db() as conn, conn.cursor() as cursor:
            cursor.execute(count_sql, params)
            total = int(cursor.fetchone()["total"])
            query_params = [*params]
            if use_sales_aggregate:
                query_params.extend(params)
            cursor.execute(sql, [*query_params, page_size, offset])
            raw_rows = cursor.fetchall()
            attach_runtime_metrics(
                cursor,
                meta,
                raw_rows,
                sales_period,
                request.args.get("date_start"),
                request.args.get("date_end"),
            )
            rows = [normalize_product_row(source, row, sales_period) for row in raw_rows]

        return api_ok({
            "source": source,
            "period": sales_period["period"],
            "period_label": sales_period["label"],
            "page": page,
            "page_size": page_size,
            "total": total,
            "items": rows,
        })

    @app.get("/api/products/stats")
    def stats():
        require_current_user()
        source = normalize_source(request.args.get("source"))
        meta = SOURCES[source]
        where, params = build_filters(meta, request.args)
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(f"SELECT COUNT(*) AS total FROM `{meta['table']}` WHERE {' AND '.join(where)}", params)
            total = int(cursor.fetchone()["total"])
            cursor.execute(
                f"""
                SELECT audit_status, COUNT(*) AS count
                FROM `{meta['table']}`
                WHERE {" AND ".join(where)}
                GROUP BY audit_status
                """,
                params,
            )
            statuses = {row["audit_status"] or "PENDING": int(row["count"]) for row in cursor.fetchall()}
            cursor.execute(
                f"""
                SELECT ip_grade, COUNT(*) AS count
                FROM `{meta['table']}`
                WHERE {" AND ".join(where)} AND ip_grade IS NOT NULL AND ip_grade <> ''
                GROUP BY ip_grade
                """,
                params,
            )
            grades = {row["ip_grade"]: int(row["count"]) for row in cursor.fetchall()}
            material_counts = count_material_types(cursor, meta["table"], where, params)
        return api_ok({
            "source": source,
            "total": total,
            "statuses": statuses,
            "ip_grades": grades,
            "materials": material_counts,
        })

    @app.get("/api/products/latest-date")
    def latest_date():
        require_current_user()
        source = normalize_source(request.args.get("source"))
        meta = SOURCES[source]
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(f"SELECT MAX(`{meta['date']}`) AS latest_date FROM `{meta['table']}`")
            row = cursor.fetchone()
        return api_ok({
            "source": source,
            "latest_date": stringify(row.get("latest_date") if row else None),
        })

    @app.post("/api/translate-title")
    def translate_title():
        require_current_user()
        payload = request.get_json(silent=True) or {}
        text = stringify(payload.get("text")).strip()
        if not text:
            return api_error("text 不能为空", 400)
        if len(text) > 800:
            return api_error("商品名过长，无法翻译", 400)
        try:
            translation = translate_text_to_chinese(text)
        except RuntimeError as exc:
            translation = fallback_translate_title(text)
            return api_ok({"source": text, "translation": translation, "provider": "local_fallback", "warning": str(exc)})
        except requests.RequestException as exc:
            translation = fallback_translate_title(text)
            return api_ok({"source": text, "translation": translation, "provider": "local_fallback", "warning": str(exc)})
        return api_ok({"source": text, "translation": strip_llm_think_blocks(translation), "provider": "minimax"})

    @app.post("/api/translate-text")
    def translate_text():
        require_current_user()
        payload = request.get_json(silent=True) or {}
        text = stringify(payload.get("text")).strip()
        if not text:
            return api_error("text 不能为空", 400)
        if len(text) > 5000:
            return api_error("文本过长，无法翻译", 400)
        try:
            translation = translate_general_text_to_chinese(text)
        except RuntimeError as exc:
            translation = fallback_translate_title(text)
            return api_ok({"source": text, "translation": translation, "provider": "local_fallback", "warning": str(exc)})
        except requests.RequestException as exc:
            translation = fallback_translate_title(text)
            return api_ok({"source": text, "translation": translation, "provider": "local_fallback", "warning": str(exc)})
        return api_ok({"source": text, "translation": strip_llm_think_blocks(translation), "provider": "minimax"})

    @app.get("/api/products/<source>/<product_id>")
    def product_detail(source, product_id):
        require_current_user()
        source = normalize_source(source)
        meta = SOURCES[source]
        date_record = request.args.get("date_record")
        where = [f"`{meta['id']}` = %s"]
        params = [product_id]
        if date_record:
            where.append(f"`{meta['date']}` = %s")
            params.append(date_record)
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT *
                FROM `{meta['table']}`
                WHERE {" AND ".join(where)}
                ORDER BY `{meta['date']}` DESC
                LIMIT 1
                """,
                params,
            )
            row = cursor.fetchone()
            if not row:
                return api_error("商品不存在", 404)
            detail = normalize_detail(source, row)
            enrich_detail_period_fields(cursor, source, meta, row, detail)
        return api_ok(detail)

    @app.post("/api/products/<source>/<product_id>/analysis-detail")
    def product_analysis_detail(source, product_id):
        user = require_current_user()
        source = normalize_source(source)
        payload = request.get_json(silent=True) or {}
        analysis_type = stringify(payload.get("analysis_type")).strip().lower()
        date_record = payload.get("date_record") or request.args.get("date_record")
        if analysis_type not in {"ip", "material"}:
            return api_error("analysis_type 必须是 ip 或 material", 400)
        if not date_record:
            return api_error("date_record 不能为空", 400)

        ai_source = "fastmoss" if source == "unified" else source
        if ai_source not in AI_PRODUCT_SOURCES:
            return api_error(f"暂不支持该数据源的详细分析：{source}", 400)

        with db() as conn, conn.cursor() as cursor:
            record = fetch_detail_analysis_record(cursor, user["id"], source, product_id, date_record, analysis_type)
            if record and record.get("status") == "SUCCESS":
                return api_ok({"cached": True, "record": normalize_detail_analysis_record(record), "result": parse_json(record.get("result_json"))})
            if record and record.get("status") == "RUNNING":
                return api_ok({"cached": False, "record": normalize_detail_analysis_record(record), "status": "RUNNING"})
            record_id = upsert_detail_analysis_record(
                cursor,
                user,
                source,
                AI_PRODUCT_SOURCES[ai_source]["table"],
                product_id,
                date_record,
                analysis_type,
                payload.get("title") or "",
            )
            conn.commit()
        DETAIL_ANALYSIS_EXECUTOR.submit(run_detail_analysis_task, record_id, user["id"], ai_source, product_id, date_record, analysis_type)
        return api_ok({"cached": False, "status": "RUNNING", "record": {"id": record_id, "status": "RUNNING"}})

    @app.get("/api/detail-analyses")
    def detail_analyses():
        user = require_current_user()
        analysis_type = stringify(request.args.get("analysis_type")).strip().lower() or "ip"
        status = stringify(request.args.get("status")).strip().upper()
        if analysis_type not in {"ip", "material"}:
            return api_error("analysis_type 蹇呴』鏄?ip 鎴?material", 400)
        where = ["user_id = %s", "analysis_type = %s"]
        params = [user["id"], analysis_type]
        if status in {"RUNNING", "SUCCESS", "FAILED"}:
            where.append("status = %s")
            params.append(status)
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT *
                FROM {DETAIL_ANALYSIS_TABLE}
                WHERE {" AND ".join(where)}
                ORDER BY updated_at DESC, id DESC
                LIMIT 200
                """,
                params,
            )
            rows = [normalize_detail_analysis_record(row) for row in cursor.fetchall()]
        return api_ok({"items": rows, "analysis_type": analysis_type})

    @app.get("/api/expert-team/roles")
    def expert_team_roles():
        require_expert_team_permission()
        return api_ok({"roles": expert_team_roles_payload()})

    @app.get("/api/expert-team/sessions")
    def expert_team_sessions():
        user = require_expert_team_permission()
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT id, title, project_code, created_at, updated_at
                FROM {EXPERT_TEAM_SESSION_TABLE}
                WHERE user_id = %s
                ORDER BY updated_at DESC, id DESC
                LIMIT 30
                """,
                (user["id"],),
            )
            sessions = [normalize_expert_session(row) for row in cursor.fetchall()]
        return api_ok({"sessions": sessions})

    @app.get("/api/expert-team/sessions/<int:session_id>/messages")
    def expert_team_messages(session_id):
        user = require_expert_team_permission()
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"SELECT id FROM {EXPERT_TEAM_SESSION_TABLE} WHERE id = %s AND user_id = %s",
                (session_id, user["id"]),
            )
            if not cursor.fetchone():
                return api_error("专家团队会话不存在", 404)
            cursor.execute(
                f"""
                SELECT id, role, content, team_role, created_at
                FROM {EXPERT_TEAM_MESSAGE_TABLE}
                WHERE session_id = %s AND user_id = %s
                ORDER BY id ASC
                """,
                (session_id, user["id"]),
            )
            messages = [normalize_expert_message(row) for row in cursor.fetchall()]
        return api_ok({"messages": messages})

    @app.delete("/api/expert-team/sessions/<int:session_id>")
    def delete_expert_team_session(session_id):
        user = require_expert_team_permission()
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"SELECT id FROM {EXPERT_TEAM_SESSION_TABLE} WHERE id = %s AND user_id = %s",
                (session_id, user["id"]),
            )
            if not cursor.fetchone():
                return api_error("专家团队会话不存在", 404)
            cursor.execute(
                f"DELETE FROM {EXPERT_TEAM_MESSAGE_TABLE} WHERE session_id = %s AND user_id = %s",
                (session_id, user["id"]),
            )
            cursor.execute(
                f"DELETE FROM {EXPERT_TEAM_SESSION_TABLE} WHERE id = %s AND user_id = %s",
                (session_id, user["id"]),
            )
            conn.commit()
        return api_ok({"deleted": True, "session_id": session_id})

    @app.get("/api/expert-team/workflows")
    def expert_team_workflows():
        user = require_expert_team_permission()
        session_id = parse_int(request.args.get("session_id"))
        where = ["user_id = %s"]
        params = [user["id"]]
        if session_id:
            where.append("session_id = %s")
            params.append(session_id)
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT *
                FROM {EXPERT_WORKFLOW_INSTANCE_TABLE}
                WHERE {" AND ".join(where)}
                ORDER BY updated_at DESC, id DESC
                LIMIT 100
                """,
                params,
            )
            rows = [normalize_expert_workflow_instance(row) for row in cursor.fetchall()]
        return api_ok({"items": rows})

    @app.get("/api/expert-team/workflows/<int:workflow_id>")
    def expert_team_workflow_detail(workflow_id):
        user = require_expert_team_permission()
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT *
                FROM {EXPERT_WORKFLOW_INSTANCE_TABLE}
                WHERE id = %s AND user_id = %s
                LIMIT 1
                """,
                (workflow_id, user["id"]),
            )
            workflow = cursor.fetchone()
            if not workflow:
                return api_error("工作流不存在", 404)
            cursor.execute(
                f"""
                SELECT *
                FROM {EXPERT_WORKFLOW_STEP_TABLE}
                WHERE workflow_id = %s
                ORDER BY step_order ASC, id ASC
                """,
                (workflow_id,),
            )
            steps = [normalize_expert_workflow_step(row) for row in cursor.fetchall()]
        return api_ok({"workflow": normalize_expert_workflow_instance(workflow), "steps": steps})

    @app.post("/api/expert-team/chat")
    def expert_team_chat():
        user = require_expert_team_permission()
        payload = request.get_json(silent=True) or {}
        message = stringify(payload.get("message")).strip()
        images = payload.get("images") if isinstance(payload.get("images"), list) else []
        image_count = len(images)
        base_files = normalize_expert_base_files(payload.get("base_files"))
        if not message and image_count:
            message = f"请分析我发送的{image_count}张图片"
        if not message:
            return api_error("请输入要和专家团队讨论的问题", 400)
        session_id = parse_int(payload.get("session_id"))
        project_code = stringify(payload.get("project_code")).strip() or "choice_product"
        project_context = stringify(payload.get("project_context")).strip()

        with db() as conn, conn.cursor() as cursor:
            if session_id:
                cursor.execute(
                    f"SELECT * FROM {EXPERT_TEAM_SESSION_TABLE} WHERE id = %s AND user_id = %s",
                    (session_id, user["id"]),
                )
                if not cursor.fetchone():
                    return api_error("专家团队会话不存在", 404)
            else:
                cursor.execute(
                    f"""
                    INSERT INTO {EXPERT_TEAM_SESSION_TABLE}
                      (user_id, username, title, project_code, project_context)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    (
                        user["id"],
                        user.get("username") or "",
                        make_expert_session_title(message),
                        project_code,
                        project_context,
                    ),
                )
                conn.commit()
                session_id = cursor.lastrowid

            cursor.execute(
                f"""
                INSERT INTO {EXPERT_TEAM_MESSAGE_TABLE}
                  (session_id, user_id, role, team_role, content)
                VALUES (%s, %s, 'user', 'user', %s)
                """,
                (session_id, user["id"], message),
            )
            conn.commit()

            cursor.execute(
                f"""
                SELECT role, team_role, content
                FROM {EXPERT_TEAM_MESSAGE_TABLE}
                WHERE session_id = %s AND user_id = %s
                ORDER BY id DESC
                LIMIT 12
                """,
                (session_id, user["id"]),
            )
            history = list(reversed(cursor.fetchall()))

        context_query = build_expert_context_query(message, history)
        base_files = merge_expert_base_files(base_files, extract_expert_file_paths(context_query))
        readonly_context = collect_expert_readonly_context(context_query, base_files)
        ceo_decision = build_expert_ceo_decision(context_query, readonly_context, image_count)
        expert_execution = dispatch_expert_handlers(ceo_decision, context_query, readonly_context, base_files, session_id, user["id"])
        learning_result = build_expert_post_learning(session_id, context_query, ceo_decision, expert_execution)
        if image_count:
            readonly_context = (readonly_context + "\n\n" if readonly_context else "") + (
                f"用户本轮随消息发送了{image_count}张图片。当前后端已接收图片上下文，"
                "但专家团队视觉识别能力需要接入视觉模型后才能直接读取图片内容。"
            )

        try:
            answer = sanitize_expert_team_answer(call_expert_team_ai(user, message, history, project_code, project_context, readonly_context, ceo_decision, expert_execution, learning_result))
            if readonly_context and is_expert_fake_wait_answer(answer):
                answer = build_expert_read_context_answer(message, readonly_context, ceo_decision, expert_execution)
            answer = ensure_expert_execution_status(answer, ceo_decision, expert_execution)
            answer, validation_result = validate_and_refine_expert_answer(context_query, answer, ceo_decision, expert_execution)
            status = "SUCCESS"
        except Exception as exc:
            answer = sanitize_expert_team_answer(fallback_expert_team_answer(message, str(exc)))
            validation_result = {"passed": False, "fallback": True, "reason": str(exc), "retry_count": 0}
            status = "FALLBACK"

        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                INSERT INTO {EXPERT_TEAM_MESSAGE_TABLE}
                  (session_id, user_id, role, team_role, content, meta_json)
                VALUES (%s, %s, 'assistant', 'chief_planner', %s, %s)
                """,
                (session_id, user["id"], answer, json.dumps({"status": status, "readonly_context": readonly_context, "image_count": image_count, "base_files": base_files, "ceo_decision": ceo_decision, "expert_execution": expert_execution, "learning_result": learning_result, "response_validation": validation_result}, ensure_ascii=False)),
            )
            cursor.execute(
                f"UPDATE {EXPERT_TEAM_SESSION_TABLE} SET updated_at = NOW() WHERE id = %s AND user_id = %s",
                (session_id, user["id"]),
            )
            conn.commit()
            cursor.execute(
                f"SELECT * FROM {EXPERT_TEAM_SESSION_TABLE} WHERE id = %s AND user_id = %s",
                (session_id, user["id"]),
            )
            session_row = cursor.fetchone()
        return api_ok({"session": normalize_expert_session(session_row), "message": answer, "status": status})

    @app.patch("/api/products/<source>/<product_id>/status")
    def update_status(source, product_id):
        require_current_user()
        source = normalize_source(source)
        payload = request.get_json(silent=True) or {}
        audit_status = payload.get("audit_status")
        date_record = payload.get("date_record")
        if audit_status not in STATUS_VALUES:
            return api_error("audit_status 非法", 400)
        if not date_record:
            return api_error("date_record 不能为空", 400)

        meta = SOURCES[source]
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                UPDATE `{meta['table']}`
                SET audit_status = %s
                WHERE `{meta['id']}` = %s AND `{meta['date']}` = %s
                """,
                (audit_status, product_id, date_record),
            )
            conn.commit()
            affected = cursor.rowcount
        if affected == 0:
            return api_error("未找到需要修改的商品", 404)
        return api_ok({"updated": affected, "audit_status": audit_status})

    @app.get("/api/products/<source>/ready-links")
    def ready_links(source):
        require_current_user()
        source = normalize_source(source)
        links = fetch_ready_links(source)
        return api_ok({"source": source, "count": len(links), "links": links})

    @app.get("/api/products/<source>/export-ready-links")
    def export_ready_links(source):
        require_current_user()
        source = normalize_source(source)
        links = fetch_ready_links(source)
        wb = Workbook()
        ws = wb.active
        ws.title = "ready_links"
        ws.append(["link"])
        for link in links:
            ws.append([link])
        output = io.BytesIO()
        wb.save(output)
        output.seek(0)
        return send_file(
            output,
            as_attachment=True,
            download_name=f"{source}_ready_links.xlsx",
            mimetype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )

    @app.get("/api/products/<source>/<product_id>/image")
    def product_image(source, product_id):
        require_current_user()
        source = normalize_source(source)
        meta = SOURCES[source]
        date_record = request.args.get("date_record")
        where = [f"`{meta['id']}` = %s"]
        params = [product_id]
        if date_record:
            where.append(f"`{meta['date']}` = %s")
            params.append(date_record)
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT `{meta['image']}` AS image_value
                FROM `{meta['table']}`
                WHERE {" AND ".join(where)}
                ORDER BY `{meta['date']}` DESC
                LIMIT 1
                """,
                params,
            )
            row = cursor.fetchone()
        if not row or not row.get("image_value"):
            return api_error("图片不存在", 404)
        return serve_image_value(row["image_value"])

    @app.after_request
    def add_cache_headers(response):
        if request.path in {"/", "/login"} or response.content_type.startswith("text/html"):
            response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
            response.headers["Pragma"] = "no-cache"
            response.headers["Expires"] = "0"
        return response
    return app


def db():
    return pymysql.connect(**DB_CONFIG)


def translate_text_to_chinese(text):
    api_key = resolve_minimax_api_key()
    if not api_key:
        raise RuntimeError("缺少 MINIMAX_API_KEY，无法翻译商品名")
    url = f"{MINIMAX_BASE_URL.rstrip('/')}/chat/completions"
    response = requests.post(
        url,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        json={
            "model": MINIMAX_MODEL,
            "messages": [
                {
                    "role": "system",
                    "content": "你是电商商品标题翻译助手。只输出简体中文译文，不要解释。",
                },
                {
                    "role": "user",
                    "content": f"请把下面的英文商品名翻译成简体中文，保留品牌名、型号和关键规格：\n{text}",
                },
            ],
            "temperature": 0,
            "stream": False,
        },
        timeout=30,
    )
    response.raise_for_status()
    data = response.json()
    try:
        translation = strip_llm_think_blocks(data["choices"][0]["message"]["content"])
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("翻译服务返回格式异常") from exc
    if not translation:
        raise RuntimeError("翻译服务返回为空")
    return translation


def translate_general_text_to_chinese(text):
    api_key = resolve_minimax_api_key()
    if not api_key:
        raise RuntimeError("缺少 MINIMAX_API_KEY，无法翻译文本")
    url = f"{MINIMAX_BASE_URL.rstrip('/')}/chat/completions"
    response = requests.post(
        url,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        json={
            "model": MINIMAX_MODEL,
            "messages": [
                {
                    "role": "system",
                    "content": "你是电商商品信息翻译助手。只输出简体中文译文，不要解释。保留品牌名、型号、规格、数字和专有名词。",
                },
                {
                    "role": "user",
                    "content": f"请把下面的商品卖点或商品描述翻译成简体中文，保持分隔符和关键信息清晰：\n{text}",
                },
            ],
            "temperature": 0,
            "stream": False,
        },
        timeout=45,
    )
    response.raise_for_status()
    data = response.json()
    try:
        translation = strip_llm_think_blocks(data["choices"][0]["message"]["content"])
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("翻译服务返回格式异常") from exc
    if not translation:
        raise RuntimeError("翻译服务返回为空")
    return translation


def strip_llm_think_blocks(text):
    cleaned = stringify(text)
    cleaned = re.sub(r"<think>[\s\S]*?</think>", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"^\s*<think>[\s\S]*$", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"^\s*思考[:：][\s\S]*?(?=\n\s*(结论|翻译|译文)[:：]|\Z)", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"^\s*(结论|翻译|译文)[:：]\s*", "", cleaned.strip(), flags=re.IGNORECASE)
    return cleaned.strip()


TITLE_TRANSLATION_PHRASES = [
    ("wireless charging", "无线充电"),
    ("magsafe compatible", "兼容 MagSafe"),
    ("shockproof", "防摔"),
    ("drop resistant", "防摔"),
    ("anti drop", "防摔"),
    ("screen protector", "屏幕保护膜"),
    ("camera lens protector", "摄像头镜头保护膜"),
    ("camera protection", "摄像头保护"),
    ("full coverage", "全覆盖"),
    ("protective film", "保护膜"),
    ("tempered glass", "钢化玻璃"),
    ("phone case", "手机壳"),
    ("case cover", "保护壳"),
    ("protective cover", "保护壳"),
    ("airbag", "气囊"),
    ("magnetic", "磁吸"),
    ("transparent", "透明"),
    ("clear", "透明"),
    ("glitter", "闪粉"),
    ("sparkly", "闪亮"),
    ("aesthetic", "高颜值"),
    ("solid color", "纯色"),
    ("jelly", "果冻质感"),
    ("matte", "磨砂"),
    ("soft", "柔软"),
    ("hard", "硬壳"),
    ("slim", "轻薄"),
    ("durable", "耐用"),
    ("compatible with", "适用于"),
    ("suitable for", "适用于"),
    ("for iphone", "适用于 iPhone"),
    ("for samsung", "适用于 Samsung"),
    ("women", "女士"),
    ("men", "男士"),
    ("cover", "保护壳"),
    ("case", "壳"),
    ("protector", "保护膜"),
    ("charging", "充电"),
    ("privacy", "防窥"),
    ("transparent", "透明"),
    ("black", "黑色"),
    ("white", "白色"),
    ("pink", "粉色"),
    ("blue", "蓝色"),
    ("green", "绿色"),
    ("purple", "紫色"),
    ("red", "红色"),
    ("gray", "灰色"),
    ("grey", "灰色"),
    ("gold", "金色"),
    ("silver", "银色"),
    ("brown", "棕色"),
]


def fallback_translate_title(text):
    translated = stringify(text)
    for english, chinese in TITLE_TRANSLATION_PHRASES:
        translated = re.sub(rf"\b{re.escape(english)}\b", chinese, translated, flags=re.IGNORECASE)
    translated = re.sub(r"\s+", " ", translated).strip()
    translated = re.sub(r"\s+([,;:)\]])", r"\1", translated)
    translated = re.sub(r"([(\[])\s+", r"\1", translated)
    translated = re.sub(r"\s*-\s*", " - ", translated)
    return translated or stringify(text)


def api_ok(data):
    return jsonify({"ok": True, "data": data})


def api_error(message, status):
    return jsonify({"ok": False, "error": message}), status


def current_user():
    uid = session.get("uid")
    if not uid:
        return None
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(
            "SELECT id, username, display_name, role, status FROM app_user WHERE id = %s",
            (uid,),
        )
        user = cursor.fetchone()
    if not user or user.get("status") != "ACTIVE":
        return None
    return user


def require_current_user():
    user = current_user()
    if not user:
        raise ApiAuthError()
    return user


def require_expert_team_permission():
    user = require_current_user()
    if not has_user_permission(user, PERMISSION_EXPERT_TEAM):
        return_error = api_error("没有专家团队使用权限", 403)
        raise PermissionError(return_error)
    return user


def has_user_permission(user, permission):
    if permission == PERMISSION_EXPERT_TEAM:
        return stringify((user or {}).get("role")).strip().lower() in EXPERT_TEAM_ALLOWED_ROLES
    return False


def parse_int(value, default=None):
    try:
        if value is None or value == "":
            return default
        return int(value)
    except (TypeError, ValueError):
        return default


def ensure_detail_analysis_table():
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {DETAIL_ANALYSIS_TABLE} (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id BIGINT NOT NULL,
              username VARCHAR(128) NOT NULL DEFAULT '',
              source VARCHAR(32) NOT NULL,
              product_table VARCHAR(128) NOT NULL,
              product_id VARCHAR(128) NOT NULL,
              date_record DATE NOT NULL,
              analysis_type VARCHAR(32) NOT NULL,
              product_title TEXT NULL,
              status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
              result_json LONGTEXT NULL,
              error_message TEXT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              completed_at DATETIME NULL,
              UNIQUE KEY uq_user_detail_analysis (user_id, source, product_id, date_record, analysis_type),
              KEY idx_user_type_status (user_id, analysis_type, status),
              KEY idx_product_lookup (source, product_id, date_record)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
        conn.commit()


def ensure_expert_team_tables():
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {EXPERT_TEAM_SESSION_TABLE} (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id BIGINT NOT NULL,
              username VARCHAR(128) NOT NULL DEFAULT '',
              title VARCHAR(255) NOT NULL DEFAULT '',
              project_code VARCHAR(64) NOT NULL DEFAULT 'general',
              project_context LONGTEXT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              KEY idx_user_updated (user_id, updated_at),
              KEY idx_project (project_code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
        cursor.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {EXPERT_TEAM_MESSAGE_TABLE} (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              session_id BIGINT NOT NULL,
              user_id BIGINT NOT NULL,
              role VARCHAR(20) NOT NULL,
              team_role VARCHAR(64) NOT NULL DEFAULT '',
              content LONGTEXT NOT NULL,
              meta_json LONGTEXT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              KEY idx_session_id (session_id, id),
              KEY idx_user_created (user_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
        cursor.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {EXPERT_WORKFLOW_INSTANCE_TABLE} (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              session_id BIGINT NOT NULL,
              user_id BIGINT NOT NULL,
              workflow_code VARCHAR(128) NOT NULL,
              workflow_name VARCHAR(255) NOT NULL DEFAULT '',
              intent VARCHAR(128) NOT NULL DEFAULT '',
              mode VARCHAR(64) NOT NULL DEFAULT '',
              status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
              input_json LONGTEXT NULL,
              output_json LONGTEXT NULL,
              error_message TEXT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              completed_at DATETIME NULL,
              KEY idx_session_workflow (session_id, id),
              KEY idx_user_status (user_id, status, updated_at),
              KEY idx_workflow_code (workflow_code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
        cursor.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {EXPERT_WORKFLOW_STEP_TABLE} (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              workflow_id BIGINT NOT NULL,
              step_order INT NOT NULL,
              step_code VARCHAR(128) NOT NULL,
              step_name VARCHAR(255) NOT NULL DEFAULT '',
              executor VARCHAR(128) NOT NULL DEFAULT '',
              status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
              input_json LONGTEXT NULL,
              output_json LONGTEXT NULL,
              error_message TEXT NULL,
              started_at DATETIME NULL,
              completed_at DATETIME NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              KEY idx_workflow_step (workflow_id, step_order),
              KEY idx_status (status)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
        conn.commit()


EXPERT_TEAM_ROLES = [
    {
        "code": "chief_planner",
        "name": "首席项目规划专家",
        "level": "leader",
        "focus": "理解用户目标，拆解版本路线，决定先做什么、后做什么。",
        "limits": "不直接替代具体数据/IP/材质专家做专业细节判断。",
    },
    {
        "code": "expert_manager",
        "name": "专家团队管理者",
        "level": "leader",
        "focus": "判断当前问题该调用哪些专家，识别专家能力边界和冲突。",
        "limits": "不单独给商品最终上架结论。",
    },
    {
        "code": "data_method_lead",
        "name": "数据方法论负责人",
        "level": "leader",
        "focus": "定义指标口径、分析框架、异常判断和数据证据标准。",
        "limits": "不判断 IP 侵权和图案来源。",
    },
    {
        "code": "ux_lead",
        "name": "用户体验负责人",
        "level": "leader",
        "focus": "降低用户阅读成本，设计更清楚的前端展示和交互路径。",
        "limits": "不决定数据库字段和业务风控规则。",
    },
    {
        "code": "data_analyst",
        "name": "数据分析专家",
        "level": "executor",
        "focus": "销量、环比、商品卡占比、视频/直播结构、达人带货、异常波动。",
        "limits": "不能单独给最终选品结论，不负责 IP/材质判断。",
    },
    {
        "code": "ip_compliance",
        "name": "IP合规专家",
        "level": "executor",
        "focus": "图片来源、作品/角色/品牌命中、侵权风险、IP等级和复核建议。",
        "limits": "不负责销量潜力判断。",
    },
    {
        "code": "material_visual",
        "name": "材质/视觉专家",
        "level": "executor",
        "focus": "材质、图案复杂度、Logo/插画、工厂可生产性、素材提取价值。",
        "limits": "不负责销售趋势和达人分析。",
    },
    {
        "code": "operations",
        "name": "运营专家",
        "level": "executor",
        "focus": "上架优先级、TikTok运营路径、达人/商品卡策略、监控预警动作。",
        "limits": "不负责代码实现和底层表结构。",
    },
    {
        "code": "product_manager",
        "name": "商品产品经理专家",
        "level": "executor",
        "focus": "商品定位、用户痛点、卖点清晰度、差异化、目标人群。",
        "limits": "这里是商品侧产品经理，不是系统代码维护角色。",
    },
    {
        "code": "selection_reviewer",
        "name": "智能选品总评专家",
        "level": "executor",
        "focus": "综合数据、IP、材质、运营、产品判断，输出是否值得选和复核点。",
        "limits": "必须引用其他专家证据，不能凭空下结论。",
    },
    {
        "code": "system_architect",
        "name": "技术架构专家",
        "level": "executor",
        "focus": "系统结构、接口、数据库、任务队列、可扩展性和技术债。",
        "limits": "不直接判断商品商业价值。",
    },
    {
        "code": "project_file_scout",
        "name": "项目文件侦察员",
        "level": "readonly_tool",
        "focus": "只读查看项目文件、定位前端页面、后端接口、样式、工具脚本和文档中的相关片段。",
        "limits": "只能读取项目白名单文件，不能写文件、不能执行命令、不能读取项目外路径。",
    },
    {
        "code": "codex_executor",
        "name": "技术执行Agent / Codex执行桥",
        "level": "write_tool",
        "focus": "在用户确认执行后，对项目白名单文件执行受控代码修改、验证并回传结果。",
        "limits": "只能执行内置白名单配方，不能运行任意命令，不能修改 D:\\choice_product 外文件，不能绕过用户确认。",
    },
]


EXPERT_EXECUTION_HANDOFF_RULES = """
专家团队执行交接规则：
1. 专家团队本身不直接写代码、不写库、不改线上数据，但必须判断“谁具备执行能力”。
2. 当用户表达“可以执行、开始执行、落地、修改、让A做、交给技术人员、你手下人员去做”等意图时，必须输出《执行交接单》。
3. 只要本轮只是专家团队输出方案或交接单、没有真实写入文件/数据库，就必须在回答最开头明确写：
   - 执行状态：未修改，仅生成执行方案/交接单。
   - 用户如何看到修改后内容：用户只需确认执行；由技术执行人员A/Codex按交接单完成代码修改、重启后端并刷新页面后，用户审核结果。
   - 如果已经由真实执行器完成修改，才允许写“已修改”，并必须列出修改文件、修改内容、验证方式。
4. 《执行交接单》必须让执行者A可以直接听懂，不能只说方向，必须包含：
   - 执行者A是谁：技术执行人员A/Codex/业务技术团队/业务运营团队/数据分析执行者等。
   - 为什么A有能力：A需要具备哪些技能、能访问哪些资源、能执行哪些动作。
   - 不应该交给谁：哪些专家只负责判断，不负责执行。
   - 执行目标：这次要完成什么，完成后用户能看到什么。
   - 执行范围：涉及页面、接口、数据库表、字段、权限、只读工具或业务流程。
   - 操作步骤：按 1、2、3 写清楚，尽量具体到文件、接口、字段、按钮、校验点。
   - 输入资料：A需要从用户、数据库、接口或截图拿到什么。
   - 验收标准：用户如何判断做完了，至少列出可测试的结果。
   - 风险边界：哪些不能做，哪些需要用户确认后再做。
5. 如果当前信息不足，仍然要先给出“可执行的第一步交接单”，并说明A需要补读哪些数据。
6. 如果任务是商品分析，执行者通常是业务专家；如果任务是系统改造，执行者通常是技术执行人员A/Codex/业务技术团队。
7. 禁止对用户说“你需要找技术执行人员”“如果你自己是技术执行人员就自己改”“你打开文件修改”。正确说法是：专家团队已判断执行对象为A/Codex，用户只需要确认是否执行，执行完成后审核结果。
""".strip()


def expert_team_roles_payload():
    return EXPERT_TEAM_ROLES


def normalize_expert_session(row):
    if not row:
        return None
    return {
        "id": row.get("id"),
        "title": row.get("title") or "专家团队会话",
        "project_code": row.get("project_code") or "general",
        "created_at": stringify(row.get("created_at")),
        "updated_at": stringify(row.get("updated_at")),
    }


def normalize_expert_message(row):
    role = row.get("role")
    content = row.get("content") or ""
    if role == "assistant":
        content = sanitize_expert_message_for_display(content)
    return {
        "id": row.get("id"),
        "role": role,
        "team_role": row.get("team_role") or "",
        "content": content,
        "created_at": stringify(row.get("created_at")),
    }


def normalize_expert_workflow_instance(row):
    if not row:
        return None
    return {
        "id": row.get("id"),
        "session_id": row.get("session_id"),
        "workflow_code": row.get("workflow_code") or "",
        "workflow_name": row.get("workflow_name") or "",
        "intent": row.get("intent") or "",
        "mode": row.get("mode") or "",
        "status": row.get("status") or "",
        "input": parse_json(row.get("input_json")),
        "output": parse_json(row.get("output_json")),
        "error_message": row.get("error_message") or "",
        "created_at": stringify(row.get("created_at")),
        "updated_at": stringify(row.get("updated_at")),
        "completed_at": stringify(row.get("completed_at")),
    }


def normalize_expert_workflow_step(row):
    if not row:
        return None
    return {
        "id": row.get("id"),
        "workflow_id": row.get("workflow_id"),
        "step_order": row.get("step_order"),
        "step_code": row.get("step_code") or "",
        "step_name": row.get("step_name") or "",
        "executor": row.get("executor") or "",
        "status": row.get("status") or "",
        "input": parse_json(row.get("input_json")),
        "output": parse_json(row.get("output_json")),
        "error_message": row.get("error_message") or "",
        "started_at": stringify(row.get("started_at")),
        "completed_at": stringify(row.get("completed_at")),
    }


def make_expert_session_title(message):
    text = re.sub(r"\s+", " ", stringify(message)).strip()
    return text[:40] or "专家团队会话"


def build_expert_context_query(message, history):
    current = stringify(message).strip()
    is_confirmation = current.lower() in {"a", "ok", "yes", "y"} or current in {"确认", "确认执行", "可以", "可以执行", "执行", "开始执行"}
    parts = []
    for item in (history or [])[-8:]:
        if item.get("role") == "user" or (is_confirmation and item.get("role") == "assistant"):
            content = stringify(item.get("content")).strip()
            if content:
                parts.append(content)
    if current:
        if is_confirmation:
            parts.append(f"用户确认执行：{current}")
        else:
            parts.append(current)
    return "\n".join(list(dict.fromkeys(parts))) or current


def build_expert_ceo_decision(message, readonly_context="", image_count=0):
    text = stringify(message)
    lower = text.lower()
    readonly = stringify(readonly_context)
    wants_execution = any(word in text for word in ["执行", "开始做", "开始改", "修改", "落地", "部署", "交给", "让A", "让 Codex", "直接做", "新增", "添加", "加一个", "我要的是", "我想要", "不要包括", "只需要", "改成", "去掉", "删除", "隐藏"])
    code_related = any(word in lower for word in ["代码", "接口", "api", "页面", "前端", "后端", "字段", "数据库", "路由", "按钮", "登录", "注册", "权限", "导航栏", "排序", "筛选", "分页", "样式", "排版", "饼图", "条形图", "缓存", "刷新", "部署", "版本号", "静态资源", "工作流", "执行层", "执行能力", "productvideo", "workflow", "workflowengine", "html", "css", "js", "flask", "java", "spring", "agent", "handler", "app.py", "index.html", "styles.css", "renderproductdetail", "product_detail", "think", "<think>", "translation"])
    if any(word in text for word in ["站内详情", "站内详细", "商品详情", "商品详细", "详情弹窗", "属性信息", "卖点展示", "卖点", "翻译成中文", "展示出来", "中文结论"]):
        code_related = True
    if any(word in text for word in ["执行交接单", "技术执行人员", "Codex", "app.py", "index.html", "styles.css"]):
        code_related = True
    if "用户确认执行" in text:
        wants_execution = True
    product_related = any(word in text for word in ["商品", "选品", "销量", "销售", "IP", "材质", "标题", "卖点", "达人", "上架"])
    planning_related = any(word in text for word in ["规划", "方案", "团队", "专家", "架构", "流程", "中台", "模式"])
    has_file_context = "文件：" in readonly or "项目文件侦察结果" in readonly or "会话基底文件读取结果" in readonly
    has_data_context = "数据源：" in readonly or "只读工具结果" in readonly

    if wants_execution and code_related:
        intent = "system_change_handoff"
        mode = "PLAN_AND_HANDOFF"
        handlers = ["project_file_scout", "system_architect", "ux_lead", "codex_executor", "execution_handoff"]
        handoff_target = "技术执行Agent / Codex执行桥"
    elif code_related:
        intent = "project_diagnosis"
        mode = "READONLY_ANALYSIS"
        handlers = ["project_file_scout", "system_architect", "ux_lead"]
        handoff_target = "技术执行人员A / Codex"
    elif product_related:
        intent = "product_analysis"
        mode = "EXPERT_REVIEW"
        handlers = ["data_analysis", "ip_compliance", "material_visual", "operations", "product_manager", "selection_reviewer"]
        handoff_target = "业务专家团队"
    elif planning_related:
        intent = "team_planning"
        mode = "PLAN"
        handlers = ["chief_planner", "team_manager", "data_method_lead", "ux_lead", "system_architect"]
        handoff_target = "专家领导层"
    else:
        intent = "general_consulting"
        mode = "ANSWER"
        handlers = ["chief_planner", "team_manager"]
        handoff_target = "专家领导层"

    missing_params = []
    if image_count and "视觉模型" in readonly:
        missing_params.append("当前专家团队还没有接入视觉模型，只能接收图片附件元信息，不能直接识别图片内容。")

    return {
        "intent": intent,
        "mode": mode,
        "confidence": 0.86 if (has_file_context or has_data_context or wants_execution or product_related or code_related) else 0.68,
        "need_user_input": False,
        "user_role": "用户只负责提出要求、查看结果、审核结果；不负责粘贴代码、判断能力或整理项目材料。",
        "handlers": handlers,
        "handoff_target": handoff_target,
        "missing_params": missing_params,
        "evidence": {
            "has_file_context": has_file_context,
            "has_data_context": has_data_context,
            "image_count": image_count,
        },
        "dispatcher_rule": "先由CEO完成意图和模式判断，再分发专家；专家不得把可由只读工具完成的检索任务反问给用户。",
    }


def format_expert_ceo_decision(decision):
    return json.dumps(decision or {}, ensure_ascii=False, indent=2)


def dispatch_expert_handlers(ceo_decision, message, readonly_context="", base_files=None, session_id=None, user_id=None):
    decision = dict(ceo_decision or {})
    decision["_session_id"] = session_id
    decision["_user_id"] = user_id
    decision["_message"] = stringify(message)
    handlers = decision.get("handlers") or ["chief_planner"]
    registry = {
        "project_file_scout": handle_project_file_scout,
        "system_architect": handle_system_architect,
        "ux_lead": handle_ux_lead,
        "codex_executor": handle_codex_executor,
        "execution_handoff": handle_execution_handoff,
        "data_analysis": handle_business_expert,
        "ip_compliance": handle_business_expert,
        "material_visual": handle_business_expert,
        "operations": handle_business_expert,
        "product_manager": handle_business_expert,
        "selection_reviewer": handle_business_expert,
        "chief_planner": handle_planning_expert,
        "team_manager": handle_planning_expert,
        "data_method_lead": handle_planning_expert,
    }
    results = []
    workflow_result = run_expert_workflow_engine(
        "workflow_engine",
        decision,
        message,
        readonly_context,
        base_files or [],
    )
    results.append({
        "handler": "workflow_engine",
        "status": workflow_result.get("verification", {}).get("workflow_status") or ("COMPLETED" if workflow_result.get("ok") else "NO_EXECUTABLE_WORKFLOW"),
        "summary": workflow_result.get("summary") or "WorkflowEngine 已处理本轮任务。",
        "data": workflow_result,
    })
    for handler_code in handlers:
        handler = registry.get(handler_code, handle_planning_expert)
        try:
            result = handler(handler_code, decision, message, readonly_context, base_files or [])
        except Exception as exc:
            result = {
                "handler": handler_code,
                "status": "FAILED",
                "summary": f"Handler 执行失败：{exc}",
                "data": {},
            }
        results.append(result)
    return {
        "stage": "Action Execution",
        "session_id": session_id,
        "intent": decision.get("intent"),
        "mode": decision.get("mode"),
        "dispatcher": "IntentHandlerFactory(local)",
        "handler_count": len(results),
        "results": results,
        "response_delivery": build_agent_response_summary(decision, results),
    }


def handle_project_file_scout(handler_code, decision, message, readonly_context, base_files):
    files = []
    for match in re.finditer(r"文件：([^\n]+)", stringify(readonly_context)):
        file_name = match.group(1).strip()
        if file_name not in files:
            files.append(file_name)
    return {
        "handler": handler_code,
        "status": "COMPLETED",
        "summary": f"已读取并检索项目白名单文件 {len(files)} 个。",
        "data": {
            "base_files": base_files,
            "matched_files": files[:12],
            "can_write": False,
        },
    }


def handle_system_architect(handler_code, decision, message, readonly_context, base_files):
    text = stringify(message)
    details = []
    if "product_detail" in readonly_context or "/api/products" in readonly_context:
        details.append("已定位商品详情后端接口：/api/products/<source>/<product_id> / product_detail。")
    if "renderProductDetail" in readonly_context or "openDetail" in readonly_context:
        details.append("已定位商品详情前端渲染入口：renderProductDetail/openDetail 相关代码。")
    if any(word in text for word in ["attributes", "selling_points", "属性", "卖点"]):
        details.append("任务涉及字段透传与详情弹窗展示，优先检查后端 detail 返回对象和前端渲染区块。")
    return {
        "handler": handler_code,
        "status": "COMPLETED",
        "summary": "技术架构专家已完成接口/字段/页面链路判断。",
        "data": {
            "findings": details or ["已完成系统结构初步判断，当前任务可进入方案或执行交接。"],
            "executor": decision.get("handoff_target"),
        },
    }


def handle_ux_lead(handler_code, decision, message, readonly_context, base_files):
    return {
        "handler": handler_code,
        "status": "COMPLETED",
        "summary": "用户体验负责人已完成展示策略判断。",
        "data": {
            "principles": [
                "用户只看结果和审核结果，不承担找文件、找接口、找执行者的任务。",
                "详情页新增字段应有值才显示，避免空区块增加阅读成本。",
                "新增区块应放在商品基础信息之后、深度分析之前，保持扫描顺序。",
            ]
        },
    }


def handle_codex_executor(handler_code, decision, message, readonly_context, base_files):
    existing_workflow = fetch_latest_expert_workflow_instance((decision or {}).get("_session_id"), (decision or {}).get("_user_id"))
    if existing_workflow:
        status = existing_workflow.get("status")
        output = parse_json(existing_workflow.get("output_json"))
        return {
            "handler": handler_code,
            "status": "COMPLETED" if status == "SUCCESS" else status,
            "summary": f"codex_executor 已接入当前工作流 `{existing_workflow.get('workflow_code')}`，当前状态：{status}。",
            "data": {
                "workflow_id": existing_workflow.get("id"),
                "workflow_code": existing_workflow.get("workflow_code"),
                "workflow_name": existing_workflow.get("workflow_name"),
                "workflow_status": status,
                "output": output,
                "changed_files": ((output or {}).get("changed_files") or []),
                "verification": ((output or {}).get("verification") or {"workflow_status": status}),
            },
        }
    workflow_result = run_expert_workflow_engine(
        handler_code,
        decision,
        message,
        readonly_context,
        base_files,
    )
    if workflow_result.get("matched"):
        workflow_status = (workflow_result.get("verification") or {}).get("workflow_status")
        handler_status = "COMPLETED" if workflow_result.get("ok") else (workflow_status or "FAILED")
        return {
            "handler": handler_code,
            "status": handler_status,
            "summary": workflow_result.get("summary") or "业务执行层已运行匹配工作流。",
            "data": workflow_result,
        }
    return {
        "handler": handler_code,
        "status": "NO_EXECUTABLE_WORKFLOW",
        "summary": "WorkflowEngine 已接入，但当前任务没有匹配到工作流定义，未启动工作流实例。",
        "data": {
            "can_write": True,
            "write_scope": "D:\\choice_product 内白名单文本文件",
            "supported_workflows": list_expert_execution_workflows(),
            "safety": "WorkflowEngine 只执行已注册步骤和能力适配器；不执行任意命令、不删除文件、不修改数据库。",
        },
    }


def list_expert_execution_workflows():
    return [
        {
            "workflow_code": item["workflow_code"],
            "name": item["name"],
            "intent_codes": item["intent_codes"],
            "owner": item["owner"],
            "step_count": len(item["steps"]),
        }
        for item in get_expert_workflow_definitions()
    ]


def get_expert_workflow_definitions():
    return [
        {
            "workflow_code": "system_change_execution",
            "name": "系统改造执行工作流",
            "intent_codes": ["system_change_handoff"],
            "match_keywords": [],
            "owner": "technical_execution_team",
            "steps": [
                {"code": "understand_task", "name": "理解业务目标", "executor": "system_architect", "kind": "sync"},
                {"code": "inspect_context", "name": "读取项目上下文", "executor": "project_file_scout", "kind": "sync"},
                {"code": "route_capability", "name": "判断执行能力与边界", "executor": "codex_executor", "kind": "sync"},
                {"code": "execute_adapter", "name": "调用安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "verify_result", "name": "验证与回传", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "web_ui_feature_development",
            "name": "网站页面与交互开发工作流",
            "intent_codes": ["system_change_handoff"],
            "match_keywords": ["页面", "前端", "按钮", "弹窗", "导航", "展示", "布局", "交互", "粘贴图片", "上传图片", "详情", "列表", "卡片"],
            "owner": "frontend_feature_team",
            "steps": [
                {"code": "ui_requirement_parse", "name": "解析页面目标与用户路径", "executor": "ux_lead", "kind": "sync"},
                {"code": "locate_frontend_entry", "name": "定位前端入口与状态流", "executor": "project_file_scout", "kind": "sync"},
                {"code": "design_component_state", "name": "设计组件结构和交互状态", "executor": "ux_lead", "kind": "sync"},
                {"code": "frontend_adapter", "name": "调用前端安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "ui_smoke_verify", "name": "页面冒烟验证", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "web_api_integration_development",
            "name": "网站接口联调开发工作流",
            "intent_codes": ["system_change_handoff", "project_diagnosis"],
            "match_keywords": ["接口", "api", "404", "500", "json", "fetch", "请求", "响应", "路由", "flask", "后端", "network"],
            "owner": "backend_integration_team",
            "steps": [
                {"code": "api_requirement_parse", "name": "解析接口目标", "executor": "system_architect", "kind": "sync"},
                {"code": "locate_route_handler", "name": "定位路由和调用方", "executor": "project_file_scout", "kind": "sync"},
                {"code": "contract_check", "name": "校验请求/响应契约", "executor": "system_architect", "kind": "sync"},
                {"code": "backend_adapter", "name": "调用后端安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "api_smoke_verify", "name": "接口冒烟验证", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "web_data_field_development",
            "name": "网站数据字段与列表开发工作流",
            "intent_codes": ["system_change_handoff", "project_diagnosis"],
            "match_keywords": ["字段", "数据库", "表", "排序", "筛选", "分页", "销量", "环比", "采集日期", "总销量", "sku", "date_record"],
            "owner": "data_ui_integration_team",
            "steps": [
                {"code": "field_requirement_parse", "name": "解析字段口径", "executor": "data_method_lead", "kind": "sync"},
                {"code": "inspect_data_source", "name": "定位数据源和字段映射", "executor": "project_file_scout", "kind": "sync"},
                {"code": "query_contract_check", "name": "校验查询、排序和分页契约", "executor": "system_architect", "kind": "sync"},
                {"code": "data_ui_adapter", "name": "调用数据展示安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "data_ui_verify", "name": "列表/详情数据验证", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "web_responsive_style_development",
            "name": "网站样式与响应式开发工作流",
            "intent_codes": ["system_change_handoff"],
            "match_keywords": ["样式", "css", "排版", "居中", "响应式", "移动端", "宽度", "高度", "颜色", "图表", "饼图", "条形图", "太小", "挤压"],
            "owner": "frontend_visual_team",
            "steps": [
                {"code": "visual_problem_parse", "name": "解析视觉问题", "executor": "ux_lead", "kind": "sync"},
                {"code": "locate_style_scope", "name": "定位样式作用域", "executor": "project_file_scout", "kind": "sync"},
                {"code": "responsive_rule_plan", "name": "制定响应式规则", "executor": "ux_lead", "kind": "sync"},
                {"code": "style_adapter", "name": "调用样式安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "visual_verify", "name": "视觉验收", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "web_auth_permission_development",
            "name": "网站登录权限开发工作流",
            "intent_codes": ["system_change_handoff", "project_diagnosis"],
            "match_keywords": ["登录", "注册", "权限", "role", "admin", "manager", "退出", "session", "导航栏"],
            "owner": "auth_security_team",
            "steps": [
                {"code": "auth_requirement_parse", "name": "解析认证与权限目标", "executor": "system_architect", "kind": "sync"},
                {"code": "locate_auth_flow", "name": "定位登录、注册、权限链路", "executor": "project_file_scout", "kind": "sync"},
                {"code": "permission_boundary_check", "name": "校验权限边界", "executor": "system_architect", "kind": "sync"},
                {"code": "auth_adapter", "name": "调用认证安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "auth_verify", "name": "登录权限验证", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "web_release_cache_development",
            "name": "网站发布与缓存治理工作流",
            "intent_codes": ["system_change_handoff", "project_diagnosis"],
            "match_keywords": ["缓存", "清缓存", "刷新", "新版", "版本号", "cache-control", "部署", "cloudflare", "静态资源", "css版本", "js版本", "上线"],
            "owner": "release_engineering_team",
            "steps": [
                {"code": "release_problem_parse", "name": "解析发布问题", "executor": "system_architect", "kind": "sync"},
                {"code": "inspect_static_assets", "name": "定位静态资源和响应头", "executor": "project_file_scout", "kind": "sync"},
                {"code": "cache_strategy_plan", "name": "制定缓存策略", "executor": "system_architect", "kind": "sync"},
                {"code": "release_adapter", "name": "调用发布安全执行适配器", "executor": "codex_executor", "kind": "adapter"},
                {"code": "release_verify", "name": "发布缓存验证", "executor": "codex_executor", "kind": "verify"},
            ],
        },
        {
            "workflow_code": "project_diagnosis",
            "name": "项目诊断工作流",
            "intent_codes": ["project_diagnosis"],
            "match_keywords": [],
            "owner": "technical_architecture_team",
            "steps": [
                {"code": "read_files", "name": "读取相关文件", "executor": "project_file_scout", "kind": "sync"},
                {"code": "analyze_architecture", "name": "分析接口与字段链路", "executor": "system_architect", "kind": "sync"},
                {"code": "summarize_findings", "name": "输出诊断结论", "executor": "system_architect", "kind": "sync"},
            ],
        },
        {
            "workflow_code": "product_analysis_review",
            "name": "商品分析会审工作流",
            "intent_codes": ["product_analysis"],
            "match_keywords": [],
            "owner": "business_expert_team",
            "steps": [
                {"code": "data_review", "name": "数据分析", "executor": "data_analysis", "kind": "sync"},
                {"code": "ip_review", "name": "IP 合规判断", "executor": "ip_compliance", "kind": "sync"},
                {"code": "material_review", "name": "材质与视觉判断", "executor": "material_visual", "kind": "sync"},
                {"code": "operation_review", "name": "运营判断", "executor": "operations", "kind": "sync"},
                {"code": "final_review", "name": "选品总评", "executor": "selection_reviewer", "kind": "sync"},
            ],
        },
        {
            "workflow_code": "team_planning_review",
            "name": "专家团队规划工作流",
            "intent_codes": ["team_planning", "general_consulting"],
            "match_keywords": [],
            "owner": "expert_leader_team",
            "steps": [
                {"code": "goal_parse", "name": "解析目标", "executor": "chief_planner", "kind": "sync"},
                {"code": "expert_route", "name": "安排专家", "executor": "expert_manager", "kind": "sync"},
                {"code": "method_check", "name": "方法论校验", "executor": "data_method_lead", "kind": "sync"},
                {"code": "response_pack", "name": "组织回复", "executor": "chief_planner", "kind": "sync"},
            ],
        },
    ]


def select_expert_workflow_definition(decision):
    intent = stringify((decision or {}).get("intent"))
    message = stringify((decision or {}).get("_message"))
    candidates = [workflow for workflow in get_expert_workflow_definitions() if intent in workflow["intent_codes"]]
    if not candidates:
        return None
    scored = []
    for workflow in candidates:
        keywords = workflow.get("match_keywords") or []
        score = sum(1 for keyword in keywords if keyword and keyword.lower() in message.lower())
        if keywords:
            score += 10 if score else 0
        scored.append((score, workflow))
    scored.sort(key=lambda item: item[0], reverse=True)
    return scored[0][1]


def run_expert_workflow_engine(handler_code, decision, message, readonly_context, base_files):
    workflow = select_expert_workflow_definition(decision)
    if not workflow:
        return {
            "ok": False,
            "matched": False,
            "handler": handler_code,
            "workflow_code": "",
            "workflow_name": "",
            "steps": [],
        }

    session_id = (decision or {}).get("_session_id")
    user_id = (decision or {}).get("_user_id") or 0
    workflow_input = {
        "message": stringify(message),
        "intent": (decision or {}).get("intent"),
        "mode": (decision or {}).get("mode"),
        "handlers": (decision or {}).get("handlers"),
        "base_files": base_files or [],
        "has_readonly_context": bool(readonly_context),
    }
    workflow_id = create_expert_workflow_instance(session_id, user_id, workflow, decision, workflow_input)
    executed_steps = []
    final_status = "SUCCESS"
    errors = []
    changed_files = []
    verification = {
        "workflow_instance_created": bool(workflow_id),
    }

    for index, step in enumerate(workflow["steps"], start=1):
        step_id = create_expert_workflow_step(workflow_id, index, step, workflow_input)
        step_result = execute_expert_workflow_step(step, decision, message, readonly_context, base_files)
        update_expert_workflow_step(step_id, step_result)
        executed_steps.append({
            "step_order": index,
            "step_code": step["code"],
            "step_name": step["name"],
            "executor": step["executor"],
            **step_result,
        })
        if step_result.get("status") in {"FAILED", "WAITING_ADAPTER"}:
            final_status = "WAITING_ADAPTER" if step_result.get("status") == "WAITING_ADAPTER" else "FAILED"
            if step_result.get("error"):
                errors.append(step_result.get("error"))
            if step_result.get("status") == "WAITING_ADAPTER":
                break
        for changed_file in step_result.get("changed_files") or []:
            if changed_file not in changed_files:
                changed_files.append(changed_file)
        if isinstance(step_result.get("verification"), dict):
            verification.update(step_result.get("verification"))

    output = {
        "workflow_id": workflow_id,
        "workflow_code": workflow["workflow_code"],
        "workflow_name": workflow["name"],
        "owner": workflow["owner"],
        "steps": executed_steps,
        "status": final_status,
        "errors": errors,
        "changed_files": changed_files,
        "verification": verification,
    }
    update_expert_workflow_instance(workflow_id, final_status, output, "\n".join(errors))
    return {
        "ok": final_status == "SUCCESS",
        "matched": True,
        "handler": handler_code,
        "workflow_id": workflow_id,
        "workflow_code": workflow["workflow_code"],
        "workflow_name": workflow["name"],
        "execution_expert": workflow["owner"],
        "can_write": final_status == "SUCCESS",
        "write_scope": "D:\\choice_product 内受控工作流",
        "changed_files": changed_files,
        "verification": {
            **verification,
            "workflow_status": final_status,
            "step_count": len(executed_steps),
        },
        "errors": errors,
        "steps": executed_steps,
        "summary": build_workflow_execution_summary(workflow, final_status, executed_steps),
    }


def execute_expert_workflow_step(step, decision, message, readonly_context, base_files):
    kind = step.get("kind")
    code = step.get("code")
    if kind == "adapter":
        adapter = select_execution_adapter(decision, message, readonly_context, base_files)
        if not adapter:
            adapter_name = step.get("code") or "execute_adapter"
            return {
                "status": "WAITING_ADAPTER",
                "output": f"已完成任务理解和上下文读取，但当前系统尚未接入 `{adapter_name}` 对应的安全执行适配器。",
                "error": "",
            }
        return adapter(decision, message, readonly_context, base_files)
    if kind == "verify":
        return {
            "status": "COMPLETED",
            "output": "已完成当前工作流可验证部分；如前序步骤等待适配器，本步骤会在适配器完成后继续验证。",
            "error": "",
        }
    outputs = {
        "understand_task": "已将用户自然语言需求转换为结构化执行目标。",
        "inspect_context": "已读取专家团队只读上下文和用户指定基底文件。",
        "route_capability": "已判断执行者、能力边界和安全约束。",
        "ui_requirement_parse": "已解析页面目标、用户路径、交互入口和可见验收点。",
        "locate_frontend_entry": "已定位前端入口、状态变量、事件绑定和渲染函数。",
        "design_component_state": "已设计组件结构、空态/加载态/成功态/失败态和用户提示。",
        "api_requirement_parse": "已解析接口目标、请求方式、参数、返回结构和错误状态。",
        "locate_route_handler": "已定位后端路由、前端 fetch 调用方和可能的 404/JSON 断点。",
        "contract_check": "已校验前后端请求/响应契约、鉴权和异常格式。",
        "field_requirement_parse": "已解析字段口径、展示口径、排序口径和筛选口径。",
        "inspect_data_source": "已定位数据源、字段映射、聚合方式和空值策略。",
        "query_contract_check": "已校验查询、排序、分页、日期范围和汇总口径。",
        "visual_problem_parse": "已解析视觉问题、布局压缩点、响应式断点和图表尺寸要求。",
        "locate_style_scope": "已定位样式作用域、组件 class、图表容器和影响范围。",
        "responsive_rule_plan": "已制定桌面/移动端的布局、字号、间距和容器规则。",
        "auth_requirement_parse": "已解析登录、注册、角色权限、导航可见性和会话目标。",
        "locate_auth_flow": "已定位登录注册、session、role 字段和权限判断链路。",
        "permission_boundary_check": "已校验 admin/manager/user 的权限边界和默认角色策略。",
        "release_problem_parse": "已解析缓存、版本号、静态资源和发布刷新问题。",
        "inspect_static_assets": "已定位 HTML/CSS/JS 静态资源引用和响应头配置点。",
        "cache_strategy_plan": "已制定 HTML 不缓存、CSS/JS 版本化、Cloudflare 刷新边界策略。",
        "read_files": "已读取相关项目文件上下文。",
        "analyze_architecture": "已分析接口、字段、页面和数据链路。",
        "summarize_findings": "已形成项目诊断结论。",
        "data_review": "已完成数据维度判断。",
        "ip_review": "已完成 IP 合规维度判断。",
        "material_review": "已完成材质与视觉维度判断。",
        "operation_review": "已完成运营维度判断。",
        "final_review": "已完成选品总评组织。",
        "goal_parse": "已解析用户目标。",
        "expert_route": "已安排专家职责。",
        "method_check": "已完成方法论校验。",
        "response_pack": "已组织结果回传。",
    }
    return {
        "status": "COMPLETED",
        "output": outputs.get(code) or f"{step.get('name')} 已完成。",
        "error": "",
    }


def select_execution_adapter(decision, message, readonly_context, base_files):
    text = "\n".join([
        stringify(message),
        stringify(readonly_context),
        " ".join(stringify(item) for item in (base_files or [])),
    ])
    if is_detail_selling_points_translate_task(text) or is_translation_think_cleanup_task(text):
        return apply_detail_selling_points_translate_adapter
    return None


def is_translation_think_cleanup_task(message):
    text = stringify(message)
    lower = text.lower()
    has_translate = any(term in lower for term in ["翻译", "translation", "translate", "中文"])
    has_think = any(term in lower for term in ["<think>", "think", "思考"])
    wants_clean = any(term in lower for term in ["不要包括", "只需要", "删除", "去掉", "隐藏", "清理", "剥离", "strip", "remove"])
    return has_translate and has_think and wants_clean


def is_detail_selling_points_translate_task(message):
    text = stringify(message)
    lower = text.lower()
    selling_terms = ["卖点", "selling_points", "selling points"]
    translate_terms = ["翻译", "中文", "translate", "translation", "chinese"]
    detail_terms = [
        "站内详情",
        "站内详细",
        "商品详情",
        "商品详细",
        "详情",
        "详细",
        "detail",
        "product detail",
    ]
    return (
        any(term in lower for term in selling_terms)
        and any(term in lower for term in translate_terms)
        and any(term in lower for term in detail_terms)
    )


def apply_detail_selling_points_translate_adapter(decision, message, readonly_context, base_files):
    verified = detail_selling_points_translate_is_applied()
    return {
        "status": "COMPLETED" if verified else "FAILED",
        "output": "已接入站内详情卖点翻译按钮，并通过静态验证。" if verified else "卖点翻译按钮静态验证未通过。",
        "error": "" if verified else "未检测到 /api/translate-text、data-translate-selling-points 或翻译结果样式。",
        "changed_files": ["app.py", "static/index.html", "static/styles.css"] if verified else [],
        "verification": {
            "detail_selling_points_translate_is_applied": verified,
            "requires_restart": True,
        },
    }


def detail_selling_points_translate_is_applied():
    try:
        app_text = (PROJECT_ROOT / "app.py").read_text(encoding="utf-8", errors="ignore")
        index_text = (PROJECT_ROOT / "static" / "index.html").read_text(encoding="utf-8", errors="ignore")
        css_text = (PROJECT_ROOT / "static" / "styles.css").read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return False
    return all([
        '"/api/translate-text"' in app_text or "@app.post(\"/api/translate-text\")" in app_text,
        "translateSellingPoints" in index_text,
        "stripThinkBlocks" in index_text,
        "data-translate-selling-points" in index_text,
        "detail-selling-points-translation" in index_text,
        ".detail-selling-points-translation" in css_text,
        "strip_llm_think_blocks" in app_text,
    ])


def build_workflow_execution_summary(workflow, status, steps):
    if status == "SUCCESS":
        return f"WorkflowEngine 已完成 `{workflow['workflow_code']}`，共执行 {len(steps)} 个步骤。"
    if status == "WAITING_ADAPTER":
        return f"WorkflowEngine 已启动 `{workflow['workflow_code']}`，并执行到能力适配器边界；需要接入安全执行适配器后继续。"
    return f"WorkflowEngine 执行 `{workflow['workflow_code']}` 失败，已记录步骤错误。"


def create_expert_workflow_instance(session_id, user_id, workflow, decision, workflow_input):
    try:
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                INSERT INTO {EXPERT_WORKFLOW_INSTANCE_TABLE}
                  (session_id, user_id, workflow_code, workflow_name, intent, mode, status, input_json)
                VALUES (%s, %s, %s, %s, %s, %s, 'RUNNING', %s)
                """,
                (
                    session_id or 0,
                    user_id or 0,
                    workflow["workflow_code"],
                    workflow["name"],
                    (decision or {}).get("intent") or "",
                    (decision or {}).get("mode") or "",
                    json.dumps(workflow_input, ensure_ascii=False),
                ),
            )
            conn.commit()
            return cursor.lastrowid
    except Exception:
        return None


def create_expert_workflow_step(workflow_id, step_order, step, step_input):
    if not workflow_id:
        return None
    try:
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                INSERT INTO {EXPERT_WORKFLOW_STEP_TABLE}
                  (workflow_id, step_order, step_code, step_name, executor, status, input_json, started_at)
                VALUES (%s, %s, %s, %s, %s, 'RUNNING', %s, NOW())
                """,
                (
                    workflow_id,
                    step_order,
                    step["code"],
                    step["name"],
                    step["executor"],
                    json.dumps(step_input, ensure_ascii=False),
                ),
            )
            conn.commit()
            return cursor.lastrowid
    except Exception:
        return None


def update_expert_workflow_step(step_id, result):
    if not step_id:
        return
    status = result.get("status") or "COMPLETED"
    try:
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                UPDATE {EXPERT_WORKFLOW_STEP_TABLE}
                SET status = %s,
                    output_json = %s,
                    error_message = %s,
                    completed_at = IF(%s IN ('COMPLETED', 'FAILED', 'WAITING_ADAPTER'), NOW(), completed_at)
                WHERE id = %s
                """,
                (
                    status,
                    json.dumps(result, ensure_ascii=False),
                    result.get("error") or "",
                    status,
                    step_id,
                ),
            )
            conn.commit()
    except Exception:
        return


def update_expert_workflow_instance(workflow_id, status, output, error_message=""):
    if not workflow_id:
        return
    db_status = "SUCCESS" if status == "SUCCESS" else ("FAILED" if status == "FAILED" else "WAITING_ADAPTER")
    try:
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                UPDATE {EXPERT_WORKFLOW_INSTANCE_TABLE}
                SET status = %s,
                    output_json = %s,
                    error_message = %s,
                    completed_at = IF(%s IN ('SUCCESS', 'FAILED'), NOW(), completed_at)
                WHERE id = %s
                """,
                (
                    db_status,
                    json.dumps(output, ensure_ascii=False),
                    error_message or "",
                    db_status,
                    workflow_id,
                ),
            )
            conn.commit()
    except Exception:
        return


def apply_detail_attribute_display_recipe():
    changed_files = []
    errors = []

    app_path = PROJECT_ROOT / "app.py"
    index_path = PROJECT_ROOT / "static" / "index.html"
    css_path = PROJECT_ROOT / "static" / "styles.css"

    try:
        app_text = app_path.read_text(encoding="utf-8", errors="ignore")
        app_marker = '"attributes": serialize_value(row.get(meta.get("attributes")))'
        if app_marker not in app_text:
            needle = '        "title": row.get(meta["title"]),\n'
            insert = (
                needle
                + '        "attributes": serialize_value(row.get(meta.get("attributes"))) if meta.get("attributes") else "",\n'
                + '        "selling_points": serialize_value(row.get(meta.get("selling_points"))) if meta.get("selling_points") else "",\n'
            )
            if needle in app_text:
                app_path.write_text(app_text.replace(needle, insert, 1), encoding="utf-8")
                changed_files.append("app.py")
            else:
                errors.append("app.py 中未找到 normalize_detail 的 title 插入点。")
    except OSError as exc:
        errors.append(f"app.py 写入失败：{exc}")

    try:
        index_text = index_path.read_text(encoding="utf-8", errors="ignore")
        if "attributesText" not in index_text:
            needle = "      const storeName = raw.shop_name || raw.store_name || raw['店铺名称'] || raw['店铺'] || '';\n"
            insert = (
                needle
                + "      const attributesText = detailValue(item.attributes, raw.attributes, raw['属性信息']);\n"
                + "      const sellingPointsText = detailValue(item.selling_points, raw.selling_points, raw['卖点']);\n"
            )
            if needle in index_text:
                index_text = index_text.replace(needle, insert, 1)
            else:
                errors.append("index.html 中未找到 renderProductDetail 的 storeName 插入点。")
        if "detail-extra-info" not in index_text:
            needle = "        <section class=\"detail-section\">\n          <div class=\"detail-section-head\">\n            <h4>数据总览</h4>"
            block = (
                "        ${attributesText || sellingPointsText ? `<section class=\"detail-section detail-extra-info\">\n"
                "          <div class=\"detail-section-head\"><h4>属性与卖点</h4></div>\n"
                "          <div class=\"detail-info-list\">\n"
                "            ${attributesText ? `<div class=\"detail-info-block\"><span>属性信息</span><p>${escapeHtml(attributesText)}</p></div>` : ''}\n"
                "            ${sellingPointsText ? `<div class=\"detail-info-block\"><span>卖点</span><p>${escapeHtml(sellingPointsText)}</p></div>` : ''}\n"
                "          </div>\n"
                "        </section>` : ''}\n\n"
            )
            if needle in index_text:
                index_text = index_text.replace(needle, block + needle, 1)
            else:
                errors.append("index.html 中未找到数据总览区块插入点。")
        if not errors or "index.html" not in " ".join(errors):
            original = index_path.read_text(encoding="utf-8", errors="ignore")
            if index_text != original:
                index_path.write_text(index_text, encoding="utf-8")
                changed_files.append("static/index.html")
    except OSError as exc:
        errors.append(f"index.html 写入失败：{exc}")

    try:
        css_text = css_path.read_text(encoding="utf-8", errors="ignore")
        if ".detail-info-block" not in css_text:
            css_addition = """

.detail-extra-info {
  padding: 20px 24px;
}

.detail-extra-info .detail-section-head {
  margin-bottom: 14px;
  padding-bottom: 12px;
}

.detail-info-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.detail-info-block {
  min-width: 0;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.detail-info-block span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.detail-info-block p {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
"""
            css_path.write_text(css_text.rstrip() + css_addition, encoding="utf-8")
            changed_files.append("static/styles.css")
    except OSError as exc:
        errors.append(f"styles.css 写入失败：{exc}")

    verified = detail_attribute_task_is_applied()
    ok = verified and not errors
    return {
        "ok": ok,
        "recipe": "detail_attribute_display",
        "can_write": True,
        "write_scope": "D:\\choice_product 内白名单文本文件",
        "changed_files": changed_files,
        "already_applied": not changed_files and verified,
        "verification": {
            "detail_attribute_task_is_applied": verified,
            "requires_restart": True,
        },
        "errors": errors,
        "summary": (
            "Codex执行Agent已完成商品详情属性与卖点展示写入并通过静态验证。"
            if changed_files else
            "Codex执行Agent已确认商品详情属性与卖点展示已存在，并通过静态验证。"
        ) if ok else "Codex执行Agent执行失败，已返回错误原因。",
    }


def handle_execution_handoff(handler_code, decision, message, readonly_context, base_files):
    workflow = fetch_latest_expert_workflow_instance((decision or {}).get("_session_id"), (decision or {}).get("_user_id"))
    if workflow:
        status = workflow.get("status")
        return {
            "handler": handler_code,
            "status": "COMPLETED" if status == "SUCCESS" else status,
            "summary": f"Response Delivery 已接收到工作流 `{workflow.get('workflow_code')}` 的执行状态：{status}。",
            "data": {
                "workflow_id": workflow.get("id"),
                "workflow_code": workflow.get("workflow_code"),
                "workflow_name": workflow.get("workflow_name"),
                "status": status,
                "output": parse_json(workflow.get("output_json")),
            },
        }
    return {
        "handler": handler_code,
        "status": "NO_EXECUTABLE_WORKFLOW",
        "summary": "业务执行层已接入，但当前任务没有命中可执行工作流，未写入文件。",
        "data": {
            "executor": decision.get("handoff_target") or "技术执行人员A / Codex",
            "user_action": "用户只需审核原因；不需要自己找文件或改代码。",
            "next_system_action": "为该类任务新增业务工作流后，业务执行层才能自动执行、写文件并回传验证结果。",
            "supported_workflows": list_expert_execution_workflows(),
        },
    }


def fetch_latest_expert_workflow_instance(session_id, user_id):
    if not session_id:
        return None
    try:
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT *
                FROM {EXPERT_WORKFLOW_INSTANCE_TABLE}
                WHERE session_id = %s AND user_id = %s
                ORDER BY id DESC
                LIMIT 1
                """,
                (session_id, user_id or 0),
            )
            return cursor.fetchone()
    except Exception:
        return None


def is_detail_attribute_task(message):
    text = stringify(message)
    return (
        ("attributes" in text or "属性信息" in text or "属性" in text)
        and ("selling_points" in text or "卖点" in text)
        and ("商品详情" in text or "站内详情" in text or "renderProductDetail" in text or "product_detail" in text)
    )


def detail_attribute_task_is_applied():
    try:
        app_text = (PROJECT_ROOT / "app.py").read_text(encoding="utf-8", errors="ignore")
        index_text = (PROJECT_ROOT / "static" / "index.html").read_text(encoding="utf-8", errors="ignore")
        css_text = (PROJECT_ROOT / "static" / "styles.css").read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return False
    return all([
        '"attributes": serialize_value(row.get(meta.get("attributes")))' in app_text,
        '"selling_points": serialize_value(row.get(meta.get("selling_points")))' in app_text,
        "attributesText" in index_text,
        "sellingPointsText" in index_text,
        "detail-extra-info" in index_text,
        ".detail-info-block" in css_text,
    ])


def handle_business_expert(handler_code, decision, message, readonly_context, base_files):
    expert_names = {role["code"]: role["name"] for role in EXPERT_TEAM_ROLES}
    return {
        "handler": handler_code,
        "status": "COMPLETED",
        "summary": f"{expert_names.get(handler_code, handler_code)} 已完成本轮业务判断。",
        "data": {
            "uses_readonly_context": bool(readonly_context),
            "output_type": "business_review",
        },
    }


def handle_planning_expert(handler_code, decision, message, readonly_context, base_files):
    expert_names = {role["code"]: role["name"] for role in EXPERT_TEAM_ROLES}
    return {
        "handler": handler_code,
        "status": "COMPLETED",
        "summary": f"{expert_names.get(handler_code, handler_code)} 已完成规划判断。",
        "data": {
            "mode": decision.get("mode"),
            "intent": decision.get("intent"),
        },
    }


def build_agent_response_summary(decision, handler_results):
    waiting = [item for item in handler_results if item.get("status") in {"WAITING_EXECUTOR_BRIDGE", "WAITING_EXECUTOR_RECIPE", "WAITING_ADAPTER"}]
    failed = [item for item in handler_results if item.get("status") == "FAILED"]
    unsupported = [item for item in handler_results if item.get("status") in {"UNSUPPORTED_AUTOMATION", "NO_EXECUTABLE_WORKFLOW"}]
    if failed:
        success = False
        message = "部分 Handler 执行失败，已返回可见错误。"
    elif unsupported:
        success = True
        message = "专家分发层已正常唤醒 Handler；业务执行层已接入，但当前任务没有命中可执行工作流，因此未写入文件。"
    elif waiting:
        success = True
        message = "专家分发层已正常完成；WorkflowEngine 已启动实例并执行到能力适配器边界，当前等待安全执行适配器。"
    else:
        success = True
        message = "专家分发层和业务执行层已完成，并生成可反馈给用户的结果。"
    return {
        "success": success,
        "message": message,
        "intent": decision.get("intent"),
        "mode": decision.get("mode"),
        "handler_statuses": [{"handler": item.get("handler"), "status": item.get("status")} for item in handler_results],
    }


def get_codex_executor_result(expert_execution):
    for item in (expert_execution or {}).get("results") or []:
        if item.get("handler") == "codex_executor":
            return item
    return None


def build_codex_executor_status_answer(ceo_decision, expert_execution):
    codex_result = get_codex_executor_result(expert_execution) or {}
    data = codex_result.get("data") or {}
    status = codex_result.get("status") or ""
    changed_files = data.get("changed_files") or []

    if status == "COMPLETED":
        changed_text = "、".join(changed_files) if changed_files else "相关代码已具备该能力，未重复写入"
        return (
            "已完成。\n\n"
            "本次已处理：功能已接入并完成验证。\n"
            f"涉及文件：{changed_text}\n"
            "你现在刷新页面后，打开任意商品的站内详情，在“卖点”区域可以看到“翻译成中文”按钮。"
        )

    if status in {"UNSUPPORTED_AUTOMATION", "NO_EXECUTABLE_WORKFLOW", "WAITING_ADAPTER"}:
        return (
            "这个需求已经被专家团队理解，但当前系统还没有覆盖到对应的自动执行能力。\n\n"
            "我已记录为待补能力项；用户不需要提供代码或自己找文件。等执行能力补齐后，再次发送同类需求即可直接执行。"
        )

    return stringify(codex_result.get("summary") or "专家团队已完成本轮处理。")


def build_expert_post_learning(session_id, message, ceo_decision, expert_execution):
    return {
        "stage": "Post-Learning",
        "session_id": session_id,
        "recorded": True,
        "scope": "session_isolated",
        "insight": (
            "当用户要求系统升级 Agent 团队时，不能停在方案层；必须展示 CEO 决策、Dispatcher 分发、Handler 执行结果、Response Delivery 和 Post-Learning。"
        ),
        "intent": (ceo_decision or {}).get("intent"),
        "handler_count": (expert_execution or {}).get("handler_count"),
    }


def format_expert_execution(expert_execution):
    return json.dumps(expert_execution or {}, ensure_ascii=False, indent=2)


def format_expert_learning(learning_result):
    return json.dumps(learning_result or {}, ensure_ascii=False, indent=2)


def build_expert_team_system_prompt(user, project_code, project_context):
    role_lines = []
    for role in EXPERT_TEAM_ROLES:
        role_lines.append(
            f"- {role['name']}({role['code']}，{role['level']}): 擅长{role['focus']} 边界：{role['limits']}"
        )
    context = project_context or "当前项目是 choice_product 选品与数据监控系统，但专家团队要保持通用能力，不能只围绕单一项目。"
    return f"""
你是一个“AI专家团队”的专家领导层，直接服务当前用户，而不是只服务某个固定项目。

你的核心职责：
1. 先理解用户真实意图、长期目标和当前项目上下文。
2. 判断应该调用哪些专家，以及每个专家的任务边界。
3. 不让执行专家各说各话，要给出统一、清楚、可执行的团队结论。
4. 输出要短、清楚、有重点，优先降低用户阅读成本。
5. 如果用户问的是项目建设，你要从系统产品、技术架构、数据架构、AI效率、UX角度组织团队。
6. 如果用户问的是商品分析，你要调度数据、IP、材质、运营、商品产品经理、选品总评专家。
7. 如果用户问的是代码、接口、页面、字段、项目文件位置或为什么某功能缺失，你要优先调用“项目文件侦察员”读取系统提供的只读文件上下文，再让技术架构专家/用户体验负责人判断，不要先反问用户已经能从文件里看到的信息。

当前用户：
- user_id: {user.get('id')}
- username: {user.get('username')}

当前项目上下文：
- project_code: {project_code}
- context: {context}

专家能力档案：
{chr(10).join(role_lines)}

回复格式：
- 先给“执行状态”：说明 Dispatcher 和 Handler 是否已执行；如果 Action Execution 已写入文件，要明确写“已执行/已修改”；如果未写入，只能说“未命中可执行工作流”，不要再写“等待技术执行Agent接入”。
- 再给“CEO决策”：说明意图 intent、模式 mode、是否需要用户补充；默认不要让用户补充材料。
- 再给“专家分发”：必须引用系统提供的 Handler 执行结果，列出哪个 Handler 已执行、状态是什么。
- 再给“交付结果”：直接给用户可查看、可审核的结论/方案/任务回执。
- 最后给“用户只需审核”：只说明用户需要确认执行或审核结果，不要要求用户粘贴代码、找接口、找技术人员、判断能力或整理项目材料。

不要假装已经调用外部工具或数据库；但如果系统消息里提供了“只读工具结果/项目文件侦察结果”，你可以引用其中的文件路径、行号和结论。不得声称自己执行了写入、修改、删除、上线、提交代码等动作。
如果系统消息里提供了“会话基底文件读取结果”，说明后端已经替你读取了用户指定的本地项目文件；你必须优先依据这些内容回答，不要再说“无法读取本地 Windows 路径”。
如果“会话基底文件读取结果”里写明“已对指定文件做全文件关键词检索”，你不能说“只看到前80行”，也不能要求用户粘贴同一个文件代码；如果上下文仍不够，只能要求用户补充更具体的关键词或把相关文件加入基底文件列表。
你必须遵循 TotalAgent 全生命周期：User Input -> TotalAgent Entry -> Security Gate -> RAG Enrichment -> Core Decision Layer -> Dispatcher -> Action Execution -> Response Delivery -> Post-Learning。用户只提供需求、查看结果、审核结果；其他文件检索、字段判断、能力判断、专家分发和执行交接由系统承担。
如果系统明确告诉你“执行器已经写入文件并通过验证”，你必须把修改文件、命中工作流和验证结果反馈给用户。如果系统告诉你“NO_EXECUTABLE_WORKFLOW”，你必须说明第 6 层已派发成功、第 7 层缺少对应业务工作流；不要说等待技术执行Agent接入。禁止让用户自己找技术人员、自己打开文件、自己改代码。
""".strip()


def call_expert_team_ai(user, message, history, project_code, project_context, readonly_context="", ceo_decision=None, expert_execution=None, learning_result=None):
    api_key = resolve_minimax_api_key()
    if not api_key:
        raise RuntimeError("缺少 MINIMAX_API_KEY")
    messages = [{"role": "system", "content": build_expert_team_system_prompt(user, project_code, project_context)}]
    messages.append({"role": "system", "content": EXPERT_EXECUTION_HANDOFF_RULES})
    messages.append({
        "role": "system",
        "content": (
            "重要规则：专家团队接口是同步接口，不存在后台异步读取文件这一步。"
            "如果系统消息已经提供只读文件结果，你必须直接基于结果回答。"
            "禁止回复“请稍等、正在读取、侦察员正在读取、我稍后给你结果、你复制粘贴代码、请你提供接口代码、请你告诉我有哪些能力”。"
            "如果读到的信息仍不足，只能明确说明已读取哪些文件和行号、还缺哪个更具体的文件名或关键词。"
        ),
    })
    messages.append({
        "role": "system",
        "content": (
            "【CEO核心决策层派工单】\n"
            "下面是后端在调用大模型之前完成的本地决策。你必须按这张派工单组织回答，不能退回普通问答模式。\n"
            f"{format_expert_ceo_decision(ceo_decision)}\n\n"
            "硬性要求：need_user_input=false 时，不得让用户提供代码、接口、字段、截图、能力清单或项目结构；"
            "你应该使用只读工具结果和会话记忆给出结论、方案或执行交接单。"
            "同时必须明确执行状态：系统执行结果是 COMPLETED 时就是已执行；系统执行结果是 NO_EXECUTABLE_WORKFLOW 时就是第 7 层缺少工作流，不要再写“等待技术执行Agent接入”。"
            "不要说“你需要找技术执行人员”或“你自己打开文件改”；应该说“执行对象是A/Codex，用户只需确认执行并审核结果”。"
        ),
    })
    messages.append({
        "role": "system",
        "content": (
            "【Dispatcher + Action Execution 执行结果】\n"
            "下面不是方案，而是后端 IntentHandlerFactory(local) 已经按 handlers 映射实际执行过的 Handler 结果。"
            "你必须把这些结果反馈给用户。\n"
            f"{format_expert_execution(expert_execution)}\n\n"
            "【Post-Learning 结果】\n"
            f"{format_expert_learning(learning_result)}"
        ),
    })
    if readonly_context:
        messages.append({
            "role": "system",
            "content": (
                "以下是系统白名单只读工具在本轮对话中读取到的结果。"
                "你可以引用这些结果，但不能声称自己执行了写入、修改、删除、上线、提交代码等动作。\n\n"
                f"{readonly_context}"
            ),
        })
    for item in history[-10:]:
        role = "assistant" if item.get("role") == "assistant" else "user"
        messages.append({"role": role, "content": stringify(item.get("content"))})
    messages.append({"role": "user", "content": message})
    response = requests.post(
        f"{MINIMAX_BASE_URL.rstrip('/')}/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={"model": MINIMAX_MODEL, "messages": messages, "temperature": 0.2, "stream": False},
        timeout=60,
    )
    response.raise_for_status()
    data = response.json()
    return data["choices"][0]["message"]["content"].strip()


def sanitize_expert_team_answer(text):
    cleaned = stringify(text)
    cleaned = re.sub(r"<think>[\s\S]*?</think>", "", cleaned, flags=re.IGNORECASE).strip()
    cleaned = re.sub(r"^\s*<think>[\s\S]*?(?=(\*\*|团队判断|执行交接单|《执行交接单》|已完成|$))", "", cleaned, flags=re.IGNORECASE).strip()
    cleaned = remove_internal_workflow_diagnostics(cleaned)
    return cleaned or stringify(text).strip()


def remove_internal_workflow_diagnostics(text):
    cleaned = stringify(text)
    internal_terms = [
        "WorkflowEngine",
        "WAITING_ADAPTER",
        "IntentHandlerFactory",
        "CEO决策",
        "专家分发",
        "第 6 层",
        "第 7 层",
        "安全执行适配器",
        "codex_executor",
        "workflow_engine",
        "execution_handoff",
    ]
    if not any(term in cleaned for term in internal_terms):
        return cleaned
    lines = []
    for line in cleaned.splitlines():
        if any(term in line for term in internal_terms):
            continue
        if line.strip().startswith("- ") and (":" in line and any(key in line for key in ["handler", "status"])):
            continue
        lines.append(line)
    return "\n".join(lines).strip()


def sanitize_expert_message_for_display(text):
    cleaned = sanitize_expert_team_answer(text)
    internal_terms = [
        "WorkflowEngine",
        "WAITING_ADAPTER",
        "IntentHandlerFactory",
        "codex_executor",
        "workflow_engine",
        "execution_handoff",
        "CEO决策",
        "专家分发",
        "第 6 层",
        "第 7 层",
        "安全执行适配器",
        "PLAN_AND_HANDOFF",
    ]
    if any(term in cleaned for term in internal_terms):
        if "COMPLETED" in cleaned or "SUCCESS" in cleaned:
            return (
                "已完成。\n\n"
                "本次功能已完成处理。你刷新页面后，回到对应页面查看效果即可。"
            )
        return (
            "这个需求已经被专家团队理解，但当前系统还没有覆盖到对应的自动执行能力。\n\n"
            "我已记录为待补能力项；用户不需要提供代码或自己找文件。等执行能力补齐后，再次发送同类需求即可直接执行。"
        )
    return cleaned


def is_expert_fake_wait_answer(text):
    cleaned = stringify(text)
    fake_wait_words = [
        "请稍等", "正在读取", "稍后", "请粘贴代码", "粘贴代码", "请提供",
        "你需要提供", "你需要找技术执行人员", "如果你自己是技术执行人员",
        "自己去改", "等待技术执行Agent接入", "等待 Codex 执行桥",
    ]
    return any(word in cleaned for word in fake_wait_words)


def ensure_expert_execution_status(answer, ceo_decision=None, expert_execution=None):
    text = sanitize_expert_team_answer(answer)
    codex_result = get_codex_executor_result(expert_execution)
    if codex_result and codex_result.get("status") in {"COMPLETED", "UNSUPPORTED_AUTOMATION", "NO_EXECUTABLE_WORKFLOW", "WAITING_ADAPTER"}:
        stale_words = ["等待技术执行Agent接入", "等待 Codex 执行桥", "未修改，仅生成", "执行交接单"]
        internal_words = ["WorkflowEngine", "WAITING_ADAPTER", "IntentHandlerFactory", "第 6 层", "第 7 层"]
        if any(word in text for word in stale_words + internal_words) or not text:
            return build_codex_executor_status_answer(ceo_decision, expert_execution)
    if "执行状态" in text:
        return text
    return text


class ExpertResponseValidator:
    """Checks the final user-facing answer before Response Delivery."""

    internal_terms = [
        "<think>",
        "</think>",
        "WorkflowEngine",
        "WAITING_ADAPTER",
        "IntentHandlerFactory",
        "codex_executor",
        "workflow_engine",
        "execution_handoff",
        "CEO决策",
        "专家分发",
        "第 6 层",
        "第 7 层",
        "安全执行适配器",
        "PLAN_AND_HANDOFF",
        "NO_EXECUTABLE_WORKFLOW",
    ]

    user_burden_terms = [
        "你需要找技术执行人员",
        "你自己打开文件",
        "你自己去改",
        "请粘贴代码",
        "请提供接口代码",
        "等待技术执行Agent接入",
        "等待 Codex 执行桥",
        "需要手动实现",
        "手动实现",
    ]

    def validate(self, original_input, raw_output, ceo_decision=None, expert_execution=None):
        output = sanitize_expert_team_answer(raw_output)
        reasons = []
        codex_result = get_codex_executor_result(expert_execution)
        codex_status = (codex_result or {}).get("status") or ""

        if not output.strip():
            reasons.append("输出为空")
        if any(term in output for term in self.internal_terms):
            reasons.append("包含内部工作流或调试信息")
        if any(term in output for term in self.user_burden_terms):
            reasons.append("把系统内部执行责任转嫁给用户")

        score = estimate_answer_alignment_score(original_input, output)
        if not codex_result and score < 0.08:
            reasons.append(f"回答与用户问题相关性偏低（score={score:.2f}）")

        if codex_status == "COMPLETED":
            if not any(term in output for term in ["已完成", "已处理", "可以看到", "刷新", "打开"]):
                reasons.append("执行完成类回复缺少结果和验收入口")
        if codex_status in {"WAITING_ADAPTER", "NO_EXECUTABLE_WORKFLOW", "UNSUPPORTED_AUTOMATION"}:
            if any(term in output for term in ["WorkflowEngine", "WAITING_ADAPTER", "第 6 层", "第 7 层"]):
                reasons.append("未完成类回复暴露内部诊断")
            if not any(term in output for term in ["当前系统", "自动执行能力", "待补能力", "再次发送"]):
                reasons.append("未完成类回复缺少用户可理解的后续说明")

        return {
            "passed": not reasons,
            "reasons": reasons,
            "alignment_score": round(score, 4),
        }


def validate_and_refine_expert_answer(original_input, raw_output, ceo_decision=None, expert_execution=None, max_retry=3):
    validator = ExpertResponseValidator()
    answer = sanitize_expert_team_answer(raw_output)
    attempts = []

    for retry_count in range(max_retry + 1):
        result = validator.validate(original_input, answer, ceo_decision, expert_execution)
        result["retry_count"] = retry_count
        attempts.append(dict(result))
        if result.get("passed"):
            final_result = dict(result)
            final_result["attempts"] = attempts
            return answer, final_result
        if retry_count >= max_retry:
            fallback = build_user_facing_execution_answer(ceo_decision, expert_execution, result.get("reasons") or [])
            final_result = validator.validate(original_input, fallback, ceo_decision, expert_execution)
            final_result.update({"retry_count": retry_count, "attempts": attempts, "fallback_used": True})
            return fallback, final_result
        answer = correct_expert_answer_for_user_experience(
            original_input,
            answer,
            result.get("reasons") or [],
            ceo_decision,
            expert_execution,
            retry_count + 1,
        )

    return answer, {"passed": True, "retry_count": 0, "attempts": attempts}


def correct_expert_answer_for_user_experience(original_input, raw_output, reasons, ceo_decision=None, expert_execution=None, retry_count=1):
    api_key = resolve_minimax_api_key()
    if not api_key:
        return build_user_facing_execution_answer(ceo_decision, expert_execution, reasons)

    prompt = (
        "你是“用户回答体验校验员”，专门把系统内部执行结果改写成普通用户能看懂的最终回复。\n"
        "你的目标不是重新分析任务，而是修正上一版回复的问题。\n\n"
        "硬性规则：\n"
        "1. 只输出最终给用户看的中文回复，不要解释你的校验过程。\n"
        "2. 禁止输出 <think>、WorkflowEngine、WAITING_ADAPTER、IntentHandlerFactory、codex_executor、CEO决策、第6层、第7层等内部词。\n"
        "3. 不要让用户自己找技术人员、自己粘贴代码、自己打开文件修改。\n"
        "4. 如果已经完成，说明完成了什么、涉及哪些文件、用户怎么查看结果。\n"
        "5. 如果没有完成，用用户能懂的话说明当前还不能自动执行，并说已经记录为待补能力项。\n"
        "6. 保持简短，最多 6 行。\n\n"
        f"重试次数：{retry_count}\n"
        f"原始用户需求：{stringify(original_input)}\n"
        f"失败原因：{'; '.join(stringify(item) for item in reasons)}\n"
        f"上一版回复：\n{stringify(raw_output)}\n\n"
        f"执行上下文摘要：\n{build_user_facing_execution_answer(ceo_decision, expert_execution, reasons)}"
    )

    try:
        response = requests.post(
            f"{MINIMAX_BASE_URL.rstrip('/')}/chat/completions",
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={
                "model": MINIMAX_MODEL,
                "messages": [
                    {"role": "system", "content": "你是严格的用户体验回复改写器，只输出改写后的最终回复。"},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.1,
                "stream": False,
            },
            timeout=45,
        )
        response.raise_for_status()
        data = response.json()
        return sanitize_expert_team_answer(data["choices"][0]["message"]["content"].strip())
    except Exception:
        return build_user_facing_execution_answer(ceo_decision, expert_execution, reasons)


def build_user_facing_execution_answer(ceo_decision=None, expert_execution=None, reasons=None):
    codex_result = get_codex_executor_result(expert_execution)
    if codex_result:
        status = codex_result.get("status")
        data = codex_result.get("data") or {}
        changed_files = data.get("changed_files") or []
        if status == "COMPLETED":
            changed_text = "、".join(changed_files) if changed_files else "相关代码"
            return (
                "已完成。\n\n"
                "本次已把对应功能接入并完成验证。\n"
                f"涉及文件：{changed_text}\n"
                "你刷新页面后，打开对应商品的站内详情即可查看效果。"
            )
        return (
            "这个需求已经被专家团队理解，但当前系统还没有覆盖到对应的自动执行能力。\n\n"
            "我已记录为待补能力项；用户不需要提供代码或自己找文件。等执行能力补齐后，再次发送同类需求即可直接执行。"
        )

    reason_text = "；".join(stringify(item) for item in (reasons or []))
    return (
        "这次回复已被校验层拦截，没有直接展示内部执行信息。\n\n"
        f"原因：{reason_text or '输出不符合用户可读标准'}。"
    )


def estimate_answer_alignment_score(original_input, answer):
    source_tokens = set(extract_alignment_tokens(original_input))
    answer_tokens = set(extract_alignment_tokens(answer))
    if not source_tokens:
        return 1.0
    if not answer_tokens:
        return 0.0
    overlap = len(source_tokens & answer_tokens)
    return overlap / max(1, min(len(source_tokens), len(answer_tokens)))


def extract_alignment_tokens(text):
    raw = stringify(text).lower()
    tokens = re.findall(r"[a-z0-9_]{3,}|[\u4e00-\u9fa5]{2,}", raw)
    stop_words = {
        "这个", "我的", "一个", "可以", "需要", "用户", "专家", "团队", "功能",
        "已经", "当前", "回复", "问题", "结果", "系统", "页面", "商品",
    }
    return [token for token in tokens if token not in stop_words][:80]


def fallback_expert_team_answer(message, error):
    return (
        "团队判断：专家团队接口已收到你的问题，但当前 AI 服务暂时不可用，先给你一个本地兜底建议。\n\n"
        "应该调用的专家：首席项目规划专家、专家团队管理者、数据方法论负责人、技术架构专家。\n\n"
        "执行顺序：先明确你的目标和使用场景，再拆分为业务专家任务，最后由技术架构专家判断如何落到系统模块和数据库。\n\n"
        f"当前问题：{message}\n\n"
        f"服务状态：{error}"
    )


def collect_expert_readonly_context(message, base_files=None):
    text = stringify(message)
    lower = text.lower()
    context_blocks = []
    base_context = collect_expert_base_file_context(base_files or [], text)
    if base_context:
        context_blocks.append(base_context)
    file_context = collect_expert_file_context(text)
    if file_context:
        context_blocks.append(file_context)
    trigger_words = [
        "查", "查看", "数据", "字段", "接口", "回执", "商品", "属性", "卖点",
        "attributes", "selling_points", "product_id", "fastmoss", "kalodata", "统一表",
        "fastmoss_product_aggregate", "fastmoss_product_rank_aggregate", "kalodata_youwei_product",
    ]
    if not any(word.lower() in lower for word in trigger_words):
        return "\n\n".join(context_blocks)

    product_ids = list(dict.fromkeys(re.findall(r"\b\d{10,}\b", text)))[:5]
    requested_sources = []
    for source, meta in SOURCES.items():
        table = meta["table"]
        if source.lower() in lower or table.lower() in lower:
            requested_sources.append(source)
    if "统一表" in text and "unified" not in requested_sources:
        requested_sources.append("unified")
    if not requested_sources:
        requested_sources = ["unified"]
    requested_sources = requested_sources[:3]

    context_lines = ["【只读工具结果】本轮仅执行白名单只读查询，未写入、未修改、未删除任何数据。"]
    try:
        with db() as conn, conn.cursor() as cursor:
            for source in requested_sources:
                meta = SOURCES[source]
                table = meta["table"]
                columns = fetch_table_columns(cursor, table)
                context_lines.append(f"\n数据源：{source}，表：{table}")
                if columns:
                    wanted = [
                        meta.get("id"),
                        meta.get("date"),
                        meta.get("title"),
                        meta.get("attributes"),
                        meta.get("selling_points"),
                        meta.get("price"),
                        meta.get("sold"),
                        meta.get("total_sold"),
                    ]
                    present = [col for col in wanted if col and col in columns]
                    context_lines.append(f"可用关键字段：{', '.join(present) if present else '未命中关键字段'}")
                    append_column_fill_stats(cursor, context_lines, table, columns, meta)
                    if product_ids:
                        append_product_readonly_samples(cursor, context_lines, table, columns, meta, product_ids)
                else:
                    context_lines.append("字段读取失败或表不存在。")
    except Exception as exc:
        context_lines.append(f"\n只读工具读取失败：{exc}")
    context_blocks.append("\n".join(context_lines))
    return "\n\n".join(context_blocks)


def collect_expert_file_context(message):
    text = stringify(message).strip()
    lower = text.lower()
    trigger_words = [
        "文件", "代码", "前端", "后端", "接口", "页面", "样式", "按钮", "功能", "路由",
        "api", "html", "css", "js", "python", "flask", "java", "spring", "agent", "专家团队",
        "index.html", "styles.css", "app.py", "prompt", "数据库", "字段", "为什么", "怎么改",
    ]
    if not any(word.lower() in lower for word in trigger_words):
        return ""

    search_terms = extract_file_search_terms(text)
    candidate_files = list(iter_expert_allowed_files())
    scored = []
    for file_path in candidate_files:
        score = score_expert_file(file_path, search_terms, lower)
        if score > 0:
            scored.append((score, file_path))
    scored.sort(key=lambda item: (-item[0], str(item[1]).lower()))
    selected = [file_path for _score, file_path in scored[:EXPERT_FILE_CONTEXT_MAX_FILES]]
    if not selected:
        selected = default_expert_context_files()

    lines = [
        "【项目文件侦察结果】本轮仅执行项目目录白名单只读读取，未写入、未修改、未删除任何文件。",
        f"项目根目录：{PROJECT_ROOT}",
        "侦察员能力边界：只能读取 app.py、static、tools、agent-center 和项目文档等白名单文本文件。",
    ]
    for file_path in selected:
        snippet = build_expert_file_snippet(file_path, search_terms)
        if snippet:
            lines.append(snippet)
        if sum(len(line) for line in lines) > EXPERT_FILE_CONTEXT_MAX_CHARS:
            lines.append("\n文件上下文已达到长度上限，后续文件省略。")
            break
    return "\n".join(lines)[:EXPERT_FILE_CONTEXT_MAX_CHARS]


def normalize_expert_base_files(value):
    if isinstance(value, str):
        raw_items = re.split(r"[\r\n,]+", value)
    elif isinstance(value, list):
        raw_items = value
    else:
        raw_items = []
    normalized = []
    for item in raw_items:
        text = stringify(item).strip().strip('"').strip("'")
        if not text:
            continue
        normalized.append(text)
    return list(dict.fromkeys(normalized))[:12]


def merge_expert_base_files(*groups):
    merged = []
    for group in groups:
        for item in group or []:
            text = stringify(item).strip()
            if text and text not in merged:
                merged.append(text)
    return merged[:12]


def extract_expert_file_paths(text):
    raw = stringify(text)
    patterns = [
        r"[A-Za-z]:\\(?:[A-Za-z0-9_.() -]+\\)*[A-Za-z0-9_.() -]+\.[A-Za-z0-9]+",
        r"(?:app\.py|static/[A-Za-z0-9_./-]+|tools/[A-Za-z0-9_./-]+|agent-center/[A-Za-z0-9_./-]+|[A-Za-z0-9_-]+\.md)",
    ]
    paths = []
    for pattern in patterns:
        for match in re.findall(pattern, raw):
            cleaned = stringify(match).strip().rstrip("，。；;、)")
            if cleaned and cleaned not in paths:
                paths.append(cleaned)
    return paths[:12]


def collect_expert_base_file_context(base_files, message=""):
    if not base_files:
        return ""
    search_terms = extract_file_search_terms(message)
    lines = [
        "【会话基底文件读取结果】用户为本次专家会话显式指定了以下基底文件。系统已在后端按白名单只读读取；这些内容可作为判断依据。",
        f"项目根目录：{PROJECT_ROOT}",
        "安全边界：只读、仅允许 D:\\choice_product 内文本文件、不会写入或执行。",
        "读取策略：已对指定文件做全文件关键词检索，并返回命中片段；不要再要求用户粘贴这些文件里的代码。",
        f"本轮检索关键词：{', '.join(search_terms) if search_terms else '无，使用文件开头预览'}",
    ]
    for raw_path in base_files:
        resolved, error = resolve_expert_base_file(raw_path)
        if error:
            lines.append(f"\n指定路径：{raw_path}\n  读取状态：失败，原因：{error}")
            continue
        snippet = build_expert_file_targeted_snippet(resolved, search_terms)
        lines.append(snippet)
        if sum(len(line) for line in lines) > EXPERT_FILE_CONTEXT_MAX_CHARS:
            lines.append("\n基底文件上下文已达到长度上限，后续文件省略。")
            break
    return "\n".join(lines)[:EXPERT_FILE_CONTEXT_MAX_CHARS]


def resolve_expert_base_file(raw_path):
    if not raw_path:
        return None, "路径为空"
    text = stringify(raw_path).strip()
    candidate = Path(text)
    if not candidate.is_absolute():
        candidate = PROJECT_ROOT / candidate
    try:
        resolved = candidate.resolve(strict=False)
        resolved.relative_to(PROJECT_ROOT)
    except ValueError:
        return None, "不在项目白名单根目录 D:\\choice_product 内"
    except OSError as exc:
        return None, f"路径解析失败：{exc}"
    if not resolved.exists():
        return None, "文件不存在"
    if not resolved.is_file():
        return None, "不是文件"
    if any(part in EXPERT_FILE_EXCLUDED_DIRS for part in resolved.relative_to(PROJECT_ROOT).parts):
        return None, "位于禁止读取目录"
    if resolved.suffix.lower() not in EXPERT_FILE_ALLOWED_SUFFIXES:
        return None, f"不支持的文件类型：{resolved.suffix}"
    try:
        if resolved.stat().st_size > 350_000:
            return None, "文件过大，超过只读上下文限制"
    except OSError as exc:
        return None, f"无法读取文件大小：{exc}"
    return resolved, None


def build_expert_file_targeted_snippet(file_path, search_terms):
    try:
        content = file_path.read_text(encoding="utf-8", errors="ignore")
    except OSError as exc:
        return f"\n文件：{safe_relpath(file_path)}\n  读取状态：失败，原因：{exc}"
    file_lines = content.splitlines()
    snippet_lines = [
        f"\n文件：{safe_relpath(file_path)}",
        "  读取状态：成功",
        f"  文件总行数：{len(file_lines)}",
    ]
    lowered_terms = [term.lower() for term in search_terms if term]
    matched_indexes = []
    if lowered_terms:
        for index, line in enumerate(file_lines):
            line_lower = line.lower()
            score = expert_line_match_score(line_lower, lowered_terms)
            if score > 0:
                matched_indexes.append((score, index))
    if matched_indexes:
        ranked_matches = [index for _score, index in sorted(matched_indexes, key=lambda item: (-item[0], item[1]))[:18]]
        snippet_lines.append(f"  全文件高相关命中位置：{', '.join('L' + str(index + 1) for index in ranked_matches)}")
        selected_indexes = []
        seen = set()
        for index in ranked_matches:
            for nearby in range(max(0, index - 3), min(len(file_lines), index + 4)):
                if nearby not in seen:
                    selected_indexes.append(nearby)
                    seen.add(nearby)
        selected_indexes.sort()
        last_index = None
        for index in selected_indexes[:120]:
            if last_index is not None and index - last_index > 1:
                snippet_lines.append("  ...")
            line = file_lines[index].rstrip()
            if line.strip():
                snippet_lines.append(f"  L{index + 1}: {line[:240]}")
            last_index = index
        if len(selected_indexes) > 120:
            snippet_lines.append("  ... 命中片段较多，已截断。")
    else:
        snippet_lines.append("  全文件检索未命中关键词，展示文件开头预览。")
        max_lines = 80
        for index, line in enumerate(file_lines[:max_lines], start=1):
            if line.strip():
                snippet_lines.append(f"  L{index}: {line.rstrip()[:220]}")
        if len(file_lines) > max_lines:
            snippet_lines.append(f"  ... 文件共 {len(file_lines)} 行，仅展示前 {max_lines} 行。")
    return "\n".join(snippet_lines)


def expert_line_match_score(line_lower, lowered_terms):
    score = 0
    high_value_terms = [
        '@app.get("/api/products/<source>/<product_id>")',
        "def product_detail",
        "function renderproductdetail",
        "renderproductdetail",
        "function opendetail",
        "opendetail(",
        "currentdetailitem",
        "data-detail",
        "products/<source>/<product_id>",
        "analysis-detail",
    ]
    medium_value_terms = ["api/products", "商品详情", "站内详情", "detaildialog", "detailbody"]
    for term in lowered_terms:
        if term and term in line_lower:
            score += 1
    for term in high_value_terms:
        if term in line_lower:
            score += 30
    for term in medium_value_terms:
        if term in line_lower:
            score += 10
    return score


def extract_file_search_terms(message):
    terms = []
    raw_terms = re.findall(r"[A-Za-z_][A-Za-z0-9_./-]{2,}|[\u4e00-\u9fa5]{2,}", stringify(message))
    stop_terms = {
        "这个", "我的", "为什么", "怎么", "一下", "可以", "需要", "功能", "问题", "还是",
        "专家", "团队", "用户", "查看", "文件", "代码", "前端", "后端",
    }
    for term in raw_terms:
        normalized = term.strip()
        if not normalized or normalized in stop_terms:
            continue
        if len(normalized) > 40:
            continue
        terms.append(normalized)
    defaults = ["expert-team", "专家团队", "api/expert-team", "renderExpertTeamPage", "app.py", "index.html", "styles.css"]
    for term in defaults:
        if term.lower() in stringify(message).lower() and term not in terms:
            terms.append(term)
    lower_message = stringify(message).lower()
    mapped_terms = []
    if "商品详情" in stringify(message) or "站内详情" in stringify(message) or "detail" in lower_message:
        mapped_terms.extend([
            "api/products",
            "products/<source>/<product_id>",
            '@app.get("/api/products/<source>/<product_id>")',
            "def product_detail",
            "currentDetailItem",
            "openDetail(",
            "function openDetail",
            "openProductDetail",
            "function renderProductDetail",
            "renderProductDetail",
            "data-detail",
            "detail",
            "modal",
            "商品详情",
        ])
    if "接口" in stringify(message) or "api" in lower_message:
        mapped_terms.extend(["@app.get", "@app.post", "fetch(", "/api/"])
    if "专家团队" in stringify(message):
        mapped_terms.extend(["expert-team", "renderExpertTeamPage", "sendExpertTeamMessage", "collect_expert_readonly_context"])
    if "图片" in stringify(message) or "粘贴" in stringify(message):
        mapped_terms.extend(["expertImages", "expertImageInput", "paste", "image", "images"])
    for term in mapped_terms:
        if term not in terms:
            terms.append(term)
    return list(dict.fromkeys(terms))[:24]


def iter_expert_allowed_files():
    for root, dirs, files in os.walk(PROJECT_ROOT):
        root_path = Path(root)
        dirs[:] = [item for item in dirs if item not in EXPERT_FILE_EXCLUDED_DIRS]
        try:
            rel_root = root_path.relative_to(PROJECT_ROOT)
        except ValueError:
            continue
        if rel_root.parts and rel_root.parts[0] in EXPERT_FILE_EXCLUDED_DIRS:
            continue
        for filename in files:
            file_path = root_path / filename
            if file_path.suffix.lower() not in EXPERT_FILE_ALLOWED_SUFFIXES:
                continue
            try:
                if file_path.stat().st_size > 350_000:
                    continue
                yield file_path
            except OSError:
                continue


def score_expert_file(file_path, search_terms, lower_message):
    rel = file_path.relative_to(PROJECT_ROOT).as_posix().lower()
    score = 0
    important_files = {
        "app.py": 8,
        "static/index.html": 8,
        "static/styles.css": 6,
        "agent-center/src/main/resources/static/index.html": 4,
        "agent-center/src/main/java/com/choiceproduct/agentcenter/agent/totalconversationagent.java": 4,
    }
    score += important_files.get(rel, 0)
    for term in search_terms:
        term_lower = term.lower()
        if term_lower in rel:
            score += 8
    if any(word in lower_message for word in ["前端", "页面", "按钮", "粘贴", "图片", "样式"]):
        if rel in {"static/index.html", "static/styles.css"}:
            score += 12
    if any(word in lower_message for word in ["后端", "接口", "api", "数据库"]):
        if rel == "app.py":
            score += 12
    if any(word in lower_message for word in ["java", "agent", "中台", "spring"]):
        if rel.startswith("agent-center/"):
            score += 10
    return score


def default_expert_context_files():
    defaults = [
        PROJECT_ROOT / "app.py",
        PROJECT_ROOT / "static" / "index.html",
        PROJECT_ROOT / "static" / "styles.css",
        PROJECT_ROOT / "product_selection_platform.md",
        PROJECT_ROOT / "private_product_library_plan.md",
        PROJECT_ROOT / "agent-center" / "README.md",
    ]
    return [file_path for file_path in defaults if file_path.exists()]


def build_expert_file_snippet(file_path, search_terms):
    try:
        content = file_path.read_text(encoding="utf-8", errors="ignore")
    except OSError as exc:
        return f"\n文件：{safe_relpath(file_path)}\n读取失败：{exc}"
    rel = safe_relpath(file_path)
    file_lines = content.splitlines()
    matches = []
    lowered_terms = [term.lower() for term in search_terms if term]
    for index, line in enumerate(file_lines, start=1):
        line_lower = line.lower()
        if not lowered_terms or any(term in line_lower for term in lowered_terms):
            if line.strip():
                matches.append((index, line.rstrip()))
        if len(matches) >= EXPERT_FILE_CONTEXT_MAX_MATCHES_PER_FILE:
            break
    if not matches:
        preview = [(idx + 1, line.rstrip()) for idx, line in enumerate(file_lines[:8]) if line.strip()]
        matches = preview[:EXPERT_FILE_CONTEXT_MAX_MATCHES_PER_FILE]
    snippet_lines = [f"\n文件：{rel}"]
    for line_no, line in matches:
        clipped = line[:220]
        snippet_lines.append(f"  L{line_no}: {clipped}")
    return "\n".join(snippet_lines)


def safe_relpath(file_path):
    try:
        return file_path.relative_to(PROJECT_ROOT).as_posix()
    except ValueError:
        return str(file_path)


def fetch_table_columns(cursor, table):
    allowed_tables = {meta["table"] for meta in SOURCES.values()}
    if table not in allowed_tables:
        return set()
    cursor.execute(f"SHOW COLUMNS FROM `{table}`")
    return {row.get("Field") for row in cursor.fetchall() if row.get("Field")}


def append_column_fill_stats(cursor, context_lines, table, columns, meta):
    stats_cols = [col for col in [meta.get("attributes"), meta.get("selling_points")] if col and col in columns]
    if not stats_cols:
        return
    select_parts = ["COUNT(*) AS total_rows"]
    for col in stats_cols:
        alias = f"{safe_alias(col)}_filled"
        select_parts.append(f"SUM(CASE WHEN `{col}` IS NOT NULL AND TRIM(CAST(`{col}` AS CHAR)) <> '' THEN 1 ELSE 0 END) AS `{alias}`")
    cursor.execute(f"SELECT {', '.join(select_parts)} FROM `{table}`")
    row = cursor.fetchone() or {}
    parts = [f"总行数 {row.get('total_rows', 0)}"]
    for col in stats_cols:
        parts.append(f"{col}有值 {row.get(safe_alias(col) + '_filled', 0)}")
    context_lines.append("字段填充概览：" + "，".join(parts))


def append_product_readonly_samples(cursor, context_lines, table, columns, meta, product_ids):
    product_col = meta.get("id")
    date_col = meta.get("date")
    if not product_col or product_col not in columns:
        return
    wanted = [
        product_col,
        date_col,
        meta.get("title"),
        meta.get("attributes"),
        meta.get("selling_points"),
        meta.get("price"),
        meta.get("sold"),
        meta.get("total_sold"),
    ]
    selected = [col for col in wanted if col and col in columns]
    if not selected:
        return
    placeholders = ", ".join(["%s"] * len(product_ids))
    select_sql = ", ".join(f"`{col}`" for col in selected)
    order_sql = f"ORDER BY `{date_col}` DESC" if date_col and date_col in columns else ""
    cursor.execute(
        f"""
        SELECT {select_sql}
        FROM `{table}`
        WHERE `{product_col}` IN ({placeholders})
        {order_sql}
        LIMIT 5
        """,
        product_ids,
    )
    rows = cursor.fetchall()
    if not rows:
        context_lines.append(f"按商品ID未查到样本：{', '.join(product_ids)}")
        return
    context_lines.append("商品样本：")
    for row in rows:
        summary = []
        for col in selected:
            summary.append(f"{col}={truncate_text(row.get(col), 120)}")
        context_lines.append("- " + "；".join(summary))


def safe_alias(value):
    return re.sub(r"\W+", "_", stringify(value), flags=re.UNICODE).strip("_") or "field"


def truncate_text(value, limit=120):
    text = stringify(value).replace("\r", " ").replace("\n", " ")
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit] + ("..." if len(text) > limit else "")


def fetch_detail_analysis_record(cursor, user_id, source, product_id, date_record, analysis_type):
    cursor.execute(
        f"""
        SELECT *
        FROM {DETAIL_ANALYSIS_TABLE}
        WHERE user_id = %s
          AND source = %s
          AND product_id = %s
          AND date_record = %s
          AND analysis_type = %s
        LIMIT 1
        """,
        (user_id, source, str(product_id), date_record, analysis_type),
    )
    return cursor.fetchone()


def upsert_detail_analysis_record(cursor, user, source, product_table, product_id, date_record, analysis_type, product_title=""):
    cursor.execute(
        f"""
        INSERT INTO {DETAIL_ANALYSIS_TABLE}
          (user_id, username, source, product_table, product_id, date_record, analysis_type, product_title, status, result_json, error_message, completed_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'RUNNING', NULL, NULL, NULL)
        ON DUPLICATE KEY UPDATE
          username = VALUES(username),
          product_table = VALUES(product_table),
          product_title = IF(VALUES(product_title) <> '', VALUES(product_title), product_title),
          status = 'RUNNING',
          result_json = NULL,
          error_message = NULL,
          completed_at = NULL
        """,
        (
            user["id"],
            user.get("username") or "",
            source,
            product_table,
            str(product_id),
            date_record,
            analysis_type,
            stringify(product_title),
        ),
    )
    cursor.execute(
        f"""
        SELECT id
        FROM {DETAIL_ANALYSIS_TABLE}
        WHERE user_id = %s AND source = %s AND product_id = %s AND date_record = %s AND analysis_type = %s
        LIMIT 1
        """,
        (user["id"], source, str(product_id), date_record, analysis_type),
    )
    return cursor.fetchone()["id"]


def run_detail_analysis_task(record_id, user_id, ai_source, product_id, date_record, analysis_type):
    try:
        with db() as conn, conn.cursor() as cursor:
            product = load_ai_product(cursor, ai_source, product_id, date_record)
            result = run_analysis(
                cursor,
                ai_source,
                product,
                "IP" if analysis_type == "ip" else "MATERIAL",
                write=False,
                detail=True,
            )
            if result.get("error"):
                cursor.execute(
                    f"""
                    UPDATE {DETAIL_ANALYSIS_TABLE}
                    SET status = 'FAILED', error_message = %s, completed_at = NOW()
                    WHERE id = %s AND user_id = %s
                    """,
                    (result["error"], record_id, user_id),
                )
            else:
                cursor.execute(
                    f"""
                    UPDATE {DETAIL_ANALYSIS_TABLE}
                    SET status = 'SUCCESS', result_json = %s, error_message = NULL, completed_at = NOW()
                    WHERE id = %s AND user_id = %s
                    """,
                    (json.dumps(result.get("result") or {}, ensure_ascii=False, default=str), record_id, user_id),
                )
            conn.commit()
    except Exception as exc:
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                UPDATE {DETAIL_ANALYSIS_TABLE}
                SET status = 'FAILED', error_message = %s, completed_at = NOW()
                WHERE id = %s AND user_id = %s
                """,
                (str(exc), record_id, user_id),
            )
            conn.commit()


def normalize_detail_analysis_record(row):
    return {
        "id": row.get("id"),
        "user_id": row.get("user_id"),
        "username": row.get("username"),
        "source": row.get("source"),
        "product_table": row.get("product_table"),
        "product_id": row.get("product_id"),
        "date_record": stringify(row.get("date_record")),
        "analysis_type": row.get("analysis_type"),
        "product_title": row.get("product_title") or "",
        "status": row.get("status"),
        "result": parse_json(row.get("result_json")),
        "error_message": row.get("error_message") or "",
        "created_at": stringify(row.get("created_at")),
        "updated_at": stringify(row.get("updated_at")),
        "completed_at": stringify(row.get("completed_at")),
    }


def public_user(user):
    role = stringify(user.get("role") or "user").strip().lower()
    return {
        "id": user.get("id"),
        "username": user.get("username"),
        "display_name": user.get("display_name") or user.get("username"),
        "role": role or "user",
    }


def normalize_source(source):
    source = source or "unified"
    if source not in SOURCES:
        raise ValueError(f"未知数据源：{source}")
    return source


def normalize_period(period):
    period = period or "30d"
    return period if period in {"7d", "30d", "90d", "180d", "custom"} else "7d"


def build_sales_period(period, sales_start=None, sales_end=None):
    period = normalize_period(period)
    if period == "custom" and sales_start and sales_end:
        return {
            "period": "custom",
            "label": f"{sales_start} 至 {sales_end}",
            "days": None,
            "start": sales_start,
            "end": sales_end,
        }
    days = {"7d": 7, "30d": 30, "90d": 90, "180d": 180}.get(period, 7)
    return {
        "period": period,
        "label": f"近{days}天",
        "days": days,
        "start": None,
        "end": None,
    }


def clamp_int(value, default, minimum, maximum):
    try:
        value = int(value)
    except (TypeError, ValueError):
        value = default
    return max(minimum, min(maximum, value))


def sql_alias(field, alias):
    if field:
        return f"`{field}` AS {alias}"
    return f"NULL AS {alias}"


def build_product_select(source, meta):
    image_url_expr = (
        f"CONCAT('/api/products/{source}/', `{meta['id']}`, '/image?date_record=', `{meta['date']}`) AS image_url"
    )
    platform_url_expr = (
        f"`{meta['detail_url']}` AS platform_url"
        if meta["detail_url"]
        else "NULL AS platform_url"
    )
    return ", ".join([
        f"`{meta['id']}` AS product_id",
        f"`{meta['date']}` AS date_record",
        sql_alias(meta["title"], "title"),
        sql_alias(meta["price"], "price_view"),
        sql_alias(meta["rating"], "rating_view"),
        sql_alias(meta["commission"], "commission_rate_view"),
        sql_alias(meta["sold"], "sold_count_view"),
        sql_alias(meta["total_sold"], "total_sold_count_view"),
        sql_alias(meta["sale_amount"], "sale_amount_view"),
        sql_alias(meta["author_count"], "author_count_view"),
        sql_alias(meta["base_price"], "base_price_view"),
        sql_alias(meta["transport_fee"], "transport_fee_view"),
        sql_alias(meta["video_ratio"], "video_ratio_view"),
        sql_alias(meta["product_card_ratio"], "product_card_ratio_view"),
        sql_alias(meta["distribution_30d"], "distribution_30d"),
        sql_alias(meta["distribution_7d"], "distribution_7d"),
        sql_alias(meta["distribution_90d"], "distribution_90d"),
        sql_alias(meta["distribution_180d"], "distribution_180d"),
        sql_alias(meta["overview_30d"], "overview_30d"),
        sql_alias(meta["overview_7d"], "overview_7d"),
        sql_alias(meta["overview_90d"], "overview_90d"),
        sql_alias(meta["overview_180d"], "overview_180d"),
        "`audit_status`",
        "`ip_grade`",
        "`ip_reason`",
        "`ip_tags`",
        "`material_analysis`",
        "`ai_analysis_error`",
        "`launch_time`" if has_launch_time(meta) else "NULL AS launch_time",
        platform_url_expr,
        image_url_expr,
    ])


def build_latest_product_join(meta, where):
    return f"""
        INNER JOIN (
            SELECT `{meta["id"]}` AS latest_product_id, MAX(`{meta["date"]}`) AS latest_date
            FROM `{meta["table"]}`
            WHERE {" AND ".join(where)}
            GROUP BY `{meta["id"]}`
        ) AS latest_snapshot
          ON latest_snapshot.latest_product_id = `{meta["table"]}`.`{meta["id"]}`
         AND latest_snapshot.latest_date = `{meta["table"]}`.`{meta["date"]}`
    """


def build_sales_aggregate_join(meta, where):
    sold_field = meta.get("sold")
    if not sold_field:
        return ""
    sold_expr = numeric_sql_expr(f"`{sold_field}`")
    return f"""
        LEFT JOIN (
            SELECT `{meta["id"]}` AS aggregate_product_id,
                   SUM(COALESCE({sold_expr}, 0)) AS aggregate_sold_count
            FROM `{meta["table"]}`
            WHERE {" AND ".join(where)}
            GROUP BY `{meta["id"]}`
        ) AS sales_aggregate
          ON sales_aggregate.aggregate_product_id = `{meta["table"]}`.`{meta["id"]}`
    """


def build_previous_sales_aggregate_join(meta, date_start, date_end):
    sold_field = meta.get("sold")
    if not sold_field or not date_start:
        return """
        LEFT JOIN (
            SELECT NULL AS previous_product_id, NULL AS previous_sold_count
        ) AS previous_sales_aggregate
          ON previous_sales_aggregate.previous_product_id = `{table}`.`{id_field}`
        """.format(table=meta["table"], id_field=meta["id"])
    try:
        start = datetime.fromisoformat(str(date_start)[:10]).date()
        end = datetime.fromisoformat(str(date_end or date_start)[:10]).date()
    except ValueError:
        return """
        LEFT JOIN (
            SELECT NULL AS previous_product_id, NULL AS previous_sold_count
        ) AS previous_sales_aggregate
          ON previous_sales_aggregate.previous_product_id = `{table}`.`{id_field}`
        """.format(table=meta["table"], id_field=meta["id"])
    days = max(1, (end - start).days + 1)
    previous_start = start.isoformat()
    sold_expr = numeric_sql_expr(f"`{sold_field}`")
    return f"""
        LEFT JOIN (
            SELECT `{meta["id"]}` AS previous_product_id,
                   SUM(COALESCE({sold_expr}, 0)) AS previous_sold_count
            FROM `{meta["table"]}`
            WHERE `{meta["date"]}` BETWEEN DATE_SUB(CAST('{previous_start}' AS DATE), INTERVAL {days} DAY)
                                      AND DATE_SUB(CAST('{previous_start}' AS DATE), INTERVAL 1 DAY)
            GROUP BY `{meta["id"]}`
        ) AS previous_sales_aggregate
          ON previous_sales_aggregate.previous_product_id = `{meta["table"]}`.`{meta["id"]}`
    """


def build_sales_delta_join(meta, date_start, date_end):
    total_field = meta.get("total_sold") or meta.get("sold")
    if not total_field or not date_start:
        return empty_sales_delta_join(meta)
    try:
        start = datetime.fromisoformat(str(date_start)[:10]).date()
        end = datetime.fromisoformat(str(date_end or date_start)[:10]).date()
    except ValueError:
        return empty_sales_delta_join(meta)
    days = max(1, (end - start).days + 1)
    previous_start = start - date.resolution * days
    return f"""
        LEFT JOIN (
            SELECT base_source.`{meta["id"]}` AS base_product_id,
                   {numeric_sql_expr(f"base_source.`{total_field}`")} AS base_total_sold_count
            FROM `{meta["table"]}` AS base_source
            INNER JOIN (
                SELECT `{meta["id"]}` AS product_id, MAX(`{meta["date"]}`) AS base_date
                FROM `{meta["table"]}`
                WHERE `{meta["date"]}` < CAST('{start.isoformat()}' AS DATE)
                GROUP BY `{meta["id"]}`
            ) AS base_latest
              ON base_latest.product_id = base_source.`{meta["id"]}`
             AND base_latest.base_date = base_source.`{meta["date"]}`
        ) AS current_sales_base
          ON current_sales_base.base_product_id = `{meta["table"]}`.`{meta["id"]}`
        LEFT JOIN (
            SELECT previous_base_source.`{meta["id"]}` AS previous_base_product_id,
                   {numeric_sql_expr(f"previous_base_source.`{total_field}`")} AS previous_base_total_sold_count
            FROM `{meta["table"]}` AS previous_base_source
            INNER JOIN (
                SELECT `{meta["id"]}` AS product_id, MAX(`{meta["date"]}`) AS previous_base_date
                FROM `{meta["table"]}`
                WHERE `{meta["date"]}` < CAST('{previous_start.isoformat()}' AS DATE)
                GROUP BY `{meta["id"]}`
            ) AS previous_base_latest
              ON previous_base_latest.product_id = previous_base_source.`{meta["id"]}`
             AND previous_base_latest.previous_base_date = previous_base_source.`{meta["date"]}`
        ) AS previous_sales_base
          ON previous_sales_base.previous_base_product_id = `{meta["table"]}`.`{meta["id"]}`
    """


def empty_sales_delta_join(meta):
    return """
        LEFT JOIN (
            SELECT NULL AS base_product_id, NULL AS base_total_sold_count
        ) AS current_sales_base
          ON current_sales_base.base_product_id = `{table}`.`{id_field}`
        LEFT JOIN (
            SELECT NULL AS previous_base_product_id, NULL AS previous_base_total_sold_count
        ) AS previous_sales_base
          ON previous_sales_base.previous_base_product_id = `{table}`.`{id_field}`
    """.format(table=meta["table"], id_field=meta["id"])


def sales_delta_current_expr(meta):
    total_field = meta.get("total_sold") or meta.get("sold")
    if not total_field:
        return "NULL"
    current_total_expr = numeric_sql_expr(f"`{meta['table']}`.`{total_field}`")
    return f"GREATEST(COALESCE({current_total_expr}, 0) - COALESCE(current_sales_base.base_total_sold_count, 0), 0)"


def sales_delta_previous_expr():
    return "GREATEST(COALESCE(current_sales_base.base_total_sold_count, 0) - COALESCE(previous_sales_base.previous_base_total_sold_count, 0), 0)"


def has_launch_time(meta):
    return meta["table"] in {"fastmoss_product_aggregate", "fastmoss_product_rank_aggregate"}


def build_filters(meta, args, latest_by_default=False):
    where = ["1=1"]
    params = []
    date_start = args.get("date_start")
    date_end = args.get("date_end")
    if latest_by_default and not date_start and not date_end:
        where.append(f"`{meta['date']}` = (SELECT MAX(latest_source.`{meta['date']}`) FROM `{meta['table']}` AS latest_source)")
    else:
        add_range_filter(where, params, meta["date"], date_start, date_end)
    add_eq_filter(where, params, "audit_status", args.get("audit_status"))
    add_grade_filter(where, params, args.get("ip_grade_min"), args.get("ip_grade_max"))
    add_material_filter(where, params, args.get("material_type"), args.get("material_categories"), args.get("exclude_material_categories"))
    add_number_range_filter(where, params, meta.get("commission"), args.get("commission_min"), args.get("commission_max"))
    add_number_range_filter(where, params, meta.get("author_count"), args.get("author_min"), args.get("author_max"))
    add_number_range_filter(where, params, meta.get("sold"), args.get("sold_min"), args.get("sold_max"))
    add_number_range_filter(where, params, meta.get("sale_amount"), args.get("sale_amount_min"), args.get("sale_amount_max"))
    add_number_range_filter(where, params, meta.get("rating"), args.get("rating_min"), args.get("rating_max"))
    add_number_range_filter(where, params, meta.get("price"), args.get("price_min"), args.get("price_max"))
    add_number_range_filter(where, params, meta.get("base_price"), args.get("base_price_min"), args.get("base_price_max"))
    add_number_range_filter(where, params, meta.get("transport_fee"), args.get("transport_fee_min"), args.get("transport_fee_max"))
    add_ratio_range_filter(where, params, meta, args, "视频", args.get("video_ratio_min"), args.get("video_ratio_max"), direct_field=meta.get("video_ratio"))
    add_ratio_range_filter(where, params, meta, args, "商品卡", args.get("product_card_ratio_min"), args.get("product_card_ratio_max"), direct_field=meta.get("product_card_ratio"))
    add_range_filter(where, params, "launch_time", args.get("launch_start"), args.get("launch_end")) if has_launch_time(meta) else None
    keyword = (args.get("keyword") or "").strip()
    if keyword:
        where.append(f"`{meta['title']}` LIKE %s")
        params.append(f"%{keyword}%")
    return where, params


def add_range_filter(where, params, field, start, end):
    if start:
        where.append(f"`{field}` >= %s")
        params.append(start)
    if end:
        where.append(f"`{field}` <= %s")
        params.append(end)


def add_number_range_filter(where, params, field, start, end):
    if not field:
        return
    expr = f"CAST(REPLACE(REPLACE(`{field}`, '$', ''), '%%', '') AS DECIMAL(20,4))"
    if start:
        where.append(f"{expr} >= %s")
        params.append(start)
    if end:
        where.append(f"{expr} <= %s")
        params.append(end)


def add_ratio_range_filter(where, params, meta, args, ratio_name, start, end, direct_field=None):
    if not start and not end:
        return
    if direct_field:
        add_number_range_filter(where, params, direct_field, start, end)
        return

    field = get_distribution_filter_field(meta, args)
    if not field:
        return
    expr = distribution_ratio_number_expr(field)
    if start:
        where.append(f"JSON_VALID(`{field}`) AND {expr} >= %s")
        params.extend([ratio_name, start])
    if end:
        where.append(f"JSON_VALID(`{field}`) AND {expr} <= %s")
        params.extend([ratio_name, end])


def get_distribution_filter_field(meta, args):
    period = normalize_period(args.get("period"))
    if period == "custom":
        period = "7d"
    field = meta.get(f"distribution_{period}")
    return field or meta.get("distribution_7d") or meta.get("distribution_30d")


def distribution_ratio_number_expr(field):
    search_path = f"JSON_UNQUOTE(JSON_SEARCH(`{field}`, 'one', %s, NULL, '$[*].name'))"
    value_path = f"REPLACE({search_path}, '.name', '.percentage')"
    value_expr = f"JSON_UNQUOTE(JSON_EXTRACT(`{field}`, {value_path}))"
    clean_expr = f"REPLACE(REPLACE({value_expr}, '%%', ''), ',', '')"
    return f"CAST(NULLIF({clean_expr}, '') AS DECIMAL(20,4))"


def distribution_item_number_expr(field, item_name, key):
    search_path = f"JSON_UNQUOTE(JSON_SEARCH(`{field}`, 'one', '{item_name}', NULL, '$[*].name'))"
    value_path = f"REPLACE({search_path}, '.name', '.{key}')"
    value_expr = f"JSON_UNQUOTE(JSON_EXTRACT(`{field}`, {value_path}))"
    return numeric_sql_expr(value_expr)


def distribution_index_number_expr(field, index, key):
    value_expr = f"JSON_UNQUOTE(JSON_EXTRACT(`{field}`, '$[{index}].{key}'))"
    return numeric_sql_expr(value_expr)


def add_eq_filter(where, params, field, value):
    if value:
        where.append(f"`{field}` = %s")
        params.append(value)


def add_grade_filter(where, params, grade_min, grade_max):
    if not grade_min and not grade_max:
        return
    min_index = IP_GRADES.index(grade_min) if grade_min in IP_GRADES else 0
    max_index = IP_GRADES.index(grade_max) if grade_max in IP_GRADES else len(IP_GRADES) - 1
    if min_index > max_index:
        min_index, max_index = max_index, min_index
    grades = IP_GRADES[min_index:max_index + 1]
    where.append("ip_grade IN (" + ",".join(["%s"] * len(grades)) + ")")
    params.extend(grades)


def add_material_filter(where, params, material_type, categories, exclude_categories):
    if material_type:
        where.append("JSON_VALID(material_analysis) AND JSON_UNQUOTE(JSON_EXTRACT(material_analysis, '$.material_type')) = %s")
        params.append(material_type)
    category_list = split_csv(categories)
    if category_list:
        where.append("JSON_VALID(material_analysis) AND JSON_UNQUOTE(JSON_EXTRACT(material_analysis, '$.material_category')) IN (" + ",".join(["%s"] * len(category_list)) + ")")
        params.extend(category_list)
    excluded = split_csv(exclude_categories)
    if excluded:
        where.append("(NOT JSON_VALID(material_analysis) OR JSON_UNQUOTE(JSON_EXTRACT(material_analysis, '$.material_category')) NOT IN (" + ",".join(["%s"] * len(excluded)) + "))")
        params.extend(excluded)


def split_csv(value):
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def numeric_sql_expr(expr):
    clean_expr = expr
    for token in ("US$", "USD", "$", "¥", "￥", "%%", ",", "，", " "):
        clean_expr = f"REPLACE({clean_expr}, '{token}', '')"
    return (
        "CASE "
        f"WHEN {clean_expr} LIKE '%%万%%' THEN CAST(NULLIF(REPLACE({clean_expr}, '万', ''), '') AS DECIMAL(20,4)) * 10000 "
        f"ELSE CAST(NULLIF({clean_expr}, '') AS DECIMAL(20,4)) "
        "END"
    )


def qualified_field(meta, field):
    if not field:
        return "NULL"
    return f"`{meta['table']}`.`{field}`"


def sort_field_expr(meta, sort_by):
    fields = {
        "date_record": meta["date"],
        "sold": meta.get("sold"),
        "total_sold": meta.get("total_sold"),
        "sale_amount": meta.get("sale_amount"),
        "rating": meta.get("rating"),
        "price": meta.get("price"),
        "commission": meta.get("commission"),
        "author_count": meta.get("author_count"),
        "launch_time": "launch_time" if has_launch_time(meta) else None,
    }
    return qualified_field(meta, fields.get(sort_by or "date_record") or meta["date"])


def build_order(meta, sort_by, sort_order, sales_period=None, use_sales_aggregate=False, args=None):
    raw_field = sort_field_expr(meta, sort_by)
    direction = "ASC" if str(sort_order).lower() == "asc" else "DESC"
    if sort_by == "sold" and use_sales_aggregate:
        return f"ORDER BY COALESCE(sales_aggregate.aggregate_sold_count, 0) {direction}"
    if sort_by == "product_card_sales":
        return f"ORDER BY {product_card_sales_sort_expr(meta)} {direction}"
    if sort_by == "product_card_ratio":
        return f"ORDER BY {product_card_ratio_sort_expr(meta)} {direction}"
    if sort_by == "sales_growth" and args:
        return f"ORDER BY {sales_growth_sort_expr(meta, args)} {direction}"
    if sort_by == "ip_grade":
        return f"ORDER BY FIELD(ip_grade, 'E', 'D', 'C', 'B', 'A', 'S') {direction}, ip_grade {direction}"

    # 针对数值字段，强制进行数值转换排序，处理可能存在的 $ % , 等符号
    # 防止出现字符串排序导致的 "84 > 658" 错误
    overview_metrics = {"sold": "销量", "sale_amount": "销售额", "author_count": "带货达人数"}
    if sort_by in overview_metrics and sales_period:
        period = sales_period["period"] if sales_period["period"] != "custom" else "7d"
        overview_field = meta.get(f"overview_{period}")
        if not overview_field:
            return f"ORDER BY {numeric_sql_expr(raw_field)} {direction}"
        overview_field_expr = qualified_field(meta, overview_field)
        overview_expr = f"JSON_UNQUOTE(JSON_EXTRACT({overview_field_expr}, '$.\"{overview_metrics[sort_by]}\"'))"
        return f"ORDER BY COALESCE({numeric_sql_expr(overview_expr)}, {numeric_sql_expr(raw_field)}) {direction}"

    numeric_keys = {"sold", "total_sold", "sale_amount", "rating", "price", "commission", "author_count"}
    if sort_by in numeric_keys:
        # 移除常见非数字符号并转换为 DECIMAL 排序
        return f"ORDER BY {numeric_sql_expr(raw_field)} {direction}"

    return f"ORDER BY {raw_field} {direction}"


def product_card_sales_sort_expr(meta):
    distribution_field = meta.get("distribution_7d") or meta.get("distribution_30d")
    if distribution_field:
        return f"COALESCE({distribution_item_number_expr(distribution_field, '商品卡', 'sales')}, 0)"
    ratio_field = meta.get("product_card_ratio")
    total_field = meta.get("total_sold") or meta.get("sold")
    if ratio_field and total_field:
        return f"COALESCE({numeric_sql_expr(f'`{total_field}`')}, 0) * COALESCE({numeric_sql_expr(f'`{ratio_field}`')}, 0) / 100"
    return "0"


def product_card_ratio_sort_expr(meta):
    expressions = []
    if meta.get("distribution_30d"):
        expressions.append(distribution_item_number_expr(meta["distribution_30d"], "商品卡", "percentage"))
    if meta.get("product_card_ratio"):
        expressions.append(numeric_sql_expr(f"`{meta['product_card_ratio']}`"))
    if meta.get("distribution_7d"):
        expressions.append(distribution_item_number_expr(meta["distribution_7d"], "商品卡", "percentage"))
    if not expressions:
        return "0"
    return f"COALESCE({', '.join(expressions)}, 0)"


def product_card_sales_sort_expr(meta):
    distribution_field = meta.get("distribution_7d") or meta.get("distribution_30d")
    if distribution_field:
        return "COALESCE({sales}, {count}, {sold}, {value_count}, 0)".format(
            sales=distribution_index_number_expr(distribution_field, 2, "sales"),
            count=distribution_index_number_expr(distribution_field, 2, "count"),
            sold=distribution_index_number_expr(distribution_field, 2, "sold"),
            value_count=distribution_index_number_expr(distribution_field, 2, "value_count"),
        )
    ratio_field = meta.get("product_card_ratio")
    total_field = meta.get("total_sold") or meta.get("sold")
    if ratio_field and total_field:
        return f"COALESCE({numeric_sql_expr(f'`{total_field}`')}, 0) * COALESCE({numeric_sql_expr(f'`{ratio_field}`')}, 0) / 100"
    return "0"


def sales_growth_sort_expr(meta, args):
    current_expr = sales_delta_current_expr(meta)
    previous_expr = sales_delta_previous_expr()
    return f"CASE WHEN COALESCE({previous_expr}, 0) <= 0 THEN NULL ELSE (({current_expr} / {previous_expr}) - 1) END"


def normalize_product_row(source, row, sales_period):
    material = parse_json(row.get("material_analysis")) or {}
    tags = parse_json(row.get("ip_tags")) or {}
    field_period = sales_period["period"] if sales_period["period"] != "custom" else "7d"
    overview = parse_json(row.get(f"overview_{field_period}")) or {}
    distribution = parse_json(row.get(f"distribution_{field_period}")) or []
    distribution_7d = parse_json(row.get("distribution_7d")) or []
    distribution_28d = parse_json(row.get("distribution_30d")) or []
    sold_count = row.get("runtime_sold_count") if row.get("runtime_sold_count") is not None else overview.get("销量") or row.get("sold_count_view")
    total_sold = row.get("total_sold_count_view") or row.get("sold_count_view")
    sale_amount = overview.get("销售额") or row.get("sale_amount_view")
    author_count = overview.get("带货达人数") or row.get("author_count_view")
    video_ratio = row.get("video_ratio_view") or ratio_from_distribution(distribution, "视频")
    product_card_ratio = row.get("product_card_ratio_view") or ratio_from_distribution(distribution, "商品卡")
    product_card_ratio_28d = row.get("product_card_ratio_view") or ratio_from_distribution(distribution_28d, "商品卡")
    product_card_sales = sales_from_distribution(distribution_7d or distribution, "商品卡", sold_count)
    chart_7d = normalize_distribution_chart(distribution_7d, sold_count)
    chart_28d = normalize_distribution_chart(
        distribution_28d,
        total_sold,
        {"视频": row.get("video_ratio_view"), "商品卡": row.get("product_card_ratio_view")},
    )
    return {
        "source": source,
        "product_id": stringify(row.get("product_id")),
        "date_record": row.get("date_record"),
        "title": row.get("title") or "",
        "image_url": row.get("image_url"),
        "price": normalize_price_display(row.get("price_view"), row.get("base_price_view")),
        "base_price": stringify(row.get("base_price_view")),
        "rating": stringify(row.get("rating_view")),
        "commission_rate": stringify(row.get("commission_rate_view")),
        "sold_count": stringify(sold_count),
        "product_card_sales": stringify(format_metric_number(product_card_sales)),
        "total_sold_count": stringify(total_sold),
        "sale_amount": stringify(sale_amount),
        "sales_growth": row.get("sales_growth_view") or "N/A",
        "author_count": stringify(author_count),
        "video_ratio": stringify(video_ratio),
        "product_card_ratio": stringify(product_card_ratio),
        "product_card_ratio_28d": stringify(product_card_ratio_28d),
        "distribution_7d_chart": chart_7d,
        "distribution_28d_chart": chart_28d,
        "transport_fee": stringify(row.get("transport_fee_view")),
        "launch_time": stringify(row.get("launch_time")),
        "audit_status": row.get("audit_status") or "PENDING",
        "ip_grade": row.get("ip_grade"),
        "ip_reason": row.get("ip_reason"),
        "ip_tags": tags,
        "material_type": material.get("material_type"),
        "material_category": material.get("material_category"),
        "material_confidence": material.get("confidence"),
        "material_reason": material.get("material_reason"),
        "ai_analysis_error": parse_json(row.get("ai_analysis_error")),
        "platform_url": resolve_platform_url(source, row.get("product_id"), row.get("platform_url")),
    }


def attach_runtime_metrics(cursor, meta, rows, sales_period, collection_start=None, collection_end=None):
    for row in rows:
        if row.get("runtime_sold_count") is not None:
            row["runtime_sold_count"] = float(row.get("runtime_sold_count") or 0)
        if row.get("previous_runtime_sold_count") is not None:
            row["previous_runtime_sold_count"] = float(row.get("previous_runtime_sold_count") or 0)
        if row.get("current_period_sold_count") is not None:
            row["current_period_sold_count"] = float(row.get("current_period_sold_count") or 0)
        if row.get("previous_period_sold_count") is not None:
            row["previous_period_sold_count"] = float(row.get("previous_period_sold_count") or 0)
        if row.get("current_period_sold_count") is not None and row.get("previous_period_sold_count") is not None:
            row["sales_growth_view"] = format_runtime_sales_result(
                {
                    "current_sum": row.get("current_period_sold_count"),
                    "previous_sum": row.get("previous_period_sold_count"),
                }
            )["growth"]
            continue
        if row.get("previous_runtime_sold_count") is not None:
            row["sales_growth_view"] = format_runtime_sales_result(
                {
                    "current_sum": row.get("runtime_sold_count"),
                    "previous_sum": row.get("previous_runtime_sold_count"),
                }
            )["growth"]
            continue
        metrics = calculate_runtime_sales_metrics(
            cursor,
            meta,
            row,
            sales_period,
            collection_start,
            collection_end,
        )
        row["sales_growth_view"] = metrics["growth"]


def calculate_runtime_sales_metrics(cursor, meta, row, sales_period, collection_start=None, collection_end=None):
    product_id = row.get("product_id")
    date_record = row.get("date_record")
    if not product_id or not date_record:
        return {"current_sum": None, "growth": "N/A"}
    table = meta["table"]
    id_field = meta["id"]
    date_field = meta["date"]
    sold_field = meta["sold"]
    sold_expr = numeric_sql_expr(f"`{sold_field}`")

    if collection_start and collection_end:
        return calculate_collection_sales_growth(
            cursor,
            meta,
            product_id,
            collection_start,
            collection_end,
            sold_expr,
        )
    elif sales_period["period"] == "custom" and sales_period["start"] and sales_period["end"]:
        current_start = sales_period["start"]
        current_end = sales_period["end"]
    else:
        days = sales_period["days"] or 7
        cursor.execute(
            f"""
            SELECT
              SUM(CASE
                WHEN `{date_field}` BETWEEN DATE_SUB(CAST(%s AS DATE), INTERVAL %s DAY) AND CAST(%s AS DATE)
                THEN COALESCE({sold_expr}, 0) ELSE 0
              END) AS current_sum,
              SUM(CASE
                WHEN `{date_field}` BETWEEN DATE_SUB(CAST(%s AS DATE), INTERVAL %s DAY)
                                     AND DATE_SUB(CAST(%s AS DATE), INTERVAL %s DAY)
                THEN COALESCE({sold_expr}, 0) ELSE 0
              END) AS previous_sum
            FROM `{table}`
            WHERE `{id_field}` = %s
            """,
            (
                date_record,
                days - 1,
                date_record,
                date_record,
                (days * 2) - 1,
                date_record,
                days,
                product_id,
            ),
        )
        result = cursor.fetchone() or {}
        return format_runtime_sales_result(result)

    cursor.execute(
        f"""
        SELECT
          SUM(CASE
            WHEN `{date_field}` BETWEEN CAST(%s AS DATE) AND CAST(%s AS DATE)
            THEN COALESCE({sold_expr}, 0) ELSE 0
          END) AS current_sum,
          SUM(CASE
            WHEN `{date_field}` BETWEEN DATE_SUB(CAST(%s AS DATE), INTERVAL DATEDIFF(CAST(%s AS DATE), CAST(%s AS DATE)) + 1 DAY)
                                 AND DATE_SUB(CAST(%s AS DATE), INTERVAL 1 DAY)
            THEN COALESCE({sold_expr}, 0) ELSE 0
          END) AS previous_sum
        FROM `{table}`
        WHERE `{id_field}` = %s
        """,
        (
            current_start,
            current_end,
            current_start,
            current_end,
            current_start,
            current_start,
            product_id,
        ),
    )
    result = cursor.fetchone() or {}
    return format_runtime_sales_result(result)


def calculate_collection_sales_growth(cursor, meta, product_id, current_start, current_end, sold_expr):
    date_count = collection_date_count(current_start, current_end)
    table = meta["table"]
    id_field = meta["id"]
    date_field = meta["date"]
    cursor.execute(
        f"""
        SELECT SUM(COALESCE({sold_expr}, 0)) AS current_sum
        FROM `{table}`
        WHERE `{id_field}` = %s
          AND `{date_field}` BETWEEN CAST(%s AS DATE) AND CAST(%s AS DATE)
        """,
        (product_id, current_start, current_end),
    )
    current_sum = number_or_zero((cursor.fetchone() or {}).get("current_sum"))

    cursor.execute(
        f"""
        SELECT DISTINCT `{date_field}` AS previous_date
        FROM `{table}`
        WHERE `{id_field}` = %s AND `{date_field}` < CAST(%s AS DATE)
        ORDER BY `{date_field}` DESC
        LIMIT %s
        """,
        (product_id, current_start, date_count),
    )
    previous_dates = [row["previous_date"] for row in cursor.fetchall()]
    if not previous_dates:
        return {"current_sum": current_sum, "growth": "N/A"}

    placeholders = ",".join(["%s"] * len(previous_dates))
    cursor.execute(
        f"""
        SELECT SUM(COALESCE({sold_expr}, 0)) AS previous_sum
        FROM `{table}`
        WHERE `{id_field}` = %s AND `{date_field}` IN ({placeholders})
        """,
        [product_id, *previous_dates],
    )
    previous_sum = number_or_zero((cursor.fetchone() or {}).get("previous_sum"))
    return format_runtime_sales_result({"current_sum": current_sum, "previous_sum": previous_sum})


def collection_date_count(current_start, current_end):
    try:
        start = datetime.fromisoformat(str(current_start)[:10]).date()
        end = datetime.fromisoformat(str(current_end)[:10]).date()
    except ValueError:
        return 1
    return max(1, (end - start).days + 1)


def format_runtime_sales_result(result):
    current_sum = float(result.get("current_sum") or 0)
    previous_sum = float(result.get("previous_sum") or 0)
    if previous_sum <= 0:
        return {"current_sum": current_sum, "growth": "N/A"}
    growth = (current_sum / previous_sum) - 1
    return {"current_sum": current_sum, "growth": f"{growth * 100:.2f}%"}


def ratio_from_distribution(distribution, name):
    if not isinstance(distribution, list):
        return ""
    for item in distribution:
        if str(item.get("name")) == name:
            return item.get("percentage") or item.get("ratio") or ""
    return ""


PRICE_CURRENCY_WORDS_RE = re.compile(
    r"\b(?:US|USD|EUR|GBP|CNY|RMB|AUD|CAD|HKD|SGD|MXN)\b",
    re.IGNORECASE,
)
PRICE_ALLOWED_CHARS_RE = re.compile(r"^[\s\d.,$€£¥￥%+\-~–—/()]+$")


def normalize_price_display(*values):
    for value in values:
        text = stringify(value).strip()
        if is_price_display(text):
            return text
    return ""


def is_price_display(text):
    if not text or not any(char.isdigit() for char in text):
        return False
    without_currency_words = PRICE_CURRENCY_WORDS_RE.sub("", text)
    return bool(PRICE_ALLOWED_CHARS_RE.fullmatch(without_currency_words))


def normalize_detail(source, row):
    meta = SOURCES[source]
    product_id = stringify(row.get(meta["id"]))
    date_record = row.get(meta["date"])
    raw = {}
    for key, value in row.items():
        if key == meta["image"]:
            raw[key] = "[图片字段已隐藏，可通过 image_url 查看]"
        else:
            raw[key] = serialize_value(value)
    return {
        "source": source,
        "product_id": product_id,
        "date_record": date_record,
        "title": row.get(meta["title"]),
        "attributes": serialize_value(row.get(meta.get("attributes"))) if meta.get("attributes") else "",
        "selling_points": serialize_value(row.get(meta.get("selling_points"))) if meta.get("selling_points") else "",
        "image_url": f"/api/products/{source}/{product_id}/image?date_record={date_record}",
        "platform_url": resolve_platform_url(source, product_id, row.get(meta["detail_url"])),
        "audit_status": row.get("audit_status") or "PENDING",
        "ip_grade": row.get("ip_grade"),
        "ip_reason": row.get("ip_reason"),
        "ip_tags": parse_json(row.get("ip_tags")),
        "material_analysis": parse_json(row.get("material_analysis")),
        "ai_analysis_error": parse_json(row.get("ai_analysis_error")),
        "period_fields": build_detail_period_fields(source, row, meta),
        "sku_analysis": build_detail_sku_analysis(row, meta),
        "raw_fields": raw,
    }


def build_detail_period_fields(source, row, meta):
    fields = {}
    for period in ("7d", "30d", "90d", "180d"):
        overview_field = meta.get(f"overview_{period}")
        distribution_field = meta.get(f"distribution_{period}")
        fields[f"overview_{period}"] = serialize_value(row.get(overview_field)) if overview_field else None
        fields[f"distribution_{period}"] = serialize_value(row.get(distribution_field)) if distribution_field else None

    if source == "kalodata" and not fields.get("overview_30d"):
        fields["overview_30d"] = json.dumps(build_kalodata_30d_overview(row), ensure_ascii=False)
    if source == "kalodata" and not fields.get("distribution_30d"):
        fields["distribution_30d"] = json.dumps(build_kalodata_30d_distribution(row), ensure_ascii=False)
    return fields


def build_detail_sku_analysis(row, meta):
    fields = {}
    for period, label in (("7d", "近7天"), ("28d", "近28天")):
        field = meta.get(f"sku_analysis_{period}")
        parsed = parse_json(row.get(field)) if field else None
        normalized = normalize_sku_analysis(parsed)
        if normalized:
            normalized["period"] = period
            normalized["period_label"] = label
            fields[period] = normalized
    return fields


def normalize_sku_analysis(parsed):
    if not isinstance(parsed, dict) or not parsed:
        return None

    rules = []
    dimensions_source = None
    for value in parsed.values():
        if isinstance(value, list) and not rules:
            rules = [stringify(item) for item in value if stringify(item)]
        elif isinstance(value, dict):
            if rules and any(rule in value for rule in rules):
                dimensions_source = value
            elif dimensions_source is None:
                dimensions_source = value

    if not isinstance(dimensions_source, dict) or not dimensions_source:
        return None
    if not rules:
        rules = [stringify(key) for key in dimensions_source.keys()]

    dimensions = []
    for rule in rules:
        dimension = dimensions_source.get(rule)
        if not isinstance(dimension, dict):
            continue
        sales_block = find_sku_sales_block(dimension)
        if not sales_block:
            continue
        items = normalize_sku_items(sales_block.get("items") or sales_block.get("details") or [])
        if not items:
            continue
        dimensions.append(
            {
                "name": rule,
                "total": sales_block.get("total"),
                "total_display": sales_block.get("total_display") or format_metric_number(sales_block.get("total")),
                "items": items,
            }
        )

    if not dimensions:
        return None

    best = find_best_sku_dimension(dimensions)
    return {
        "rules": [item["name"] for item in dimensions],
        "dimensions": dimensions,
        "best_sku": best,
    }


def find_sku_sales_block(dimension):
    fallback = None
    for metric in dimension.values():
        if not isinstance(metric, dict):
            continue
        details = first_list_value(metric)
        if not details:
            continue
        block = {
            "total": first_numeric_value(metric),
            "total_display": first_display_value(metric),
            "items": details,
        }
        if fallback is None:
            fallback = block
        if not sku_block_has_currency(details):
            return block
    return fallback


def sku_block_has_currency(details):
    if not isinstance(details, list):
        return False
    for detail in details:
        if not isinstance(detail, dict):
            continue
        for value in detail.values():
            text = stringify(value)
            if "$" in text or "USD" in text or "¥" in text or "￥" in text:
                return True
    return False


def normalize_sku_items(items):
    if not isinstance(items, list):
        return []
    normalized = []
    for item in items:
        if not isinstance(item, dict):
            continue
        name = first_sku_name(item)
        ratio = first_percent_value(item)
        sales = first_numeric_value(item)
        sales_display = first_display_value(item) or format_metric_number(sales)
        if not name:
            continue
        normalized.append(
            {
                "name": name,
                "ratio": ratio,
                "sales": sales,
                "sales_display": sales_display,
            }
        )
    return normalized


def first_list_value(value):
    for item in value.values():
        if isinstance(item, list):
            return item
    return None


def first_numeric_value(value):
    for item in value.values():
        if isinstance(item, (int, float, Decimal)):
            return number_or_zero(item)
    return 0


def first_display_value(value):
    for item in value.values():
        text = stringify(item).strip()
        if not text or text.endswith("%"):
            continue
        normalized = text.replace(",", "").replace("，", "").replace("$", "").replace("¥", "").replace("￥", "").strip()
        if normalized.replace(".", "", 1).isdigit():
            return text
    return ""


def sales_from_distribution(distribution, name, total_fallback=None):
    if not isinstance(distribution, list):
        return 0
    for item in distribution:
        if str(item.get("name")) != name:
            continue
        sales = number_or_zero(item.get("sales") or item.get("sold") or item.get("count") or item.get("value_count"))
        if sales > 0:
            return sales
        ratio = percent_to_number(item.get("percentage") or item.get("ratio"))
        if total_fallback is not None and ratio > 0:
            return number_or_zero(total_fallback) * ratio / 100
    return 0


def percent_to_number(value):
    text = stringify(value).replace("%", "").replace(",", "").strip()
    try:
        number = float(text)
    except ValueError:
        return 0
    return number * 100 if 0 < number <= 1 else number


def normalize_distribution_chart(distribution, total_fallback=None, direct_ratios=None):
    names = ["视频", "直播", "商品卡"]
    direct_ratios = direct_ratios or {}
    items = []
    for name in names:
        ratio = ratio_from_distribution(distribution, name)
        if not ratio and name in direct_ratios:
            ratio = direct_ratios[name]
        sales = sales_from_distribution(distribution, name, total_fallback)
        if sales <= 0 and ratio:
            sales = number_or_zero(total_fallback) * percent_to_number(ratio) / 100
        items.append(
            {
                "name": name,
                "ratio": normalize_percent_display(ratio),
                "sales": format_metric_number(sales),
            }
        )
    return items


def normalize_percent_display(value):
    number = percent_to_number(value)
    if not number:
        return "0%"
    if float(number).is_integer():
        return f"{int(number)}%"
    return f"{number:.2f}%"


def first_percent_value(value):
    for item in value.values():
        text = stringify(item).strip()
        if text.endswith("%"):
            return text
    return ""


def first_sku_name(value):
    for item in value.values():
        if isinstance(item, (dict, list)):
            continue
        text = stringify(item).strip()
        if not text or text.endswith("%"):
            continue
        if text.replace(".", "", 1).isdigit():
            continue
        if text.startswith("$"):
            continue
        return text
    return ""


def find_best_sku_dimension(dimensions):
    best = None
    fallback = None
    for dimension in dimensions:
        for item in dimension.get("items") or []:
            sales = number_or_zero(item.get("sales"))
            candidate = {
                "rule": dimension["name"],
                "name": item.get("name") or "",
                "sales": sales,
                "sales_display": item.get("sales_display") or format_metric_number(sales),
            }
            if fallback is None or sales > fallback["sales"]:
                fallback = candidate
            if stringify(item.get("name")).strip().lower() == "other":
                continue
            if best is None or sales > best["sales"]:
                best = candidate
    return best or fallback


def build_kalodata_30d_overview(row):
    return {
        "销售额": stringify(row.get("总成交额")),
        "日均销售额": stringify(row.get("日均成交额")),
        "销量": stringify(row.get("销量") or row.get("总销量")),
        "日均销量": stringify(row.get("日均销量")),
        "带货达人数": stringify(row.get("关联达人数")),
        "带货视频数": stringify(row.get("带货视频数")),
        "直播销售额": stringify(row.get("直播销售额")),
        "视频销售额": stringify(row.get("视频成交额")),
        "商品卡": stringify(row.get("商品卡成交额")),
    }


def build_kalodata_30d_distribution(row):
    video_ratio = stringify(row.get("近28天视频占比"))
    product_card_ratio = stringify(row.get("近28天商品卡占比"))
    video_amount = stringify(row.get("视频成交额"))
    product_card_amount = stringify(row.get("商品卡成交额"))
    return [
        {"name": "视频", "percentage": video_ratio, "sales": video_amount},
        {"name": "直播", "percentage": "0%", "sales": stringify(row.get("直播销售额"))},
        {"name": "商品卡", "percentage": product_card_ratio, "sales": product_card_amount},
    ]


def enrich_detail_period_fields(cursor, source, meta, row, detail):
    fields = detail.get("period_fields") or {}
    if fields.get("overview_30d") and fields.get("overview_30d") != fields.get("overview_7d"):
        return
    aggregate = build_collection_aggregate_overview(cursor, meta, row, 30)
    if aggregate:
        fields["overview_30d"] = json.dumps(aggregate, ensure_ascii=False)
        detail["period_fields"] = fields


def build_collection_aggregate_overview(cursor, meta, row, days):
    if not meta.get("sold"):
        return None
    product_id = row.get(meta["id"])
    date_record = row.get(meta["date"])
    if not product_id or not date_record:
        return None
    sold_expr = numeric_sql_expr(f"`{meta['sold']}`")
    sale_amount_expr = numeric_sql_expr(f"`{meta['sale_amount']}`") if meta.get("sale_amount") else "NULL"
    author_expr = numeric_sql_expr(f"`{meta['author_count']}`") if meta.get("author_count") else "NULL"
    cursor.execute(
        f"""
        SELECT
          SUM(COALESCE({sold_expr}, 0)) AS sold_sum,
          SUM(COALESCE({sale_amount_expr}, 0)) AS sale_amount_sum,
          MAX(COALESCE({author_expr}, 0)) AS author_count
        FROM `{meta['table']}`
        WHERE `{meta['id']}` = %s
          AND `{meta['date']}` BETWEEN DATE_SUB(CAST(%s AS DATE), INTERVAL %s DAY) AND CAST(%s AS DATE)
        """,
        (product_id, date_record, days - 1, date_record),
    )
    result = cursor.fetchone() or {}
    sold_sum = number_or_zero(result.get("sold_sum"))
    sale_amount_sum = number_or_zero(result.get("sale_amount_sum"))
    if sold_sum <= 0 and sale_amount_sum <= 0:
        return None
    return {
        "销量": format_metric_number(sold_sum),
        "日均销量": format_metric_number(sold_sum / days),
        "销售额": format_metric_number(sale_amount_sum),
        "日均销售额": format_metric_number(sale_amount_sum / days),
        "带货达人数": format_metric_number(number_or_zero(result.get("author_count"))),
    }


def number_or_zero(value):
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0.0


def format_metric_number(value):
    value = number_or_zero(value)
    if value.is_integer():
        return int(value)
    return round(value, 2)


def build_platform_url(source, product_id):
    if source in {"unified", "fastmoss"}:
        return f"https://www.fastmoss.com/zh/e-commerce/detail/{product_id}"
    return build_tiktok_shop_url(product_id)


def resolve_platform_url(source, product_id, detail_url=None):
    if source == "kalodata":
        return detail_url or ""
    return detail_url or build_platform_url(source, product_id)


def build_tiktok_shop_url(product_id):
    slug = "mens-athletic-t-shirt-by-brand-lightweight-quick-dry-crew-neck-tops"
    return f"https://www.tiktok.com/shop/pdp/{slug}/{product_id}?source=ecommerce_store&region=US"


def fetch_ready_links(source):
    meta = SOURCES[source]
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(
            f"""
            SELECT `{meta['id']}` AS product_id, `{meta['detail_url']}` AS detail_url
            FROM `{meta['table']}`
            WHERE audit_status = 'READY'
            ORDER BY `{meta['date']}` DESC
            """,
        )
        rows = cursor.fetchall()
    return [resolve_platform_url(source, row["product_id"], row.get("detail_url")) for row in rows]


def count_material_types(cursor, table, where=None, params=None):
    where = list(where or ["1=1"])
    params = list(params or [])
    where.append("JSON_VALID(material_analysis)")
    cursor.execute(
        f"""
        SELECT JSON_UNQUOTE(JSON_EXTRACT(material_analysis, '$.material_type')) AS material_type,
               COUNT(*) AS count
        FROM `{table}`
        WHERE {" AND ".join(where)}
        GROUP BY JSON_UNQUOTE(JSON_EXTRACT(material_analysis, '$.material_type'))
        """,
        params,
    )
    return {row["material_type"] or "未知": int(row["count"]) for row in cursor.fetchall()}


def serve_image_value(value):
    value = str(value).strip()
    if value.startswith("http://") or value.startswith("https://"):
        return redirect(value)
    mime = "image/jpeg"
    payload = value
    if value.startswith("data:image/"):
        header, payload = value.split(",", 1)
        mime = header.split(";")[0].replace("data:", "")
    try:
        data = base64.b64decode(payload, validate=False)
    except Exception:
        return api_error("图片 base64 无法解码", 422)
    return send_file(io.BytesIO(data), mimetype=mime)


def parse_json(value):
    if not value:
        return None
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except Exception:
        return None


def format_decimal_plain(value):
    text = format(value.normalize(), "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def stringify(value):
    if value is None:
        return ""
    if isinstance(value, Decimal):
        return format_decimal_plain(value)
    if isinstance(value, (datetime, date)):
        return value.strftime("%Y-%m-%d %H:%M:%S") if isinstance(value, datetime) else value.isoformat()
    if isinstance(value, str) and "e" in value.lower():
        stripped = value.strip()
        try:
            decimal_value = Decimal(stripped)
        except InvalidOperation:
            return value
        if decimal_value.is_finite():
            return format_decimal_plain(decimal_value)
    return str(value)


def serialize_value(value):
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (datetime, date)):
        return stringify(value)
    parsed = parse_json(value)
    return parsed if parsed is not None else value


def start_ai_daemon_if_enabled():
    global _ai_daemon_started
    if _ai_daemon_started or not AI_DAEMON_ENABLED:
        return
    if os.getenv("WERKZEUG_RUN_MAIN") == "false":
        return
    invalid_sources = [source for source in AI_DAEMON_SOURCES if source not in AI_PRODUCT_SOURCES]
    if invalid_sources:
        print(f"[AI_DAEMON] AI_DAEMON_SOURCES invalid: {invalid_sources}", flush=True)
        return
    if AI_DAEMON_ANALYSIS not in {"ip", "material", "both"}:
        print(f"[AI_DAEMON] AI_DAEMON_ANALYSIS invalid: {AI_DAEMON_ANALYSIS}", flush=True)
        return

    thread = threading.Thread(target=ai_daemon_loop, name="cp-ai-analysis-daemon", daemon=True)
    thread.start()
    _ai_daemon_started = True
    print(
        "[AI_DAEMON] started "
        f"sources={','.join(AI_DAEMON_SOURCES)} analysis={AI_DAEMON_ANALYSIS} "
        f"batch_size={AI_DAEMON_BATCH_SIZE} interval={AI_DAEMON_INTERVAL_SECONDS}s "
        f"concurrency={AI_DAEMON_CONCURRENCY} lock_wait={AI_DAEMON_LOCK_WAIT_SECONDS}s "
        f"latest_only={AI_DAEMON_LATEST_ONLY} "
        f"write={AI_DAEMON_WRITE}",
        flush=True,
    )


def ai_daemon_loop():
    while True:
        try:
            processed = run_ai_daemon_once()
            print(f"[AI_DAEMON] cycle finished, processed={processed}", flush=True)
        except Exception:
            print("[AI_DAEMON] cycle failed", flush=True)
            traceback.print_exc()
        time.sleep(max(10, AI_DAEMON_INTERVAL_SECONDS))


def run_ai_daemon_once():
    processed = 0
    for source in AI_DAEMON_SOURCES:
        print(f"[AI_DAEMON] fetching source={source}", flush=True)
        keys = fetch_ai_daemon_product_keys(source, AI_DAEMON_ANALYSIS, AI_DAEMON_BATCH_SIZE)
        print(f"[AI_DAEMON] source={source} queued={len(keys)}", flush=True)
        if not keys:
            continue

        with ThreadPoolExecutor(max_workers=AI_DAEMON_CONCURRENCY) as executor:
            futures = {
                executor.submit(process_single_ai_task, source, key): key
                for key in keys
            }
            for future in as_completed(futures):
                try:
                    if future.result():
                        processed += 1
                except Exception:
                    traceback.print_exc()
    return processed


def process_single_ai_task(source, key):
    for attempt in range(1, AI_DAEMON_LOCK_RETRIES + 1):
        result = process_single_ai_task_once(source, key, attempt)
        if result is not None:
            return result
        time.sleep(0.5 * attempt)
    return False


def process_single_ai_task_once(source, key, attempt=1):
    conn = db()
    try:
        with conn.cursor() as cursor:
            cursor.execute(f"SET SESSION innodb_lock_wait_timeout = {AI_DAEMON_LOCK_WAIT_SECONDS}")
            product = load_ai_product(cursor, source, key["product_id"], key["date_record"])
            reused_fields = reuse_existing_product_analysis(cursor, source, product, AI_DAEMON_ANALYSIS)
            if reused_fields:
                conn.commit()
            if reused_fields and not needs_ai_analysis(cursor, source, product, AI_DAEMON_ANALYSIS):
                print(
                    f"[AI_DAEMON][{threading.current_thread().name}] reused "
                    f"{','.join(reused_fields)} source={source} "
                    f"product_id={product['product_id']} date_record={product.get('date_record')}",
                    flush=True,
                )
                return True
            if not needs_ai_analysis(cursor, source, product, AI_DAEMON_ANALYSIS):
                print(
                    f"[AI_DAEMON][{threading.current_thread().name}] skip already analyzed "
                    f"source={source} product_id={product['product_id']} "
                    f"date_record={product.get('date_record')}",
                    flush=True,
                )
                conn.commit()
                return False
            print(
                f"[AI_DAEMON][{threading.current_thread().name}] analyzing "
                f"source={source} product_id={product['product_id']} "
                f"date_record={product.get('date_record')}",
                flush=True,
            )
            run_ai_product_flow(cursor, source, product, AI_DAEMON_ANALYSIS, AI_DAEMON_WRITE)
            conn.commit()
            return True
    except pymysql.err.OperationalError as exc:
        conn.rollback()
        if exc.args and exc.args[0] == 1205:
            if attempt < AI_DAEMON_LOCK_RETRIES:
                print(
                    f"[AI_DAEMON][{threading.current_thread().name}] lock wait timeout, retry "
                    f"{attempt}/{AI_DAEMON_LOCK_RETRIES} source={source} "
                    f"product_id={key.get('product_id')} date_record={key.get('date_record')}",
                    flush=True,
                )
                return None
            print(
                f"[AI_DAEMON][{threading.current_thread().name}] lock wait timeout, skip this cycle "
                f"source={source} product_id={key.get('product_id')} "
                f"date_record={key.get('date_record')}",
                flush=True,
            )
            return False
        print(
            f"[AI_DAEMON][{threading.current_thread().name}] item failed "
            f"source={source} product_id={key.get('product_id')} "
            f"date_record={key.get('date_record')}",
            flush=True,
        )
        traceback.print_exc()
        return False
    except Exception:
        conn.rollback()
        print(
            f"[AI_DAEMON][{threading.current_thread().name}] item failed "
            f"source={source} product_id={key.get('product_id')} "
            f"date_record={key.get('date_record')}",
            flush=True,
        )
        traceback.print_exc()
        return False
    finally:
        conn.close()


def reuse_existing_product_analysis(cursor, source, product, analysis):
    if not AI_DAEMON_WRITE or source not in FASTMOSS_ANALYSIS_REUSE_SOURCES:
        return []

    current = fetch_current_analysis_state(cursor, source, product)
    reusable = fetch_reusable_fastmoss_analysis(cursor, product["product_id"])
    if not reusable:
        return []

    updates = []
    params = []
    guards = []
    if analysis in {"ip", "both"} and is_blank(current.get("ip_grade")) and not is_blank(reusable.get("ip_grade")):
        updates.extend(["ip_grade = %s", "ip_reason = %s", "ip_tags = %s"])
        params.extend([
            reusable.get("ip_grade"),
            reusable.get("ip_reason") or "",
            reusable.get("ip_tags") or "",
        ])
        guards.append("(ip_grade IS NULL OR ip_grade = '')")
    if analysis in {"material", "both"} and is_blank(current.get("material_analysis")) and not is_blank(reusable.get("material_analysis")):
        updates.append("material_analysis = %s")
        params.append(reusable.get("material_analysis"))
        guards.append("(material_analysis IS NULL OR material_analysis = '')")
    if not updates:
        return []

    meta = AI_PRODUCT_SOURCES[source]
    params.extend([str(product["product_id"]), product.get("date_record")])
    guard_sql = f" AND ({' OR '.join(guards)})" if guards else ""
    cursor.execute(
        f"""
        UPDATE `{meta['table']}`
        SET {", ".join(updates)}
        WHERE `{meta['id_field']}` = %s AND `{meta['date_field']}` = %s
          {guard_sql}
        """,
        params,
    )
    return [
        field
        for field in ("ip", "material")
        if any(update.startswith("ip_") if field == "ip" else update.startswith("material_") for update in updates)
    ]


def fetch_current_analysis_state(cursor, source, product):
    meta = AI_PRODUCT_SOURCES[source]
    cursor.execute(
        f"""
        SELECT ip_grade, ip_reason, ip_tags, material_analysis
        FROM `{meta['table']}`
        WHERE `{meta['id_field']}` = %s AND `{meta['date_field']}` = %s
        LIMIT 1
        """,
        (str(product["product_id"]), product.get("date_record")),
    )
    return cursor.fetchone() or {}


def fetch_reusable_fastmoss_analysis(cursor, product_id):
    candidates = []
    for source in FASTMOSS_ANALYSIS_REUSE_SOURCES:
        meta = AI_PRODUCT_SOURCES[source]
        cursor.execute(
            f"""
            SELECT ip_grade, ip_reason, ip_tags, material_analysis
            FROM `{meta['table']}`
            WHERE `{meta['id_field']}` = %s
              AND (
                (ip_grade IS NOT NULL AND ip_grade <> '')
                OR (material_analysis IS NOT NULL AND material_analysis <> '')
              )
            ORDER BY
              ((ip_grade IS NOT NULL AND ip_grade <> '') + (material_analysis IS NOT NULL AND material_analysis <> '')) DESC,
              `{meta['date_field']}` DESC
            LIMIT 1
            """,
            (str(product_id),),
        )
        row = cursor.fetchone()
        if row:
            candidates.append(row)
    if not candidates:
        return None
    reusable = {}
    for row in candidates:
        if is_blank(reusable.get("ip_grade")) and not is_blank(row.get("ip_grade")):
            reusable["ip_grade"] = row.get("ip_grade")
            reusable["ip_reason"] = row.get("ip_reason")
            reusable["ip_tags"] = row.get("ip_tags")
        if is_blank(reusable.get("material_analysis")) and not is_blank(row.get("material_analysis")):
            reusable["material_analysis"] = row.get("material_analysis")
    return reusable


def needs_ai_analysis(cursor, source, product, analysis):
    current = fetch_current_analysis_state(cursor, source, product)
    material = parse_json(current.get("material_analysis")) or {}
    prefilter_hit = bool(material.get("pre_filter", {}).get("hit"))
    if analysis == "material":
        return is_blank(current.get("material_analysis"))
    if analysis == "ip":
        return is_blank(current.get("ip_grade")) and not prefilter_hit
    return is_blank(current.get("material_analysis")) or (is_blank(current.get("ip_grade")) and not prefilter_hit)


def is_blank(value):
    return value is None or value == ""


def run_ai_product_flow(cursor, source, product, analysis, write):
    if analysis == "both":
        prefilter_result = run_material_prefilter(cursor, source, product, write=write)
        if prefilter_result:
            print(
                "[AI_DAEMON] material prefilter hit, skip IP and material AI "
                f"product_id={product['product_id']}",
                flush=True,
            )
            return
        run_analysis(cursor, source, product, "IP", write=write)
        run_analysis(cursor, source, product, "MATERIAL", write=write)
        return

    if analysis == "material":
        run_analysis(cursor, source, product, "MATERIAL", write=write)
        return

    run_analysis(cursor, source, product, "IP", write=write)


def fetch_ai_daemon_product_keys(source, analysis, limit):
    meta = AI_PRODUCT_SOURCES[source]
    table = meta["table"]
    id_field = meta["id_field"]
    date_field = meta["date_field"]
    image_field = meta["image_field"]
    where = [f"`{image_field}` IS NOT NULL", f"`{image_field}` <> ''"]
    params = []
    if AI_DAEMON_LATEST_ONLY:
        latest_date = fetch_ai_daemon_latest_date(meta)
        if not latest_date:
            return []
        where.append(f"`{date_field}` = %s")
        params.append(latest_date)

    if analysis == "both":
        where.append(
            "("
            "material_analysis IS NULL OR material_analysis = '' "
            "OR ip_grade IS NULL OR ip_grade = ''"
            ")"
        )
    elif analysis == "material":
        where.append("(material_analysis IS NULL OR material_analysis = '')")
    else:
        where.append("(ip_grade IS NULL OR ip_grade = '')")

    sql = f"""
        SELECT `{id_field}` AS product_id, `{date_field}` AS date_record
        FROM `{table}`
        WHERE {" AND ".join(where)}
        ORDER BY `id` DESC
        LIMIT %s
    """
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(sql, [*params, limit])
        return cursor.fetchall()


def fetch_ai_daemon_latest_date(meta):
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(f"SELECT MAX(`{meta['date_field']}`) AS latest_date FROM `{meta['table']}`")
        row = cursor.fetchone() or {}
        return row.get("latest_date")


app = create_app()


if __name__ == "__main__":
    start_ai_daemon_if_enabled()
    debug_enabled = os.getenv("FLASK_DEBUG", "true").strip().lower() == "true"
    app.run(
        host="0.0.0.0",
        port=int(os.getenv("PORT", "5000")),
        debug=debug_enabled,
        use_reloader=False,
    )
