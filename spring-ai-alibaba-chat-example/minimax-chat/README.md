# 小雨点智能客服 Chat-Bot v1.0

`minimax-chat` 已整理为一个面向真实业务学习的 Spring AI Alibaba 智能客服示例。当前版本不再保留早期学习助手 Demo 入口，主线聚焦“闲鱼类客服助手”：商品咨询、订单查询、物流查询、议价策略、退款资格、售后状态、人工接管、客服知识库 RAG、长期 Memory、Agent 执行报告和质量评估。

## v1.0 功能清单

- MiniMax-M2.7 模型接入，兼容 OpenAI 风格 Chat Completions。
- 前端聊天页面支持多轮上下文、Markdown、用户 ID、渠道选择、同步/流式切换和调试区。
- Spring AI Alibaba `ReactAgent`：由模型自主选择客服工具并生成回答。
- Spring AI Alibaba `StateGraph`：固定编排客服流程，展示 Graph 节点。
- Spring AI Alibaba `SequentialAgent`：客服处理 Agent 和质检 Agent 串行协作。
- Tool Calling：商品、订单、物流、议价、退款、售后、工单、人工接管、知识库检索、Skill 读取。
- MCP 门面：优先调用真实 MCP 工具，未发现时回退到本地 Mock 客服数据。
- RAG：本地高召回客服知识库，并预留 Spring AI `VectorStore` 接入边界。
- Memory：按 `userId` 持久化客服长期记忆到 `memory/customer-memory.json`。
- 报告与评估：保存 Agent 运行报告、规则评估结果、LLM-as-Judge 结果。
- 日志：控制台输出关键流程日志，`log/customer-service-flow.log` 单独记录客服链路。

## 核心调用链路

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

访问页面：

```text
http://localhost:8080/index.html
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
| ReactAgent 客服对话 | `POST /minimax/chat-client/customer-service/chat` |
| ReactAgent 流式客服对话 | `POST /minimax/chat-client/customer-service/stream` |
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

1. 打开 `http://localhost:8080/index.html`。
2. 用户 ID 输入 `default-user`，渠道选择 `闲鱼`。
3. 模式先选 `客服 ReactAgent`，发送：`这个 p-1001 商品还在吗？能简单介绍一下吗？`
4. 观察调试区是否出现商品工具调用、MCP fallback 或真实 MCP 状态、Memory 更新。
5. 切换 `客服 StateGraph`，发送：`订单 o-9001 物流到哪了？`
6. 观察 Graph 节点是否按 `memory_read -> intent_plan -> skill_select -> react_agent -> risk_review -> memory_write -> response` 展示。
7. 切换 `客服 Multi-Agent`，发送：`订单 o-9002 想退款，可以直接帮我退吗？`
8. 观察是否触发退款资格、人工接管或工单类工具，回答不能直接承诺高风险动作。
9. 打开报告、评估、Judge 按钮，检查本轮执行质量。

## HTTP 测试

推荐使用：

```text
minimax-chat.http
CUSTOMER-SERVICE-TEST.http
```

`minimax-chat.http` 是 v1.0 发布冒烟用例，覆盖客服对话、Graph、Multi-Agent、Memory、MCP、RAG、报告、评估和 Judge。

## v1.0 边界说明

- 真实订单、商品、物流等外部系统当前通过 `CustomerMcpService` 统一接入。发现真实 MCP 工具时优先调用真实工具；未发现时使用 `MockCustomerDataService` 兜底，便于本地无外部系统也能完整测试。
- RAG 当前默认使用本地知识库高召回检索，`application-vector.yml` 提供向量库 Profile 示例，后续可把 `CustomerPolicyRagService` 的存储层替换为真实 `VectorStore`。
- `pendingMcpWrite` 仅作为历史报告兼容字段保留，v1.0 客服主线不再使用早期学习资源写入确认流程。
- 早期学习助手相关类、HTTP 文件和 README 已移除，避免和智能客服主线混淆。
