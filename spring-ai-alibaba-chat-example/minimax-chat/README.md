# Spring AI Alibaba MiniMax Chat 示例

完整学习流测试手册：

```text
README-LEARNING-FLOW.md
```

本模块是一个基于 Spring AI Alibaba 和 MiniMax-M2.7 的聊天示例。

当前它已经从“手写学习 Agent 示例”切换为“Spring AI Alibaba 官方框架主线示例”。旧的手写 `LearningAgentService`、轻量 Workflow、手写 Multi-Agent 和轻量 Graph 编排入口已移除，页面主线只保留智能客服官方 `ReactAgent`、官方学习 `ReactAgent` 和官方 `StateGraph` 三种模式。

## 当前功能

- 多轮聊天页面
- Markdown 回答渲染
- MiniMax-M2.7 模型接入
- Tool Calling 调试信息展示
- 智能客服官方 `ReactAgent`
- 智能客服官方 `StateGraph`
- 官方学习 `ReactAgent`
- 官方 `StateGraph`
- 客服 Skill、RAG、MCP、Memory 和人工接管工具
- JSON 文件持久化客服 Memory / Learning Memory
- 多用户 Memory 查看和清空管理
- Agent 执行报告、规则评估和 LLM-as-Judge
- 前端调试区展示 Agent 步骤、Graph 节点、Tool 调用、MCP 状态和 Memory 信息

> 说明：下方演进记录保留了项目从学习助手逐步演进到智能客服助手的过程。当前可运行主线以本节和 `CUSTOMER-SERVICE-AGENT-REFACTOR-PLAN.md` 的“官方框架化落地情况”为准。

## 当前请求链路

智能客服主线调用链路：

```text
前端问题
 -> 前端携带 userId
 -> Controller
 -> CustomerServiceAgentService
 -> Spring AI Alibaba ReactAgent
 -> CustomerServiceTools / CustomerMcpService / CustomerSkillService / CustomerPolicyRagService
 -> CustomerMemoryService 按 userId 更新客服记忆并写回 JSON 文件
 -> 前端展示回答 + Agent步骤 + Tool调用 + MCP状态 + 客服Memory信息
```

智能客服 Graph 链路：

```text
前端问题
 -> Controller
 -> CustomerServiceGraphService
 -> Spring AI Alibaba StateGraph
 -> memory_read
 -> intent_plan
 -> skill_select
 -> react_agent
 -> risk_review
 -> memory_write
 -> response
 -> 前端展示回答 + Graph节点 + Agent步骤 + Tool调用 + MCP状态 + 客服Memory信息
```

官方 Graph 学习链路：

```text
前端问题
 -> Controller
 -> OfficialLearningGraphService
 -> Spring AI Alibaba StateGraph
 -> memory_read
 -> planner
 -> mcp_node
 -> react_agent
 -> memory_write
 -> response
 -> 前端展示回答 + graphDefinition + graphSteps + Tool调用 + Memory信息
```

## 各层职责

| 层 | 类 | 职责 |
| --- | --- | --- |
| 前端 | `src/main/resources/static/index.html` | 发送用户 ID 和用户问题，维护短期聊天历史，渲染 Markdown，展示调试信息。 |
| Controller | `MiniMaxChatClientController` | 接收 HTTP 请求，把对话处理委托给 Agent 层。 |
| Agent | `CustomerServiceAgentService` / `OfficialLearningAgentService` | 通过 Spring AI Alibaba 官方 `ReactAgent` 调用模型和工具。 |
| Graph | `CustomerServiceGraphService` / `OfficialLearningGraphService` | 通过 Spring AI Alibaba 官方 `StateGraph` 编排客服节点或学习节点。 |
| Memory | `LearningMemoryService` | 按 userId 从 JSON 文件读取用户学习记忆，并在每轮对话后写回该用户的学习阶段、关注主题、最近意图和对话轮次。 |
| RAG | `LearningRagService` | 基于关键词检索当前 minimax-chat 的 README 和关键源码，为模型回答当前项目实现细节提供本地资料。 |
| MCP | `LearningMcpService` | 通过真实 MCP Client 查询、创建和更新外部学习资源；未启用真实 MCP 时提供 mock fallback。 |
| Planner | `LearningIntentPlanner` | 把当前用户请求识别成具体学习意图。 |
| Tool | `MiniMaxLearningTools` | 暴露可以被大模型调用的工具入口。 |
| Skill | `LearningSkillService` | 承载学习建议、计划生成、概念解释、当前时间等真实业务逻辑。 |
| Model | MiniMax-M2.7 | 生成最终回答，并根据需要决定是否调用工具。 |

