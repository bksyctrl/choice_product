import requests
import json

BASE_URL = "http://127.0.0.1:5000"

# Narrow filters to confirm if they work
filters_to_test = [
    {"name": "launchTime_narrow", "params": {"launch_start": "2026-05-01", "launch_end": "2026-05-02"}},
    {"name": "transportFee_narrow", "params": {"transport_fee_min": "100", "transport_fee_max": "101"}},
    {"name": "sold_narrow", "params": {"sold_min": "1000", "sold_max": "1001"}},
    {"name": "rating_narrow", "params": {"rating_min": "4.9", "rating_max": "5.0"}},
    {"name": "price_narrow", "params": {"price_min": "100", "price_max": "101"}},
    {"name": "basePrice_narrow", "params": {"base_price_min": "100", "base_price_max": "101"}},
    {"name": "productCardRatio_narrow", "params": {"product_card_ratio_min": "50", "product_card_ratio_max": "51"}},
    {"name": "videoRatio_narrow", "params": {"video_ratio_min": "50", "video_ratio_max": "51"}},
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

def run_narrow_test():
    print("Starting narrow filter test to confirm if they are ignored...\n")
    
    for source in sources:
        print(f"Testing source: {source}")
        total_count = get_count(source)
        
        for f in filters_to_test:
            f_name = f["name"]
            filtered_count = get_count(source, f["params"])
            
            status = "UNKNOWN"
            if filtered_count == total_count:
                status = "TOTALLY_IGNORED"
            elif filtered_count < total_count:
                status = "WORKING"
            
            print(f"    - {f_name.ljust(25)}: {str(filtered_count).rjust(8)} / {total_count} ({status})")

if __name__ == "__main__":
    run_narrow_test()
