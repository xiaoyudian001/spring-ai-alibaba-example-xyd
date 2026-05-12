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

package com.alibaba.cloud.ai.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Learning MCP facade.
 *
 * <p>
 * This service tries a real Spring AI MCP ToolCallbackProvider first. If no MCP
 * server is configured, or the MCP call fails, it falls back to the local mock
 * resource list so the learning demo remains runnable.
 */
@Service
public class LearningMcpService {

	private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;

	private final ObjectMapper objectMapper;

	private final boolean writeEnabled;

	private final String writeMode;

	private final ThreadLocal<McpDebugInfo> debugInfoHolder = ThreadLocal.withInitial(McpDebugInfo::none);

	private final List<McpLearningResource> resources = List.of(
			new McpLearningResource("mcp-tool", "Tool",
					"Tool Calling 基础",
					"理解 Tool 是模型可调用的具体函数入口，适合从 getCurrentTime 和 generateDailyPlan 开始。",
					"对比 MiniMaxLearningTools 和 OfficialLearningToolCallbacks。"),
			new McpLearningResource("mcp-agent", "Agent",
					"ReactAgent 调用链",
					"理解 ReactAgent 如何把模型调用、ToolCallback、MemorySaver 和状态输出组织起来。",
					"测试 /official-agent/chat 并观察 toolCalls。"),
			new McpLearningResource("mcp-graph", "Graph",
					"StateGraph 编排",
					"理解 StateGraph 如何把 memory_read、planner、mcp_node、react_agent、memory_write 串成节点流。",
					"测试 /official-graph/chat 并查看 graphDefinition。"),
			new McpLearningResource("mcp-memory", "Memory",
					"多用户 Memory",
					"理解短期 history 与长期 JSON Memory 的区别，以及 userId 如何隔离学习状态。",
					"分别用 user-a 和 user-b 测试 Memory 更新。"),
			new McpLearningResource("mcp-rag", "RAG",
					"本地知识检索",
					"理解 searchLearningDocs 如何把当前项目 README 和源码作为学习资料提供给模型。",
					"询问当前项目调用链并观察 searchLearningDocs。"),
			new McpLearningResource("mcp-mcp", "MCP",
					"MCP Node 入门",
					"理解 MCP 是外部工具和资源接入协议，本阶段先接入真实 MCP Client，再保留 mock fallback。",
					"配置 MCP Server 后询问通过 MCP 获取 Agent 学习资源。"));

	public LearningMcpService(ObjectProvider<ToolCallbackProvider> toolCallbackProvider, ObjectMapper objectMapper,
			@Value("${minimax.mcp.write-enabled:false}") boolean writeEnabled,
			@Value("${minimax.mcp.write-mode:dry-run}") String writeMode) {
		this.toolCallbackProvider = toolCallbackProvider;
		this.objectMapper = objectMapper;
		this.writeEnabled = writeEnabled;
		this.writeMode = normalizeWriteMode(writeMode);
	}

	public List<String> listLearningTopics() {
		return this.resources.stream()
				.map(McpLearningResource::topic)
				.distinct()
				.toList();
	}

	public McpLearningResource getLearningResource(String resourceId) {
		String safeResourceId = normalize(resourceId);
		return this.resources.stream()
				.filter(resource -> normalize(resource.id()).equals(safeResourceId))
				.findFirst()
				.orElse(this.resources.get(0));
	}

	public String searchProjectKnowledge(String query, Integer limit) {
		return searchProjectKnowledgeWithStatus(query, limit).content();
	}

	public McpWriteResult createLearningResource(String id, String topic, String title, String summary,
			String nextAction) {
		List<String> toolNames = availableToolNames();
		Map<String, Object> arguments = Map.of(
				"id", safeText(id),
				"topic", safeText(topic),
				"title", safeText(title),
				"summary", safeText(summary),
				"nextAction", safeText(nextAction));
		McpWriteResult result = guardedWrite("createLearningResource", id, arguments, toolNames);
		recordWriteDebug(id, result);
		return result;
	}

