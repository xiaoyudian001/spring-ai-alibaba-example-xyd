# 智能客服助手整改计划

## 1. 整改目标

当前 `minimax-chat` 已经具备多轮上下文、Tool Calling、Planner、Memory、RAG、MCP、Workflow、官方 Agent、官方 Graph、Multi-Agent 和调试信息等学习能力，但主线仍然偏“学习 Spring AI Alibaba”。下一阶段建议把它升级为一个真实通用需求：

```text
基于 Spring AI Alibaba + MiniMax 的智能客服助手
```

这个智能客服助手面向真实客服场景，而不是单纯回答技术学习问题。它可以逐步接入网页客服、闲鱼、微信、企业微信、小程序、工单系统、订单系统和知识库。

目标不是一次性接入所有平台，而是先建立一套可替换、可扩展、可测试的智能客服架构：

```text
多渠道消息接入
 -> 客服意图识别
 -> 用户与会话记忆
 -> RAG 检索知识库
 -> MCP 调用外部系统
 -> Skills 加载客服技能
 -> Workflow / AgentGraph 编排流程
 -> Multi-Agent 专家协作
 -> 人工接管与确认
 -> 生成客服回复并沉淀工单
```

## 2. 名称修正

后续文档和代码中统一使用：

```text
闲鱼
```

不要写成“咸鱼”。

## 3. 推荐业务定位

建议把项目定位为：

```text
Omni-channel AI Customer Service Agent
多渠道智能客服助手
```

第一阶段优先支持：

- 网页客服：当前前端页面直接复用，作为最容易测试的入口。
- 闲鱼 Mock 渠道：先模拟闲鱼买家咨询消息，后续再研究官方授权能力。
- 微信 Mock 渠道：先模拟微信公众号、企业微信或小程序客服消息，后续再接真实接口。

后续真实接入时必须使用平台官方授权接口，不建议通过爬虫、逆向、模拟个人 App 登录等方式接入闲鱼或微信。

## 4. 智能客服典型问题

智能客服助手应该面向真实用户问题：

```text
这个商品还在吗？
能便宜一点吗？
这件商品什么时候发货？
我的订单为什么还没到？
我想退款，怎么处理？
超过 7 天还能退吗？
帮我写一段礼貌的售后解释。
这个买家一直追问价格，应该怎么回复？
```

这些问题会自然触发不同能力：

- 商品咨询：查商品信息、库存、价格策略。
- 订单咨询：查订单、物流、发货状态。
- 售后咨询：查退款规则、售后政策、订单状态。
- 话术生成：根据平台、用户情绪、客服规范生成回复。
- 风险控制：涉及退款、赔付、取消订单时进入人工确认。

## 5. 总体架构

```text
网页客服 / 闲鱼 / 微信 / 企业微信 / 小程序
        |
        v
Channel Adapter 渠道适配层
        |
        v
CustomerServiceController
        |
        v
CustomerServiceAgent
        |
        +--> Intent Planner 意图识别
        +--> Memory 用户与会话记忆
        +--> RAG 客服知识库检索
        +--> MCP Tools 外部系统调用
        +--> Skills 客服技能加载
        +--> Workflow 固定客服流程
        +--> Multi-Agent 专家协作
        +--> Human-in-the-loop 人工接管
        |
        v
客服回复 / 工单记录 / 人工确认任务
```

## 6. 建议包结构

后续可以逐步从 `Learning*` 迁移到 `CustomerService*`：

