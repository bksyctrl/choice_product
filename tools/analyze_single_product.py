import argparse
import base64
import hashlib
import json
import os
from decimal import Decimal
from datetime import datetime

import pymysql
from openai import OpenAI
from dotenv import load_dotenv


load_dotenv()


DB_CONFIG = {
    "host": "192.168.0.168",
    "port": 3306,
    "user": "ITaimysql",
    "password": "Ai12345678@",
    "database": "ecommerce_workflow",
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}

MINIMAX_BASE_URL = os.getenv("MINIMAX_BASE_URL", "https://api.minimax.io/v1")
MINIMAX_API_KEY = os.getenv("MINIMAX_API_KEY") or os.getenv("minimax_api_key") or ""
MINIMAX_MODEL = os.getenv("MINIMAX_MODEL", "MiniMax-M2.7")
PROMPT_VERSION = "single_product_v1"
MAX_LOG_TEXT_LENGTH = 120000

PRODUCT_SOURCES = {
    "fastmoss": {
        "table": "fastmoss_product_aggregate",
        "id_field": "product_id",
        "title_field": "title",
        "selling_points_field": "selling_points",
        "image_field": "image_base64",
        "date_field": "date_record",
        "attributes_field": "attributes",
    },
    "fastmoss_rank": {
        "table": "fastmoss_product_rank_aggregate",
        "id_field": "product_id",
        "title_field": "title",
        "selling_points_field": None,
        "image_field": "image_base64",
        "date_field": "date_record",
        "attributes_field": None,
    },
    "kalodata": {
        "table": "kalodata_youwei_product",
        "id_field": "商品ID",
        "title_field": "商品标题",
        "selling_points_field": "卖点",
        "image_field": "商品主图",
        "date_field": "date_record",
        "attributes_field": "属性信息",
    },
}

MATERIAL_EXCLUDE_RULES = [
    ("屏幕保护类", ["钢化膜", "手机屏幕膜", "屏幕膜", "保护膜", "tempered glass", "screen protector", "screen film"]),
    ("非手机壳保护对象", ["耳机壳", "airpods case", "earbuds case", "平板膜", "手表膜"]),
    ("贴纸贴膜类", ["贴纸", "手机贴纸", "镜头膜", "背膜", "skin sticker", "phone sticker"]),
    ("支架配件类", ["手机支架", "支架", "stand", "holder", "grip", "pop socket"]),
    ("其他配件类", ["挂绳", "挂扣", "镜头盖", "镜头保护圈", "数据线", "充电器"]),
]


def connect_db():
    return pymysql.connect(**DB_CONFIG)


def json_dumps(data):
    def default(obj):
        if isinstance(obj, Decimal):
            return float(obj)
        if isinstance(obj, datetime):
            return obj.strftime("%Y-%m-%d %H:%M:%S")
        return str(obj)

    return json.dumps(data, ensure_ascii=False, separators=(",", ":"), default=default)


def truncate_text(value, max_length=MAX_LOG_TEXT_LENGTH):
    if value is None:
        return None
    text = value if isinstance(value, str) else json_dumps(value)
    if len(text) <= max_length:
        return text
    digest = hashlib.sha256(text.encode("utf-8", errors="ignore")).hexdigest()
    return text[:max_length] + f"\n...[TRUNCATED length={len(text)} sha256={digest}]"


def compact_rules_for_log(rules):
    compact = []
    for rule in rules:
        item = {
            "rule_code": rule.get("rule_code"),
            "rule_name": rule.get("rule_name"),
            "ip_grade": rule.get("ip_grade"),
            "ip_type": rule.get("ip_type"),
            "material_type": rule.get("material_type"),
            "material_category": rule.get("material_category"),
            "keywords": rule.get("keywords"),
        }
        reason = rule.get("rule_reason") or rule.get("image_description") or ""
        item["summary"] = reason[:500]
        compact.append(item)
    return compact


def compact_request_for_log(request_payload):
    if not request_payload:
        return None
    user_text = request_payload.get("user_text") or ""
    compact = dict(request_payload)
    compact["user_text_length"] = len(user_text)
    compact["user_text_sha256"] = hashlib.sha256(user_text.encode("utf-8", errors="ignore")).hexdigest()
    compact["user_text_preview"] = user_text[:5000]
    compact.pop("user_text", None)
    return compact


