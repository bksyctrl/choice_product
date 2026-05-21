# 商品选品审核平台开发文档

版本：V1  
日期：2026-05-18  
技术方向：Flask + Vue3 + Element Plus + MySQL/MariaDB

## 1. 项目目标

本系统是面向团队选品人员、运营人员和上架人员的商品筛选与审核后台。核心目标是让团队可以基于 FastMoss 与 Kalodata 数据快速筛选爆品、审核商品是否适合上架、管理商品状态，并批量导出待上架商品链接。

V1 重点解决以下问题：

- 快速筛选爆品。
- 对比统一表、FastMoss 聚合表、Kalodata 表中的商品数据。
- 管理商品审核状态。
- 支持 AI IP 风险标签与材质标签筛选。
- 导出待上架商品 TikTok 链接。

## 2. 用户角色

V1 暂不做登录和权限系统，默认使用对象为：

- 团队选品人员：负责筛选和判断商品。
- 运营/上架人员：负责查看待上架商品并导出链接。

V1 不记录操作人，但商品状态字段需要保留状态信息。`fastmoss_product_aggregate` 已存在 `audit_status`，优先沿用该字段。

## 3. 导航结构

采用左侧侧边栏导航。

一级导航：

- 商品

二级导航：

- 统一表
- fastmoss
- kalodata

数据源对应关系：

| 导航名称 | 数据表 | 说明 |
| --- | --- | --- |
| 统一表 | `fastmoss_product_aggregate` | 主要审核与上架商品池 |
| fastmoss | `fastmoss_product_rank_aggregate` | 三张 FastMoss 表聚合后的排名/商品数据 |
| kalodata | `kalodata_youwei_product` | Kalodata 商品数据 |

## 4. 页面规划

### 4.1 首页

首页采用简单导航页。左侧展示导航，主区域可展示系统说明和统计概览入口。

### 4.2 商品列表页

商品列表页支持表格视图和卡片视图切换。

默认建议使用表格视图，适合团队审核和高密度筛选；卡片视图用于快速浏览商品图片。

#### 4.2.1 列表展示字段

列表中需要展示以下字段：

| 字段 | 说明 |
| --- | --- |
| 商品图 | 商品主图 |
| 商品标题 | 商品名称/标题 |
| 价格 | 商品售价 |
| 评分 | 商品评分 |
| 佣金比例 | 商品佣金比例 |
| 销量 | 根据当前销售周期展示对应销量 |
| 成交额 | 根据当前销售周期展示对应成交额 |
| 销售环比 | 根据当前周期与上周期的 `rank_sold_count` 汇总计算 |
| 带货达人数 | 当前周期关联/带货达人数量 |
| 视频成交占比 | 视频渠道成交占比 |
| 商品卡成交占比 | 商品卡渠道成交占比 |
| 上架时间 | 商品上架时间 |
| IP 等级 | AI 识别的 IP 风险等级 |
| 材质标签 | AI 材质分析结果 |
| 当前状态 | 商品审核/上架状态 |
| TikTok 链接按钮 | 打开商品 TikTok/detail 链接 |
| 平台详情按钮 | 打开 FastMoss 或 Kalodata 详情页 |
| 站内详情按钮 | 打开本系统商品详情页 |

平台详情链接规则：

- FastMoss：`https://www.fastmoss.com/zh/e-commerce/detail/{product_id}`
- Kalodata：`https://www.kalodata.com/product/detail?id={product_id}`

#### 4.2.2 筛选区

左侧筛选区：

- 采集日期：筛选 `date_record`。
- 上架时间。
- 佣金比例。
- 达人数量。
- 商品卡比例。
- 运费。
- 总销量。
- 视频成交占比。
- 评分。
- 售价范围。
- 定价范围。
- 商品状态。

列表顶部筛选区：

- 销售周期：7 天、30 天、90 天、180 天、自定义。
- IP 等级范围：例如选择 B 到 S，则隐藏 C/D。
- 材质类型：工厂材质、非工厂材质、其他疑似材质。
- 工厂材质细分类：支持多选、全选、排除某些材质。

#### 4.2.3 日期与销售周期

