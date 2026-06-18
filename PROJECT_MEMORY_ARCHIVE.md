# 选品审核系统项目记忆存档

更新时间：2026-06-11  
维护目的：记录当前网站的文件职责、核心函数、功能边界、数据库影响和每次修改记录。后续任何代码修改都必须同步更新本文档，避免再次出现“改过什么没人记得”“误删旧功能”“失败后无法回滚思路”的问题。

## 维护规则

1. 每次修改代码后，必须在本文档的“变更日志”追加一条记录。
2. 记录必须包含：修改时间、修改目标、涉及文件、具体改了什么、验证方式、风险边界、是否需要重启服务、是否需要回算数据。
3. 不允许只写“已完成”。必须写清楚用户刷新后在哪里能看到效果。
4. 若修改失败，也必须记录失败原因和回滚方式。
5. 修改列表页、筛选、排序、去重、详情页、AI分析、专家团队、数据库字段时，必须明确说明有没有影响其他功能。
6. 任何新增字段，都要同步记录字段来源、用途、前端展示位置、是否需要全量回算。
7. 任何临时文件、测试文件、脚本输出，都要记录是否已清理。

## 当前运行形态

- 主站仍以 Flask `app.py` 提供页面和商品接口，前端为 `static/index.html` + `static/styles.css`。
- 登录页为独立 `static/login.html`，不是放在首页。
- Java `agent-center` 是专家团队/Agent 中台方向，端口通常为 `5010`，但商品列表与站内详情仍走 Flask `5000`。
- 数据库连接配置在 `app.py` / 环境变量中读取，生产表主要为 FastMoss、Kalodata、统一表、POD跨品类表、用户/专家团队相关表。

## 主要文件职责

| 文件/目录 | 职责 | 注意事项 |
|---|---|---|
| `app.py` | Flask 主后端；登录注册；商品列表；详情；筛选排序；图片代理；AI粗略分析；深度分析；专家团队桥接；选品分回算；POD跨品类基础能力 | 这是最容易牵一发动全身的文件。修改前必须先定位路由、筛选、排序、归一化三个链路 |
| `static/index.html` | 主前端单页应用；导航；商品列表/卡片；筛选器；分页；站内详情；IP/材质展示；深度分析；专家团队 UI；选品分明细 | 修改 DOM 字符串时要做 JS 语法检查；不要破坏去重、筛选参数、分页状态 |
| `static/styles.css` | 全站样式；表格、弹窗、详情页、专家团队、POD、选品分等样式 | 新增样式尽量使用独立 class，避免覆盖旧组件 |
| `static/login.html` | 登录/注册页面 | 登录接口依赖 `/api/auth/login`、`/api/auth/register` |
| `tools/analyze_single_product.py` | 单商品 IP/材质分析、规则库/经验库/AI调用逻辑 | 自动粗略分析依赖它；谨慎修改预筛、复用、写库逻辑 |
| `tools/analyze_batch_products.py` | 批量分析脚本入口 | 用于离线批量分析 |
| `tools/run_stepwise_product_analysis.py` | 分步分析调试脚本 | 适合排查单品分析过程 |
| `tools/import_rule_workbooks.py` | 规则表导入 | 修改规则库前先备份 |
| `tools/import_cp_rule_images.py` | IP规则图片导入/处理 | 涉及图片规则和数据库结构 |
| `agent-center/` | Java Agent 中台；专家团队、工作流、记忆、意图分发、技能/工具方向 | 与 Flask 专家团队桥接并存，修改时要确认前端调用的是 Flask 还是 Java |
| `agent-center/memory/` | 专家团队长期记忆文档 | 可记录用户偏好、系统定位、学习结果 |
| `product_selection_platform.md` | 选品平台规划文档 | 产品方向参考 |
| `private_product_library_plan.md` | 私域选品库规划 | 产品方向参考 |
| `1.1.2版本_详细开发文档.md` | 1.1.2版本开发规划 | 需求开发参考，不等同于已实现代码 |

## 当前核心功能地图

### 1. 用户与权限

- 登录、注册、退出。
- `role` 字段控制权限。
- 专家团队入口应只对 `admin`、`manager` 开放。
- 登录成功后进入主页面；未登录跳登录页。

### 2. 商品数据列表

支持数据源：

- `unified`：统一表。
- `fastmoss`：FastMoss聚合表。
- `kalodata`：Kalodata商品表。
- `pod_cross_category`：跨品类/POD热品方向。

列表能力：

- 默认按最新采集日期展示。
- 支持采集日期筛选：昨日、过去7天、过去30天、过去90天、过去180天、自定义。
- 支持关键词、IP起点/终点、材质类型、材质细分、排除材质、状态、排序等筛选。
- 商品列表需要保持“同商品不同日期”去重策略，优先展示最新日期。
- 当前选品分列可点击“明细”查看评分拆解。

### 3. 排序与指标

主要排序字段：

- 选品分：`selection_score`
- 商品卡销量：按采集日期范围内商品卡销量/榜单销量相关字段计算。
- 近7天销量占比、近28天销量占比：由 distribution JSON 归一化。
- 总销量。
- 销售环比。
- 商品卡占比。
- 上架时间。
- 评分。
- IP等级。

注意：

- 前端展示字段不一定等于数据库字段名。
- `product_card_sales` 是前端排序语义，不一定是数据库真实字段。
- Kalodata表中实际字段包含 `rank_sold_count`、`all_solds`、`总销量` 等，需要通过后端映射。

### 4. 站内详情

站内详情包含：

- 商品图、标题、商品ID、采集日期、平台按钮。
- 价格、评分、佣金、热度、人气、总销量、GMV、达人、视频、直播、上架时间等指标。
- 属性信息与卖点。
- 卖点“翻译成中文”按钮，展示时会去除 `<think>...</think>`。
- 数据总览：近7天/近30天切换，成交渠道可条形图/饼图切换。
- SKU分析：近7天/近28天，按总销量展示条形图/饼图。
- IP分析：先展示轻量摘要，点击详细分析后触发/读取深度分析。
- 材质分析：轻量摘要 + 详细分析。
- 选品分面板：显示选品分、销售环比、插画状态，并可展开“查看评分明细”。

### 5. AI粗略分析与深度分析

粗略分析：

- 后台自动处理商品的 IP 等级和材质分析。
- 目标是提高覆盖率，降低每条商品的AI阅读成本。
- 注意：材质预筛不应阻断 IP 粗略分析。

深度分析：

- 用户点击“详细分析”后创建记录。
- 保存用户、商品表、商品ID、采集日期、分析类型、状态、结果。
- 再次点击同一商品同一分析类型时，应优先读取已有记录，避免重复请求。

### 6. 选品分

字段：

- `selection_score`：综合选品分。
- `score_reason`：评分原因。新逻辑保存 JSON，用于前端明细拆解；旧数据可能是普通文本。
- `sales_mom`：销售环比。
- `illustration_status`：插画/素材提取状态。
- `illustration_result_url`：插画结果地址。

评分规则：

- 综合得分 = 销量分 + 评分分 + 热度分 + 趋势分，满分100。
- 销量分满分35：基于30天销量 log10 归一化，并使用中位最优惩罚函数。
- 评分分满分25：评分/5，缺失按3.5。
- 热度分满分25：基于视频数 log10 归一化，并使用同样惩罚函数。
- 趋势分满分15：7天归一化销量 / 30天归一化销量。
- 风控直接0分：评分 < 3.0；价格 < $1 或 > $100；30天销量=0但总销量>0。

前端展示：

- 列表“选品分”旁有“明细”按钮。
- 站内详情有“查看评分明细”折叠区。
- 如果 `score_reason` 是旧文本，前端展示总结；如果是 JSON，展示四个维度拆解、输入值、类目基准、归一化值。

### 7. 专家团队

目标架构：

1. 用户输入。
2. CEO/TotalAgent 接入并管理 session_id。
3. 安全合规层。
4. RAG/知识增强层。
5. 核心决策层：意图识别、参数抽取、执行模式判断。
6. 专家分发层：根据 Intent 唤醒 Handler。
7. 业务执行层：具备读文件、读数据库、代码补丁等受控能力。
8. 结果返回层：给用户看最终结果，不暴露内部调试日志。
9. 反思学习层：按 session_id 沉淀经验。

已暴露过的问题：

- 专家团队曾出现“未修改却说已完成”。
- 曾出现跨会话旧回复污染。
- 曾出现数据库问题却回答前端翻译功能。
- 因此必须加强 session_id、回答校验、权限缺口提示、执行证据检查。

专家团队回答规则：

- 没有执行证据时，不能说“已完成”。
- 缺少权限时，必须明确说“我没有访问权限”，并给两个选择：用户执行SQL/命令后贴结果，或询问用户是否授权一次访问。
- 用户问数据库，就不能回复前端功能完成。
- 新会话必须用新的 session_id 和对应历史，不允许沿用旧上下文。

## Flask 路由索引

| 路由 | 方法 | 功能 |
|---|---|---|
| `/` | GET | 主页面，未登录则跳登录 |
| `/login` | GET | 登录页面 |
| `/api/auth/register` | POST | 注册 |
| `/api/auth/login` | POST | 登录 |
| `/api/auth/me` | GET | 当前用户 |
| `/api/auth/logout` | POST | 退出登录 |
| `/api/products` | GET | 商品列表、筛选、排序、分页 |
| `/api/products/stats` | GET | 统计卡片 |
| `/api/products/latest-date` | GET | 当前数据源最新采集日期 |
| `/api/products/recalculate-scores` | POST | 选品分回算，管理员/经理 |
| `/api/products/pod_cross_category/<product_id>/extract` | POST | POD插画提取任务 |
| `/api/products/pod_cross_category/extract-tasks/<task_id>` | GET | 查询POD插画任务 |
| `/api/translate-title` | POST | 标题翻译 |
| `/api/translate-text` | POST | 通用文本翻译 |
| `/api/products/<source>/<product_id>` | GET | 站内详情 |
| `/api/products/<source>/<product_id>/analysis-detail` | POST | IP/材质深度分析 |
| `/api/detail-analyses` | GET | 深度分析记录列表 |
| `/api/expert-team/roles` | GET | 专家角色 |
| `/api/expert-team/sessions` | GET | 专家会话列表 |
| `/api/expert-team/sessions/<session_id>/messages` | GET | 专家会话消息 |
| `/api/expert-team/sessions/<session_id>` | DELETE | 删除专家会话 |
| `/api/expert-team/workflows` | GET | 专家工作流列表 |
| `/api/expert-team/workflows/<workflow_id>` | GET | 专家工作流详情 |
| `/api/expert-team/chat` | POST | 专家团队聊天入口 |
| `/api/products/<source>/ready-links` | GET | 待上架链接 |
| `/api/products/<source>/export-ready-links` | GET | 导出待上架链接 |
| `/api/products/<source>/<product_id>/image` | GET | 商品图片输出 |

## 后端函数索引

### 基础工具与权限

- `db`：创建数据库连接。
- `safe_rollback` / `safe_close`：数据库异常时安全回滚/关闭，避免连接断开后再次抛错。
- `api_ok` / `api_error`：统一接口返回。
- `current_user` / `require_current_user` / `require_expert_team_permission`：用户态与权限。
- `has_user_permission`：权限判断。
- `parse_int` / `clamp_int` / `stringify` / `serialize_value`：输入/输出清洗。
- `strip_llm_think_blocks`：删除大模型 `<think>...</think>` 内容。
- `translate_text_to_chinese` / `translate_general_text_to_chinese` / `fallback_translate_title`：翻译相关。

### 表结构与初始化

- `ensure_pod_cross_category_tables`：创建/维护跨品类POD相关表。
- `ensure_selection_score_columns`：确保四类商品表有选品分相关字段。
- `ensure_detail_analysis_table`：深度分析记录表。
- `ensure_expert_team_tables`：专家团队会话、消息、工作流表。

### 专家团队

- `expert_team_roles_payload`：专家角色列表。
- `normalize_expert_session` / `normalize_expert_message` / `normalize_expert_workflow_instance` / `normalize_expert_workflow_step`：专家团队数据归一化。
- `make_expert_session_title`：会话标题。
- `build_expert_context_query`：结合历史构建检索查询。
- `build_expert_ceo_decision`：CEO决策。
- `dispatch_expert_handlers`：专家分发。
- `handle_project_file_scout`：项目文件侦察。
- `handle_system_architect`：技术架构专家。
- `handle_ux_lead`：用户体验负责人。
- `handle_codex_executor`：代码执行/交接执行者。
- `list_expert_execution_workflows` / `get_expert_workflow_definitions`：专家工作流定义。
- `select_expert_workflow_definition` / `run_expert_workflow_engine` / `execute_expert_workflow_step`：工作流选择与执行。
- `select_execution_adapter`：选择执行适配器。
- `apply_controlled_codepatch_adapter`：受控代码补丁执行适配器。
- `generate_codepatch_plan` / `build_codepatch_planning_prompt` / `parse_codepatch_json` / `normalize_codepatch_plan`：代码补丁计划。
- `validate_codepatch_plan_scope` / `resolve_codepatch_path` / `is_codepatch_default_whitelisted` / `user_confirmed_extra_write_access` / `find_dangerous_codepatch_content`：执行安全校验。
- `apply_codepatch_plan` / `rollback_codepatch_changes` / `apply_codepatch_operation` / `verify_codepatch_result`：代码补丁应用与验证。
- `ExpertResponseValidator` / `validate_and_refine_expert_answer` / `correct_expert_answer_for_user_experience`：专家回答校验与纠偏。
- `sanitize_expert_team_answer` / `sanitize_expert_message_for_display` / `remove_internal_workflow_diagnostics`：用户可见回答清理。
- `build_permission_required_answer` / `needs_database_runtime_access` / `append_database_permission_or_diagnostics`：权限缺口与数据库诊断提示。
- `collect_expert_readonly_context` / `collect_expert_file_context` / `collect_expert_base_file_context`：只读上下文收集。

