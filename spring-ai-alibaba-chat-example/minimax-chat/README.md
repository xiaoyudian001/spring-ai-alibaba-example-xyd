# 小雨点智能客服 Chat-Bot v1.0

`minimax-chat` 已整理为一个面向真实业务学习的 Spring AI Alibaba 智能客服示例。当前版本不再保留早期学习助手 Demo 入口，主线聚焦“闲鱼类客服助手”：商品咨询、订单查询、物流查询、议价策略、退款资格、售后状态、人工接管、客服知识库 RAG、长期 Memory、Agent 执行报告和质量评估。

## v1.0 功能清单

- MiniMax-M2.7 模型接入，兼容 OpenAI 风格 Chat Completions。
- 前端聊天主页面只保留客户对话体验；用户 ID、渠道、链路模式、调试信息、RAG 和评估能力集中到运营调试工作台。
- Spring AI Alibaba `ReactAgent`：由模型自主选择客服工具并生成回答。
- Spring AI Alibaba `StateGraph`：固定编排客服流程，展示 Graph 节点。
- Spring AI Alibaba `SequentialAgent`：客服处理 Agent 和质检 Agent 串行协作。
- Tool Calling：商品、订单、物流、议价、退款、售后、工单、人工接管、知识库检索、Skill 读取。
- MCP 门面：优先调用真实 MCP 工具，未发现时回退到本地 Mock 客服数据。
- RAG：本地高召回客服知识库，并预留 Spring AI `VectorStore` 接入边界。
- Memory：按 `userId` 持久化客服长期记忆到 MySQL，Redis 可保存短期多轮上下文。
- 报告与评估：保存 Agent 运行报告、规则评估结果、LLM-as-Judge 结果。
- 日志：控制台输出关键流程日志，`log/customer-service-flow.log` 单独记录客服链路。
- 轻量直连：寒暄、感谢、自我介绍等简单对话走 `CUSTOMER_SERVICE_DIRECT_LLM`，直接调用 MiniMax-M2.7，不触发 Tool、RAG、MCP 或 Agent。

## 核心调用链路

### 统一客服入口自动路由

```text
前端问题
 -> MiniMaxChatClientController
 -> CustomerServiceAssistantService
 -> CustomerServiceIntentPlanner 识别客服意图
 -> GENERAL_CHAT 且无业务关键词：CustomerDirectChatService 直连 MiniMax-M2.7
 -> 商品、订单、物流、退款、投诉、人工接管等业务问题：ReactAgent / SequentialAgent
 -> 前端展示回答、链路模式、调试信息和执行报告
```

### ReactAgent 模式

```text
前端问题
 -> MiniMaxChatClientController
 -> CustomerServiceAgentService
 -> CustomerMemoryService 读取长期记忆
 -> CustomerServiceIntentPlanner 识别客服意图
 -> CustomerSkillService 选择客服 Skill
 -> Spring AI Alibaba ReactAgent 调用 MiniMax-M2.7
 -> CustomerServiceTools
 -> CustomerMcpService / CustomerPolicyRagService / CustomerSkillService
 -> CustomerMemoryService 更新并持久化记忆
 -> AgentRunReportService 保存执行报告
 -> 前端展示回答、Agent 步骤、Tool 调用、MCP 状态、RAG 召回和 Memory
```

### StateGraph 模式

```text
前端问题
 -> MiniMaxChatClientController
 -> CustomerServiceGraphService
 -> Spring AI Alibaba StateGraph
 -> memory_read
 -> intent_plan
 -> skill_select
 -> react_agent
 -> risk_review
 -> memory_write
 -> response
 -> 前端展示回答、Graph 节点、Agent 步骤、Tool 调用、MCP 状态和 Memory
```

### Multi-Agent 模式

```text
前端问题
 -> MiniMaxChatClientController
 -> CustomerServiceMultiAgentService
 -> SequentialAgent
 -> customer_handler_agent 生成客服回答
 -> customer_reviewer_agent 做话术与风险复核
 -> CustomerMemoryService 更新记忆
 -> 前端展示多 Agent 协作步骤和最终回答
```

## 目录结构

