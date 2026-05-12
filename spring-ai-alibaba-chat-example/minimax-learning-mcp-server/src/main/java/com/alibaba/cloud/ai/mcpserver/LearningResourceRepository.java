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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

@Repository
public class LearningResourceRepository {

	private static final String CLASSPATH_RESOURCE_FILE = "learning-resources.json";

	private final ObjectMapper objectMapper;

	private final Path resourceFilePath;

	private final List<LearningResource> resources;

	private final String resourceSource;

	public LearningResourceRepository(ObjectMapper objectMapper,
			@Value("${learning.resources.file:src/main/resources/learning-resources.json}") String resourceFile) {
		this.objectMapper = objectMapper;
		this.resourceFilePath = resolveResourceFilePath(resourceFile);
		ResourceLoadResult loadResult = loadResources();
		this.resources = new ArrayList<>(loadResult.resources());
		this.resourceSource = loadResult.resourceSource();
	}

	public synchronized List<LearningResource> all() {
		return List.copyOf(this.resources);
	}

	public String resourceSource() {
		return this.resourceSource;
	}

	public Path resourceFilePath() {
		return this.resourceFilePath;
	}

	public synchronized List<String> listTopics() {
		return this.resources.stream()
			.map(LearningResource::topic)
			.distinct()
			.toList();
	}

	public synchronized LearningResource getById(String id) {
		return findById(id).orElse(this.resources.get(0));
	}

	public synchronized Optional<LearningResource> findById(String id) {
		String safeId = normalize(id);
		return this.resources.stream()
			.filter(resource -> normalize(resource.id()).equals(safeId))
			.findFirst();
	}

	public synchronized List<LearningResource> search(String query, Integer limit) {
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

	public synchronized LearningResource create(LearningResource resource) {
		LearningResource sanitizedResource = sanitize(resource.id(), resource);
		if (findById(sanitizedResource.id()).isPresent()) {
			throw new IllegalArgumentException("资源 ID 已存在：" + sanitizedResource.id());
		}
		this.resources.add(sanitizedResource);
		saveResources();
		return sanitizedResource;
	}

	public synchronized Optional<LearningResource> update(String id, LearningResource resource) {
		String safeId = requireText(id, "id");
		for (int i = 0; i < this.resources.size(); i++) {
			if (normalize(this.resources.get(i).id()).equals(normalize(safeId))) {
				LearningResource sanitizedResource = sanitize(safeId, resource);
				this.resources.set(i, sanitizedResource);
				saveResources();
				return Optional.of(sanitizedResource);
			}
		}
		return Optional.empty();
	}

	public synchronized boolean delete(String id) {
		boolean removed = this.resources.removeIf(resource -> normalize(resource.id()).equals(normalize(id)));
		if (removed) {
			saveResources();
		}
		return removed;
	}

	public synchronized String formatSearchResult(String query, Integer limit) {
		List<LearningResource> hits = search(query, limit);
		return """
				真实 MCP Server 调用结果
				- Server：minimax-learning-mcp-server
				- 资源来源：%s
				- 查询：%s
				- 可用主题：%s
				- 命中资源数：%s

				%s
				""".formatted(this.resourceSource, query == null || query.isBlank() ? "全部" : query,
				String.join("、", listTopics()), hits.size(),
				hits.stream().map(this::format).collect(Collectors.joining("\n\n")));
	}

	private ResourceLoadResult loadResources() {
		if (Files.exists(this.resourceFilePath)) {
			try (InputStream inputStream = Files.newInputStream(this.resourceFilePath)) {
				List<LearningResource> loadedResources = readResources(inputStream);
				if (!loadedResources.isEmpty()) {
					return new ResourceLoadResult(loadedResources,
							"file:" + this.resourceFilePath.toAbsolutePath().normalize());
				}
			}
			catch (IOException ex) {
				// 外部资源文件损坏时继续尝试 classpath 资源，保证本地学习流程不中断。
			}
		}

		ClassPathResource resource = new ClassPathResource(CLASSPATH_RESOURCE_FILE);
		if (resource.exists()) {
			try (InputStream inputStream = resource.getInputStream()) {
				List<LearningResource> loadedResources = readResources(inputStream);
				if (!loadedResources.isEmpty()) {
					return new ResourceLoadResult(loadedResources, "classpath:" + CLASSPATH_RESOURCE_FILE);
				}
			}
			catch (IOException ex) {
				// 资源文件损坏时仍允许 MCP Server 启动，方便本地学习环境继续验证链路。
			}
		}
		return new ResourceLoadResult(fallbackResources(), "fallback:built-in");
	}

	private List<LearningResource> readResources(InputStream inputStream) throws IOException {
		List<LearningResource> loadedResources = this.objectMapper.readValue(inputStream,
				new TypeReference<List<LearningResource>>() {
				});
		return loadedResources == null ? List.of() : List.copyOf(loadedResources);
	}

	private void saveResources() {
		try {
			Path parent = this.resourceFilePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.resourceFilePath.toFile(), this.resources);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("写回学习资源 JSON 文件失败：" + this.resourceFilePath, ex);
		}
	}