系统需要同时支持两个时间概念：采集日期和销售周期。

采集日期用于筛选商品数据快照，对应数据库字段：

```text
date_record
```

例如用户选择 `2026-05-18`，则列表展示 `date_record = 2026-05-18` 的商品数据。

销售周期用于决定列表中的销量、成交额、成交占比等指标读取哪个周期的数据。

销售周期映射：

| 周期 | FastMoss/Kalodata 字段策略 |
| --- | --- |
| 7 天 | 读取 `overview_7d`、`distribution_7d` |
| 30 天 | 读取原 30 天字段；后续可统一扩展为 `overview_30d`、`distribution_30d` |
| 90 天 | 读取 `overview_90d`、`distribution_90d` |
| 180 天 | 读取 `overview_180d`、`distribution_180d` |

#### 4.2.4 销售环比

销售环比基于 `rank_sold_count` 计算。

`rank_sold_count` 表示当天的榜单销量。销售环比不是直接读取 `overview_*` 中的销量，而是根据用户选择的采集日期和销售周期，统计当前周期与上一个周期的 `rank_sold_count` 总和。

公式：

```text
销售环比 = ((当前周期 rank_sold_count 总和 / 上周期 rank_sold_count 总和 - 1) * 100%)
```

计算规则：

| 用户选择 | 当前周期 | 上周期 |
| --- | --- | --- |
| 单日 / 1 天 | 所选日期当天的 `rank_sold_count` | 前一天的 `rank_sold_count` |
| 近 7 天 | 所选日期往前 7 天内的 `rank_sold_count` 总和 | 再往前 7 天的 `rank_sold_count` 总和 |
| 近 30 天 | 所选日期往前 30 天内的 `rank_sold_count` 总和 | 再往前 30 天的 `rank_sold_count` 总和 |
| 近 90 天 | 所选日期往前 90 天内的 `rank_sold_count` 总和 | 再往前 90 天的 `rank_sold_count` 总和 |
| 近 180 天 | 所选日期往前 180 天内的 `rank_sold_count` 总和 | 再往前 180 天的 `rank_sold_count` 总和 |

示例：

如果用户选择采集日期 `2026-05-18`，并选择单日对比：

```text
当前周期 = 2026-05-18 的 rank_sold_count
上周期 = 2026-05-17 的 rank_sold_count
```

如果用户选择采集日期 `2026-05-18`，并选择近 7 天：

```text
当前周期 = 2026-05-12 ~ 2026-05-18 的 rank_sold_count 总和
上周期 = 2026-05-05 ~ 2026-05-11 的 rank_sold_count 总和
```

如果上周期 `rank_sold_count` 总和为空、缺失或为 0，则销售环比返回 `N/A`。

#### 4.2.5 排序

列表支持升序/降序排序：

- 销量。
- 销售环比。
- 上架时间。
- 达人数量。
- 佣金比例。
- 商品卡成交。
- 视频成交。

#### 4.2.6 状态管理

状态字段采用已有或新增字段：

- `fastmoss_product_aggregate`：沿用 `audit_status`。
- `fastmoss_product_rank_aggregate`：建议新增 `audit_status`。
- `kalodata_youwei_product`：建议新增 `audit_status`。

状态枚举：

| 状态值 | 中文含义 | 说明 |
| --- | --- | --- |
| `PENDING` | 待选中 | 默认状态，商品尚未进入明确上架流程 |
| `REVIEWING` | 待复核 | 商品初步可行，但需要再次确认 |
| `READY` | 待上架 | 已确认可上架，等待运营导出链接 |
| `PUBLISHED` | 已上架 | 商品已完成上架 |
| `OTHER` | 预留/其他 | 预留状态 |

前端展示可以显示中文，后端和数据库保存英文枚举。

修改状态时必须二次确认。例如用户选择“待上架”时弹窗提示：确认将该商品状态修改为待上架？

V1 不需要 `review_status_updated_at`。

#### 4.2.7 导出

导出对象：当前数据源中 `audit_status = 'READY'` 的商品。

导出内容：只导出 TikTok/detail 链接。

导出方式：

- 复制到剪贴板。
- 导出 Excel 文件。

