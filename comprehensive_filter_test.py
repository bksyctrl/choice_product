import requests
import json

BASE_URL = "http://127.0.0.1:5000"

# 12 filters from frontend
filters_to_test = [
    {"name": "dateRecord", "params": {"date_start": "2026-05-18", "date_end": "2026-05-19"}},
    {"name": "launchTime", "params": {"launch_start": "2020-01-01", "launch_end": "2026-12-31"}},
    {"name": "commission", "params": {"commission_min": "1", "commission_max": "50"}},
    {"name": "author", "params": {"author_min": "0", "author_max": "10000"}},
    {"name": "productCardRatio", "params": {"product_card_ratio_min": "0", "product_card_ratio_max": "100"}},
    {"name": "transportFee", "params": {"transport_fee_min": "0", "transport_fee_max": "100"}},
    {"name": "sold", "params": {"sold_min": "0", "sold_max": "1000000"}},
    {"name": "videoRatio", "params": {"video_ratio_min": "0", "video_ratio_max": "100"}},
    {"name": "rating", "params": {"rating_min": "0", "rating_max": "5"}},
    {"name": "price", "params": {"price_min": "0", "price_max": "1000"}},
    {"name": "basePrice", "params": {"base_price_min": "0", "base_price_max": "1000"}},
    {"name": "auditStatus", "params": {"audit_status": "PENDING"}},
]

sources = ["unified", "fastmoss", "kalodata"]

def get_count(source, extra_params=None):
    params = {"source": source, "page_size": 1}
    if extra_params:
        params.update(extra_params)
    try:
        resp = requests.get(f"{BASE_URL}/api/products", params=params, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            if data.get("ok"):
                return data["data"]["total"]
    except Exception as e:
        print(f"Error getting count for {source}: {e}")
    return -1

def run_comprehensive_test():
    print("Starting comprehensive filter test...\n")
    results = {}
    
    for source in sources:
        print(f"Testing source: {source}")
        total_count = get_count(source)
        print(f"  Total count: {total_count}")
        
        results[source] = {"total": total_count, "filters": {}}
        
        for f in filters_to_test:
            f_name = f["name"]
            filtered_count = get_count(source, f["params"])
            
            status = "UNKNOWN"
            if filtered_count == -1:
                status = "ERROR"
            elif filtered_count == total_count:
                # If we apply a broad range and it returns everything, it might be ignored or just all match.
                # For filters like auditStatus, it shouldn't be equal if there are multiple statuses.
                # For numeric ranges 0-1000000, it's likely it matches everything.
                status = "LIKELY_IGNORED_OR_ALL_MATCH"
            elif filtered_count < total_count:
                status = "WORKING"
            elif filtered_count == 0 and total_count > 0:
                status = "WORKING_OR_DATA_MISMATCH"
            
            results[source]["filters"][f_name] = {
                "count": filtered_count,
                "status": status
            }
            print(f"    - {f_name.ljust(20)}: {str(filtered_count).rjust(8)} / {total_count} ({status})")

    # Final summary in a more readable format
    print("\n" + "="*80)
    print("FILTER TEST SUMMARY")
    print("="*80)
    header = "Filter".ljust(20) + " | " + " | ".join(s.center(15) for s in sources)
    print(header)
    print("-" * len(header))
    
    for f in filters_to_test:
        f_name = f["name"]
        row = f_name.ljust(20) + " | "
        for source in sources:
            res = results[source]["filters"][f_name]
            count_str = f"{res['count']}"
            if res['status'] == "WORKING":
                row += f"{count_str} (OK)".center(15) + " | "
            elif res['status'] == "LIKELY_IGNORED_OR_ALL_MATCH":
                row += f"{count_str} (IGNORED?)".center(15) + " | "
            else:
                row += f"{count_str} ({res['status']})".center(15) + " | "
        print(row)

if __name__ == "__main__":
    run_comprehensive_test()
