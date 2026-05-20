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
import java.util.LinkedHashMap;
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
import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 智能客服官方 StateGraph 服务，用官方 Graph 编排客服记忆、意图、技能、ReactAgent、风控和响应汇总。
 *
 * @author xyd
 * @date 2026-05-19 00:20:26
 */
@Service
public class CustomerServiceGraphService {

	private static final int MAX_HISTORY_MESSAGES = 20;

	private final ReactAgent customerServiceReactAgent;

	private final CustomerServiceIntentPlanner intentPlanner;

	private final CustomerMemoryService memoryService;

	private final CustomerSkillService skillService;

	private final CustomerMcpService customerMcpService;

	private final ToolCallDebugRecorder debugRecorder;

	private final CustomerServiceTraceLogger traceLogger;

	private final CompiledGraph compiledGraph;

	private final String graphDefinition;

	/**
	 * 创建智能客服官方 StateGraph 服务。
	 * @param customerServiceReactAgent 智能客服官方 ReactAgent
	 * @param intentPlanner 客服意图规划器
	 * @param memoryService 客服长期记忆服务
	 * @param skillService 客服 Skills 服务
	 * @param customerMcpService 智能客服 MCP 门面服务
	 * @param debugRecorder 工具调用调试记录器
	 * @param traceLogger 智能客服链路日志埋点
	 * @throws GraphStateException 官方 StateGraph 构建失败时抛出
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public CustomerServiceGraphService(@Qualifier("customerServiceReactAgent") ReactAgent customerServiceReactAgent,
			CustomerServiceIntentPlanner intentPlanner, CustomerMemoryService memoryService,
			CustomerSkillService skillService, CustomerMcpService customerMcpService,
			ToolCallDebugRecorder debugRecorder, CustomerServiceTraceLogger traceLogger) throws GraphStateException {
		this.customerServiceReactAgent = customerServiceReactAgent;
		this.intentPlanner = intentPlanner;
		this.memoryService = memoryService;
		this.skillService = skillService;
		this.customerMcpService = customerMcpService;
		this.debugRecorder = debugRecorder;
		this.traceLogger = traceLogger;
		StateGraph graph = buildGraph();
		this.graphDefinition = graph.getGraph(GraphRepresentation.Type.MERMAID, "customer service graph").content();
		this.compiledGraph = graph.compile();
	}

	/**
	 * 执行一轮智能客服官方 StateGraph 对话。
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @return 智能客服官方 StateGraph 响应结果
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public CustomerServiceGraphResult chat(String userId, ChannelType channel, String message,
			List<CustomerConversationMessage> history) {
		this.debugRecorder.clear();
		this.customerMcpService.clearDebugInfo();
		String normalizedUserId = normalizeUserId(userId);
		String traceId = this.traceLogger.start("CUSTOMER_SERVICE_STATE_GRAPH", normalizedUserId, channel, message);
		try {
			RunnableConfig config = RunnableConfig.builder()
					.threadId(normalizedUserId + "-customer-service-graph")
					.build();
			Optional<OverAllState> result = this.compiledGraph.invoke(Map.of("traceId", traceId, "userId",
					normalizedUserId, "channel", channel == null ? ChannelType.WEB : channel, "message",
					normalizeMessage(message), "history", history == null ? List.of() : history), config);
			CustomerServiceGraphResult graphResult = toResult(result.orElseThrow());
			this.traceLogger.finish("CUSTOMER_SERVICE_STATE_GRAPH", traceId, graphResult.intent(),
					graphResult.content());
			return graphResult;
		}
		catch (Exception ex) {
			this.traceLogger.error("CUSTOMER_SERVICE_STATE_GRAPH", traceId, ex);
			CustomerMemory memory = this.memoryService.read(normalizedUserId);
			return new CustomerServiceGraphResult("智能客服官方 StateGraph 调用失败：" + ex.getMessage(),
					CustomerServiceIntent.GENERAL_CHAT, memory, memory,
					List.of(new CustomerServiceStep("error", ex.getMessage())), List.of(),
					this.debugRecorder.snapshot(), this.customerMcpService.snapshotDebugInfo(), Map.of(),
					this.graphDefinition);
		}
		finally {
			this.debugRecorder.remove();
			this.customerMcpService.clearDebugInfo();
		}
	}

	/**
	 * 构建智能客服官方 StateGraph 节点和边。
	 * @return 智能客服官方 StateGraph
	 * @throws GraphStateException 图状态构建失败时抛出
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private StateGraph buildGraph() throws GraphStateException {
		KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
				.addPatternStrategy("userId", new ReplaceStrategy())
				.addPatternStrategy("traceId", new ReplaceStrategy())
				.addPatternStrategy("channel", new ReplaceStrategy())
				.addPatternStrategy("message", new ReplaceStrategy())
				.addPatternStrategy("history", new ReplaceStrategy())
				.addPatternStrategy("memoryBefore", new ReplaceStrategy())
				.addPatternStrategy("memoryAfter", new ReplaceStrategy())
				.addPatternStrategy("intent", new ReplaceStrategy())
				.addPatternStrategy("selectedSkill", new ReplaceStrategy())
				.addPatternStrategy("content", new ReplaceStrategy())
				.addPatternStrategy("agentSteps", new ReplaceStrategy())
				.addPatternStrategy("toolCalls", new ReplaceStrategy())
				.addPatternStrategy("mcpDebugInfo", new ReplaceStrategy())
				.addPatternStrategy("graphSteps", new ReplaceStrategy())
				.addPatternStrategy("rawAgentState", new ReplaceStrategy())
				.build();

		return new StateGraph(keyStrategyFactory)
				.addNode("memory_read", node_async(memoryReadNode()))
				.addNode("intent_plan", node_async(intentPlanNode()))
				.addNode("skill_select", node_async(skillSelectNode()))
				.addNode("react_agent", node_async(reactAgentNode()))
				.addNode("risk_review", node_async(riskReviewNode()))
				.addNode("memory_write", node_async(memoryWriteNode()))
				.addNode("response", node_async(responseNode()))
				.addEdge(START, "memory_read")
				.addEdge("memory_read", "intent_plan")
				.addEdge("intent_plan", "skill_select")
				.addEdge("skill_select", "react_agent")
				.addEdge("react_agent", "risk_review")
				.addEdge("risk_review", "memory_write")
				.addEdge("memory_write", "response")
				.addEdge("response", END);
	}

	/**
	 * 读取客服长期记忆节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction memoryReadNode() {
		return state -> {
			String userId = stringValue(state, "userId", "default-user");
			String traceId = stringValue(state, "traceId", "-");
			CustomerMemory memory = this.memoryService.read(userId);
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", traceId, "memory_read", memory.summary());
			return Map.of("memoryBefore", memory, "graphSteps",
					appendStep(state, "memory_read", "读取客服长期记忆：" + memory.summary()));
		};
	}

	/**
	 * 识别客服业务意图节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction intentPlanNode() {
		return state -> {
			CustomerServiceIntent intent = this.intentPlanner.plan(stringValue(state, "message", ""));
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"),
					"intent_plan", String.valueOf(intent));
			return Map.of("intent", intent, "graphSteps", appendStep(state, "intent_plan", "识别客服意图为 " + intent + "。"));
		};
	}

	/**
	 * 选择客服 Skill 节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction skillSelectNode() {
		return state -> {
			ChannelType channel = channelValue(state, "channel");
			CustomerServiceIntent intent = intentValue(state, "intent");
			String selectedSkill = this.skillService.selectSkill(channel, intent);
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"),
					"skill_select", selectedSkill);
			return Map.of("selectedSkill", selectedSkill, "graphSteps",
					appendStep(state, "skill_select", "根据渠道 " + channel + " 和意图 " + intent + " 选择 Skill："
							+ selectedSkill + "。"));
		};
	}

	/**
	 * 调用官方客服 ReactAgent 节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction reactAgentNode() {
		return state -> {
			this.debugRecorder.clear();
			String userId = stringValue(state, "userId", "default-user");
			ChannelType channel = channelValue(state, "channel");
			String message = stringValue(state, "message", "");
			CustomerServiceIntent intent = intentValue(state, "intent");
			String selectedSkill = stringValue(state, "selectedSkill", "xianyu-reply");
			CustomerMemory memory = memoryValue(state, "memoryBefore", this.memoryService.read(userId));
			List<CustomerConversationMessage> history = historyValue(state);
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"),
					"react_agent", "调用官方客服 ReactAgent");
			RunnableConfig config = RunnableConfig.builder()
					.threadId(userId + "-customer-service-react-agent")
					.build();
			Optional<NodeOutput> output = this.customerServiceReactAgent.invokeAndGetOutput(
					buildAgentPrompt(channel, message, history, intent, selectedSkill, memory), config);
			OverAllState agentState = output.map(NodeOutput::state).orElse(null);
			String content = extractContent(agentState);
			List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = this.debugRecorder.snapshot();
			this.traceLogger.tools("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"), toolCalls);
			this.debugRecorder.remove();
			return Map.of("content", content, "toolCalls", toolCalls,
					"rawAgentState", agentState == null ? Map.of() : agentState.data(),
					"mcpDebugInfo", this.customerMcpService.snapshotDebugInfo(),
					"agentSteps", agentSteps(intent, selectedSkill), "graphSteps",
					appendStep(state, "react_agent", "调用官方客服 ReactAgent，模型触发工具 " + toolCalls.size() + " 次。"));
		};
	}

	/**
	 * 执行客服风险复核节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction riskReviewNode() {
		return state -> {
			CustomerServiceIntent intent = intentValue(state, "intent");
			String detail = switch (intent) {
				case REFUND_REQUEST, COMPLAINT, HUMAN_HANDOFF ->
					"本轮属于高风险或人工接管场景，应通过工具生成工单或人工接管任务，不能直接执行退款、赔付、取消订单等动作。";
				default -> "本轮未命中高风险动作，保持事实查询和礼貌回复。";
			};
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"),
					"risk_review", detail);
			return Map.of("graphSteps", appendStep(state, "risk_review", detail));
		};
	}

	/**
	 * 更新客服长期记忆节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction memoryWriteNode() {
		return state -> {
			String userId = stringValue(state, "userId", "default-user");
			ChannelType channel = channelValue(state, "channel");
			String message = stringValue(state, "message", "");
			CustomerServiceIntent intent = intentValue(state, "intent");
			CustomerMemory memoryAfter = this.memoryService.update(userId, channel, message, intent);
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"),
					"memory_write", memoryAfter.summary());
			return Map.of("memoryAfter", memoryAfter, "graphSteps",
					appendStep(state, "memory_write", "根据本轮客服消息更新长期记忆：" + memoryAfter.summary()));
		};
	}

	/**
	 * 汇总客服 Graph 响应节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private NodeAction responseNode() {
		return state -> {
			this.traceLogger.step("CUSTOMER_SERVICE_STATE_GRAPH", stringValue(state, "traceId", "-"),
					"response", "汇总客服回复、Graph 节点、Agent 步骤、Tool 调用、MCP 和 Memory 信息。");
			return Map.of("graphSteps",
					appendStep(state, "response", "汇总客服回复、Graph 节点、Agent 步骤、Tool 调用、MCP 和 Memory 信息。"));
		};
	}

	/**
	 * 将官方 Graph 总状态转换为前端响应对象。
	 * @param state 官方 Graph 总状态
	 * @return 智能客服官方 StateGraph 响应结果
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private CustomerServiceGraphResult toResult(OverAllState state) {
		String userId = stringValue(state, "userId", "default-user");
		CustomerMemory memoryBefore = memoryValue(state, "memoryBefore", this.memoryService.read(userId));
		CustomerMemory memoryAfter = memoryValue(state, "memoryAfter", memoryBefore);
		return new CustomerServiceGraphResult(stringValue(state, "content", "智能客服官方 StateGraph 没有返回内容。"),
				intentValue(state, "intent"), memoryBefore, memoryAfter, steps(state, "graphSteps"),
				steps(state, "agentSteps"), toolCalls(state), mcpDebugInfo(state), state.data(), this.graphDefinition);
	}

	/**
	 * 构造传入官方客服 ReactAgent 的提示词。
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @param memory 客服长期记忆
	 * @return ReactAgent 输入提示词
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String buildAgentPrompt(ChannelType channel, String message, List<CustomerConversationMessage> history,
			CustomerServiceIntent intent, String selectedSkill, CustomerMemory memory) {
		return """
				用户问题：
				%s

				当前渠道：
				%s

				Graph 识别意图：
				%s

				客服处理策略：
				%s

				Graph 选择 Skill：
				%s

				可用 Skills：
				%s

				客服长期记忆：
				%s

				最近对话历史：
				%s

				请使用 Spring AI Alibaba ReactAgent 的工具能力完成本轮客服处理。
				需要商品、订单、物流、议价底价、退款资格或售后状态事实时调用工具；需要政策或话术时检索知识库或读取 Skill；
				涉及退款、赔偿、取消订单、修改地址、承诺额外优惠、投诉升级等高风险动作时，必须请求人工接管。
				""".formatted(message, channel, intent, this.intentPlanner.instructionFor(intent), selectedSkill,
				this.skillService.listSkills(), memory.summary(), historySummary(history));
	}

	private List<CustomerServiceStep> agentSteps(CustomerServiceIntent intent, String selectedSkill) {
		return List.of(new CustomerServiceStep("ReceptionAgent", "识别客服意图：" + intent + "。"),
				new CustomerServiceStep("SkillAgent", "选择客服 Skill：" + selectedSkill + "。"),
				new CustomerServiceStep("OfficialReactAgent", "由 Spring AI Alibaba ReactAgent 执行工具选择和回复生成。"),
				new CustomerServiceStep("RiskReviewerAgent", "Graph 风控节点负责约束高风险动作。"));
	}

	private String extractContent(OverAllState state) {
		if (state == null) {
			return "官方智能客服 ReactAgent 没有返回结果。";
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

	private String historySummary(List<CustomerConversationMessage> history) {
		List<CustomerConversationMessage> safeHistory = history == null ? List.of() : history;
		int start = Math.max(0, safeHistory.size() - MAX_HISTORY_MESSAGES);
		StringBuilder builder = new StringBuilder();
		for (CustomerConversationMessage item : safeHistory.subList(start, safeHistory.size())) {
			if (item != null && item.content() != null && !item.content().isBlank()) {
				builder.append("- ").append(item.role()).append("：").append(item.content()).append("\n");
			}
		}
		return builder.isEmpty() ? "暂无" : builder.toString().trim();
	}

	private List<CustomerConversationMessage> historyValue(OverAllState state) {
		Optional<Object> value = state.value("history");
		if (value.isEmpty() || !(value.get() instanceof List<?> list)) {
			return List.of();
		}
		List<CustomerConversationMessage> history = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof CustomerConversationMessage message) {
				history.add(message);
			}
			else if (item instanceof Map<?, ?> map) {
				history.add(new CustomerConversationMessage(text(map.get("role"), "user"),
						text(map.get("content"), "")));
			}
		}
		return List.copyOf(history);
	}

	private List<CustomerServiceStep> appendStep(OverAllState state, String name, String detail) {
		List<CustomerServiceStep> steps = new ArrayList<>(steps(state, "graphSteps"));
		steps.add(new CustomerServiceStep(name, detail));
		return List.copyOf(steps);
	}

	private List<CustomerServiceStep> steps(OverAllState state, String key) {
		Optional<Object> value = state.value(key);
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

	private List<ToolCallDebugRecorder.ToolCallDebug> toolCalls(OverAllState state) {
		Optional<Object> value = state.value("toolCalls");
		if (value.isEmpty() || !(value.get() instanceof List<?> list)) {
			return List.of();
		}
		List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof ToolCallDebugRecorder.ToolCallDebug toolCall) {
				toolCalls.add(toolCall);
			}
			else if (item instanceof Map<?, ?> map) {
				toolCalls.add(new ToolCallDebugRecorder.ToolCallDebug(text(map.get("name"), ""),
						arguments(map.get("arguments")), text(map.get("result"), "")));
			}
		}
		return List.copyOf(toolCalls);
	}

	private Map<String, Object> arguments(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> arguments = new LinkedHashMap<>();
			map.forEach((key, argument) -> arguments.put(String.valueOf(key), argument));
			return Map.copyOf(arguments);
		}
		return Map.of();
	}

	private McpDebugInfo mcpDebugInfo(OverAllState state) {
		Optional<Object> value = state.value("mcpDebugInfo");
		if (value.isEmpty()) {
			return this.customerMcpService.snapshotDebugInfo();
		}
		Object raw = value.get();
		if (raw instanceof McpDebugInfo debugInfo) {
			return debugInfo;
		}
		if (raw instanceof Map<?, ?> map) {
			List<String> toolNames = map.get("availableToolNames") instanceof List<?> list
					? list.stream().map(String::valueOf).toList() : List.of();
			return new McpDebugInfo(text(map.get("mode"), "UNKNOWN"),
					Boolean.parseBoolean(text(map.get("realMcpAvailable"), "false")),
					text(map.get("selectedToolName"), ""), toolNames, text(map.get("fallbackReason"), ""),
					text(map.get("query"), ""), null);
		}
		return McpDebugInfo.none();
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
		if (value.isEmpty()) {
			return fallback;
		}
		Object raw = value.get();
		if (raw instanceof CustomerMemory memory) {
			return memory;
		}
		if (raw instanceof Map<?, ?> map) {
			CustomerMemory memory = new CustomerMemory();
			memory.setUserId(text(map.get("userId"), fallback.getUserId()));
			memory.setChannel(channelFromObject(map.get("channel"), fallback.getChannel()));
			memory.setRecentProductIds(stringList(map.get("recentProductIds")));
			memory.setRecentOrderIds(stringList(map.get("recentOrderIds")));
			memory.setPreferredTone(text(map.get("preferredTone"), fallback.getPreferredTone()));
			memory.setLastIntent(customerIntentFromObject(map.get("lastIntent"), fallback.getLastIntent()));
			memory.setLastQuestion(text(map.get("lastQuestion"), fallback.getLastQuestion()));
			memory.setRiskFlags(stringList(map.get("riskFlags")));
			memory.setConversationCount(number(map.get("conversationCount"), fallback.getConversationCount()));
			return memory;
		}
		return fallback;
	}

	private ChannelType channelFromObject(Object value, ChannelType fallback) {
		if (value instanceof ChannelType channel) {
			return channel;
		}
		try {
			return value == null ? fallback : ChannelType.valueOf(String.valueOf(value));
		}
		catch (IllegalArgumentException ex) {
			return fallback;
		}
	}

	private CustomerServiceIntent customerIntentFromObject(Object value, CustomerServiceIntent fallback) {
		if (value instanceof CustomerServiceIntent intent) {
			return intent;
		}
		try {
			return value == null ? fallback : CustomerServiceIntent.valueOf(String.valueOf(value));
		}
		catch (IllegalArgumentException ex) {
			return fallback;
		}
	}

	private List<String> stringList(Object value) {
		if (!(value instanceof Iterable<?> iterable)) {
			return new ArrayList<>();
		}
		List<String> values = new ArrayList<>();
		for (Object item : iterable) {
			if (item != null) {
				values.add(String.valueOf(item));
			}
		}
		return values;
	}

	private String stringValue(OverAllState state, String key, String fallback) {
		return state.value(key).map(String::valueOf).orElse(fallback);
	}

	private String text(Object value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? fallback : text;
	}

	private int number(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return value == null ? fallback : Integer.parseInt(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

	private String normalizeMessage(String message) {
		return message == null || message.isBlank() ? "你好，请问有什么可以帮你？" : message;
	}

}