## 5. 商品详情页

详情页采用统一模板，根据数据源显示不同字段组合。

### 5.1 顶部区域

顶部区域布局：

- 左侧：商品主图。
- 中间：标题、价格、评分、佣金、上架时间、TikTok 链接、平台详情链接。
- 右侧：当前状态、状态修改按钮、复制链接/导出相关操作。

### 5.2 Tab 区域

详情页分为以下 Tab：

- 数据概览。
- 成交分布。
- 达人/视频/直播。
- AI 分析。
- 原始字段。

### 5.3 数据概览

数据概览支持销售周期切换。切换 7 / 30 / 90 / 180 天后，详情页核心指标同步变化。

### 5.4 成交分布

成交分布支持图表切换：

- 饼图：展示视频、直播、商品卡占比。
- 条形图：展示各渠道成交额/销量。

### 5.5 AI 分析

AI 分析由后端定时任务自动执行，前端只展示分析结果。V1 不提供手动触发 AI 分析按钮。

展示内容：

- IP 等级。
- IP 分析原因。
- IP 命中标签。
- 材质大类。
- 材质细分类。
- 材质分析原因。
- 材质置信度。
- AI 分析失败信息。

### 5.6 原始字段

原始字段区域用于排查数据问题。它展示当前商品数据库记录中的原始字段和值。

建议形式：

- 字段名 + 字段值表格。
- JSON 查看器。
- 支持复制字段值。

该区域不是选品人员的主要工作区，但对排查数据异常很有帮助。

## 6. AI 分析设计

### 6.1 分析范围

AI 分析覆盖三张商品表：

- `fastmoss_product_aggregate`
- `fastmoss_product_rank_aggregate`
- `kalodata_youwei_product`

后端采用守护线程方式自动扫描未分析、分析失败或需要重跑的数据，并将分析结果写回数据库。AI 分析拆分为两个独立任务：

- IP 分析任务。
- 材质分析任务。

| 守护线程 | 职责 | 写回字段 |
| --- | --- | --- |
| IP AI 分析守护线程 | 持续扫描三张商品表中 IP 分析未完成或需要重试的商品，读取商品主图 base64、标题、卖点，结合 IP 规则库、IP 成功经验、IP 失败经验，调用 AI 判断商品 IP 风险等级、原因和命中标签 | `ip_grade`、`ip_reason`、`ip_tags`、`ai_analysis_error` |
| 材质 AI 分析守护线程 | 持续扫描三张商品表中材质分析未完成或需要重试的商品，读取商品主图 base64、标题、卖点，结合材质规则库、材质成功经验、材质失败经验，调用 AI 判断材质类型、材质细分类、原因、置信度和命中规则 | `material_analysis`、`ai_analysis_error` |

商品 AI 分析总流程：

```text
材质初步筛选
  ↓
命中明确非工厂材质：写入 material_analysis，跳过 IP 分析和材质 AI 分析
  ↓
未命中：进入 IP 分析
  ↓
进入材质 AI 分析
  ↓
写入 ai_analysis_log，并按置信度沉淀经验
```

守护线程要求：

- IP 分析和材质分析互相独立，避免一个任务阻塞另一个任务。
- 每个守护线程按固定间隔轮询数据库，例如每 5 到 10 分钟扫描一批待分析商品。
- 每次只领取有限数量商品，避免一次性扫描全表造成数据库压力。
- 分析成功后立即写回对应字段，并清空 `ai_analysis_error` 中对应模块的错误。
- 分析失败时只写入 `ai_analysis_error` 中对应模块的错误，不覆盖该商品已有的成功分析结果。
- 后续再次轮询时，可以对失败记录进行重试。
- 守护线程应具备异常捕获能力，单条商品分析失败不能导致整个线程退出。
- 系统启动时自动启动两个守护线程；系统关闭时应尽量安全停止。

### 6.2 AI 输入规则

AI 分析只读取以下商品字段：

