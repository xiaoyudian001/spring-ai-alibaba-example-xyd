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
		checks.add(customerFactToolCheck(report));
		checks.add(customerPolicyKnowledgeCheck(report));
		checks.add(customerRiskCheck(report));
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
				passed ? "Memory 在本轮调用后发生变化。" : "Memory 未变化，可能是问题未触发客服状态更新。");
	}

	private EvaluationCheck customerFactToolCheck(AgentRunReport report) {
		boolean applicable = containsAny(report.message(), "p-", "o-", "商品", "订单", "物流", "快递", "便宜", "优惠",
				"退款", "售后", "进度");
		boolean passed = !applicable || hasAnyTool(report, "getProductInfo", "getOrderInfo", "getLogisticsInfo",
				"getPricePolicy", "getRefundEligibility", "getAfterSaleStatus");
		return new EvaluationCheck("客服事实工具", applicable, passed,
				!applicable ? "本轮没有明显实时事实查询诉求。"
						: passed ? "客服事实类问题调用了商品、订单、物流、价格或售后工具。" : "客服事实类问题未看到必要工具调用。");
	}

	private EvaluationCheck customerPolicyKnowledgeCheck(AgentRunReport report) {
		boolean applicable = containsAny(report.message(), "政策", "规则", "话术", "怎么回", "投诉", "安抚", "质量",
				"瑕疵", "改地址", "发票", "召回率", "知识库");
		boolean passed = !applicable || hasAnyTool(report, "searchCustomerPolicy", "evaluateCustomerPolicyRecall",
				"listCustomerSkills", "readCustomerSkill");
		return new EvaluationCheck("客服知识检索", applicable, passed,
				!applicable ? "本轮没有明显政策、话术或知识库诉求。"
						: passed ? "客服知识类问题使用了 RAG 或 Skill。" : "客服知识类问题未看到 RAG/Skill 调用。");
	}

	private EvaluationCheck customerRiskCheck(AgentRunReport report) {
		boolean applicable = containsAny(report.message(), "投诉", "赔偿", "直接退款", "转人工", "人工", "举报", "差评");
		boolean passed = !applicable || hasAnyTool(report, "createCustomerTicket", "requestHumanHandoff")
				|| containsAny(report.answerContent(), "人工", "工单", "升级", "记录", "不能直接", "需确认");
		return new EvaluationCheck("客服风险处理", applicable, passed,
				!applicable ? "本轮不是高风险客服场景。"
						: passed ? "高风险客服场景包含工单、人工接管或谨慎处理说明。" : "高风险客服场景缺少工单、人工接管或风控说明。");
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

	private boolean hasAnyTool(AgentRunReport report, String... toolNames) {
		for (String toolName : toolNames) {
			if (hasTool(report, toolName)) {
				return true;
			}
		}
		return false;
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
