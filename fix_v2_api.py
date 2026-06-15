import sys
import os

app_path = 'D:/choice_product/app.py'
with open(app_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. 修复 generate_artwork_description
old_gen_desc = content.find('def generate_artwork_description(api_key, image_url):')
next_def = content.find('def ', old_gen_desc + 10)
if next_def == -1: next_def = len(content)

new_gen_desc_func = """def generate_artwork_description(api_key, image_url):
    system_prompt = '''角色设定 (Role):
你现在是一位顶级的数字资产提取专家和高级纹理艺术家。你的任务是敏锐地观察用户上传的产品参考图，并为图像生成模型（如 Midjourney, DALL-E, 或 Stable Diffusion）撰写极其精确的英文提示词（Prompt），目的是将附着在 3D 物品上的平面图案完美剥离出来。

工作流 (Workflow):

视觉解构： 忽略所有 3D 结构（包的形状、衣服的褶皱、人物、背景、光影）。只盯住“印刷图案”本身。

细节提取： 准确识别图案的风格、核心元素（如花朵、几何体、Logo）、排列方式（单图居中还是无缝平铺铺满）、精确的颜色组成以及底色。

排除干扰： 敏锐识别出必须去除的元素（如水印、品牌 Logo、缝线、拉链、反光）。

输出提示词： 根据以上分析，输出一段用于生成平面资产的英文提示词。

提示词撰写规则 (Rules for Prompt Writing):

首句定调： 必须以 "A detailed, high-resolution photo of a flat, seamless textile pattern swatch..." 或类似强调“平面、无缝、资产”的词汇开头。

详尽描述图案： 用专业的视觉词汇描述提取出的图案细节（例如：stylized blooms, vector art, repeating tile）。

强化否定指令： 在提示词中必须明确指出“不要什么”。例如："completely devoid of 3D contours, creases, hardware, handles, models, shadows, watermarks..."

格式要求： 只输出最终的英文提示词本身，不需要任何解释。'''.strip()

    payload = {
        "model": "abab6.5s-chat",
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "请根据以上要求，观察图片并直接输出英文提示词（Prompt）。"},
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
    response.raise_for_status()
    data = response.json()
    try:
        raw_answer = data["choices"][0]["message"]["content"]
        return raw_answer.strip()
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("无法获取原图的插画描述") from exc

"""

# 2. 修复 evaluate_generated_illustration
old_eval = content.find('def evaluate_generated_illustration(api_key, source_image_file, generated_bytes, task_id="illustration"):')
next_def_eval = content.find('def ', old_eval + 10)
if next_def_eval == -1: next_def_eval = len(content)

new_eval_func = """def evaluate_generated_illustration(api_key, source_image_file, generated_bytes, task_id="illustration"):
    generated_image_url = image_bytes_to_data_url(generated_bytes)
    user_text = \"\"\"
You will receive two images:
Image 1: original product photo.
Image 2: generated extraction result.

Only judge whether the artwork/pattern/illustration in Image 2 is the same as the artwork printed on the product in Image 1.

Judge these points:
1. Same artwork subject: text, letters, numbers, flag, slogan, animal, flower, geometric pattern, texture, etc.
2. Same text content and layout if the original artwork contains text.
3. Same main colors, darkness, saturation and material texture.
4. Correct extraction format: a single original graphic should remain a single graphic; only a true repeating pattern should become a full-canvas pattern.
5. No product carrier should remain: no clothing, bag, person, handle, zipper, pocket, background or mockup.

Return JSON only:
{
  "is_same": true/false,
  "match_score": 0-100,
  "reason": "why they are not the same, or why they match",
  "prompt_advice": "short advice for the next image-to-image prompt"
}
\"\"\".strip()
    
    payload = {
        "model": "abab6.5s-chat",
        "messages": [
            {"role": "system", "content": "You are a strict visual QA judge. Compare only the source artwork on the product and the generated extraction result."},
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
    response.raise_for_status()
    data = response.json()
    try:
        raw_answer = data["choices"][0]["message"]["content"]
        parsed = parse_llm_json_object(raw_answer)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        raise RuntimeError("视觉质检服务返回格式异常") from exc
        
    score = float(parsed.get("match_score", 0))
    raw_same = parsed.get("is_same", parsed.get("pass"))
    is_same = raw_same if isinstance(raw_same, bool) else stringify(raw_same).strip().lower() in {"1", "true", "yes", "same", "match"}
    reason = stringify(parsed.get("reason")).strip()
    advice = stringify(parsed.get("prompt_advice") or parsed.get("advice")).strip()
    feedback_parts = [part for part in [reason, advice] if part]
    feedback = "；".join(feedback_parts).strip()[:260]
    passed = is_same and score >= MINIMAX_ILLUSTRATION_PASS_SCORE
    
    return {
        "pass": passed,
        "is_same": is_same,
        "score": score,
        "feedback": feedback or "The generated artwork does not match the source artwork closely enough.",
    }

"""

# 替换内容
content = content[:old_gen_desc] + new_gen_desc_func + content[next_def:]
# 重新计算 eval 的位置，因为上面的长度变了
old_eval_after = content.find('def evaluate_generated_illustration(api_key, source_image_file, generated_bytes, task_id="illustration"):')
next_def_eval_after = content.find('def ', old_eval_after + 10)
content = content[:old_eval_after] + new_eval_func + content[next_def_eval_after:]

with open(app_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fix applied successfully.")
