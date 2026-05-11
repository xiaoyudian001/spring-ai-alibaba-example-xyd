# MiniMax Learning MCP Server

这个模块是 `minimax-chat` 的真实 MCP Server 示例。

它不调用大模型，只负责通过 MCP 协议暴露学习资源工具。大模型仍然由 `minimax-chat` 使用 MiniMax-M2.7 调用。

## 模块职责

```text
minimax-learning-mcp-server
 -> 端口 19000
 -> 暴露 MCP Tools
 -> 从 learning-resources.json 读取 Spring AI Alibaba 学习资源

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

启动时 `LearningResourceRepository` 会优先读取 `learning-resources.json`。如果文件不存在、为空或格式错误，会自动使用 Java 内置兜底资源，保证 MCP Server 仍然可以启动。

新增学习资源时，只需要修改 `learning-resources.json`，然后重启 `minimax-learning-mcp-server`。

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
  "resourceSource": "classpath:learning-resources.json",
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
03 MCP Server 普通 REST 资源检索
04 minimax-chat MCP Client 状态检查
05 通过手写 Agent 调用真实 MCP Server
06 通过官方 ReactAgent 调用真实 MCP Server
07 通过官方 StateGraph 调用真实 MCP Server
```

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
