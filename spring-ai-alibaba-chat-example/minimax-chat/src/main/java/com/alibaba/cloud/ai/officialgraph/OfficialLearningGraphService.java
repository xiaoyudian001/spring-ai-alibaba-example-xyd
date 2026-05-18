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

package com.alibaba.cloud.ai.officialgraph;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.alibaba.cloud.ai.mcp.LearningMcpService;
import com.alibaba.cloud.ai.mcp.LearningMcpService.McpSearchResult;
import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.memory.LearningMemory;
import com.alibaba.cloud.ai.memory.LearningMemoryService;
import com.alibaba.cloud.ai.officialgraph.OfficialLearningGraphResult.OfficialGraphStep;
import com.alibaba.cloud.ai.planner.LearningIntent;
import com.alibaba.cloud.ai.planner.LearningIntentPlanner;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 官方 StateGraph 学习编排服务，通过 Spring AI Alibaba Graph 串联记忆、规划、MCP 和 ReactAgent。
 *
 * @author xyd
 * @date 2026-05-18 11:34:38
 */
@Service
public class OfficialLearningGraphService {

	private final ReactAgent officialLearningAgent;

	private final LearningMemoryService memoryService;

	private final LearningIntentPlanner intentPlanner;

	private final LearningMcpService mcpService;

	private final ToolCallDebugRecorder debugRecorder;

	private final CompiledGraph compiledGraph;

	private final String graphDefinition;