def resolve_minimax_api_key():
    if MINIMAX_API_KEY:
        return MINIMAX_API_KEY
    test_file = os.path.join(os.getcwd(), "test.py")
    if not os.path.exists(test_file):
        return ""
    try:
        import re

        with open(test_file, "r", encoding="utf-8") as file:
            text = file.read()
        match = re.search(r"api_key\s*=\s*['\"]([^'\"]+)['\"]", text)
        return match.group(1).strip() if match else ""
    except Exception:
        return ""


def normalize_image_data(image_value):
    if not image_value:
        return ""
    image_value = str(image_value).strip()
    if image_value.startswith("data:image/"):
        return image_value
    if image_value.startswith("http://") or image_value.startswith("https://"):
        return image_value
    return "data:image/jpeg;base64," + image_value


def image_digest(image_value):
    if not image_value:
        return None
    value = str(image_value)
    if "," in value and value.startswith("data:image/"):
        value = value.split(",", 1)[1]
    try:
        raw = base64.b64decode(value, validate=False)
        return hashlib.sha256(raw).hexdigest()
    except Exception:
        return hashlib.sha256(value.encode("utf-8", errors="ignore")).hexdigest()


def load_product(cursor, source, product_id=None, date_record=None):
    meta = PRODUCT_SOURCES[source]
    table = meta["table"]
    id_field = meta["id_field"]
    date_field = meta["date_field"]
    title_field = meta["title_field"]
    selling_field = meta["selling_points_field"]
    image_field = meta["image_field"]
    attributes_field = meta["attributes_field"]

    select_fields = [
        f"`{id_field}` AS product_id",
        f"`{date_field}` AS date_record",
        f"`{title_field}` AS title",
        f"`{image_field}` AS image_base64",
        "`ai_analysis_error`",
    ]
    if selling_field:
        select_fields.append(f"`{selling_field}` AS selling_points")
    else:
        select_fields.append("'' AS selling_points")
    if attributes_field:
        select_fields.append(f"`{attributes_field}` AS attributes")
    else:
        select_fields.append("'' AS attributes")

    where = [f"`{image_field}` IS NOT NULL", f"`{image_field}` <> ''"]
    params = []
    if product_id:
        where.append(f"`{id_field}` = %s")
        params.append(str(product_id))
    if date_record:
        where.append(f"`{date_field}` = %s")
        params.append(date_record)

    sql = f"""
        SELECT {", ".join(select_fields)}
        FROM `{table}`
        WHERE {" AND ".join(where)}
        ORDER BY `{date_field}` DESC
        LIMIT 1
    """
    cursor.execute(sql, params)
    row = cursor.fetchone()
    if not row:
        raise RuntimeError(f"没有找到可分析商品：source={source}, product_id={product_id}, date_record={date_record}")
    row["source_table"] = table
    return row


def load_rules(cursor, analysis_type):
    if analysis_type == "IP":
        cursor.execute(
            """
            SELECT rule_code, rule_name, ip_grade, ip_type, keywords, image_description, rule_reason
            FROM cp_ai_ip_rule
            WHERE enabled = 1
            ORDER BY id
            """
        )
    else:
        cursor.execute(
            """
            SELECT rule_code, rule_name, material_type, material_category, keywords, image_description, rule_reason
            FROM cp_ai_material_rule
            WHERE enabled = 1
            ORDER BY id
            """
        )
    return cursor.fetchall()


def load_experiences(cursor, analysis_type, limit=20):
    cursor.execute(
        """
        SELECT experience_type, result_summary, matched_rules, reason, match_score
        FROM cp_ai_analysis_experience
        WHERE enabled = 1 AND analysis_type = %s
        ORDER BY updated_at DESC, id DESC
        LIMIT %s
        """,
        (analysis_type, limit),
    )
    return cursor.fetchall()


def prefilter_material(product):
    text_parts = {
        "title": product.get("title") or "",
        "selling_points": product.get("selling_points") or "",
        "attributes": product.get("attributes") or "",
    }
    haystack = "\n".join(text_parts.values()).lower()
    for category, keywords in MATERIAL_EXCLUDE_RULES:
        hits = [kw for kw in keywords if kw.lower() in haystack]
        if hits:
            source_fields = [
                field for field, value in text_parts.items()
                if any(kw.lower() in str(value).lower() for kw in hits)
            ]
            return {
                "material_type": "非工厂材质",
                "material_category": category,
                "material_reason": f"商品文本命中明确非手机壳工厂材质关键词：{', '.join(hits)}，因此跳过材质AI判断。",
                "confidence": 100,
                "matched_tags": hits,
                "matched_rules": [],
                "pre_filter": {
                    "hit": True,
                    "matched_keywords": hits,
                    "source_fields": source_fields,
                },
            }
    return None


