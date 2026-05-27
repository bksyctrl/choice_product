# 用户注册登录与私人选品库改造方案

## 1. 改造目标

当前系统有三个公共商品数据源：

| 前端 source | 真实数据表 | 说明 |
| --- | --- | --- |
| `unified` | `fastmoss_product_aggregate` | 统一表 |
| `fastmoss` | `fastmoss_product_rank_aggregate` | FastMoss 表 |
| `kalodata` | `kalodata_youwei_product` | Kalodata 表 |

改造后：

- 三张商品表都是公共数据源。
- 公共商品表只由采集、同步、AI 分析等系统任务更新。
- 用户选品状态不再写入公共商品表。
- 用户注册登录后，自己的待上架、已上架商品写入私人选品库。
- 不同用户看到的商品状态互相隔离。

新增两张核心表：

1. `app_user`：用户表，记录用户名、密码哈希、角色、状态等。
2. `app_private_product_library`：私人选品库表，记录某个用户选中了哪个商品，以及选中时的商品快照和核心指标。

## 2. 核心概念

### 2.1 uid

`uid` 是接口里的简称，实际含义是：

```text
uid = app_user.id
```

例如：

| app_user.id | username |
| --- | --- |
| 1 | user1 |
| 2 | user2 |
| 3 | user3 |

那么接口中的：

```text
uid=1
uid=2
uid=3
```

分别代表这三个用户。

### 2.2 source

`source` 是前端和接口使用的数据源标识。

| source | 含义 |
| --- | --- |
| `unified` | 统一表 |
| `fastmoss` | FastMoss 表 |
| `kalodata` | Kalodata 表 |

### 2.3 source_table

`source_table` 是真实数据库表名。

| source | source_table |
| --- | --- |
| `unified` | `fastmoss_product_aggregate` |
| `fastmoss` | `fastmoss_product_rank_aggregate` |
| `kalodata` | `kalodata_youwei_product` |

### 2.4 date_record

`date_record` 使用公共商品表中的采集日期，不是用户选择时间。

例如：

```text
用户在 2026-05-27 选中商品
商品来自公共表 date_record = 2026-05-24
```

私人选品库保存：

```text
date_record = 2026-05-24
selected_at = 2026-05-27 用户实际选择时间
```

### 2.5 product_snapshot_json

`product_snapshot_json` 是用户选中商品那一刻的完整商品数据快照。

作用：

- 记录用户当时看到的商品数据。
- 公共表后续刷新后，仍能保留当时的选品依据。
- 后续做异常提醒时，可以拿当时快照与最新公共数据对比。
- 避免私人库强行复制三张公共表不同的字段结构。

### 2.6 watch_metrics_json

`watch_metrics_json` 是用户选中商品时的核心指标快照，用于后续异常提醒。

第一版建议保存：

```json
{
  "commission_rate": "佣金比例",
  "author_count": "达人数量",
  "product_card_ratio": "商品卡比例",
  "transport_fee": "运费",
  "total_sold_count": "总销量",
  "video_ratio": "视频成交占比",
  "rating": "评分",
  "price": "售价",
  "base_price": "定价",
  "audit_status": "商品状态"
}
```

注意：“售价范围”和“定价范围”是筛选条件，落库时保存实际值，即 `price` 和 `base_price`。

## 3. 建表 SQL

### 3.1 用户表 app_user

密码不保存明文，只保存 `password_hash`。

```sql
CREATE TABLE `app_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID，自增主键；前端和接口中的 uid 就是这个 id',
  `username` VARCHAR(100) NOT NULL COMMENT '用户名/登录账号，必须唯一',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '用户密码哈希值，不保存明文密码；可使用 bcrypt、argon2 或 pbkdf2 等方式生成',
  `display_name` VARCHAR(100) DEFAULT NULL COMMENT '用户展示名称，例如运营人员姓名或昵称',
  `role` VARCHAR(50) NOT NULL DEFAULT 'user' COMMENT '用户角色：user=普通用户，admin=管理员',
  `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态：ACTIVE=启用，DISABLED=禁用',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '用户信息最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_username` (`username`),
  KEY `idx_app_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表：记录系统用户账号、密码哈希、角色和状态信息';
```

### 3.2 私人选品库表 app_private_product_library

