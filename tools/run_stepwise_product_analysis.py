import argparse
import json
import time

from analyze_batch_products import fetch_product_keys
from analyze_single_product import (
    PRODUCT_SOURCES,
    build_input_snapshot,
    connect_db,
    run_analysis,
    run_material_prefilter,
    load_product,
)


def print_json(title, data):
    print("\n" + "=" * 80, flush=True)
    print(title, flush=True)
    print("=" * 80, flush=True)
    print(json.dumps(data, ensure_ascii=False, indent=2), flush=True)


def summarize_result(result):
    if result.get("error"):
        return {
            "analysis_type": result.get("analysis_type"),
            "status": "FAILED",
            "error": result.get("error"),
            "log_id": result.get("log_id"),
            "prefiltered": result.get("prefiltered"),
        }
    parsed = result.get("result") or {}
    if result.get("analysis_type") == "IP":
        return {
            "analysis_type": "IP",
            "status": "SUCCESS",
            "ip_grade": parsed.get("ip_grade"),
            "match_score": parsed.get("match_score"),
            "reason": parsed.get("reason"),
            "matched_rules": parsed.get("matched_rules"),
            "tags": parsed.get("tags"),
            "log_id": result.get("log_id"),
            "prefiltered": result.get("prefiltered"),
        }
    return {
        "analysis_type": "MATERIAL",
        "status": "SUCCESS",
        "material_type": parsed.get("material_type"),
        "material_category": parsed.get("material_category"),
        "confidence": parsed.get("confidence"),
        "material_reason": parsed.get("material_reason"),
        "matched_rules": parsed.get("matched_rules"),
        "matched_tags": parsed.get("matched_tags"),
        "pre_filter": parsed.get("pre_filter"),
        "log_id": result.get("log_id"),
        "prefiltered": result.get("prefiltered"),
    }


def main():
    parser = argparse.ArgumentParser(description="逐条商品AI分析入口：逐条输出、逐条提交")
    parser.add_argument("--source", choices=PRODUCT_SOURCES.keys(), default="fastmoss")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--analysis", choices=["ip", "material", "both"], default="both")
    parser.add_argument("--write", action="store_true", help="写回商品表；不加则只写日志/经验库")
    parser.add_argument("--no-commit", action="store_true", help="每条执行后回滚，便于纯测试")
    parser.add_argument("--start-offset", type=int, default=0, help="跳过前N条，便于断点测试")
    args = parser.parse_args()

    analysis_types = ["IP", "MATERIAL"] if args.analysis == "both" else [args.analysis.upper()]
    total_start = time.perf_counter()
    summaries = []

    conn = connect_db()
    try:
        with conn.cursor() as cursor:
            keys = fetch_product_keys(cursor, args.source, args.limit + args.start_offset)
            keys = keys[args.start_offset:args.start_offset + args.limit]
    finally:
        conn.close()

    print_json("待分析商品", {
        "source": args.source,
        "limit": args.limit,
        "start_offset": args.start_offset,
        "analysis": args.analysis,
        "write_product_table": args.write,
        "no_commit": args.no_commit,
        "count": len(keys),
        "keys": keys,
    })

    for index, key in enumerate(keys, start=1):
        item_start = time.perf_counter()
        conn = connect_db()
        try:
            with conn.cursor() as cursor:
                product = load_product(cursor, args.source, key["product_id"], key["date_record"])
                product_snapshot = build_input_snapshot(product)
                print_json(f"开始第 {index}/{len(keys)} 条", product_snapshot)

                results = []
                if args.analysis == "both":
                    step_start = time.perf_counter()
                    print(f"[{index}/{len(keys)}] 开始 MATERIAL_PREFILTER 材质初步筛选...", flush=True)
                    prefilter_result = run_material_prefilter(cursor, args.source, product, write=args.write)
                    if prefilter_result:
                        result_summary = summarize_result(prefilter_result)
                        result_summary["elapsed_seconds"] = round(time.perf_counter() - step_start, 2)
                        result_summary["skip_ip_and_material_ai"] = True
                        print_json(f"第 {index}/{len(keys)} 条 MATERIAL_PREFILTER 结果", result_summary)
                        results.append(result_summary)
                        if args.no_commit:
                            conn.rollback()
                            committed = False
                        else:
                            conn.commit()
                            committed = True
                        item_summary = {
                            "index": index,
                            "product_id": str(product["product_id"]),
                            "date_record": product.get("date_record"),
                            "committed": committed,
                            "elapsed_seconds": round(time.perf_counter() - item_start, 2),
                            "skipped_after_prefilter": True,
                            "results": results,
                        }
                        summaries.append(item_summary)
                        print_json(f"第 {index}/{len(keys)} 条完成", item_summary)
                        continue
                    print(f"[{index}/{len(keys)}] MATERIAL_PREFILTER 未命中，继续 IP -> MATERIAL AI...", flush=True)

                for analysis_type in analysis_types:
                    step_start = time.perf_counter()
                    print(f"[{index}/{len(keys)}] 开始 {analysis_type} 分析...", flush=True)
                    result = run_analysis(cursor, args.source, product, analysis_type, write=args.write)
                    result_summary = summarize_result(result)
                    result_summary["elapsed_seconds"] = round(time.perf_counter() - step_start, 2)
                    print_json(f"第 {index}/{len(keys)} 条 {analysis_type} 结果", result_summary)
                    results.append(result_summary)

                if args.no_commit:
                    conn.rollback()
                    committed = False
                else:
                    conn.commit()
                    committed = True

                item_summary = {
                    "index": index,
                    "product_id": str(product["product_id"]),
                    "date_record": product.get("date_record"),
                    "committed": committed,
                    "elapsed_seconds": round(time.perf_counter() - item_start, 2),
                    "results": results,
                }
                summaries.append(item_summary)
                print_json(f"第 {index}/{len(keys)} 条完成", item_summary)
        except Exception as exc:
            conn.rollback()
            error_summary = {
                "index": index,
                "product_id": str(key.get("product_id")),
                "date_record": key.get("date_record"),
                "committed": False,
                "elapsed_seconds": round(time.perf_counter() - item_start, 2),
                "error": str(exc),
            }
            summaries.append(error_summary)
            print_json(f"第 {index}/{len(keys)} 条异常", error_summary)
        finally:
            conn.close()

    print_json("全部完成", {
        "total_elapsed_seconds": round(time.perf_counter() - total_start, 2),
        "items": summaries,
    })


if __name__ == "__main__":
    main()