```text
com.alibaba.cloud.ai.customer
  ├── channel
  │   ├── ChannelType
  │   ├── ChannelMessage
  │   ├── WebChannelAdapter
  │   ├── XianyuChannelAdapter
  │   └── WechatChannelAdapter
  ├── conversation
  │   ├── CustomerConversation
  │   └── ConversationService
  ├── intent
  │   ├── CustomerServiceIntent
  │   └── CustomerServiceIntentPlanner
  ├── memory
  │   ├── CustomerMemory
  │   └── CustomerMemoryService
  ├── rag
  │   ├── CustomerKnowledgeDocument
  │   ├── CustomerKnowledgeIngestionService
  │   └── CustomerPolicyRagService
  ├── mcp
  │   ├── CustomerMcpService
  │   └── CustomerMcpDebugInfo
  ├── skill
  │   ├── CustomerSkillRegistry
  │   ├── CustomerSkillService
  │   └── CustomerSkillDescriptor
  ├── tool
  │   ├── CustomerServiceTools
  │   ├── OrderTools
  │   ├── ProductTools
  │   └── TicketTools
  ├── agent
  │   ├── CustomerCoordinatorAgent
  │   ├── ProductAgent
  │   ├── OrderAgent
  │   ├── PolicyAgent
  │   ├── ReplyWriterAgent
  │   └── RiskReviewerAgent
  ├── workflow
  │   ├── CustomerServiceWorkflow
  │   └── CustomerServiceWorkflowStep
  ├── graph
  │   └── CustomerServiceGraph
  ├── handoff
  │   ├── HumanHandoffService
  │   └── PendingApproval
  ├── ticket
  │   ├── CustomerTicket
  │   └── TicketService
  └── controller
      └── CustomerServiceController
```

## 7. Channel Adapter 渠道适配层

渠道适配层负责把不同平台消息统一成内部消息模型。

统一输入模型：

```java
public record ChannelMessage(
        String channel,
        String userId,
        String conversationId,
        String messageId,
        String text,
        Map<String, Object> metadata) {
}
```

推荐渠道枚举：

```java
public enum ChannelType {
    WEB,
    XIANYU,
    WECHAT_OFFICIAL_ACCOUNT,
    WECHAT_WORK,
    WECHAT_MINI_PROGRAM
}
```

第一阶段先做 Mock：

- `WebChannelAdapter`：当前页面直接发送消息。
- `XianyuChannelAdapter`：模拟闲鱼买家咨询。
- `WechatChannelAdapter`：模拟微信客服消息。

真实接入阶段再替换具体 Adapter，不影响 Agent、RAG、MCP 和 Workflow。

## 8. Intent Planner 客服意图

客服意图应该从学习意图改为业务意图：

```java
public enum CustomerServiceIntent {
    PRODUCT_INQUIRY,
    PRICE_NEGOTIATION,
    ORDER_STATUS,
    LOGISTICS_QUERY,
    REFUND_REQUEST,
    RETURN_POLICY,
    COMPLAINT,
    HUMAN_HANDOFF,
    GENERAL_CHAT
}
```

示例：

```text
“这个还在吗？” -> PRODUCT_INQUIRY
“能便宜点吗？” -> PRICE_NEGOTIATION
“什么时候发货？” -> LOGISTICS_QUERY
“我要退款” -> REFUND_REQUEST
“找人工” -> HUMAN_HANDOFF
```

## 9. Tool 与 MCP 的职责

Tool 负责让模型调用具体能力。MCP 负责把外部系统能力标准化暴露给模型或 Agent。

在智能客服里，Tool/MCP 应该对应真实系统：

```text
getProductInfo(productId)
getOrderInfo(orderId)
getLogisticsInfo(orderId)
getRefundStatus(orderId)
createTicket(conversationId, summary)
requestHumanHandoff(conversationId, reason)
```

推荐第一阶段：

```text
Java 本地 Tool + Mock 数据
```

第二阶段：

```text
MCP Client -> Customer MCP Server -> Mock 订单/商品/工单服务
```

第三阶段：

```text
MCP Client -> Customer MCP Server -> 真实订单系统/CRM/工单系统/平台授权 API
```

注意：

- 订单、物流、库存、退款状态是结构化实时数据，优先走 Tool/MCP。
- 售后政策、客服话术、商品说明、平台规则是文档知识，优先走 RAG。
- 退款、赔偿、取消订单等高风险动作不能由模型直接执行，应进入人工确认。

