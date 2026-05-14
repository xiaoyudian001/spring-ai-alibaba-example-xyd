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

package com.alibaba.cloud.ai.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.cloud.ai.agent.LearningAgentResult;
import com.alibaba.cloud.ai.agent.LearningAgentService;
import com.alibaba.cloud.ai.agent.LearningAgentService.LearningAgentMessage;
import com.alibaba.cloud.ai.agent.LearningStreamEvent;
import com.alibaba.cloud.ai.evaluation.AgentEvaluationResult;
import com.alibaba.cloud.ai.evaluation.AgentEvaluationService;
import com.alibaba.cloud.ai.judge.AgentJudgeResult;
import com.alibaba.cloud.ai.judge.AgentJudgeService;
import com.alibaba.cloud.ai.mcp.LearningMcpService;
import com.alibaba.cloud.ai.mcp.LearningMcpService.LearningMcpStatus;
import com.alibaba.cloud.ai.mcp.LearningMcpService.McpWriteResult;
import com.alibaba.cloud.ai.mcp.PendingMcpWrite;
import com.alibaba.cloud.ai.memory.LearningMemory;
import com.alibaba.cloud.ai.memory.LearningMemoryService;
import com.alibaba.cloud.ai.official.OfficialLearningAgentResult;
import com.alibaba.cloud.ai.official.OfficialLearningAgentService;
import com.alibaba.cloud.ai.officialgraph.OfficialLearningGraphResult;
import com.alibaba.cloud.ai.officialgraph.OfficialLearningGraphService;
import com.alibaba.cloud.ai.report.AgentRunReport;
import com.alibaba.cloud.ai.report.AgentRunReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * MiniMax chat examples.
 *
 * @author wangx
 */
@RestController
@RequestMapping("/minimax/chat-client")
public class MiniMaxChatClientController {

	private static final String DEFAULT_PROMPT = "你好，介绍下你自己吧。";

	private final ChatClient chatClient;

	private final LearningAgentService learningAgentService;

	private final LearningMemoryService learningMemoryService;

	private final LearningMcpService learningMcpService;

	private final OfficialLearningAgentService officialLearningAgentService;

	private final OfficialLearningGraphService officialLearningGraphService;

	private final AgentRunReportService agentRunReportService;

	private final AgentEvaluationService agentEvaluationService;

	private final AgentJudgeService agentJudgeService;

	public MiniMaxChatClientController(ChatModel chatModel, LearningAgentService learningAgentService,
			LearningMemoryService learningMemoryService, LearningMcpService learningMcpService,
			OfficialLearningAgentService officialLearningAgentService,
			OfficialLearningGraphService officialLearningGraphService, AgentRunReportService agentRunReportService,
			AgentEvaluationService agentEvaluationService, AgentJudgeService agentJudgeService) {
		this.learningAgentService = learningAgentService;
		this.learningMemoryService = learningMemoryService;
		this.learningMcpService = learningMcpService;
		this.officialLearningAgentService = officialLearningAgentService;
		this.officialLearningGraphService = officialLearningGraphService;
		this.agentRunReportService = agentRunReportService;
		this.agentEvaluationService = agentEvaluationService;
		this.agentJudgeService = agentJudgeService;
		this.chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(new SimpleLoggerAdvisor())
				.defaultOptions(defaultOptions())
				.build();
	}

	@GetMapping("/simple/chat")
	public String simpleChat(@RequestParam(value = "message", defaultValue = DEFAULT_PROMPT) String message) {
		return this.chatClient.prompt(message).call().content();
	}

	@GetMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> streamChat(@RequestParam(value = "message", defaultValue = DEFAULT_PROMPT) String message,
			HttpServletResponse response) {
		response.setCharacterEncoding("UTF-8");
		return this.chatClient.prompt(message).stream().content();
	}

	@PostMapping(value = "/conversation/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public LearningAgentResult conversationChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		List<LearningAgentMessage> history = toAgentHistory(request);
		LearningAgentResult result = this.learningAgentService.chat(userId, message, history);
		saveEvaluation(this.agentRunReportService.saveHandwritten(userId, message, history.size(), result));
		return result;
	}

	@PostMapping(value = "/conversation/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<LearningStreamEvent>> conversationStream(@RequestBody ChatRequest request,
			HttpServletResponse response) {
		response.setCharacterEncoding("UTF-8");
		String userId = extractUserId(request);
		String message = extractMessage(request);
		List<LearningAgentMessage> history = toAgentHistory(request);
		AtomicReference<LearningStreamEvent> debugEvent = new AtomicReference<>();
		StringBuilder content = new StringBuilder();
		return this.learningAgentService.streamEvents(userId, message, history)
				.doOnNext(event -> {
					if ("debug".equals(event.type())) {
						debugEvent.set(event);
					}
					else if ("message".equals(event.type())) {
						content.append(event.content() == null ? "" : event.content());
					}
					else if ("done".equals(event.type())) {
						saveEvaluation(this.agentRunReportService.saveStream(userId, message, history.size(),
								content.toString(), debugEvent.get(), event));
					}
				})
				.map(event -> ServerSentEvent.builder(event).event(event.type()).build());
	}

