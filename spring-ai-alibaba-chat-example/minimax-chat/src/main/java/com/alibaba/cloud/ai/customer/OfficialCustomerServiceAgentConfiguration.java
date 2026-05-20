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

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能客服官方 Spring AI Alibaba Agent Framework 配置。
 *
 * @author xyd
 * @date 2026-05-18 11:34:38
 */
@Configuration
public class OfficialCustomerServiceAgentConfiguration {

	/**
	 * 构建智能客服官方 ReactAgent，所有工具均通过 ToolCallback 暴露给 Agent Framework。
	 * @param chatModel 聊天模型
	 * @param toolCallbacks 智能客服官方工具适配器
	 * @return 智能客服官方 ReactAgent
	 * @throws GraphStateException Agent 状态图构建失败时抛出
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	@Bean
	public ReactAgent customerServiceReactAgent(ChatModel chatModel, OfficialCustomerServiceToolCallbacks toolCallbacks)
			throws GraphStateException {
		return ReactAgent.builder()
				.name("customer-service-agent")
				.description("""
						你是基于 Spring AI Alibaba Agent Framework 的多渠道智能客服助手。
						请始终使用中文回答，语气自然、礼貌、简洁。
						你服务网页客服、闲鱼、微信、企业微信和小程序客服场景。
						订单、物流、库存、议价底价、退款资格和售后状态属于实时事实，必须优先调用工具查询。
						售后政策、发货规则、闲鱼回复规范、微信客服规范属于知识内容，优先检索客服知识库。
						遇到议价、退款、物流跟进、投诉、闲鱼或微信特定话术时，优先读取对应 Skill。
						涉及退款、赔偿、取消订单、修改地址、承诺额外优惠、投诉升级等高风险动作时，不得直接执行，必须请求人工接管。
						不要输出 <think>、</think> 或任何思考标签。
						""")
				.model(chatModel)
				.saver(new MemorySaver())
				.tools(toolCallbacks.all())
				.build();
	}

}
