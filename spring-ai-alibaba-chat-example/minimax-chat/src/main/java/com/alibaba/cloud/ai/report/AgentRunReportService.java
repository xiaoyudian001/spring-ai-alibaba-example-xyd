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

package com.alibaba.cloud.ai.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.alibaba.cloud.ai.customer.CustomerServiceGraphResult;
import com.alibaba.cloud.ai.customer.CustomerServiceResult;
import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.official.OfficialLearningAgentResult;
import com.alibaba.cloud.ai.officialgraph.OfficialLearningGraphResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Persists compact execution reports for later review and evaluation.
 */
@Service
public class AgentRunReportService {

	private static final int DEFAULT_LIMIT = 20;

	private static final int SUMMARY_MAX_LENGTH = 220;

	private final ObjectMapper objectMapper;

	private final Path reportFile;

	private final int maxReports;

	public AgentRunReportService(ObjectMapper objectMapper,
			@Value("${minimax.report.file:report/agent-runs.json}") String reportFile,
			@Value("${minimax.report.max-reports:200}") int maxReports) {
		this.objectMapper = objectMapper;
		this.reportFile = Path.of(reportFile);
		this.maxReports = Math.max(20, maxReports);
	}

	public AgentRunReport saveOfficialAgent(String userId, String message, int historySize,
			OfficialLearningAgentResult result) {
		return append(new AgentRunReport(newId(), Instant.now(), normalizeUserId(userId), "OFFICIAL_REACT_AGENT",
				normalizeText(message), historySize, intentName(result.intent()), summarize(result.content()),
				fullAnswer(result.content()), mcpMode(result.mcpDebugInfo()), hasPendingWrite(result.mcpDebugInfo()),
				safeSize(result.toolCalls()), safeSize(result.agentSteps()), 0, result.memoryBefore(),
				result.memoryAfter(), result.agentSteps(), List.of(), result.toolCalls(), result.mcpDebugInfo()));
	}

	public AgentRunReport saveOfficialGraph(String userId, String message, int historySize,
			OfficialLearningGraphResult result) {
		return append(new AgentRunReport(newId(), Instant.now(), normalizeUserId(userId), "OFFICIAL_STATE_GRAPH",
				normalizeText(message), historySize, intentName(result.intent()), summarize(result.content()),
				fullAnswer(result.content()), mcpMode(result.mcpDebugInfo()), hasPendingWrite(result.mcpDebugInfo()),
				safeSize(result.toolCalls()), 0, safeSize(result.graphSteps()), result.memoryBefore(),
				result.memoryAfter(), List.of(), result.graphSteps(), result.toolCalls(), result.mcpDebugInfo()));
	}

	/**
	 * 保存智能客服助手执行报告，用于 Evaluation Dashboard 和后续 AI Judge 复盘。
	 * @param userId 用户唯一标识
	 * @param message 用户原始输入
	 * @param historySize 历史消息数量
	 * @param result 智能客服助手响应结果
	 * @return 已持久化的执行报告
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public AgentRunReport saveCustomerService(String userId, String message, int historySize,
			CustomerServiceResult result) {
		return append(new AgentRunReport(newId(), Instant.now(), normalizeUserId(userId), "CUSTOMER_SERVICE_AGENT",
				normalizeText(message), historySize, intentName(result.intent()), summarize(result.content()),
				fullAnswer(result.content()), mcpMode(result.mcpDebugInfo()), hasPendingWrite(result.mcpDebugInfo()),
				safeSize(result.toolCalls()), safeSize(result.multiAgentSteps()), safeSize(result.workflowSteps()),
				result.memoryBefore(), result.memoryAfter(), result.multiAgentSteps(), result.workflowSteps(),
				result.toolCalls(), result.mcpDebugInfo()));
	}

	/**
	 * 保存智能客服官方 StateGraph 执行报告，用于观察真实客服 Graph 节点和工具调用效果。
	 * @param userId 用户唯一标识
	 * @param message 用户原始输入
	 * @param historySize 历史消息数量
	 * @param result 智能客服官方 StateGraph 响应结果
	 * @return 已持久化的执行报告
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public AgentRunReport saveCustomerServiceGraph(String userId, String message, int historySize,
			CustomerServiceGraphResult result) {
		return append(new AgentRunReport(newId(), Instant.now(), normalizeUserId(userId), "CUSTOMER_SERVICE_GRAPH",
				normalizeText(message), historySize, intentName(result.intent()), summarize(result.content()),
				fullAnswer(result.content()), mcpMode(result.mcpDebugInfo()), hasPendingWrite(result.mcpDebugInfo()),
				safeSize(result.toolCalls()), safeSize(result.agentSteps()), safeSize(result.graphSteps()),
				result.memoryBefore(), result.memoryAfter(), result.agentSteps(), result.graphSteps(),
				result.toolCalls(), result.mcpDebugInfo()));
	}

	public synchronized List<AgentRunReport> latest(int limit) {
		List<AgentRunReport> reports = readAll();
		Collections.reverse(reports);
		int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, this.maxReports);
		return List.copyOf(reports.subList(0, Math.min(safeLimit, reports.size())));
	}

	public synchronized int clear() {
		int count = readAll().size();
		writeAll(List.of());
		return count;
	}

	private synchronized AgentRunReport append(AgentRunReport report) {
		List<AgentRunReport> reports = readAll();
		reports.add(report);
		if (reports.size() > this.maxReports) {
			reports = new ArrayList<>(reports.subList(reports.size() - this.maxReports, reports.size()));
		}
		writeAll(reports);
		return report;
	}

	private List<AgentRunReport> readAll() {
		if (!Files.exists(this.reportFile)) {
			return new ArrayList<>();
		}
		try {
			return new ArrayList<>(this.objectMapper.readValue(this.reportFile.toFile(),
					new TypeReference<List<AgentRunReport>>() {
					}));
		}
		catch (IOException ex) {
			return new ArrayList<>();
		}
	}

	private void writeAll(List<AgentRunReport> reports) {
		try {
			Path parent = this.reportFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.reportFile.toFile(), reports);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write agent run report file: " + this.reportFile, ex);
		}
	}

	private String newId() {
		return "run-" + UUID.randomUUID();
	}

	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

	private String normalizeText(String text) {
		return text == null ? "" : text.trim();
	}

	private String summarize(String content) {
		String text = normalizeText(content).replaceAll("<think>[\\s\\S]*?</think>", "").replaceAll("\\s+", " ");
		if (text.length() <= SUMMARY_MAX_LENGTH) {
			return text;
		}
		return text.substring(0, SUMMARY_MAX_LENGTH) + "...";
	}

	private String fullAnswer(String content) {
		return normalizeText(content).replaceAll("<think>[\\s\\S]*?</think>", "").trim();
	}

	private String intentName(Object intent) {
		return intent == null ? "UNKNOWN" : String.valueOf(intent);
	}

	private String mcpMode(McpDebugInfo mcpDebugInfo) {
		return mcpDebugInfo == null ? "NOT_USED" : mcpDebugInfo.mode();
	}

	private boolean hasPendingWrite(McpDebugInfo mcpDebugInfo) {
		return mcpDebugInfo != null && mcpDebugInfo.pendingWrite() != null;
	}

	private int safeSize(List<?> items) {
		return items == null ? 0 : items.size();
	}

}
