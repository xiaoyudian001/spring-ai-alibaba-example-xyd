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

	/**
	 * 创建统一智能客服门面服务。
	 * @param intentPlanner 客服意图规划器
	 * @param customerServiceAgentService 普通客服 ReactAgent 服务
	 * @param customerServiceMultiAgentService 客服 Multi-Agent 服务
	 * @author xyd
	 * @date 2026-05-20 09:40:00
	 */
	public CustomerServiceAssistantService(CustomerServiceIntentPlanner intentPlanner,
			CustomerServiceAgentService customerServiceAgentService,
			CustomerServiceMultiAgentService customerServiceMultiAgentService) {
		this.intentPlanner = intentPlanner;
		this.customerServiceAgentService = customerServiceAgentService;
		this.customerServiceMultiAgentService = customerServiceMultiAgentService;
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
		CustomerServiceIntent intent = this.intentPlanner.plan(message);
		if (requiresMultiAgent(intent, message)) {
			CustomerServiceMultiAgentResult result = this.customerServiceMultiAgentService.chat(userId, channel, message,
					history);
			return CustomerServiceAssistantResult.fromMultiAgent(result,
					new CustomerServiceStep("AUTO_ROUTE", "识别到高风险或复合售后场景，后端自动选择 Multi-Agent 协作处理。"));
		}
		CustomerServiceResult result = this.customerServiceAgentService.chat(userId, channel, message, history);
		return CustomerServiceAssistantResult.fromAgent(result,
				new CustomerServiceStep("AUTO_ROUTE", "识别到常规客服咨询，后端自动选择客服 ReactAgent 快速处理。"));
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
		if (intent == CustomerServiceIntent.REFUND_REQUEST || intent == CustomerServiceIntent.COMPLAINT
				|| intent == CustomerServiceIntent.HUMAN_HANDOFF) {
			return true;
		}
		String text = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
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
