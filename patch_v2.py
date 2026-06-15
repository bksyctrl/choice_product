import sys

with open('D:/choice_product/app.py', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update generate_artwork_description
old_vlm_func = """def generate_artwork_description(api_key, image_url):
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
'''.strip()"""

new_vlm_func = """def generate_artwork_description(api_key, image_url):
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

if old_vlm_func in content:
    content = content.replace(old_vlm_func, new_vlm_func)
else:
    print('WARNING: old_vlm_func not found')

# 2. Update build_illustration_generation_prompt
old_build_func = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
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

new_build_func = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
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

if old_build_func in content:
    content = content.replace(old_build_func, new_build_func)
else:
    print('WARNING: old_build_func not found')

with open('D:/choice_product/app.py', 'w', encoding='utf-8') as f:
    f.write(content)

print('Update successful')