def build_input_snapshot(product):
    image_value = product.get("image_base64") or ""
    return {
        "source_table": product["source_table"],
        "product_id": str(product["product_id"]),
        "date_record": product.get("date_record"),
        "title": product.get("title") or "",
        "selling_points": product.get("selling_points") or "",
        "attributes": product.get("attributes") or "",
        "image_sha256": image_digest(image_value),
        "image_length": len(str(image_value)),
    }


def build_ip_prompt(product, rules, experiences):
    return {
        "system": (
            "你是选品系统的IP风险分析器。你必须严格根据商品主图、标题、卖点、IP规则库和经验库判断。"
            "只输出JSON，不要输出Markdown。reason必须详细，原因和证据合并写在reason里。"
            "如果商品命中等级规则但没有命中规则库中的具体rule_code，允许matched_rules为空数组，禁止编造不存在的rule_code。"
        ),
        "user_text": json_dumps({
            "task": "判断该商品是否命中IP/品牌/角色/潮牌/擦边插画风险",
            "grade_rules": {
                "S": "奢侈品大牌，例如古驰、LV、克罗心、爱马仕",
                "A": "美国本地IP品牌，例如米老鼠、贝兹娃娃、飞天小女警",
                "B": "非美国本地动漫IP，例如火影忍者、咒术回战、JOJO的奇妙冒险",
                "C": "非美国本地潮牌品牌，例如BAPE猿人头、红牛",
                "D": "擦边插画/不知名IP延伸扭曲设计",
                "E": "完全无风险",
            },
            "product": {
                "product_id": str(product["product_id"]),
                "date_record": product.get("date_record"),
                "title": product.get("title") or "",
                "selling_points": product.get("selling_points") or "",
            },
            "rules": rules,
            "experiences": experiences,
            "required_json": {
                "ip_grade": "S/A/B/C/D/E",
                "matched_grade_rule": "S/A/B/C/D/E，对应命中的等级规则",
                "reason": "判断原因与证据合并写在这里",
                "match_score": "这个是置信度，判断的把握有多少分 满分100分 把握度越高分数越高，0-100数字",
                "matched_rules": "数组；只有确实命中rules里存在的rule_code时才填写，否则返回空数组；禁止编造rule_code",
                "tags": ["品牌名/角色名/IP类型/关键词"],
            },
        }),
    }


def build_material_prompt(product, rules, experiences):
    return {
        "system": (
            "你是选品系统的手机壳工厂材质分析器。你必须严格根据商品主图、标题、卖点、材质规则库和经验库判断。"
            "只输出JSON，不要输出Markdown。原因和证据合并写在material_reason里。"
        ),
        "user_text": json_dumps({
            "task": "判断该商品是否属于工厂可生产手机壳材质，并输出材质大类和细分类",
            "product": {
                "product_id": str(product["product_id"]),
                "date_record": product.get("date_record"),
                "title": product.get("title") or "",
                "selling_points": product.get("selling_points") or "",
            },
            "rules": rules,
            "experiences": experiences,
            "required_json": {
                "material_type": "工厂材质/非工厂材质/疑似材质/其他",
                "material_category": "TPU/硅胶/亚克力/金属/皮革/布料/磁吸类/其他",
                "material_reason": "判断原因与证据合并写在这里",
                "confidence": "这个是置信度评分，判断的把握有多少分 满分100分 把握度越高分数越高0-100数字",
                "matched_tags": ["材质关键词"],
                "matched_rules": [{"rule_code": "MAT_RULE_xxxx", "rule_name": "规则名称", "why": "命中原因"}],
                "pre_filter": {"hit": False, "matched_keywords": [], "source_fields": []},
            },
        }),
    }


def call_minimax(prompt, image_value):
    api_key = resolve_minimax_api_key()
    if not api_key:
        raise RuntimeError("缺少 MINIMAX_API_KEY 环境变量，无法调用 MiniMax。")
    client = OpenAI(base_url=MINIMAX_BASE_URL, api_key=api_key)
    image_url = normalize_image_data(image_value)
    response = client.chat.completions.create(
        model=MINIMAX_MODEL,
        messages=[
            {"role": "system", "content": prompt["system"]},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt["user_text"]},
                    {"type": "image_url", "image_url": {"url": image_url}},
                ],
            },
        ],
        temperature=0,
        stream=False,
    )
    return response.choices[0].message.content


