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

import com.alibaba.cloud.ai.audit.OperationAuditEvent;
import com.alibaba.cloud.ai.audit.OperationAuditService;
import com.alibaba.cloud.ai.customer.ApprovalTaskService;
import com.alibaba.cloud.ai.customer.ApprovalTaskStatus;
import com.alibaba.cloud.ai.customer.ChannelType;
import com.alibaba.cloud.ai.customer.CustomerConversationMessage;
import com.alibaba.cloud.ai.customer.CustomerConversationContextService;
import com.alibaba.cloud.ai.customer.CustomerConversationContextService.CustomerConversationContextStatus;
import com.alibaba.cloud.ai.customer.CustomerConversationContextService.CustomerConversationContextView;
import com.alibaba.cloud.ai.customer.CustomerKnowledgeDocument;
import com.alibaba.cloud.ai.customer.CustomerKnowledgeUpsertRequest;
import com.alibaba.cloud.ai.customer.CustomerMemory;
import com.alibaba.cloud.ai.customer.CustomerMemoryService;
import com.alibaba.cloud.ai.customer.CustomerMcpService;
import com.alibaba.cloud.ai.customer.CustomerMcpService.CustomerMcpStatus;
import com.alibaba.cloud.ai.customer.CustomerPolicyRagService;
import com.alibaba.cloud.ai.customer.CustomerPolicyRagService.CustomerPolicyRagStatus;
import com.alibaba.cloud.ai.customer.CustomerPolicySearchResult;
import com.alibaba.cloud.ai.customer.CustomerServiceAssistantResult;
import com.alibaba.cloud.ai.customer.CustomerServiceAssistantService;
import com.alibaba.cloud.ai.customer.CustomerServiceAgentService;
import com.alibaba.cloud.ai.customer.CustomerServiceGraphResult;
import com.alibaba.cloud.ai.customer.CustomerServiceGraphService;
import com.alibaba.cloud.ai.customer.CustomerServiceMultiAgentResult;
import com.alibaba.cloud.ai.customer.CustomerServiceMultiAgentService;
import com.alibaba.cloud.ai.customer.CustomerServiceResult;
import com.alibaba.cloud.ai.customer.CustomerStorageAdminService;
import com.alibaba.cloud.ai.customer.CustomerStorageAdminService.CustomerMysqlTableOverview;
import com.alibaba.cloud.ai.customer.CustomerStorageAdminService.CustomerStorageCleanupResult;
import com.alibaba.cloud.ai.customer.CustomerStorageAdminService.CustomerStorageStatus;
import com.alibaba.cloud.ai.customer.PendingApprovalTask;
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

	private final CustomerServiceAssistantService customerServiceAssistantService;

	private final CustomerServiceGraphService customerServiceGraphService;

	private final CustomerServiceMultiAgentService customerServiceMultiAgentService;

	private final CustomerMemoryService customerMemoryService;

	private final CustomerConversationContextService customerConversationContextService;

	private final CustomerMcpService customerMcpService;

	private final CustomerPolicyRagService customerPolicyRagService;

	private final CustomerStorageAdminService customerStorageAdminService;

	private final AgentRunReportService agentRunReportService;

	private final AgentEvaluationService agentEvaluationService;

	private final AgentJudgeService agentJudgeService;

	private final ApprovalTaskService approvalTaskService;

	private final OperationAuditService operationAuditService;

	public MiniMaxChatClientController(CustomerServiceAgentService customerServiceAgentService,
			CustomerServiceAssistantService customerServiceAssistantService,
			CustomerServiceGraphService customerServiceGraphService,
			CustomerServiceMultiAgentService customerServiceMultiAgentService,
			CustomerMemoryService customerMemoryService,
			CustomerConversationContextService customerConversationContextService,
			CustomerMcpService customerMcpService, CustomerPolicyRagService customerPolicyRagService,
			CustomerStorageAdminService customerStorageAdminService, AgentRunReportService agentRunReportService,
			AgentEvaluationService agentEvaluationService, AgentJudgeService agentJudgeService,
			ApprovalTaskService approvalTaskService, OperationAuditService operationAuditService) {
		this.customerServiceAgentService = customerServiceAgentService;
		this.customerServiceAssistantService = customerServiceAssistantService;
		this.customerServiceGraphService = customerServiceGraphService;
		this.customerServiceMultiAgentService = customerServiceMultiAgentService;
		this.customerMemoryService = customerMemoryService;
		this.customerConversationContextService = customerConversationContextService;
		this.customerMcpService = customerMcpService;
		this.customerPolicyRagService = customerPolicyRagService;
		this.customerStorageAdminService = customerStorageAdminService;
		this.agentRunReportService = agentRunReportService;
		this.agentEvaluationService = agentEvaluationService;
		this.agentJudgeService = agentJudgeService;
		this.approvalTaskService = approvalTaskService;
		this.operationAuditService = operationAuditService;
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
		List<CustomerConversationMessage> history = loadConversationHistory(userId, request);
		CustomerServiceResult result = this.customerServiceAgentService.chat(userId, extractChannel(request), message,
				history);
		this.customerConversationContextService.appendTurn(userId, history, message, result.content());
		saveEvaluation(this.agentRunReportService.saveCustomerService(userId, message, history.size(), result));
		return result;
	}

	/**
	 * 执行客户无感智能客服对话，前端无需关心 ReactAgent、Graph、Multi-Agent、同步或流式等技术选项。
	 * @param request 前端聊天请求
	 * @return 统一客服响应结果
	 * @author xyd
	 * @date 2026-05-20 09:43:00
	 */
	@PostMapping(value = "/customer-service/assistant/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CustomerServiceAssistantResult customerServiceAssistantChat(@RequestBody ChatRequest request) {
		String userId = extractUserId(request);
		String message = extractMessage(request);
		List<CustomerConversationMessage> history = loadConversationHistory(userId, request);
		CustomerServiceAssistantResult result = this.customerServiceAssistantService.chat(userId,
				extractChannel(request), message, history);
		this.customerConversationContextService.appendTurn(userId, history, message, result.content());
		saveEvaluation(this.agentRunReportService.saveCustomerServiceAssistant(userId, message, history.size(), result));
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
		List<CustomerConversationMessage> history = loadConversationHistory(userId, request);
		CustomerServiceGraphResult result = this.customerServiceGraphService.chat(userId, extractChannel(request),
				message, history);
		this.customerConversationContextService.appendTurn(userId, history, message, result.content());
		saveEvaluation(this.agentRunReportService.saveCustomerServiceGraph(userId, message, history.size(), result));
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
		List<CustomerConversationMessage> history = loadConversationHistory(userId, request);
		CustomerServiceMultiAgentResult result = this.customerServiceMultiAgentService.chat(userId,
				extractChannel(request), message, history);
		this.customerConversationContextService.appendTurn(userId, history, message, result.content());
		saveEvaluation(this.agentRunReportService.saveCustomerServiceMultiAgent(userId, message, history.size(),
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
	 * 查询智能客服 RAG 运行状态，便于确认当前是否真正接入 VectorStore。
	 * @return 客服 RAG 运行状态
	 * @author xyd
	 * @date 2026-05-21 00:00:00
	 */
	@GetMapping("/customer-service/rag/status")
	public CustomerPolicyRagStatus customerRagStatus() {
		return this.customerPolicyRagService.status();
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
		CustomerKnowledgeDocument document = this.customerPolicyRagService.upsertCustomDocument(request);
		this.operationAuditService.record("dashboard", "UPSERT_RAG_DOCUMENT", document.id(), document.title(), true);
		return document;
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
		boolean deleted = this.customerPolicyRagService.deleteCustomDocument(id);
		this.operationAuditService.record("dashboard", "DELETE_RAG_DOCUMENT", id, "deleted=" + deleted, deleted);
		return new CustomerKnowledgeDeleteResponse(id, deleted);
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
	 * 查询客服 Memory 持久化后端，用于工作台确认当前是否已从 JSON 迁移到数据库。
	 * @return Memory 后端状态
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@GetMapping("/customer-service/memory/backend")
	public MemoryBackendResponse customerMemoryBackend() {
		return new MemoryBackendResponse(this.customerMemoryService.backend());
	}

	/**
	 * 查询客服短期上下文后端状态，用于确认是否已启用 Redis 保存多轮对话上下文。
	 * @return 短期上下文状态
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	@GetMapping("/customer-service/context/status")
	public CustomerConversationContextStatus customerConversationContextStatus() {
		return this.customerConversationContextService.status();
	}

	/**
	 * 查看指定用户的 Redis 短期上下文内容，便于区分短期上下文和 MySQL 长期 Memory。
	 * @param userId 用户唯一标识
	 * @return Redis 短期上下文详情
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	@GetMapping("/customer-service/context")
	public CustomerConversationContextView customerConversationContext(
			@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		return this.customerConversationContextService.view(userId);
	}

	/**
	 * 清空指定用户的 Redis 短期上下文，不影响长期 Memory。
	 * @param userId 用户唯一标识
	 * @return 清空结果
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	@DeleteMapping("/customer-service/context")
	public CustomerConversationContextClearResponse clearCustomerConversationContext(
			@RequestParam(value = "userId", defaultValue = "default-user") String userId) {
		boolean deleted = this.customerConversationContextService.clear(userId);
		this.operationAuditService.record(userId, "CLEAR_REDIS_CONTEXT", userId, "清空客服 Redis 短期上下文", true);
		return new CustomerConversationContextClearResponse(userId, deleted);
	}

	/**
	 * 查询智能客服存储健康状态，统一返回 MySQL、Redis、Memory、RAG 和 MCP 运行模式。
	 * @return 存储健康状态
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	@GetMapping("/customer-service/storage/status")
	public CustomerStorageStatus customerStorageStatus() {
		return this.customerStorageAdminService.status();
	}

	/**
	 * 查询智能客服 MySQL 核心业务表概览，用于工作台确认数据是否真实写入。
	 * @return MySQL 表概览列表
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	@GetMapping("/customer-service/storage/tables")
	public List<CustomerMysqlTableOverview> customerStorageTables() {
		return this.customerStorageAdminService.tables();
	}

	/**
	 * 清理本地联调产生的测试数据，仅删除测试用户和测试会话相关记录。
	 * @return 清理结果
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	@DeleteMapping("/customer-service/storage/test-data")
	public CustomerStorageCleanupResult clearCustomerStorageTestData() {
		CustomerStorageCleanupResult result = this.customerStorageAdminService.clearTestData();
		this.operationAuditService.record("dashboard", "CLEAR_STORAGE_TEST_DATA", "mysql",
				"清理测试数据：" + result.totalDeleted(), true);
		return result;
	}

	/**
	 * 保存工作台编辑后的客服 Memory。
	 * @param userId 用户唯一标识
	 * @param memory 客服长期记忆
	 * @return 保存后的客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@PostMapping(value = "/customer-service/memory", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CustomerMemory saveCustomerMemory(
			@RequestParam(value = "userId", defaultValue = "default-user") String userId,
			@RequestBody CustomerMemory memory) {
		CustomerMemory saved = this.customerMemoryService.save(userId, memory);
		this.operationAuditService.record(userId, "SAVE_MEMORY", userId, "可视化编辑客服 Memory", true);
		return saved;
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
		CustomerMemory memory = this.customerMemoryService.clear(userId);
		this.customerConversationContextService.clear(userId);
		this.operationAuditService.record(userId, "CLEAR_MEMORY", userId, "清空客服 Memory", true);
		return memory;
	}

	/**
	 * 查询最近的高风险待审核任务。
	 * @param limit 最大返回数量
	 * @return 待审核任务列表
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@GetMapping("/customer-service/approval/tasks")
	public List<PendingApprovalTask> approvalTasks(@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.approvalTaskService.latest(limit);
	}

	/**
	 * 更新高风险待审核任务状态。
	 * @param request 审核状态更新请求
	 * @return 更新后的审核任务
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@PostMapping(value = "/customer-service/approval/tasks/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public PendingApprovalTask updateApprovalTaskStatus(@RequestBody ApprovalTaskStatusRequest request) {
		PendingApprovalTask task = this.approvalTaskService.updateStatus(request.id(),
				ApprovalTaskStatus.valueOf(request.status()));
		this.operationAuditService.record("dashboard", "UPDATE_APPROVAL_TASK", task.id(), task.status().name(), true);
		return task;
	}

	/**
	 * 查询最近的操作审计日志。
	 * @param limit 最大返回数量
	 * @return 审计日志列表
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@GetMapping("/audit/events")
	public List<OperationAuditEvent> auditEvents(@RequestParam(value = "limit", defaultValue = "30") int limit) {
		return this.operationAuditService.latest(limit);
	}

	@GetMapping("/report/runs")
	public List<AgentRunReport> agentRunReports(@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.agentRunReportService.latest(limit);
	}

	@GetMapping("/report/runs/filter")
	public List<AgentRunReport> filterAgentRunReports(
			@RequestParam(value = "userId", required = false) String userId,
			@RequestParam(value = "intent", required = false) String intent,
			@RequestParam(value = "chainMode", required = false) String chainMode,
			@RequestParam(value = "channel", required = false) String channel,
			@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.agentRunReportService.filter(userId, intent, chainMode, channel, limit);
	}

	@DeleteMapping("/report/runs")
	public ClearReportResponse clearAgentRunReports() {
		int deleted = this.agentRunReportService.clear();
		this.operationAuditService.record("dashboard", "CLEAR_AGENT_REPORTS", "agent-runs", "deleted=" + deleted,
				true);
		return new ClearReportResponse(deleted);
	}

	@GetMapping("/evaluation/runs")
	public List<AgentEvaluationResult> agentEvaluations(@RequestParam(value = "limit", defaultValue = "20") int limit) {
		return this.agentEvaluationService.latest(limit);
	}

	@DeleteMapping("/evaluation/runs")
	public ClearReportResponse clearAgentEvaluations() {
		int deleted = this.agentEvaluationService.clear();
		this.operationAuditService.record("dashboard", "CLEAR_AGENT_EVALUATIONS", "evaluation-runs",
				"deleted=" + deleted, true);
		return new ClearReportResponse(deleted);
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
		int deleted = this.agentJudgeService.clear();
		this.operationAuditService.record("dashboard", "CLEAR_AGENT_JUDGES", "judge-runs", "deleted=" + deleted,
				true);
		return new ClearReportResponse(deleted);
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

	/**
	 * 加载本轮对话短期上下文，Redis 启用时优先从 Redis 读取，未启用时使用前端传入历史。
	 * @param userId 用户唯一标识
	 * @param request 前端聊天请求
	 * @return 本轮模型调用使用的短期上下文
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	private List<CustomerConversationMessage> loadConversationHistory(String userId, ChatRequest request) {
		return this.customerConversationContextService.loadHistory(userId, toCustomerHistory(request));
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

	/**
	 * Memory 后端状态响应。
	 *
	 * @param backend Memory 持久化后端
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public record MemoryBackendResponse(String backend) {
	}

	/**
	 * 短期上下文清空响应。
	 *
	 * @param userId 用户唯一标识
	 * @param deleted Redis Key 是否被删除
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public record CustomerConversationContextClearResponse(String userId, boolean deleted) {
	}

	/**
	 * 审核任务状态更新请求。
	 *
	 * @param id 审核任务唯一标识
	 * @param status 目标状态，取值 APPROVED 或 REJECTED
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public record ApprovalTaskStatusRequest(String id, String status) {
	}

}
