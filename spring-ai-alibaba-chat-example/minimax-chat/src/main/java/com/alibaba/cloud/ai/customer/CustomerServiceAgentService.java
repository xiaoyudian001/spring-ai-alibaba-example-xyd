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

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 智能客服官方 Agent 服务，统一通过 Spring AI Alibaba ReactAgent 执行客服对话。
 *
 * @author xyd
 * @date 2026-05-18 11:34:38
 */
@Service
public class CustomerServiceAgentService {

	private static final int MAX_HISTORY_MESSAGES = 20;

	private final ReactAgent customerServiceReactAgent;

	private final CustomerServiceIntentPlanner intentPlanner;

	private final CustomerMemoryService memoryService;

	private final CustomerSkillService skillService;

	private final CustomerMcpService customerMcpService;

	private final ToolCallDebugRecorder debugRecorder;

	/**
	 * 创建智能客服官方 Agent 服务。
	 * @param customerServiceReactAgent 智能客服官方 ReactAgent
	 * @param intentPlanner 客服意图规划器
	 * @param memoryService 客服长期记忆服务
	 * @param skillService 客服 Skills 服务
	 * @param customerMcpService 智能客服 MCP 门面服务
	 * @param debugRecorder 工具调用调试记录器
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	public CustomerServiceAgentService(@Qualifier("customerServiceReactAgent") ReactAgent customerServiceReactAgent,
			CustomerServiceIntentPlanner intentPlanner, CustomerMemoryService memoryService,
			CustomerSkillService skillService, CustomerMcpService customerMcpService,
			ToolCallDebugRecorder debugRecorder) {
		this.customerServiceReactAgent = customerServiceReactAgent;
		this.intentPlanner = intentPlanner;
		this.memoryService = memoryService;
		this.skillService = skillService;
		this.customerMcpService = customerMcpService;
		this.debugRecorder = debugRecorder;
	}

	/**
	 * 执行一轮智能客服对话，内部调用官方 ReactAgent，由 Agent Framework 决定工具调用。
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @return 智能客服响应结果
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	public CustomerServiceResult chat(String userId, ChannelType channel, String message,
			List<CustomerConversationMessage> history) {
		this.debugRecorder.clear();
		this.customerMcpService.clearDebugInfo();
		CustomerMemory memoryBefore = this.memoryService.read(userId);
		CustomerServiceIntent intent = this.intentPlanner.plan(message);
		String selectedSkill = this.skillService.selectSkill(channel, intent);
		List<CustomerServiceStep> workflowSteps = workflowSteps(channel, message, intent, selectedSkill, memoryBefore);
		List<CustomerServiceStep> multiAgentSteps = multiAgentSteps(intent, selectedSkill);
		try {
			RunnableConfig config = RunnableConfig.builder()
					.threadId(normalizeUserId(userId))
					.build();
			Optional<NodeOutput> output = this.customerServiceReactAgent.invokeAndGetOutput(
					buildPrompt(channel, message, history, intent, selectedSkill, memoryBefore), config);
			OverAllState state = output.map(NodeOutput::state).orElse(null);
			String content = extractContent(state);
			List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = this.debugRecorder.snapshot();
			CustomerMemory memoryAfter = this.memoryService.update(userId, channel, message, intent);
			McpDebugInfo mcpDebugInfo = this.customerMcpService.snapshotDebugInfo();
			return new CustomerServiceResult(content, intent, memoryBefore, memoryAfter, workflowSteps,
					multiAgentSteps, toolCalls, mcpDebugInfo);
		}
		catch (Exception ex) {
			CustomerMemory memoryAfter = this.memoryService.read(userId);
			List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = this.debugRecorder.snapshot();
			return new CustomerServiceResult("官方智能客服 ReactAgent 调用失败：" + ex.getMessage(), intent,
					memoryBefore, memoryAfter, workflowSteps, multiAgentSteps, toolCalls,
					this.customerMcpService.snapshotDebugInfo());
		}
		finally {
			this.debugRecorder.remove();
			this.customerMcpService.clearDebugInfo();
		}
	}

	/**
	 * 生成客服业务流程步骤，用于展示官方 ReactAgent 调用前后的业务处理路径。
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @param memory 调用前客服记忆
	 * @return Workflow 步骤列表
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private List<CustomerServiceStep> workflowSteps(ChannelType channel, String message, CustomerServiceIntent intent,
			String selectedSkill, CustomerMemory memory) {
		List<CustomerServiceStep> steps = new ArrayList<>();
		steps.add(new CustomerServiceStep("接收渠道消息", "渠道：" + channel + "；消息：" + normalizeForStep(message)));
		steps.add(new CustomerServiceStep("识别客服意图", "Planner 识别为 " + intent + "。"));
		steps.add(new CustomerServiceStep("读取用户记忆", memory.summary()));
		steps.add(new CustomerServiceStep("选择客服技能", "命中 Skill：" + selectedSkill + "。"));
		steps.add(new CustomerServiceStep("官方 ReactAgent",
				"调用 Spring AI Alibaba ReactAgent，由 Agent Framework 自主决定 Tool、RAG、Skill 和人工接管调用。"));
		return steps;
	}

	/**
	 * 生成客服 Multi-Agent 角色步骤，表达后续接入 SequentialAgent 的业务角色边界。
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @return Multi-Agent 步骤列表
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private List<CustomerServiceStep> multiAgentSteps(CustomerServiceIntent intent, String selectedSkill) {
		List<CustomerServiceStep> steps = new ArrayList<>();
		steps.add(new CustomerServiceStep("ReceptionAgent", "接待用户并识别客服意图：" + intent + "。"));
		steps.add(new CustomerServiceStep("SkillAgent", "根据渠道和意图选择客服技能：" + selectedSkill + "。"));
		steps.add(new CustomerServiceStep("OfficialReactAgent", "由 Spring AI Alibaba ReactAgent 执行工具选择和回复生成。"));
		steps.add(new CustomerServiceStep("RiskReviewerAgent", "通过提示词和 requestHumanHandoff 工具约束高风险动作。"));
		return steps;
	}

	/**
	 * 构造传入官方 ReactAgent 的业务提示词。
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @param memory 客服长期记忆
	 * @return ReactAgent 输入提示词
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private String buildPrompt(ChannelType channel, String message, List<CustomerConversationMessage> history,
			CustomerServiceIntent intent, String selectedSkill, CustomerMemory memory) {
		return """
				用户问题：
				%s

				当前渠道：
				%s

				识别意图：
				%s

				客服处理策略：
				%s

				建议优先读取 Skill：
				%s

				可用 Skills：
				%s

				客服长期记忆：
				%s

				最近对话历史：
				%s

				请使用 Spring AI Alibaba ReactAgent 的工具能力完成本轮客服处理。
				需要商品、订单、物流事实时调用工具；需要政策或话术时检索知识库或读取 Skill；
				涉及高风险动作必须请求人工接管。
				""".formatted(message, channel, intent, this.intentPlanner.instructionFor(intent), selectedSkill,
				this.skillService.listSkills(), memory.summary(), historySummary(history));
	}

	/**
	 * 从官方 ReactAgent 输出状态中提取最终回复内容。
	 * @param state 官方 ReactAgent 输出状态
	 * @return 最终回复内容
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private String extractContent(OverAllState state) {
		if (state == null) {
			return "官方智能客服 ReactAgent 没有返回结果。";
		}
		Optional<Object> output = state.value("output");
		if (output.isPresent()) {
			return String.valueOf(output.get());
		}
		Optional<List<AbstractMessage>> messages = state.value("messages");
		if (messages.isPresent() && !messages.get().isEmpty()) {
			return messages.get().get(messages.get().size() - 1).getText();
		}
		Optional<Map<String, Object>> data = Optional.ofNullable(state.data());
		return data.map(String::valueOf).orElse("官方智能客服 ReactAgent 没有返回可展示内容。");
	}

	/**
	 * 生成最近对话历史摘要，避免一次性塞入过长上下文。
	 * @param history 多轮对话历史
	 * @return 历史摘要
	 * @author xyd
	 * @date 2026-05-18 11:34:38
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
	 * 压缩用户输入用于展示在 Workflow 步骤中。
	 * @param message 用户原始输入
	 * @return 可展示文本
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private String normalizeForStep(String message) {
		if (message == null || message.isBlank()) {
			return "空消息";
		}
		String text = message.replaceAll("\\s+", " ").trim();
		return text.length() > 80 ? text.substring(0, 80) + "..." : text;
	}

	/**
	 * 规范化用户 ID，空值统一映射为 default-user。
	 * @param userId 原始用户 ID
	 * @return 规范化用户 ID
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

}
