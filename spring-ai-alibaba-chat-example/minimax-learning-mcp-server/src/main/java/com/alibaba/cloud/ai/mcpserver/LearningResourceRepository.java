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

package com.alibaba.cloud.ai.mcpserver;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

@Repository
public class LearningResourceRepository {

	private final List<LearningResource> resources = List.of(
			new LearningResource("mcp-tool", "Tool",
					"Tool Calling 基础",
					"Tool 是模型可以调用的具体函数入口，适合从 getCurrentTime、generateDailyPlan 这类小工具开始理解。",
					"在 minimax-chat 中观察 MiniMaxLearningTools 和 OfficialLearningToolCallbacks。"),
			new LearningResource("mcp-skill", "Skill",
					"Skill 业务封装",
					"Skill 负责稳定业务逻辑，Tool 只负责暴露给模型调用。这样以后接 Agent 或 MCP 时业务逻辑可以复用。",
					"阅读 LearningSkillService，确认 Tool 如何委托给 Skill。"),
			new LearningResource("mcp-agent", "Agent",
					"ReactAgent 调用链",
					"Agent 负责把模型、工具、上下文和执行状态组织起来，让模型可以按需调用工具后再回答。",
					"测试 /minimax/chat-client/official-agent/chat 并观察 toolCalls。"),
			new LearningResource("mcp-graph", "Graph",
					"StateGraph 编排",
					"Graph 用节点显式编排流程，例如 memory_read、planner、mcp_node、react_agent、memory_write。",
					"测试 /minimax/chat-client/official-graph/chat 并查看 graphSteps 与 graphDefinition。"),
			new LearningResource("mcp-memory", "Memory",
					"多用户 Memory",
					"Memory 记录用户阶段、关注主题、历史轮次和上次意图，适合做个性化学习助手。",
					"分别使用 user-a 和 user-b 测试长期记忆隔离。"),
			new LearningResource("mcp-rag", "RAG",
					"项目知识检索",
					"RAG 把 README、源码结构和项目说明作为上下文提供给模型，适合回答当前项目实现细节。",
					"询问当前 minimax-chat 调用链，观察 searchLearningDocs。"),
			new LearningResource("mcp-mcp", "MCP",
					"MCP Server / Client",
					"MCP 把外部工具和资源作为协议化能力提供给 Agent。minimax-chat 是 Client，本模块是 Server。",
					"启动本模块后访问 /minimax/chat-client/mcp/status，确认 REAL_MCP_READY。"));

	public List<String> listTopics() {
		return this.resources.stream()
				.map(LearningResource::topic)
				.distinct()
				.toList();
	}

	public LearningResource getById(String id) {
		String safeId = normalize(id);
		return this.resources.stream()
				.filter(resource -> normalize(resource.id()).equals(safeId))
				.findFirst()
				.orElse(this.resources.get(0));
	}

	public List<LearningResource> search(String query, Integer limit) {
		String safeQuery = normalize(query);
		int safeLimit = limit == null || limit < 1 ? 3 : Math.min(limit, 5);
		List<LearningResource> hits = this.resources.stream()
				.filter(resource -> matches(resource, safeQuery))
				.limit(safeLimit)
				.toList();
		if (hits.isEmpty()) {
			return this.resources.stream().limit(safeLimit).toList();
		}
		return hits;
	}

	public String formatSearchResult(String query, Integer limit) {
		List<LearningResource> hits = search(query, limit);
		return """
				真实 MCP Server 调用结果
				- Server：minimax-learning-mcp-server
				- 查询：%s
				- 可用主题：%s
				- 命中资源数：%s

				%s
				""".formatted(query == null || query.isBlank() ? "全部" : query,
				String.join("、", listTopics()), hits.size(),
				hits.stream().map(this::format).collect(Collectors.joining("\n\n")));
	}

	private boolean matches(LearningResource resource, String query) {
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

	private String format(LearningResource resource) {
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
