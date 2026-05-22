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

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * 智能客服轻量直连大模型服务，专门处理寒暄、感谢、自我介绍等不需要 Tool、RAG、MCP 或 Agent 编排的简单对话。
 *
 * @author xyd
 * @date 2026-05-21 11:20:00
 */
@Service
public class CustomerDirectChatService {

	private static final int MAX_HISTORY_MESSAGES = 8;

	private final ChatClient chatClient;

	private final CustomerMemoryService memoryService;

	private final CustomerServiceTraceLogger traceLogger;

	private final CustomerFactCollectorService factCollectorService;

	/**
	 * 创建客服轻量直连大模型服务。
	 * @param chatModel Spring AI 聊天模型
	 * @param memoryService 客服长期记忆服务
	 * @param traceLogger 智能客服链路日志埋点
	 * @param factCollectorService 客服事实收集服务
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	public CustomerDirectChatService(ChatModel chatModel, CustomerMemoryService memoryService,
			CustomerServiceTraceLogger traceLogger, CustomerFactCollectorService factCollectorService) {
		this.chatClient = ChatClient.builder(chatModel)
				.defaultOptions(OpenAiChatOptions.builder().model("MiniMax-M2.7").temperature(0.5).build())
				.build();
		this.memoryService = memoryService;
		this.traceLogger = traceLogger;
		this.factCollectorService = factCollectorService;
	}

	/**
	 * 执行一轮轻量客服对话，仅调用 MiniMax 大模型生成自然回复，不触发工具、RAG、MCP 或长期 Memory 更新。
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始问题
	 * @param history 短期多轮上下文
	 * @return 轻量直连大模型响应结果
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	public CustomerServiceAssistantResult chat(String userId, ChannelType channel, String message,
			List<CustomerConversationMessage> history) {
		String traceId = this.traceLogger.start("CUSTOMER_SERVICE_DIRECT_LLM", userId, channel, message);
		CustomerMemory memory = this.memoryService.read(userId);
		CustomerFactBundle factBundle = this.factCollectorService.collect(CustomerServiceIntent.GENERAL_CHAT, message,
				memory);
		this.traceLogger.step("CUSTOMER_SERVICE_DIRECT_LLM", traceId, "FACT_COLLECT",
				factBundle.summaryForPrompt());
		this.traceLogger.step("CUSTOMER_SERVICE_DIRECT_LLM", traceId, "DIRECT_ROUTE",
				"简单对话直连 MiniMax，不调用 Tool、RAG、MCP 或 Agent。");
		try {
			this.traceLogger.step("CUSTOMER_SERVICE_DIRECT_LLM", traceId, "MODEL_CALL", "调用 MiniMax-M2.7 轻量客服回复");
			String content = this.chatClient.prompt()
					.system(systemPrompt(channel))
					.user(userPrompt(message, history, memory, factBundle))
					.options(OpenAiChatOptions.builder().model("MiniMax-M2.7").temperature(0.5).build())
					.call()
					.content();
			this.traceLogger.finish("CUSTOMER_SERVICE_DIRECT_LLM", traceId, CustomerServiceIntent.GENERAL_CHAT,
					content);
			return CustomerServiceAssistantResult.fromDirect(content, memory, memory,
					new CustomerServiceStep("DIRECT_LLM", "识别为简单对话，直接调用 MiniMax-M2.7 生成回复。"), factBundle);
		}
		catch (Exception ex) {
			this.traceLogger.error("CUSTOMER_SERVICE_DIRECT_LLM", traceId, ex);
			return CustomerServiceAssistantResult.fromDirect("您好，我是小雨点智能客服。刚才轻量模型调用失败："
					+ ex.getMessage() + "。您可以继续告诉我商品号、订单号或具体售后问题。", memory, memory,
					new CustomerServiceStep("DIRECT_LLM_ERROR", "轻量直连大模型失败，返回友好兜底回复。"), factBundle);
		}
	}

	/**
	 * 构造轻量客服系统提示词，约束模型只处理简单对话，不编造业务事实。
	 * @param channel 当前客服渠道
	 * @return 系统提示词
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	private String systemPrompt(ChannelType channel) {
		return """
				你是小雨点智能客服助手，当前渠道是 %s。
				本轮只处理寒暄、感谢、自我介绍、能力说明等简单对话。
				请用中文简洁回复，语气自然友好。
				不要编造订单、商品、物流、退款、工单等业务事实。
				如果用户开始咨询具体商品、订单、物流、退款、投诉或人工处理，请提示用户提供商品号或订单号，系统后续会进入业务处理链路。
				""".formatted(channel == null ? ChannelType.WEB : channel);
	}

	/**
	 * 构造轻量客服用户提示词，携带少量历史和长期记忆摘要，但不要求模型调用工具。
	 * @param message 用户原始问题
	 * @param history 短期多轮上下文
	 * @param memory 客服长期记忆
	 * @param factBundle 后端预取事实包
	 * @return 用户提示词
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	private String userPrompt(String message, List<CustomerConversationMessage> history, CustomerMemory memory,
			CustomerFactBundle factBundle) {
		return """
				最近对话：
				%s

				客服长期记忆摘要：
				%s

				后端预取事实：
				%s

				用户当前消息：
				%s

				必须优先回答“用户当前消息”，最近对话只作为上下文参考，不能把历史里的旧问题当成本轮问题。
				如“后端预取事实”不为空，必须优先依据事实回答，不能编造商品、订单、物流或售后状态。
				请直接回复用户，不要输出调试信息。
				""".formatted(historySummary(history), memory == null ? "暂无" : memory.summary(),
				factBundle == null ? "暂无后端预取事实。" : factBundle.summaryForPrompt(), safeText(message));
	}

	/**
	 * 汇总短期历史上下文，避免简单对话携带过多上下文导致成本升高。
	 * @param history 短期多轮上下文
	 * @return 历史摘要
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	private String historySummary(List<CustomerConversationMessage> history) {
		if (history == null || history.isEmpty()) {
			return "暂无";
		}
		int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
		StringBuilder builder = new StringBuilder();
		for (CustomerConversationMessage item : history.subList(start, history.size())) {
			builder.append(safeText(item.role())).append(": ").append(safeText(item.content())).append('\n');
		}
		return builder.toString().trim();
	}

	/**
	 * 对文本做空值安全处理。
	 * @param value 原始文本
	 * @return 安全文本
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	private String safeText(String value) {
		return value == null ? "" : value.trim();
	}

}
