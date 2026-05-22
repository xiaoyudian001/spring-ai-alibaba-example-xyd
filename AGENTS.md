# AGENTS.md

此文件为 Codex 在处理本代码库时提供协作约束。请以后续实际代码为准，优先围绕 `spring-ai-alibaba-chat-example/minimax-chat` 智能客服 Agent 系统推进。

最后更新：2026-05-22 11:36:13

## 当前项目定位

本仓库源自 Spring AI Alibaba 示例工程，但当前学习和开发主线已经收敛为“小雨点智能客服 Chat-Bot”：

- 面向闲鱼类、微信类客服场景。
- 接入 MiniMax-M2.7，使用 OpenAI 兼容 Chat Completions 配置。
- 基于 Spring AI Alibaba 实现 ReactAgent、StateGraph、SequentialAgent。
- 支持 Tool Calling、MCP、RAG、Memory、长期记忆、短期上下文、运行报告、规则评估、AI Judge、审计日志和待审核任务。
- 前端拆分为客户主聊天页和运营调试工作台。客户侧隐藏技术细节，工作台侧展示 Agent、Tool、Memory、RAG、MCP、Graph、报告和评估信息。

当前重点模块：

- `spring-ai-alibaba-chat-example/minimax-chat`：智能客服主应用。
- `spring-ai-alibaba-chat-example/minimax-customer-mcp-server`：智能客服 MCP Server，暴露商品、订单、物流、议价、退款、售后、工单和人工接管工具。

其它 chat 示例模块可以参考，但不要在无明确需求时改动。

## 主要架构

### minimax-chat 后端结构

```text
src/main/java/com/alibaba/cloud/ai
├── audit       # 操作审计
├── controller  # HTTP API 和页面入口
├── customer    # 智能客服核心业务：Agent、Graph、Tool、Skill、RAG、Memory、MCP 门面
├── evaluation  # 规则评估
├── judge       # LLM-as-Judge
├── mcp         # MCP 调试信息模型
├── report      # Agent 运行报告
├── security    # 工作台鉴权
├── tool        # Tool 调用调试记录
└── web         # 统一异常和 Web 配置
```

### 核心调用链路

客户主入口：

```text
前端问题
 -> MiniMaxChatClientController
 -> CustomerServiceAssistantService
 -> CustomerServiceIntentPlanner
 -> 简单寒暄：CustomerDirectChatService 直连 MiniMax-M2.7
 -> 业务问题：CustomerServiceAgentService / CustomerServiceMultiAgentService
 -> CustomerFactCollectorService 预取商品、订单、物流、售后、RAG 事实
 -> ReactAgent / SequentialAgent + Tool Calling
 -> CustomerMemoryService 更新 MySQL 长期记忆
 -> AgentRunReportService 保存报告
 -> 前端展示回答和 Tips 调试信息
```

Graph 模式：

```text
前端问题
 -> CustomerServiceGraphService
 -> StateGraph
 -> memory_read
 -> intent_plan
 -> skill_select
 -> fact_collect
 -> react_agent
 -> risk_review
 -> memory_write
 -> response
```

MCP 工具链路：

```text
CustomerServiceTools
 -> CustomerMcpService
 -> 发现真实 MCP Tool：调用 minimax-customer-mcp-server
 -> 未发现真实 MCP Tool：回退 MockCustomerDataService
```

RAG 当前默认是本地关键词高召回知识库，`application-vector.yml` 只保留真实 VectorStore 接入边界。后续做 RAG 优化时，应优先完善文档分组、版本、切分、召回分数和来源展示，再替换真实向量库。

## 开发优先级

默认以 `minimax-chat` 为主，不要把项目重新拉回早期学习助手 Demo。

当前 v2.0 待办优先级以 `spring-ai-alibaba-chat-example/minimax-chat/README.md` 为准：

- P1：Memory 经验复用。
- P1：高级 Agent 编排。
- P1：工作台增强。
- P2：测试与验收闭环。

已经完成的 P0 不要重新写回待开发任务：

- 后端事实预取 `CustomerFactCollectorService` / `CustomerFactBundle`。
- Direct LLM、ReactAgent、SequentialAgent、StateGraph 注入事实包。
- 前端 Tips 展示 Fact 来源。
- 真实客服 MCP Server 模块。
- `mcp` profile 可连接 `minimax-customer-mcp-server`。
- RAG 知识治理，包括知识分组、版本、启停、MySQL 文档与 Chunk 存储、召回分数和 baseline 对比。

## 运行与构建

项目要求：

- Java 17+
- Maven 3.6+
- 本地 MySQL：`localhost:3306`
- 可选 Redis：`localhost:6379`
- MiniMax API Key：`MINIMAX_API_KEY`

推荐从仓库根目录执行 Maven：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am -DskipTests compile
```

同时编译客服主应用和客服 MCP Server：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat,spring-ai-alibaba-chat-example/minimax-customer-mcp-server -am -DskipTests compile
```

启动主应用：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am spring-boot:run
```

启动真实客服 MCP Server：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-customer-mcp-server -am spring-boot:run
```