### 商品查询、筛选、排序

- `normalize_source` / `normalize_period` / `build_sales_period`：数据源与周期。
- `build_product_select`：商品列表 SELECT 字段构造。
- `build_latest_product_join`：最新日期/最新快照 join。
- `build_sales_aggregate_join`：采集日期范围销量聚合。
- `build_previous_sales_aggregate_join`：上期销量聚合。
- `build_sales_delta_join` / `empty_sales_delta_join` / `sales_delta_current_expr` / `sales_delta_previous_expr`：销售环比计算辅助。
- `build_filters`：主筛选条件。
- `add_range_filter` / `add_number_range_filter` / `add_ratio_range_filter` / `add_eq_filter` / `add_grade_filter` / `add_material_filter`：筛选条件构造。
- `get_distribution_filter_field` / `distribution_ratio_number_expr` / `distribution_item_number_expr` / `distribution_index_number_expr`：分布占比筛选。
- `numeric_sql_expr` / `qualified_field` / `sort_field_expr` / `build_order`：排序字段转换。
- `product_card_sales_sort_expr` / `product_card_ratio_sort_expr` / `sales_growth_sort_expr`：关键排序表达式。
- `normalize_product_row`：列表数据归一化，前端主要依赖该函数输出。

### 选品分

- `clean_number`：数值清洗。
- `overview_metric_value`：从 overview JSON 中取指标。
- `score_category_key`：类目分组键。
- `score_input_from_row`：从商品行提取评分输入。
- `penalty_norm_score`：中位最优惩罚函数。
- `calculate_selection_score`：核心选品分计算，返回分数与 JSON 原因。
- `score_sales_mom`：销售环比。
- `table_column_names`：查询表字段。
- `build_score_select_sql`：回算所需SELECT。
- `recalculate_selection_scores`：批量/全量回算选品分。

### 详情页与数据归一化

- `attach_runtime_metrics` / `calculate_runtime_sales_metrics` / `calculate_collection_sales_growth`：运行时销量与环比。
- `format_runtime_sales_result`：销售指标格式化。
- `ratio_from_distribution`：成交分布比例。
- `normalize_price_display` / `is_price_display`：价格展示。
- `normalize_detail`：站内详情归一化。
- `build_detail_period_fields` / `build_detail_sku_analysis`：详情周期字段与SKU。
- `normalize_sku_analysis` / `find_sku_sales_block` / `normalize_sku_items`：SKU数据整理。
- `sales_from_distribution` / `percent_to_number` / `normalize_distribution_chart`：成交分布图表数据。
- `build_kalodata_30d_overview` / `build_kalodata_30d_distribution`：Kalodata 30天详情数据。
- `enrich_detail_period_fields` / `build_collection_aggregate_overview`：详情页周期数据补充。
- `build_platform_url` / `resolve_platform_url` / `build_tiktok_shop_url`：外链构建。
- `serve_image_value`：图片输出。

### AI后台任务

- `start_ai_daemon_if_enabled` / `ai_daemon_loop` / `run_ai_daemon_once`：AI分析守护任务。
- `process_single_ai_task` / `process_single_ai_task_once`：单商品AI任务处理。
- `reuse_existing_product_analysis` / `fetch_current_analysis_state` / `fetch_reusable_fastmoss_analysis`：分析复用。
- `needs_ai_analysis`：判断是否需要AI分析。
- `run_ai_product_flow`：运行IP/材质分析。
- `fetch_ai_daemon_product_keys` / `fetch_ai_daemon_latest_date`：AI任务取数。
- `start_score_daemon_if_enabled` / `score_daemon_loop`：选品分后台回算。

## 前端函数索引

### 初始化、认证、页面切换

- `initAuth`：初始化登录态。
- `canUseExpertTeam`：专家团队权限。
- `openDetailAnalysisWorkspace`：打开深度分析页面。
- `openExpertTeamWorkspace`：打开专家团队页面。
- `renderExpertTeamPage`：渲染专家团队页面。
- `logout`：退出登录。

### 专家团队前端

- `expertSessionsHtml` / `loadExpertSessions` / `loadExpertSessionMessages` / `deleteExpertSession`：专家会话。
- `expertMessagesHtml` / `formatExpertMessage`：专家消息渲染。
- `expertImagesHtml` / `addExpertImageFiles` / `addExpertImageFile` / `handleExpertImagePaste`：专家图片附件。
- `parseExpertBaseFiles` / `defaultExpertBaseFiles`：专家基底文件。
- `sendExpertTeamMessage`：发送专家团队消息。

### 深度分析

- `renderDetailAnalysisPage`：深度分析页面。
- `loadDetailAnalyses`：加载深度分析记录。
- `detailAnalysisRecordsHtml`：记录列表。

### 筛选与日期

- `initCustomFilters` / `renderFilterOptions` / `filterOptionHint`：筛选器初始化。
- `addFilter` / `removeFilter` / `renderActiveFilters`：动态筛选项。
- `filterCardTemplate` / `filterControlTemplate`：筛选控件HTML。
- `datePickerInput` / `openDatePicker`：日期控件。
- `applyDatePreset`：昨日、过去7天、过去30天等日期快捷选择。
- `getLatestCollectionDate` / `loadDefaultDateAndData`：默认最新采集日期。
- `derivePeriodFromCollectionDates` / `collectionDateHint`：采集日期与周期提示。
- `buildParams`：构建 `/api/products` 参数。

### 商品列表与表格

- `loadAll` / `loadProducts` / `loadStats`：加载数据。
- `dedupeLatestItems`：同商品去重，保留较新日期。
- `compareDateRecord`：日期比较。
- `renderItems` / `renderTable` / `renderPodTable`：列表渲染。
- `rowTemplate` / `podRowTemplate`：行模板。
- `selectionScoreBadge`：选品分徽标与明细按钮。
- `illustrationStatusBadge` / `podStatusBadge` / `podExtractButton` / `bindPodActions`：POD状态与插画提取。
- `salesSharePie`：销量占比小饼图。
- `sortHeader` / `bindSortHeaders`：排序表头。
- `bindRowActions`：绑定详情、图片、原因、评分明细等行内动作。

### 选品分明细

- `openScoreDetail`：打开选品分明细弹窗。
- `parseScoreDetail`：解析 `score_reason`，兼容 JSON 与旧文本。
- `scoreSummaryText`：提取评分摘要。
- `scoreConclusion`：根据分数生成结论。
- `scoreDetailHtml`：评分明细HTML。
- `scorePartCard`：单个维度评分卡片。
- `formatScoreNumber`：评分数字展示。

### 站内详情

- `openDetail` / `renderDetailDialog` / `bindDetailControls`：详情弹窗。
- `renderProductDetail`：站内详情主体。
- `scoreDecisionSection`：详情页选品分、销售环比、插画状态、评分明细。
- `detailMetricCard`：指标卡。
- `detailOverview` / `detailDistribution`：周期数据。
- `distributionPanel` / `distributionDonutTable` / `distributionBarTable`：成交渠道图表。
- `skuAnalysisSection` / `detailSkuAnalysis` / `skuDonutTable` / `skuBarTable`：SKU分析。
- `overviewPanel`：核心指标面板。
- `translateSellingPoints`：卖点翻译。
- `stripThinkBlocks`：前端去除 `<think>`。

### IP与材质展示

- `formatIpReasonHtml`：IP分析展示。
- `ipSummaryData` / `ipDetailedResultHtml`：IP轻量/详细分析。
- `materialAnalysisHtml` / `materialDetailedResultHtml`：材质分析。
- `loadDetailedAnalysis`：触发/读取详细分析。
- `detailAnalysisHintHtml`：详细分析提示。

### 链接、标题、工具函数

- `platformLinks` / `sourcePlatformLink` / `tiktokPlatformLink` / `platformLogo`：平台按钮。
- `buildFastMossUrl` / `buildTikTokShopUrl`：外链。
- `productTitleCell`：标题单元格。
- `showProductTitleTooltip` / `positionProductTitleTooltip` / `hideProductTitleTooltip`：标题悬浮。
- `copyProductTitle` / `translateProductTitle`：标题复制与翻译。
- `statusSelect`：审核状态下拉。
- `escapeHtml` / `showToast`：基础UI工具。

## 当前已知风险与注意事项

1. `static/index.html` 中存在部分重复函数名，如 `formatIpReasonHtml`、`loadDetailedAnalysis`、`materialAnalysisHtml` 后定义会覆盖前定义。后续整理前必须先确认实际生效的是最后一个定义。
2. `app.py` 中 `product_card_sales_sort_expr` 出现重复定义，后定义会覆盖前定义。修改排序时要谨慎。
3. PowerShell 输出中文可能乱码，但文件本身在浏览器中通常正常；不要因终端乱码误判前端文件损坏。
4. 商品列表查询慢通常与日期筛选、排序表达式、缺索引、JOIN聚合有关。不能只看前端字段名判断数据库字段。
5. AI守护任务与商品列表请求共用数据库时，可能出现锁等待或连接超时，需要控制并发、缩短事务、避免长事务内调用AI。
6. 专家团队必须有执行证据才能宣称完成。

## 变更日志

### 2026-06-12：插画提取第一步可提取性判断

修改目标：先不做图生图/真实提取，只判断商品主图是否存在“适合迁移到手机壳上的插画、图案、纹理或装饰元素”。用户先抽样验证判断是否准确，确认后再接第二步图生图提取和第三步 iPhone 17 手机壳效果图。

涉及文件：

- `app.py`
- `static/index.html`
- `static/styles.css`
- `PROJECT_MEMORY_ARCHIVE.md`

后端修改：

- `SOURCES` 四个数据源都新增字段映射：`illustration_extractable`、`illustration_extract_reason`。
- `ensure_selection_score_columns()` 自动补齐数据库字段：`illustration_extractable TINYINT DEFAULT NULL`、`illustration_extract_reason VARCHAR(500) DEFAULT NULL`。
- 新增接口：`POST /api/products/<source>/<product_id>/illustration-check`，入参为 `date_record`，读取商品主图并调用 MiniMax 多模态判断是否适合提取为手机壳插画，随后写回当前商品行。
- 新增函数：`normalize_vision_image_url`、`parse_llm_json_object`、`analyze_illustration_extractability`。
- `build_product_select()`、`normalize_product_row()`、`normalize_detail()` 返回插画可提取性字段。

前端修改：

- 商品列表“插画状态”列追加展示：`未判断`、`可提取`、`不可提取`。
- 新增“判断插画”按钮，点击后调用 `/illustration-check`。
- 站内详情选品分/插画面板增加“插画判断”展示和同样的判断按钮。
- POD 的“提取插画”按钮现在只在 `illustration_extractable == 1` 时展示。

验证方式：

- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`。
- 已执行静态检查：`static/index.html` 中 `illustration-check` 与 `illustration_extractable` 标记存在。

使用方式：

- 重启 Flask 后端，让 `ensure_selection_score_columns()` 自动补齐字段。
- 刷新页面，在商品列表或站内详情点击“判断插画”。
- 判断完成后页面显示“可提取/不可提取”，悬浮可查看原因。

风险边界：

- 本次不做图生图、不做真实抠图、不生成手机壳效果图。
- 本次不修改选品分公式、AI_DAEMON、IP/材质分析、商品列表筛选排序去重逻辑。
- MiniMax 返回格式异常时接口会报错，不会写入假判断结果。

后续修正：

- 用户确认该能力不应由用户手动点击“判断插画”，而应由系统后台自动判断并写入数据库。
- 新增 `ILLUSTRATION_DAEMON` 后台任务：
  - `ILLUSTRATION_DAEMON_ENABLED` 默认开启。
  - `ILLUSTRATION_DAEMON_INTERVAL_SECONDS` 默认 300 秒。
  - `ILLUSTRATION_DAEMON_BATCH_SIZE` 默认 30。
  - `ILLUSTRATION_DAEMON_SOURCES` 默认 `pod_cross_category`。
  - `ILLUSTRATION_DAEMON_LATEST_ONLY` 默认只跑最新采集日期。
- 后台只处理 `illustration_extractable IS NULL` 且有主图的商品。
- 判断成功后写入 `illustration_extractable` 与 `illustration_extract_reason`。
- 判断失败时不写入“不可提取”，保留 NULL 等待下轮重试，避免 API 抖动导致误判。
- 前端已移除用户可见的“判断插画”按钮，只展示后台判断结果。
- 后续修正：不可提取商品不再展示在 POD 跨品类前端列表中，后端列表过滤条件为 `illustration_extractable IS NULL OR illustration_extractable <> 0`。
- 后续修正：插画可提取性判断只用于 `pod_cross_category`，后台 `ILLUSTRATION_DAEMON` 会忽略统一表、FastMoss 表和 Kalodata 表；手动调试接口也限制为 POD 来源。
- 后续修正：普通商品表格与非 POD 详情不展示插画可提取性判断，避免用户误以为统一表、FastMoss、Kalodata 也参与该判断。

### 2026-06-12：选品分公式问号改为可点击展开

修改目标：修复选品分明细里的小问号只能悬浮 `title`，点击没有反应的问题。

涉及文件：

- `static/index.html`
- `static/styles.css`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：

- `scorePartCard()` 中的公式问号改为带 `data-score-formula` 的按钮。
- 新增 `bindScoreFormulaHelp(root)`：
  - 点击问号后在当前评分卡片内展开公式说明。
  - 再次点击其它问号时自动收起其它公式。
  - 同时支持列表评分弹窗和站内详情评分折叠区。
- 新增 `.score-formula-popover` 样式，作为公式说明框。

数据库影响：

- 无。

是否需要回算：

- 不需要。

验证方式：

- 从 `static/index.html` 抽取 `<script>` 后执行 `node --check` 通过。
- `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py` 通过。

用户如何看到：

- 重启/刷新前端资源后，打开选品分明细。
- 点击每个分项标题旁边的 `?`，会在当前卡片内展开公式说明。

风险边界：

- 本次只改选品分明细的公式提示交互。
- 不改评分计算、不改筛选排序、不改后台任务。

### 2026-06-12：选品分明细增加参数层与公式提示

修改目标：修复选品分明细解释不清的问题。用户看到“30天销量 162 / 类目最大 18264”时会按原始占比理解，误以为销量分不可能接近满分；实际评分使用的是 log 归一化，因此必须把参数层拆清楚。

涉及文件：

- `static/index.html`
- `static/styles.css`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：

- 选品分明细不再只展示一句“30天销量 / 类目最大”。
- 每个分项卡展示关键参数：
  - 销量分：30天销量、类目最大30天销量、原始销量占比、log归一化结果、曲线得分比例。
  - 评分分：商品评分、评分得分比例。
  - 热度分：视频数、类目最大视频数、原始视频占比、log归一化结果、曲线得分比例。
  - 趋势分：7天销量、7天log归一化、30天log归一化、趋势比、封顶后比例。
- 每个分项标题旁新增 `?` 公式提示按钮，用户悬浮即可查看对应公式。
- 老数据如果没有后端新增的 raw ratio 字段，前端会用现有输入值现场计算原始占比。
- 明细卡片由四列改为两列，避免公式参数挤压。

数据库影响：

- 无新增字段。
- 不需要立即回算；旧 `score_reason` JSON 也能由前端补算部分展示参数。

是否需要回算：

- 不需要。
- 如果要让后端 JSON 携带最完整字段，可等待后台评分任务逐步重算。

验证方式：

- `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py` 通过。
- 从 `static/index.html` 抽取 `<script>` 后执行 `node --check` 通过。

用户如何看到：

- 重启 Flask 后端并刷新页面。
- 点击列表“选品分”的“明细”。
- 在每个分项卡里查看参数；悬浮 `?` 查看公式。

风险边界：

- 本次只修改选品分明细展示。
- 不改评分计算公式、不改列表查询、不改筛选排序、不改AI分析。

### 2026-06-12：评分后台任务改为队列式回算

修改目标：修复评分任务默认不开、10分钟只跑100条、只跑最新日期、反复重算同一批、已评分无限重算的问题。

涉及文件：

- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：

- `SCORE_DAEMON_ENABLED` 默认改为开启，除非显式设置 `SCORE_DAEMON_ENABLED=false`。
- `SCORE_DAEMON_INTERVAL_SECONDS` 默认从 600 秒改为 120 秒。
- `SCORE_DAEMON_BATCH_SIZE` 默认从 100 改为 1000。
- 新增 `SCORE_RECALC_MAX_ATTEMPTS`，默认 3。
- 新增 `SCORE_RECALC_LATEST_ONLY`，默认 false，即不只跑最新日期。
- 商品表新增字段：
  - `score_attempts`：选品分回算次数。
  - `score_updated_at`：最后回算时间。
- 新增索引 `idx_score_queue(selection_score, score_attempts, date_record)`。
- `recalculate_selection_scores()` 新增参数：
  - `force`：是否强制重算。
  - `latest_only`：是否只算最新日期。
- 队列取数逻辑改为：
  - 未评分优先。
  - 默认覆盖全部日期。
  - 已评分商品最多重算到 `score_attempts < 3`。
  - 每轮最多取 `SCORE_DAEMON_BATCH_SIZE` 条，不再反复卡住固定前100条。
- 队列 SQL 二次调整：
  - 不再使用 `ORDER BY CASE WHEN selection_score ...`。
  - 先查未评分队列，不足时再查重算候选。
  - 避免复杂排序导致大表 SELECT 超时。
- 队列 SQL 三次调整：
  - 默认批量从 200 降为 50，避免后台守护任务压垮 MySQL。
  - 队列排序从 `date_record DESC, id ASC` 改为 `id ASC`，优先走主键，避免未创建索引时 filesort 超时。
  - 单个数据源超时后只跳过该 source 并记录错误，不再让整轮守护任务 traceback 刷屏。

数据库影响：

- 四类商品表会自动补 `score_attempts`、`score_updated_at` 字段。
- 自动补 `idx_score_queue` 索引。

是否需要回算：

- 不需要手动全量一次性跑完。
- 后台评分任务会按队列逐轮消化。
- 如果要强制重算，可调用 `/api/products/recalculate-scores?force=true`。

验证方式：

- 需要执行 `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`。
- 已执行 `recalculate_selection_scores(source='unified', limit=1)`，返回 `{'unified': {'scanned': 1, 'updated': 1}}`。
- 再次执行 `recalculate_selection_scores(source='unified', limit=1)`，从超时恢复为正常返回。
- 启动日志应显示：`[SCORE_DAEMON] started batch_size=50 interval=180s latest_only=False max_attempts=3`。

用户如何看到：

- 重启 Flask 后端。
- 商品列表中“未评分”会随着后台任务逐步变成分数。
- 已经评分过的商品不会无限重算，最多重算3次。

风险边界：

- 本次只改评分后台任务和评分字段。
- 不改商品筛选、排序、去重、IP/材质AI分析。

### 2026-06-12：修正选品分维度超分问题

修改目标：修复销量分、热度分超过自身维度满分的问题，例如销量分满分35却算出46.5。

涉及文件：

- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：

- 修改 `penalty_norm_score(norm, max_points)`。
- 旧实现为 `norm * (1 - penalty) * 2 * max_points`，在 `norm > 0.5` 时会超过维度满分。
- 新实现按用户给出的惩罚表做分段插值：0%=0、10%=14.6%、30%=52%、50%=100%、70%=80.6%、100%=66.8%，再乘以维度满分。
- 修复后销量分最高不超过35，热度分最高不超过25。

数据库影响：

- 无新增字段。
- 需要重新回算已有 `selection_score` 和 `score_reason`，否则页面仍会展示旧公式算出的分数。

是否需要回算：

- 需要。
- 已成功回算 `unified` 最新日期：扫描 3248 条。
- 已成功回算 `kalodata` 最新日期：扫描 3983 条，MySQL rowcount 显示实际变化 183 条。
- `fastmoss` 回算时数据库 UPDATE 阶段出现 `(2013, Lost connection to MySQL server during query timed out)`，即使 limit=50 仍失败，疑似数据库锁等待、连接超时或服务端写入压力问题。公式已修，但 fastmoss 旧分数尚未完全覆盖。

验证方式：

- `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py` 通过。
- 函数级验证：
  - `penalty_norm_score(0.69, 35) = 28.5495`
  - `penalty_norm_score(0.83, 25) = 18.655`
  - `penalty_norm_score(0.5, 35) = 35`
  - `penalty_norm_score(1, 35) = 23.38`

用户如何看到：

- 重启 Flask 后端。
- 已回算成功的数据源会显示修正后的选品分。
- fastmoss 需要数据库写入恢复后再次执行回算。

风险边界：

- 本次只改评分惩罚函数和回算分批读取/分批提交逻辑。
- 不修改列表筛选、排序、去重、AI分析、站内详情其它功能。

### 2026-06-12：统计接口材质聚合超时降级

修改目标：修复 `/api/products/stats` 因材质统计 SQL 超时导致整个页面 500 的问题。

涉及文件：

- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：

- 在 `stats()` 中对 `count_material_types()` 增加 `try/except pymysql.err.OperationalError`。
- 如果材质 JSON 聚合超时，接口不再整体失败，而是返回空的 `materials`，其它统计如总数、状态、IP等级仍正常返回。
- `count_material_types()` 增加 `material_analysis IS NOT NULL` 和 `material_analysis <> ''` 条件，减少无效 JSON 解析。

数据库影响：

- 无新增字段。
- 无索引变更。
- 只是减少无效扫描，并避免慢聚合拖垮接口。

是否需要回算：

- 不需要。

验证方式：

- 需要执行 `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`。
- 页面刷新后，即使材质统计超时，商品列表统计接口也不应再返回 500。

用户如何看到：

- 重启 Flask 后端。
- 刷新商品列表页。
- 如果材质统计超时，材质统计卡可能为空，但页面和其它统计应正常加载。

风险边界：

- 本次不修改商品列表 SQL、排序、选品分、AI分析逻辑。
- 这只是降级保护，不是彻底性能优化。彻底优化需要给材质类型做独立字段或生成列索引。

### 2026-06-11：选品分明细展示

修改目标：让用户能点击列表中的选品分查看“为什么打到100分”，并在站内详情中增加可展开的评分明细。

涉及文件：

- `app.py`
- `static/index.html`
- `static/styles.css`

具体改动：

- `calculate_selection_score` 的 `score_reason` 从普通一句话升级为 JSON 结构，包含输入值、类目基准、四个维度分数、归一化值、趋势比、风控、结论、摘要。
- 修复评分原因中的中文问号文案，改为正常中文。
- 列表选品分徽标旁增加“明细”按钮。
- 新增 `scoreDialog` 弹窗。
- 新增前端函数：`openScoreDetail`、`parseScoreDetail`、`scoreSummaryText`、`scoreConclusion`、`scoreDetailHtml`、`scorePartCard`、`formatScoreNumber`。
- 站内详情 `scoreDecisionSection` 增加“查看评分明细”折叠区。
- 新增评分明细样式：`score-cell`、`score-detail-link`、`score-detail-panel`、`score-breakdown-grid`、`score-part-card`、`score-detail-kv` 等。
- 对 `fastmoss` 2026-06-09 执行了一次选品分全量回算，使第一屏商品可立即看到结构化明细。

验证方式：

- `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py` 通过。
- 从 `static/index.html` 抽取 `<script>` 后执行 `node --check` 通过。
- 使用 Flask test client 请求 `/api/products?source=fastmoss&page=1&page_size=10&period=7d&sort_by=score&sort_order=desc&date_start=2026-06-09&date_end=2026-06-09` 返回 200。
- 验证前5条 `score_reason` 已为 JSON 格式。

用户如何看到：

- 重启 Flask 后端。
- 刷新主页面。
- 进入 `fastmoss`，选择 2026-06-09，按选品分排序。
- 在列表“选品分”列点击“明细”。
- 或点击“站内详情”，在选品分面板点击“查看评分明细”。

风险边界：

- 本次不修改筛选逻辑、去重逻辑、排序SQL、状态下拉、IP/材质分析逻辑。
- 旧数据的 `score_reason` 如果仍是一句话，前端会兼容显示摘要，但不会有完整拆解；需要回算后才有完整明细。

## 后续修改记录模板

```md
### YYYY-MM-DD：修改标题

修改目标：

涉及文件：

具体改动：

数据库影响：

是否需要回算：

验证方式：

用户如何看到：

风险边界：