## 演进记录

### 1. Chat

初始目标：让 MiniMax 聊天先跑通，支持简单前端页面和自定义输入内容。

```text
前端问题
 -> Controller
 -> MiniMax
 -> 前端展示回答
```

### 2. Tool Calling

新增可以被模型调用的工具，用于获取当前时间和生成 Spring AI Alibaba 学习建议。

```text
前端问题
 -> Controller
 -> MiniMax
 -> MiniMax 按需调用 Tools
 -> 前端展示回答 + Tool调用
```

### 3. Skill 层

把真实学习业务逻辑从 Tool 方法中拆出来，放入 `LearningSkillService`。

```text
前端问题
 -> Controller
 -> MiniMax
 -> Tools
 -> LearningSkillService
 -> 前端展示回答 + Tool调用
```

### 4. Planner

新增 `LearningIntentPlanner`，用于识别用户是在询问时间、学习建议、每日计划、概念解释、混合意图还是普通聊天。

```text
前端问题
 -> Controller
 -> Planner 判断意图
 -> MiniMax + Tools
 -> 前端展示回答 + Planner意图 + Tool调用
```

### 5. 轻量 Agent

新增 `LearningAgentService` 和 `LearningAgentResult`。

Controller 不再自己组装完整 prompt，而是把对话处理交给 Agent 层。Agent 层负责记录执行步骤，例如接收问题、读取记忆、规划意图、选择策略、调用模型和处理工具结果。

```text
前端问题
 -> Controller
 -> LearningAgentService
 -> Planner 判断意图
 -> MiniMax + Tools
 -> 前端展示回答 + Agent步骤 + Tool调用
```

### 6. Learning Memory

新增 `LearningMemory` 和 `LearningMemoryService`。

Agent 在调用模型前读取用户学习记忆，在回答生成后更新记忆。

```text
前端问题
 -> Controller
 -> LearningAgentService
 -> LearningMemoryService 读取记忆
 -> Planner 判断意图
 -> MiniMax + Tools
 -> LearningMemoryService 更新记忆
 -> 前端展示回答 + Agent步骤 + Tool调用 + Memory信息
```

### 7. Memory 持久化

把第 6 阶段的进程内 Memory 升级为 JSON 文件持久化 Memory。

记忆文件位置：

```text
memory/learning-memory.json
```

当前示例在 `application.yml` 中固定配置为：

```yaml
minimax:
  memory:
    file: spring-ai-alibaba-chat-example/minimax-chat/memory/learning-memory.json
```

这样即使从项目根目录启动应用，也会写入 `minimax-chat` 模块下的记忆文件，而不会误写到项目根目录的 `memory/learning-memory.json`。

应用启动时，`LearningMemoryService` 会从该文件读取历史学习记忆；每次对话结束后，会把更新后的记忆写回该文件。这样即使 Spring Boot 应用重启，用户学习阶段、关注主题、上次意图和对话轮次也不会丢失。

```text
前端问题
 -> Controller
 -> LearningAgentService
 -> LearningMemoryService 从 JSON 文件读取记忆
 -> Planner 判断意图
 -> MiniMax + Tools
 -> LearningMemoryService 更新记忆并写回 JSON 文件
 -> 前端展示回答 + Agent步骤 + Tool调用 + Memory信息
```