def parse_json_response(raw):
    text = (raw or "").strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:].strip()
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end >= start:
        text = text[start:end + 1]
    return json.loads(text)


def validate_ip_result(result):
    grade = result.get("ip_grade")
    if grade not in {"S", "A", "B", "C", "D", "E"}:
        raise ValueError(f"ip_grade 非法：{grade}")
    result["match_score"] = float(result.get("match_score", 0))
    reason = str(result.get("reason") or "").strip()
    if not reason:
        raise ValueError("reason 不能为空")
    result["reason"] = reason
    result.setdefault("matched_grade_rule", grade)
    result.setdefault("matched_rules", [])
    if not isinstance(result["matched_rules"], list):
        result["matched_rules"] = []
    result.setdefault("tags", [])
    return result


def validate_material_result(result):
    result.setdefault("material_type", "其他")
    result.setdefault("material_category", "其他")
    result.setdefault("material_reason", "")
    result["confidence"] = float(result.get("confidence", 0))
    result.setdefault("matched_tags", [])
    result.setdefault("matched_rules", [])
    result.setdefault("pre_filter", {"hit": False, "matched_keywords": [], "source_fields": []})
    return result


def insert_log(cursor, product, analysis_type, status, input_snapshot, rules, experiences, request_payload, raw_response, parsed_result, score, error_message=None):
    rules_snapshot = compact_rules_for_log(rules)
    request_snapshot = compact_request_for_log(request_payload)
    cursor.execute(
        """
        INSERT INTO cp_ai_analysis_log
          (source_table, product_id, date_record, analysis_type, model_provider, model_name, prompt_version,
           input_snapshot, rules_snapshot, experience_snapshot, request_payload, raw_response,
           parsed_result, match_score, status, error_message)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            product["source_table"],
            str(product["product_id"]),
            product.get("date_record"),
            analysis_type,
            "MiniMax" if request_payload else "LOCAL",
            MINIMAX_MODEL if request_payload else "PRE_FILTER",
            PROMPT_VERSION,
            truncate_text(input_snapshot),
            truncate_text(rules_snapshot),
            truncate_text(experiences),
            truncate_text(request_snapshot),
            truncate_text(raw_response),
            truncate_text(parsed_result) if parsed_result is not None else None,
            score,
            status,
            truncate_text(error_message, 20000),
        ),
    )
    return cursor.lastrowid


def maybe_insert_experience(cursor, log_id, product, analysis_type, result, score, raw_response):
    experience_type = None
    if score >= 85:
        experience_type = "SUCCESS"
    elif score <= 15:
        experience_type = "FAILURE"
    if not experience_type:
        return None
    matched_rules = result.get("matched_rules") or []
    reason = result.get("reason") or result.get("material_reason") or ""
    summary = {
        "analysis_type": analysis_type,
        "result": result,
    }
    cursor.execute(
        """
        INSERT INTO cp_ai_analysis_experience
          (experience_type, analysis_type, source_log_id, source_table, product_id, date_record,
           product_title, product_selling_points, result_summary, matched_rules, reason, match_score, raw_response, enabled)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1)
        """,
        (
            experience_type,
            analysis_type,
            log_id,
            product["source_table"],
            str(product["product_id"]),
            product.get("date_record"),
            product.get("title"),
            product.get("selling_points"),
            json_dumps(summary),
            json_dumps(matched_rules),
            reason,
            score,
            raw_response,
        ),
    )
    return cursor.lastrowid


def update_product_success(cursor, product, source, analysis_type, result):
    meta = PRODUCT_SOURCES[source]
    table = meta["table"]
    id_field = meta["id_field"]
    date_field = meta["date_field"]
    if analysis_type == "IP":
        cursor.execute(
            f"""
            UPDATE `{table}`
            SET ip_grade = %s, ip_reason = %s, ip_tags = %s
            WHERE `{id_field}` = %s AND `{date_field}` = %s
            """,
            (
                result["ip_grade"],
                result.get("reason", ""),
                json_dumps(result),
                str(product["product_id"]),
                product.get("date_record"),
            ),
        )
    else:
        cursor.execute(
            f"""
            UPDATE `{table}`
            SET material_analysis = %s
            WHERE `{id_field}` = %s AND `{date_field}` = %s
            """,
            (
                json_dumps(result),
                str(product["product_id"]),
                product.get("date_record"),
            ),
        )


def run_analysis(cursor, source, product, analysis_type, write=False):
    input_snapshot = build_input_snapshot(product)
    if analysis_type == "MATERIAL":
        prefiltered = prefilter_material(product)
        if prefiltered:
            log_id = insert_log(
                cursor, product, "MATERIAL", "SUCCESS", input_snapshot, [], [], None,
                json_dumps(prefiltered), prefiltered, prefiltered["confidence"], None
            )
            maybe_insert_experience(cursor, log_id, product, "MATERIAL", prefiltered, prefiltered["confidence"], json_dumps(prefiltered))
            if write:
                update_product_success(cursor, product, source, "MATERIAL", prefiltered)
            return {"analysis_type": "MATERIAL", "result": prefiltered, "log_id": log_id, "prefiltered": True}

    rules = load_rules(cursor, analysis_type)
    experiences = load_experiences(cursor, analysis_type)
    prompt = build_ip_prompt(product, rules, experiences) if analysis_type == "IP" else build_material_prompt(product, rules, experiences)
    request_payload = {
        "model": MINIMAX_MODEL,
        "prompt_version": PROMPT_VERSION,
        "system": prompt["system"],
        "user_text": prompt["user_text"],
        "has_image": bool(product.get("image_base64")),
    }

    raw_response = None
    try:
        raw_response = call_minimax(prompt, product.get("image_base64"))
        parsed = parse_json_response(raw_response)
        parsed = validate_ip_result(parsed) if analysis_type == "IP" else validate_material_result(parsed)
        score = parsed["match_score"] if analysis_type == "IP" else parsed["confidence"]
        log_id = insert_log(cursor, product, analysis_type, "SUCCESS", input_snapshot, rules, experiences, request_payload, raw_response, parsed, score, None)
        maybe_insert_experience(cursor, log_id, product, analysis_type, parsed, score, raw_response)
        if write:
            update_product_success(cursor, product, source, analysis_type, parsed)
        return {"analysis_type": analysis_type, "result": parsed, "log_id": log_id, "prefiltered": False}
    except Exception as exc:
        log_id = insert_log(cursor, product, analysis_type, "FAILED", input_snapshot, rules, experiences, request_payload, raw_response, None, None, str(exc))
        return {"analysis_type": analysis_type, "error": str(exc), "log_id": log_id, "prefiltered": False}


def run_material_prefilter(cursor, source, product, write=False):
    input_snapshot = build_input_snapshot(product)
    prefiltered = prefilter_material(product)
    if not prefiltered:
        return None
    log_id = insert_log(
        cursor, product, "MATERIAL", "SUCCESS", input_snapshot, [], [], None,
        json_dumps(prefiltered), prefiltered, prefiltered["confidence"], None
    )
    maybe_insert_experience(cursor, log_id, product, "MATERIAL", prefiltered, prefiltered["confidence"], json_dumps(prefiltered))
    if write:
        update_product_success(cursor, product, source, "MATERIAL", prefiltered)
    return {"analysis_type": "MATERIAL_PREFILTER", "result": prefiltered, "log_id": log_id, "prefiltered": True}


def main():
    parser = argparse.ArgumentParser(description="单商品 AI 分析测试脚本")
    parser.add_argument("--source", choices=PRODUCT_SOURCES.keys(), default="fastmoss")
    parser.add_argument("--product-id")
    parser.add_argument("--date-record")
    parser.add_argument("--analysis", choices=["ip", "material", "both"], default="both")
    parser.add_argument("--write", action="store_true", help="写回商品表；默认只写分析日志和打印结果")
    parser.add_argument("--no-commit", action="store_true", help="调试用：执行后回滚")
    args = parser.parse_args()

    conn = connect_db()
    try:
        with conn.cursor() as cursor:
            product = load_product(cursor, args.source, args.product_id, args.date_record)
            analysis_types = ["IP", "MATERIAL"] if args.analysis == "both" else [args.analysis.upper()]
            results = []
            for analysis_type in analysis_types:
                results.append(run_analysis(cursor, args.source, product, analysis_type, write=args.write))
            if args.no_commit:
                conn.rollback()
            else:
                conn.commit()
            print(json.dumps({
                "product": build_input_snapshot(product),
                "write_product_table": args.write,
                "committed": not args.no_commit,
                "results": results,
            }, ensure_ascii=False, indent=2))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
