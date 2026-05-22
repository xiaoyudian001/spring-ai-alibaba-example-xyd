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
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 智能客服高级 Agent 编排服务，使用当前项目可用的 StateGraph API 串联事实收集、LLM 路由、专家 Agent、主管复核和回复聚合。
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@Service
public class CustomerAgentWorkflowGraphService {

	private final ReactAgent factCollectorAgent;

	private final ReactAgent productExpertAgent;

	private final ReactAgent orderExpertAgent;

	private final ReactAgent complaintExpertAgent;

	private final ReactAgent supervisorAgent;

	private final ReactAgent responseAggregatorAgent;

	private final CustomerServiceIntentPlanner intentPlanner;

	private final CustomerMemoryService memoryService;

	private final CustomerServiceTraceLogger traceLogger;

	private final CompiledGraph compiledGraph;

	private final String graphDefinition;

	/**
	 * 创建高级 Agent 编排服务。
	 * @param factCollectorAgent 事实收集 Agent
	 * @param productExpertAgent 商品专家 Agent
	 * @param orderExpertAgent 订单专家 Agent
	 * @param complaintExpertAgent 投诉专家 Agent
	 * @param supervisorAgent 主管 Agent
	 * @param responseAggregatorAgent 回复聚合 Agent
	 * @param intentPlanner 意图规划器
	 * @param memoryService 记忆服务
	 * @param traceLogger 链路日志
	 * @throws GraphStateException 图构建失败
	 * @author xyd
	 * @date 2026-05-22 12:30:00
	 */
	public CustomerAgentWorkflowGraphService(@Qualifier("factCollectorAgent") ReactAgent factCollectorAgent,
			@Qualifier("productExpertAgent") ReactAgent productExpertAgent,
			@Qualifier("orderExpertAgent") ReactAgent orderExpertAgent,
			@Qualifier("complaintExpertAgent") ReactAgent complaintExpertAgent,
			@Qualifier("supervisorAgent") ReactAgent supervisorAgent,
			@Qualifier("responseAggregatorAgent") ReactAgent responseAggregatorAgent,
			CustomerServiceIntentPlanner intentPlanner, CustomerMemoryService memoryService,
			CustomerServiceTraceLogger traceLogger) throws GraphStateException {
		this.factCollectorAgent = factCollectorAgent;
		this.productExpertAgent = productExpertAgent;
		this.orderExpertAgent = orderExpertAgent;
		this.complaintExpertAgent = complaintExpertAgent;
		this.supervisorAgent = supervisorAgent;
		this.responseAggregatorAgent = responseAggregatorAgent;
		this.intentPlanner = intentPlanner;
		this.memoryService = memoryService;
		this.traceLogger = traceLogger;
		StateGraph graph = buildGraph();
		this.graphDefinition = graph.getGraph(GraphRepresentation.Type.MERMAID,
				"customer service agent workflow graph").content();
		this.compiledGraph = graph.compile();
	}

	/**
	 * 执行高级 Agent-as-Node 工作流。
	 * @param userId 用户 ID
	 * @param channel 渠道
	 * @param message 用户消息
	 * @param history 历史消息
	 * @return 工作流结果
	 * @author xyd
	 * @date 2026-05-22 12:30:00
	 */
	public WorkflowGraphResult chat(String userId, ChannelType channel, String message,
			List<CustomerConversationMessage> history) {
		String normalizedUserId = normalizeUserId(userId);
		String normalizedMessage = normalizeMessage(message);
		String traceId = this.traceLogger.start("CUSTOMER_SERVICE_WORKFLOW_GRAPH", normalizedUserId, channel,
				normalizedMessage);
		try {
			RunnableConfig config = RunnableConfig.builder()
					.threadId(normalizedUserId + "-workflow-graph-" + traceId)
					.build();
			Optional<OverAllState> result = this.compiledGraph.invoke(Map.of("traceId", traceId, "userId",
					normalizedUserId, "channel", channel == null ? ChannelType.WEB : channel, "message",
					normalizedMessage, "history", history == null ? List.of() : history), config);
			WorkflowGraphResult workflowResult = toResult(result.orElseThrow());
			this.traceLogger.finish("CUSTOMER_SERVICE_WORKFLOW_GRAPH", traceId, workflowResult.intent(),
					workflowResult.content());
			return workflowResult;
		}
		catch (Exception ex) {
			this.traceLogger.error("CUSTOMER_SERVICE_WORKFLOW_GRAPH", traceId, ex);
			CustomerMemory memory = this.memoryService.read(normalizedUserId);
			return new WorkflowGraphResult("高级 Agent 编排工作流执行失败：" + ex.getMessage(),
					CustomerServiceIntent.GENERAL_CHAT, memory, memory,
					List.of(new CustomerServiceStep("error", ex.getMessage())), "CUSTOMER_AGENT_WORKFLOW_GRAPH");
		}
	}