### 8. 多用户 Memory

把第 7 阶段的单用户持久化 Memory 升级为多用户 Memory。

前端新增用户 ID 输入框，请求体新增 `userId` 字段。Controller 会把 `userId` 传给 `LearningAgentService`，Agent 再使用真实 `userId` 调用 `LearningMemoryService.read(userId)` 和 `LearningMemoryService.update(userId, ...)`。

JSON 文件结构保持为按用户 ID 分组：

```json
{
  "default-user": {},
  "user-a": {},
  "user-b": {}
}
```

升级后的链路：

```text
前端问题
 -> 前端携带 userId
 -> Controller
 -> LearningAgentService
 -> LearningMemoryService 按 userId 从 JSON 文件读取记忆
 -> Planner 判断意图
 -> MiniMax + Tools
 -> LearningMemoryService 按 userId 更新记忆并写回 JSON 文件
 -> 前端展示回答 + Agent步骤 + Tool调用 + 当前用户Memory信息
```

### 9. Memory 管理能力

新增长期 Memory 的查看和清空能力，用于区分短期上下文和长期记忆。

后端接口：

```text
GET /minimax/chat-client/memory?userId=user-a
DELETE /minimax/chat-client/memory?userId=user-a
```

前端新增按钮：

- `查看记忆`：读取当前用户的长期 Memory。
- `清空记忆`：清空当前用户的长期 Memory，并同步清空当前浏览器中的短期上下文。
- `清空`：只清空当前用户的短期上下文，不影响 JSON 文件中的长期 Memory。

查看 Memory 链路：

```text
用户点击查看记忆
 -> Controller
 -> LearningMemoryService.read(userId)
 -> 前端展示当前用户 Memory
```

清空 Memory 链路：

```text
用户点击清空记忆
 -> Controller
 -> LearningMemoryService.clear(userId)
 -> JSON 文件写回
 -> 前端提示已清空该用户长期记忆
```

### 10. Simple RAG

新增本地文档检索能力，用于让 Agent 回答当前 `minimax-chat` 项目的 README、源码结构和调用链问题。

新增类：

- `LearningDocument`
- `LearningRagService`

新增 Tool：

```text
searchLearningDocs(query, limit)
```

当前 Simple RAG 不接向量数据库，先用关键词检索本地文档和关键源码，覆盖以下资料：

- `README.md`
- `MiniMaxChatClientController`
- `LearningAgentService`
- `MiniMaxLearningTools`
- `LearningSkillService`
- `LearningIntentPlanner`
- `LearningMemoryService`

调用链：

```text
前端问题
 -> Controller
 -> LearningAgentService
 -> LearningMemoryService 按 userId 读取记忆
 -> Planner 判断意图
 -> MiniMax
 -> searchLearningDocs Tool
 -> LearningRagService 检索本地文档
 -> MiniMax 基于检索结果生成回答
 -> 前端展示回答 + Tool调用 + Memory信息
```

这一阶段的目标不是构建完整知识库，而是先理解 RAG 在 Agent 链路中的位置：模型在回答当前项目相关问题前，先通过工具检索本地资料，再基于资料组织回答。

### 11. 轻量 Graph 工作流

新增轻量 Graph 层，用于把当前 Agent 编排过程表达成节点流程。

新增类：

- `LearningGraphStep`
- `LearningGraphResult`
- `LearningGraphService`

当前阶段不改变真实执行逻辑，只把 Agent 流程图谱化，方便观察和学习：

```text
Receive
 -> Memory Read
 -> Planner
 -> Strategy
 -> Model Call
 -> Tool Execute
 -> Memory Write
 -> Response
```

请求链路：

```text
前端问题
 -> Controller
 -> LearningAgentService
 -> LearningGraphService 生成 Graph 节点
 -> Memory Read
 -> Planner
 -> MiniMax + Tools/RAG
 -> Memory Write
 -> 前端展示回答 + Graph节点 + Tool调用 + Memory信息
```

