import re

with open('static/index.html', 'r', encoding='utf-8') as f:
    html = f.read()

script_content = re.search(r'<script>(.*?)</script>', html, re.DOTALL)
if script_content:
    with open('test_script.js', 'w', encoding='utf-8') as out:
        out.write(script_content.group(1))
    print("Script extracted.")
else:
    print("No script found.")
