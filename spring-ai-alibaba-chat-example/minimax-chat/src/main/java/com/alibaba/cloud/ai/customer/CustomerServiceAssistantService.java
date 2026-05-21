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
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * 面向客户主页面的统一智能客服门面，负责把技术链路选择收敛到后端自动策略。
 *
 * @author xyd
 * @date 2026-05-20 09:40:00
 */
@Service
public class CustomerServiceAssistantService {

	private final CustomerServiceIntentPlanner intentPlanner;

	private final CustomerServiceAgentService customerServiceAgentService;

	private final CustomerServiceMultiAgentService customerServiceMultiAgentService;

	private final CustomerDirectChatService customerDirectChatService;

	private final CustomerServiceTraceLogger traceLogger;

	/**
	 * 创建统一智能客服门面服务。
	 * @param intentPlanner 客服意图规划器
	 * @param customerServiceAgentService 普通客服 ReactAgent 服务
	 * @param customerServiceMultiAgentService 客服 Multi-Agent 服务
	 * @param customerDirectChatService 客服轻量直连大模型服务
	 * @param traceLogger 智能客服链路日志埋点
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	public CustomerServiceAssistantService(CustomerServiceIntentPlanner intentPlanner,
			CustomerServiceAgentService customerServiceAgentService,
			CustomerServiceMultiAgentService customerServiceMultiAgentService,
			CustomerDirectChatService customerDirectChatService, CustomerServiceTraceLogger traceLogger) {
		this.intentPlanner = intentPlanner;
		this.customerServiceAgentService = customerServiceAgentService;
		this.customerServiceMultiAgentService = customerServiceMultiAgentService;
		this.customerDirectChatService = customerDirectChatService;
		this.traceLogger = traceLogger;
	}

	/**
	 * 执行一轮客户无感客服对话：先识别意图，再按风险和复杂度自动选择后端 Agent 策略。
	 * <p>
	 * 普通商品、订单、物流、议价问题走客服 ReactAgent；退款、投诉、人工接管、强情绪和复合售后问题走
	 * Multi-Agent，让处理 Agent 和质检 Agent 协作约束高风险话术。
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始问题
	 * @param history 多轮上下文
	 * @return 统一客服响应
	 * @author xyd
	 * @date 2026-05-20 09:40:00
	 */
	public CustomerServiceAssistantResult chat(String userId, ChannelType channel, String message,
			List<CustomerConversationMessage> history) {
		String traceId = this.traceLogger.start("CUSTOMER_SERVICE_ASSISTANT_ROUTER", userId, channel, message);
		// 步骤1：意图识别 - 使用意图规划器分析用户消息，识别用户的具体客服意图类型
		CustomerServiceIntent intent = this.intentPlanner.plan(message);
		this.traceLogger.step("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, "INTENT_PLAN", String.valueOf(intent));
		
		// 步骤2：路由决策1 - 判断是否适合轻量直连大模型处理（如寒暄、问候等简单对话）
		if (requiresDirectLlm(intent, message)) {
			// 路径A：直连大模型 - 快速响应简单对话，避免进入完整Agent链路
			this.traceLogger.step("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, "AUTO_ROUTE", "DIRECT_LLM");
			CustomerServiceAssistantResult result = this.customerDirectChatService.chat(userId, channel, message,
					history);
			this.traceLogger.finish("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, intent, result.content());
			return result;
		}
		
		// 步骤3：路由决策2 - 判断是否需要多智能体协作处理（如退款、投诉、高风险场景）
		if (requiresMultiAgent(intent, message)) {
			// 路径B：Multi-Agent协作 - 使用多个专业Agent协作处理复杂场景，包含风险控制
			this.traceLogger.step("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, "AUTO_ROUTE", "MULTI_AGENT");
			CustomerServiceMultiAgentResult result = this.customerServiceMultiAgentService.chat(userId, channel, message,
					history);
			// 返回多智能体处理结果，并记录路由决策信息
			CustomerServiceAssistantResult assistantResult = CustomerServiceAssistantResult.fromMultiAgent(result,
					new CustomerServiceStep("AUTO_ROUTE", "识别到高风险或复合售后场景，后端自动选择 Multi-Agent 协作处理。"));
			this.traceLogger.finish("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, intent, assistantResult.content());
			return assistantResult;
		}
		
		// 路径C：ReactAgent处理 - 使用单一ReactAgent快速处理常规客服咨询
		this.traceLogger.step("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, "AUTO_ROUTE", "REACT_AGENT");
		CustomerServiceResult result = this.customerServiceAgentService.chat(userId, channel, message, history);
		// 返回单Agent处理结果，并记录路由决策信息
		CustomerServiceAssistantResult assistantResult = CustomerServiceAssistantResult.fromAgent(result,
				new CustomerServiceStep("AUTO_ROUTE", "识别到常规客服咨询，后端自动选择客服 ReactAgent 快速处理。"));
		this.traceLogger.finish("CUSTOMER_SERVICE_ASSISTANT_ROUTER", traceId, intent, assistantResult.content());
		return assistantResult;
	}

	/**
	 * 判断当前问题是否可以走轻量直连大模型，避免寒暄类问题进入完整 Agent 链路。
	 * @param intent 已识别的客服意图
	 * @param message 用户原始问题
	 * @return 是否适合直连大模型
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	private boolean requiresDirectLlm(CustomerServiceIntent intent, String message) {
		// 检查1：只有普通聊天意图才考虑直连大模型
		if (intent != CustomerServiceIntent.GENERAL_CHAT) {
			return false;
		}
		// 预处理：标准化消息文本（转小写、去空格）便于匹配
		String text = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
		// 检查2：如果消息包含订单号格式（如O-12345或P-12345），则不适合直连
		if (text.matches(".*[op]-\\d+.*")) {
			return false;
		}
		// 检查3：如果消息包含任何客服相关关键词，则不适合直连，需要进入完整Agent链路
		return !containsAny(text, "订单", "商品", "物流", "快递", "退款", "退货", "售后", "投诉", "人工", "赔偿", "优惠", "便宜",
				"发货", "工单", "地址", "发票");
	}

	/**
	 * 判断当前问题是否需要使用 Multi-Agent 做额外质检和风险约束。
	 * @param intent 已识别的客服意图
	 * @param message 用户原始问题
	 * @return 是否需要多智能体处理
	 * @author xyd
	 * @date 2026-05-20 09:40:00
	 */
	private boolean requiresMultiAgent(CustomerServiceIntent intent, String message) {
		// 检查1：高风险意图类型直接触发多智能体处理（退款请求、投诉、人工接管）
		if (intent == CustomerServiceIntent.REFUND_REQUEST || intent == CustomerServiceIntent.COMPLAINT
				|| intent == CustomerServiceIntent.HUMAN_HANDOFF) {
			return true;
		}
		// 预处理：标准化消息文本（转小写、去空格）便于匹配
		String text = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
		// 检查2：如果消息包含任何高风险关键词，则需要多智能体协作处理以加强风险控制
		return containsAny(text, "赔偿", "投诉", "差评", "人工", "退款", "退货", "取消订单", "售后", "生气", "马上处理", "必须解决");
	}

	/**
	 * 判断文本是否包含任一触发词。
	 * @param text 待检测文本
	 * @param keywords 触发词集合
	 * @return 是否命中
	 * @author xyd
	 * @date 2026-05-20 09:40:00
	 */
	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

}