| 来源 | 商品 ID | 主图 base64 | 标题 | 卖点 | 日期 |
| --- | --- | --- | --- | --- | --- |
| `fastmoss_product_aggregate` | `product_id` | `image_base64` | `title` | `selling_points` | `date_record` |
| `fastmoss_product_rank_aggregate` | `product_id` | `image_base64` | `title` | 暂无则传空 | `date_record` |
| `kalodata_youwei_product` | `商品ID` | `商品主图` | `商品标题` | `卖点` | `date_record` |

V1 不默认加入类目、属性、价格、店铺等其他字段。后续如果发现必要，再单独扩展 Prompt 输入。

材质分析允许增加确定性预筛选字段：

- 材质初步筛选是商品 AI 分析的第一步，必须先于 IP 分析执行。
- 材质初步筛选可以额外读取商品标题、属性信息、卖点中的文本关键词，用于做非工厂材质的确定性剔除。
- 该预筛选用于识别明显不是目标手机壳/工厂材质范围的商品。
- 命中明确排除关键词时，不调用 IP AI，也不调用材质 AI，直接写入 `material_analysis`，将 `material_type` 标记为 `非工厂材质`。
- 预筛选命中后仍需写入 `cp_ai_analysis_log`，记录命中的关键词、来源字段和跳过 AI 的原因。
- 预筛选不确定时，不做硬判定，继续进入 IP 分析，再进入材质规则库 + 成功经验 + 失败经验 + AI 判断流程。

非工厂材质预筛选关键词示例：

| 类别 | 关键词示例 | 建议处理 |
| --- | --- | --- |
| 屏幕保护类 | 钢化膜、手机屏幕膜、屏幕膜、保护膜、glass screen protector、tempered glass | 标记为非工厂材质，跳过材质 AI |
| 非手机壳保护对象 | 耳机壳、airpods case、earbuds case、平板膜、手表膜 | 标记为非工厂材质，跳过材质 AI |
| 贴纸贴膜类 | 贴纸、手机贴纸、镜头膜、背膜、skin sticker、phone sticker | 标记为非工厂材质，跳过材质 AI |
| 支架配件类 | 手机支架、支架、stand、holder、grip、pop socket | 标记为非工厂材质，跳过材质 AI |
| 其他配件类 | 挂绳、挂扣、镜头盖、镜头保护圈、数据线、充电器 | 标记为非工厂材质或配件类，按后续规则细分 |

### 6.3 IP 分析字段

| 字段 | 类型建议 | 备注 |
| --- | --- | --- |
| `ip_grade` | `varchar(10)` | AI识别的IP风险等级，取值为S/A/B/C/D/E；S=奢侈品大牌，A=美国本地IP品牌，B=非美国本地动漫IP，C=非美国本地潮牌品牌，D=擦边插画/不知名IP延伸扭曲设计，E=完全无风险 |
| `ip_reason` | `text` | AI识别IP风险等级的判断原因与证据。原因和证据合并保存，不再拆成两个字段 |
| `ip_tags` | `longtext` | AI识别命中的IP标签JSON字符串，例如命中规则、品牌名、角色名、IP类别、风险关键词、匹配度等 |

IP 等级标准：

| 等级 | 含义 | 示例 |
| --- | --- | --- |
| S | 奢侈品大牌 | 古驰、LV、克罗心、爱马仕 |
| A | 美国本地 IP 品牌 | 米老鼠、贝兹娃娃、飞天小女警 |
| B | 非美国本地动漫 IP | 火影忍者、咒术回战、JOJO 的奇妙冒险 |
| C | 非美国本地潮牌品牌 | BAPE 猿人头、红牛 |
| D | 擦边插画/不知名 IP 延伸扭曲设计 | AI 判断插画并非直接照搬明确 IP 元素，但属于对不知名 IP 或疑似 IP 风格做延伸、扭曲、再设计，存在擦边风险 |
| E | 完全无风险 | 无明显 IP、品牌、角色、潮牌或擦边插画风险 |

IP 分析固定回执 JSON 建议：

```json
{
  "ip_grade": "A",
  "ip_type": "美国本地IP品牌",
  "matched_rules": ["IP_RULE_001"],
  "reason": "判断原因与证据合并写在这里。",
  "match_score": 92
}
```

写回规则：

