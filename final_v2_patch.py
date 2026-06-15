import sys
import os
import json
import requests
import re

app_path = 'D:/choice_product/app.py'
with open(app_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Helper to inject logging and better error handling
def patch_minimax_vision_functions(content):
    # 1. generate_artwork_description
    gen_desc_start = content.find('def generate_artwork_description(api_key, image_url):')
    gen_desc_end = content.find('def ', gen_desc_start + 10)
    if gen_desc_end == -1: gen_desc_end = len(content)
    
    new_gen_desc = """def generate_artwork_description(api_key, image_url):
    system_prompt = '''角色设定 (Role):
你现在是一位顶级的数字资产提取专家和高级纹理艺术家。你的任务是敏锐地观察用户上传的产品参考图，并为图像生成模型（如 Midjourney, DALL-E, 或 Stable Diffusion）撰写极其精确的英文提示词（Prompt），目的是将附着在 3D 物品上的平面图案完美剥离出来。

工作流 (Workflow):
视觉解构： 忽略所有 3D 结构（包的形状、衣服的褶皱、人物、背景、光影）。只盯住“印刷图案”本身。
细节提取： 准确识别图案的风格、核心元素（如花朵、几何体、Logo）、排列方式（单图居中还是无缝平铺铺满）、精确的颜色组成以及底色。
排除干扰： 敏锐识别出必须去除的元素（如水印、品牌 Logo、缝线、拉链、反光）。
输出提示词： 根据以上分析，输出一段用于生成平面资产的英文提示词。

提示词撰写规则 (Rules for Prompt Writing):
首句定调： 必须以 "A detailed, high-resolution photo of a flat, seamless textile pattern swatch..." 开头。
详尽描述图案： 用专业的视觉词汇描述提取出的图案细节。
强化否定指令： 明确指出“不要什么”。
格式要求： 只输出最终的英文提示词本身，不需要任何解释。'''.strip()

    payload = {
        "model": "abab6.5s-chat",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": f"{system_prompt}\\n\\n请观察这张图片并直接输出英文提示词（Prompt）。"},
                    {"type": "image_url", "image_url": {"url": image_url}}
                ]
            }
        ]
    }
    
    print(f"[MINIMAX_V2] Requesting description for image...", flush=True)
    response = requests.post(
        "https://api.minimax.io/v1/text/chatcompletion_v2",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=payload,
        timeout=60
    )
    if response.status_code != 200:
        print(f"[MINIMAX_V2] Error {response.status_code}: {response.text}", flush=True)
        response.raise_for_status()
        
    data = response.json()
    try:
        raw_answer = data["choices"][0]["message"]["content"]
        return raw_answer.strip()
    except Exception as exc:
        print(f"[MINIMAX_V2] Parse error: {data}", flush=True)
        raise RuntimeError("无法获取原图的插画描述") from exc

"""

    # 2. analyze_illustration_extractability
    check_start = content.find('def analyze_illustration_extractability(image_value, title=""):')
    check_end = content.find('def ', check_start + 10)
    
    new_check = """def analyze_illustration_extractability(image_value, title=""):
    api_key = resolve_minimax_api_key()
    if not api_key: raise RuntimeError("缺少 MINIMAX_API_KEY")
    api_key = stringify(api_key).strip().removeprefix("Bearer ").strip()
    image_url = normalize_vision_image_url(image_value)
    
    user_text = f'''
请判断这张商品主图是否适合提取插画并迁移到手机壳设计中。
商品标题：{title or "无"}
判断标准：
1. 如果存在清晰的插画、图案、纹理，且可用于手机壳设计，返回 true。
2. 如果主要是品牌Logo、人物、纯实拍、复杂广告背景，返回 false。
只返回 JSON: {{"can_extract_illustration": true/false, "reason": "..."}}
'''.strip()
    
    payload = {
        "model": "abab6.5s-chat",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_text},
                    {"type": "image_url", "image_url": {"url": image_url}}
                ]
            }
        ]
    }

    response = requests.post(
        "https://api.minimax.io/v1/text/chatcompletion_v2",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=payload,
        timeout=60
    )
    if response.status_code != 200:
        print(f"[MINIMAX_V2] Check Error {response.status_code}: {response.text}", flush=True)
        response.raise_for_status()
    
    data = response.json()
    parsed = parse_llm_json_object(data["choices"][0]["message"]["content"])
    return {
        "can_extract_illustration": bool(parsed.get("can_extract_illustration")),
        "reason": parsed.get("reason", ""),
    }

"""

    # 3. evaluate_generated_illustration
    eval_start = content.find('def evaluate_generated_illustration(api_key, source_image_file, generated_bytes, task_id="illustration"):')
    eval_end = content.find('def ', eval_start + 10)
    
    new_eval = """def evaluate_generated_illustration(api_key, source_image_file, generated_bytes, task_id="illustration"):
    generated_image_url = image_bytes_to_data_url(generated_bytes)
    user_text = "Compare the artwork in Image 2 with the original in Image 1. Return JSON with 'match_score' (0-100) and 'reason'."
    
    payload = {
        "model": "abab6.5s-chat",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_text},
                    {"type": "image_url", "image_url": {"url": source_image_file}},
                    {"type": "image_url", "image_url": {"url": generated_image_url}}
                ]
            }
        ]
    }
    
    response = requests.post(
        "https://api.minimax.io/v1/text/chatcompletion_v2",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=payload,
        timeout=60
    )
    if response.status_code != 200:
        print(f"[MINIMAX_V2] Eval Error {response.status_code}: {response.text}", flush=True)
        response.raise_for_status()
        
    data = response.json()
    parsed = parse_llm_json_object(data["choices"][0]["message"]["content"])
    score = float(parsed.get("match_score", 0))
    return {
        "pass": score >= MINIMAX_ILLUSTRATION_PASS_SCORE,
        "is_same": score >= 80,
        "score": score,
        "feedback": parsed.get("reason", ""),
    }

"""

    # Perform replacements
    parts = []
    last_idx = 0
    
    # Sort replacements by start index
    replacements = [
        (check_start, check_end, new_check),
        (gen_desc_start, gen_desc_end, new_gen_desc),
        (eval_start, eval_end, new_eval)
    ]
    replacements.sort()
    
    for start, end, new in replacements:
        if start == -1: continue
        parts.append(content[last_idx:start])
        parts.append(new)
        last_idx = end
    parts.append(content[last_idx:])
    
    return "".join(parts)

new_content = patch_minimax_vision_functions(content)
with open(app_path, 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Final V2 Patch Applied.")