	/**
	 * 返回 Mermaid 图定义。
	 * @return Mermaid 图定义
	 * @author xyd
	 * @date 2026-05-22 12:30:00
	 */
	public String getGraphDefinition() {
		return this.graphDefinition;
	}

	/**
	 * 构建高级客服 Agent 工作流图。
	 * @return StateGraph
	 * @throws GraphStateException 图构建失败
	 * @author xyd
	 * @date 2026-05-22 12:30:00
	 */
	private StateGraph buildGraph() throws GraphStateException {
		KeyStrategyFactory keyFactory = new KeyStrategyFactoryBuilder()
				.addPatternStrategy("traceId", new ReplaceStrategy())
				.addPatternStrategy("userId", new ReplaceStrategy())
				.addPatternStrategy("channel", new ReplaceStrategy())
				.addPatternStrategy("message", new ReplaceStrategy())
				.addPatternStrategy("history", new ReplaceStrategy())
				.addPatternStrategy("memoryBefore", new ReplaceStrategy())
				.addPatternStrategy("memoryAfter", new ReplaceStrategy())
				.addPatternStrategy("intent", new ReplaceStrategy())
				.addPatternStrategy("factSummary", new ReplaceStrategy())
				.addPatternStrategy("routingResult", new ReplaceStrategy())
				.addPatternStrategy("expertResponse", new ReplaceStrategy())
				.addPatternStrategy("supervisorDecision", new ReplaceStrategy())
				.addPatternStrategy("content", new ReplaceStrategy())
				.addPatternStrategy("steps", new ReplaceStrategy())
				.build();

		return new StateGraph(keyFactory)
				.addNode("memory_read", node_async(memoryReadNode()))
				.addNode("intent_plan", node_async(intentPlanNode()))
				.addNode("fact_collect_agent", node_async(factCollectAgentNode()))
				.addNode("llm_routing", node_async(routingNode()))
				.addNode("expert_agent", node_async(expertAgentNode()))
				.addNode("supervisor_agent", node_async(supervisorNode()))
				.addNode("response_aggregator", node_async(responseAggregatorNode()))
				.addNode("memory_write", node_async(memoryWriteNode()))
				.addEdge(START, "memory_read")
				.addEdge("memory_read", "intent_plan")
				.addEdge("intent_plan", "fact_collect_agent")
				.addEdge("fact_collect_agent", "llm_routing")
				.addEdge("llm_routing", "expert_agent")
				.addEdge("expert_agent", "supervisor_agent")
				.addEdge("supervisor_agent", "response_aggregator")
				.addEdge("response_aggregator", "memory_write")
				.addEdge("memory_write", END);
	}

	private NodeAction memoryReadNode() {
		return state -> {
			String userId = stringValue(state, "userId", "default-user");
			CustomerMemory memory = this.memoryService.read(userId);
			return Map.of("memoryBefore", memory, "steps",
					appendStep(state, "memory_read", "读取长期 Memory：" + memory.summary()));
		};
	}

	private NodeAction intentPlanNode() {
		return state -> {
			CustomerServiceIntent intent = this.intentPlanner.plan(stringValue(state, "message", ""));
			return Map.of("intent", intent, "steps", appendStep(state, "intent_plan", "识别意图：" + intent));
		};
	}

	private NodeAction factCollectAgentNode() {
		return state -> {
			String prompt = "请收集本轮客服问题需要的商品、订单、物流、RAG 和用户上下文事实。\n用户问题："
					+ stringValue(state, "message", "");
			String factSummary = invokeAgent(this.factCollectorAgent, prompt, stringValue(state, "userId", "default-user"),
					"fact");
			return Map.of("factSummary", factSummary, "steps",
					appendStep(state, "fact_collect_agent", truncate(factSummary)));
		};
	}

