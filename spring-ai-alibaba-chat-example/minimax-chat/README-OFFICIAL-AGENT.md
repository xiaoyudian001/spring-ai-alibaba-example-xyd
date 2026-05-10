# 官方 Agent Framework 接入说明

本阶段在 `minimax-chat` 中新增了一个并行的官方 Agent 调用链路，用于学习和对比 Spring AI Alibaba 官方 `ReactAgent`。

## 新增内容

### 1. Maven 依赖

`minimax-chat/pom.xml` 新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
</dependency>
```

该依赖由根项目 BOM 管理版本，避免使用 `1.1.2.0-SNAPSHOT`。

### 2. official 包

新增包：

```text
com.alibaba.cloud.ai.official
```

包含：

```text
OfficialLearningAgentConfiguration
OfficialLearningAgentService
OfficialLearningAgentResult
OfficialLearningToolCallbacks
```

职责划分：

```text
OfficialLearningToolCallbacks
 -> 把现有 MiniMaxLearningTools 包装成官方 ToolCallback

OfficialLearningAgentConfiguration
 -> 构建官方 ReactAgent

OfficialLearningAgentService
 -> 读取 Memory
 -> Planner 识别意图
 -> 调用官方 ReactAgent
 -> 收集 Tool 调用信息
 -> 更新 Memory

OfficialLearningAgentResult
 -> 返回 content、intent、memoryBefore、memoryAfter、agentSteps、toolCalls、rawState
```

### 3. 新增接口

```text
POST /minimax/chat-client/official-agent/chat
```

请求体：

```json
{
  "userId": "user-a",
  "message": "请使用官方 ReactAgent 告诉我现在北京时间，并给我一个 30 分钟 Agent 学习计划。",
  "history": []
}
```

响应重点字段：

```json
{
  "content": "...",
  "intent": "MIXED",
  "memoryBefore": {},
  "memoryAfter": {},
  "agentSteps": [],
  "toolCalls": [],
  "rawState": {}
}
```

## 当前两条链路的区别

### 原有学习链路

```text
前端
 -> Controller
 -> LearningAgentService
 -> LearningMemoryService
 -> LearningIntentPlanner
 -> MiniMax ChatClient + @Tool
 -> LearningGraphService 调试步骤
 -> Memory 更新
 -> 前端调试区展示
```

特点：

- 调试信息最完整
- 前端已完全适配
- Graph 目前是轻量调试图，不是官方 StateGraph

### 官方 Agent 链路

```text
HTTP 测试请求
 -> Controller
 -> OfficialLearningAgentService
 -> LearningMemoryService
 -> LearningIntentPlanner
 -> 官方 ReactAgent
 -> 官方 ToolCallback
 -> MiniMaxLearningTools
 -> Memory 更新
 -> JSON 响应
```

特点：

- 已开始使用 Spring AI Alibaba 官方 Agent Framework
- Tool 入口从 `@Tool` 对象适配为官方 `ToolCallback`
- 当前先提供独立接口，不影响原有前端主链路

## 如何测试

推荐使用：

```text
minimax-official-agent.http
```

测试顺序：

```text
1. 执行 01，验证官方 ReactAgent 可以调用时间工具和学习计划工具。
2. 执行 02，验证官方 ReactAgent 可以调用 searchLearningDocs 检索当前项目资料。
3. 执行 03，对比原有 LearningAgent 链路，观察两套返回结构差异。
```

关键观察点：

```text
toolCalls 不为空：说明官方 ReactAgent 成功触发了工具。
memoryAfter.round 增加：说明官方链路也写入了长期 Memory。
rawState 不为空：说明拿到了官方 Graph/Agent 执行后的状态数据。
```

## 下一步建议

下一阶段可以把当前轻量 `LearningGraphService` 升级为真正的官方 `StateGraph`：

```text
Memory Read 节点
 -> Planner 节点
 -> ReactAgent 节点
 -> Memory Write 节点
 -> Response 节点
