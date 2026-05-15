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

package com.alibaba.cloud.ai.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.alibaba.cloud.ai.report.AgentRunReport;
import com.alibaba.cloud.ai.report.AgentRunReportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calls the model manually to judge the latest agent run quality.
 */
@Service
public class AgentJudgeService {

	private static final int DEFAULT_LIMIT = 20;

	private final ObjectMapper objectMapper;

	private final AgentRunReportService reportService;

	private final ChatClient judgeClient;

	private final Path judgeFile;

	private final int  maxJudges;

	public AgentJudgeService(ObjectMapper objectMapper, AgentRunReportService reportService, ChatModel chatModel,
			@Value("${minimax.judge.file:report/agent-judges.json}") String judgeFile,
			@Value("${minimax.judge.max-judges:200}") int maxJudges) {
		this.objectMapper = objectMapper;
		this.reportService = reportService;
		this.judgeFile = Path.of(judgeFile);
		this.maxJudges = Math.max(20, maxJudges);
		this.judgeClient = ChatClient.builder(chatModel)
				.defaultAdvisors(new SimpleLoggerAdvisor())
				.defaultOptions(OpenAiChatOptions.builder().model("MiniMax-M2.7").temperature(0.2).build())
				.build();
	}

	public AgentJudgeResult judgeLatest() {
		List<AgentRunReport> reports = this.reportService.latest(1);
		if (reports.isEmpty()) {
			return append(noReportResult());
		}
		return judgeAndSave(reports.get(0));
	}

	public synchronized List<AgentJudgeResult> latest(int limit) {
		List<AgentJudgeResult> judges = readAll();
		Collections.reverse(judges);
		int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, this.maxJudges);
		return List.copyOf(judges.subList(0, Math.min(safeLimit, judges.size())));
	}

	public synchronized int clear() {
		int count = readAll().size();
		writeAll(List.of());
		return count;
	}

	private AgentJudgeResult judgeAndSave(AgentRunReport report) {
		String rawResponse = "";
		try {
			rawResponse = this.judgeClient.prompt()
					.system(systemPrompt())
					.user(userPrompt(report))
					.options(OpenAiChatOptions.builder().model("MiniMax-M2.7").temperature(0.2).build())
					.call()
					.content();
			return append(toResult(report, rawResponse, "SUCCESS"));
		}
		catch (Exception ex) {
			return append(new AgentJudgeResult(newId(), report.id(), Instant.now(), report.userId(),
					report.chainMode(), report.message(), 0, 0, 0, 0, 0, "AI 评审调用失败：" + ex.getMessage(),
					"先确认 MiniMax API 配置和网络可用，再重新点击 AI 评审。", "FAILED", rawResponse));
		}
	}

	private String systemPrompt() {
		return """
				你是一个严格但务实的 Agent 质量评审员。
				请只输出 JSON，不要输出 Markdown、解释文字或代码块。
				你需要根据 AgentRunReport 判断这轮 Agent 回答质量。
				评分范围为 0 到 10，分数必须是整数。
				请关注：
				1. relevanceScore：回答是否贴合用户问题。
				2. helpfulnessScore：回答是否有帮助、可执行。
				3. clarityScore：表达是否清晰。
				4. groundingScore：是否合理利用 Tool、RAG、MCP、Memory、Graph 等上下文。
				5. riskNotes：潜在问题、幻觉风险或工具使用不足。
				6. improvementAdvice：如何改进 Agent。
				JSON 字段必须包含：
				relevanceScore, helpfulnessScore, clarityScore, groundingScore, riskNotes, improvementAdvice
				""";
	}

	private String userPrompt(AgentRunReport report) throws IOException {
		return """
				请评审下面这次 Agent 执行报告。
				注意：answerSummary 只是列表摘要，可能被截断；请优先根据 answerContent 判断回答质量。

				AgentRunReport:
				%s
				""".formatted(this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
	}

	private AgentJudgeResult toResult(AgentRunReport report, String rawResponse, String status) throws IOException {
		JsonNode json = parseJson(rawResponse);
		int relevanceScore = score(json, "relevanceScore");
		int helpfulnessScore = score(json, "helpfulnessScore");
		int clarityScore = score(json, "clarityScore");
		int groundingScore = score(json, "groundingScore");
		int overallScore = Math.round((relevanceScore + helpfulnessScore + clarityScore + groundingScore) / 4.0f);
		return new AgentJudgeResult(newId(), report.id(), Instant.now(), report.userId(), report.chainMode(),
				report.message(), relevanceScore, helpfulnessScore, clarityScore, groundingScore, overallScore,
				text(json, "riskNotes", "暂无风险说明。"), text(json, "improvementAdvice", "暂无改进建议。"), status,
				rawResponse);
	}

	private JsonNode parseJson(String rawResponse) throws IOException {
		String text = rawResponse == null ? "" : rawResponse.trim();
		if (text.startsWith("```")) {
			text = text.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "").replaceFirst("\\s*```$", "").trim();
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start >= 0 && end >= start) {
			text = text.substring(start, end + 1);
		}
		return this.objectMapper.readTree(text);
	}

	private int score(JsonNode json, String field) {
		int value = json.hasNonNull(field) ? json.get(field).asInt(0) : 0;
		return Math.max(0, Math.min(10, value));
	}

	private String text(JsonNode json, String field, String defaultValue) {
		String value = json.hasNonNull(field) ? json.get(field).asText() : "";
		return value.isBlank() ? defaultValue : value;
	}

	private AgentJudgeResult noReportResult() {
		return new AgentJudgeResult(newId(), "", Instant.now(), "default-user", "NONE", "", 0, 0, 0, 0, 0,
				"当前没有可评审的 AgentRunReport。", "先发送一轮对话，生成 report/agent-runs.json 后再点击 AI 评审。",
				"NO_REPORT", "");
	}

	private synchronized AgentJudgeResult append(AgentJudgeResult judge) {
		List<AgentJudgeResult> judges = readAll();
		judges.add(judge);
		if (judges.size() > this.maxJudges) {
			judges = new ArrayList<>(judges.subList(judges.size() - this.maxJudges, judges.size()));
		}
		writeAll(judges);
		return judge;
	}

	private List<AgentJudgeResult> readAll() {
		if (!Files.exists(this.judgeFile)) {
			return new ArrayList<>();
		}
		try {
			return new ArrayList<>(this.objectMapper.readValue(this.judgeFile.toFile(),
					new TypeReference<List<AgentJudgeResult>>() {
					}));
		}
		catch (IOException ex) {
			return new ArrayList<>();
		}
	}

	private void writeAll(List<AgentJudgeResult> judges) {
		try {
			Path parent = this.judgeFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.judgeFile.toFile(), judges);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write agent judge file: " + this.judgeFile, ex);
		}
	}

	private String newId() {
		return "judge-" + UUID.randomUUID();
	}

}
