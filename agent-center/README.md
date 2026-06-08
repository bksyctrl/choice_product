# Choice Product Agent Center

这是 `choice_product` 的 Java LLM + Agent 中台第一版骨架，目标是复刻 `productviedo` 的多专家调度思想，但先保持轻量，避免把视频生成、消息队列、向量库等重依赖一次性搬进来。

## 运行方式

```powershell
cd D:\choice_product\agent-center
mvn spring-boot:run
```

默认端口：`5010`

```http
POST http://localhost:5010/api/agent/chat
Content-Type: application/json

{
  "sessionId": "demo-session",
  "userId": 1,
  "message": "帮我查看 fastmoss 商品 1729413776507506859 的 attributes 和 selling_points"
}
```

## 当前接口

- `POST /api/agent/chat`：专家团队入口
- `GET /api/agent/intents`：查看当前排班表
- `GET /api/agent/handlers`：查看当前 Handler
- `GET /api/agent/sessions/{sessionId}/messages`：查看会话记忆
- `DELETE /api/agent/sessions/{sessionId}`：清空当前 session 记忆

## 与 productviedo 的 9 步对齐

| 步骤 | productviedo 逻辑 | agent-center 第一版 |
| --- | --- | --- |
| 1. 用户输入 | `AgentRequest` 接收自然语言 | `AgentRequest` 接收 `sessionId/userId/message/context/parameters` |
| 2. CEO 接入 | `TotalConversationAgent.process` 创建/锁定 session | `TotalConversationAgent` 通过 `SessionService` 创建/恢复 session |
| 3. 安全合规层 | `ComplianceControlAgent.checkCompliance` | `ComplianceControlAgent` 先做轻量关键词预审，后续可接规则库 |
| 4. 知识增强层 | RAG 检索经验、规则、专家视角 | `RagService` 已预留成功经验、避坑指南、平台规则结构 |
| 5. 核心决策层 | `BrainService` + `recognizeIntent` + slot filling | `BrainService` 输出 `DispatchDecision`：意图、模式、置信度、实体、缺参、工具、专家、风险 |
| 6. 专家分发层 | `IntentHandlerFactory` 从排班表拿 Handler | `IntentHandlerFactory` 根据 `handlerCode` 分发 |
| 7. 业务执行层 | Handler 调用底层工作流/模型 | 已有通用沟通、只读查询、商品详情查询、执行交接 Handler |
| 8. 结果返回 | `AgentResponse` 返回结果 | `AgentResponse` 返回消息、决策单、数据、动作 |
| 9. 反思学习层 | 会话结束提炼知识入库 | `LearningService` 已预留记录入口，第一版先日志化 |

## 当前边界

- 第一版不直接修改 Flask 代码和数据库。
- 第一版只提供“专家领导层”的判断、派工、会话记忆和只读/执行交接框架。
- 真正的数据库只读工具、MiniMax 决策大脑、长期记忆表、权限透传，可以在第二阶段接入。
