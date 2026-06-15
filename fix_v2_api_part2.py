import sys
import os

app_path = 'D:/choice_product/app.py'
with open(app_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. 修复 analyze_illustration_extractability
# 找到函数开始
func_name = 'def analyze_illustration_extractability(image_value, title=""): '
# 实际上 grep 看到的没有末尾空格
func_name = 'def analyze_illustration_extractability(image_value, title=""): '

start_idx = content.find('def analyze_illustration_extractability(image_value, title=""):')
if start_idx != -1:
    end_idx = content.find('def ', start_idx + 10)
    if end_idx == -1: end_idx = len(content)
    
    old_func = content[start_idx:end_idx]
    
    new_func = """def analyze_illustration_extractability(image_value, title=""):
    api_key = resolve_minimax_api_key()
    if not api_key:
        raise RuntimeError("缺少 MINIMAX_API_KEY，无法判断插画可提取性")
    api_key = stringify(api_key).strip().removeprefix("Bearer ").strip()
    image_url = normalize_vision_image_url(image_value)
    if not image_url:
        raise RuntimeError("商品主图为空，无法判断插画可提取性")
    system_prompt = (
        "你是商品视觉选品助手。你的任务只判断商品主图中是否存在适合迁移到手机壳上的"
        "插画、图案、纹理或装饰元素。不要生成图片，不要输出长分析。"
    )
    user_text = f\"\"\"
请判断这张商品主图是否适合提取插画/图案/纹理并迁移到手机壳设计中。

商品标题：{title or "无"}

判断标准：
1. 如果存在清晰的插画、图案、纹理、装饰元素，并且脱离原商品后仍可用于手机壳设计，返回 true。
2. 如果图片主要是品牌Logo、商标文字、纯商品实拍、人物、复杂广告背景，返回 false。
3. 如果图案太小、太模糊、遮挡严重、很难迁移为手机壳素材，返回 false。
4. 如果有可用图案但同时存在品牌Logo或文字，只要可避开风险区域并提取非品牌图案，可以返回 true，并在 reason 中说明需要避开品牌/文字区域。

只返回 JSON，不要 Markdown，不要解释：
{{
  "can_extract_illustration": true 或 false,
  "reason": "一句话说明原因，50字以内"
}}
\"\"\".strip()
    
    payload = {
        "model": "abab6.5s-chat",
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_text},
                    {"type": "image_url", "image_url": {"url": image_url}}
                ]
            }
        ],
        "temperature": 0
    }

    response = requests.post(
        "https://api.minimax.io/v1/text/chatcompletion_v2",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=payload,
        timeout=60
    )
    response.raise_for_status()
    data = response.json()
    try:
        raw_answer = data["choices"][0]["message"]["content"]
        parsed = parse_llm_json_object(raw_answer)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        raise RuntimeError("视觉判断服务返回格式异常") from exc
    reason = stringify(parsed.get("reason")).strip()
    raw_extractable = parsed.get("can_extract_illustration")
    if isinstance(raw_extractable, bool):
        can_extract = raw_extractable
    else:
        can_extract = stringify(raw_extractable).strip().lower() in {"1", "true", "yes", "y", "可提取", "可以", "是"}
    return {
        "can_extract_illustration": can_extract,
        "reason": reason or "模型未给出原因",
    }

"""
    content = content[:start_idx] + new_func + content[end_idx:]
    with open(app_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Fixed analyze_illustration_extractability")
else:
    print("Could not find analyze_illustration_extractability")