- `ip_grade` 写入 JSON 中的 `ip_grade`。
- `ip_reason` 写入 JSON 中的 `reason`。
- `ip_tags` 写入完整 JSON 或命中标签 JSON。
- `match_score` 用于经验沉淀判断，不单独拆商品表字段。

### 6.4 材质分析字段

材质分析结果统一存入一个 JSON 字段。

| 字段 | 类型建议 | 备注 |
| --- | --- | --- |
| `material_analysis` | `longtext` | AI材质分析结果JSON字符串，包含材质类型、材质细分类、分析原因、置信度、命中的知识库标签等 |

示例：

```json
{
  "material_type": "工厂材质",
  "material_category": "硅胶",
  "material_reason": "商品标题和卖点中出现 silicone phone case，与工厂材质库中的硅胶手机壳特征匹配。",
  "confidence": 93,
  "matched_tags": ["silicone", "phone case"]
}
```

分类规则暂定：

- 在调用 AI 前，先执行非工厂材质关键词预筛选；命中明确排除关键词时，直接归入 `非工厂材质`，不消耗 AI 调用。
- 当 AI 置信度大于等于 90 时，归入明确材质标签。
- 当 AI 置信度低于 90 时，归入疑似材质。
- 无法判断时归入其他疑似材质。

材质分析固定回执 JSON 建议：

```json
{
  "material_type": "工厂材质",
  "material_category": "TPU",
  "material_reason": "判断原因与证据合并写在这里。",
  "confidence": 88,
  "matched_tags": ["TPU", "phone case"],
  "matched_rules": ["MAT_RULE_001"],
  "pre_filter": {
    "hit": false,
    "matched_keywords": [],
    "source_fields": []
  }
}
```

非工厂材质预筛选命中时的固定回执 JSON 示例：

```json
{
  "material_type": "非工厂材质",
  "material_category": "屏幕保护类",
  "material_reason": "商品标题或属性中命中“钢化膜/手机屏幕膜”等明确非手机壳材质关键词，因此跳过材质AI判断，直接归入非工厂材质。",
  "confidence": 100,
  "matched_tags": ["钢化膜", "手机屏幕膜"],
  "matched_rules": [],
  "pre_filter": {
    "hit": true,
    "matched_keywords": ["钢化膜"],
    "source_fields": ["title", "attributes"]
  }
}
```

写回规则：

- `material_analysis` 写入完整 JSON。
- `confidence` 用于经验沉淀判断，不单独拆商品表字段。

### 6.5 AI 错误字段

| 字段 | 类型建议 | 备注 |
| --- | --- | --- |
| `ai_analysis_error` | `longtext` | AI分析失败信息JSON字符串，用于分别记录 IP 分析或材质分析失败原因、最后失败时间、重试次数和原始错误信息 |

示例：

```json
{
  "ip": null,
  "material": {
    "status": "FAILED",
    "reason": "MiniMax返回不是合法JSON",
    "last_failed_at": "2026-05-19 18:45:00",
    "retryable": true,
    "attempt_count": 1,
    "raw_error": "Expecting value: line 1 column 1"
  }
}
```

失败处理规则：

- IP 成功：写 `ip_grade`、`ip_reason`、`ip_tags`，并清空 `ai_analysis_error.ip`。
- IP 失败：不清空、不覆盖已有 IP 成功结果，只写 `ai_analysis_error.ip`。
- 材质成功：写 `material_analysis`，并清空 `ai_analysis_error.material`。
- 材质失败：不清空、不覆盖已有材质成功结果，只写 `ai_analysis_error.material`。
- IP 和材质都成功后，`ai_analysis_error` 可以置为 `NULL`。

### 6.6 规则库、经验库与 Prompt 组装

V1 的 AI 判断逻辑以数据库为中心，Excel 只作为规则数据来源，不作为线上运行时的直接读取对象。

规则库表：

- `cp_ai_ip_rule`：选品 AI 分析 IP 规则表。
- `cp_ai_material_rule`：选品 AI 分析材质规则表。

规则导入原则：

