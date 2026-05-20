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

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能客服官方 SequentialAgent 配置，定义接待、事实查询、政策检索、回复生成和风险复核子 Agent。
 *
 * @author xyd
 * @date 2026-05-19 00:20:26
 */
@Configuration
public class CustomerServiceSequentialAgentConfiguration {

	/**
	 * 构建智能客服官方 SequentialAgent。
	 * @param chatModel 聊天模型
	 * @param toolCallbacks 智能客服官方工具适配器
	 * @return 智能客服官方 SequentialAgent
	 * @throws GraphStateException Agent 状态图构建失败时抛出
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	// 声明为Spring Bean，将此方法返回的SequentialAgent实例注入到Spring容器中
	@Bean
	// 构建智能客服SequentialAgent的主方法，接收聊天模型和工具回调作为参数
	public SequentialAgent customerServiceSequentialAgent(ChatModel chatModel,
			OfficialCustomerServiceToolCallbacks toolCallbacks) throws GraphStateException {
		// 构建第一个子Agent：客服接待Agent，负责理解用户输入的基本信息
		ReactAgent receptionAgent = ReactAgent.builder()
				// 设置Agent名称为customer_reception_agent
			.name("customer_reception_agent")
			// 设置Agent描述，说明其职责是理解渠道、用户问题、意图和上下文
			.description("客服接待 Agent，负责理解渠道、用户问题、意图和上下文。")
			// 注入聊天模型用于处理对话
			.model(chatModel)
				// 设置Agent的指令，定义其工作方式和输出要求
			.instruction("""
					你是客服接待 Agent。请阅读用户输入，提取渠道、用户问题、商品号、订单号、用户情绪和可能意图。
					只输出结构化 Markdown 摘要，不要调用工具。
					用户输入：{input}
					""")
			// 设置输出键名，用于后续Agent引用此Agent的输出
			.outputKey("reception_agent_output")
			// 构建接待Agent实例
			.build();

		// 构建第二个子Agent：客服事实查询Agent，负责查询具体的业务数据
		ReactAgent factAgent = ReactAgent.builder()
			// 设置Agent名称为customer_fact_agent
			.name("customer_fact_agent")
			// 设置Agent描述，说明其职责是查询商品、订单和物流事实
			.description("客服事实查询 Agent，负责查询商品、订单和物流事实。")
			// 注入聊天模型用于处理对话
			.model(chatModel)
				// 设置Agent的指令，定义其工作方式和工具调用规则
			.instruction("""
					你是客服事实查询 Agent。请基于接待摘要和用户输入判断是否需要查询商品、订单或物流。
					如果存在商品号、订单号、议价、退款或物流问题，必须调用对应工具获取商品、订单、物流、底价策略、退款资格或售后状态事实。
					接待摘要：{reception_agent_output}
					用户输入：{input}
					""")
			// 注入所有客服工具，使Agent能够调用商品、订单、物流等查询工具
			.tools(toolCallbacks.all())
			// 设置输出键名，用于后续Agent引用此Agent的输出
			.outputKey("fact_agent_output")
			// 构建事实查询Agent实例
			.build();

		// 构建第三个子Agent：客服政策和技能Agent，负责检索客服政策和读取Skill
		ReactAgent policyAgent = ReactAgent.builder()
			// 设置Agent名称为customer_policy_agent
			.name("customer_policy_agent")
			// 设置Agent描述，说明其职责是检索客服政策和读取Skill
			.description("客服政策和技能 Agent，负责检索客服政策和读取 Skill。")
			// 注入聊天模型用于处理对话
			.model(chatModel)
				// 设置Agent的指令，定义其工作方式和Skill调用规则
			.instruction("""
					你是客服政策和技能 Agent。请根据用户问题、接待摘要和事实查询结果，判断需要哪个客服 Skill 或政策知识。
					涉及闲鱼、微信、议价、退款、退货、投诉、改地址、质量争议或人工接管时，优先读取 Skill 或检索客服知识库；需要评估召回效果时可调用 evaluateCustomerPolicyRecall。
					接待摘要：{reception_agent_output}
					事实查询：{fact_agent_output}
					用户输入：{input}
					""")
			// 注入所有客服工具，使Agent能够调用政策检索和Skill读取工具
			.tools(toolCallbacks.all())
			// 设置输出键名，用于后续Agent引用此Agent的输出
			.outputKey("policy_agent_output")
			// 构建政策Agent实例
			.build();

		// 构建第四个子Agent：客服回复Agent，负责生成面向用户的最终客服回复草稿
		ReactAgent replyAgent = ReactAgent.builder()
			// 设置Agent名称为customer_reply_agent
			.name("customer_reply_agent")
			// 设置Agent描述，说明其职责是生成面向用户的最终客服回复草稿
			.description("客服回复 Agent，负责生成面向用户的最终客服回复草稿。")
			// 注入聊天模型用于处理对话
			.model(chatModel)
				// 设置Agent的指令，定义其工作方式和回复生成规则
			.instruction("""
					你是客服回复 Agent。请综合接待摘要、事实查询和政策技能结果，生成自然、礼貌、简洁的中文客服回复。
					不要输出  标签，不要编造商品、订单、物流或政策事实。
					接待摘要：{reception_agent_output}
					事实查询：{fact_agent_output}
					政策技能：{policy_agent_output}
					用户输入：{input}
					""")
			// 设置输出键名，用于后续Agent引用此Agent的输出
			.outputKey("reply_agent_output")
			// 构建回复Agent实例
			.build();

		// 构建第五个子Agent：客服风险复核Agent，负责检查高风险动作并输出最终回复
		ReactAgent riskReviewAgent = ReactAgent.builder()
			// 设置Agent名称为customer_risk_review_agent
			.name("customer_risk_review_agent")
			// 设置Agent描述，说明其职责是检查高风险动作并输出最终回复
			.description("客服风险复核 Agent，负责检查高风险动作并输出最终回复。")
			// 注入聊天模型用于处理对话
			.model(chatModel)
				// 设置Agent的指令，定义其工作方式和风险控制规则
			.instruction("""
					你是客服风险复核 Agent。请检查回复草稿是否直接承诺了退款、赔付、取消订单、修改地址、额外优惠或投诉升级。
					如果涉及高风险动作，必须调用 requestHumanHandoff 工具，最终回复中只能说明已转人工或需人工确认。
					最终只返回面向用户的客服回复，不要包含内部评审过程。
					回复草稿：{reply_agent_output}
					政策技能：{policy_agent_output}
					用户输入：{input}
					""")
			// 注入所有客服工具，使Agent能够调用人工接管工具
			.tools(toolCallbacks.all())
			// 设置输出键名，用于最终输出结果
			.outputKey("risk_review_agent_output")
			// 构建风险复核Agent实例
			.build();

		// 构建SequentialAgent，将所有子Agent按顺序串联起来
		return SequentialAgent.builder()
			// 设置SequentialAgent名称为customer_service_multi_agent
			.name("customer_service_multi_agent")
			// 设置SequentialAgent描述，说明其处理流程
			.description("智能客服官方 Multi-Agent，按接待、事实、政策、回复和风控顺序处理客服请求。")
			// 设置内存保存器，用于保存Agent执行过程中的状态
			.saver(new MemorySaver())
			// 设置子Agent列表，按接待、事实、政策、回复、风控顺序执行
			.subAgents(List.of(receptionAgent, factAgent, policyAgent, replyAgent, riskReviewAgent))
			// 构建SequentialAgent实例并返回
			.build();
	}

}