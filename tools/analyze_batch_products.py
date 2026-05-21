import argparse
import json

from analyze_single_product import (
    PRODUCT_SOURCES,
    build_input_snapshot,
    connect_db,
    load_product,
    run_analysis,
    run_material_prefilter,
)


def fetch_product_keys(cursor, source, limit):
    meta = PRODUCT_SOURCES[source]
    table = meta["table"]
    id_field = meta["id_field"]
    date_field = meta["date_field"]
    image_field = meta["image_field"]
    cursor.execute(
        f"""
        SELECT `{id_field}` AS product_id, `{date_field}` AS date_record
        FROM `{table}`
        WHERE `{image_field}` IS NOT NULL AND `{image_field}` <> ''
        ORDER BY `{date_field}` DESC, `id`
        LIMIT %s
        """,
        (limit,),
    )
    return cursor.fetchall()


def main():
    parser = argparse.ArgumentParser(description="批量测试商品AI分析链路")
    parser.add_argument("--source", choices=PRODUCT_SOURCES.keys(), default="fastmoss")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--analysis", choices=["ip", "material", "both"], default="both")
    parser.add_argument("--write", action="store_true", help="写回商品表；不加则只写日志/经验库")
    parser.add_argument("--no-commit", action="store_true", help="执行后回滚")
    args = parser.parse_args()

    conn = connect_db()
    summary = []
    try:
        with conn.cursor() as cursor:
            keys = fetch_product_keys(cursor, args.source, args.limit)
            analysis_types = ["IP", "MATERIAL"] if args.analysis == "both" else [args.analysis.upper()]
            for key in keys:
                product = load_product(cursor, args.source, key["product_id"], key["date_record"])
                item = {
                    "product": build_input_snapshot(product),
                    "results": [],
                }
                if args.analysis == "both":
                    prefilter_result = run_material_prefilter(cursor, args.source, product, write=args.write)
                    if prefilter_result:
                        item["results"].append(prefilter_result)
                        item["skipped_after_prefilter"] = True
                        summary.append(item)
                        continue
                for analysis_type in analysis_types:
                    item["results"].append(run_analysis(cursor, args.source, product, analysis_type, write=args.write))
                summary.append(item)
            if args.no_commit:
                conn.rollback()
            else:
                conn.commit()
            print(json.dumps({
                "source": args.source,
                "limit": args.limit,
                "analysis": args.analysis,
                "write_product_table": args.write,
                "committed": not args.no_commit,
                "items": summary,
            }, ensure_ascii=False, indent=2))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