	/**
	 * 创建官方 StateGraph 学习编排服务。
	 * @param officialLearningAgent 官方学习 ReactAgent
	 * @param memoryService 学习记忆服务
	 * @param intentPlanner 学习意图规划器
	 * @param mcpService MCP 学习资源服务
	 * @param debugRecorder 工具调用调试记录器
	 * @throws GraphStateException 官方 StateGraph 构建失败时抛出
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	public OfficialLearningGraphService(@Qualifier("officialLearningAgent") ReactAgent officialLearningAgent,
			LearningMemoryService memoryService, LearningIntentPlanner intentPlanner, LearningMcpService mcpService,
			ToolCallDebugRecorder debugRecorder) throws GraphStateException {
		this.officialLearningAgent = officialLearningAgent;
		this.memoryService = memoryService;
		this.intentPlanner = intentPlanner;
		this.mcpService = mcpService;
		this.debugRecorder = debugRecorder;
		StateGraph graph = buildGraph();
		this.graphDefinition = graph.getGraph(GraphRepresentation.Type.MERMAID, "official learning graph").content();
		this.compiledGraph = graph.compile();
	}

	/**
	 * 执行一轮官方 StateGraph 学习对话。
	 * @param userId 用户唯一标识
	 * @param message 用户问题
	 * @return 官方 StateGraph 响应结果
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	public OfficialLearningGraphResult chat(String userId, String message) {
		this.debugRecorder.clear();
		this.mcpService.clearDebugInfo();
		String normalizedUserId = normalizeUserId(userId);
		this.mcpService.useUser(normalizedUserId);
		try {
			RunnableConfig config = RunnableConfig.builder()
					.threadId(normalizedUserId + "-official-graph")
					.build();
			Optional<OverAllState> result = this.compiledGraph.invoke(Map.of("userId", normalizedUserId, "message",
					normalizeMessage(message)), config);
			OverAllState state = result.orElseThrow();
			return toResult(state);
		}
		catch (Exception ex) {
			LearningMemory memory = this.memoryService.read(normalizedUserId);
			return new OfficialLearningGraphResult("官方 StateGraph 调用失败：" + ex.getMessage(),
					LearningIntent.GENERAL_CHAT, memory, memory, List.of(step("error", ex.getMessage())),
					this.debugRecorder.snapshot(), this.mcpService.snapshotDebugInfo(), Map.of(),
					this.graphDefinition);
		}
		finally {
			this.debugRecorder.remove();
			this.mcpService.clearDebugInfo();
			this.mcpService.clearUser();
		}
	}

	/**
	 * 构建官方 StateGraph 节点和边。
	 * @return 官方 StateGraph
	 * @throws GraphStateException 图状态构建失败时抛出
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private StateGraph buildGraph() throws GraphStateException {
		KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
				.addPatternStrategy("userId", new ReplaceStrategy())
				.addPatternStrategy("message", new ReplaceStrategy())
				.addPatternStrategy("memoryBefore", new ReplaceStrategy())
				.addPatternStrategy("memoryAfter", new ReplaceStrategy())
				.addPatternStrategy("intent", new ReplaceStrategy())
				.addPatternStrategy("content", new ReplaceStrategy())
				.addPatternStrategy("agentState", new ReplaceStrategy())
				.addPatternStrategy("mcpContext", new ReplaceStrategy())
				.addPatternStrategy("mcpDebugInfo", new ReplaceStrategy())
				.addPatternStrategy("toolCalls", new ReplaceStrategy())
				.addPatternStrategy("graphSteps", new ReplaceStrategy())
				.build();

		return new StateGraph(keyStrategyFactory)
				.addNode("memory_read", node_async(memoryReadNode()))
				.addNode("planner", node_async(plannerNode()))
				.addNode("mcp_node", node_async(mcpNode()))
				.addNode("react_agent", node_async(reactAgentNode()))
				.addNode("memory_write", node_async(memoryWriteNode()))
				.addNode("response", node_async(responseNode()))
				.addEdge(START, "memory_read")
				.addEdge("memory_read", "planner")
				.addEdge("planner", "mcp_node")
				.addEdge("mcp_node", "react_agent")
				.addEdge("react_agent", "memory_write")
				.addEdge("memory_write", "response")
				.addEdge("response", END);
	}

	/**
	 * 读取用户长期学习记忆节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private NodeAction memoryReadNode() {
		return state -> {
			String userId = stringValue(state, "userId", "default-user");
			LearningMemory memory = this.memoryService.read(userId);
			return Map.of("memoryBefore", memory, "graphSteps",
					appendStep(state, "memory_read", "按 userId 读取长期学习记忆：" + memory.summary()));
		};
	}

	/**
	 * 识别学习意图节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private NodeAction plannerNode() {
		return state -> {
			String message = stringValue(state, "message", "");
			LearningIntent intent = this.intentPlanner.plan(message);
			return Map.of("intent", intent, "graphSteps", appendStep(state, "planner", "识别意图为 " + intent + "。"));
		};
	}

	/**
	 * 预取 MCP 学习资源节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private NodeAction mcpNode() {
		return state -> {
			String message = stringValue(state, "message", "");
			McpSearchResult mcpResult = this.mcpService.searchProjectKnowledgeWithStatus(message, 2);
			return Map.of("mcpContext", mcpResult.content(),
					"mcpDebugInfo", toDebugInfo(message, 2, mcpResult),
					"graphSteps",
					appendStep(state, "mcp_node", "MCP Node 使用 " + mcpResult.source()
							+ " 准备学习资源；真实 MCP 可用：" + mcpResult.realMcpAvailable() + "。"));
		};
	}

	/**
	 * 调用官方 ReactAgent 节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private NodeAction reactAgentNode() {
		return state -> {
			this.debugRecorder.clear();
			String userId = stringValue(state, "userId", "default-user");
			String message = stringValue(state, "message", "");
			String mcpContext = stringValue(state, "mcpContext", "");
			LearningIntent intent = intentValue(state, "intent");
			LearningMemory memory = memoryValue(state, "memoryBefore", this.memoryService.read(userId));
			RunnableConfig config = RunnableConfig.builder()
					.threadId(userId + "-official-react-agent")
					.build();
			Optional<NodeOutput> output = this.officialLearningAgent.invokeAndGetOutput(buildAgentPrompt(message,
					intent, memory, mcpContext), config);
			OverAllState agentState = output.map(NodeOutput::state).orElse(null);
			String content = extractContent(agentState);
			List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = this.debugRecorder.snapshot();
			this.debugRecorder.remove();
			return Map.of("content", content, "agentState", agentState == null ? Map.of() : agentState.data(),
					"toolCalls", toolCalls, "graphSteps",
					appendStep(state, "react_agent", "调用官方 ReactAgent，模型触发工具 " + toolCalls.size() + " 次。"));
		};
	}

	/**
	 * 写入用户长期学习记忆节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private NodeAction memoryWriteNode() {
		return state -> {
			String userId = stringValue(state, "userId", "default-user");
			String message = stringValue(state, "message", "");
			LearningIntent intent = intentValue(state, "intent");
			LearningMemory memoryAfter = this.memoryService.update(userId, message, intent);
			return Map.of("memoryAfter", memoryAfter, "graphSteps",
					appendStep(state, "memory_write", "根据本轮问题和意图更新长期 Memory：" + memoryAfter.summary()));
		};
	}

	/**
	 * 汇总响应节点。
	 * @return 官方 Graph 节点动作
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private NodeAction responseNode() {
		return state -> Map.of("graphSteps",
				appendStep(state, "response", "汇总回答、Graph 节点、工具调用和 Memory 信息。"));
	}

	/**
	 * 将官方 Graph 状态转换为前端响应结果。
	 * @param state 官方 Graph 总状态
	 * @return 官方 StateGraph 响应结果
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private OfficialLearningGraphResult toResult(OverAllState state) {
		String userId = stringValue(state, "userId", "default-user");
		LearningMemory memoryBefore = memoryValue(state, "memoryBefore", this.memoryService.read(userId));
		LearningMemory memoryAfter = memoryValue(state, "memoryAfter", memoryBefore);
		LearningIntent intent = intentValue(state, "intent");
		String content = stringValue(state, "content", "官方 StateGraph 没有返回内容。");
		List<OfficialGraphStep> steps = steps(state);
		List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = toolCalls(state);
		McpDebugInfo mcpDebugInfo = mcpDebugInfo(state);
		return new OfficialLearningGraphResult(content, intent, memoryBefore, memoryAfter, steps, toolCalls,
				mcpDebugInfo, state.data(), this.graphDefinition);
	}

	/**
	 * 构造传入官方 ReactAgent 的提示词。
	 * @param message 用户问题
	 * @param intent 学习意图
	 * @param memory 用户长期学习记忆
	 * @param mcpContext MCP 预取上下文
	 * @return ReactAgent 输入提示词
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private String buildAgentPrompt(String message, LearningIntent intent, LearningMemory memory, String mcpContext) {
		return """
				用户问题：
				%s

				Graph Planner 识别意图：
				%s

				用户长期学习记忆：
				%s

				Graph MCP Node 预取资源：
				%s

				请结合用户问题、记忆和 MCP 预取资源回答。
				需要真实时间、学习建议、学习计划、概念解释或当前项目资料时，请调用可用工具。
				如果用户明确要求保存、记录、沉淀或新增学习资源，请调用 createMcpLearningResource。
				如果用户明确要求修改、更新或完善已有学习资源，请调用 updateMcpLearningResource。
				""".formatted(message, intent, memory.summary(), mcpContext);
	}

	private McpDebugInfo toDebugInfo(String query, Integer limit, McpSearchResult result) {
		return new McpDebugInfo(result.source(), result.realMcpAvailable(), result.selectedToolName(),
				result.availableToolNames(), result.fallbackReason(), query == null ? "" : query, limit,
				false, "read-only", null);
	}

	private McpDebugInfo mcpDebugInfo(OverAllState state) {
		Optional<Object> value = state.value("mcpDebugInfo");
		if (value.isEmpty()) {
			return this.mcpService.snapshotDebugInfo();
		}
		Object raw = value.get();
		if (raw instanceof McpDebugInfo debugInfo) {
			return debugInfo;
		}
		if (raw instanceof Map<?, ?> map) {
			List<String> toolNames = map.get("availableToolNames") instanceof List<?> list
					? list.stream().map(String::valueOf).toList() : List.of();
			return new McpDebugInfo(stringValue(map, "mode", "UNKNOWN"),
					Boolean.parseBoolean(stringValue(map, "realMcpAvailable", "false")),
					stringValue(map, "selectedToolName", ""), toolNames,
					stringValue(map, "fallbackReason", ""),
					stringValue(map, "query", ""), integerValue(map.get("limit")),
					Boolean.parseBoolean(stringValue(map, "writeEnabled", "false")),
					stringValue(map, "writeMode", "disabled"), null);
		}
		return McpDebugInfo.none();
	}

	private String stringValue(Map<?, ?> map, String key, String fallback) {
		Object value = map.get(key);
		return value == null ? fallback : String.valueOf(value);
	}

	private Integer integerValue(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value == null) {
			return null;
		}
		try {
			return Integer.parseInt(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private String extractContent(OverAllState state) {
		if (state == null) {
			return "官方 ReactAgent 没有返回结果。";
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

	private List<OfficialGraphStep> appendStep(OverAllState state, String node, String detail) {
		List<OfficialGraphStep> steps = new ArrayList<>(steps(state));
		steps.add(step(node, detail));
		return List.copyOf(steps);
	}

	private List<OfficialGraphStep> steps(OverAllState state) {
		Optional<Object> value = state.value("graphSteps");
		if (value.isEmpty()) {
			return List.of();
		}
		Object rawSteps = value.get();
		if (!(rawSteps instanceof List<?> list)) {
			return List.of();
		}
		List<OfficialGraphStep> steps = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof OfficialGraphStep step) {
				steps.add(step);
			}
			else if (item instanceof Map<?, ?> map) {
				steps.add(step(text(map.get("node"), ""), text(map.get("detail"), "")));
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

	private OfficialGraphStep step(String node, String detail) {
		return new OfficialGraphStep(node, detail);
	}

	private String stringValue(OverAllState state, String key, String fallback) {
		return state.value(key).map(String::valueOf).orElse(fallback);
	}

	private LearningIntent intentValue(OverAllState state, String key) {
		Optional<Object> value = state.value(key);
		if (value.isEmpty()) {
			return LearningIntent.GENERAL_CHAT;
		}
		Object rawIntent = value.get();
		if (rawIntent instanceof LearningIntent intent) {
			return intent;
		}
		try {
			return LearningIntent.valueOf(String.valueOf(rawIntent));
		}
		catch (IllegalArgumentException ex) {
			return LearningIntent.GENERAL_CHAT;
		}
	}

	private LearningMemory memoryValue(OverAllState state, String key, LearningMemory fallback) {
		Optional<Object> value = state.value(key);
		if (value.isEmpty()) {
			return fallback;
		}
		Object rawMemory = value.get();
		if (rawMemory instanceof LearningMemory memory) {
			return memory;
		}
		if (rawMemory instanceof Map<?, ?> map) {
			return memoryFromMap(map, fallback);
		}
		return fallback;
	}

	private LearningMemory memoryFromMap(Map<?, ?> map, LearningMemory fallback) {
		LearningMemory memory = new LearningMemory(text(map.get("userId"), fallback.getUserId()));
		memory.setLevel(text(map.get("level"), fallback.getLevel()));
		memory.getTopics().addAll(topics(map.get("topics")));
		memory.setLastIntent(text(map.get("lastIntent"), fallback.getLastIntent()));
		memory.setLastQuestion(text(map.get("lastQuestion"), fallback.getLastQuestion()));
		memory.setConversationCount(number(map.get("conversationCount"), fallback.getConversationCount()));
		memory.setUpdatedAt(localDateTime(map.get("updatedAt"), fallback.getUpdatedAt()));
		return memory;
	}

	private Set<String> topics(Object value) {
		Set<String> topics = new LinkedHashSet<>();
		if (value instanceof Iterable<?> iterable) {
			for (Object item : iterable) {
				if (item != null) {
					topics.add(String.valueOf(item));
				}
			}
		}
		return topics;
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

	private LocalDateTime localDateTime(Object value, LocalDateTime fallback) {
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime;
		}
		if (value instanceof String text && !text.isBlank()) {
			try {
				return LocalDateTime.parse(text);
			}
			catch (RuntimeException ex) {
				return fallback;
			}
		}
		return fallback;
	}

	private String normalizeUserId(String userId) {
		if (userId == null || userId.isBlank()) {
			return "default-user";
		}
		return userId.trim();
	}

	private String normalizeMessage(String message) {
		if (message == null || message.isBlank()) {
			return "你好，请介绍一下你自己。";
		}
		return message;
	}

}