	public McpWriteResult updateLearningResource(String id, String topic, String title, String summary,
			String nextAction) {
		List<String> toolNames = availableToolNames();
		Map<String, Object> arguments = Map.of(
				"id", safeText(id),
				"topic", safeText(topic),
				"title", safeText(title),
				"summary", safeText(summary),
				"nextAction", safeText(nextAction));
		McpWriteResult result = guardedWrite("updateLearningResource", id, arguments, toolNames);
		recordWriteDebug(id, result);
		return result;
	}

	public McpSearchResult searchProjectKnowledgeWithStatus(String query, Integer limit) {
		List<String> toolNames = availableToolNames();
		Optional<McpSearchResult> realResult = invokeRealMcp(query, limit, toolNames);
		if (realResult.isPresent()) {
			McpSearchResult result = realResult.get();
			recordDebug(query, limit, result);
			return result;
		}
		McpSearchResult result = mockSearch(query, limit, toolNames, fallbackReason(toolNames));
		recordDebug(query, limit, result);
		return result;
	}

	public LearningMcpStatus status() {
		List<String> toolNames = availableToolNames();
		return new LearningMcpStatus(!toolNames.isEmpty(), toolNames.size(), toolNames,
				toolNames.isEmpty() ? "MOCK_FALLBACK" : "REAL_MCP_READY", this.writeEnabled, this.writeMode);
	}

	public McpDebugInfo snapshotDebugInfo() {
		return this.debugInfoHolder.get();
	}

	public void clearDebugInfo() {
		this.debugInfoHolder.remove();
	}

	private Optional<McpSearchResult> invokeRealMcp(String query, Integer limit, List<String> toolNames) {
		ToolCallback callback = selectMcpSearchTool();
		if (callback == null) {
			return Optional.empty();
		}
		try {
			String arguments = this.objectMapper.writeValueAsString(Map.of(
					"query", query == null ? "" : query,
					"limit", normalizeLimit(limit)));
			String content = callback.call(arguments);
			if (content == null || content.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new McpSearchResult(content, "REAL_MCP", true,
					callback.getToolDefinition().name(), toolNames, ""));
		}
		catch (JsonProcessingException ex) {
			return Optional.of(mockSearch(query, limit, toolNames, "MCP 参数序列化失败：" + ex.getMessage()));
		}
		catch (Exception ex) {
			return Optional.of(mockSearch(query, limit, toolNames, "真实 MCP 调用失败：" + ex.getMessage()));
		}
	}

	private ToolCallback selectMcpSearchTool() {
		return selectMcpTool(List.of("searchlearningresources"), List.of("search"));
	}

	private ToolCallback selectMcpWriteTool(String toolName) {
		return selectMcpTool(List.of(normalize(toolName)), List.of(normalize(toolName.replace("LearningResource", ""))));
	}

	private ToolCallback selectMcpTool(List<String> preferredNames, List<String> requiredKeywords) {
		ToolCallback[] callbacks = toolCallbacks();
		ToolCallback fallback = null;
		for (ToolCallback callback : callbacks) {
			String name = normalize(callback.getToolDefinition().name());
			String description = normalize(callback.getToolDefinition().description());
			if (preferredNames.contains(name) || preferredNames.stream().anyMatch(name::contains)) {
				return callback;
			}
			boolean keywordMatched = requiredKeywords.stream().allMatch(keyword -> name.contains(keyword)
					|| description.contains(keyword));
			if (keywordMatched && fallback == null) {
				fallback = callback;
			}
		}
		return fallback;
	}

