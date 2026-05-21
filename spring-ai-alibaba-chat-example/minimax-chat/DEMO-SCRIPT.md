# 小雨点智能客服 Agent 系统演示脚本

本文档用于面试、项目汇报或本地验收时快速演示 `minimax-chat` 的核心业务链路。建议按顺序演示，先证明客户侧体验，再证明运营侧可观测和数据闭环。

## 1. 启动准备

确认本机 MySQL 已启动，账号密码与 `application.yml` 保持一致：

```text
host: localhost
port: 3306
database: minimax_customer_service
username: root
password: root
```

如果需要演示 Redis 短期上下文，确认本机 Redis 已启动：

```text
host: localhost
port: 6379
database: 0
```

启动应用：

```powershell
mvn spring-boot:run
```

启用 Redis：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=redis"
```

页面入口：

```text
客户主页面：http://localhost:8080/index.html
运营工作台：http://localhost:8080/dashboard.html
```

## 2. 演示主线

### 场景一：简单对话直连 LLM

客户主页面输入：

```text
你好，你是谁？
```

预期效果：

- 返回简短客服自我介绍。
- 工作台执行报告中 `chainMode` 为 `CUSTOMER_SERVICE_DIRECT_LLM`。
- 不触发 Tool、RAG、MCP 或 Agent。
- MySQL 长期 Memory 不因为简单寒暄增加业务画像。

演示价值：

```text
简单对话不走重 Agent 链路，降低延迟和成本。
```

### 场景二：商品咨询走 ReactAgent

客户主页面输入：

```text
这个 p-1001 商品还在吗？能介绍一下成色吗？
```

预期效果：

- `chainMode` 为 `CUSTOMER_SERVICE_ASSISTANT_AGENT`。
- 识别意图为 `PRODUCT_INQUIRY`。
- 工具调用中出现商品查询相关 Tool。
- MySQL `customer_memory` 记录最近商品 `p-1001`。

演示价值：

```text
业务问题自动进入 Agent 链路，由模型结合工具事实回答。
```

### 场景三：物流查询触发订单和物流工具

客户主页面输入：

```text
我的订单 o-202605150001 怎么还没到？帮我查一下物流。
```

预期效果：

- 识别意图为 `LOGISTICS_QUERY`。
- 工具调用中出现订单查询和物流查询。
- 回答包含订单或物流状态。
- MySQL Memory 记录最近订单号。

演示价值：

```text
Agent 不直接编造物流，而是先查业务事实。
```

### 场景四：退款售后进入高风险审核

客户主页面输入：

```text
订单 o-202605150001 我要退款，你直接帮我退掉。
```

预期效果：

- 识别意图为 `REFUND_REQUEST`。
- 系统不会直接执行真实退款。
- 高风险动作进入 `customer_approval_task` 待审核任务。
- 工作台可以查看审核任务和审计日志。

演示价值：

```text
大模型只提出处理建议，高风险业务动作必须人工审核。
```

### 场景五：RAG 召回客服规则

进入运营工作台，使用 RAG 召回率测试台输入：

```text
订单迟迟不到我想退款并投诉客服
```

期望主题：

```text
refund,shipping,complaint
```

预期效果：

- 显示 RAG 模式。
- 显示命中主题和召回率。
- 命中文档覆盖退款、物流、投诉相关知识。

演示价值：

```text
客服回答不是只靠模型记忆，而是能召回项目内业务规则和话术知识。
```

### 场景六：Redis 短期上下文

启用 Redis 后，连续发送两轮：

```text
第一轮：这个 p-1001 商品还在吗？
第二轮：那它能便宜一点吗？
```

工作台点击：

```text
MySQL 与 Redis 数据面板 -> 查看上下文
```

预期效果：

- 显示 Redis Key：`minimax:customer:conversation:{userId}`。
- 显示最近 `user/assistant` 对话内容。
- 显示 TTL 和消息数。

演示价值：

```text
Redis 保存短期对话上下文，MySQL 保存长期用户画像，两者职责分离。
```

### 场景七：MySQL 数据闭环

进入运营工作台，查看：

```text
MySQL 与 Redis 数据面板
```

预期效果：

- `customer_memory` 有用户长期记忆。
- `customer_approval_task` 有高风险待审核任务。
- `operation_audit_event` 有工作台操作审计。
- 可以点击“清理测试数据”清理本地演示数据。

演示价值：

```text
聊天、审批、审计都真实落到 MySQL，方便排查和上线治理。
```

## 3. 推荐讲解顺序

1. 先讲客户侧：用户只看到普通客服聊天窗口。
2. 再讲后端路由：简单对话走 Direct LLM，业务问题走 Agent。
3. 再讲工具事实：商品、订单、物流、退款、工单通过 Tool/MCP 进入模型上下文。
4. 再讲知识增强：RAG 召回客服政策和话术规范。
5. 再讲记忆：Redis 保存短期上下文，MySQL 保存长期 Memory。
6. 最后讲安全治理：高风险动作待审核，操作审计可追踪。

## 4. 验收标准

- 简单寒暄能返回，且链路为 `CUSTOMER_SERVICE_DIRECT_LLM`。
- 商品咨询能返回，且链路为 Agent。
- 物流问题能触发订单或物流工具。
- 退款高风险问题不会直接执行真实业务动作。
- RAG 测试能展示命中文档和召回率。
- 工作台能展示 MySQL 表状态和 Redis 上下文。
- 执行报告、规则评估、AI Judge 能记录最近执行结果。
