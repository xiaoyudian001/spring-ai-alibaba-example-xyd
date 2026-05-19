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

import com.alibaba.cloud.ai.customer.ChannelType;
import com.alibaba.cloud.ai.customer.CustomerConversationMessage;
import com.alibaba.cloud.ai.customer.CustomerMcpService;
import com.alibaba.cloud.ai.customer.CustomerMcpService.CustomerMcpStatus;
import com.alibaba.cloud.ai.customer.CustomerServiceAgentService;
import com.alibaba.cloud.ai.customer.CustomerServiceGraphResult;
import com.alibaba.cloud.ai.customer.CustomerServiceGraphService;
import com.alibaba.cloud.ai.customer.CustomerServiceResult;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

	private final LearningMemoryService learningMemoryService;

	private final LearningMcpService learningMcpService;

	private final OfficialLearningAgentService officialLearningAgentService;

	private final OfficialLearningGraphService officialLearningGraphService;

	private final CustomerServiceAgentService customerServiceAgentService;

	private final CustomerServiceGraphService customerServiceGraphService;

	private final CustomerMcpService customerMcpService;

	private final AgentRunReportService agentRunReportService;

	private final AgentEvaluationService agentEvaluationService;

	private final AgentJudgeService agentJudgeService;

	public MiniMaxChatClientController(ChatModel chatModel, LearningMemoryService learningMemoryService,
			LearningMcpService learningMcpService,
			OfficialLearningAgentService officialLearningAgentService,
			OfficialLearningGraphService officialLearningGraphService, CustomerServiceAgentService customerServiceAgentService,
			CustomerServiceGraphService customerServiceGraphService, CustomerMcpService customerMcpService, AgentRunReportService agentRunReportService,
			AgentEvaluationService agentEvaluationService, AgentJudgeService agentJudgeService) {
		this.learningMemoryService = learningMemoryService;
		this.learningMcpService = learningMcpService;
		this.officialLearningAgentService = officialLearningAgentService;
		this.officialLearningGraphService = officialLearningGraphService;
		this.customerServiceAgentService = customerServiceAgentService;
		this.customerServiceGraphService = customerServiceGraphService;
		this.customerMcpService = customerMcpService;
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

	@PostMapping(value = "/official-agent/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public OfficialLearningAgentResult officialAgentChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		OfficialLearningAgentResult result = this.officialLearningAgentService.chat(userId, message);
		saveEvaluation(this.agentRunReportService.saveOfficialAgent(userId, message, historySize(request), result));
		return result;
	}

	@PostMapping(value = "/official-graph/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public OfficialLearningGraphResult officialGraphChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		OfficialLearningGraphResult result = this.officialLearningGraphService.chat(userId, message);
		saveEvaluation(this.agentRunReportService.saveOfficialGraph(userId, message, historySize(request), result));
		return result;
	}

	/**
	 * 执行智能客服助手对话，面向网页客服、闲鱼 Mock、微信 Mock 等真实客服场景。
	 * @param request 前端聊天请求
	 * @return 智能客服助手响应结果
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@PostMapping(value = "/customer-service/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CustomerServiceResult customerServiceChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		CustomerServiceResult result = this.customerServiceAgentService.chat(userId, extractChannel(request), message,
				toCustomerHistory(request));
		saveEvaluation(this.agentRunReportService.saveCustomerService(userId, message, historySize(request), result));
		return result;
	}

	/**
	 * 执行智能客服官方 StateGraph 对话，使用真实 Graph 节点编排客服处理流程。
	 * @param request 前端聊天请求
	 * @return 智能客服官方 StateGraph 响应结果
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	@PostMapping(value = "/customer-service/graph/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CustomerServiceGraphResult customerServiceGraphChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		CustomerServiceGraphResult result = this.customerServiceGraphService.chat(userId, extractChannel(request),
				message, toCustomerHistory(request));
		saveEvaluation(this.agentRunReportService.saveCustomerServiceGraph(userId, message, historySize(request),
				result));
		return result;
	}

	/**
	 * 查询智能客服 MCP 接入状态，便于确认当前是调用真实 MCP 还是 Mock 兜底。
	 * @return 智能客服 MCP 状态
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	@GetMapping("/customer-service/mcp/status")
	public CustomerMcpStatus customerMcpStatus() {
		return this.customerMcpService.status();
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

	private List<CustomerConversationMessage> toCustomerHistory(ChatRequest request) {
		if (request == null || request.history() == null || request.history().isEmpty()) {
			return List.of();
		}
		List<CustomerConversationMessage> messages = new ArrayList<>();
		for (ChatMessage item : request.history()) {
			messages.add(new CustomerConversationMessage(item.role(), item.content()));
		}
		return messages;
	}

	private int historySize(ChatRequest request) {
		return request == null || request.history() == null ? 0 : request.history().size();
	}

	/**
	 * 从请求中提取客服渠道，缺省使用 WEB，非法渠道也回退到 WEB。
	 * @param request 前端聊天请求
	 * @return 客服渠道
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private ChannelType extractChannel(ChatRequest request) {
		if (request == null || request.channel() == null || request.channel().isBlank()) {
			return ChannelType.WEB;
		}
		try {
			return ChannelType.valueOf(request.channel().trim().toUpperCase().replace('-', '_'));
		}
		catch (IllegalArgumentException ex) {
			return ChannelType.WEB;
		}
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

	public record ChatRequest(String userId, String message, List<ChatMessage> history, String channel) {
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