	private NodeAction routingNode() {
		return state -> {
			String routing = expertRoute(intentValue(state, "intent"), stringValue(state, "message", ""));
			return Map.of("routingResult", routing, "steps", appendStep(state, "llm_routing", "专家路由：" + routing));
		};
	}

	private NodeAction expertAgentNode() {
		return state -> {
			String routing = stringValue(state, "routingResult", "GENERAL");
			ReactAgent expert = switch (routing) {
				case "PRODUCT" -> this.productExpertAgent;
				case "ORDER" -> this.orderExpertAgent;
				case "COMPLAINT" -> this.complaintExpertAgent;
				default -> this.responseAggregatorAgent;
			};
			String prompt = """
					用户问题：%s
					意图：%s
					事实收集：%s
					请以 %s 专家视角给出处理建议。
					""".formatted(stringValue(state, "message", ""), intentValue(state, "intent"),
					stringValue(state, "factSummary", ""), routing);
			String response = invokeAgent(expert, prompt, stringValue(state, "userId", "default-user"), routing);
			return Map.of("expertResponse", response, "steps",
					appendStep(state, "expert_agent", routing + " 专家回复：" + truncate(response)));
		};
	}

	private NodeAction supervisorNode() {
		return state -> {
			String prompt = """
					请以客服主管身份复核专家建议，重点检查是否涉及退款、赔付、投诉升级或人工接管。
					用户问题：%s
					专家建议：%s
					""".formatted(stringValue(state, "message", ""), stringValue(state, "expertResponse", ""));
			String decision = invokeAgent(this.supervisorAgent, prompt, stringValue(state, "userId", "default-user"),
					"supervisor");
			return Map.of("supervisorDecision", decision, "steps",
					appendStep(state, "supervisor_agent", truncate(decision)));
		};
	}

	private NodeAction responseAggregatorNode() {
		return state -> {
			String prompt = """
					请整合事实、专家建议和主管复核，生成最终客服回复。不要暴露内部流程。
					用户问题：%s
					事实：%s
					专家建议：%s
					主管复核：%s
					""".formatted(stringValue(state, "message", ""), stringValue(state, "factSummary", ""),
					stringValue(state, "expertResponse", ""), stringValue(state, "supervisorDecision", ""));
			String content = invokeAgent(this.responseAggregatorAgent, prompt, stringValue(state, "userId", "default-user"),
					"aggregator");
			return Map.of("content", content, "steps", appendStep(state, "response_aggregator", truncate(content)));
		};
	}

	private NodeAction memoryWriteNode() {
		return state -> {
			String userId = stringValue(state, "userId", "default-user");
			CustomerMemory memory = this.memoryService.update(userId, channelValue(state, "channel"),
					stringValue(state, "message", ""), intentValue(state, "intent"));
			return Map.of("memoryAfter", memory, "steps",
					appendStep(state, "memory_write", "更新长期 Memory：" + memory.summary()));
		};
	}

	private WorkflowGraphResult toResult(OverAllState state) {
		CustomerMemory before = memoryValue(state, "memoryBefore", new CustomerMemory());
		CustomerMemory after = memoryValue(state, "memoryAfter", before);
		return new WorkflowGraphResult(stringValue(state, "content", "高级 Agent 编排工作流没有返回内容。"),
				intentValue(state, "intent"), before, after, steps(state), "CUSTOMER_AGENT_WORKFLOW_GRAPH");
	}

	private String invokeAgent(ReactAgent agent, String prompt, String userId, String nodeName) {
		try {
			RunnableConfig config = RunnableConfig.builder().threadId(userId + "-" + nodeName).build();
			Optional<NodeOutput> output = agent.invokeAndGetOutput(prompt, config);
			return output.map(NodeOutput::state).map(this::extractContent).orElse("");
		}
		catch (Exception ex) {
			return nodeName + " 执行失败：" + ex.getMessage();
		}
	}

