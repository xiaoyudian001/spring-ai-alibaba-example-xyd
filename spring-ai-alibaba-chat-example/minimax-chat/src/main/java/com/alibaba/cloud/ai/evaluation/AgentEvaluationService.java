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

package com.alibaba.cloud.ai.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.alibaba.cloud.ai.evaluation.AgentEvaluationResult.EvaluationCheck;
import com.alibaba.cloud.ai.report.AgentRunReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Evaluates agent execution reports with deterministic rules.
 */
@Service
public class AgentEvaluationService {

	private static final int DEFAULT_LIMIT = 20;

	private final ObjectMapper objectMapper;

	private final Path evaluationFile;

	private final int maxEvaluations;

	public AgentEvaluationService(ObjectMapper objectMapper,
			@Value("${minimax.evaluation.file:report/agent-evaluations.json}") String evaluationFile,
			@Value("${minimax.evaluation.max-evaluations:200}") int maxEvaluations) {
		this.objectMapper = objectMapper;
		this.evaluationFile = Path.of(evaluationFile);
		this.maxEvaluations = Math.max(20, maxEvaluations);
	}

	public AgentEvaluationResult evaluateAndSave(AgentRunReport report) {
		return append(evaluate(report));
	}

	public synchronized List<AgentEvaluationResult> latest(int limit) {
		List<AgentEvaluationResult> evaluations = readAll();
		Collections.reverse(evaluations);
		int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, this.maxEvaluations);
		return List.copyOf(evaluations.subList(0, Math.min(safeLimit, evaluations.size())));
	}

	public synchronized int clear() {
		int count = readAll().size();
		writeAll(List.of());
		return count;
	}

	private AgentEvaluationResult evaluate(AgentRunReport report) {
		List<EvaluationCheck> checks = new ArrayList<>();
		checks.add(intentCheck(report));
		checks.add(answerCheck(report));
		checks.add(memoryCheck(report));
		checks.add(timeToolCheck(report));
		checks.add(saveResourceCheck(report));
		checks.add(projectKnowledgeCheck(report));
		int maxScore = (int) checks.stream().filter(EvaluationCheck::applicable).count();
		int score = (int) checks.stream().filter(EvaluationCheck::applicable).filter(EvaluationCheck::passed).count();
		String level = level(score, maxScore);
		return new AgentEvaluationResult(newId(), report.id(), Instant.now(), report.userId(), report.chainMode(),
				report.intent(), score, maxScore, level, maxScore > 0 && score == maxScore, List.copyOf(checks));
	}

	private EvaluationCheck intentCheck(AgentRunReport report) {
		boolean passed = report.intent() != null && !report.intent().isBlank() && !"UNKNOWN".equals(report.intent());
		return new EvaluationCheck("意图识别", true, passed,
				passed ? "Planner 返回了有效意图：" + report.intent() : "Planner 没有返回有效意图。");
	}

	private EvaluationCheck answerCheck(AgentRunReport report) {
		boolean passed = report.answerSummary() != null && !report.answerSummary().isBlank();
		return new EvaluationCheck("回答非空", true, passed, passed ? "回答摘要非空。" : "最终回答为空，需要检查模型调用。");
	}

	private EvaluationCheck memoryCheck(AgentRunReport report) {
		boolean passed = report.memoryAfter() != null && !sameJson(report.memoryBefore(), report.memoryAfter());
		return new EvaluationCheck("Memory 更新", true, passed,
				passed ? "Memory 在本轮调用后发生变化。" : "Memory 未变化，可能是问题未触发学习状态更新。");
	}

	private EvaluationCheck timeToolCheck(AgentRunReport report) {
		boolean applicable = "TIME_QUERY".equals(report.intent()) || containsAny(report.message(), "几点", "时间", "北京时间",
				"当前时间", "现在");
		boolean passed = !applicable || hasTool(report, "getCurrentTime");
		return new EvaluationCheck("时间工具", applicable, passed,
				!applicable ? "本轮不是时间问题。"
						: passed ? "时间问题已调用 getCurrentTime。" : "时间问题没有调用 getCurrentTime。");
	}

	private EvaluationCheck saveResourceCheck(AgentRunReport report) {
		boolean applicable = containsAny(report.message(), "保存", "记录", "沉淀", "新增资源", "学习资源", "写入 mcp", "写入mcp",
				"更新资源", "修改资源");
		boolean passed = !applicable || report.pendingMcpWrite() || startsWith(report.mcpMode(), "MCP_WRITE")
				|| "REAL_MCP".equals(report.mcpMode()) || hasTool(report, "createMcpLearningResource")
				|| hasTool(report, "updateMcpLearningResource");
		return new EvaluationCheck("MCP 写入意图", applicable, passed,
				!applicable ? "本轮没有明确保存或更新资源诉求。"
						: passed ? "保存诉求已产生 MCP 写入或待确认写入。" : "保存诉求没有触发 MCP 写入工具。");
	}

	private EvaluationCheck projectKnowledgeCheck(AgentRunReport report) {
		boolean applicable = containsAny(report.message(), "当前项目", "项目", "源码", "readme", "调用链", "实现", "代码",
				"minimax-chat", "mcp", "agent", "graph", "rag", "tool", "skill");
		boolean passed = !applicable || hasTool(report, "searchLearningDocs") || hasTool(report, "searchMcpLearningResources")
				|| "REAL_MCP".equals(report.mcpMode()) || "MOCK_MCP".equals(report.mcpMode());
		return new EvaluationCheck("项目知识检索", applicable, passed,
				!applicable ? "本轮不是项目知识类问题。"
						: passed ? "项目知识类问题使用了 RAG 或 MCP 检索。" : "项目知识类问题未看到 RAG/MCP 检索。");
	}

	private synchronized AgentEvaluationResult append(AgentEvaluationResult evaluation) {
		List<AgentEvaluationResult> evaluations = readAll();
		evaluations.add(evaluation);
		if (evaluations.size() > this.maxEvaluations) {
			evaluations = new ArrayList<>(evaluations.subList(evaluations.size() - this.maxEvaluations,
					evaluations.size()));
		}
		writeAll(evaluations);
		return evaluation;
	}

	private List<AgentEvaluationResult> readAll() {
		if (!Files.exists(this.evaluationFile)) {
			return new ArrayList<>();
		}
		try {
			return new ArrayList<>(this.objectMapper.readValue(this.evaluationFile.toFile(),
					new TypeReference<List<AgentEvaluationResult>>() {
					}));
		}
		catch (IOException ex) {
			return new ArrayList<>();
		}
	}

	private void writeAll(List<AgentEvaluationResult> evaluations) {
		try {
			Path parent = this.evaluationFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.evaluationFile.toFile(), evaluations);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write agent evaluation file: " + this.evaluationFile, ex);
		}
	}

	private boolean hasTool(AgentRunReport report, String toolName) {
		return report.toolCalls() != null && report.toolCalls().stream().anyMatch(call -> toolName.equals(toolName(call)));
	}

	private String toolName(Object call) {
		JsonNode node = this.objectMapper.valueToTree(call);
		return node.hasNonNull("name") ? node.get("name").asText() : "";
	}

	private boolean sameJson(Object left, Object right) {
		return this.objectMapper.valueToTree(left).equals(this.objectMapper.valueToTree(right));
	}

	private boolean containsAny(String text, String... keywords) {
		String safeText = text == null ? "" : text.toLowerCase(Locale.ROOT);
		for (String keyword : keywords) {
			if (safeText.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private boolean startsWith(String text, String prefix) {
		return text != null && text.startsWith(prefix);
	}

	private String level(int score, int maxScore) {
		if (maxScore == 0) {
			return "UNKNOWN";
		}
		if (score == maxScore) {
			return "PASS";
		}
		if (score >= Math.max(1, maxScore - 1)) {
			return "WARN";
		}
		return "FAIL";
	}

	private String newId() {
		return "eval-" + UUID.randomUUID();
	}

}
