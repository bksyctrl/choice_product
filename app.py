import base64
import io
import json
import os
import sys
import threading
import time
import traceback
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

import pymysql
from dotenv import load_dotenv
from flask import Flask, jsonify, redirect, render_template, request, send_file
from flask_cors import CORS
from openpyxl import Workbook


load_dotenv()

TOOLS_DIR = Path(__file__).resolve().parent / "tools"
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from analyze_single_product import (  # noqa: E402
    PRODUCT_SOURCES as AI_PRODUCT_SOURCES,
    build_input_snapshot,
    load_product as load_ai_product,
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
AI_DAEMON_BATCH_SIZE = int(os.getenv("AI_DAEMON_BATCH_SIZE", "5"))
AI_DAEMON_CONCURRENCY = max(1, min(5, int(os.getenv("AI_DAEMON_CONCURRENCY", "5"))))
AI_DAEMON_WRITE = os.getenv("AI_DAEMON_WRITE", "true").strip().lower() != "false"
_ai_daemon_started = False
_ai_daemon_current_concurrency = AI_DAEMON_CONCURRENCY
FASTMOSS_ANALYSIS_REUSE_SOURCES = ("fastmoss", "fastmoss_rank")

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
        "distribution_30d": "distribution_7d",
        "distribution_7d": "distribution_7d",
        "distribution_90d": "distribution_90d",
        "distribution_180d": "distribution_180d",
        "overview_30d": None,
        "overview_7d": "overview_7d",
        "overview_90d": "overview_90d",
        "overview_180d": "overview_180d",
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
}


def create_app():
    app = Flask(__name__, static_folder="static", template_folder="static")
    CORS(app)

    @app.get("/")
    def index():
        return render_template("index.html")

    @app.get("/api/products")
    def products():
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
        where, params = build_filters(meta, request.args, latest_by_default=True)
        order_sql = build_order(request.args.get("sort_by"), request.args.get("sort_order"))
        offset = (page - 1) * page_size

        select_sql = build_product_select(source, meta)
        sql = f"""
            SELECT {select_sql}
            FROM `{meta["table"]}`
            WHERE {" AND ".join(where)}
            {order_sql}
            LIMIT %s OFFSET %s
        """
        count_sql = f"""
            SELECT COUNT(*) AS total
            FROM `{meta["table"]}`
            WHERE {" AND ".join(where)}
        """

        with db() as conn, conn.cursor() as cursor:
            cursor.execute(count_sql, params)
            total = int(cursor.fetchone()["total"])
            cursor.execute(sql, [*params, page_size, offset])
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
        source = normalize_source(request.args.get("source"))
        meta = SOURCES[source]
        where, params = build_filters(meta, request.args, latest_by_default=True)
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
        source = normalize_source(request.args.get("source"))
        meta = SOURCES[source]
        with db() as conn, conn.cursor() as cursor:
            cursor.execute(f"SELECT MAX(`{meta['date']}`) AS latest_date FROM `{meta['table']}`")
            row = cursor.fetchone()
        return api_ok({
            "source": source,
            "latest_date": stringify(row.get("latest_date") if row else None),
        })

    @app.get("/api/products/<source>/<product_id>")
    def product_detail(source, product_id):
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
        return api_ok(normalize_detail(source, row))

    @app.patch("/api/products/<source>/<product_id>/status")
    def update_status(source, product_id):
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
        source = normalize_source(source)
        links = fetch_ready_links(source)
        return api_ok({"source": source, "count": len(links), "links": links})

    @app.get("/api/products/<source>/export-ready-links")
    def export_ready_links(source):
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

    return app


def db():
    return pymysql.connect(**DB_CONFIG)


def api_ok(data):
    return jsonify({"ok": True, "data": data})


def api_error(message, status):
    return jsonify({"ok": False, "error": message}), status


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


