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
EXPERT_TEAM_ALLOWED_ROLES = {"admin", "manager"}
PERMISSION_EXPERT_TEAM = "expert_team"
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
        return api_ok({"source": text, "translation": translation, "provider": "minimax"})

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

    @app.post("/api/expert-team/chat")
    def expert_team_chat():
        user = require_expert_team_permission()
        payload = request.get_json(silent=True) or {}
        message = stringify(payload.get("message")).strip()
        images = payload.get("images") if isinstance(payload.get("images"), list) else []
        image_count = len(images)
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

        readonly_context = collect_expert_readonly_context(message)
        if image_count:
            readonly_context = (readonly_context + "\n\n" if readonly_context else "") + (
                f"用户本轮随消息发送了{image_count}张图片。当前后端已接收图片上下文，"
                "但专家团队视觉识别能力需要接入视觉模型后才能直接读取图片内容。"
            )

        try:
            answer = sanitize_expert_team_answer(call_expert_team_ai(user, message, history, project_code, project_context, readonly_context))
            status = "SUCCESS"
        except Exception as exc:
            answer = sanitize_expert_team_answer(fallback_expert_team_answer(message, str(exc)))
            status = "FALLBACK"

        with db() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"""
                INSERT INTO {EXPERT_TEAM_MESSAGE_TABLE}
                  (session_id, user_id, role, team_role, content, meta_json)
                VALUES (%s, %s, 'assistant', 'chief_planner', %s, %s)
                """,
                (session_id, user["id"], answer, json.dumps({"status": status, "readonly_context": readonly_context, "image_count": image_count}, ensure_ascii=False)),
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
        translation = data["choices"][0]["message"]["content"].strip()
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("翻译服务返回格式异常") from exc
    if not translation:
        raise RuntimeError("翻译服务返回为空")
    return translation


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
]


EXPERT_EXECUTION_HANDOFF_RULES = """
专家团队执行交接规则：
1. 专家团队本身不直接写代码、不写库、不改线上数据，但必须判断“谁具备执行能力”。
2. 当用户表达“可以执行、开始执行、落地、修改、让A做、交给技术人员、你手下人员去做”等意图时，必须输出《执行交接单》。
3. 《执行交接单》必须让执行者A可以直接听懂，不能只说方向，必须包含：
   - 执行者A是谁：技术执行人员A/Codex/业务技术团队/业务运营团队/数据分析执行者等。
   - 为什么A有能力：A需要具备哪些技能、能访问哪些资源、能执行哪些动作。
   - 不应该交给谁：哪些专家只负责判断，不负责执行。
   - 执行目标：这次要完成什么，完成后用户能看到什么。
   - 执行范围：涉及页面、接口、数据库表、字段、权限、只读工具或业务流程。
   - 操作步骤：按 1、2、3 写清楚，尽量具体到文件、接口、字段、按钮、校验点。
   - 输入资料：A需要从用户、数据库、接口或截图拿到什么。
   - 验收标准：用户如何判断做完了，至少列出可测试的结果。
   - 风险边界：哪些不能做，哪些需要用户确认后再做。
4. 如果当前信息不足，仍然要先给出“可执行的第一步交接单”，并说明A需要补读哪些数据。
5. 如果任务是商品分析，执行者通常是业务专家；如果任务是系统改造，执行者通常是技术执行人员A/Codex/业务技术团队。
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
    return {
        "id": row.get("id"),
        "role": row.get("role"),
        "team_role": row.get("team_role") or "",
        "content": row.get("content") or "",
        "created_at": stringify(row.get("created_at")),
    }


def make_expert_session_title(message):
    text = re.sub(r"\s+", " ", stringify(message)).strip()
    return text[:40] or "专家团队会话"


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

当前用户：
- user_id: {user.get('id')}
- username: {user.get('username')}

当前项目上下文：
- project_code: {project_code}
- context: {context}

专家能力档案：
{chr(10).join(role_lines)}

回复格式：
- 先给“团队判断”
- 再给“应该调用的专家”
- 再给“执行顺序”
- 最后给“你现在可以怎么和团队继续对话”

不要假装已经调用外部工具或数据库；如果需要具体商品数据、代码或截图，要明确说明需要用户提供或让系统接入。
""".strip()


def call_expert_team_ai(user, message, history, project_code, project_context, readonly_context=""):
    api_key = resolve_minimax_api_key()
    if not api_key:
        raise RuntimeError("缺少 MINIMAX_API_KEY")
    messages = [{"role": "system", "content": build_expert_team_system_prompt(user, project_code, project_context)}]
    messages.append({"role": "system", "content": EXPERT_EXECUTION_HANDOFF_RULES})
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
    cleaned = re.sub(r"^\s*<think>[\s\S]*?(?=(\*\*|团队判断|执行交接单|《执行交接单》|$))", "", cleaned, flags=re.IGNORECASE).strip()
    return cleaned or stringify(text).strip()


def fallback_expert_team_answer(message, error):
    return (
        "团队判断：专家团队接口已收到你的问题，但当前 AI 服务暂时不可用，先给你一个本地兜底建议。\n\n"
        "应该调用的专家：首席项目规划专家、专家团队管理者、数据方法论负责人、技术架构专家。\n\n"
        "执行顺序：先明确你的目标和使用场景，再拆分为业务专家任务，最后由技术架构专家判断如何落到系统模块和数据库。\n\n"
        f"当前问题：{message}\n\n"
        f"服务状态：{error}"
    )


def collect_expert_readonly_context(message):
    text = stringify(message)
    lower = text.lower()
    trigger_words = [
        "查", "查看", "数据", "字段", "接口", "回执", "商品", "属性", "卖点",
        "attributes", "selling_points", "product_id", "fastmoss", "kalodata", "统一表",
        "fastmoss_product_aggregate", "fastmoss_product_rank_aggregate", "kalodata_youwei_product",
    ]
    if not any(word.lower() in lower for word in trigger_words):
        return ""

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
    return "\n".join(context_lines)


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