```text
src/main/java/com/alibaba/cloud/ai
├── controller      # 前端和 HTTP API 入口
├── customer        # 智能客服 Agent、Graph、Multi-Agent、Tool、Skill、RAG、Memory、Mock 数据
├── evaluation      # 规则评估和 LLM-as-Judge
├── mcp             # MCP 调试信息模型
├── report          # Agent 运行报告持久化
└── tool            # Tool Calling 调试记录器

src/main/resources
├── application.yml         # MiniMax、客服 Memory、报告、MCP 客户端基础配置
├── application-mcp.yml     # 真实 MCP Client Profile
├── application-vector.yml  # 向量库 Profile 示例
├── logback.xml             # 日志文件配置
└── static/index.html       # v1.0 前端聊天页面
```

## 运行方式

配置 MiniMax API Key：

```powershell
$env:MINIMAX_API_KEY="你的 MiniMax API Key"
```

启动模块：

```powershell
cd F:\project\spring-ai-alibaba-example-xyd
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am spring-boot:run
```

访问聊天主页面：

```text
http://localhost:8080/index.html
```

访问运营调试工作台：

```text
http://localhost:8080/dashboard.html
```

如需开启真实 MCP Client：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am spring-boot:run "-Dspring-boot.run.profiles=mcp"
```

如需测试向量库配置边界：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am spring-boot:run "-Dspring-boot.run.profiles=vector"
```

## 主要接口

| 能力 | 接口 |
| --- | --- |
| 客户无感统一客服入口 | `POST /minimax/chat-client/customer-service/assistant/chat` |
| ReactAgent 客服对话 | `POST /minimax/chat-client/customer-service/chat` |
| StateGraph 客服对话 | `POST /minimax/chat-client/customer-service/graph/chat` |
| Multi-Agent 客服对话 | `POST /minimax/chat-client/customer-service/multi-agent/chat` |
| 查看客服 Memory | `GET /minimax/chat-client/customer-service/memory?userId=default-user` |
| 清空客服 Memory | `DELETE /minimax/chat-client/customer-service/memory?userId=default-user` |
| 查看 MCP 状态 | `GET /minimax/chat-client/customer-service/mcp/status` |
| 查看 RAG 主题 | `GET /minimax/chat-client/customer-service/rag/topics` |
| 写入客服知识 | `POST /minimax/chat-client/customer-service/rag/documents` |
| 删除客服知识 | `DELETE /minimax/chat-client/customer-service/rag/documents/{id}` |
| 查看运行报告 | `GET /minimax/chat-client/report/runs?limit=10` |
| 清空运行报告 | `DELETE /minimax/chat-client/report/runs` |
| 查看规则评估 | `GET /minimax/chat-client/evaluation/runs?limit=5` |
| 清空规则评估 | `DELETE /minimax/chat-client/evaluation/runs` |
| LLM-as-Judge | `POST /minimax/chat-client/judge/latest` |
| 查看 Judge 结果 | `GET /minimax/chat-client/judge/runs?limit=5` |

## 页面测试建议

1. 打开聊天主页面 `http://localhost:8080/index.html`。
2. 直接发送：`这个 p-1001 商品还在吗？能简单介绍一下吗？`
3. 再发送：`订单 o-9002 想退款，可以直接帮我退吗？`
4. 主页面不会暴露 ReactAgent、Graph、Multi-Agent、同步、流式等技术选项，后端会自动选择处理策略。
5. 点击页面顶部“运营调试工作台”，进入 `http://localhost:8080/dashboard.html`。
6. 在工作台查看报告、评估、Judge、RAG 召回率和知识库维护能力。

完整演示顺序参考：

```text
DEMO-SCRIPT.md
```

## HTTP 测试

推荐使用：

```text
minimax-chat.http
CUSTOMER-SERVICE-TEST.http
CUSTOMER-SERVICE-E2E.http
```

`minimax-chat.http` 是 v1.0 发布冒烟用例，覆盖客服对话、Graph、Multi-Agent、Memory、MCP、RAG、报告、评估和 Judge。
`CUSTOMER-SERVICE-E2E.http` 是端到端验收用例，按 Direct LLM、Agent、Tool、RAG、Memory、Redis、MySQL、审批、审计、报告和 Judge 的顺序验证完整业务闭环。

## v1.0 边界说明

