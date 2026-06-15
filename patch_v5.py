import sys
import re

with open('D:/choice_product/app.py', 'r', encoding='utf-8') as f:
    content = f.read()

def patch_func(func_name, content):
    start = content.find(func_name)
    if start == -1: return content
    end = content.find('def ', start + 10)
    if end == -1: end = len(content)
    
    block = content[start:end]
    block = block.replace('"model": MINIMAX_MODEL', '"model": "abab6.5s-chat"')
    return content[:start] + block + content[end:]

content = patch_func('def analyze_illustration_extractability', content)
content = patch_func('def generate_artwork_description', content)
content = patch_func('def evaluate_generated_illustration', content)

with open('D:/choice_product/app.py', 'w', encoding='utf-8') as f:
    f.write(content)

print("Patch applied successfully.")