这一阶段的目标是理解 Graph 在 Agent 中的位置：Agent 负责编排任务，Graph 负责把编排过程表达成节点流程。后续可以再接入正式的 Spring AI Alibaba Graph/工作流框架。

### 12. 流式调试增强

把流式接口 `/conversation/stream` 从单纯文本流升级为多事件 SSE。

事件格式：

```text
event: debug
data: {"intent":"MIXED","graphSteps":[...],"agentSteps":[...],"memoryBefore":...}

event: message
data: {"content":"模型回答片段"}

event: done
data: {"memoryAfter":...,"toolCalls":[...],"agentSteps":[...]}
```

流式模式现在可以边输出回答，边展示调试信息：

- `debug`：回答开始前返回 Planner 意图、Graph 节点、Agent 初始步骤和调用前 Memory。
- `message`：持续追加模型回答片段。
- `done`：回答结束后返回 Tool 调用、调用后 Memory 和最终 Agent 步骤。

同步模式和流式模式的定位：

```text
同步模式：一次性返回完整结果，适合接口验证。
流式模式：边输出边展示调试信息，适合真实聊天体验。
```

### 13. MCP 学习资源写入

在真实 MCP Server 已启动、`minimax-chat` 使用 `mcp` profile 启动时，Agent 不仅可以查询学习资源，还可以把用户要求保存的学习点写入 MCP Server。

写入类工具受安全配置控制：

```yaml
minimax:
  mcp:
    write-enabled: false
    write-mode: dry-run
```

含义：

```text
write-enabled=false：直接拦截写入，不调用 MCP Server。
write-enabled=true + write-mode=dry-run：只预览将要写入的内容，不落盘。
write-enabled=true + write-mode=commit：真正调用 MCP Server 写回 learning-resources.json。
```

`application-mcp.yml` 默认开启写入流程但仍保持 dry-run：

```yaml
minimax:
  mcp:
    write-enabled: true
    write-mode: dry-run
```

dry-run 模式下，后端会保存一份 `PendingMcpWrite` 草稿，前端调试区会显示“确认写入”按钮。点击后调用确认接口，由后端明确执行 commit：

```text
POST /minimax/chat-client/mcp/write/confirm
```

也可以跳过确认流程，直接用 commit 模式启动，让模型工具调用时立即写入：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mcp -Dminimax.mcp.write-mode=commit
```

新增 Tool：

```text
createMcpLearningResource(id, topic, title, summary, nextAction)
updateMcpLearningResource(id, topic, title, summary, nextAction)
```

调用链路：

```text
前端问题
 -> MiniMax 判断用户明确要求保存 / 记录 / 沉淀学习资源
 -> createMcpLearningResource
 -> LearningMcpService
 -> Spring AI MCP Client ToolCallbackProvider
 -> minimax-learning-mcp-server
 -> createLearningResource
 -> learning-resources.json
 -> 前端调试区展示 MCP 写入工具调用
