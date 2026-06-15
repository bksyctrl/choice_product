import sys

with open('D:/choice_product/app.py', 'r', encoding='utf-8') as f:
    content = f.read()

old_build = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
    feedback_text = stringify(retry_feedback).strip()
    feedback_part = f"\\nPrevious failed because: {feedback_text[:260]}. Fix this exactly in this attempt." if feedback_text else ""
    
    if artwork_description:
        prompt = artwork_description
    else:
        prompt = "A detailed, high-resolution photo of a flat, seamless textile pattern swatch, precisely extracted from the printed design on the product in the reference image. The output must be a single, flat, repeating tile asset filling the entire square canvas. The texture is that of a scanned fabric sample, completely smooth and devoid of any three-dimensional product contours, creases, hardware, or the product shape itself. Do not generate a new product, model, or scene."
    
    prompt += feedback_part
    return prompt[:1450]"""

new_build = """def build_illustration_generation_prompt(artwork_description="", retry_feedback=""):
    feedback_text = stringify(retry_feedback).strip()
    feedback_part = f"\\nPrevious failed because: {feedback_text[:260]}. Fix this exactly in this attempt." if feedback_text else ""
    
    if artwork_description:
        prompt = artwork_description
    else:
        # 这是一个兜底的极简提示词，防止由于某种原因视觉模型没返回内容
        prompt = "A detailed, high-resolution photo of a flat, seamless textile pattern swatch, precisely extracted from the printed design on the product in the reference image. The output must be a single, flat, repeating tile asset filling the entire square canvas. The texture is that of a scanned fabric sample, completely smooth and devoid of any three-dimensional product contours, creases, hardware, or the product shape itself. Do not generate a new product, model, or scene."
    
    # 强制加上基础铁律，防止视觉模型生成的提示词遗漏了“扁平化”和“去产品特征”的要求
    base_rules = "\\nCRITICAL INSTRUCTIONS: The output MUST be a flat 2D asset. It must be completely devoid of 3D contours, creases, hardware, handles, straps, models, shadows, and watermarks. Do NOT generate the product shape (e.g. do not generate a bag or a shirt)."
    
    if base_rules not in prompt:
        prompt += base_rules
        
    prompt += feedback_part
    return prompt[:1450]"""

if old_build in content:
    content = content.replace(old_build, new_build)
    with open('D:/choice_product/app.py', 'w', encoding='utf-8') as f:
        f.write(content)
    print('fixed build function')
else:
    print('could not find build function to patch')
