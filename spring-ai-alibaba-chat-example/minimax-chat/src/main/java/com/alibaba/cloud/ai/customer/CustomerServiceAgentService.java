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

import com.alibaba.cloud.ai.agent.LearningAgentService.LearningAgentMessage;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * 智能客服主 Agent 服务，整合渠道、意图、Memory、Skills、RAG、Tool 和可观察执行步骤。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Service
public class CustomerServiceAgentService {

	private static final int MAX_HISTORY_MESSAGES = 20;

	private static final String SYSTEM_PROMPT = """
			你是一个多渠道智能客服助手，服务网页客服、闲鱼、微信、企业微信和小程序客服场景。
			请始终使用中文回答，语气自然、礼貌、简洁。
			你可以调用客服工具查询商品、订单、物流，检索客服政策，读取客服 Skill，创建工单或请求人工接管。
			订单、物流、库存、退款状态属于实时事实，必须优先使用 Tool 查询。
			售后政策、发货规则、闲鱼回复规范、微信客服规范属于知识内容，优先使用 searchCustomerPolicy 检索。
			遇到议价、退款、投诉、闲鱼或微信特定话术时，优先读取对应 Skill。
			涉及退款、赔偿、取消订单、修改地址、承诺额外优惠、投诉升级等高风险动作时，不得直接执行，必须调用 requestHumanHandoff 生成人工确认建议。
			不要输出 <think>、</think> 或任何思考标签。
			""";

	private final ChatClient chatClient;

	private final CustomerServiceTools customerServiceTools;

	private final CustomerServiceIntentPlanner intentPlanner;

	private final CustomerMemoryService memoryService;

	private final CustomerSkillService skillService;

	private final CustomerMcpService customerMcpService;

	private final ToolCallDebugRecorder debugRecorder;

