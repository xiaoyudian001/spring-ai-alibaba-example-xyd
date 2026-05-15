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

package com.alibaba.cloud.ai.multiagent;

import java.util.Locale;

import com.alibaba.cloud.ai.mcp.LearningMcpService;
import com.alibaba.cloud.ai.mcp.LearningMcpService.McpSearchResult;
import com.alibaba.cloud.ai.rag.LearningRagService;
import org.springframework.stereotype.Component;

/**
 * Collects project context for the multi-agent chain.
 */
@Component
public class LearningResearchAgent {

	private final LearningRagService ragService;

	private final LearningMcpService mcpService;

	public LearningResearchAgent(LearningRagService ragService, LearningMcpService mcpService) {
		this.ragService = ragService;
		this.mcpService = mcpService;
	}

	public ResearchOutput research(String userId, String message) {
		ResearchScope scope = detectScope(message);
		if (scope == ResearchScope.GENERAL_CONCEPT) {
			String ragSummary = "本轮是通用概念学习问题，不强制绑定当前 minimax-chat 项目源码。回答应先解释通用原理，再按需补充本项目落地映射。";
			String mcpSummary = "未强制调用项目知识检索，避免把通用 Workflow / AgentGraph / Multi-Agent 概念误收窄为当前项目实现。";
			String detail = "ResearchAgent 判断范围为 GENERAL_CONCEPT：优先支持通用概念学习，项目上下文只作为可选补充。";
			return new ResearchOutput(scope.name(), ragSummary, mcpSummary, detail);
		}
		String ragSummary = compact(this.ragService.search(message, 2));
		this.mcpService.useUser(userId);
		try {
			McpSearchResult mcpResult = this.mcpService.searchProjectKnowledgeWithStatus(message, 2);
			String detail = "ResearchAgent 判断范围为 PROJECT_IMPLEMENTATION，已收集项目上下文：RAG 摘要：" + ragSummary + "；MCP 模式："
					+ mcpResult.source() + mcpFallbackAdvice(mcpResult) + "。";
			return new ResearchOutput(scope.name(), ragSummary, compact(mcpResult.content()), detail);
		}
		finally {
			this.mcpService.clearUser();
		}
	}

	private String mcpFallbackAdvice(McpSearchResult mcpResult) {
		if (mcpResult.realMcpAvailable()) {
			return "，真实 MCP 可用";
		}
		return "，真实 MCP 当前不可用，已使用 Mock/Fallback；备选方案：确认 mcp profile、MCP Server 端口、spring.ai.mcp.client 配置和 ToolCallbackProvider 是否生效";
	}

	private ResearchScope detectScope(String message) {
		String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
		boolean projectCue = text.contains("当前项目") || text.contains("这个项目") || text.contains("本项目")
				|| text.contains("项目中") || text.contains("项目里") || text.contains("项目里面")
				|| text.contains("当前实现") || text.contains("源码") || text.contains("代码")
				|| text.contains("readme") || text.contains("minimax-chat") || text.contains("application.yml")
				|| text.contains("controller") || text.contains("service") || text.contains("接口")
				|| text.contains("类") || text.contains("包") || text.contains("文件");
		return projectCue ? ResearchScope.PROJECT_IMPLEMENTATION : ResearchScope.GENERAL_CONCEPT;
	}

	private String compact(String text) {
		String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		return value.length() <= 260 ? value : value.substring(0, 260) + "...";
	}

	public enum ResearchScope {

		GENERAL_CONCEPT,

		PROJECT_IMPLEMENTATION

	}

	public record ResearchOutput(String scope, String ragSummary, String mcpSummary, String detail) {
	}

}