```

这样项目就会从“官方 ReactAgent 接入”继续演进到“官方 Graph 编排接入”。

## 官方 Graph 编排阶段

本阶段继续新增了独立的官方 `StateGraph` 链路：

```text
POST /minimax/chat-client/official-graph/chat
```

新增包：

```text
com.alibaba.cloud.ai.officialgraph
```

包含：

```text
OfficialLearningGraphService
OfficialLearningGraphResult
```

当前官方 Graph 节点：

```text
START
 -> memory_read
 -> planner
 -> react_agent
 -> memory_write
 -> response
 -> END
```

每个节点职责：

```text
memory_read
 -> 根据 userId 从 LearningMemoryService 读取长期学习记忆

planner
 -> 使用 LearningIntentPlanner 识别用户意图

react_agent
 -> 调用官方 ReactAgent
 -> ReactAgent 内部继续决定是否触发 ToolCallback

memory_write
 -> 根据本轮问题和意图更新长期 Memory

response
 -> 汇总 content、graphSteps、toolCalls、memoryBefore、memoryAfter、rawState
```

这条链路和前一个官方 ReactAgent 接口的区别是：

```text
official-agent/chat
 -> 直接调用官方 ReactAgent

official-graph/chat
 -> 先进入官方 StateGraph
 -> 再由 Graph 的 react_agent 节点调用官方 ReactAgent
```

也就是说，现在项目里已经有三套可对比链路：

```text
1. /conversation/chat
   手写 LearningAgentService + 轻量 Graph 调试

2. /official-agent/chat
   官方 ReactAgent

3. /official-graph/chat
   官方 StateGraph + 官方 ReactAgent 节点
```

### 官方 Graph 测试

使用：

```text
minimax-official-agent.http
```

新增用例：

```text
04 官方 Graph Framework：StateGraph 编排调用
05 官方 Graph Framework：检索当前项目资料
```

关键观察点：

```text
graphSteps
 -> 应包含 memory_read、planner、react_agent、memory_write、response

toolCalls
 -> 应显示 ReactAgent 节点内触发的工具

graphDefinition
 -> 应返回 Mermaid 图定义，说明 StateGraph 已成功编译

memoryAfter
 -> 应显示长期学习记忆被更新
```

## Mock MCP Node 阶段

本阶段新增了本地 mock MCP 能力，用于先理解 MCP 在 Agent/Graph 中的位置，不启动真实 MCP Server。

新增包：

```text
com.alibaba.cloud.ai.mcp
```

包含：

```text
LearningMcpService
McpLearningResource
```

新增 Tool：

```text
searchMcpLearningResources
```

调用关系：

```text
用户问题
 -> 模型判断需要 MCP 学习资源
 -> searchMcpLearningResources
 -> LearningMcpService
 -> 返回 mock MCP 资源
 -> 模型整合成最终回答
```

官方 Graph 现在增加了 `mcp_node`：

```text
START
 -> memory_read
 -> planner
 -> mcp_node
 -> react_agent
 -> memory_write
 -> response
 -> END
```

`mcp_node` 负责模拟 MCP 资源预取，`react_agent` 仍然可以按需继续调用 `searchMcpLearningResources` 工具。

测试用例：

```text
minimax-official-agent.http
```

新增：

```text
06 Mock MCP：手写 Agent 触发 MCP 学习资源工具
07 Mock MCP：官方 ReactAgent 触发 MCP 学习资源工具
08 Mock MCP：官方 StateGraph 包含 mcp_node
```

推荐问题：

```text
通过 MCP 给我列出 Agent 和 Graph 的学习资源，并说明下一步怎么学。
```

预期：

```text
toolCalls 中出现 searchMcpLearningResources
官方 Graph 的 graphSteps 中出现 mcp_node
回答中出现 mock MCP 学习资源
```