	@PostMapping(value = "/official-agent/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public OfficialLearningAgentResult officialAgentChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		List<LearningAgentMessage> history = toAgentHistory(request);
		OfficialLearningAgentResult result = this.officialLearningAgentService.chat(userId, message);
		saveEvaluation(this.agentRunReportService.saveOfficialAgent(userId, message, history.size(), result));
		return result;
	}

	@PostMapping(value = "/official-graph/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public OfficialLearningGraphResult officialGraphChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		List<LearningAgentMessage> history = toAgentHistory(request);
		OfficialLearningGraphResult result = this.officialLearningGraphService.chat(userId, message);
		saveEvaluation(this.agentRunReportService.saveOfficialGraph(userId, message, history.size(), result));
		return result;
	}

	@GetMapping("/memory")
	public LearningMemory getMemory(@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.learningMemoryService.read(userId);
	}

	@DeleteMapping("/memory")
	public LearningMemory clearMemory(@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.learningMemoryService.clear(userId);
	}

	@GetMapping("/mcp/status")
	public LearningMcpStatus mcpStatus() {
		return this.learningMcpService.status();
	}

	@GetMapping("/mcp/write/pending")
	public PendingMcpWrite pendingMcpWrite(@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.learningMcpService.pendingWrite(userId);
	}

	@PostMapping(value = "/mcp/write/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
	public McpWriteResult confirmMcpWrite(@RequestBody ConfirmMcpWriteRequest request) {
		return this.learningMcpService.confirmPendingWrite(extractConfirmUserId(request));
	}

	@DeleteMapping("/mcp/write/pending")
	public McpWriteResult cancelMcpWrite(@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.learningMcpService.cancelPendingWrite(userId);
	}

	@GetMapping("/report/runs")
	public List<AgentRunReport> agentRunReports(@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.agentRunReportService.latest(limit);
	}

	@DeleteMapping("/report/runs")
	public ClearReportResponse clearAgentRunReports() {
		return new ClearReportResponse(this.agentRunReportService.clear());
	}

	@GetMapping("/evaluation/runs")
	public List<AgentEvaluationResult> agentEvaluations(@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.agentEvaluationService.latest(limit);
	}

	@DeleteMapping("/evaluation/runs")
	public ClearReportResponse clearAgentEvaluations() {
		return new ClearReportResponse(this.agentEvaluationService.clear());
	}

	@PostMapping("/judge/latest")
	public AgentJudgeResult judgeLatest() {
		return this.agentJudgeService.judgeLatest();
	}

	@GetMapping("/judge/runs")
	public List<AgentJudgeResult> agentJudges(@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.agentJudgeService.latest(limit);
	}

	@DeleteMapping("/judge/runs")
	public ClearReportResponse clearAgentJudges() {
		return new ClearReportResponse(this.agentJudgeService.clear());
	}

	private String extractUserId(ChatRequest request) {
		if (request == null || request.userId() == null || request.userId().isBlank()) {
			return "default-user";
		}
		return request.userId().trim();
	}

	private String extractMessage(ChatRequest request) {
		if (request == null || request.message() == null || request.message().isBlank()) {
			return DEFAULT_PROMPT;
		}
		return request.message();
	}

	private List<LearningAgentMessage> toAgentHistory(ChatRequest request) {
		if (request == null || request.history() == null || request.history().isEmpty()) {
			return List.of();
		}
		List<LearningAgentMessage> messages = new ArrayList<>();
		for (ChatMessage item : request.history()) {
			messages.add(new LearningAgentMessage(item.role(), item.content()));
		}
		return messages;
	}

	private OpenAiChatOptions defaultOptions() {
		return OpenAiChatOptions.builder()
				.model("MiniMax-M2.7")
				.temperature(0.7)
				.build();
	}

	private void saveEvaluation(AgentRunReport report) {
		this.agentEvaluationService.evaluateAndSave(report);
	}

	public record ChatRequest(String userId, String message, List<ChatMessage> history) {
	}

	public record ChatMessage(String role, String content) {
	}

	private String extractConfirmUserId(ConfirmMcpWriteRequest request) {
		if (request == null || request.userId() == null || request.userId().isBlank()) {
			return "default-user";
		}
		return request.userId().trim();
	}

	public record ConfirmMcpWriteRequest(String userId) {
	}

	public record ClearReportResponse(int deleted) {
	}

}