def build_order(sort_by, sort_order):
    raw_field = SORT_FIELDS.get(sort_by or "date_record", "date_record")
    direction = "ASC" if str(sort_order).lower() == "asc" else "DESC"

    # 针对数值字段，强制进行数值转换排序，处理可能存在的 $ % , 等符号
    # 防止出现字符串排序导致的 "84 > 658" 错误
    numeric_keys = {"sold", "sale_amount", "rating", "price", "author_count"}
    if sort_by in numeric_keys:
        # 移除常见非数字符号并转换为 DECIMAL 排序
        clean_expr = f"REPLACE(REPLACE(REPLACE({raw_field}, '$', ''), '%', ''), ',', '')"
        return f"ORDER BY CAST(NULLIF({clean_expr}, '') AS DECIMAL(20,4)) {direction}"

    return f"ORDER BY {raw_field} {direction}"


def normalize_product_row(source, row, sales_period):
    material = parse_json(row.get("material_analysis")) or {}
    tags = parse_json(row.get("ip_tags")) or {}
    field_period = sales_period["period"] if sales_period["period"] != "custom" else "7d"
    overview = parse_json(row.get(f"overview_{field_period}")) or {}
    distribution = parse_json(row.get(f"distribution_{field_period}")) or []
    sold_count = row.get("runtime_sold_count") if row.get("runtime_sold_count") is not None else overview.get("销量") or row.get("sold_count_view")
    sale_amount = overview.get("销售额") or row.get("sale_amount_view")
    author_count = overview.get("带货达人数") or row.get("author_count_view")
    video_ratio = row.get("video_ratio_view") or ratio_from_distribution(distribution, "视频")
    product_card_ratio = row.get("product_card_ratio_view") or ratio_from_distribution(distribution, "商品卡")
    return {
        "source": source,
        "product_id": stringify(row.get("product_id")),
        "date_record": row.get("date_record"),
        "title": row.get("title") or "",
        "image_url": row.get("image_url"),
        "price": stringify(row.get("price_view")),
        "base_price": stringify(row.get("base_price_view")),
        "rating": stringify(row.get("rating_view")),
        "commission_rate": stringify(row.get("commission_rate_view")),
        "sold_count": stringify(sold_count),
        "total_sold_count": stringify(row.get("total_sold_count_view") or row.get("sold_count_view")),
        "sale_amount": stringify(sale_amount),
        "sales_growth": row.get("sales_growth_view") or "N/A",
        "author_count": stringify(author_count),
        "video_ratio": stringify(video_ratio),
        "product_card_ratio": stringify(product_card_ratio),
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
    if meta.get("sold") != "rank_sold_count":
        for row in rows:
            row["sales_growth_view"] = "N/A"
            row["runtime_sold_count"] = None
        return
    for row in rows:
        metrics = calculate_runtime_sales_metrics(
            cursor,
            meta,
            row,
            sales_period,
            collection_start,
            collection_end,
        )
        row["runtime_sold_count"] = metrics["current_sum"]
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

    if collection_start and collection_end:
        current_start = collection_start
        current_end = collection_end
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
                THEN COALESCE(`{sold_field}`, 0) ELSE 0
              END) AS current_sum,
              SUM(CASE
                WHEN `{date_field}` BETWEEN DATE_SUB(CAST(%s AS DATE), INTERVAL %s DAY)
                                     AND DATE_SUB(CAST(%s AS DATE), INTERVAL %s DAY)
                THEN COALESCE(`{sold_field}`, 0) ELSE 0
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
            THEN COALESCE(`{sold_field}`, 0) ELSE 0
          END) AS current_sum,
          SUM(CASE
            WHEN `{date_field}` BETWEEN DATE_SUB(CAST(%s AS DATE), INTERVAL DATEDIFF(CAST(%s AS DATE), CAST(%s AS DATE)) + 1 DAY)
                                 AND DATE_SUB(CAST(%s AS DATE), INTERVAL 1 DAY)
            THEN COALESCE(`{sold_field}`, 0) ELSE 0
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


def format_runtime_sales_result(result):
    current_sum = float(result.get("current_sum") or 0)
    previous_sum = float(result.get("previous_sum") or 0)
    if previous_sum <= 0:
        return {"current_sum": current_sum, "growth": "N/A"}
    return {"current_sum": current_sum, "growth": f"{((current_sum / previous_sum) - 1) * 100:.1f}%"}


def ratio_from_distribution(distribution, name):
    if not isinstance(distribution, list):
        return ""
    for item in distribution:
        if str(item.get("name")) == name:
            return item.get("percentage") or item.get("ratio") or ""
    return ""


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
        "raw_fields": raw,
    }