```sql
CREATE TABLE `app_private_product_library` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '私人选品记录ID，自增主键',
  `uid` BIGINT UNSIGNED NOT NULL COMMENT '用户ID，对应 app_user.id；接口中的 uid 就是该字段',

  `source` VARCHAR(50) NOT NULL COMMENT '前端/接口数据源标识：unified=统一表，fastmoss=FastMoss表，kalodata=Kalodata表',
  `source_table` VARCHAR(100) NOT NULL COMMENT '真实来源数据库表名，例如 fastmoss_product_aggregate、fastmoss_product_rank_aggregate、kalodata_youwei_product',
  `product_id` VARCHAR(100) NOT NULL COMMENT '来源公共商品表中的商品ID',
  `date_record` DATE DEFAULT NULL COMMENT '来源公共商品表中的采集日期 date_record，用于定位用户选中时对应的公共数据',

  `audit_status` VARCHAR(30) NOT NULL COMMENT '该用户对该商品的私人状态：READY=待上架，PUBLISHED=已上架',
  `product_snapshot_json` LONGTEXT NOT NULL COMMENT '用户选中商品时的完整商品数据快照JSON，保留当时标题、价格、评分、销量、图片、链接等完整信息',
  `watch_metrics_json` TEXT DEFAULT NULL COMMENT '用户选中商品时用于后续异常提醒的核心指标JSON，例如佣金比例、达人数量、商品卡比例、运费、总销量、视频成交占比、评分、售价、定价、商品状态',

  `platform_url` TEXT DEFAULT NULL COMMENT '商品平台链接快照，冗余保存便于导出或快速打开商品',
  `title` TEXT DEFAULT NULL COMMENT '商品标题快照，冗余保存便于私人库列表快速展示',
  `image_url` TEXT DEFAULT NULL COMMENT '商品图片地址快照，冗余保存便于私人库列表快速展示',

  `selected_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户将商品加入私人选品库的时间',
  `published_at` DATETIME DEFAULT NULL COMMENT '用户将商品状态改为已上架的时间；未上架时为空',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '私人选品记录最后更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_private_product_user_source_product` (`uid`, `source`, `product_id`),
  KEY `idx_app_private_product_user_status` (`uid`, `audit_status`),
  KEY `idx_app_private_product_source_product` (`source`, `product_id`),
  KEY `idx_app_private_product_source_record` (`source_table`, `product_id`, `date_record`),
  CONSTRAINT `fk_app_private_product_user`
    FOREIGN KEY (`uid`) REFERENCES `app_user` (`id`)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私人选品库表：记录每个用户选中的待上架或已上架商品，公共商品表不因用户操作而改变';