```

推荐测试：

```text
把 Tool 和 MCP 的区别保存成一条学习资源，资源 ID 用 mcp-tool-vs-mcp，主题用 MCP。
```

预期：

```text
toolCalls 中出现 createMcpLearningResource。
dry-run 时 MCP 调试信息 mode = MCP_WRITE_DRY_RUN，不会落盘。
dry-run 调试区会出现“确认写入”按钮。
点击确认写入后，确认接口返回 REAL_MCP，并写回 MCP Server。
commit 启动模式下，模型工具调用会直接返回 REAL_MCP 并写回 MCP Server。
打开 http://localhost:19000/index.html 可以看到新资源。
```

## 建议测试用例

打开：

```text
http://localhost:8080/index.html
```

测试：

```text
我是初学者，想学习 Agent，给我一个 30 分钟计划。
```

继续追问：

```text
基于我刚才的学习方向，下一步应该学什么？
```

预期调试结果：

- `intent` 会显示 Planner 识别出的意图。
- `agentSteps` 会包含 `MEMORY_READ` 和 `MEMORY_WRITE`。
- `toolCalls` 会显示模型本轮调用了哪些工具。
- `memoryBefore` 和 `memoryAfter` 会显示学习记忆的变化。
- 重启应用后再次提问，`memoryBefore` 应该能读取到上次保存在 `memory/learning-memory.json` 中的学习记忆。
- 切换不同用户 ID 后，不同用户的关注主题和对话轮次应该互不影响。
- 点击 `查看记忆` 可以展示当前用户的长期 Memory。
- 点击 `清空记忆` 后，该用户在 JSON 文件中的长期 Memory 会重置。
- 点击 `清空` 只清空短期上下文，不会重置 JSON 文件中的长期 Memory。
- 询问当前项目 README、源码结构或调用链时，`toolCalls` 中应出现 `searchLearningDocs`。
- 明确要求保存学习资源时，`toolCalls` 中应出现 `createMcpLearningResource`，并且 MCP Server 资源管理页面能看到新资源。
- 同步模式下，调试区应展示 `Graph 节点`，包含 Receive、Memory Read、Planner、Model Call 等节点。
- 流式模式下，回答应逐步输出，并在调试区展示 Graph、Tool 调用和 Memory 变化。

多用户测试：

```text
用户 ID：user-a
问题：我是初学者，想学习 Agent。
```

```text
用户 ID：user-b
问题：我是进阶开发者，想学习 RAG。
```

预期 `memory/learning-memory.json` 中会出现 `user-a` 和 `user-b` 两份独立记忆。

Simple RAG 测试：

```text
根据当前 minimax-chat 项目，解释 Tool、Skill、Agent、Memory 的调用关系。
```

```text
查看当前项目 README，说明这个项目已经演进到哪一步。
```

预期前端调试区会显示 `searchLearningDocs` 工具调用，并返回本地文档检索摘要。

## 接口测试矩阵

推荐使用模块根目录下的 HTTP 用例文件进行回归测试：

```text
minimax-chat.http
minimax-tool-calling.http
```

### 全量回归用例

| 编号 | 测试目标 | HTTP 用例 | 请求接口 | 预期结果 |
| --- | --- | --- | --- | --- |
| 01 | 验证基础 ChatModel 同步调用 | `minimax-chat.http` | `GET /minimax/chat-model/simple/chat` | 返回普通文本回答 |
| 02 | 验证基础 ChatModel 流式调用 | `minimax-chat.http` | `GET /minimax/chat-model/stream/chat` | 返回 `text/event-stream` 文本流 |
| 03 | 验证自定义 ChatOptions | `minimax-chat.http` | `GET /minimax/chat-model/custom/chat` | 使用 Controller 内自定义模型参数返回回答 |
| 04 | 验证 ChatClient 同步调用 | `minimax-chat.http` | `GET /minimax/chat-client/simple/chat` | 返回普通文本回答 |
| 05 | 验证 ChatClient 流式调用 | `minimax-chat.http` | `GET /minimax/chat-client/stream/chat` | 返回基础流式文本 |
| 06 | 验证 Tool Calling 时间工具 | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | `toolCalls` 中出现 `getCurrentTime` |
| 07 | 验证 Skill 学习计划 | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | `toolCalls` 中出现 `generateDailyPlan` |
| 08 | 验证多轮上下文 | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | 回答能结合请求体中的 `history` |
| 09 | 验证 Simple RAG | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | `toolCalls` 中出现 `searchLearningDocs` |
| 10 | 验证 Graph 调试节点 | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | 响应中包含 `graphSteps` |
| 11 | 验证流式 SSE 调试 | `minimax-chat.http` | `POST /minimax/chat-client/conversation/stream` | 依次返回 `debug`、`message`、`done` 事件 |
| 12 | 查看用户 Memory | `minimax-chat.http` | `GET /minimax/chat-client/memory` | 返回指定 `userId` 的长期 Memory |
| 13 | 清空用户 Memory | `minimax-chat.http` | `DELETE /minimax/chat-client/memory` | 指定 `userId` 的 Memory 被重置并写回 JSON |
| 14 | 写入 user-a Memory | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | `user-a` 关注主题更新为 Agent |
| 15 | 写入 user-b Memory | `minimax-chat.http` | `POST /minimax/chat-client/conversation/chat` | `user-b` 关注主题更新为 RAG |
| 16 | 验证多用户隔离 | `minimax-chat.http` | `GET /minimax/chat-client/memory` | `user-a` 和 `user-b` 记忆互不影响 |
| 17 | 查看 Agent 执行报告 | 页面按钮或 HTTP | `GET /minimax/chat-client/report/runs?limit=5` | 返回最近执行链路、意图、Tool、MCP 和 Memory 摘要 |
| 18 | 查看 Agent 规则评估 | 页面按钮或 HTTP | `GET /minimax/chat-client/evaluation/runs?limit=5` | 返回每轮规则评分和检查项 |

### 专项能力用例

| 能力 | HTTP 用例 | 示例问题 | 重点观察 |
| --- | --- | --- | --- |
| 时间 Tool | `minimax-tool-calling.http` | `现在北京时间几点？` | `toolCalls.name = getCurrentTime` |
| 学习建议 Skill | `minimax-tool-calling.http` | `下一步应该怎么学习 Agent？` | `toolCalls.name = generateLearningAdvice` |
| 今日计划 Skill | `minimax-tool-calling.http` | `给我今天 30 分钟学习计划` | `toolCalls.name = generateDailyPlan` |
| 概念解释 Skill | `minimax-tool-calling.http` | `解释 Tool、Skill、Agent、Graph 的区别` | `toolCalls.name = explainConcept` |
| 本地文档 RAG | `minimax-tool-calling.http` | `根据当前项目 README 和源码说明调用关系` | `toolCalls.name = searchLearningDocs` |
| Graph 节点 | `minimax-tool-calling.http` | `当前 Agent 的 Graph 节点有哪些？` | 响应中包含 `graphSteps` |
| 流式调试 | `minimax-tool-calling.http` | `请流式解释 Tool、RAG 和 Graph 的关系` | SSE 中包含 `debug/message/done` |

### 回归测试顺序

建议每次较大改动后按以下顺序测试：

```text
1. 基础模型接口：01-05
2. Agent 同步链路：06-10
3. 流式 SSE 链路：11
4. Memory 管理：12-13
5. 多用户隔离：14-16
6. Tool/RAG/Graph 专项：minimax-tool-calling.http
7. Agent 执行报告：GET /minimax/chat-client/report/runs?limit=5
8. Agent 规则评估：GET /minimax/chat-client/evaluation/runs?limit=5
```

### 关键断言

同步接口 `/conversation/chat` 的响应应包含：

```json
{
  "content": "...",
  "intent": "MIXED",
  "memoryBefore": {},
  "memoryAfter": {},
  "graphSteps": [],
  "agentSteps": [],
  "toolCalls": []
}
```

流式接口 `/conversation/stream` 应包含：

```text
event:debug
event:message
event:done
```

Memory 持久化文件应按用户 ID 分组：

```json
{
  "user-a": {},
  "user-b": {}
}
```

## 15. Agent 执行报告

每轮同步对话、流式对话、官方 ReactAgent 和官方 StateGraph 调用结束后，`AgentRunReportService` 会把本轮执行过程写入：

```text
report/agent-runs.json
```

报告内容包括：

- 用户 ID、用户问题、链路模式和历史上下文条数
- Planner 识别出的意图
- Memory 调用前后状态
- Agent 步骤、Graph 节点和 Tool 调用次数
- MCP 模式、是否存在待确认写入
- 最终回答摘要

查看最近 5 条报告：

```http
GET /minimax/chat-client/report/runs?limit=5
```

清空报告：

```http
DELETE /minimax/chat-client/report/runs
```

前端页面也提供“查看报告”按钮，用于快速复盘最近几轮 Agent 执行链路。这个能力为下一步做 Agent Evaluation 打基础。

## 16. Agent 规则评估

每轮执行报告保存后，`AgentEvaluationService` 会基于 `AgentRunReport` 自动生成规则评估，并写入：

```text
report/agent-evaluations.json
```

当前评估不额外调用大模型，先使用确定性规则检查：

- Planner 是否返回有效意图
- 最终回答摘要是否为空
- Memory 是否在本轮后发生变化
- 时间类问题是否调用 `getCurrentTime`
- 保存/沉淀资源诉求是否触发 MCP 写入或待确认写入
- 项目知识类问题是否调用 RAG 或 MCP 检索

查看最近 5 条评估：

```http
GET /minimax/chat-client/evaluation/runs?limit=5
```

清空评估：

```http
DELETE /minimax/chat-client/evaluation/runs
```

前端页面提供“查看评估”按钮。评估结果中的 `score/maxScore` 可以帮助你判断本轮 Agent 是否按预期执行，`checks` 会列出每个规则的通过、未通过或跳过原因。

## 17. Evaluation Dashboard

前端页面在聊天窗口下方提供轻量 Dashboard，用于观察最近 5 轮 Agent 规则评估趋势。

Dashboard 展示：

- 最近 5 轮平均得分
- PASS 数量
- WARN / FAIL 数量
- 最近一次评估等级
- 每轮链路模式、意图、用户和需要关注的检查项

页面加载时会自动读取：

```http
GET /minimax/chat-client/evaluation/runs?limit=5
```

每次完成一轮对话后也会自动刷新。也可以点击“刷新评估”手动更新。

这一层的目标是把 Agent 工程链路闭环可视化：

```text
Agent 执行
 -> Report
 -> Evaluation
 -> Dashboard
 -> 继续优化 Planner / Tool / Memory / RAG / MCP
