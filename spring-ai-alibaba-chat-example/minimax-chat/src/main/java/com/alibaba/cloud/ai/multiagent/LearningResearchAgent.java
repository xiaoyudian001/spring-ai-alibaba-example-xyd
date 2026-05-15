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
		String ragSummary = shouldSearchProject(message) ? compact(this.ragService.search(message, 2))
				: "本轮不是强项目源码/README 问题，RAG 作为可选上下文。";
		this.mcpService.useUser(userId);
		try {
			McpSearchResult mcpResult = this.mcpService.searchProjectKnowledgeWithStatus(message, 2);
			String detail = "ResearchAgent 已收集上下文：RAG 摘要：" + ragSummary + "；MCP 模式："
					+ mcpResult.source() + "。";
			return new ResearchOutput(ragSummary, compact(mcpResult.content()), detail);
		}
		finally {
			this.mcpService.clearUser();
		}
	}

	private boolean shouldSearchProject(String message) {
		String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
		return text.contains("项目") || text.contains("源码") || text.contains("readme") || text.contains("当前")
				|| text.contains("workflow") || text.contains("multi-agent") || text.contains("agentgraph")
				|| text.contains("mcp") || text.contains("rag") || text.contains("tool") || text.contains("skill");
	}

	private String compact(String text) {
		String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		return value.length() <= 260 ? value : value.substring(0, 260) + "...";
	}

	public record ResearchOutput(String ragSummary, String mcpSummary, String detail) {
	}

}