失败/回滚记录：
```

### 2026-06-12：AI 分析参考库读取超时降级与缓存
修改目标：
- 处理 AI daemon 在 `load_experiences()` 读取 `cp_ai_analysis_experience` 时出现 `(2013, Lost connection to MySQL server during query timed out)` 的问题。
- 明确选品分字段已覆盖 `fastmoss_product_aggregate`、`fastmoss_product_rank_aggregate`、`kalodata_youwei_product`，启动 Flask 时会自动检查并补齐字段。

涉及文件：
- `tools/analyze_single_product.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- 为 `tools/analyze_single_product.py` 的数据库连接补充 `connect_timeout/read_timeout/write_timeout`，与 `app.py` 的连接超时配置保持一致。
- 新增规则库/经验库本地 TTL 缓存，默认 `AI_REFERENCE_CACHE_TTL_SECONDS=300` 秒。
- `load_rules()` 不再使用商品任务正在写入的事务连接读取规则库，而是优先读缓存；缓存过期后使用独立只读连接加载。
- `load_experiences()` 不再使用商品任务正在写入的事务连接读取经验库，而是优先读缓存；缓存过期后使用独立只读连接加载。
- 如果规则库或经验库读取失败，会打印 `[AI_REFERENCE] ... fallback_rows=...`，并返回旧缓存；没有旧缓存时返回空列表，让当前商品分析继续走下去，而不是拖坏商品写入连接。

问题原因：
- 旧逻辑中每个商品、每个分析类型都会用同一个商品事务连接读取经验库：
  `run_analysis()` → `load_experiences(cursor, analysis_type)`。
- 并发提升后，经验库查询被多线程重复放大；一旦读取超时，PyMySQL 当前连接已不可用，后续 rollback/写入也容易跟着失败。
- 本次改动把“参考资料读取”和“商品分析写入”隔离开，降低单个慢查询拖垮整条商品任务的概率。

数据库影响：
- 本次没有新增字段、没有自动创建索引、没有修改商品数据。
- 选品分相关字段仍由 `app.py` 的 `ensure_selection_score_columns()` 在 Flask 启动时自动检查并补齐，覆盖 `SOURCES` 中的表，包括 `fastmoss_product_aggregate`、`fastmoss_product_rank_aggregate`、`kalodata_youwei_product` 和 `pod_cross_category_product`。
- 如果数据库账号没有 `ALTER TABLE` 权限，启动时会报错，需要手动执行建字段 SQL 或给账号授权。

是否需要回算：
- 不需要。该改动只影响 AI daemon 读取规则库/经验库的方式。

验证方式：
- `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py tools\analyze_single_product.py` 通过。
- 函数级验证通过：
  - `load_rules(None, "IP")` 返回 125 条。
  - `load_experiences(None, "IP")` 返回 20 条。
  - 第二次调用 `load_experiences(None, "IP")` 命中缓存，仍返回 20 条。

用户如何看到：
- 重启 Flask 后端。
- AI daemon 日志中不应再频繁因为 `load_experiences()` 查询超时导致整条商品任务失败。
- 如果参考库读取失败，会看到 `[AI_REFERENCE] load_experiences failed... fallback_rows=...`，但商品任务会继续执行。

风险边界：
- 本次不修改商品列表、筛选、排序、去重、站内详情、选品分公式和 MiniMax prompt。
- 如果经验库表本身持续慢，建议后续手动加索引：
  `CREATE INDEX idx_ai_exp_type_enabled_updated ON cp_ai_analysis_experience (analysis_type, enabled, updated_at, id);`

### 2026-06-12：选品分启动逻辑说明归档
修改目标：
- 归档当前选品分功能的启动方式，避免后续误以为需要手动开启或只覆盖单表。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

当前启动逻辑：
- 启动 Flask 后端时，也就是运行 `D:\choice_product\.venv\Scripts\python.exe D:\choice_product\app.py` 时，会先创建 Flask app。
- `create_app()` 内会调用 `ensure_selection_score_columns()`，自动检查并补齐选品分相关字段。
- 选品分后台任务由 `start_score_daemon_if_enabled()` 启动，该函数在 `if __name__ == "__main__":` 下执行。
- 只要环境变量 `SCORE_DAEMON_ENABLED` 没有设置为 `false`，后台评分任务默认自动开启。

覆盖的数据表：
- `fastmoss_product_aggregate`
- `fastmoss_product_rank_aggregate`
- `kalodata_youwei_product`
- `pod_cross_category_product`

自动检查/补齐的字段：
- `selection_score`：选品综合分。
- `score_reason`：选品分明细与原因，JSON 字符串。
- `sales_mom`：销售环比百分比。
- `score_attempts`：评分回算次数，默认最多 3 次。
- `score_updated_at`：最后评分时间。
- `illustration_status`：插画提取状态。
- `illustration_result_url`：插画提取结果链接。

后台评分默认配置：
- `SCORE_DAEMON_ENABLED=true`，默认自动开启。
- `SCORE_DAEMON_INTERVAL_SECONDS=180`，默认每 180 秒跑一轮。
- `SCORE_DAEMON_BATCH_SIZE=50`，默认每轮最多处理 50 条。
- `SCORE_RECALC_MAX_ATTEMPTS=3`，已经打过分的数据最多重算 3 次。
- `SCORE_RECALC_LATEST_ONLY=false`，默认不是只跑最新日期，而是按队列补全。
- `SCORE_AUTO_CREATE_INDEXES=false`，默认不自动创建索引，避免大表建索引时锁表。

运行时日志：
- 后端启动后，如果评分后台任务正常开启，应看到类似日志：
  `[SCORE_DAEMON] started batch_size=50 interval=180s latest_only=False max_attempts=3`
- 每轮结束会打印：
  `[SCORE_DAEMON] cycle finished summary=...`

用户如何看到：
- 重启 Flask 后端。
- 商品列表中原本显示“未评分”的商品，会随着后台任务逐步变成具体分数。
- 如果要立即回算，可以调用 `/api/products/recalculate-scores`，但需要 admin 或 manager 权限。

风险边界：
- 如果数据库账号没有 `ALTER TABLE` 权限，字段自动补齐会失败，需要手动执行建字段 SQL 或给账号授权。
- 如果表缺少索引，后台评分仍可能较慢；当前默认不自动建索引。

### 2026-06-12：修复评分后台 tuple.extend 崩溃
修改目标：
- 修复评分后台任务日志中出现的错误：
  `AttributeError: 'tuple' object has no attribute 'extend'`。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

问题原因：
- `recalculate_selection_scores()` 中先执行 `rows = cursor.fetchall()`。
- 在当前 PyMySQL/游标返回形态下，`fetchall()` 返回的是 tuple。
- 后续逻辑需要继续追加“已评分但未满最大重算次数”的候选数据，调用了 `rows.extend(...)`，tuple 没有 `extend()`，所以后台评分任务整轮失败。

具体改动：
- 将评分队列查询结果统一转成 list：
  - `rows = list(cursor.fetchall())`
  - `rows.extend(list(cursor.fetchall()))`
  - force 分支中也对 `cursor.fetchall()` 转 list 后再追加。

数据库影响：
- 无字段变更。
- 无数据结构变更。
- 只修复后台评分任务运行时错误。

是否需要回算：
- 不需要单独回算。重启后端后，后台评分任务会按原有队列继续跑。

验证方式：
- 需要执行 `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`。

用户如何看到：
- 重启 Flask 后端。
- 后台日志不应再出现 `tuple object has no attribute extend`。
- 应恢复输出 `[SCORE_DAEMON] cycle finished summary=...`。

### 2026-06-12：选品分后台批次调整为每源 200
修改目标：
- 将选品分后台任务从每源每轮 50 条调整为每源每轮 200 条，缩短 1.2 万级数据量的补分时间。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- `SCORE_DAEMON_BATCH_SIZE` 默认值从 `50` 改为 `200`。
- 仍保留环境变量覆盖能力：如果启动前设置 `SCORE_DAEMON_BATCH_SIZE`，则以环境变量为准。

当前选品分扫描逻辑：
- 默认 `SCORE_RECALC_LATEST_ONLY=false`，不是只跑最新日期。
- 每轮按源分别处理：
  - `unified`
  - `fastmoss`
  - `kalodata`
  - `pod_cross_category`
- 优先处理 `selection_score` 为空的数据。
- 如果未评分数据不足本轮批次，才会补充处理已经评分但 `score_attempts < 3` 的数据。
- 因此它是“先补空分，再有限重算”，不是只分析最新日期。

预期速度：
- 如果 3 个主源都有待评分数据，每轮约处理 `200 * 3 = 600` 条。
- 默认每 180 秒一轮，1.2 万条理论约 20 轮，约 1 小时左右；实际速度取决于 MySQL 写入压力。

索引检查结果：
- `fastmoss_product_aggregate` 已有：
  - `idx_date_record(date_record)`
  - `idx_ip_grade(ip_grade)`
  - `idx_selection_score_date(date_record, selection_score)`
  - `idx_sales_mom_date(date_record, sales_mom)`
- `fastmoss_product_rank_aggregate` 已有：
  - `idx_date_record(date_record)`
  - `idx_ip_grade(ip_grade)`
  - 分类索引 `idx_category(category_l1, category_l2, category_l3)`
- `kalodata_youwei_product` 已有：
  - `idx_date_record(date_record)`
  - `idx_ip_grade(ip_grade)`
- 当前索引对选品分队列有一定帮助，但对 AI daemon 的 `material_analysis IS NULL OR ip_grade IS NULL` 队列查询帮助有限。

AI_DAEMON 超时结论：
- 本次日志里的 AI 超时发生在 `fetch_ai_daemon_product_keys()`，也就是“取待分析商品队列”阶段，不是在 MiniMax 请求阶段。
- 查询条件会包含：
  - 最新日期过滤（如果 `AI_DAEMON_LATEST_ONLY=true`）
  - `material_analysis IS NULL/='' OR ip_grade IS NULL/=''`
  - `ORDER BY id DESC LIMIT N`
- 现有索引没有专门覆盖这个 OR 条件，尤其 `material_analysis` 是 longtext，不适合作为普通联合索引字段。

是否需要回算：
- 不需要手动回算。重启后端后评分后台会按新批次继续跑。

验证方式：
- 需要执行 `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`。

用户如何看到：
- 重启 Flask 后端。
- 启动日志应显示：
  `[SCORE_DAEMON] started batch_size=200 interval=180s latest_only=False max_attempts=3`
- 每轮 summary 中每个有待评分数据的源最多会显示 `scanned: 200`。

风险边界：
- 本次只调整选品分后台批次，不调整 AI daemon 批次/并发。
- 如果 MySQL 写入压力过大，可以临时通过环境变量把 `SCORE_DAEMON_BATCH_SIZE` 降回 50 或 100。

### 2026-06-12：AI_DAEMON 队列查询拆分 OR 条件
修改目标：
- 修复/缓解 AI_DAEMON 在 `fetch_ai_daemon_product_keys()` 取待分析商品队列时超时的问题。
- 将 `both` 模式下的大 OR 条件拆成两个更容易走索引的查询。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

问题原因：
- 旧逻辑在 `analysis == "both"` 时使用：
  `material_analysis IS NULL OR material_analysis = '' OR ip_grade IS NULL OR ip_grade = ''`
- 同时还要按 `date_record` 过滤、按 `id DESC` 排序、`LIMIT N`。
- 这种“过滤 + OR + 排序”的组合很难被一个普通索引完整接住，尤其 `material_analysis` 是 longtext 字段，MySQL 容易扫描大量行后超时。

具体改动：
- `fetch_ai_daemon_product_keys()` 的 `both` 模式不再一次性执行大 OR 查询。
- 新逻辑分两步：
  1. 先查询 `ip_grade IS NULL OR ip_grade = ''` 的商品。
  2. 如果数量不够，再查询 `material_analysis IS NULL OR material_analysis = ''` 的商品。
  3. 按 `(product_id, date_record)` 去重，凑够本轮 `limit`。
- 新增辅助函数 `query_ai_daemon_product_keys()`，统一执行单条件队列查询并返回 list。

联合索引建议：
- 当前已经存在单列 `idx_date_record(date_record)` 和 `idx_ip_grade(ip_grade)`，但缺少专门服务队列查询的联合索引。
- 推荐后续在业务低峰期手动加：
  - `fastmoss_product_aggregate`：
    `CREATE INDEX idx_ai_queue_date_ip_id ON fastmoss_product_aggregate(date_record, ip_grade, id);`
    `CREATE INDEX idx_ai_queue_date_id ON fastmoss_product_aggregate(date_record, id);`
  - `fastmoss_product_rank_aggregate`：
    `CREATE INDEX idx_ai_queue_date_ip_id ON fastmoss_product_rank_aggregate(date_record, ip_grade, id);`
    `CREATE INDEX idx_ai_queue_date_id ON fastmoss_product_rank_aggregate(date_record, id);`
  - `kalodata_youwei_product`：
    `CREATE INDEX idx_ai_queue_date_ip_id ON kalodata_youwei_product(date_record, ip_grade, id);`
    `CREATE INDEX idx_ai_queue_date_id ON kalodata_youwei_product(date_record, id);`
- 不建议直接给 `material_analysis` longtext 做普通联合索引；更彻底的方案是未来增加 `material_done` / `ip_done` 这种 tinyint 状态字段。

数据库影响：
- 本次代码修改没有自动创建索引，避免大表 DDL 锁表。
- 如果要创建上述联合索引，建议手动确认业务低峰期执行。

