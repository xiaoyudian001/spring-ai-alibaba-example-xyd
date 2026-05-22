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
import com.alibaba.cloud.ai.graph.agent.ReactAgent.ReactAgentBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestBuilder;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能客服专家 Agent 配置，配置商品专家、订单专家、投诉专家等子 Agent。
 *
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Configuration
public class ExpertAgentConfiguration {

private static final String PRODUCT_EXPERT_SYSTEM = """
你是一位专业的商品客服专家。请根据已收集的商品信息、议价策略和政策知识，为用户提供专业的商品咨询和议价建议。

回复原则：
1. 熟悉商品信息，如实介绍商品的成色、规格、库存
2. 议价时不能超过商品底价，可以提供赠品或其他优惠方案
3. 保持专业、友好、耐心的态度
4. 不确定时要诚实说明，不要胡乱承诺
""";

private static final String ORDER_EXPERT_SYSTEM = """
你是一位专业的订单客服专家。请根据已收集的订单信息、退款资格和政策知识，为用户提供专业的订单状态查询和退款处理建议。

回复原则：
1. 准确告知订单当前状态（待支付、已支付、已发货、已收货等）
2. 退款必须先查询订单状态和退款资格
3. 符合退款条件时说明处理时效，不符合时给出替代方案
4. 高风险操作（退款、取消订单）必须说明需要人工审核
""";

private static final String COMPLAINT_EXPERT_SYSTEM = """
你是一位专业的投诉处理专家。请根据已收集的信息和政策知识，为用户提供专业的投诉处理方案。

回复原则：
1. 首先真诚道歉，表达对用户不满的理解
2. 不要急于解释或推卸责任，先让用户发泄情绪
3. 积极提供解决方案，如赠品、优惠券、优先处理等
4. 需要升级时及时转人工接管
5. 记录投诉原因用于后续改进
""";

private static final String LOGISTICS_EXPERT_SYSTEM = """
你是一位专业的物流客服专家。请根据已收集的物流信息，为用户提供专业的物流查询和异常处理建议。

回复原则：
1. 准确告知当前物流位置和预计到达时间
2. 物流异常时主动联系快递查询
3. 已发货不可修改地址，只能尝试联系快递
4. 超时未到时主动协助查询或提交物流异常工单
""";

private static final String SUPERVISOR_SYSTEM = """
你是一位智能客服主管。你的职责是协调各专家的处理结果，做出最终决策。

决策原则：
1. 综合各专家的意见和建议
2. 优先处理用户最关心的问题
3. 确保回复专业、友好、一致
4. 复杂问题及时升级人工
5. 高风险操作必须有人工审核
""";

private static final String RESPONSE_AGGREGATOR_SYSTEM = """
你是一位专业的客服回复聚合专家。请整合所有信息和专家建议，生成最终的客服回复。

回复要求：
1. 简洁、专业、礼貌
2. 直接回答用户问题
3. 不要堆砌技术细节
4. 涉及退款、赔偿等高风险操作要说明需要审核
""";

private static final String FACT_COLLECTOR_SYSTEM = """
你是一位专业的客服事实收集专家。请根据用户问题和历史记忆，确定需要收集哪些事实。

收集原则：
1. 识别用户问题中涉及的关键实体（商品ID、订单ID）
2. 判断需要查询哪些事实（商品、订单、物流、RAG知识）
3. 提取关键意图和上下文
4. 输出格式清晰，便于后续专家处理
""";

@Bean
public ReactAgent factCollectorAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient)
.systemPrompt(FACT_COLLECTOR_SYSTEM).maxSteps(3).toolCallbackProvider(toolCallbackProvider)
.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

@Bean
public ReactAgent productExpertAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient)
.systemPrompt(PRODUCT_EXPERT_SYSTEM).maxSteps(5).toolCallbackProvider(toolCallbackProvider)
.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

@Bean
public ReactAgent orderExpertAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient)
.systemPrompt(ORDER_EXPERT_SYSTEM).maxSteps(5).toolCallbackProvider(toolCallbackProvider)
.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

@Bean
public ReactAgent complaintExpertAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient)
.systemPrompt(COMPLAINT_EXPERT_SYSTEM).maxSteps(5).toolCallbackProvider(toolCallbackProvider)
.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

@Bean
public ReactAgent logisticsExpertAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient)
.systemPrompt(LOGISTICS_EXPERT_SYSTEM).maxSteps(5).toolCallbackProvider(toolCallbackProvider)
.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

@Bean
public ReactAgent supervisorAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient).systemPrompt(SUPERVISOR_SYSTEM).maxSteps(3)
.toolCallbackProvider(toolCallbackProvider).chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

@Bean
public ReactAgent responseAggregatorAgent(ChatClient chatClient, ExpertAgentTools expertTools,
MethodToolCallbackProvider toolCallbackProvider) {
ReactAgentBuilder builder = ReactAgent.builder().chatClient(chatClient)
.systemPrompt(RESPONSE_AGGREGATOR_SYSTEM).maxSteps(3).toolCallbackProvider(toolCallbackProvider)
.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
return builder.build();
}

}
