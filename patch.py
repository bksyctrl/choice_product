import sys
import re

with open('D:/choice_product/app.py', 'r', encoding='utf-8') as f:
    content = f.read()

# find build_illustration_generation_prompt
idx = content.find('def build_illustration_generation_prompt')

new_func = """
def generate_artwork_description(api_key, image_url):
    system_prompt = (
        "You are an expert prompt engineer for Image-to-Image models. "
        "Your task is to accurately describe the 2D artwork/pattern/graphic printed on the product in the provided image."
    )
    user_text = '''
Please provide a dense, highly detailed descriptive paragraph of the artwork printed on the product surface. 
Focus ONLY on the graphic, pattern, text, colors, layout, and style. 
DO NOT describe the product carrier (e.g., bag, shirt, phone case, handle), the model, the background, or the lighting.
Provide just the description in English, ready to be used as a generative prompt.
Example: "A colorful floral pattern with varying-sized stylized blooms and precise colors on a taupe-brown background."
'''.strip()
    
    response = requests.post(
        f"{MINIMAX_BASE_URL.rstrip('/')}/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={
            "model": MINIMAX_MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": user_text},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ],
                },
            ],
            "temperature": 0,
            "stream": False,
        },
        timeout=60,
    )
    response.raise_for_status()
    data = response.json()
    try:
        raw_answer = data["choices"][0]["message"]["content"]
        return raw_answer.strip()
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("无法获取原图的插画描述") from exc


"""

content = content[:idx] + new_func + content[idx:]

old_build = """def build_illustration_generation_prompt(retry_feedback=""):


    prompt = "\\n".join([
        \"\"\"A detailed, high-resolution photo of a flat, seamless textile pattern swatch, precisely extracted from the printed design on the product (the bag) in image_0.png. The output must be a single, flat, repeating tile asset filling the entire square canvas. It must feature the identical colorful floral pattern seen in image_0.png, with all its varying-sized stylized blooms and precise colors on the specific taupe-brown background. The texture is that of a scanned fabric sample, completely smooth and devoid of any three-dimensional product contours, creases, bag hardware (handles, seams, zippers, tags), or the bag shape itself. The model, her body, clothing, and the background environment from image_0.png are entirely removed. Crucially, the 'GLOBAL PICKS' watermark has been perfectly erased, leaving only the uninterrupted floral pattern field. All original colors, motifs, layout, and texture grain from image_0.png are preserved, but in a flattened, clean layout. Do not generate a new product, model, or scene. This is a clean digital pattern asset for direct use.\"\"\"
    ])

    return prompt"""

new_build = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
    feedback_text = stringify(retry_feedback).strip()
    feedback_part = f"\\nPrevious failed because: {feedback_text[:260]}. Fix this exactly in this attempt." if feedback_text else ""
    
    desc_part = f"It must feature the identical artwork as described here: {artwork_description}" if artwork_description else ""
    
    prompt = "\\n".join(part for part in [
        "Extract the existing artwork/pattern printed on the product surface in THIS reference image.",
        "Output only the extracted flat artwork asset, never a product photo or mockup.",
        "If the source has one main graphic, text, flag, logo-style mark, or slogan, output that single graphic only, centered on plain white/transparent-looking background. Do NOT turn it into a repeated background pattern.",
        "If the source has an all-over repeating textile pattern, output only that repeat pattern filling the square canvas.",
        desc_part,
        "Keep exact layout, colors, darkness, saturation, texture grain, spacing and scale from the reference image.",
        "Remove the carrier and scene: clothing, bag, person, model, handle, strap, zipper, pocket, seam, hardware, tag, background, shadow.",
        "Remove unrelated watermarks, ads, prices, labels. If the text is the actual printed graphic on the product, keep the text exactly.",
        "Do not invent, redesign, recolor, add decorative backgrounds, add borders, add new motifs, or use any template from previous images.",
        "Failure: wrong subject, missing text/flag, repeated pattern when source is a single graphic, visible product, or mismatched color."
    ] if part)
    
    prompt += feedback_part
    return prompt[:1450]"""

if old_build in content:
    content = content.replace(old_build, new_build)
else:
    print("WARNING: old_build string not found.")

old_call = """    best_bytes = b""
    best_score = -1.0
    best_feedback = ""
    retry_feedback = ""
    for attempt in range(1, MINIMAX_ILLUSTRATION_MAX_ATTEMPTS + 1):
        prompt = build_illustration_generation_prompt(retry_feedback)"""

new_call = """    # 阶段一：用大模型查看原图，生成详细描述
    print(f"[MINIMAX_IMAGE_QC] task_id={task_id} generating artwork description...", flush=True)
    try:
        artwork_description = generate_artwork_description(api_key, image_file)
        print(f"[MINIMAX_IMAGE_QC] task_id={task_id} artwork description: {artwork_description}", flush=True)
    except Exception as e:
        print(f"[MINIMAX_IMAGE_QC] task_id={task_id} failed to get description: {e}", flush=True)
        artwork_description = ""

    best_bytes = b""
    best_score = -1.0
    best_feedback = ""
    retry_feedback = ""
    for attempt in range(1, MINIMAX_ILLUSTRATION_MAX_ATTEMPTS + 1):
        prompt = build_illustration_generation_prompt(artwork_description, retry_feedback)"""

if old_call in content:
    content = content.replace(old_call, new_call)
else:
    print("WARNING: old_call string not found.")

with open('D:/choice_product/app.py', 'w', encoding='utf-8') as f:
    f.write(content)

print("Patch applied successfully.")