def build_platform_url(source, product_id):
    if source in {"unified", "fastmoss"}:
        return f"https://www.fastmoss.com/zh/e-commerce/detail/{product_id}"
    return build_tiktok_shop_url(product_id)


def resolve_platform_url(source, product_id, detail_url=None):
    if source == "kalodata":
        return build_tiktok_shop_url(product_id)
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


def stringify(value):
    if value is None:
        return ""
    if isinstance(value, Decimal):
        return str(value.normalize())
    if isinstance(value, (datetime, date)):
        return value.strftime("%Y-%m-%d %H:%M:%S") if isinstance(value, datetime) else value.isoformat()
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
        keys = fetch_ai_daemon_product_keys(source, AI_DAEMON_ANALYSIS, AI_DAEMON_BATCH_SIZE)
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
    conn = db()
    try:
        with conn.cursor() as cursor:
            product = load_ai_product(cursor, source, key["product_id"], key["date_record"])
            reused_fields = reuse_existing_product_analysis(cursor, source, product, AI_DAEMON_ANALYSIS)
            if reused_fields and not needs_ai_analysis(cursor, source, product, AI_DAEMON_ANALYSIS):
                print(
                    f"[AI_DAEMON][{threading.current_thread().name}] reused "
                    f"{','.join(reused_fields)} source={source} "
                    f"product_id={product['product_id']} date_record={product.get('date_record')}",
                    flush=True,
                )
                conn.commit()
                return True
            print(
                f"[AI_DAEMON][{threading.current_thread().name}] analyzing "
                f"source={source} product_id={product['product_id']} "
                f"date_record={product.get('date_record')}",
                flush=True,
            )
            run_ai_product_flow(cursor, source, product, AI_DAEMON_ANALYSIS, AI_DAEMON_WRITE)
            conn.commit()
            return True
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
    if analysis in {"ip", "both"} and is_blank(current.get("ip_grade")) and not is_blank(reusable.get("ip_grade")):
        updates.extend(["ip_grade = %s", "ip_reason = %s", "ip_tags = %s"])
        params.extend([
            reusable.get("ip_grade"),
            reusable.get("ip_reason") or "",
            reusable.get("ip_tags") or "",
        ])
    if analysis in {"material", "both"} and is_blank(current.get("material_analysis")) and not is_blank(reusable.get("material_analysis")):
        updates.append("material_analysis = %s")
        params.append(reusable.get("material_analysis"))
    if not updates:
        return []

    meta = AI_PRODUCT_SOURCES[source]
    params.extend([str(product["product_id"]), product.get("date_record")])
    cursor.execute(
        f"""
        UPDATE `{meta['table']}`
        SET {", ".join(updates)}
        WHERE `{meta['id_field']}` = %s AND `{meta['date_field']}` = %s
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

    prefilter_hit_sql = (
        "JSON_VALID(material_analysis) "
        "AND JSON_UNQUOTE(JSON_EXTRACT(material_analysis, '$.pre_filter.hit')) = 'true'"
    )
    if analysis == "both":
        where.append(
            "("
            "material_analysis IS NULL OR material_analysis = '' "
            "OR ((ip_grade IS NULL OR ip_grade = '') AND NOT (" + prefilter_hit_sql + "))"
            ")"
        )
    elif analysis == "material":
        where.append("(material_analysis IS NULL OR material_analysis = '')")
    else:
        where.append("(ip_grade IS NULL OR ip_grade = '')")
        where.append(f"NOT ({prefilter_hit_sql})")

    sql = f"""
        SELECT `{id_field}` AS product_id, `{date_field}` AS date_record
        FROM `{table}`
        WHERE {" AND ".join(where)}
        ORDER BY `{date_field}` DESC, `id`
        LIMIT %s
    """
    with db() as conn, conn.cursor() as cursor:
        cursor.execute(sql, (limit,))
        return cursor.fetchall()


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