主应用启用 MCP Client：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am spring-boot:run "-Dspring-boot.run.profiles=mcp"
```

主应用启用 Redis 短期上下文：

```powershell
mvn -pl spring-ai-alibaba-chat-example/minimax-chat -am spring-boot:run "-Dspring-boot.run.profiles=redis"
```

主页面：

```text
http://localhost:8080/index.html
```

运营调试工作台：

```text
http://localhost:8080/dashboard.html
```

客服 MCP 健康检查：

```text
http://localhost:19001/customer-mcp/health
```

## 配置约定

- MySQL 已作为默认数据库，不再保留 H2 作为主线。
- `application.yml` 中 MySQL 默认固定为 `jdbc:mysql://localhost:3306/minimax_customer_service`，账号密码默认 `root/root`。
- Redis 配置在 `application-redis.yml`，默认 `localhost:6379`。
- MCP Client 只在 `mcp` profile 开启。
- `minimax-chat` 默认 MCP 地址为 `http://localhost:19001`，对应 `minimax-customer-mcp-server`。
- MiniMax 使用 `spring.ai.openai` 兼容配置，`base-url` 为 `https://api.minimaxi.com`，模型为 `MiniMax-M2.7`。

## 前端约定

- `index.html` 是客户主聊天页，保持简洁，不向客户暴露 ReactAgent、Graph、Multi-Agent、同步、流式等技术概念。
- 主页面可以保留用户 ID 和渠道入口，方便本地测试不同用户、不同来源，但表达要面向客服业务，不要变成技术演示面板。
- 技术细节放入 Tips 或 `dashboard.html`。
- `dashboard.html` 是运营调试工作台，可以展示链路、报告、评估、Judge、RAG、Memory、MCP、MySQL、Redis、审计和待审核任务。
- 页面样式应服务客服产品体验：安静、清晰、可扫描，避免大面积堆技术标签。

## 业务安全边界

高风险动作必须走待审核任务，不能让模型直接执行：

- 退款
- 赔付
- 取消订单
- 人工接管
- 修改地址
- 承诺额外优惠
- 投诉升级

实现时优先使用 `ApprovalTaskService`、`customer_approval_task` 和审计日志。即使接入真实 MCP，也应把高风险动作设计为“创建待审核任务”，而不是直接完成真实操作。

## 代码注释与枚举规范

后续新增或重构 Java 代码时，必须遵守以下规范：

- 所有新建类、接口、枚举、记录类都必须添加 JavaDoc，说明业务职责、使用场景或在调用链中的位置。
- 所有新建方法都必须添加 JavaDoc，说明方法作用、关键入参、返回值含义。
- 核心业务逻辑方法需要额外说明主要处理步骤、调用外部能力或副作用。
- 所有新建枚举类的每一个枚举属性都必须添加中文注释，说明该枚举值对应的业务含义和触发场景。
- JavaDoc 中统一标注作者和日期，日期必须包含时分秒，格式为 `yyyy-MM-dd HH:mm:ss`。

示例：

```java
/**
 * 根据用户输入识别客服意图，并返回后续 Workflow 或 Agent 可使用的业务意图。
 *
 * @param message 用户原始输入内容
 * @return 客服业务意图
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
public CustomerServiceIntent plan(String message) {
    return CustomerServiceIntent.GENERAL_CHAT;
}
```

枚举属性示例：

```java
public enum CustomerServiceIntent {

    /**
     * 商品咨询，用户询问商品是否还在、规格、价格、成色等售前问题。
     */
    PRODUCT_INQUIRY,

    /**
     * 退款请求，用户明确表达退款、退货或售后处理诉求。
     */
    REFUND_REQUEST

}
```

## 开发注意事项

- 修改前先读现有代码和 README，不要凭空设计一套新架构。
- 优先复用当前 `customer` 包内已有服务边界，不要重复创建平行体系。
- Tool 只作为模型调用入口，业务逻辑应尽量沉到 Service。
- Memory 长期状态走 MySQL，短期多轮上下文走 Redis，不要重新引入 JSON 作为主存储。
- RAG 默认本地关键词召回，做向量库时要保留召回来源、召回分数和 fallback 说明。
- MCP 不可用时必须能 fallback，本地测试不能因为外部 MCP 未启动而全链路失败。
- 前端客户主页面不要堆技术概念，技术调试信息放到 Tips 和工作台。
- 修改接口响应字段时，要同步更新前端渲染、HTTP 用例和 README。
- 每次较大功能完成后至少运行相关模块 `compile`；涉及测试逻辑时补充或运行对应测试。

## 常用验证用例

商品事实：

```text
这个 p-1001 商品还在吗？能介绍一下成色吗？
```

订单物流：

```text
帮我查一下订单 o-202605150001 的物流。
```

退款风控：

```text
订单 o-202605150001 我想退款，可以直接帮我退吗？
```

简单直连：

```text
你好，你是谁？
```

多用户 Memory：

```text
user-a 和 default-user 分别咨询不同商品，确认 Memory 和 Redis Context 不串数据。
```

真实 MCP：

```text
先启动 minimax-customer-mcp-server，再以 mcp profile 启动 minimax-chat。
GET /minimax/chat-client/customer-service/mcp/status 应返回 REAL_CUSTOMER_MCP_READY。
```

## 文档维护

- `spring-ai-alibaba-chat-example/minimax-chat/README.md` 是当前主线说明。
- 完成 README 中待开发任务后，应删除对应待开发条目，不要让文档和代码状态脱节。
- 新增重要功能时同步更新 README 的运行方式、接口、测试建议或 v2.0 待办。
- 不要把已废弃的早期学习助手说明重新写回主 README。