	/**
	 * 创建智能客服主 Agent 服务，并配置 MiniMax ChatClient 与客服工具集合。
	 * @param chatModel 聊天模型
	 * @param customerServiceTools 客服工具集合
	 * @param intentPlanner 客服意图规划器
	 * @param memoryService 客服长期记忆服务
	 * @param skillService 客服 Skills 服务
	 * @param customerMcpService 智能客服 MCP 门面服务
	 * @param debugRecorder 工具调用调试记录器
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerServiceAgentService(ChatModel chatModel, CustomerServiceTools customerServiceTools,
			CustomerServiceIntentPlanner intentPlanner, CustomerMemoryService memoryService,
			CustomerSkillService skillService, CustomerMcpService customerMcpService,
			ToolCallDebugRecorder debugRecorder) {
		this.customerServiceTools = customerServiceTools;
		this.intentPlanner = intentPlanner;
		this.memoryService = memoryService;
		this.skillService = skillService;
		this.customerMcpService = customerMcpService;
		this.debugRecorder = debugRecorder;
		this.chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(new SimpleLoggerAdvisor())
				.defaultOptions(defaultOptions())
				.build();
	}

	/**
	 * 执行一轮智能客服对话，输出客服回复、Workflow、Multi-Agent 步骤、Memory 和 Tool 调用信息。
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @return 智能客服响应结果
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerServiceResult chat(String userId, ChannelType channel, String message,
			List<LearningAgentMessage> history) {
		this.debugRecorder.clear();
		this.customerMcpService.clearDebugInfo();
		CustomerMemory memoryBefore = this.memoryService.read(userId);
		CustomerServiceIntent intent = this.intentPlanner.plan(message);
		String selectedSkill = this.skillService.selectSkill(channel, intent);
		List<CustomerServiceStep> workflowSteps = workflowSteps(channel, message, intent, selectedSkill, memoryBefore);
		List<CustomerServiceStep> multiAgentSteps = multiAgentSteps(intent, selectedSkill);
		try {
			String content = this.chatClient.prompt()
					.messages(buildMessages(channel, message, history, intent, selectedSkill, memoryBefore))
					.options(defaultOptions())
					.tools(this.customerServiceTools)
					.call()
					.content();
			List<ToolCallDebugRecorder.ToolCallDebug> toolCalls = this.debugRecorder.snapshot();
			CustomerMemory memoryAfter = this.memoryService.update(userId, channel, message, intent);
			return new CustomerServiceResult(content, intent, memoryBefore, memoryAfter, workflowSteps,
					multiAgentSteps, toolCalls, this.customerMcpService.snapshotDebugInfo());
		}
		finally {
			this.debugRecorder.remove();
			this.customerMcpService.clearDebugInfo();
		}
	}

	/**
	 * 生成客服 Workflow 业务步骤，用于观察本轮客服任务的稳定处理流程。
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @param memory 调用前客服记忆
	 * @return Workflow 步骤列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private List<CustomerServiceStep> workflowSteps(ChannelType channel, String message, CustomerServiceIntent intent,
			String selectedSkill, CustomerMemory memory) {
		List<CustomerServiceStep> steps = new ArrayList<>();
		steps.add(new CustomerServiceStep("接收渠道消息", "渠道：" + channel + "；消息：" + normalizeForStep(message)));
		steps.add(new CustomerServiceStep("识别客服意图", "Planner 识别为 " + intent + "。"));
		steps.add(new CustomerServiceStep("读取用户记忆", memory.summary()));
		steps.add(new CustomerServiceStep("选择客服技能", "命中 Skill：" + selectedSkill + "。"));
		steps.add(new CustomerServiceStep("判断外部能力",
				"订单/物流/商品事实走 Tool/MCP；客服政策和话术走 RAG；高风险动作走人工确认。"));
		steps.add(new CustomerServiceStep("生成客服回复", "携带渠道、意图、Memory、Skill 列表和可用工具调用 MiniMax-M2.7。"));
		return steps;
	}

	/**
	 * 生成客服 Multi-Agent 角色步骤，用于展示真实业务职责拆分。
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @return Multi-Agent 步骤列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private List<CustomerServiceStep> multiAgentSteps(CustomerServiceIntent intent, String selectedSkill) {
		List<CustomerServiceStep> steps = new ArrayList<>();
		steps.add(new CustomerServiceStep("ReceptionAgent", "接待用户并识别客服意图：" + intent + "。"));
		steps.add(new CustomerServiceStep("SkillAgent", "根据渠道和意图选择客服技能：" + selectedSkill + "。"));
		steps.add(new CustomerServiceStep("FactAgent", "必要时通过 Tool/MCP 查询商品、订单或物流事实。"));
		steps.add(new CustomerServiceStep("PolicyAgent", "必要时通过 RAG 检索退款、发货、投诉或渠道回复规范。"));
		steps.add(new CustomerServiceStep("ReplyWriterAgent", "整合事实、政策、技能和渠道语气生成客服回复。"));
		steps.add(new CustomerServiceStep("RiskReviewerAgent", "检查退款、赔偿、取消订单和投诉升级等高风险动作是否需要人工确认。"));
		return steps;
	}

	/**
	 * 构造发给模型的系统消息、历史消息和用户消息。
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @param history 多轮对话历史
	 * @param intent 客服意图
	 * @param selectedSkill 命中的客服技能
	 * @param memory 客服长期记忆
	 * @return 模型消息列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private List<Message> buildMessages(ChannelType channel, String message, List<LearningAgentMessage> history,
			CustomerServiceIntent intent, String selectedSkill, CustomerMemory memory) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage(SYSTEM_PROMPT + "\n" + "当前渠道：" + channel + "\n" + "客服意图：" + intent + "\n"
				+ this.intentPlanner.instructionFor(intent) + "\n" + "可用 Skills：\n" + this.skillService.listSkills()
				+ "\n" + "建议优先读取 Skill：" + selectedSkill + "\n" + "客服记忆：" + memory.summary()));
		List<LearningAgentMessage> safeHistory = history == null ? List.of() : history;
		int start = Math.max(0, safeHistory.size() - MAX_HISTORY_MESSAGES);
		for (LearningAgentMessage item : safeHistory.subList(start, safeHistory.size())) {
			Message historyMessage = toMessage(item);
			if (historyMessage != null) {
				messages.add(historyMessage);
			}
		}
		messages.add(new UserMessage(message));
		return messages;
	}

	/**
	 * 把前端历史消息转换成 Spring AI 消息对象。
	 * @param message 前端历史消息
	 * @return Spring AI 消息对象
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private Message toMessage(LearningAgentMessage message) {
		if (message == null || message.content() == null || message.content().isBlank()) {
			return null;
		}
		return "assistant".equals(normalizeRole(message.role())) ? new AssistantMessage(message.content())
				: new UserMessage(message.content());
	}

	/**
	 * 规范化前端历史消息角色。
	 * @param role 原始角色
	 * @return 规范化角色
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private String normalizeRole(String role) {
		return role == null ? "user" : role.trim().toLowerCase();
	}

	/**
	 * 压缩用户输入用于展示在 Workflow 步骤中。
	 * @param message 用户原始输入
	 * @return 可展示文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private String normalizeForStep(String message) {
		if (message == null || message.isBlank()) {
			return "空消息";
		}
		String text = message.replaceAll("\\s+", " ").trim();
		return text.length() > 80 ? text.substring(0, 80) + "..." : text;
	}

	/**
	 * 创建 MiniMax-M2.7 默认调用参数。
	 * @return OpenAI 兼容 Chat Options
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private OpenAiChatOptions defaultOptions() {
		return OpenAiChatOptions.builder()
				.model("MiniMax-M2.7")
				.temperature(0.4)
				.build();
	}

}