```

## 4. 注册登录功能设计

### 4.1 注册功能

注册接口：

```http
POST /api/auth/register
```

请求体：

```json
{
  "username": "user1",
  "password": "123456",
  "display_name": "用户1"
}
```

后端处理：

1. 校验用户名不能为空。
2. 校验密码不能为空，建议最少 6 位。
3. 检查用户名是否已存在。
4. 对密码生成哈希，写入 `password_hash`。
5. 创建 `app_user` 记录。
6. 返回用户基础信息，不返回 `password_hash`。

响应示例：

```json
{
  "ok": true,
  "data": {
    "user": {
      "id": 1,
      "username": "user1",
      "display_name": "用户1",
      "role": "user",
      "status": "ACTIVE"
    }
  }
}
```

密码哈希建议：

- Python 可以使用 `werkzeug.security.generate_password_hash`。
- 登录校验使用 `werkzeug.security.check_password_hash`。
- 不要保存明文密码。

### 4.2 登录功能

登录接口：

```http
POST /api/auth/login
```

请求体：

```json
{
  "username": "user1",
  "password": "123456"
}
```

后端处理：

1. 根据 `username` 查找用户。
2. 用户不存在则返回用户名或密码错误。
3. 用户 `status != ACTIVE` 时禁止登录。
4. 使用 `check_password_hash` 校验密码。
5. 校验成功后写入登录态。
6. 更新 `last_login_at`。
7. 返回用户基础信息。

响应示例：

```json
{
  "ok": true,
  "data": {
    "user": {
      "id": 1,
      "username": "user1",
      "display_name": "用户1",
      "role": "user",
      "status": "ACTIVE"
    }
  }
}
```

### 4.3 登录态方案

第一版建议使用 Flask Session，简单够用。

登录成功后：

```python
session["uid"] = user["id"]
```

退出登录时：

```python
session.clear()
```

需要在 Flask 中配置：

```python
app.secret_key = os.getenv("SECRET_KEY", "dev-secret-key")
```

如果后续要部署成前后端分离、多服务或移动端访问，再考虑 JWT。

### 4.4 当前用户接口

接口：

```http
GET /api/auth/me
```

作用：

- 前端刷新页面后确认当前是否已登录。
- 获取当前登录用户信息。

未登录响应：

```json
{
  "ok": false,
  "error": "未登录"
}
```

已登录响应：

```json
{
  "ok": true,
  "data": {
    "user": {
      "id": 1,
      "username": "user1",
      "display_name": "用户1",
      "role": "user",
      "status": "ACTIVE"
    }
  }
}
```

### 4.5 退出登录接口

接口：

```http
POST /api/auth/logout
```

后端处理：

```python
session.clear()
```

### 4.6 需要登录保护的接口

以下接口需要登录：

- 商品列表：`GET /api/products`
- 商品详情：`GET /api/products/{source}/{product_id}`
- 修改状态：`PATCH /api/products/{source}/{product_id}/status`
- 复制待上架链接：`GET /api/products/{source}/ready-links`
- 导出待上架链接：`GET /api/products/{source}/export-ready-links`
- 私人选品库列表：`GET /api/private-products`

后端不要完全信任前端传来的 `uid`。

更稳的做法：

```text
uid 优先从 session["uid"] 获取
前端不需要传 uid
```

如果第一版为了开发方便继续传 `uid`，也必须校验：

```text
请求中的 uid 必须等于 session["uid"]
```

## 5. 状态逻辑

### 5.1 公共表不再存用户状态

旧逻辑可能是：

```text
公共商品表.audit_status = READY / PUBLISHED
```

新逻辑：

```text
用户状态只保存到 app_private_product_library
```

公共表不因为任何用户的选品操作而变化。

### 5.2 私人状态规则

| 用户操作 | 后端行为 |
| --- | --- |
| 改为待上架 `READY` | 插入或更新 `app_private_product_library` |
| 改为已上架 `PUBLISHED` | 插入或更新 `app_private_product_library`，并写入 `published_at` |
| 改回待选中 `PENDING` | 删除该用户在 `app_private_product_library` 中的该商品记录 |
| 改为待复核 `REVIEWING` | 第一版建议不进入私人库，按未选中处理 |
| 改为其他 `OTHER` | 第一版建议不进入私人库，按未选中处理 |

第一版私人库只保留：

```text
READY
PUBLISHED
```

## 6. 后端改造方案

### 6.1 商品列表接口

推荐请求：

```http
GET /api/products?source=unified
```

后端从 session 中获取当前用户：

```python
uid = session["uid"]
```

处理流程：

1. 按原逻辑从公共商品表查商品列表。
2. 查询私人库：

```sql
SELECT product_id, audit_status
FROM app_private_product_library
WHERE uid = %s
AND source = %s;
```

3. 将私人库状态覆盖到列表返回值中的 `audit_status`。
4. 私人库没有记录的商品，默认返回 `PENDING`。

### 6.2 状态修改接口

接口继续沿用：

```http
PATCH /api/products/{source}/{product_id}/status
```

请求体：

```json
{
  "date_record": "2026-05-24",
  "audit_status": "READY"
}
```

后端从 session 中获取 uid，不需要前端传 uid。

处理流程：

1. 校验用户已登录。
2. 校验 `source` 是否合法。
3. 根据 `source + product_id + date_record` 从公共表查出这条商品数据。
4. 生成 `product_snapshot_json`。
5. 生成 `watch_metrics_json`。
6. 如果状态是 `READY` 或 `PUBLISHED`，写入 `app_private_product_library`。
7. 如果状态是 `PENDING`，删除 `app_private_product_library` 中该用户该商品记录。
8. 不更新公共商品表。

upsert 示例：

```sql
INSERT INTO app_private_product_library (
  uid,
  source,
  source_table,
  product_id,
  date_record,
  audit_status,
  product_snapshot_json,
  watch_metrics_json,
  platform_url,
  title,
  image_url,
  selected_at,
  published_at
) VALUES (
  %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(),
  CASE WHEN %s = 'PUBLISHED' THEN NOW() ELSE NULL END
)
ON DUPLICATE KEY UPDATE
  date_record = VALUES(date_record),
  audit_status = VALUES(audit_status),
  product_snapshot_json = VALUES(product_snapshot_json),
  watch_metrics_json = VALUES(watch_metrics_json),
  platform_url = VALUES(platform_url),
  title = VALUES(title),
  image_url = VALUES(image_url),
  published_at = CASE
    WHEN VALUES(audit_status) = 'PUBLISHED' THEN COALESCE(published_at, NOW())
    ELSE published_at
  END,
  updated_at = NOW();