是否需要回算：
- 不需要。

验证方式：
- 需要执行 `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`。

用户如何看到：
- 重启 Flask 后端。
- AI_DAEMON 日志中 `fetch_ai_daemon_product_keys()` 阶段超时概率应下降。
- 如果仍然超时，下一步应执行联合索引或新增状态字段。
### 2026-06-13：系统每日自动任务统计日志

修改目标：
- 增加统一日志，统计每天系统自动处理了多少条数据。
- 统计四个方向：插画可提取性自动判断、IP 分析、材质分析、选品分。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- 新增日志文件路径：`D:\choice_product\logs\system_daily_stats.jsonl`。
- 新增统计函数：
  - `empty_daily_stats()`
  - `load_latest_daily_stats(target_date)`
  - `record_daily_system_stats(event, delta=None, detail=None)`
- 每次后台任务完成一轮后写入一行 JSON：
  - `score_daemon_cycle`：按各 source 的 `updated` 汇总到 `selection_scored`。
  - `illustration_daemon_cycle`：按各 source 的 `processed` 汇总到 `illustration_checked`。
  - `ai_daemon_cycle`：按真实补齐字段数量汇总 `ip_analyzed` 与 `material_analyzed`。
- AI 统计口径不是简单的“处理了多少商品”，而是：
  - 处理前 `ip_grade` 为空，处理后不为空，记 1 条 IP 分析。
  - 处理前 `material_analysis` 为空，处理后不为空，记 1 条材质分析。
  - 复用已有历史分析、材质预筛、真实 MiniMax 分析，只要最终补齐字段，都会被统计。

日志字段：
- `timestamp`：写入时间。
- `date`：统计日期。
- `event`：来源任务，例如 `ai_daemon_cycle`。
- `delta`：本轮增量。
- `totals`：当天累计。
- `detail`：本轮原始 summary，方便排查每个 source 的处理量。

用户如何查看：
- 重启 Flask 后端后，后台任务每轮结束会自动写入：
  `D:\choice_product\logs\system_daily_stats.jsonl`
- 控制台也会出现：
  `[SYSTEM_DAILY_STATS] ... delta=... totals=...`

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 结果：通过。

风险边界：
- 本次不改数据库结构。
- 本次不改变 AI 分析、插画判断、选品分的原有处理逻辑，只增加统计记录。
### 2026-06-13：POD 插画提取接入 MiniMax 图生图 API

修改目标：
- 将 POD 的“提取插画”从只创建任务，升级为真正调用 MiniMax `image_generation` 图生图接口。
- 用户点击可提取商品的“提取插画”后，系统生成插画图片，保存到本地静态目录，并把结果 URL 写回数据库。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- 新增配置：
  - `MINIMAX_IMAGE_GENERATION_URL`，默认 `https://api.minimax.io/v1/image_generation`
  - `MINIMAX_IMAGE_MODEL`，默认 `image-01`
  - `MINIMAX_IMAGE_SUBJECT_TYPE`，默认 `character`
- 新增本地结果目录：
  - `D:\choice_product\static\generated\illustrations`
  - 前端访问前缀：`/static/generated/illustrations`
- 新增函数：
  - `build_illustration_generation_prompt(title="", style_prompt="")`
  - `call_minimax_illustration_generation(image_value, title="", style_prompt="", task_id="illustration")`
- 图生图调用方式：
  - 使用 `POST https://api.minimax.io/v1/image_generation`
  - `model=image-01`
  - `aspect_ratio=1:1`
  - `subject_reference=[{"type": MINIMAX_IMAGE_SUBJECT_TYPE, "image_file": ...}]`
  - `response_format=base64`
- 参考图处理：
  - 如果商品图是公网 URL，直接传 URL。
  - 如果商品图是数据库 base64，转成 `data:image/jpeg;base64,...` 传给 MiniMax。
  - 这样避免 MiniMax 无法访问本地 `192.168.../image` 登录接口的问题。
- `/api/products/pod_cross_category/<product_id>/extract` 修改：
  - 先检查 `illustration_extractable == 1`，未通过可提取判断的商品不允许生成。
  - 如果已有 `illustration_result_url`，直接返回已有结果，不重复生成。
  - 创建 `pod_illustration_task` 后，将商品 `illustration_status` 置为 1。
  - 调用 MiniMax 成功后：
    - 保存图片到 `static/generated/illustrations/{task_id}.jpeg`
    - 更新 `pod_illustration_task.status = 2`
    - 更新 `pod_illustration_task.result_image_url`
    - 更新 `pod_cross_category_product.illustration_status = 2`
    - 更新 `pod_cross_category_product.illustration_result_url`
  - 调用失败后：
    - 更新任务 `status = 3`
    - 写入 `error_msg`
    - 更新商品 `illustration_status = 3`

生成提示词核心要求：
- 从商品图中提取可用插画、图案、纹理或装饰元素。
- 生成适合 iPhone 17 手机壳印刷的干净设计素材。
- 去除商品本体、透视、阴影、水印、品牌 Logo、商标文字、广告文字。
- 保留非品牌的可复用装饰图案。
- 白色背景、高清、边缘清晰，不生成手机壳 mockup，不添加文字。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 结果：通过。

用户如何看到：
- 重启 Flask 后端。
- 进入 POD 跨品类列表。
- 等自动插画可提取判断把商品标为“可提取”。
- 点击“提取插画”。
- 成功后商品状态变为已提取，并出现可查看的 `illustration_result_url`。

风险边界：
- 本次只接入 MiniMax 图生图生成插画素材。
- 暂未实现“插画贴到 iPhone 17 手机壳 mockup 上”的第二个按钮。
- 如果 MiniMax 不接受 `data:image/...;base64,...` 形式的 `image_file`，需要后续改为把参考图上传到可公网访问的对象存储/CDN 后再传 URL。

补充修正（2026-06-13）：
- `tools/analyze_single_product.py`
  - `resolve_minimax_api_key()` 兼容读取 `MINIMAX_API_KEY` 和 `.env` 中的小写 `minimax_api_key`。
  - 如果环境变量里误写了 `Bearer xxx`，会自动去掉 `Bearer ` 前缀，避免请求头变成 `Bearer Bearer xxx`。
- `app.py`
  - `call_minimax_illustration_generation(...)` 增加 `n=1`，与 MiniMax 图生图接口参数保持一致。
  - 图生图返回解析不再只认 `data.image_base64`，同时兼容 `image_base64s`、`image_urls`、`image_url`、`images`、`urls`。
  - 如果接口返回 URL，后端会下载图片并保存到 `static/generated/illustrations/`，前端仍使用本地静态图地址。
- 注意：
  - API Key 不硬编码进代码，只放在 `.env` 或系统环境变量中。
  - 这次修改只影响 POD 插画生成接口，不影响 IP 分析、材质分析、商品列表和选品分。

补充修正（2026-06-13，提取接口可观测日志）：
- `app.py`
  - `/api/products/pod_cross_category/<product_id>/extract` 增加 `[POD_EXTRACT]` 日志。
  - 日志节点包括：收到请求、商品不存在、不可提取拒绝、已有结果、创建任务、调用 MiniMax、成功、失败。
  - `call_minimax_illustration_generation(...)` 增加 `[MINIMAX_IMAGE]` 日志。
  - 日志节点包括：发起 MiniMax 图生图请求、参考图类型（url/base64）、MiniMax 响应状态和 `data` 字段列表。
- 排查方法：
  - 点击“提取/重试提取”后，如果后台没有 `[POD_EXTRACT] request`，说明前端没有真正请求后端。
  - 如果有 `[POD_EXTRACT] request` 但没有 `[MINIMAX_IMAGE] request`，说明被可提取性、已有结果或数据库查询逻辑挡住。
  - 如果有 `[MINIMAX_IMAGE] request` 但失败，看 `[POD_EXTRACT] failed` 的错误内容。

补充修正（2026-06-13，修复“未判断 + 提取失败”矛盾状态）：
- 问题表现：
  - POD 列表中出现同一商品同时显示 `提取失败` 和 `未判断`。
- 原因：
  - `提取失败` 来自 `illustration_status=3`。
  - `未判断` 来自 `illustration_extractable IS NULL`。
  - 两个字段表达不同阶段：前者是图生图提取任务状态，后者是系统自动判断是否值得提取插画。
  - 之前为修复“提取中卡死”增加了超时重置逻辑，会把卡住的 `illustration_status=1` 标记为 `3`；历史数据中存在未完成可提取性判断的记录，因此出现矛盾展示。
- 代码修复：
  - `app.py` 的 `reset_stale_pod_illustration_tasks()` 收窄条件：只有 `illustration_extractable = 1` 的商品才允许被超时重置为 `提取失败`。
  - `static/index.html` 的 `illustrationStatusBadge(...)` 增加“未判断优先”展示保护：当显式传入 `illustration_extractable` 且为空时，前端按 `待处理` 展示，不再显示 `提取失败`。
- 数据修复：
  - 已将历史脏数据 `illustration_status=3 AND illustration_extractable IS NULL` 的 4 条记录改回 `illustration_status=0`。
- 验证：
  - `D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py` 通过。
### 2026-06-13：跨品类顶部增加 POD 状态筛选

修改目标：
- 在跨品类数据页面顶部筛选栏增加一个直接可见的 `POD状态` 下拉筛选。
- 用户不用再进入“添加筛选条件”才能筛 POD 状态。

涉及文件：
- `static/index.html`
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- `static/index.html`
  - 顶部筛选栏新增 `topPodStatusFilter`。
  - 下拉项包括：
    - 全部
    - 待处理
    - 提取中
    - 已提取
    - 提取失败
    - 已废弃
  - 新增 `topPodStatus -> pod_status` 参数映射。
  - 新增 `syncPodTopFilters()`：
    - 当前 source 为 `pod_cross_category` 时显示顶部 POD 状态筛选。
    - 切换到统一表、fastmoss、kalodata 时隐藏并清空该筛选。
  - `topPodStatus` change 后自动刷新列表和统计。
- `app.py`
  - 后端 `build_filters()` 中 `pod_status` 过滤限定为只对 `pod_cross_category_product` 生效，避免普通表误带参数时被过滤。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 结果：通过。

用户如何看到：
- 重启 Flask 后端并刷新前端。
- 点击左侧 `跨品类数据`。
- 顶部筛选栏会显示 `POD状态` 下拉框。
- 选择状态后会自动刷新商品列表。
### 2026-06-13：修复 POD 插画提取任务卡在“提取中”

问题现象：
- 跨品类列表中多个商品长时间显示 `提取中`。
- 页面按钮一直禁用，用户无法重试。

排查结果：
- 数据库中 `pod_cross_category_product.illustration_status = 1` 的商品有 4 条。
- 对应 `pod_illustration_task.status` 仍为 0。
- 原因是创建任务时任务表状态写入为 0，但商品表状态已经写为 1，导致前端一直按商品状态显示“提取中”。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- `/api/products/pod_cross_category/<product_id>/extract` 创建 `pod_illustration_task` 时，`status` 从 0 改为 1。
- 这样任务表和商品表都会进入“处理中”状态，后续成功/失败可以正确回写。

数据修复：
- 已将当前卡住的 4 条任务从 `status=0` 改为 `status=3`。
- 已将对应商品从 `illustration_status=1` 改为 `illustration_status=3`。
- 前端刷新后这些商品会显示“提取失败”，按钮会变成可点击的“重试提取”。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 结果：通过。
- 已复查数据库：
  - `pod_cross_category_product` 当前无 `illustration_status=1`
  - `pod_illustration_task` 当前无 `status=0`

用户如何看到：
- 重启 Flask 后端。
- 刷新跨品类数据页面。
- 原本卡住的“提取中”会变为“提取失败”，可点击重试。

补充修复：
- 新增 `POD_ILLUSTRATION_STALE_MINUTES`，默认 10 分钟。
- 新增 `reset_stale_pod_illustration_tasks()`：
  - 自动把超过时间仍处于 `pod_illustration_task.status IN (0, 1)` 的任务标记为失败。
  - 同步将对应 `pod_cross_category_product.illustration_status = 1` 的商品标记为失败。
- 在 `/api/products` 和 `/api/products/stats` 访问跨品类数据时自动执行一次卡住任务清理。
- 已再次清理当前数据库中的卡住任务：
  - `pod_illustration_task` 当前无 `status=0/1`
  - `pod_cross_category_product` 当前无 `illustration_status=1`
### 2026-06-13：优化 POD 插画可提取性自动判断吞吐
修改目标：
- 解决跨品类列表中大量商品长期显示“未判断”的问题。
- 在已有联合索引基础上，让插画可提取性判断后台真正提升吞吐，而不是继续 5 分钟串行处理 30 条。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

