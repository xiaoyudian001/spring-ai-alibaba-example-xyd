# Spring AI Alibaba Agent 学习版

这个仓库已经按 Agent 与 Skill 学习路线做了瘦身，只保留 MiniMax、Tool Calling、Agent/Skill、Agentic RAG、SQL Agent、Sequential Agent、Graph、MCP 和 Multi-Agent 相关示例。

## 保留模块

| 模块 | 学习重点 |
|---|---|
| `spring-ai-alibaba-chat-example/minimax-chat` | MiniMax `ChatModel`、`ChatClient`、同步与流式调用 |
| `spring-ai-alibaba-tool-calling-example` | `@Tool`、`@ToolParam`、`FunctionToolCallback`、工具调用基础 |
| `spring-ai-alibaba-agent-example/react-agent-example` | `ReactAgent`、工具注册、`MemorySaver`、Human-in-the-loop、拦截器 |
| `spring-ai-alibaba-agent-example/skills-agent-example` | `SkillsInterceptor`、`SKILL.md`、动态 Skill 加载 |
| `spring-ai-alibaba-agent-example/rag-agent-example` | Agentic RAG、知识检索工具、向量检索 |
| `spring-ai-alibaba-agent-example/sql-agent-example` | SQL Agent、数据库工具、安全查询约束 |
| `spring-ai-alibaba-agent-example/adk-samples-llm-auditor` | `SequentialAgent`、审稿/修订 Agent、联网搜索工具 |
| `spring-ai-alibaba-graph-example/react` | Graph 版 ReAct Agent |
| `spring-ai-alibaba-graph-example/mcp-node` | MCP 工具按 Graph 节点分配 |
| `spring-ai-alibaba-graph-example/multiagent-openmanus` | Planning Agent、Supervisor Agent、Executor Agent |
| `spring-ai-alibaba-graph-example/big-tool` | 大量工具场景下的工具检索与筛选 |

## 建议学习顺序

1. 跑通 `minimax-chat`，确认 MiniMax API Key 和基础对话。
2. 学 `tool-calling-example`，理解工具如何暴露给模型。
3. 学 `react-agent-example`，把工具、记忆、审批、拦截器组合成 Agent。
4. 学 `skills-agent-example`，理解 Skill 是给 Agent 的领域流程说明，Tool 才是执行能力。
5. 学 `rag-agent-example`、`sql-agent-example`、`adk-samples-llm-auditor`，掌握典型业务 Agent。
6. 学 `graph/react`、`mcp-node`、`multiagent-openmanus`、`big-tool`，进入工作流和多智能体编排。

## 运行提示

当前环境需要 Java 17+ 和 Maven。示例通常需要配置模型 API Key：

```bash
set MINIMAX_API_KEY=your_minimax_api_key
set AI_DASHSCOPE_API_KEY=your_dashscope_api_key
```

| module | purpose | command | required services | env vars | env template | entry |
|---|---|---|---|---|---|---|
| spring-ai-alibaba-helloworld | basic chat and advisor examples | `mvn -pl spring-ai-alibaba-helloworld spring-boot:run` | none | `AI_DASHSCOPE_API_KEY` | — | [README](./spring-ai-alibaba-helloworld/README.md) |
| spring-ai-alibaba-chat-example/dashscope-chat | DashScope chat basics | `mvn -pl spring-ai-alibaba-chat-example/dashscope-chat spring-boot:run` | none | `AI_DASHSCOPE_API_KEY` | — | [README](./spring-ai-alibaba-chat-example/dashscope-chat/README.md) |
| spring-ai-alibaba-image-example/dashscope-image | DashScope image generation | `mvn -pl spring-ai-alibaba-image-example/dashscope-image spring-boot:run` | none | `AI_DASHSCOPE_API_KEY` | — | [README](./spring-ai-alibaba-image-example/dashscope-image/README.md) |
| spring-ai-alibaba-mcp-example | MCP demo | `mvn -pl spring-ai-alibaba-mcp-example spring-boot:run` | none/local mcp tool | model api key | [`.env.example`](./spring-ai-alibaba-mcp-example/.env.example) | [README](./spring-ai-alibaba-mcp-example/README.md) |
| spring-ai-alibaba-rag-example | RAG demo | `mvn -pl spring-ai-alibaba-rag-example spring-boot:run` | vector db (optional by profile) | model api key, embedding model | [`.env.example`](./spring-ai-alibaba-rag-example/.env.example) | [README](./spring-ai-alibaba-rag-example/README.md) |
| spring-ai-alibaba-tool-calling-example | tool calling | `mvn -pl spring-ai-alibaba-tool-calling-example spring-boot:run` | none | model api key, map api key | [`.env.example`](./spring-ai-alibaba-tool-calling-example/.env.example) | [README](./spring-ai-alibaba-tool-calling-example/README.md) |

## 常用配置键速查
运行某个模块：

```bash
cd spring-ai-alibaba-chat-example/minimax-chat
mvn spring-boot:run
```

如果只使用 MiniMax，可优先从 `minimax-chat` 开始；后续 Agent 示例默认多为 DashScope，可再逐步替换为 MiniMax `ChatModel`。
| 配置键 | 常见模块 | 说明 |
|---|---|---|
| `AI_DASHSCOPE_API_KEY` | `spring-ai-alibaba-helloworld`、DashScope chat/image、tool calling、evaluation、很多 graph/rag 示例 | DashScope 兼容模型最常见的 API Key |
| `OPENAI_API_KEY` | `spring-ai-alibaba-chat-example/openai-chat`、`spring-ai-alibaba-chat-example/vllm-chat` | OpenAI 兼容接口示例常用 |
| `AI_OPENAI_API_KEY` | `spring-ai-alibaba-image-example/openai-image` | OpenAI 图片生成示例使用 |
| `AI_DEEPSEEK_API_KEY` | `spring-ai-alibaba-chat-example/deepseek-chat`、`spring-ai-alibaba-mem0-example` | DeepSeek 相关示例使用 |
| `MINIMAX_API_KEY` | `spring-ai-alibaba-chat-example/minimax-chat` | MiniMax 模型示例使用 |
| `ZHIPUAI_API_KEY` | `spring-ai-alibaba-chat-example/zhipuai-chat` | 智谱模型示例使用 |
| `BAIDU_MAP_API_KEY` | `spring-ai-alibaba-tool-calling-example` | 地图工具调用示例需要 |

## 常见启动问题 / Troubleshooting

- `AI_DASHSCOPE_API_KEY` 未设置 / Missing `AI_DASHSCOPE_API_KEY`: 先确认环境变量已在当前 shell 或 IDE 中生效，再重新启动示例。
- 端口被占用 / Port already in use: 检查对应模块 `application.yml` 中的 `server.port`，释放端口或改端口后重试。
- 本地依赖未启动 / Required local services not running: RAG、MCP、向量库或 Docker 相关示例通常需要先启动对应的中间件或容器。
- 模块里暂时没有 `.env.example` / No `.env.example` in a module yet: 优先查看该模块 README 和 `src/main/resources/application.yml`，确认真实的变量名和依赖服务。
