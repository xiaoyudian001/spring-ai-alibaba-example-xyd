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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.customer.ChannelType;
import com.alibaba.cloud.ai.customer.CustomerConversationMessage;
import com.alibaba.cloud.ai.customer.CustomerKnowledgeDocument;
import com.alibaba.cloud.ai.customer.CustomerKnowledgeUpsertRequest;
import com.alibaba.cloud.ai.customer.CustomerMemory;
import com.alibaba.cloud.ai.customer.CustomerMemoryService;
import com.alibaba.cloud.ai.customer.CustomerMcpService;
import com.alibaba.cloud.ai.customer.CustomerMcpService.CustomerMcpStatus;
import com.alibaba.cloud.ai.customer.CustomerPolicyRagService;
import com.alibaba.cloud.ai.customer.CustomerPolicySearchResult;
import com.alibaba.cloud.ai.customer.CustomerServiceAgentService;
import com.alibaba.cloud.ai.customer.CustomerServiceGraphResult;
import com.alibaba.cloud.ai.customer.CustomerServiceGraphService;
import com.alibaba.cloud.ai.customer.CustomerServiceMultiAgentResult;
import com.alibaba.cloud.ai.customer.CustomerServiceMultiAgentService;
import com.alibaba.cloud.ai.customer.CustomerServiceResult;
import com.alibaba.cloud.ai.evaluation.AgentEvaluationResult;
import com.alibaba.cloud.ai.evaluation.AgentEvaluationService;
import com.alibaba.cloud.ai.judge.AgentJudgeResult;
import com.alibaba.cloud.ai.judge.AgentJudgeService;
import com.alibaba.cloud.ai.report.AgentRunReport;
import com.alibaba.cloud.ai.report.AgentRunReportService;
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

	private final CustomerServiceAgentService customerServiceAgentService;

	private final CustomerServiceGraphService customerServiceGraphService;

	private final CustomerServiceMultiAgentService customerServiceMultiAgentService;

	private final CustomerMemoryService customerMemoryService;

	private final CustomerMcpService customerMcpService;

	private final CustomerPolicyRagService customerPolicyRagService;

	private final AgentRunReportService agentRunReportService;

	private final AgentEvaluationService agentEvaluationService;

	private final AgentJudgeService agentJudgeService;

	public MiniMaxChatClientController(CustomerServiceAgentService customerServiceAgentService,
			CustomerServiceGraphService customerServiceGraphService,
			CustomerServiceMultiAgentService customerServiceMultiAgentService,
			CustomerMemoryService customerMemoryService, CustomerMcpService customerMcpService,
			CustomerPolicyRagService customerPolicyRagService,
			AgentRunReportService agentRunReportService,
			AgentEvaluationService agentEvaluationService, AgentJudgeService agentJudgeService) {
		this.customerServiceAgentService = customerServiceAgentService;
		this.customerServiceGraphService = customerServiceGraphService;
		this.customerServiceMultiAgentService = customerServiceMultiAgentService;
		this.customerMemoryService = customerMemoryService;
		this.customerMcpService = customerMcpService;
		this.customerPolicyRagService = customerPolicyRagService;
		this.agentRunReportService = agentRunReportService;
		this.agentEvaluationService = agentEvaluationService;
		this.agentJudgeService = agentJudgeService;
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
	 * 执行智能客服官方 Multi-Agent 对话，使用 SequentialAgent 串行协作多个专业客服子 Agent。
	 * @param request 前端聊天请求
	 * @return 智能客服官方 Multi-Agent 响应结果
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	@PostMapping(value = "/customer-service/multi-agent/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CustomerServiceMultiAgentResult customerServiceMultiAgentChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		CustomerServiceMultiAgentResult result = this.customerServiceMultiAgentService.chat(userId,
				extractChannel(request), message, toCustomerHistory(request));
		saveEvaluation(this.agentRunReportService.saveCustomerServiceMultiAgent(userId, message, historySize(request),
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

	/**
	 * 查询智能客服知识库主题覆盖情况。
	 * @return 知识库主题集合
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	@GetMapping("/customer-service/rag/topics")
	public Set<String> customerRagTopics() {
		return this.customerPolicyRagService.topics();
	}

	/**
	 * 查询智能客服 RAG 知识库全部文档，用于页面知识管理和召回调试。
	 * @return 客服知识文档列表
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	@GetMapping("/customer-service/rag/documents")
	public List<CustomerKnowledgeDocument> customerRagDocuments() {
		return this.customerPolicyRagService.documents();
	}

	/**
	 * 新增或更新智能客服 RAG 自定义知识，并写入 JSON 文件。
	 * @param request 知识新增或更新请求
	 * @return 保存后的客服知识文档
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	@PostMapping(value = "/customer-service/rag/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CustomerKnowledgeDocument upsertCustomerRagDocument(@RequestBody CustomerKnowledgeUpsertRequest request) {
		return this.customerPolicyRagService.upsertCustomDocument(request);
	}

	/**
	 * 删除智能客服 RAG 自定义知识；内置知识不会被删除。
	 * @param id 文档唯一标识
	 * @return 删除结果
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	@DeleteMapping("/customer-service/rag/documents")
	public CustomerKnowledgeDeleteResponse deleteCustomerRagDocument(@RequestParam("id") String id) {
		return new CustomerKnowledgeDeleteResponse(id, this.customerPolicyRagService.deleteCustomDocument(id));
	}

	/**
	 * 评估智能客服 RAG 召回率，便于对比本地检索和真实向量库检索效果。
	 * @param query 检索问题
	 * @param expectedTopics 期望主题，逗号分隔
	 * @param limit 返回结果数量
	 * @return 客服 RAG 召回评估结果
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	@GetMapping("/customer-service/rag/evaluate")
	public CustomerPolicySearchResult customerRagEvaluate(
			@RequestParam(value = "query", defaultValue = "超过 7 天还能退吗？") String query,
			@RequestParam(value = "expectedTopics", defaultValue = "refund") String expectedTopics,
			@RequestParam(value = "limit", defaultValue = "5") Integer limit) {
		return this.customerPolicyRagService.searchWithMetrics(query, limit, splitTopics(expectedTopics));
	}

	/**
	 * 查看指定用户的智能客服长期记忆，用于页面直接验证客服 Memory 是否按用户隔离。
	 * @param userId 用户唯一标识
	 * @return 智能客服长期记忆
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	@GetMapping("/customer-service/memory")
	public CustomerMemory getCustomerMemory(
			@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.customerMemoryService.read(userId);
	}

	/**
	 * 清空指定用户的智能客服长期记忆，并写回客服 Memory JSON 文件。
	 * @param userId 用户唯一标识
	 * @return 重置后的智能客服长期记忆
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	@DeleteMapping("/customer-service/memory")
	public CustomerMemory clearCustomerMemory(
			@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.customerMemoryService.clear(userId);
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
			return "你好，请问有什么可以帮你？";
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

	/**
	 * 拆分前端传入的 RAG 期望主题，支持中英文逗号和空白分隔。
	 * @param expectedTopics 期望主题文本
	 * @return 期望主题集合
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private Set<String> splitTopics(String expectedTopics) {
		if (expectedTopics == null || expectedTopics.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(expectedTopics.split("[,，\\s]+"))
				.map(String::trim)
				.filter(text -> !text.isBlank())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void saveEvaluation(AgentRunReport report) {
		this.agentEvaluationService.evaluateAndSave(report);
	}

	public record ChatRequest(String userId, String message, List<ChatMessage> history, String channel) {
	}

	public record ChatMessage(String role, String content) {
	}

	public record ClearReportResponse(int deleted) {
	}

	/**
	 * 智能客服知识删除结果，用于页面判断自定义知识是否删除成功。
	 *
	 * @param id 文档唯一标识
	 * @param deleted 是否删除成功
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	public record CustomerKnowledgeDeleteResponse(String id, boolean deleted) {
	}

}
