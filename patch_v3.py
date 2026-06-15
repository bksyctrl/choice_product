import sys

with open('D:/choice_product/app.py', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update generate_artwork_description
old_vlm_func = """def generate_artwork_description(api_key, image_url):
    system_prompt = (
        "You are an expert art director and prompt engineer. "
        "Analyze the printed artwork/pattern on the main product in the image and write a highly detailed, professional description of just the flat graphic design."
    )
    user_text = '''
Please describe the flat 2D artwork/pattern printed on the product. 
You must describe:
1. The exact subjects/motifs (e.g., specific flowers, cartoon characters, typography, geometric shapes).
2. The exact color palette (background color, main element colors).
3. The layout and art style (e.g., hand-drawn watercolor, dense repeating floral, centered minimalist logo).

CRITICAL: DO NOT describe the product itself (bag, shirt, phone case), the straps, zippers, the model, the background, or lighting. Describe ONLY the flat design as if looking at the original illustrator file.
Write the description as a single dense paragraph in English.
'''.strip()"""

new_vlm_func = """def generate_artwork_description(api_key, image_url):
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

    user_text = "请根据以上要求，观察图片并直接输出英文提示词（Prompt）。"
"""

if old_vlm_func in content:
    content = content.replace(old_vlm_func, new_vlm_func)
else:
    print('WARNING: old_vlm_func not found')

# 2. Update build_illustration_generation_prompt
old_build_func = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
    feedback_text = stringify(retry_feedback).strip()
    feedback_part = f"\\nPrevious failed because: {feedback_text[:260]}. Fix this exactly in this attempt." if feedback_text else ""
    
    # 结合用户要求的绝对不变量和 AI 生成的动态特征描述
    prompt = "\\n".join(part for part in [
        "A detailed, high-resolution photo of a flat, seamless textile pattern swatch, precisely extracted from the printed design on the product in the reference image.",
        "The output must be a single, flat, repeating tile asset filling the entire square canvas.",
        f"It must feature the identical artwork as described here: {artwork_description}" if artwork_description else "",
        "The texture is that of a scanned fabric sample, completely smooth and devoid of any three-dimensional product contours, creases, hardware (handles, seams, zippers, tags), or the product shape itself.",
        "The model, body, clothing, and the background environment from the reference image are entirely removed.",
        "Crucially, any watermarks, ads, or UI overlays have been perfectly erased, leaving only the uninterrupted artwork field.",
        "All original colors, motifs, layout, and texture grain from the reference image are preserved, but in a flattened, clean layout.",
        "Do not generate a new product, model, or scene. This is a clean digital pattern asset for direct use."
    ] if part)
    
    prompt += feedback_part
    return prompt[:1450]"""

new_build_func = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
    feedback_text = stringify(retry_feedback).strip()
    feedback_part = f"\\nPrevious failed because: {feedback_text[:260]}. Fix this exactly in this attempt." if feedback_text else ""
    
    if artwork_description:
        prompt = artwork_description
    else:
        prompt = "A detailed, high-resolution photo of a flat, seamless textile pattern swatch, precisely extracted from the printed design on the product in the reference image. The output must be a single, flat, repeating tile asset filling the entire square canvas. The texture is that of a scanned fabric sample, completely smooth and devoid of any three-dimensional product contours, creases, hardware, or the product shape itself. Do not generate a new product, model, or scene."
    
    prompt += feedback_part
    return prompt[:1450]"""

if old_build_func in content:
    content = content.replace(old_build_func, new_build_func)
else:
    print('WARNING: old_build_func not found')

with open('D:/choice_product/app.py', 'w', encoding='utf-8') as f:
    f.write(content)

print('Update successful')