```

## 18. LLM-as-Judge AI 评审

规则评估只能判断工具、Memory、MCP 等链路是否按预期执行，不能很好判断回答质量。`AgentJudgeService` 提供手动触发的 LLM-as-Judge 能力，会读取最近一条 `AgentRunReport`，调用 MiniMax-M2.7 进行质量评审，并写入：

```text
report/agent-judges.json
```

AI 评审维度：

- `relevanceScore`：回答是否贴合用户问题
- `helpfulnessScore`：回答是否有帮助、是否可执行
- `clarityScore`：表达是否清晰
- `groundingScore`：是否合理基于 Tool、RAG、MCP、Memory、Graph 等上下文
- `riskNotes`：潜在问题、幻觉风险或工具使用不足
- `improvementAdvice`：下一步如何优化 Agent

触发最近一轮 AI 评审：

```http
POST /minimax/chat-client/judge/latest
```

查看最近 5 条 AI 评审：

```http
GET /minimax/chat-client/judge/runs?limit=5
```

清空 AI 评审：

```http
DELETE /minimax/chat-client/judge/runs
```

前端页面提供“AI 评审”按钮。这个能力是手动触发的，因为它会额外调用一次大模型。

## 19. Workflow 模式

`LearningWorkflowService` 提供一个轻量、确定性的 Workflow 编排层，用来学习“固定流程”和“Agent 自主决策”的区别。

当前 Workflow 已改成学习辅导业务流程：

```text
识别学习目标
 -> 判断当前学习阶段
 -> 判断是否需要项目上下文
 -> 选择学习路径
 -> 生成学习计划
 -> 给出验证任务
 -> 生成下一步建议