问题原因：
- 原逻辑 `ILLUSTRATION_DAEMON_BATCH_SIZE=30`、`ILLUSTRATION_DAEMON_INTERVAL_SECONDS=300`，并且 `run_illustration_daemon_once()` 对每条商品串行调用视觉判断。
- 插画判断真正慢的主要不是读库，而是每条商品都要调用视觉模型；串行执行时，即使数据库很快，页面也会长时间看到“未判断”。
- POD 队列查询条件是“最新日期 + illustration_extractable IS NULL + id 排序”，原表没有专门覆盖这个队列的索引。

具体改动：
- `ILLUSTRATION_DAEMON_INTERVAL_SECONDS` 默认从 `300` 秒改为 `120` 秒。
- `ILLUSTRATION_DAEMON_BATCH_SIZE` 默认从 `30` 改为 `120`，并设置最小值 `20`。
- 新增 `ILLUSTRATION_DAEMON_CONCURRENCY`，默认 `4`，最大 `8`。
- `run_illustration_daemon_once()` 改为 `ThreadPoolExecutor` 并发执行 `process_single_illustration_check()`。
- 插画判断启动日志新增 `concurrency=...`，每轮 summary 新增 `failed`。
- `ensure_pod_cross_category_tables()` 自动检查并补充 POD 判断队列索引：
  `idx_pod_illustration_queue(date_record, illustration_extractable, id)`。

用户如何看到：
- 重启 Flask 后端。
- 启动日志应显示：
  `[ILLUSTRATION_DAEMON] started sources=pod_cross_category batch_size=120 interval=120s concurrency=4 latest_only=True`
- 每轮日志中的 `queued/processed` 应比之前更快增长。
- 跨品类列表中“未判断”会按后台判断速度逐步变成“可提取”或“不展示不可提取商品”。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 结果：通过。

风险边界：
- 本次不修改 MiniMax 判断 prompt。
- 本次不修改图生图提取接口。
- 本次不修改其他商品表的前端展示逻辑。
- 如果 MiniMax API 本身限流，可以通过环境变量临时降低 `ILLUSTRATION_DAEMON_CONCURRENCY`。

### 2026-06-15：修正 POD 可提取判断、前端展示过滤与 MiniMax 502 代理问题

修改目标：
- 让 POD 跨品类前端只展示已经判断为“可提取”的商品，不再展示“未判断”和“不可提取”商品。
- 修正插画可提取性判断过宽的问题，避免把普通卫衣、拉链、帽绳、口袋、缝线、纯色面料、普通纹理等商品本体误判为可提取图案。
- 将 POD 插画可提取判断改为复用 `tools/analyze_single_product.py` 中 IP 分析/材质分析已经验证过的 MiniMax 图片识别调用方式。
- 排查并修复 `/api/products/pod_cross_category/<product_id>/extract` 返回 502 的问题。

涉及文件：
- `app.py`
- `tools/analyze_single_product.py`
- `PROJECT_MEMORY_ARCHIVE.md`

具体改动：
- `app.py`
  - `build_filters()` 对 `pod_cross_category_product` 增加固定过滤：`illustration_extractable = 1`。
  - 结果是 POD 跨品类列表和统计只返回“可提取”商品；`illustration_extractable IS NULL` 的未判断数据、`illustration_extractable = 0` 的不可提取数据不再进入前端列表。
  - `analyze_illustration_extractability()` 改为调用 `call_product_vision_minimax(prompt, image_value)`，并用 `parse_product_json_response()` 解析结果，和 IP/材质分析共用同一套图片识别入口。
  - 插画判断 prompt 增加硬性排除规则：不能把商品本体、服装版型、包型结构、拉链、帽绳、口袋、缝线、普通面料纹理当成可提取图案。
  - 判断标准收紧为：只有清晰的非品牌装饰图案、插画、重复印花、动物/植物/几何/骷髅/节日元素、刺绣图案等才允许判定为可提取。
  - 普通纯色服装、纯色包、版型结构、拉链帽绳口袋、褶皱阴影、针织/罗纹/绗缝/摇粒绒等普通面料质感、品牌 Logo、商标、广告文字、小而模糊或遮挡严重的图案，都应判为不可提取。
  - 本地兜底判断的正向词收窄，移除泛化过强的 `shirt`、`hoodie`、`sweatshirt`、`crewneck`、`letter` 等词，避免普通服装标题触发可提取。
  - 本地兜底判断新增负向词：`plain`、`solid`、`minimalist`、`basic`、`ribbed`、`knit`、`zip`、`zipper`、`drawstring`、`pocket`、`pockets`、`fleece`、`pullover`、`oversized`、`puffy`、`quilted`。
  - MiniMax 图生图请求 `call_minimax_api_with_retry()` 改为使用 `requests.Session()` 且 `session.trust_env = False`，避免读取系统代理。
  - 下载 MiniMax 返回的图片 URL 时同样使用 `session.trust_env = False`。
- `tools/analyze_single_product.py`
  - 引入 `httpx`。
  - `call_minimax()` 初始化 OpenAI 客户端时传入 `http_client=httpx.Client(trust_env=False)`，使 IP 分析、材质分析、POD 插画判断共用的 MiniMax 图片识别调用都不再读取系统代理。

数据处理：
- 已将 MiniMax 调用失败导致的“检查失败:%”旧记录从不可提取状态重置回未判断，避免接口失败被当成真实不可提取。
- 已将用户人工确认的 3 个 PID 标记为可提取：
  - `1729736965019177472`
  - `1730285344755257600`
  - `1731320050892509696`
- 已按新规则重置一批由旧本地兜底或不符合文档要求导致的误判可提取记录，例如品牌、Logo、商标、国旗、徽章、绗缝、结构线条等不应作为手机壳素材的记录。

502 排查结论：
- 失败接口：`POST /api/products/pod_cross_category/1729398920156844544/extract`。
- 数据库中该商品已是 `illustration_extractable = 1`，不是因为不可提取被拒绝。
- `pod_illustration_task.error_msg` 显示 MiniMax 请求失败原因是代理错误：请求 `api.minimax.io:443` 时走到了 `127.0.0.1:9`，连接被拒绝。
- 因此 502 根因是后端请求 MiniMax 图生图接口时读取了错误的系统代理，不是商品判断逻辑，也不是商品图片本身。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py tools\analyze_single_product.py`
- 结果：通过。
- 已重启 Flask 服务。
- 已验证本地入口：`GET http://127.0.0.1:5000/` 返回 `200`。
- 当前 5000 端口监听进程为新启动进程，旧的 Flask 进程已停止。

用户如何看到：
- 刷新跨品类/POD 数据页面后，列表不再展示“未判断”和“不可提取”的商品，只保留 `illustration_extractable = 1` 的商品。
- 对已经可提取的商品点击“提取/重试提取”，后端会使用修复后的 MiniMax 请求逻辑，不再走 `127.0.0.1:9` 代理。
- 如果再次返回失败，需要查看新的 `[POD_EXTRACT] failed` 或 `pod_illustration_task.error_msg`，那时再按 MiniMax 真实业务错误继续排查。

风险边界：
- 本次没有新增数据库字段。
- 本次没有改变 IP 分析和材质分析的业务 prompt，只改变其 MiniMax 客户端的代理读取行为。
- POD 前端列表现在只看可提取数据，未判断数据仍会留在数据库中等待后台判断，但不会展示给用户。
- 普通纯色衣服、纯色拉链卫衣、无明确装饰图案的包不应再被判断为可提取；如果用户认为某个具体商品可尝试，需要人工确认或进一步调整判断规则。

补充修正（2026-06-15，提取接口生成成功但质检误判导致 502）：
- 问题表现：
  - `/api/products/pod_cross_category/<product_id>/extract` 日志中 MiniMax 图生图接口已经返回 `data_keys=['image_base64']`。
  - 但前置 artwork description 阶段返回“请提供您想要分析的参考图片”，后置质检阶段得分为 0，最终接口返回 502。
- 原因：
  - 图生图接口本身已经上传并使用了参考图，日志 `reference=base64` 和返回 `image_base64` 可以证明这一点。
  - 出问题的是前置描述和后置质检使用的旧 `chatcompletion_v2` 手写多模态 payload；该接口对 `data:image/...;base64` 形式图片识别不稳定，导致模型以为没有上传图片。
  - 这不是 MiniMax image-to-image 官方接口 `subject_reference` 没传图；当前图生图入参字段为 `subject_reference: [{"type": ..., "image_file": ...}]`，与官方文档方向一致。
- 代码修复：
  - `generate_artwork_description()` 不再手写 `chatcompletion_v2` payload，改为复用 `call_product_vision_minimax(prompt, image_url)`，与 IP 分析、材质分析、POD 可提取判断使用同一套已验证的图片识别入口。
  - `call_minimax_illustration_generation()` 中，如果 MiniMax 图生图已经返回图片，但多轮质检都未通过，不再抛出 `插画生成质检未通过` 导致 502，而是保存最佳生成图并返回结果 URL。
  - 日志会输出：`all attempts failed ... saving best generated image`，用于标记这是“质检未通过但保留最佳图”的降级路径。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。

补充修正（2026-06-15，MiniMax 视觉模型默认值修正并端到端自测）：
- 问题表现：
  - `image_description` 多次返回“没有看到图片”。
  - 质检多次返回“未检测到图像数据”。
  - 图生图接口本身可以返回 `image_base64`，但第一阶段描述和第三阶段质检无法稳定看图。
- 根因：
  - 项目默认 `MINIMAX_MODEL` 为 `MiniMax-M2.7`。
  - 对同一张 base64 商品图直接测试 `/chat/completions` 后确认：`MiniMax-M2.7`、`image_url` object、`image_url` string、`image_base64` 三种入参都回复“没有看到图片”。
  - 同样图片切换到 `MiniMax-Text-01` 后可以识别图像，例如识别出灰色卫衣、黑猫、女巫帽和南瓜。
  - 因此问题不是数据库没图，也不是 base64 格式错误，而是视觉描述/质检用错了不看图的模型。
- 代码修复：
  - `tools/analyze_single_product.py` 中 `MINIMAX_MODEL` 默认值从 `MiniMax-M2.7` 改为 `MiniMax-Text-01`。
  - 该改动影响复用 `call_product_vision_minimax()` 的 IP/材质分析、POD 可提取判断、`image_description` 和质检视觉判断。
- 端到端自测：
  - 商品 `1729398920156844544`：第一阶段成功生成 `image_description`，识别黑猫、南瓜、星星；图生图第一次返回 1033 后自动重试，第二次返回 `image_base64`；质检返回 `match_score=95`；保存结果 `/static/generated/illustrations/manualtest3_1729398920156844544_1781520520673.jpeg`。
  - 商品 `1729432197146579968`：第一阶段识别 tactical webbing；图生图一次返回 `image_base64`；质检返回 `match_score=95`；保存结果 `/static/generated/illustrations/manualtest3_1729432197146579968_1781520586772.jpeg`。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py tools\analyze_single_product.py`
  - 已直接调用 `call_product_vision_minimax()` 验证 `MiniMax-Text-01` 能看见商品图。
  - 已直接调用 `call_minimax_illustration_generation()` 完成两张商品图端到端生成和质检。

补充修正（2026-06-15，生成图含三维商品结构时强制低分）：
- 问题表现：
  - 用户反馈生成结果仍出现背包商品图，包含书包带子、包体、拉链、透视和阴影。
  - 该类结果不是用户需要的纯二维平面素材。
- 代码修复：
  - 新增 `detect_generated_3d_artifacts()`，在左右对比质检前，先单独审查生成图本身是否含有任何三维商品形态。
  - 禁止元素包括：背包、书包、手提包、包体轮廓、衣服、手机壳样机、手柄、书包带子、肩带、背带、拉链、拉链头、口袋、缝线、五金、扣具、厚度、折痕、褶皱、透视、阴影、摄影棚光影、桌面、背景场景。
  - 如果生成图出现上述任何元素，`evaluate_generated_illustration()` 直接返回 `score=20`、`pass=false`，不会进入 85 分通过逻辑。
  - 规则含义：任何跟三维商品结构有关的内容都必须低于 80 分。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 已用用户提供的背包生成图 `C:\Users\admin\AppData\Local\Temp\codex-clipboard-ce98cbe5-085b-45cd-98b9-d29019244486.png` 调用 `detect_generated_3d_artifacts()`。
  - 返回：`forbidden_3d=true`，原因：图片中出现背包、包体轮廓、肩带、拉链等三维结构元素。

### 2026-06-16：修复 WEBP 商品图被伪装成 JPEG 导致 MiniMax 图生图 1033

问题表现：
- 商品 `1729450215760696320` 的提取日志中，`image_description` 可以生成花卉描述，但 MiniMax 图生图连续 3 次返回空 `data_keys=[]`，`base_resp.status_code=1033 system error`。
- 商品 `1732011246795854592` 的第一阶段视觉描述偶发 `NEED_RETRY_IMAGE_NOT_VISIBLE`，但最终任务可成功生成。
- 日志中两个任务并发执行，`173201...` 与 `172945...` 的日志交织，容易误以为同一个任务串线；实际是两个不同 task_id 同时运行。

根因：
- 数据库中这两张商品图实际是 `WEBP`：
  - `1729450215760696320`：PIL 识别为 `WEBP (1350, 1800)`，base64 前缀 `UklG...`。
  - `1732011246795854592`：PIL 识别为 `WEBP (1600, 1600)`，base64 前缀 `UklG...`。
- 旧代码对所有裸 base64 都直接拼成 `data:image/jpeg;base64,...`。
- 也就是说实际传给 MiniMax 的是“WEBP 数据 + JPEG MIME”，视觉接口有时容忍，图生图接口容易返回 `1033 system error`。

代码修复：
- `app.py`
  - `normalize_vision_image_url()` 不再直接给裸 base64 加 JPEG 前缀。
  - 会先用 PIL 解码图片，再统一转成真实 JPEG bytes，最后输出 `data:image/jpeg;base64,...`。
  - 该修复覆盖 POD 图生图、image_description、质检、三维结构审查等 app 内视觉链路。
- `tools/analyze_single_product.py`
  - `normalize_image_data()` 同步改为 PIL 解码后转真实 JPEG data URL。
  - 该修复覆盖 IP 分析、材质分析、POD 可提取判断等共享视觉入口。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py tools\analyze_single_product.py`