## 10. RAG 知识库设计

智能客服 RAG 不应该检索项目 README 和源码，而应该检索客服知识。

推荐知识来源：

```text
售前 FAQ
商品说明
平台规则
退换货政策
售后处理手册
客服标准话术
价格协商策略
投诉处理规范
闲鱼沟通规范
微信客服回复规范
```

第一阶段可以使用本地 Markdown 文档：

```text
src/main/resources/customer-knowledge/
  ├── product-faq.md
  ├── refund-policy.md
  ├── shipping-policy.md
  ├── xianyu-reply-guide.md
  └── wechat-service-guide.md
```

第二阶段接入向量库：

- PGVector：本地开发和教学最推荐。
- Milvus：数据量更大时使用。
- Elasticsearch：已有搜索体系时使用。

典型链路：

```text
用户问售后政策
 -> RAG 检索 refund-policy.md
 -> 模型基于检索片段生成回复
```

复杂链路：

```text
用户问“我的订单超过 7 天还能退吗？”
 -> Tool/MCP 查询订单签收时间
 -> RAG 检索退货政策
 -> Agent 综合订单事实和政策
 -> 如果涉及真实退款，进入人工确认
```

## 11. Skills 技能层设计

Skills 是可复用的指令与上下文包。它和 Tool、RAG、MCP 不一样：

- Tool：执行一个具体动作，例如查订单、查物流、创建工单。
- MCP：把外部工具和资源用统一协议暴露出来。
- RAG：从知识库检索事实或文档片段。
- Skill：告诉 Agent 面对某类任务时应该如何思考、按什么步骤处理、使用哪些工具和资料。

Skills 适合沉淀客服“操作手册”：

```text
price-negotiation-skill
refund-handling-skill
complaint-handling-skill
xianyu-reply-skill
wechat-service-skill
human-handoff-skill
```

### 11.1 Skills 渐进式披露

系统提示中不应直接塞入所有技能全文，而是只注入技能列表：

```text
name
description
skillPath
```

模型判断需要某个技能时，调用：

```text
read_skill(skill_name)
```

再加载完整 `SKILL.md`。这样可以减少上下文浪费，也能让技能按需加载。

### 11.2 SkillRegistry

`SkillRegistry` 负责扫描和管理技能：

```text
CustomerSkillRegistry
 -> 扫描 skills/customer-service/*
 -> 读取每个 SKILL.md 的 name 和 description
 -> 暴露技能列表给 Agent
```

### 11.3 SkillsAgentHook / SkillsInterceptor

可以参考当前仓库里的 `skills-agent-example`：

```text
spring-ai-alibaba-agent-example/skills-agent-example
```

它通过 `SkillsInterceptor` 扫描 `skills` 目录，并注入到 `ReactAgent`。

智能客服中可以进一步抽象为：

```text
SkillsAgentHook
 -> 注册 read_skill 工具
 -> 将技能列表注入系统提示
 -> 当模型调用 read_skill(skill_name) 时加载完整 SKILL.md
```

如果当前版本直接使用 `SkillsInterceptor`，则可以先按官方示例实现；后续再封装成更贴近业务的 `CustomerSkillsAgentHook`。

### 11.4 Skill 目录结构

推荐新增：

```text
src/main/resources/skills/customer-service/
  ├── xianyu-reply/
  │   └── SKILL.md
  ├── wechat-service/
  │   └── SKILL.md
  ├── refund-handling/
  │   └── SKILL.md
  ├── price-negotiation/
  │   └── SKILL.md
  └── complaint-handling/
      └── SKILL.md
```

### 11.5 SKILL.md 示例

