img1= 'https://s.500fd.com/tt_product/3924223b022048be8175e1dcc0ab6a7f~tplv-dx0w9n1ysr-crop-jpeg:200:200.jpeg'





from openai import OpenAI

client = OpenAI(
    base_url="https://api.minimax.io/v1",
    api_key="sk-cp-9hCfHirB-HE_Fhe2sk7OVc97UKoQXwrREGlPoRGarWJm2f8_SSf-KBelZlsNbSkqkxT2YNT0y3KtVWDaa7nQnkhgE01gq_4o1SZ-GvH8Revt-O-8OkYHcdg"  # ⚠️ 记得把这里换成你真正申请到的 mm- 开头的长字符串
)

print("Starting stream response...\n")
print("=" * 60)
print("Thinking Process:")
print("=" * 60)

stream = client.chat.completions.create(
    model="MiniMax-M2.7",
    messages=[
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content":
            [# 1. 文本问题部分
                {
                    "type": "text",
                    "text": " 请问这张图片里画的是什么？描述一下它的细节。"
                },
                # 2. 图片部分（通过这个结构，SDK会自动将图片转化为模型能识别的视觉输入）
                {
                    "type": "image_url",
                    "image_url": {
                        "url": "https://s.500fd.com/tt_product/3924223b022048be8175e1dcc0ab6a7f~tplv-dx0w9n1ysr-crop-jpeg:200:200.jpeg"
                    }
                }]
         },
    ],
    # Set reasoning_split=True to separate thinking content into reasoning_details field
    extra_body={"reasoning_split": True},
    stream=True,
)

reasoning_buffer = ""
text_buffer = ""

for chunk in stream:
    if (
        hasattr(chunk.choices[0].delta, "reasoning_details")
        and chunk.choices[0].delta.reasoning_details
    ):
        for detail in chunk.choices[0].delta.reasoning_details:
            if "text" in detail:
                reasoning_text = detail["text"]
                new_reasoning = reasoning_text[len(reasoning_buffer) :]
                if new_reasoning:
                    print(new_reasoning, end="", flush=True)
                    reasoning_buffer = reasoning_text

    if chunk.choices[0].delta.content:
        content_text = chunk.choices[0].delta.content
        new_text = content_text[len(text_buffer) :] if text_buffer else content_text
        if new_text:
            print(new_text, end="", flush=True)
            text_buffer = content_text

print("\n" + "=" * 60)
print("Response Content:")
print("=" * 60)
print(f"{text_buffer}\n")