	private Path resolveResourceFilePath(String resourceFile) {
		Path configuredPath = Paths.get(resourceFile);
		if (Files.exists(configuredPath) || configuredPath.isAbsolute()) {
			return configuredPath;
		}
		Path rootModulePath = Paths.get("spring-ai-alibaba-chat-example", "minimax-learning-mcp-server")
			.resolve(resourceFile);
		if (Files.exists(rootModulePath)) {
			return rootModulePath;
		}
		return configuredPath;
	}

	private LearningResource sanitize(String id, LearningResource resource) {
		return new LearningResource(requireText(id, "id"), requireText(resource.topic(), "topic"),
				requireText(resource.title(), "title"), requireText(resource.summary(), "summary"),
				requireText(resource.nextAction(), "nextAction"));
	}

	private String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("资源字段不能为空：" + fieldName);
		}
		return value.trim();
	}

	private List<LearningResource> fallbackResources() {
		return List.of(
				new LearningResource("mcp-tool", "Tool", "Tool Calling 基础",
						"Tool 是模型可以调用的具体函数入口，适合从 getCurrentTime、generateDailyPlan 这类小工具开始理解。",
						"在 minimax-chat 中观察 MiniMaxLearningTools 和 OfficialLearningToolCallbacks。"),
				new LearningResource("mcp-skill", "Skill", "Skill 业务封装",
						"Skill 负责稳定业务逻辑，Tool 只负责暴露给模型调用。这样以后接 Agent 或 MCP 时业务逻辑可以复用。",
						"阅读 LearningSkillService，确认 Tool 如何委托给 Skill。"),
				new LearningResource("mcp-agent", "Agent", "ReactAgent 调用链",
						"Agent 负责把模型、工具、上下文和执行状态组织起来，让模型可以按需调用工具后再回答。",
						"测试 /minimax/chat-client/official-agent/chat 并观察 toolCalls。"),
				new LearningResource("mcp-graph", "Graph", "StateGraph 编排",
						"Graph 用节点显式编排流程，例如 memory_read、planner、mcp_node、react_agent、memory_write。",
						"测试 /minimax/chat-client/official-graph/chat 并查看 graphSteps 和 graphDefinition。"),
				new LearningResource("mcp-memory", "Memory", "多用户 Memory",
						"Memory 记录用户阶段、关注主题、历史轮次和上次意图，适合做个性化学习助手。",
						"分别使用 user-a 和 user-b 测试长期记忆隔离。"),
				new LearningResource("mcp-rag", "RAG", "项目知识检索",
						"RAG 把 README、源码结构和项目说明作为上下文提供给模型，适合回答当前项目实现细节。",
						"询问当前 minimax-chat 调用链，观察 searchLearningDocs。"),
				new LearningResource("mcp-mcp", "MCP", "MCP Server / Client",
						"MCP 把外部工具和资源作为协议化能力提供给 Agent。minimax-chat 是 Client，本模块是 Server。",
						"启动本模块后访问 /minimax/chat-client/mcp/status，确认 REAL_MCP_READY。"));
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

	private record ResourceLoadResult(List<LearningResource> resources, String resourceSource) {
	}

}
