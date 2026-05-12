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

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class LearningResourceTool {

	private final LearningResourceRepository repository;

	public LearningResourceTool(LearningResourceRepository repository) {
		this.repository = repository;
	}

	@Tool(description = "查询 Spring AI Alibaba 学习资源。适合查询 Tool、Skill、Agent、Graph、Memory、RAG、MCP 的学习路线、资料和下一步建议。")
	public String searchLearningResources(
			@ToolParam(description = "查询关键词，例如 Agent、Graph、MCP、Tool Calling、Memory。") String query,
			@ToolParam(description = "返回资源数量，建议 1 到 5。") Integer limit) {
		return this.repository.formatSearchResult(query, limit);
	}

	@Tool(description = "列出当前 MCP Server 支持的 Spring AI Alibaba 学习主题。")
	public String listLearningTopics() {
		return String.join("、", this.repository.listTopics());
	}

	@Tool(description = "根据资源 ID 获取某个 Spring AI Alibaba 学习资源详情。")
	public String getLearningResource(
			@ToolParam(description = "资源 ID，例如 mcp-agent、mcp-graph、mcp-mcp。") String resourceId) {
		LearningResource resource = this.repository.getById(resourceId);
		return format("学习资源详情", resource);
	}

	@Tool(description = "创建新的 Spring AI Alibaba 学习资源，并写回 learning-resources.json。当用户明确要求保存、记录、沉淀、新增学习资源时使用。")
	public String createLearningResource(
			@ToolParam(description = "资源 ID，建议使用小写短横线，例如 mcp-tool-vs-skill。") String id,
			@ToolParam(description = "资源主题，例如 Tool、Skill、Agent、Graph、Memory、RAG、MCP。") String topic,
			@ToolParam(description = "资源标题。") String title,
			@ToolParam(description = "资源摘要，说明这条资源解决什么学习问题。") String summary,
			@ToolParam(description = "下一步学习建议。") String nextAction) {
		LearningResource resource = this.repository.create(new LearningResource(id, topic, title, summary, nextAction));
		return format("已创建学习资源", resource);
	}

	@Tool(description = "更新已有 Spring AI Alibaba 学习资源，并写回 learning-resources.json。当用户明确要求修改、更新、完善某条学习资源时使用。")
	public String updateLearningResource(
			@ToolParam(description = "要更新的资源 ID，例如 mcp-agent。") String id,
			@ToolParam(description = "资源主题，例如 Tool、Skill、Agent、Graph、Memory、RAG、MCP。") String topic,
			@ToolParam(description = "资源标题。") String title,
			@ToolParam(description = "资源摘要，说明这条资源解决什么学习问题。") String summary,
			@ToolParam(description = "下一步学习建议。") String nextAction) {
		LearningResource resource = this.repository.update(id, new LearningResource(id, topic, title, summary,
				nextAction)).orElseThrow(() -> new IllegalArgumentException("学习资源不存在：" + id));
		return format("已更新学习资源", resource);
	}

	private String format(String title, LearningResource resource) {
		return """
				%s
				### %s
				- 资源 ID：%s
				- 主题：%s
				- 摘要：%s
				- 下一步：%s
				""".formatted(title, resource.title(), resource.id(), resource.topic(), resource.summary(),
				resource.nextAction());
	}

}