	private Optional<McpWriteResult> invokeRealMcpWrite(String toolName, Map<String, Object> arguments,
			List<String> toolNames) {
		ToolCallback callback = selectMcpWriteTool(toolName);
		if (callback == null) {
			return Optional.empty();
		}
		try {
			String content = callback.call(this.objectMapper.writeValueAsString(arguments));
			if (content == null || content.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new McpWriteResult(content, "REAL_MCP", true,
					callback.getToolDefinition().name(), toolNames, "", this.writeEnabled, this.writeMode));
		}
		catch (JsonProcessingException ex) {
			return Optional.of(mockWrite(toolName, String.valueOf(arguments.get("id")), toolNames,
					"MCP 参数序列化失败：" + ex.getMessage()));
		}
		catch (Exception ex) {
			return Optional.of(mockWrite(toolName, String.valueOf(arguments.get("id")), toolNames,
					"真实 MCP 写入调用失败：" + ex.getMessage()));
		}
	}

	private McpWriteResult guardedWrite(String operation, String resourceId, Map<String, Object> arguments,
			List<String> toolNames) {
		if (!this.writeEnabled) {
			return blockedWrite(operation, resourceId, arguments, toolNames);
		}
		if ("dry-run".equals(this.writeMode)) {
			return dryRunWrite(operation, resourceId, arguments, toolNames);
		}
		return invokeRealMcpWrite(operation, arguments, toolNames)
			.orElseGet(() -> mockWrite(operation, resourceId, toolNames, fallbackReason(toolNames)));
	}

	private McpWriteResult blockedWrite(String operation, String resourceId, Map<String, Object> arguments,
			List<String> toolNames) {
		String content = """
				MCP 写入已被安全策略拦截
				- 操作：%s
				- 资源 ID：%s
				- 写入开关：false
				- 写入模式：disabled
				- 结果：未调用 MCP Server，未写入 learning-resources.json

				计划写入内容：
				%s
				""".formatted(operation, safeText(resourceId), prettyArguments(arguments));
		return new McpWriteResult(content, "MCP_WRITE_DISABLED", false, "", toolNames,
				"minimax.mcp.write-enabled=false", false, "disabled");
	}

	private McpWriteResult dryRunWrite(String operation, String resourceId, Map<String, Object> arguments,
			List<String> toolNames) {
		String content = """
				MCP 写入 Dry-Run 预览
				- 操作：%s
				- 资源 ID：%s
				- 写入开关：true
				- 写入模式：dry-run
				- 结果：未调用 MCP Server，未写入 learning-resources.json

				计划写入内容：
				%s
				""".formatted(operation, safeText(resourceId), prettyArguments(arguments));
		return new McpWriteResult(content, "MCP_WRITE_DRY_RUN", false, "", toolNames,
				"minimax.mcp.write-mode=dry-run", true, "dry-run");
	}

	private ToolCallback[] toolCallbacks() {
		return this.toolCallbackProvider.orderedStream()
				.flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
				.toArray(ToolCallback[]::new);
	}

	private List<String> availableToolNames() {
		List<String> names = new ArrayList<>();
		for (ToolCallback callback : toolCallbacks()) {
			names.add(callback.getToolDefinition().name());
		}
		return List.copyOf(names);
	}

	private McpSearchResult mockSearch(String query, Integer limit, List<String> toolNames, String fallbackReason) {
		String safeQuery = normalize(query);
		int safeLimit = normalizeLimit(limit);
		List<McpLearningResource> hits = this.resources.stream()
				.filter(resource -> matches(resource, safeQuery))
				.limit(safeLimit)
				.toList();
		if (hits.isEmpty()) {
			hits = this.resources.stream().limit(safeLimit).toList();
		}
		return new McpSearchResult(formatMock(query, hits, fallbackReason), "MOCK_MCP", !toolNames.isEmpty(),
				"", toolNames, fallbackReason);
	}