	private String extractContent(OverAllState state) {
		if (state == null) {
			return "";
		}
		Optional<Object> output = state.value("output");
		if (output.isPresent()) {
			return String.valueOf(output.get());
		}
		Optional<List<AbstractMessage>> messages = state.value("messages");
		if (messages.isPresent() && !messages.get().isEmpty()) {
			return messages.get().get(messages.get().size() - 1).getText();
		}
		return state.toString();
	}

	private String expertRoute(CustomerServiceIntent intent, String message) {
		if (intent == CustomerServiceIntent.PRODUCT_INQUIRY || intent == CustomerServiceIntent.PRICE_NEGOTIATION) {
			return "PRODUCT";
		}
		if (intent == CustomerServiceIntent.ORDER_STATUS || intent == CustomerServiceIntent.LOGISTICS_QUERY
				|| intent == CustomerServiceIntent.REFUND_REQUEST || intent == CustomerServiceIntent.RETURN_POLICY) {
			return "ORDER";
		}
		if (intent == CustomerServiceIntent.COMPLAINT || intent == CustomerServiceIntent.HUMAN_HANDOFF) {
			return "COMPLAINT";
		}
		String text = message == null ? "" : message;
		if (text.contains("投诉") || text.contains("差评")) {
			return "COMPLAINT";
		}
		if (text.contains("订单") || text.contains("物流") || text.contains("退款")) {
			return "ORDER";
		}
		return "GENERAL";
	}

	private List<CustomerServiceStep> appendStep(OverAllState state, String name, String detail) {
		List<CustomerServiceStep> result = new ArrayList<>(steps(state));
		result.add(new CustomerServiceStep(name, detail));
		return List.copyOf(result);
	}

	private List<CustomerServiceStep> steps(OverAllState state) {
		Optional<Object> value = state.value("steps");
		if (value.isEmpty() || !(value.get() instanceof List<?> list)) {
			return List.of();
		}
		List<CustomerServiceStep> steps = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof CustomerServiceStep step) {
				steps.add(step);
			}
			else if (item instanceof Map<?, ?> map) {
				steps.add(new CustomerServiceStep(text(map.get("name"), text(map.get("node"), "")),
						text(map.get("detail"), "")));
			}
		}
		return List.copyOf(steps);
	}

	private CustomerServiceIntent intentValue(OverAllState state, String key) {
		Optional<Object> value = state.value(key);
		if (value.isEmpty()) {
			return CustomerServiceIntent.GENERAL_CHAT;
		}
		Object raw = value.get();
		if (raw instanceof CustomerServiceIntent intent) {
			return intent;
		}
		try {
			return CustomerServiceIntent.valueOf(String.valueOf(raw));
		}
		catch (IllegalArgumentException ex) {
			return CustomerServiceIntent.GENERAL_CHAT;
		}
	}

	private ChannelType channelValue(OverAllState state, String key) {
		Optional<Object> value = state.value(key);
		if (value.isEmpty()) {
			return ChannelType.WEB;
		}
		Object raw = value.get();
		if (raw instanceof ChannelType channel) {
			return channel;
		}
		try {
			return ChannelType.valueOf(String.valueOf(raw));
		}
		catch (IllegalArgumentException ex) {
			return ChannelType.WEB;
		}
	}

	private CustomerMemory memoryValue(OverAllState state, String key, CustomerMemory fallback) {
		Optional<Object> value = state.value(key);
		return value.filter(CustomerMemory.class::isInstance).map(CustomerMemory.class::cast).orElse(fallback);
	}

	private String stringValue(OverAllState state, String key, String fallback) {
		return state.value(key).map(String::valueOf).orElse(fallback);
	}

	private String text(Object value, String fallback) {
		String text = value == null ? "" : String.valueOf(value);
		return text.isBlank() ? fallback : text;
	}

	private String truncate(String text) {
		String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		return value.length() <= 180 ? value : value.substring(0, 180) + "...";
	}

	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

	private String normalizeMessage(String message) {
		return message == null || message.isBlank() ? "你好，请问有什么可以帮你？" : message;
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
	 * @date 2026-05-22 12:30:00
	 */
	public record WorkflowGraphResult(String content, CustomerServiceIntent intent, CustomerMemory memoryBefore,
			CustomerMemory memoryAfter, List<CustomerServiceStep> steps, String chainMode) {
	}

}
