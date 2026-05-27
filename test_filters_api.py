import requests
import json

BASE_URL = "http://127.0.0.1:5000"

filters_to_test = [
    {"name": "dateRecord", "params": {"date_start": "2026-05-01", "date_end": "2026-05-31"}},
    {"name": "launchTime", "params": {"launch_start": "2026-01-01", "launch_end": "2026-12-31"}},
    {"name": "commission", "params": {"commission_min": "10", "commission_max": "30"}},
    {"name": "author", "params": {"author_min": "1", "author_max": "100"}},
    {"name": "productCardRatio", "params": {"product_card_ratio_min": "10", "product_card_ratio_max": "90"}},
    {"name": "transportFee", "params": {"transport_fee_min": "0", "transport_fee_max": "10"}},
    {"name": "sold", "params": {"sold_min": "100", "sold_max": "1000"}},
    {"name": "videoRatio", "params": {"video_ratio_min": "10", "video_ratio_max": "90"}},
    {"name": "rating", "params": {"rating_min": "4", "rating_max": "5"}},
    {"name": "price", "params": {"price_min": "1", "price_max": "100"}},
    {"name": "basePrice", "params": {"base_price_min": "1", "base_price_max": "100"}},
    {"name": "auditStatus", "params": {"audit_status": "PENDING"}},
]

sources = ["unified", "fastmoss", "kalodata"]

def test_filters():
    results = {}
    for source in sources:
        results[source] = {}
        print(f"Testing source: {source}")
        for f in filters_to_test:
            params = {"source": source}
            params.update(f["params"])
            try:
                resp = requests.get(f"{BASE_URL}/api/products", params=params, timeout=5)
                if resp.status_code == 200:
                    data = resp.json()
                    if data.get("ok"):
                        results[source][f["name"]] = "PASS"
                    else:
                        results[source][f["name"]] = f"FAIL: {data.get('error')}"
                else:
                    results[source][f["name"]] = f"ERROR: Status {resp.status_code}"
            except Exception as e:
                results[source][f["name"]] = f"EXCEPTION: {str(e)}"
    
    print("\n--- Summary ---")
    header = "Filter".ljust(20) + " | " + " | ".join(s.center(10) for s in sources)
    print(header)
    print("-" * len(header))
    for f in filters_to_test:
        row = f["name"].ljust(20) + " | "
        for source in sources:
            res = results[source].get(f["name"], "N/A")
            row += res.center(10) + " | "
        print(row)

if __name__ == "__main__":
    # Ensure the app is running
    try:
        requests.get(BASE_URL)
    except:
        print(f"Error: App is not running at {BASE_URL}. Please start it first.")
        exit(1)
    test_filters()