	private McpWriteResult mockWrite(String operation, String resourceId, List<String> toolNames,
			String fallbackReason) {
		String reason = fallbackReason == null || fallbackReason.isBlank()
				? "未配置真实 MCP Server，写入操作不会落盘。"
				: fallbackReason;
		String content = """
				Mock MCP 写入结果
				- 操作：%s
				- 资源 ID：%s
				- 来源：MOCK_MCP
				- 写入模式：%s
				- 写入状态：未写入真实 learning-resources.json
				- 兜底原因：%s
				""".formatted(operation, safeText(resourceId), this.writeMode, reason);
		return new McpWriteResult(content, "MOCK_MCP", !toolNames.isEmpty(), "", toolNames, reason,
				this.writeEnabled, this.writeMode);
	}

	private boolean matches(McpLearningResource resource, String query) {
		if (query.isBlank()) {
			return true;
		}
		String text = normalize(String.join(" ", resource.id(), resource.topic(), resource.title(),
				resource.summary(), resource.nextAction()));
		for (String token : query.split("\\s+")) {
			if (!token.isBlank() && text.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private String formatMock(String query, List<McpLearningResource> hits, String fallbackReason) {
		String reason = fallbackReason == null || fallbackReason.isBlank() ? "未配置真实 MCP Server，使用本地 mock 资源。"
				: fallbackReason;
		return """
				Mock MCP 调用结果
				- 来源：MOCK_MCP
				- 兜底原因：%s
				- 查询：%s
				- 可用主题：%s
				- 命中资源数：%s

				%s
				""".formatted(reason, query == null || query.isBlank() ? "全部" : query,
				String.join("、", listLearningTopics()), hits.size(),
				hits.stream().map(this::format).collect(Collectors.joining("\n\n")));
	}

	private String format(McpLearningResource resource) {
		return """
				### %s
				- 资源 ID：%s
				- 主题：%s
				- 摘要：%s
				- 下一步：%s
				""".formatted(resource.title(), resource.id(), resource.topic(), resource.summary(),
				resource.nextAction());
	}

	private String fallbackReason(List<String> toolNames) {
		return toolNames.isEmpty() ? "未发现 Spring AI MCP ToolCallbackProvider。请启用 spring.ai.mcp.client 并配置 MCP Server。"
				: "未找到可用的 MCP 查询工具。";
	}

	private int normalizeLimit(Integer limit) {
		return limit == null || limit < 1 ? 3 : Math.min(limit, 5);
	}

	private void recordDebug(String query, Integer limit, McpSearchResult result) {
		this.debugInfoHolder.set(new McpDebugInfo(result.source(), result.realMcpAvailable(),
				result.selectedToolName(), result.availableToolNames(), result.fallbackReason(),
				query == null ? "" : query, normalizeLimit(limit), this.writeEnabled, this.writeMode));
	}

	private void recordWriteDebug(String resourceId, McpWriteResult result) {
		this.debugInfoHolder.set(new McpDebugInfo(result.source(), result.realMcpAvailable(),
				result.selectedToolName(), result.availableToolNames(), result.fallbackReason(),
				resourceId == null ? "" : resourceId, null, result.writeEnabled(), result.writeMode()));
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	private String safeText(String value) {
		return value == null ? "" : value.trim();
	}

	private String normalizeWriteMode(String writeMode) {
		String safeWriteMode = normalize(writeMode);
		return "commit".equals(safeWriteMode) ? "commit" : "dry-run";
	}

	private String prettyArguments(Map<String, Object> arguments) {
		try {
			return this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(arguments);
		}
		catch (JsonProcessingException ex) {
			return String.valueOf(arguments);
		}
	}

	public record LearningMcpStatus(boolean realMcpAvailable, int toolCount, List<String> toolNames, String mode,
			boolean writeEnabled, String writeMode) {
	}

	public record McpSearchResult(String content, String source, boolean realMcpAvailable, String selectedToolName,
			List<String> availableToolNames, String fallbackReason) {
	}

	public record McpWriteResult(String content, String source, boolean realMcpAvailable, String selectedToolName,
			List<String> availableToolNames, String fallbackReason, boolean writeEnabled, String writeMode) {
	}

}