```markdown
---
name: xianyu-reply
description: 闲鱼客服回复技能。用于处理闲鱼买家询价、议价、商品状态、发货时间、售后沟通等场景。
---

# 闲鱼客服回复技能

## 使用场景

当用户来自闲鱼渠道，并且问题涉及商品咨询、议价、发货、售后或沟通话术时使用。

## 回复原则

1. 回复要简短自然，不要像机器人。
2. 不承诺无法确认的信息。
3. 涉及价格让步时，先查看商品底价策略。
4. 涉及退款、赔偿、取消订单时，必须进入人工确认。

## 推荐流程

1. 判断买家意图。
2. 必要时调用商品或订单工具。
3. 必要时检索售后政策。
4. 生成符合闲鱼语境的回复。
5. 高风险操作生成 pending approval，不直接执行。
```

## 12. Memory 设计

学习记忆需要改为客服记忆：

```json
{
  "user-a": {
    "channel": "XIANYU",
    "recentProductIds": ["p-1001"],
    "recentOrderIds": ["o-202605150001"],
    "preferredTone": "简洁友好",
    "lastIntent": "PRICE_NEGOTIATION",
    "riskFlags": ["frequent_refund"],
    "conversationCount": 8
  }
}
```

Memory 的作用：

- 记住用户最近咨询的商品或订单。
- 记住用户偏好的回复风格。
- 记住历史风险标记。
- 帮助多轮对话省略重复信息。

Memory 不能替代真实订单数据。订单状态必须实时查 Tool/MCP。

## 13. Workflow 客服流程

客服 Workflow 应该是业务流程，不是技术调用链。

推荐流程：

```text
接收渠道消息
 -> 识别客服意图
 -> 读取用户记忆
 -> 判断是否需要技能
 -> 判断是否需要工具
 -> 判断是否需要 RAG
 -> 生成处理方案
 -> 风险检查
 -> 是否需要人工确认
 -> 生成客服回复
 -> 写入记忆和工单
```

示例：退款咨询

```text
REFUND_REQUEST
 -> 查询订单
 -> 检索退款政策
 -> 判断是否满足条件
 -> 风险审核
 -> 需要退款动作时进入人工确认
 -> 生成回复
```

## 14. Multi-Agent 设计

客服场景适合多个专业 Agent 协作。

推荐角色：

```text
ReceptionAgent        接待与意图识别
ProductAgent          商品咨询专家
OrderAgent            订单与物流专家
PolicyAgent           售后政策专家
ReplyWriterAgent      客服话术生成专家
RiskReviewerAgent     风险审核专家
HumanHandoffAgent     人工接管判断专家
```

可以使用官方 `ReactAgent` 和 `SequentialAgent` 表达顺序协作：

```java
ReactAgent orderAgent = ReactAgent.builder()
        .name("order_agent")
        .model(chatModel)
        .description("订单与物流查询 Agent")
        .instruction("你负责根据用户输入提取订单号，并调用订单或物流工具查询事实。用户问题：{input}")
        .outputKey("order_info")
        .build();

ReactAgent policyAgent = ReactAgent.builder()
        .name("policy_agent")
        .model(chatModel)
        .description("售后政策 Agent")
        .instruction("你负责根据订单事实和用户问题检索售后政策，并输出可执行判断。订单信息：{order_info}，用户问题：{input}")
        .outputKey("policy_result")
        .build();

ReactAgent replyAgent = ReactAgent.builder()
        .name("reply_agent")
        .model(chatModel)
        .description("客服回复 Agent")
        .instruction("你负责把订单事实、政策判断和渠道语境整理成客服回复。订单信息：{order_info}，政策判断：{policy_result}")
        .outputKey("final_reply")
        .build();

SequentialAgent customerServiceAgent = SequentialAgent.builder()
        .name("customer_service_agent")
        .description("智能客服处理 Agent，先查事实，再查政策，最后生成回复")
        .subAgents(List.of(orderAgent, policyAgent, replyAgent))
        .build();
```

如果后续需要分支和人工确认，则升级到 AgentGraph。

## 15. AgentGraph 设计