```

Workflow 固定的是学习辅导过程，不再只是技术调用链。每个节点会根据用户问题、Memory、Planner 意图、Tool/RAG/MCP 调用情况生成真实业务含义。回答生成仍复用现有 `LearningAgentService`，因此不会重复实现 Tool、Skill、RAG、MCP、Memory 逻辑。

为了避免把通用概念问题误收窄为当前项目实现，Workflow 会区分两类问题：

- 通用概念学习：例如“Workflow、Multi-Agent 和 AgentGraph 区别是什么？”，先解释通用定义、适用场景、设计原则和验证方法。
- 当前项目实现分析：例如“基于当前 minimax-chat 项目源码说明 Workflow 如何实现”，再优先结合 RAG/MCP、README 和源码上下文。

调用入口：

```http
POST /minimax/chat-client/workflow/chat
```

前端“Agent 链路模式”新增：

```text
Workflow
```

Workflow 调用结束后同样会进入：

```text
AgentRunReport
 -> AgentEvaluation
 -> Evaluation Dashboard
 -> 可手动触发 AI 评审
```

这个阶段用于理解：

- Workflow 关注固定步骤和状态流转
- Agent 关注模型自主选择工具和生成回答
- Graph 可以表达 Workflow，也可以承载未来 Multi-Agent 节点

## 20. Multi-Agent 模式

`LearningCoordinatorAgent` 提供第一版串行 Multi-Agent 学习辅导链路。它不是并发多智能体，而是先把多个角色的职责拆清楚，方便观察和测试。

当前角色：

```text
CoordinatorAgent
 -> PlannerAgent
 -> ResearchAgent
 -> TeacherAgent
 -> ReviewerAgent
 -> CoordinatorAgent