```

### 6.3 复制待上架链接接口

请求：

```http
GET /api/products/{source}/ready-links
```

后端从 session 中获取 uid，然后查询私人库：

```sql
SELECT platform_url
FROM app_private_product_library
WHERE uid = %s
AND source = %s
AND audit_status = 'READY'
AND platform_url IS NOT NULL
AND platform_url <> '';
```

### 6.4 导出待上架链接接口

请求：

```http
GET /api/products/{source}/export-ready-links
```

导出内容从 `app_private_product_library` 查询当前登录用户的待上架商品。

### 6.5 可选：私人选品库列表接口

```http
GET /api/private-products?status=READY
```

用途：

- 展示“我的选品库”。
- 查看当前用户待上架/已上架商品。
- 后续接异常提醒。

## 7. 前端改造方案

### 7.1 页面结构

需要新增登录/注册页面或弹窗：

- 未登录时显示登录页。
- 登录页提供“登录”和“注册”入口。
- 登录成功后进入商品列表页。
- 页面右上角显示当前用户。
- 提供退出登录按钮。

### 7.2 前端启动流程

页面加载时先请求：

```http
GET /api/auth/me
```

如果已登录：

```js
state.currentUser = json.data.user;
loadAll();
```

如果未登录：

```js
showLoginView();
```

### 7.3 登录

前端提交：

```http
POST /api/auth/login
```

请求体：

```json
{
  "username": "user1",
  "password": "123456"
}
```

登录成功后：

- 保存 `state.currentUser`
- 隐藏登录页
- 加载商品列表

### 7.4 注册

前端提交：

```http
POST /api/auth/register
```

请求体：

```json
{
  "username": "user1",
  "password": "123456",
  "display_name": "用户1"
}
```

注册成功后可以：

1. 自动登录。
2. 或提示注册成功，让用户手动登录。

第一版建议注册成功后自动登录，体验更顺。

### 7.5 退出登录

前端调用：

```http
POST /api/auth/logout
```

成功后：

- 清空 `state.currentUser`
- 回到登录页

### 7.6 uid 处理

如果使用 Flask Session：

- 前端不需要再给商品接口传 `uid`。
- 后端从 session 中识别当前用户。
- 这样更安全，避免用户手动改 `uid` 查看别人数据。

第一版如果为了兼容旧代码临时传 `uid`，也必须由后端校验：

```text
请求 uid == session["uid"]
```

## 8. 异常提醒预留

后续可以使用 `watch_metrics_json` 作为基准，对比公共表最新数据。

示例提醒：

| 指标 | 示例规则 |
| --- | --- |
| 评分 | 评分从 4.4 掉到 3.0 |
| 佣金比例 | 佣金下降 |
| 达人数量 | 达人数明显下降 |
| 商品卡比例 | 商品卡成交占比下降严重 |
| 视频成交占比 | 视频成交占比下降严重 |
| 运费 | 运费上涨 |
| 总销量 | 总销量增长明显变慢或异常 |
| 售价 | 售价明显变化 |
| 定价 | 定价明显变化 |

后续可新增提醒表，例如 `app_private_product_alert`。

## 9. 推荐实施顺序

1. 创建 `app_user`。
2. 创建 `app_private_product_library`。
3. 后端新增注册接口 `/api/auth/register`。
4. 后端新增登录接口 `/api/auth/login`。
5. 后端新增当前用户接口 `/api/auth/me`。
6. 后端新增退出接口 `/api/auth/logout`。
7. 前端新增登录/注册视图。
8. 商品列表接口从 session 获取 uid，并用私人库状态覆盖返回状态。
9. 状态修改接口改为写 `app_private_product_library`。
10. 复制待上架链接和导出接口改为按当前登录用户查私人库。
11. 公共商品表不再因用户选品状态而更新。
12. 后续再做私人选品库页面和异常提醒。

## 10. 测试用户示例

密码需要由后端生成哈希后写入。下面的 `password_hash` 只是占位示例，不能直接作为真实密码使用。

```sql
INSERT INTO `app_user` (`username`, `password_hash`, `display_name`, `role`)
VALUES
('user1', '$2b$12$replace_with_real_hash_1', '用户1', 'user'),
('user2', '$2b$12$replace_with_real_hash_2', '用户2', 'user'),
('user3', '$2b$12$replace_with_real_hash_3', '用户3', 'user');
```
