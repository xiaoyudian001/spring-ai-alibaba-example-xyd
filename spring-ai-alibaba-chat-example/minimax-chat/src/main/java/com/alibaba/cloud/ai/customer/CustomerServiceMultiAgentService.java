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

package com.alibaba.cloud.ai.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 智能客服官方 Multi-Agent 服务，通过 SequentialAgent 串行执行多个专业客服子 Agent。
 *
 * @author xyd
 * @date 2026-05-19 00:20:26
 */
@Service
public class CustomerServiceMultiAgentService {

	private static final int MAX_HISTORY_MESSAGES = 20;

	private final SequentialAgent customerServiceSequentialAgent;

	private final CustomerServiceIntentPlanner intentPlanner;

	private final CustomerMemoryService memoryService;

	private final CustomerSkillService skillService;

	private final CustomerMcpService customerMcpService;

	private final ToolCallDebugRecorder debugRecorder;

	private final CustomerServiceTraceLogger traceLogger;

	private final CustomerFactCollectorService factCollectorService;

	/**
	 * 创建智能客服官方 Multi-Agent 服务。
	 * @param customerServiceSequentialAgent 智能客服官方 SequentialAgent
	 * @param intentPlanner 客服意图规划器
	 * @param memoryService 客服长期记忆服务
	 * @param skillService 客服 Skills 服务
	 * @param customerMcpService 智能客服 MCP 门面服务
	 * @param debugRecorder 工具调用调试记录器
	 * @param traceLogger 智能客服链路日志埋点
	 * @param factCollectorService 客服事实收集服务
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public CustomerServiceMultiAgentService(
			@Qualifier("customerServiceSequentialAgent") SequentialAgent customerServiceSequentialAgent,
			CustomerServiceIntentPlanner intentPlanner, CustomerMemoryService memoryService,
			CustomerSkillService skillService, CustomerMcpService customerMcpService,
			ToolCallDebugRecorder debugRecorder, CustomerServiceTraceLogger traceLogger,
			CustomerFactCollectorService factCollectorService) {
		this.customerServiceSequentialAgent = customerServiceSequentialAgent;
		this.intentPlanner = intentPlanner;
		this.memoryService = memoryService;
		this.skillService = skillService;
		this.customerMcpService = customerMcpService;
		this.debugRecorder = debugRecorder;
		this.traceLogger = traceLogger;
		this.factCollectorService = factCollectorService;
	}

	/**
	 * 执行一轮智能客服官方 Multi-Agent 对话。
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @return 智能客服官方 Multi-Agent 响应结果
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public CustomerServiceMultiAgentResult chat(String userId, ChannelType channel, String message,
			List<CustomerConversationMessage> history) {
		this.debugRecorder.clear();
		this.customerMcpService.clearDebugInfo();
		String normalizedUserId = normalizeUserId(userId);
		ChannelType safeChannel = channel == null ? ChannelType.WEB : channel;
		String traceId = this.traceLogger.start("CUSTOMER_SERVICE_MULTI_AGENT", normalizedUserId, safeChannel, message);
		CustomerMemory memoryBefore = this.memoryService.read(normalizedUserId);
		this.traceLogger.step("CUSTOMER_SERVICE_MULTI_AGENT", traceId, "MEMORY_READ", memoryBefore.summary());
		CustomerServiceIntent intent = this.intentPlanner.plan(message);
		this.traceLogger.step("CUSTOMER_SERVICE_MULTI_AGENT", traceId, "INTENT_PLAN", String.valueOf(intent));
		String selectedSkill = this.skillService.selectSkill(safeChannel, intent);
		this.traceLogger.step("CUSTOMER_SERVICE_MULTI_AGENT", traceId, "SKILL_SELECT", selectedSkill);
		CustomerFactBundle factBundle = this.factCollectorService.collect(intent, message, memoryBefore);
		this.traceLogger.step("CUSTOMER_SERVICE_MULTI_AGENT", traceId, "FACT_COLLECT", factBundle.summaryForPrompt());
		try {
			RunnableConfig config = RunnableConfig.builder()
					.threadId(normalizedUserId + "-customer-service-multi-agent-" + traceId)
					.build();
			this.traceLogger.step("CUSTOMER_SERVICE_MULTI_AGENT", traceId, "SEQUENTIAL_AGENT",
					"调用官方 SequentialAgent");
			Optional<OverAllState> stateOptional = this.customerServiceSequentialAgent.invoke(buildPrompt(safeChannel,
					message, history, intent, selectedSkill, memoryBefore, factBundle), config);
			OverAllState state = stateOptional.orElseThrow();
			List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = this.debugRecorder.snapshot();
			this.traceLogger.tools("CUSTOMER_SERVICE_MULTI_AGENT", traceId, toolCalls);
			String content = finalContent(state);
			CustomerMemory memoryAfter = this.memoryService.update(normalizedUserId, safeChannel, message, intent);
			this.traceLogger.step("CUSTOMER_SERVICE_MULTI_AGENT", traceId, "MEMORY_WRITE", memoryAfter.summary());
			this.traceLogger.finish("CUSTOMER_SERVICE_MULTI_AGENT", traceId, intent, content);
			return new CustomerServiceMultiAgentResult(content, intent, memoryBefore, memoryAfter, agentSteps(state),
					toolCalls, this.customerMcpService.snapshotDebugInfo(), state.data(), factBundle);
		}
		catch (Exception ex) {
			this.traceLogger.error("CUSTOMER_SERVICE_MULTI_AGENT", traceId, ex);
			CustomerMemory memoryAfter = this.memoryService.read(normalizedUserId);
			return new CustomerServiceMultiAgentResult("智能客服官方 Multi-Agent 调用失败：" + ex.getMessage(), intent,
					memoryBefore, memoryAfter, List.of(new CustomerServiceStep("error", ex.getMessage())),
					this.debugRecorder.snapshot(), this.customerMcpService.snapshotDebugInfo(), Map.of(), factBundle);
		}
		finally {
			this.debugRecorder.remove();
			this.customerMcpService.clearDebugInfo();
		}
	}

	/**
	 * 构造传入官方 SequentialAgent 的业务提示词。
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @param memory 客服长期记忆
	 * @param factBundle 后端预取事实包
	 * @return SequentialAgent 输入提示词
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String buildPrompt(ChannelType channel, String message, List<CustomerConversationMessage> history,
			CustomerServiceIntent intent, String selectedSkill, CustomerMemory memory, CustomerFactBundle factBundle) {
		return """
				当前渠道：%s
				识别意图：%s
				推荐 Skill：%s
				客服处理策略：%s
				客服长期记忆：%s
				后端预取事实：
				%s

				最近对话历史：
				%s

				本轮用户问题：%s

				必须优先回答“本轮用户问题”，最近对话历史只能作为上下文参考，不能把历史里的旧问题当成本轮问题。
				如“后端预取事实”不为空，必须优先依据这些事实回答；只有事实不足时才继续调用工具补充。
				""".formatted(channel, intent, selectedSkill, this.intentPlanner.instructionFor(intent),
				memory.summary(), factBundle == null ? "暂无后端预取事实。" : factBundle.summaryForPrompt(),
				historySummary(history), normalizeMessage(message));
	}

	/**
	 * 从官方 SequentialAgent 状态中提取最终客服回复。
	 * @param state 官方 SequentialAgent 总状态
	 * @return 最终客服回复
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String finalContent(OverAllState state) {
		return assistantText(state, "risk_review_agent_output")
				.or(() -> assistantText(state, "reply_agent_output"))
				.or(() -> assistantText(state, "policy_agent_output"))
				.orElse("智能客服官方 Multi-Agent 没有返回内容。");
	}

	/**
	 * 生成前端可展示的官方 Multi-Agent 子 Agent 步骤。
	 * @param state 官方 SequentialAgent 总状态
	 * @return 子 Agent 步骤列表
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private List<CustomerServiceStep> agentSteps(OverAllState state) {
		List<CustomerServiceStep> steps = new ArrayList<>();
		addAssistantStep(state, steps, "ReceptionAgent", "reception_agent_output");
		addAssistantStep(state, steps, "FactAgent", "fact_agent_output");
		addAssistantStep(state, steps, "PolicyAgent", "policy_agent_output");
		addAssistantStep(state, steps, "ReplyAgent", "reply_agent_output");
		addAssistantStep(state, steps, "RiskReviewAgent", "risk_review_agent_output");
		return List.copyOf(steps);
	}

	/**
	 * 把指定 outputKey 的 AssistantMessage 加入步骤列表。
	 * @param state 官方 SequentialAgent 总状态
	 * @param steps 步骤列表
	 * @param name 子 Agent 名称
	 * @param key 输出 Key
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private void addAssistantStep(OverAllState state, List<CustomerServiceStep> steps, String name, String key) {
		assistantText(state, key).ifPresent(text -> steps.add(new CustomerServiceStep(name, summary(text))));
	}

	/**
	 * 从指定 outputKey 中读取 AssistantMessage 文本。
	 * @param state 官方 SequentialAgent 总状态
	 * @param key 输出 Key
	 * @return AssistantMessage 文本
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private Optional<String> assistantText(OverAllState state, String key) {
		return state.value(key, AssistantMessage.class).map(AssistantMessage::getText);
	}

	/**
	 * 生成最近对话历史摘要。
	 * @param history 多轮对话历史
	 * @return 历史摘要
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String historySummary(List<CustomerConversationMessage> history) {
		List<CustomerConversationMessage> safeHistory = history == null ? List.of() : history;
		int start = Math.max(0, safeHistory.size() - MAX_HISTORY_MESSAGES);
		StringBuilder builder = new StringBuilder();
		for (CustomerConversationMessage item : safeHistory.subList(start, safeHistory.size())) {
			if (item != null && item.content() != null && !item.content().isBlank()) {
				builder.append("- ").append(item.role()).append("：").append(item.content()).append("\n");
			}
		}
		return builder.isEmpty() ? "暂无" : builder.toString().trim();
	}

	/**
	 * 生成适合调试区展示的摘要。
	 * @param text 原始文本
	 * @return 摘要文本
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String summary(String text) {
		String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		return value.length() <= 240 ? value : value.substring(0, 240) + "...";
	}

	/**
	 * 规范化用户 ID。
	 * @param userId 原始用户 ID
	 * @return 规范化用户 ID
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

	/**
	 * 规范化用户消息。
	 * @param message 原始用户消息
	 * @return 规范化用户消息
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String normalizeMessage(String message) {
		return message == null || message.isBlank() ? "你好，请问有什么可以帮你？" : message;
	}

}
