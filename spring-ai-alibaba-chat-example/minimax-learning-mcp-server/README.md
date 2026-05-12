# MiniMax Learning MCP Server

这个模块是 `minimax-chat` 的真实 MCP Server 示例。

它不调用大模型，只负责通过 MCP 协议暴露学习资源工具。大模型仍然由 `minimax-chat` 使用 MiniMax-M2.7 调用。

## 模块职责

```text
minimax-learning-mcp-server
 -> 端口 19000
 -> 暴露 MCP Tools
 -> 从 learning-resources.json 读取和维护 Spring AI Alibaba 学习资源

minimax-chat
 -> 端口 8080
 -> 使用 MiniMax-M2.7
 -> 作为 MCP Client 连接 http://localhost:19000
 -> 让 Agent / Graph 调用 MCP 工具
```

## 暴露的 MCP Tools

```text
searchLearningResources(query, limit)
listLearningTopics()
getLearningResource(resourceId)
```

其中 `searchLearningResources` 是 `minimax-chat` 最常调用的工具。

## JSON 资源文件

学习资源放在：

```text
src/main/resources/learning-resources.json
```

每条资源结构如下：

```json
{
  "id": "mcp-agent",
  "topic": "Agent",
  "title": "ReactAgent 调用链",
  "summary": "Agent 负责把模型、工具、上下文和执行状态组织起来。",
  "nextAction": "测试 /minimax/chat-client/official-agent/chat 并观察 toolCalls。"
}
```

启动时 `LearningResourceRepository` 会优先读取 `learning.resources.file` 指向的 JSON 文件。如果文件不存在、为空或格式错误，会继续尝试 classpath 下的 `learning-resources.json`，最后再使用 Java 内置兜底资源，保证 MCP Server 仍然可以启动。

默认配置在 `application.yml` 中：

```yaml
learning:
  resources:
    file: src/main/resources/learning-resources.json
```

新增、修改、删除资源时，接口会把变更写回这个 JSON 文件。

## 资源管理接口

这些接口是普通 REST 接口，用来验证和维护 MCP Server 的学习资源数据：

```text
GET    /learning-mcp/resources
GET    /learning-mcp/resources/{id}
POST   /learning-mcp/resources
PUT    /learning-mcp/resources/{id}
DELETE /learning-mcp/resources/{id}
GET    /learning-mcp/resources/search?query=Agent&limit=3
```

其中 `POST`、`PUT`、`DELETE` 会写回 `learning-resources.json`。

`PUT /learning-mcp/resources/{id}` 以路径里的 `id` 为准，避免请求体里的 `id` 和 URL 中的 `id` 不一致。

## 资源管理页面

启动 MCP Server 后，可以直接打开：

```text
http://localhost:19000/index.html
```

页面能力：

```text
查看全部学习资源
按关键词和主题筛选资源
新增学习资源并写回 learning-resources.json
编辑学习资源并写回 learning-resources.json
删除学习资源并写回 learning-resources.json
查看 MCP Server 状态、资源数量和资源来源
```

推荐页面测试顺序：

```text
1. 打开 http://localhost:19000/index.html
2. 新增一条 topic 为 MCP 的测试资源
3. 搜索这条资源，确认列表可以过滤出来
4. 编辑标题或下一步建议，确认页面更新
5. 打开 learning-resources.json，确认文件已写回
6. 删除测试资源，确认文件同步删除
7. 回到 minimax-chat，通过真实 MCP 查询资源，确认 Agent / Graph 仍可正常使用
```

## 启动顺序

### 1. 启动 MCP Server

```bash
cd spring-ai-alibaba-chat-example/minimax-learning-mcp-server
mvn spring-boot:run
```

启动后先访问：

```text
http://localhost:19000/learning-mcp/health
```

预期：

```json
{
  "status": "UP",
  "server": "minimax-learning-mcp-server",
  "resourceSource": "file:.../src/main/resources/learning-resources.json",
  "resourceFile": ".../src/main/resources/learning-resources.json",
  "resourceCount": 7
}
```

也可以直接查看全部资源：

```text
http://localhost:19000/learning-mcp/resources
```

或者测试普通 REST 检索：

```text
http://localhost:19000/learning-mcp/resources/search?query=Agent%20Graph%20MCP&limit=3
```

也可以打开资源管理页面：

```text
http://localhost:19000/index.html
```

### 2. 开启 minimax-chat 的 MCP Client

推荐使用 `mcp` profile 启动：

```bash
cd spring-ai-alibaba-chat-example/minimax-chat
mvn spring-boot:run -Dspring-boot.run.profiles=mcp
```

`application-mcp.yml` 会把 MCP Client 开启：

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
```

确认连接地址：

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            learning:
              url: http://localhost:19000
```

### 3. 检查 MCP Client 状态

```text
http://localhost:8080/minimax/chat-client/mcp/status
```

预期：

```json
{
  "realMcpAvailable": true,
  "mode": "REAL_MCP_READY"
}
```

## 完整测试

使用：

```text
minimax-learning-mcp-server.http
```

建议按顺序执行：

```text
01 MCP Server 健康检查
02 MCP Server 查看全部 JSON 学习资源
03 MCP Server 新增学习资源并写回 JSON
04 MCP Server 查看单条学习资源
05 MCP Server 修改学习资源并写回 JSON
06 MCP Server 普通 REST 资源检索
07 MCP Server 删除测试学习资源并写回 JSON
08 minimax-chat MCP Client 状态检查
09 通过手写 Agent 调用真实 MCP Server
10 通过官方 ReactAgent 调用真实 MCP Server
11 通过官方 StateGraph 调用真实 MCP Server
```

页面测试可以配合 `.http` 使用：先用页面新增资源，再执行 `06 MCP Server 普通 REST 资源检索` 或 `09/10/11`，观察新增资源是否能被查询和 Agent 使用。

Graph 测试时重点观察：

```text
graphSteps
 -> mcp_node
 -> detail 包含 REAL_MCP

toolCalls
 -> searchMcpLearningResources
 -> arguments.source = REAL_MCP
```

## 调用链路

```text
前端问题
 -> minimax-chat Controller
 -> LearningAgent / Official ReactAgent / Official StateGraph
 -> searchMcpLearningResources
 -> LearningMcpService
 -> Spring AI MCP Client ToolCallbackProvider
 -> minimax-learning-mcp-server
 -> LearningResourceTool
 -> LearningResourceRepository
 -> learning-resources.json
 -> 返回真实 MCP 工具结果
 -> MiniMax-M2.7 整合回答
 -> 前端展示回答和调试信息
```
