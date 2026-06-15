# 插画提取自动优化工作流 (Illustration Extraction Auto-Loop)

这个文档展示了 `/api/products/pod_cross_category/<product_id>/extract` 接口在加入“AI视觉自动打分与重试”机制后的前后端及大模型交互流程。

## 核心流程图
1. **[生成]** 调用 MiniMax `image-01` (图生图)，提取插画。
2. **[评估]** 调用 MiniMax 多模态视觉模型 (如 `abab6.5s-chat`)，将【原图】与【生成的插画】同时发给它进行对比打分。
3. **[决策]** 解析视觉模型的 JSON：
   - 如果 `score >= 85`：通过！直接返回最终结果。
   - 如果 `score < 85`：失败！将 `mismatch_reason` 或 `optimization_suggestion` 追加到下一轮图生图的提示词中，回到第 1 步（设定最大重试次数，比如 3 次）。

---

## 阶段一：初次提取 (图生图)

**大模型接口**: `POST https://api.minimax.io/v1/image_generation`

### 1. 入参 (Request Body)
```json
{
  "model": "image-01",
  "prompt": "Extract the existing artwork/pattern printed on the product surface... (此处为固定基础提示词)",
  "aspect_ratio": "1:1",
  "response_format": "url",
  "subject_reference": [
    {
      "type": "character",
      "image_file": "data:image/jpeg;base64,原商品图片的Base64"
    }
  ]
}
```

### 2. 返回 (Response Body)
```json
{
  "base_resp": { "status_code": 0, "status_msg": "success" },
  "data": {
    "image_urls": [
      "https://cdn.minimax.io/generated_illustration_v1.jpg"
    ]
  }
}
```

---

## 阶段二：AI视觉对比评估 (Vision VQA)

获取到刚才生成的插画图片后，我们需要用带有视觉能力的大模型（VLM）来扮演“质量质检员（QA）”。

**大模型接口**: `POST https://api.minimax.io/v1/text/chatcompletion_v2` (必须使用支持识图的模型)

### 1. 入参 (Request Body)
我们要强制模型输出 JSON 格式（`response_format: {"type": "json_object"}`），以便于代码直接提取分数。

```json
{
  "model": "abab6.5s-chat", 
  "response_format": { "type": "json_object" },
  "messages": [
    {
      "role": "system",
      "content": "你是一个严格的设计素材质检专家。你的任务是对比【商品原图】和【提取出的平面插画】，评估插画是否完美还原了商品上的印刷图案。请以JSON格式输出：\n{\n  \"is_match\": boolean,\n  \"score\": integer (0-100的匹配度),\n  \"mismatch_reason\": string (如果不一致，原因是什么，精确指出颜色、丢失的文字、多余的背景等),\n  \"optimization_suggestion\": string (一句话建议，如何修改提示词来修复这个问题)\n}"
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Image A 是带有印花的实物商品原图。Image B 是 AI 提取出的插画设计稿。请评估 Image B 是否完美还原了 Image A 上的印刷图案内容（忽略商品实物背景和形变）。"
        },
        {
          "type": "image_url",
          "image_url": { "url": "data:image/jpeg;base64,原商品图片的Base64" }
        },
        {
          "type": "image_url",
          "image_url": { "url": "https://cdn.minimax.io/generated_illustration_v1.jpg (刚才生成的插画图)" }
        }
      ]
    }
  ]
}
```

### 2. 返回 (Response Body) - 场景 A：不合格 (< 85分)
```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{\n  \"is_match\": false,\n  \"score\": 65,\n  \"mismatch_reason\": \"图案主体提取正确，但是把商品拉链的阴影和一部分布料纹理也一起生成进去了，且颜色比原图偏暗。\",\n  \"optimization_suggestion\": \"Remove the zipper and fabric texture completely. Ensure the background is pure white and brighten the colors to match the vibrant source.\"\n}"
      }
    }
  ]
}
```
*💡 后续动作：将 `optimization_suggestion` 拼接到新的图生图 prompt 中，发起第二次提取。*

### 3. 返回 (Response Body) - 场景 B：合格 (>= 85分)
```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{\n  \"is_match\": true,\n  \"score\": 92,\n  \"mismatch_reason\": \"\",\n  \"optimization_suggestion\": \"\"\n}"
      }
    }
  ]
}
```
*💡 后续动作：流程结束，将这张图保存并返回给前端 `result_image_url`。*

---

## 阶段三：重试提取 (第二次图生图)

如果阶段二的分数不到 85 分，我们根据 AI 给出的优化建议，组装新的入参，再次调用图生图。

### 1. 入参 (Request Body)
```json
{
  "model": "image-01",
  "prompt": "Extract the existing artwork/pattern printed on the product surface... (固定提示词)\n\nPrevious attempt failed. FIX THIS EXACTLY: Remove the zipper and fabric texture completely. Ensure the background is pure white and brighten the colors to match the vibrant source.",
  "aspect_ratio": "1:1",
  "subject_reference": [ ... ]
}
```

如此循环，直到分数达到 85，或者达到最大重试次数（防止死循环和过度消耗 API 余额）。