- 已验证两个 WEBP 商品图经 `normalize_vision_image_url()` 后输出真实 JPEG：
  - `1729450215760696320` -> `data:image/jpeg;base64,...`，PIL 识别 `JPEG (1350, 1800)`。
  - `1732011246795854592` -> `data:image/jpeg;base64,...`，PIL 识别 `JPEG (1600, 1600)`。
- 已端到端复测 `1729450215760696320`：
  - 第一阶段生成 floral pattern 的 `image_description`。
  - MiniMax 图生图 attempt=1 返回 `data_keys=['image_base64']`，不再出现 1033。
  - 三维审查返回 `forbidden_3d=false`。
  - 质检返回 `match_score=90`。
  - 保存结果 `/static/generated/illustrations/manualjpeg_1729450215760696320_1781572772624.jpeg`。

风险边界：
- 转 JPEG 会让传给 MiniMax 的图片体积变大，但格式更稳定。
- 如果后续遇到透明 PNG，当前会转成 RGB JPEG，透明区域会被合成到默认背景；如需要保留透明，需要单独处理。

### 2026-06-16：POD 提取语义调整为“抠图/图案提取”并增加高置信本地抠图

修改目标：
- 用户明确该能力更准确的叫法是“抠图/图案提取”，不是“AI 生图”。
- 目标是从商品图中提取已有插画/印花/图案，而不是让 AI 重新创作一个相似图片。

代码策略：
- `call_minimax_illustration_generation()` 现在先尝试 `local_cutout_print_artwork()` 做本地代码抠图。
- 本地抠图只作为高置信路径：检测深色/高饱和“墨色”区域，输出透明 PNG。
- 如果本地抠图疑似选中了大块商品本体或整件衣服/包，会放弃本地结果，降级到 MiniMax 严格 prompt 路径。
- 新增 `save_extracted_artwork_png_bytes()`，本地抠图结果保存为 PNG，避免透明背景丢失。

防误扣规则：
- 如果本地抠图 bbox 接近整张图，拒绝。
- 如果 bbox 超过图片宽/高 48% 且覆盖率超过 2%，认为可能选中商品本体，拒绝。
- 用户提供的衣服示例图只是需求示例，不用于过拟合调参；经验证该图会被本地抠图拒绝：`本地抠图疑似选中大块商品区域`。

风险边界：
- 纯代码抠图适合边界清晰、墨色/高饱和印花明显的图案。
- 对复杂衣服褶皱、浅色印花、同色系布料，本地抠图容易误扣或漏扣，因此只做高置信路径。
- 低置信时仍走 MiniMax，但 prompt 语义应持续按“提取/抠取原图案”而不是“重新生成图案”维护。

验证方式：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 已用用户提供的示例衣服图测试，本地抠图拒绝整件衣服区域。

补充修正（2026-06-16，大面积胸前图案本地抠图改为保守兜底）：
- 问题表现：
  - 商品 `1731041924962947584` 是白色上衣上的大面积胸前印花。
  - 本地抠图能检测到大 bbox，但调试图仍可能混入衣服/人物照片区域，不能直接保存为合格抠图。
  - 同时该商品已有旧成功结果，后续重新提取失败不应把商品状态打成失败。
- 代码修复：
  - `local_cutout_print_artwork()` 对 bbox 超过图片宽/高 48% 的大面积结果改为拒绝：`本地抠图大面积图案置信不足，交给AI/已有结果兜底`。
  - 提取接口异常处理增加已有结果保护：如果重新提取失败但商品已有 `illustration_result_url`，新任务写为 status=2 并返回已有结果，不再返回 502，也不把商品状态改成失败。
  - 本地抠图结果即使产出，也会先经过 `detect_generated_3d_artifacts()`；三维/商品照片审查失败则降级，不保存。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 已验证 `1731041924962947584` 本地抠图会被拒绝并进入 AI/已有结果兜底。

补充修正（2026-06-15，质检接口未检测到图像数据时重试）：
- 问题表现：
  - 质检返回 `{"match_score": 0, "reason": "未检测到图像数据，无法进行图案匹配评估"}`。
  - 这表示质检视觉接口没有识别到 QC 对比图，不代表生成图真实匹配度为 0。
- 代码修复：
  - `is_missing_image_response()` 新增中文缺图标记：`未检测到图像数据`。
  - `evaluate_generated_illustration()` 增加 `comparison_image_len` 日志，用于确认拼接后的质检对比图不是空数据。
  - `evaluate_generated_illustration()` 增加最多 3 次质检重试，日志格式为 `qc_attempt=1/2/3 qc_raw=...`。
  - 如果三次后仍没有 `match_score` 或仍未识别到图片，feedback 会写入 `质检返回未包含match_score或未识别到图片: ...`。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。

补充修正（2026-06-15，image_description 缺图重试与防幻觉）：
- 问题表现：
  - `image_description` 偶发返回 `I don't see an image attached... Please try uploading...`，说明第一阶段视觉描述接口没有识别到图片。
  - 随机抽查多张图时，描述结果集中出现“花、玫瑰、植物”等元素，但原图未必有花，说明 prompt 示例词和模型补全导致了幻觉。
- 代码修复：
  - 新增 `is_missing_image_response()`，识别英文/中文的缺图提示，例如 `don't see an image`、`no image attached`、`please upload`、`无法看到图片`、`请提供图片`。
  - `generate_artwork_description()` 增加最多 3 次重试：如果模型返回缺图提示或 `NEED_RETRY_IMAGE_NOT_VISIBLE`，不会进入图生图，而是重新请求视觉描述。
  - 第一阶段描述 prompt 移除“花朵、动物、几何、抽象纹样等”这种示例诱导，改为“只能描述图片中真实可见的元素，不能根据品类或示例自行补花朵、动物、植物、几何等不存在的内容”。
  - 第一阶段 user_text 增加：不要发散、不要补花朵或其他未出现的元素；如果没有收到图片或看不清图案，只输出 `NEED_RETRY_IMAGE_NOT_VISIBLE`。
  - `sanitize_artwork_description()` 兼容新的 `A perfectly flat 2D seamless print pattern asset...` 开头，避免清洗时截断失败。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。

补充修正（2026-06-15，强制插画提取结果为纯二维平面素材）：
- 问题表现：
  - 用户反馈生成结果仍可能出现包、背包、手柄、肩带、透视、阴影等三维商品图，而用户需要的是可用于手机壳印刷的纯平面图案素材。
- 提示词放置策略：
  - 第一阶段 `generate_artwork_description()`：要求视觉模型只描述“可印刷二维图案”，不要把包型、衣服版型、手柄、拉链、五金、阴影、透视等商品本体写进图片描述 prompt。
  - 第二阶段 `build_illustration_generation_prompt()`：这是最关键位置，直接控制 MiniMax image-to-image 输出；因此把 `ABSOLUTE OUTPUT REQUIREMENT` 放在最终 prompt 最前面，强制只生成 flat 2D print pattern asset。
  - 第三阶段 `evaluate_generated_illustration()`：质检时如果右侧生成图仍有包、背包、手提包、衣服、手机壳样机、手柄、肩带、拉链、口袋、五金、商品轮廓、透视、阴影或任何 3D 体积，直接判为不合格，`match_score` 最高不超过 20。
- 代码修复：
  - 第一阶段描述 prompt 首句要求以 `A perfectly flat 2D seamless print pattern asset...` 开头。
  - 第二阶段最终图生图 prompt 开头新增 `ABSOLUTE OUTPUT REQUIREMENT: Generate ONLY a perfectly flat 2D print pattern asset...`。
  - 第二阶段明确禁止：bag、backpack、tote、purse、shirt、hoodie、model、mannequin、phone case mockup、product silhouette、handles、straps、zipper、pockets、seams、hardware、folds、wrinkles、leather shine、fabric depth、perspective、shadows、studio lighting、table、room、3D volume。
  - 第三阶段质检只有在右侧是纯二维平面图案且图案相似时，才允许给 85 分以上。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。
- 风险边界：
  - 本次没有改变 MiniMax image-to-image 的官方请求字段。
  - 本次主要解决“生成成功但质检链路看不到图”导致的误报失败。
  - 保存最佳图可能会让少量质量一般的生成图进入结果，需要后续在前端或人工复核时筛掉。

补充修正（2026-06-15，恢复质检阈值约束并打印图片描述 prompt）：
- 问题表现：
  - 上一版为了避免“生成成功但质检看不到图”直接 502，曾在多轮质检都未通过时保存最佳生成图。
  - 该降级逻辑会绕过“生成图片必须达到匹配度阈值才算成功”的原始需求。
- 代码修复：
  - `MINIMAX_ILLUSTRATION_PASS_SCORE` 默认值改为 `85`，仍可通过环境变量覆盖。
  - `call_minimax_illustration_generation()` 恢复严格质检：多轮生成都低于阈值时，不保存为成功结果，继续抛出 `插画生成质检未通过`。
  - 第一阶段 AI 根据商品图生成的英文图片描述 prompt 会打印到后端日志，前缀固定为 `image_description`，格式示例：
    `image_description task_id=pod_xxx: A detailed, high-resolution photo...`
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。
- 风险边界：
  - 如果质检接口本身仍无法识别图片，接口仍可能返回质检失败；但不会把未达标图片误写为成功结果。
  - 后续若要区分“质检接口异常”和“真实匹配度不足”，需要把质检失败类型单独入库或返回给前端。

补充修正（2026-06-15，解释质检最佳评分为 0 并优化质检链路）：
- 问题表现：
  - 日志显示 MiniMax 图生图每轮都返回 `data_keys=['image_base64']`，说明生成接口有返回图片。
  - 但每轮 `[MINIMAX_IMAGE_QC] attempt=... score=0.0 feedback=`，最佳评分为 0 且原因为空。
- 原因：
  - 评分为 0 不是因为没有继续生图；三轮生图已经执行。
  - 评分为 0 的直接原因是质检接口返回内容没有被解析出 `match_score`，代码按默认值落到 0。
  - 旧质检仍使用 `chatcompletion_v2` 手写双图 payload，同时传原图和生成图两个 `data:image/...;base64`，该路径对多图/base64 识别不稳定。
  - 第一阶段 `image_description` 还暴露出另一个问题：视觉模型返回内容带 `<think>...</think>` 和 `--v 6 --ar 1:1` 等 Midjourney 参数，直接传给 MiniMax 图生图会污染 prompt。
- 代码修复：
  - 新增 `sanitize_artwork_description()`，清理 `<think>`、Markdown 代码块和 `--v/--ar/--style/--s/--q` 等参数，只保留真正的英文图片描述 prompt。
  - 新增 `build_qc_comparison_image()`：把原商品图和生成图拼成一张左右对比图，再交给视觉模型评估。
  - `evaluate_generated_illustration()` 不再走旧的 `chatcompletion_v2` 双图 payload，改为复用 `call_product_vision_minimax()` 对单张对比图打分。
  - 质检日志新增 `qc_raw`，打印模型原始质检返回，便于后续判断是解析失败、模型没看懂图，还是生成图真实不达标。
  - 如果质检返回不包含 `match_score`，feedback 会写入 `质检返回未包含match_score: ...`，不再出现空 feedback。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。

补充修正（2026-06-16，碎片残渣/无有效主体的提取结果强制低分）：
- 问题表现：
  - 用户反馈部分提取结果是大面积空白灰底，只在边角残留极少线条碎片。
  - 另一些结果只剩 logo、铆钉、五金点、包边线等零散残留，没有形成可用于印刷的完整平面图案主体。
  - 这类结果既不是合格抠图，也不是可用二维印花素材，必须低于 80 分。
