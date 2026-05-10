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

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Mock MCP layer for learning purposes.
 *
 * <p>
 * This service simulates capabilities normally discovered from an external MCP
 * server. Keeping it local lets the demo teach the call position before adding
 * stdio/SSE MCP transport and server lifecycle concerns.
 */
@Service
public class LearningMcpService {

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
					"理解 StateGraph 如何把 memory_read、planner、react_agent、memory_write 串成节点流。",
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
					"理解 MCP 是外部工具和资源接入协议，本阶段先用 mock MCP 模拟资源发现和调用。",
					"询问通过 MCP 获取 Agent 学习资源。"));

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
		String safeQuery = normalize(query);
		int safeLimit = limit == null || limit < 1 ? 3 : Math.min(limit, 5);
		List<McpLearningResource> hits = this.resources.stream()
				.filter(resource -> matches(resource, safeQuery))
				.limit(safeLimit)
				.toList();
		if (hits.isEmpty()) {
			hits = this.resources.stream().limit(safeLimit).toList();
		}
		return format(query, hits);
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

	private String format(String query, List<McpLearningResource> hits) {
		return """
				Mock MCP 调用结果
				- 查询：%s
				- 可用主题：%s
				- 命中资源数：%s

				%s
				""".formatted(query == null || query.isBlank() ? "全部" : query,
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

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

}