AgentGraph 适合表达带分支、状态和人工确认的客服流程：

```text
START
 -> classify_intent
 -> load_memory
 -> load_skill
 -> collect_facts
 -> retrieve_policy
 -> risk_review
 -> [LOW_RISK] generate_reply
 -> [HIGH_RISK] human_approval
 -> write_ticket
 -> END
```

典型分支：

- 普通商品咨询：直接生成回复。
- 订单状态咨询：查询订单后生成回复。
- 退款请求：查询订单 + 检索政策 + 风险检查。
- 高风险退款：人工确认后执行。

## 16. Human-in-the-loop

以下动作必须进入人工确认：

- 执行退款
- 发放补偿
- 取消订单
- 修改地址
- 承诺价格优惠
- 升级投诉
- 拉黑用户或标记风险

模型只能生成：

```text
pending_action
```

前端展示：

```text
待确认动作：为订单 o-202605150001 发起退款
原因：用户明确申请退款，订单符合 7 天无理由条件
风险：中
[确认执行] [拒绝] [转人工]
```

人工确认后再调用 MCP Tool 执行动作。

## 17. 前端改造方向

当前聊天页面可以继续复用，但需要从学习调试台改为客服工作台。

推荐区域：

```text
左侧：渠道与会话列表
中间：客服聊天窗口
右侧：调试与业务上下文
底部：输入框 + 快捷操作
```

右侧调试区展示：

- 当前渠道：WEB / XIANYU / WECHAT
- 用户记忆
- 识别意图
- 命中的 Skill
- RAG 检索片段
- MCP 工具调用
- Multi-Agent 执行步骤
- Workflow / Graph 节点
- 是否需要人工确认

## 18. 分阶段整改计划

### 第 1 阶段：文档和业务边界

目标：

- 明确智能客服助手方向。
- 明确闲鱼、微信先 Mock 后真实接入。
- 明确 Skills、MCP、RAG、Tool、Workflow、AgentGraph 的职责。

产出：

- `CUSTOMER-SERVICE-AGENT-REFACTOR-PLAN.md`

### 第 2 阶段：客服领域模型

新增：

```text
CustomerServiceIntent
ChannelMessage
CustomerMemory
CustomerServiceRequest
CustomerServiceResponse
```

测试：

```text
输入“这个商品还在吗？”
 -> 识别 PRODUCT_INQUIRY

输入“我要退款”
 -> 识别 REFUND_REQUEST
```

### 第 3 阶段：客服 Tool

新增：

```text
ProductTools
OrderTools
TicketTools
```

先使用 Mock 数据。

测试：

```text
请查询商品 p-1001 是否还在
请查询订单 o-202605150001 的物流
```

### 第 4 阶段：客服 Skills

新增：

```text
skills/customer-service/xianyu-reply/SKILL.md
skills/customer-service/wechat-service/SKILL.md
skills/customer-service/refund-handling/SKILL.md
```

接入方式：

- 先参考 `skills-agent-example` 使用 `SkillsInterceptor`。
- 后续封装 `CustomerSkillRegistry` 和 `read_skill`。

测试：

```text
闲鱼买家问：这个还能便宜吗？
 -> 命中 xianyu-reply 或 price-negotiation skill
```

### 第 5 阶段：客服 RAG

新增客服知识库：

```text
customer-knowledge/refund-policy.md
customer-knowledge/shipping-policy.md
customer-knowledge/xianyu-reply-guide.md
```

先本地检索，再升级 PGVector。

测试：

```text
超过 7 天还能退吗？
 -> 检索 refund-policy.md
```

### 第 6 阶段：真实 MCP Server

新增或复用 MCP Server：

```text
customer-mcp-server
 -> get_product
 -> get_order
 -> get_logistics
 -> create_ticket
 -> request_human_handoff
```

测试：

```text
模型通过 MCP 查询订单，而不是直接调用本地方法。
```