- 真实订单、商品、物流等外部系统当前通过 `CustomerMcpService` 统一接入。发现真实 MCP 工具时优先调用真实工具；未发现时使用 `MockCustomerDataService` 兜底，便于本地无外部系统也能完整测试。
- RAG 当前默认使用本地知识库高召回检索，`application-vector.yml` 提供向量库 Profile 示例，后续可把 `CustomerPolicyRagService` 的存储层替换为真实 `VectorStore`。
- `pendingMcpWrite` 仅作为历史报告兼容字段保留，v1.0 客服主线不再使用早期学习资源写入确认流程。
- 早期学习助手相关类、HTTP 文件和 README 已移除，避免和智能客服主线混淆。

## 上线前硬化能力

- 工作台鉴权：通过 `MINIMAX_DASHBOARD_AUTH_ENABLED=true` 开启，通过 `MINIMAX_DASHBOARD_TOKEN` 设置访问令牌。访问 `dashboard.html?token=你的令牌` 后，前端会在工作台请求中自动附带 `X-Dashboard-Token`。
- Memory 持久化：`CustomerMemoryService` 已从 JSON 文件切换到 MySQL 数据库表 `customer_memory`，默认连接本机 `minimax_customer_service` 数据库。
- 高风险审核：人工接管等高风险动作统一进入 `customer_approval_task` 待审核任务表，模型不会直接执行退款、赔付、取消订单等动作。
- 操作审计：RAG 知识写入/删除、Memory 编辑/清空、报告清空、审核状态变更会写入 `operation_audit_event`。
- 数据工作台：工作台可查看 MySQL 核心表记录数、最近数据、Redis 短期上下文和统一存储健康状态。
- 统一异常：后端 API 通过 `GlobalApiExceptionHandler` 返回统一 JSON 错误结构，前端聊天主流程会展示友好错误提示。
- 自动化测试：已新增 Memory 数据库持久化测试和待审核任务流转测试。

## 新增接口

| 能力 | 接口 |
| --- | --- |
| Memory 后端状态 | `GET /minimax/chat-client/customer-service/memory/backend` |
| 短期上下文状态 | `GET /minimax/chat-client/customer-service/context/status` |
| 查看 Redis 短期上下文 | `GET /minimax/chat-client/customer-service/context?userId=default-user` |
| 清空 Redis 短期上下文 | `DELETE /minimax/chat-client/customer-service/context?userId=default-user` |
| 统一存储健康检查 | `GET /minimax/chat-client/customer-service/storage/status` |
| MySQL 表概览 | `GET /minimax/chat-client/customer-service/storage/tables` |
| 清理本地测试数据 | `DELETE /minimax/chat-client/customer-service/storage/test-data` |
| RAG 运行状态 | `GET /minimax/chat-client/customer-service/rag/status` |
| 编辑客服 Memory | `POST /minimax/chat-client/customer-service/memory?userId=default-user` |
| 查询待审核任务 | `GET /minimax/chat-client/customer-service/approval/tasks?limit=20` |
| 更新审核任务状态 | `POST /minimax/chat-client/customer-service/approval/tasks/status` |
| 查询操作审计 | `GET /minimax/chat-client/audit/events?limit=30` |

## MySQL 与 Redis 配置

MySQL 已作为默认业务数据库，直接启动即可连接本机 MySQL：

```powershell
mvn spring-boot:run
```

MySQL 连接已固定在 `application.yml`：`jdbc:mysql://localhost:3306/minimax_customer_service`，账号密码默认为 `root/root`，并通过 `createDatabaseIfNotExist=true` 自动创建数据库。

数据库建表脚本位于 `src/main/resources/schema-mysql.sql`，包含 `customer_memory`、`customer_approval_task`、`operation_audit_event` 三张核心业务表。

接入真实 Redis 短期上下文：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=redis"
```

Redis 连接已固定在 `application-redis.yml`：`localhost:6379`，数据库为 `0`，默认无密码。

同时接入 MySQL 和 Redis：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=redis"
```

MySQL 会承载 `customer_memory`、`customer_approval_task`、`operation_audit_event` 等长期数据表；Redis 会承载 `minimax:customer:conversation:{userId}` 短期多轮上下文，默认保留 20 条消息，TTL 为 12 小时。
