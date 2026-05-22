/*
 * Copyright 2026-2027 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.parallel.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.sequential.SequentialAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 智能客服高级 Agent 编排服务，将多个 ReactAgent.asNode() 接入 StateGraph，形成 Agent-as-Node Workflow。
 * <p>
 * 工作流节点编排：
 * <ol>
 *     <li>fact_collector：并行收集商品、订单、物流、RAG 事实</li>
 *     <li>routing：LLM 路由判断是否需要专家介入</li>
 *     <li>product_expert：商品专家处理商品咨询和议价</li>
 *     <li>order_expert：订单专家处理订单状态和退款</li>
 *     <li>complaint_expert：投诉专家处理投诉升级</li>
 *     <li>supervisor：主管 Agent 动态分配子 Agent</li>
 *     <li>response_aggregator：回复聚合汇总</li>
 * </ol>
 *
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Service
public class CustomerAgentWorkflowGraphService {

private static final int MAX_HISTORY_MESSAGES = 20;

private final ReactAgent factCollectorAgent;

private final ReactAgent productExpertAgent;

private final ReactAgent orderExpertAgent;

private final ReactAgent complaintExpertAgent;

private final ReactAgent supervisorAgent;

private final ReactAgent responseAggregatorAgent;

private final CustomerServiceIntentPlanner intentPlanner;

private final CustomerMemoryService memoryService;

private final ToolCallDebugRecorder debugRecorder;

private final CustomerServiceTraceLogger traceLogger;

private final CompiledGraph compiledGraph;

private final String graphDefinition;

/**
 * 创建智能客服高级 Agent 编排服务。
 * @param factCollectorAgent 事实收集 Agent
 * @param productExpertAgent 商品专家 Agent
 * @param orderExpertAgent 订单专家 Agent
 * @param complaintExpertAgent 投诉专家 Agent
 * @param supervisorAgent 主管 Agent
 * @param responseAggregatorAgent 回复聚合 Agent
 * @param intentPlanner 意图规划器
 * @param memoryService 记忆服务
 * @param debugRecorder 调试记录器
 * @param traceLogger 链路日志
 * @throws GraphStateException 图构建失败
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public CustomerAgentWorkflowGraphService(@Qualifier("factCollectorAgent") ReactAgent factCollectorAgent,
@Qualifier("productExpertAgent") ReactAgent productExpertAgent,
@Qualifier("orderExpertAgent") ReactAgent orderExpertAgent,
@Qualifier("complaintExpertAgent") ReactAgent complaintExpertAgent,
@Qualifier("supervisorAgent") ReactAgent supervisorAgent,
@Qualifier("responseAggregatorAgent") ReactAgent responseAggregatorAgent,
CustomerServiceIntentPlanner intentPlanner, CustomerMemoryService memoryService,
ToolCallDebugRecorder debugRecorder, CustomerServiceTraceLogger traceLogger) throws GraphStateException {
this.factCollectorAgent = factCollectorAgent;
this.productExpertAgent = productExpertAgent;
this.orderExpertAgent = orderExpertAgent;
this.complaintExpertAgent = complaintExpertAgent;
this.supervisorAgent = supervisorAgent;
this.responseAggregatorAgent = responseAggregatorAgent;
this.intentPlanner = intentPlanner;
this.memoryService = memoryService;
this.debugRecorder = debugRecorder;
this.traceLogger = traceLogger;
StateGraph graph = buildGraph();
this.graphDefinition = graph.getGraph(GraphRepresentation.Type.MERMAID, "customer service workflow graph").content();
this.compiledGraph = graph.compile();
}

/**
 * 执行高级 Agent 编排工作流。
 * @param userId 用户 ID
 * @param channel 渠道
 * @param message 用户消息
 * @param history 历史消息
 * @return 工作流执行结果
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public WorkflowGraphResult chat(String userId, ChannelType channel, String message,
List<CustomerConversationMessage> history) {
this.debugRecorder.clear();
String traceId = this.traceLogger.start("CUSTOMER_SERVICE_WORKFLOW", userId, channel, message);
CustomerMemory memoryBefore = this.memoryService.read(userId);
CustomerServiceIntent intent = this.intentPlanner.plan(message);
this.traceLogger.step("CUSTOMER_SERVICE_WORKFLOW", traceId, "INTENT_PLAN", String.valueOf(intent));
try {
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-workflow-" + traceId)
.build();
Optional<OverAllState> result = this.compiledGraph.invoke(Map.of("userId", userId, "channel",
channel == null ? ChannelType.WEB : channel, "message", message, "history",
history == null ? List.of() : history, "intent", intent, "memoryBefore", memoryBefore), config);
OverAllState state = result.orElseThrow();
String content = extractContent(state);
CustomerMemory memoryAfter = this.memoryService.update(userId, channel, message, intent);
this.traceLogger.finish("CUSTOMER_SERVICE_WORKFLOW", traceId, intent, content);
return new WorkflowGraphResult(content, intent, memoryBefore, memoryAfter, extractSteps(state), intent.name());
}
catch (Exception ex) {
this.traceLogger.error("CUSTOMER_SERVICE_WORKFLOW", traceId, ex);
return new WorkflowGraphResult("高级 Agent 编排工作流执行失败：" + ex.getMessage(), intent, memoryBefore,
memoryBefore, List.of(), "WORKFLOW_ERROR");
}
}

/**
 * 获取工作流图定义（Mermaid 格式）。
 * @return 工作流图定义
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public String getGraphDefinition() {
return this.graphDefinition;
}

/**
 * 构建工作流图。
 * @return StateGraph
 * @throws GraphStateException 图构建失败
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
private StateGraph buildGraph() throws GraphStateException {
KeyStrategyFactory keyFactory = new KeyStrategyFactoryBuilder()
.addPatternStrategy("userId", new ReplaceStrategy())
.addPatternStrategy("channel", new ReplaceStrategy())
.addPatternStrategy("message", new ReplaceStrategy())
.addPatternStrategy("history", new ReplaceStrategy())
.addPatternStrategy("intent", new ReplaceStrategy())
.addPatternStrategy("memoryBefore", new ReplaceStrategy())
.addPatternStrategy("memoryAfter", new ReplaceStrategy())
.addPatternStrategy("factBundle", new ReplaceStrategy())
.addPatternStrategy("routingResult", new ReplaceStrategy())
.addPatternStrategy("expertResponses", new ReplaceStrategy())
.addPatternStrategy("finalContent", new ReplaceStrategy())
.build();

return new StateGraph(keyFactory)
.addNode("memory_read", node_async(memoryReadNode()))
.addNode("intent_plan", node_async(intentPlanNode()))
.addNode("fact_collector", node_async(factCollectorNode()))
.addNode("routing", node_async(routingNode()))
.addNode("product_expert", node_async(productExpertNode()))
.addNode("order_expert", node_async(orderExpertNode()))
.addNode("complaint_expert", node_async(complaintExpertNode()))
.addNode("supervisor", node_async(supervisorNode()))
.addNode("response_aggregator", node_async(responseAggregatorNode()))
.addNode("memory_write", node_async(memoryWriteNode()))
.addEdge(START, "memory_read")
.addEdge("memory_read", "intent_plan")
.addEdge("intent_plan", "fact_collector")
.addEdge("fact_collector", "routing")
.addConditionalEdge("routing", this::routeToExpert, Map.of("PRODUCT", "product_expert",
"ORDER", "order_expert", "COMPLAINT", "complaint_expert", "GENERAL", "response_aggregator"))
.addEdge("product_expert", "response_aggregator")
.addEdge("order_expert", "response_aggregator")
.addEdge("complaint_expert", "supervisor")
.addEdge("supervisor", "response_aggregator")
.addEdge("response_aggregator", "memory_write")
.addEdge("memory_write", END);
}

/**
 * 路由判断。
 * @param state 状态
 * @return 路由目标
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
private String routeToExpert(OverAllState state) {
String routing = state.get("routingResult", String.class);
if (routing == null) {
return "GENERAL";
}
return routing.toUpperCase();
}

private NodeAction memoryReadNode() {
return ctx -> {
CustomerMemory memory = ctx.get("memoryBefore", CustomerMemory.class);
if (memory == null) {
String userId = ctx.get("userId", String.class);
memory = this.memoryService.read(userId);
}
return NodeOutput.of(ctx.update("memoryBefore", memory));
};
}

private NodeAction intentPlanNode() {
return ctx -> {
String message = ctx.get("message", String.class);
CustomerServiceIntent intent = this.intentPlanner.plan(message);
return NodeOutput.of(ctx.update("intent", intent));
};
}

private NodeAction factCollectorNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
CustomerServiceIntent intent = ctx.get("intent", CustomerServiceIntent.class);
CustomerMemory memory = ctx.get("memoryBefore", CustomerMemory.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-fact-collector")
.build();
String prompt = buildFactCollectorPrompt(intent, message, memory);
Optional<NodeOutput> output = this.factCollectorAgent.invokeAndGetOutput(prompt, config);
String factBundle = output.map(o -> o.state().get("content", String.class)).orElse("");
return NodeOutput.of(ctx.update("factBundle", factBundle));
};
}

private NodeAction routingNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
CustomerServiceIntent intent = ctx.get("intent", CustomerServiceIntent.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-routing")
.build();
String prompt = buildRoutingPrompt(intent, message);
Optional<NodeOutput> output = this.supervisorAgent.invokeAndGetOutput(prompt, config);
String routing = output.map(o -> o.state().get("content", String.class)).orElse("GENERAL");
if (routing.contains("PRODUCT")) {
routing = "PRODUCT";
}
else if (routing.contains("ORDER") || routing.contains("REFUND")) {
routing = "ORDER";
}
else if (routing.contains("COMPLAINT")) {
routing = "COMPLAINT";
}
else {
routing = "GENERAL";
}
return NodeOutput.of(ctx.update("routingResult", routing));
};
}

private NodeAction productExpertNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
String factBundle = ctx.get("factBundle", String.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-product-expert")
.build();
String prompt = buildExpertPrompt("商品专家", factBundle, message);
Optional<NodeOutput> output = this.productExpertAgent.invokeAndGetOutput(prompt, config);
String response = output.map(o -> o.state().get("content", String.class)).orElse("商品专家暂时无法处理。");
return NodeOutput.of(ctx.update(Map.of("expertResponses", Map.of("product", response))));
};
}

private NodeAction orderExpertNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
String factBundle = ctx.get("factBundle", String.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-order-expert")
.build();
String prompt = buildExpertPrompt("订单专家", factBundle, message);
Optional<NodeOutput> output = this.orderExpertAgent.invokeAndGetOutput(prompt, config);
String response = output.map(o -> o.state().get("content", String.class)).orElse("订单专家暂时无法处理。");
Map<String, String> responses = new java.util.HashMap<>(ctx.get("expertResponses", Map.class));
responses.put("order", response);
return NodeOutput.of(ctx.update("expertResponses", responses));
};
}

private NodeAction complaintExpertNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
String factBundle = ctx.get("factBundle", String.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-complaint-expert")
.build();
String prompt = buildExpertPrompt("投诉专家", factBundle, message);
Optional<NodeOutput> output = this.complaintExpertAgent.invokeAndGetOutput(prompt, config);
String response = output.map(o -> o.state().get("content", String.class)).orElse("投诉专家暂时无法处理。");
Map<String, String> responses = new java.util.HashMap<>(ctx.get("expertResponses", Map.class));
responses.put("complaint", response);
return NodeOutput.of(ctx.update("expertResponses", responses));
};
}

private NodeAction supervisorNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
Map<String, String> expertResponses = ctx.get("expertResponses", Map.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-supervisor")
.build();
String prompt = buildSupervisorPrompt(message, expertResponses);
Optional<NodeOutput> output = this.supervisorAgent.invokeAndGetOutput(prompt, config);
String decision = output.map(o -> o.state().get("content", String.class)).orElse("");
return NodeOutput.of(ctx.update(Map.of("supervisorDecision", decision)));
};
}

private NodeAction responseAggregatorNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
String message = ctx.get("message", String.class);
String factBundle = ctx.get("factBundle", String.class);
Map<String, String> expertResponses = ctx.get("expertResponses", Map.class);
String routing = ctx.get("routingResult", String.class);
RunnableConfig config = RunnableConfig.builder()
.threadId(userId + "-aggregator")
.build();
String prompt = buildAggregatorPrompt(message, factBundle, expertResponses, routing);
Optional<NodeOutput> output = this.responseAggregatorAgent.invokeAndGetOutput(prompt, config);
String content = output.map(o -> o.state().get("content", String.class))
.orElse("抱歉，暂时无法处理您的问题。");
return NodeOutput.of(ctx.update("finalContent", content));
};
}

private NodeAction memoryWriteNode() {
return ctx -> {
String userId = ctx.get("userId", String.class);
ChannelType channel = ctx.get("channel", ChannelType.class);
String message = ctx.get("message", String.class);
CustomerServiceIntent intent = ctx.get("intent", CustomerServiceIntent.class);
CustomerMemory memoryAfter = this.memoryService.update(userId, channel, message, intent);
return NodeOutput.of(ctx.update("memoryAfter", memoryAfter));
};
}

private String buildFactCollectorPrompt(CustomerServiceIntent intent, String message, CustomerMemory memory) {
return String.format("""
你是智能客服事实收集专家。请根据用户问题和历史记忆，确定需要收集哪些事实。

用户问题：%s
识别意图：%s
用户记忆：%s

请判断需要收集的事实类型：商品信息、订单信息、物流信息、RAG知识等。
输出格式：列出需要收集的事实项，每项一行。
""", message, intent, memory != null ? memory.summary() : "无");
}

private String buildRoutingPrompt(CustomerServiceIntent intent, String message) {
return String.format("""
你是智能客服路由专家。请根据用户意图判断应该路由到哪个专家处理。

用户问题：%s
识别意图：%s

可选路由目标：
- PRODUCT：商品咨询、议价相关问题
- ORDER：订单状态、退款、退货相关问题
- COMPLAINT：投诉、升级相关问题
- GENERAL：一般问题，主客服直接处理

请输出路由目标（PRODUCT/ORDER/COMPLAINT/GENERAL）：
""", message, intent);
}

private String buildExpertPrompt(String expertName, String factBundle, String message) {
return String.format("""
你是智能客服%s。请根据收集到的事实和用户问题，给出专业的回复。

已收集事实：
%s

用户问题：%s

请给出专业、礼貌、简洁的回复：
""", expertName, factBundle, message);
}

private String buildSupervisorPrompt(String message, Map<String, String> expertResponses) {
StringBuilder sb = new StringBuilder();
sb.append("你是智能客服主管。请根据用户问题和各专家回复，做出最终决策。\n\n用户问题：")
.append(message)
.append("\n\n专家回复：\n");
expertResponses.forEach((key, value) -> sb.append(key).append("专家：").append(value).append("\n"));
sb.append("\n请做出最终处理决策：");
return sb.toString();
}

private String buildAggregatorPrompt(String message, String factBundle, Map<String, String> expertResponses,
String routing) {
return String.format("""
你是智能客服回复聚合专家。请整合所有信息和专家回复，生成最终回复。

用户问题：%s
已收集事实：%s
路由目标：%s
专家回复：%s

请生成最终回复（简洁、专业、礼貌）：
""", message, factBundle, routing, expertResponses);
}

private String extractContent(OverAllState state) {
return state.get("finalContent", String.class);
}

@SuppressWarnings("unchecked")
private List<CustomerServiceStep> extractSteps(OverAllState state) {
List<CustomerServiceStep> steps = new ArrayList<>();
steps.add(new CustomerServiceStep("Memory读取", state.get("memoryBefore", CustomerMemory.class).summary()));
steps.add(new CustomerServiceStep("意图识别", String.valueOf(state.get("intent", CustomerServiceIntent.class))));
steps.add(new CustomerServiceStep("事实收集", state.get("factBundle", String.class)));
steps.add(new CustomerServiceStep("路由判断", state.get("routingResult", String.class)));
Map<String, String> expertResponses = state.get("expertResponses", Map.class);
if (expertResponses != null) {
expertResponses.forEach((key, value) -> steps.add(new CustomerServiceStep(key + "专家回复", value)));
}
steps.add(new CustomerServiceStep("最终回复", extractContent(state)));
return steps;
}

/**
 * 工作流执行结果。
 *
 * @param content 最终回复内容
 * @param intent 识别的意图
 * @param memoryBefore 执行前记忆
 * @param memoryAfter 执行后记忆
 * @param steps 工作流步骤
 * @param chainMode 链路模式
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public record WorkflowGraphResult(String content, CustomerServiceIntent intent, CustomerMemory memoryBefore,
CustomerMemory memoryAfter, List<CustomerServiceStep> steps, String chainMode) {

}

}