- IP 规则与材质规则先从 Excel 导入到数据库，线上商品分析只读取数据库，不直接读取 Excel。
- 规则表不保存图片、不保存 base64，只保存可给 AI 使用的文字化规则说明。
- Excel 中的规则图片只在导入阶段临时使用，先进入本地图片识别模块生成自然语言视觉描述，再结合 Excel 标注生成 `image_description`、`ocr_text`、`visual_features`、`rule_reason` 等文字字段。
- 本地图片识别模块当前使用 `HuggingFaceTB/SmolVLM2-256M-Video-Instruct`：用 PIL 打开 Excel 内嵌图片，经 `transformers` 的 `AutoProcessor` 和 `AutoModelForImageTextToText` 生成视觉描述。
- 图片识别模块只负责把规则样本图片转成文字，不直接决定最终商品是否侵权、是否属于某种材质；最终判断仍由商品分析 Prompt 结合数据库规则、成功经验、失败经验和商品主图完成。
- IP 规则说明必须写清楚“为什么别人一眼能看出来是这个 IP”：品牌文字、Logo/视觉风格、角色形象、标志性轮廓、影视/动漫/专辑画面、图形商标、老花纹样、配色组合和拼贴风格。
- 如果是多角色动漫/影视 IP，规则说明要拆到具体角色。例如火影忍者要写鸣人、小樱、佐助、卡卡西等角色的发型、服装、姿态、道具和标志性记号；海贼王要写路飞、索隆、山治等角色及草帽、三刀流、骷髅旗等线索。
- 如果是 K-pop/影视动画类 IP，不能只写“女团/人物插画”，要写清楚作品来源、团名、角色名、官方周边图案、舞台站位、配色和构图。例如 Netflix《K-Pop Demon Hunters》要记录 HUNTR/X、HUNTRXX、Huntrix、Zoey、Rumi、Mira 等可识别线索。
- 材质规则说明必须描述图片“长什么样”：材质外观、透明度、软硬质感、边框包覆方式、镜头区域结构、表面纹理、颜色表现、磁吸/挂绳/贴件等功能结构，以及是否属于工厂可稳定生产的标准款式。

经验与日志表：

- `cp_ai_analysis_log`：每次 IP 或材质分析都写入日志。
- `cp_ai_analysis_experience`：按匹配度自动沉淀成功经验和失败经验。

IP 分析 Prompt 输入：

- 某条商品的主图 base64。
- 某条商品的标题。
- 某条商品的卖点，没有则传空。
- `cp_ai_ip_rule` 中启用状态的全部规则字段数据。
- `cp_ai_analysis_experience` 中 `analysis_type = 'IP'` 的成功经验和失败经验。

材质分析 Prompt 输入：

- 某条商品的主图 base64。
- 某条商品的标题。
- 某条商品的卖点，没有则传空。
- `cp_ai_material_rule` 中启用状态的全部规则字段数据。
- `cp_ai_analysis_experience` 中 `analysis_type = 'MATERIAL'` 的成功经验和失败经验。

经验沉淀规则：

- 每次 AI 分析都写入 `cp_ai_analysis_log`。
- 如果分析结果匹配度/置信度大于等于 85，则沉淀为典型成功经验，写入 `cp_ai_analysis_experience`。
- 如果分析结果匹配度/置信度小于等于 15，则沉淀为典型失败经验，写入 `cp_ai_analysis_experience`。
- 匹配度在 15 到 85 之间的记录只保留分析日志，不进入成功/失败经验库。
- V1 不做人工审查流程。

实现要求：

- 不在商品分析前单独做商品图片预识别。
- 不在商品分析流程中临时读取 Excel 或重新识别规则图片；规则图片只允许在离线规则导入阶段转成文字规则。
- 由后端读取数据库规则、经验和商品字段，组装固定 Prompt 后调用 MiniMax-M2.7。
- AI 返回必须是固定 JSON，后端校验通过后才能写回商品表。

## 7. 后端接口设计

### 7.1 商品列表接口

统一接口：

```http
GET /api/products
```

核心参数：