- 代码修复：
  - 新增 `detect_low_value_flat_artifact()`，在视觉质检前先用图像算法检查生成图/抠图结果是否缺少有效图案主体。
  - 检测维度包括：有效图案覆盖率、主体 bbox 占比、主体是否偏在角落、连通组件数量、最大组件占比。
  - 对截图黑边、白/灰背景做背景差异过滤，避免把画布背景误认为有效图案。
  - 如果结果是“有效图案面积过小”“主体占画布太小”“主体偏角且面积不足”“过于碎片化”“碎片残渣而非完整图案”，直接返回 `score=20`、`pass=false`。
  - 本地抠图保存前也会先经过该低价值检查；如果只抠到文字、五金点、边缘残渣，则拒绝保存并降级到 MiniMax/已有结果兜底。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 用户提供的空白灰底碎片图返回：`生成图有效图案面积过小 coverage=0.0083`。
  - 用户提供的 logo/铆钉/五金点残留图返回：`生成图过于碎片化 components=95 largest_ratio=0.178`。
  - 用户提供的黑色卫衣文字图本地抠图被拒绝：`本地抠图图案像素过少 coverage=0.0009`。
  - 用户提供的棕色包源图本地抠图被拒绝：`本地抠图大面积图案置信不足，交给AI/已有结果兜底`。

补充修正（2026-06-16，高置信大面积文字/印花允许本地抠图，避免 MiniMax 1033 阻断）：
- 问题表现：
  - 商品 `1729491397801252352` 的第一阶段视觉描述正常，识别出 `jesus is for everybody` 文字和小十字图案。
  - 本地抠图已检测到较完整图案区域：`bbox=679x566 coverage=0.0784`。
  - 旧规则因为 bbox 超过图片宽/高 48%，直接判定“大面积图案置信不足”，强制降级到 MiniMax。
  - MiniMax `image-01` 连续 3 次返回 `status_code=1033 system error` 且无图片数据，最终接口 502。
- 根因：
  - “大 bbox 一律拒绝”的规则过于保守。
  - 对胸前大字、大面积图案、居中印花来说，bbox 大是正常现象，不等于抠到了整件衣服。
  - 应该结合覆盖率、组件数量、最大主体占比判断是否为高置信图案主体。
- 代码修复：
  - `local_cutout_print_artwork()` 对大 bbox 增加 `large_print_confident` 例外。
  - 大 bbox 只有同时满足以下条件才允许本地抠图保存：
    - `0.045 <= coverage <= 0.14`
    - `meaningful_components <= 30`
    - `largest_component_ratio >= 0.35`
  - 不满足时仍拒绝，并在日志中打印 `components` 与 `largest_ratio`。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 商品 `1729491397801252352` 本地抠图成功：`coverage=0.0784 bbox=679x566`。
  - 该抠图通过低价值检测：`平面图案有效 coverage=0.0895 bbox_ratio=0.6245`。
  - 用户提供的棕色包源图仍被拒绝：`coverage=0.0275 components=43 largest_ratio=0.407`。

补充修正（2026-06-16，POD 提取失败后重跑完整流程 2 次）：
- 问题表现：
  - 单轮流程内虽然已有 MiniMax 图生图 attempt 重试，但如果整轮流程因为 `1033 system error`、质检失败、视觉接口异常等原因失败，会直接进入接口异常处理。
  - 用户要求：这种失败不要马上报错，而是重新走完整流程 2 次；两次重跑后仍失败再报错。
- 代码修复：
  - 新增 `POD_EXTRACT_FLOW_MAX_ATTEMPTS`，默认值为 `3`，表示首次执行 + 失败后额外 2 次完整流程重跑。
  - 将原 `call_minimax_illustration_generation()` 的主体拆为 `call_minimax_illustration_generation_once_flow()`。
  - 新的 `call_minimax_illustration_generation()` 作为外层包装器，捕获单轮流程异常并重新执行完整流程。
  - 每轮都会重新执行：本地抠图、低价值检测、三维结构审查、`image_description`、MiniMax 图生图多次 attempt、质检。
  - 日志新增 `[POD_EXTRACT_FLOW] task_id=... flow_attempt=1/3 start/failed`，便于判断当前是第几轮完整流程。
  - 如果 3 轮都失败，最终错误写为：`POD提取完整流程失败；已重跑 3 轮；flow 1: ... | flow 2: ... | flow 3: ...`。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。

补充修正（2026-06-16，POD 提取不再默认强制循环平铺）：
- 问题表现：
  - 用户指出并非所有商品都是循环纹样，很多 POD 商品只有单个胸前图案、背后大图、徽章、小图标或一句文字。
  - 旧提示词第一阶段硬要求 `A perfectly flat 2D seamless print pattern asset...`，第二阶段兜底 prompt 写了 `repeating tile`，容易把单个图案误生成循环排布。
- 代码修复：
  - 第一阶段 `generate_artwork_description()` 首句要求改为 `A perfectly flat 2D printable artwork asset...`。
  - 明确提示：不要默认写 `seamless/repeating`；只有原图本身是满版循环纹样时才描述为 seamless/repeating。
  - 增加排布规则：必须保留原图的图案排布类型；单个胸前图案、背后大图、徽章、单句文字、局部小图标应描述为 `single centered/front/back graphic` 或 `isolated motif`。
  - 第二阶段 `build_illustration_generation_prompt()` 改为 `printable artwork asset`，并要求保留 reference 的 layout type。
  - 兜底 prompt 从 `Fill the square canvas with a clean 2D motif or repeating tile` 改为：单个图案/短语就输出单个干净孤立图案，只有原图已经是重复印花时才用 repeating tile。
  - 第二阶段允许 `text graphic`，用于类似 “Jesus is for everybody” 这类非品牌文字印花。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 已确认 `app.py` 中不再把所有 POD 提取结果默认定调为 seamless/repeating。

补充修正（2026-06-16，压缩第二阶段提示词并避免像素级抠残片）：
- 问题表现：
  - 用户将第二阶段提示词改为 `SYSTEM DIRECTIVE: 2D ASSET EXTRACTION & RECONSTRUCTION` 后，日志显示前两轮生成图被低价值规则打 20 分：
    - `生成图有效图案面积过小 coverage=0.0092`
    - `生成图过于碎片化 components=333 largest_ratio=0.081`
  - 第三轮 MiniMax 图生图返回 `1033 system error`。
- 根因：
  - 提示词中的 `PRECISION MASKING`、`trace ONLY the artwork's pixels` 容易诱导图生图模型做字面像素抠图，输出细碎残片，而不是完整可用的二维印花重建。
  - 该提示词本身过长，而代码最终会 `prompt[:1450]`；实际测试发现最终 prompt 被截断在规则中间，`PATTERN/ARTWORK TO RECREATE` 和具体图案描述没有传给 MiniMax。
- 代码修复：
  - 第二阶段 prompt 从“像素级 masking/cutout”改为“完整 2D printable digital design reconstruction”。
  - 明确写入：`Do NOT do literal pixel masking; do not output tiny fragments, scattered residue, edge scraps`。
  - 增加画布占比规则：单个图案/短语应居中并占画面约 55-85%，防止输出角落小残片。
  - 压缩 `flat_rules` 长度，并在 `build_illustration_generation_prompt()` 中按预算截断 `artwork_description`，确保 `ARTWORK TO RECREATE` 总能保留在最终 1450 字符内。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 已测试 `build_illustration_generation_prompt()`：最终 prompt 长度 1253，包含 `ARTWORK TO RECREATE`，且具体描述 `Statue of Liberty` 未被截断。

补充修正（2026-06-16，MiniMax 视觉接口 429 限流退避与插画判断后台降速）：
- 问题表现：
  - 日志出现：`插画视觉判断接口调用失败: Error code: 429 ... rate limit exceeded(RPM) (1002)`。
  - 该错误表示 MiniMax 视觉接口一分钟请求数超限，不是单个商品判断逻辑错误。
- 根因：
  - 插画判断后台默认 `batch_size=120`、`concurrency=4`，同时手动 POD 提取、IP/材质分析也会复用同一个 MiniMax 视觉接口，容易打满 RPM。
- 代码修复：
  - `tools/analyze_single_product.py` 的 `call_minimax()` 增加 429/rate_limit 专用退避重试。
  - 默认最多重试 `MINIMAX_VISION_MAX_RETRIES=5` 次。
  - 默认退避基准 `MINIMAX_VISION_RATE_LIMIT_SLEEP_SECONDS=10` 秒，按 10/20/40/60/60 秒等待。
  - `app.py` 插画判断后台默认降速：
    - `ILLUSTRATION_DAEMON_INTERVAL_SECONDS`：120 -> 180
    - `ILLUSTRATION_DAEMON_BATCH_SIZE`：120 -> 40
    - `ILLUSTRATION_DAEMON_CONCURRENCY`：4 -> 2
- 操作建议：
  - 遇到 429 后先等 1-2 分钟再手动提取。
  - 如果仍频繁 429，可临时设置 `ILLUSTRATION_DAEMON_ENABLED=false`，先让手动提取独占视觉额度。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py tools\analyze_single_product.py`
  - 结果：通过。

补充修正（2026-06-16，取消 POD 提取前置本地抠图，只保留 AI 识别/生成链路）：
- 问题背景：
  - 用户要求取消提取插画前的程序识别/本地抠图步骤，只使用 AI 识别。
  - 当前代码中没有实际使用 `rembg`；前置步骤是 `local_cutout_print_artwork()` 的 OpenCV 本地抠图逻辑。
- 代码修复：
  - 从 `call_minimax_illustration_generation_once_flow()` 中移除本地抠图调用。
  - POD 提取流程现在直接进入：
    1. `generate_artwork_description()` 让 AI 识别原图图案并生成描述；
    2. `call_minimax_illustration_generation_once()` 调用 MiniMax 图生图；
    3. `evaluate_generated_illustration()` 质检。
  - 日志新增：`[POD_EXTRACT_FLOW] task_id=... local_cutout_disabled use_ai_only=true`，用于确认当前已跳过本地抠图。
- 保留项：
  - `local_cutout_print_artwork()` 和 `save_extracted_artwork_png_bytes()` 函数暂时保留在文件中，但当前主流程不再调用。
- 验证方式：
  - 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
  - 结果：通过。
### 2026-06-18：修复 POD 插画判断队列有索引仍超时
修改目标：
- 解决 `[ILLUSTRATION_DAEMON] cycle failed` 中 `fetch_illustration_daemon_product_keys()` 取待判断队列超时的问题。
- 用户已经建立联合索引，但 MySQL 实际没有使用该索引，需要让队列 SQL 真正走索引。

涉及文件：
- `app.py`
- `PROJECT_MEMORY_ARCHIVE.md`

问题原因：
- 数据库中确实存在 `idx_pod_illustration_queue(date_record, illustration_extractable, id)`。
- 但原 SQL 同时包含：
  - `date_record = (SELECT MAX(...))`
  - `image_base64 IS NOT NULL`
  - `image_base64 <> ''`
  - `ORDER BY date_record DESC, id ASC`
- `image_base64` 是 LONGTEXT 大字段，无法作为普通联合索引字段使用。
- `EXPLAIN` 显示 MySQL 实际选择了 `PRIMARY`，不是 `idx_pod_illustration_queue`；它为了满足排序按主键扫，再逐行检查 date/status/image 条件，数据一多就会触发 PyMySQL read timeout。

具体改动：
- `fetch_illustration_daemon_product_keys()`：
  - 先单独查询最新 `date_record`，不再在主队列 SQL 中使用 `SELECT MAX(...)` 子查询。
  - 主队列 SQL 改为 `FORCE INDEX (idx_pod_illustration_queue)`。
  - 主队列 SQL 移除 `image_base64 IS NOT NULL AND image_base64 <> ''`，避免取队列阶段扫描 LONGTEXT。
  - 排序改为 `ORDER BY id ASC`，配合 `(date_record, illustration_extractable, id)` 使用。
- 新增 `fetch_source_latest_date(meta)`，用于通用获取 source 最新采集日期。
- `process_single_illustration_check()`：
  - 如果商品不存在或主图为空，将该商品标记为 `illustration_extractable = 0`，并写入原因“商品主图为空，无法判断插画可提取性”。
  - 这样无图商品不会反复进入“未判断”队列。

验证结果：
- 已执行：`D:\choice_product\.venv\Scripts\python.exe -m py_compile app.py`
- 结果：通过。
- 已执行新 SQL 的 `EXPLAIN`：
  - `key = idx_pod_illustration_queue`
  - `type = ref`
  - `Extra = Using index condition; Using where`
- 实测调用：
  - `fetch_illustration_daemon_product_keys('pod_cross_category', 40)`
  - 返回 40 条，耗时约 `0.762s`。

用户如何看到：
- 重启 Flask 后端。
- `[ILLUSTRATION_DAEMON] cycle failed` 中取队列阶段的 MySQL timeout 应显著减少。
- 控制台应继续看到：
  `[ILLUSTRATION_DAEMON] checked source=pod_cross_category ... extractable=...`

风险边界：
- 本次不修改视觉判断 prompt。
- 本次不修改图生图提取接口。
- 本次不修改前端展示。
- 如果后续仍超时，下一步应检查视觉模型请求本身是否卡住，而不是数据库取队列。