### 第 7 阶段：Workflow

把客服流程固定下来：

```text
接收消息
 -> 识别意图
 -> 读取 Memory
 -> 选择 Skill
 -> 调用 Tool/MCP
 -> 检索 RAG
 -> 风险判断
 -> 生成回复
 -> 写入工单
```

测试：

```text
退款问题应经过订单查询、政策检索、风险判断。
```

### 第 8 阶段：官方 Multi-Agent

接入：

```text
ReactAgent
SequentialAgent
```

测试：

```text
订单问题先由 OrderAgent 查事实，再由 PolicyAgent 查规则，最后由 ReplyWriterAgent 生成回复。
```

### 第 9 阶段：AgentGraph + Human-in-the-loop

接入：

```text
StateGraph / AgentGraph
PendingApproval
```

测试：

```text
用户要求退款
 -> Graph 进入 human_approval 节点
 -> 前端出现确认按钮
 -> 确认后才调用退款工具
```

### 第 10 阶段：真实渠道接入

优先级建议：

1. 网页客服真实可用。
2. 企业微信或微信公众号客服。
3. 闲鱼官方授权能力。
4. 其他电商渠道。

真实接入必须满足：

- 有官方授权。
- 有回调验签。
- 有消息去重。
- 有敏感动作人工确认。
- 有日志和审计。

## 19. 第一批测试用例

### 商品咨询

```text
渠道：XIANYU
用户：这个商品还在吗？
预期：
- 意图 PRODUCT_INQUIRY
- 命中 xianyu-reply skill
- 调用 getProductInfo
- 生成简短自然回复
```

### 议价

```text
渠道：XIANYU
用户：能便宜 50 吗？
预期：
- 意图 PRICE_NEGOTIATION
- 命中 price-negotiation skill
- 查询商品底价策略
- 不直接承诺超出策略的优惠
```

### 物流查询

```text
渠道：WECHAT
用户：我的订单怎么还没到？
预期：
- 意图 LOGISTICS_QUERY
- 调用 getOrderInfo
- 调用 getLogisticsInfo
- 生成客服解释
```

### 退款请求

```text
渠道：WEB
用户：我要退款
预期：
- 意图 REFUND_REQUEST
- 查询订单
- 检索退款政策
- 风险判断
- 生成 pending approval，不直接退款
```

### 投诉

```text
渠道：WECHAT
用户：你们服务太差了，我要投诉
预期：
- 意图 COMPLAINT
- 命中 complaint-handling skill
- 降低情绪冲突
- 必要时创建工单并转人工
```

## 20. 与当前 minimax-chat 的迁移关系

建议保留：

- MiniMax 模型配置
- 前端聊天窗口
- Markdown 渲染
- Tool 调试区
- MCP 调试区
- Memory 持久化思路
- AgentRunReport
- Evaluation / Judge
- 官方 Agent / Graph 接入经验

建议逐步替换：

```text
LearningIntent             -> CustomerServiceIntent
LearningMemory             -> CustomerMemory
LearningRagService         -> CustomerPolicyRagService
LearningSkillService       -> CustomerSkillService / CustomerSkillRegistry
MiniMaxLearningTools       -> CustomerServiceTools
LearningWorkflowService    -> CustomerServiceWorkflow
LearningCoordinatorAgent   -> CustomerCoordinatorAgent
```

## 21. 最重要的设计原则

1. 先 Mock 渠道，再接真实渠道。
2. 订单、物流、库存走 Tool/MCP，不走 RAG。
3. 政策、话术、规则走 RAG。
4. Skills 存放任务处理流程和业务操作手册。
5. 高风险动作必须人工确认。
6. Multi-Agent 必须按业务职责拆分，而不是为了演示而拆分。
7. Workflow 负责稳定流程，Agent 负责动态判断，Graph 负责复杂分支。
8. 前端必须展示调试链路，让每次回复可以解释、可以复盘。