| 参数 | 说明 |
| --- | --- |
| `source` | `unified`、`fastmoss`、`kalodata` |
| `page` | 页码 |
| `page_size` | 每页数量，支持10、30、50、100 |
| `date_start` | 采集日期开始 |
| `date_end` | 采集日期结束 |
| `period` | 销售周期，支持7d、30d、90d、180d |
| `audit_status` | 商品审核状态 |
| `ip_grade_min` | IP 等级范围起点 |
| `ip_grade_max` | IP 等级范围终点 |
| `material_type` | 材质类型 |
| `material_categories` | 材质细分类，多选 |
| `exclude_material_categories` | 排除的材质细分类 |
| `sort_by` | 排序字段 |
| `sort_order` | `asc` 或 `desc` |

后端针对不同 `source` 使用不同 SQL 查询，但返回给前端时统一字段结构。

### 7.2 商品详情接口

```http
GET /api/products/{source}/{product_id}
```

建议支持：

```http
GET /api/products/{source}/{product_id}?date_record=2026-05-18
```

### 7.3 修改状态接口

```http
PATCH /api/products/{source}/{product_id}/status
```

请求体：

```json
{
  "date_record": "2026-05-18",
  "audit_status": "READY"
}
```

### 7.4 复制待上架链接接口

```http
GET /api/products/{source}/ready-links
```

返回 `audit_status = 'READY'` 的商品 TikTok/detail 链接数组。

### 7.5 导出待上架链接接口

```http
GET /api/products/{source}/export-ready-links
```

返回 Excel 文件，只包含待上架商品链接。

### 7.6 统计概览接口

```http
GET /api/products/stats?source=unified
```

统计内容：

- 总商品数。
- 待选中数量。
- 待复核数量。
- 待上架数量。
- 已上架数量。
- S/A/B/C/D/E 各等级数量。
- 工厂材质数量。
- 非工厂材质数量。
- 疑似材质数量。

## 8. 数据库字段设计与 SQL

### 8.1 状态字段

`fastmoss_product_aggregate` 已存在：

```sql
`audit_status` varchar(20) DEFAULT 'PENDING' COMMENT '审核状态：PENDING=待选中, REVIEWING=待复核, READY=待上架, PUBLISHED=已上架, OTHER=预留/其他'
```

`fastmoss_product_rank_aggregate` 和 `kalodata_youwei_product` 建议新增：

```sql
ALTER TABLE `fastmoss_product_rank_aggregate`
  ADD COLUMN `audit_status` varchar(20) DEFAULT 'PENDING'
  COMMENT '审核状态：PENDING=待选中, REVIEWING=待复核, READY=待上架, PUBLISHED=已上架, OTHER=预留/其他';

ALTER TABLE `kalodata_youwei_product`
  ADD COLUMN `audit_status` varchar(20) DEFAULT 'PENDING'
  COMMENT '审核状态：PENDING=待选中, REVIEWING=待复核, READY=待上架, PUBLISHED=已上架, OTHER=预留/其他';
```

## 9. V1 范围

V1 开发内容：

- 左侧导航。
- 三个商品列表页。
- 表格/卡片视图切换。
- 筛选与排序。
- 商品状态修改与二次确认。
- 待上架链接复制。
- 待上架链接 Excel 导出。
- 商品详情页。
- 统计概览。
- AI 标签展示与筛选。

V1 暂不开发：

- 登录权限。
- 操作人记录。
- 知识库管理后台。
- 手动触发 AI 分析。
- 去重分析。

## 10. 推荐开发顺序

1. 创建 `cp_ai_ip_rule`、`cp_ai_material_rule`、`cp_ai_analysis_log`、`cp_ai_analysis_experience` 四张 AI 支撑表。
2. 导入或维护 IP 规则与材质规则数据。
3. 编写单商品 MiniMax-M2.7 分析测试脚本，先跑通 IP 分析，再跑通材质分析。
4. 完成 AI 分析日志写入与成功/失败经验沉淀。
5. Flask 项目骨架与数据库连接。
6. 商品列表统一接口。
7. 统计概览接口。
8. 状态修改接口。
9. 待上架链接复制与 Excel 导出接口。
10. 前端左侧导航和商品列表页。
11. AI 标签展示与筛选。
12. 商品详情页。
13. 后端 AI 定时分析任务。