```

职责说明：

- `PlannerAgent`：识别学习意图，拆解学习子任务
- `ResearchAgent`：判断本轮是通用概念学习还是当前项目实现分析；通用概念问题不强制绑定项目源码，项目实现问题才收集 RAG 和 MCP 上下文
- `TeacherAgent`：复用 `LearningAgentService` 生成教学回答，并显式注入 Planner 与 ResearchAgent 产出的 RAG/MCP 上下文
- `ReviewerAgent`：检查回答是否非空、是否包含实践/测试/下一步、项目问题是否使用工具
- `CoordinatorAgent`：串联角色并输出最终回答与协作摘要

优化说明：

- `AgentRunReport` 同时保存 `answerSummary` 和 `answerContent`。`answerSummary` 用于列表展示，`answerContent` 保留完整回答，供 AI Judge 评审使用。
- `ResearchAgent` 新增范围判断：`GENERAL_CONCEPT` 用于通用 Workflow / AgentGraph / Multi-Agent 学习，`PROJECT_IMPLEMENTATION` 用于当前项目源码和实现分析。
- `TeacherAgent` 会根据 ResearchAgent 的范围调整回答方式：通用概念先讲原理，项目映射只作为补充；项目实现问题才优先使用当前项目上下文。
- 当真实 MCP 不可用时，`ResearchAgent` 会明确说明已使用 Mock/Fallback，并提示检查 `mcp` profile、MCP Server 端口、`spring.ai.mcp.client` 配置和 `ToolCallbackProvider`。
- `TeacherAgent` 会基于 ResearchAgent 的 RAG/MCP 摘要生成回答，减少“ResearchAgent 收集了资料但回答没用上”的问题。

调用入口：

```http
POST /minimax/chat-client/multi-agent/chat
```

前端“Agent 链路模式”新增：

```text
Multi-Agent
```

Multi-Agent 调用结束后同样会进入：

```text
AgentRunReport
 -> AgentEvaluation
 -> Evaluation Dashboard
 -> 可手动触发 AI 评审
```

这一阶段用于理解：

- 单 Agent 是一个智能体自己完成任务
- Workflow 是固定业务流程
- Multi-Agent 是多个角色串行协作
- 后续可以把 Multi-Agent 角色放进 Workflow 或 AgentGraph 节点里
