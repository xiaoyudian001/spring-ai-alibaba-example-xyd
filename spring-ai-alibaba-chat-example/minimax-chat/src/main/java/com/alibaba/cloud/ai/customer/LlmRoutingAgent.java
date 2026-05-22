/*
 * Copyright 2026-2027 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain this file except in compliance with the License.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 智能客服 LLM 路由服务，逐步替代手写 Planner，用于判断商品、订单、退款、投诉、人工接管等专家路由。
 * <p>
 * LLM 路由优势：
 * <ul>
 *     <li>可以理解语义，更准确地判断复杂意图</li>
 *     <li>可以处理模糊或混合意图</li>
 *     <li>可以通过提示词工程持续优化路由准确率</li>
 *     <li>无需编写大量 if-else 规则</li>
 * </ul>
 *
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Service
public class LlmRoutingAgent {

private static final String ROUTING_PROMPT = """
你是智能客服意图识别专家。请仔细分析用户问题，判断用户真实的客服意图。

用户问题：%s

请从以下意图中选择最匹配的一个（只输出意图名称，不要输出其他内容）：

意图列表：
- PRODUCT_INQUIRY：商品咨询，用户询问商品是否还在、价格、成色、规格、库存等
- PRICE_NEGOTIATION：议价咨询，用户要求降价、优惠、便宜、包邮等
- ORDER_STATUS：订单状态查询，用户询问订单是否创建、是否支付、当前处理状态
- LOGISTICS_QUERY：物流查询，用户询问快递、发货时间、签收状态
- REFUND_REQUEST：退款请求，用户明确表达退款、退货、取消订单
- RETURN_POLICY：退换货政策咨询，用户询问是否支持退货、超过期限能否退
- COMPLAINT：投诉咨询，用户表达不满、投诉、差评威胁
- HUMAN_HANDOFF：人工接管，用户明确要求人工客服
- GENERAL_CHAT：一般对话，不属于以上任何意图

判断规则：
1. 如果用户问题包含多个意图，选择最主要的一个
2. 如果是退款+投诉，优先识别为投诉
3. 如果是议价+商品，先识别商品再看是否议价
4. 如果用户没有明确意图，选择 GENERAL_CHAT

请只输出一个意图名称（大写加下划线）：
""";

private static final String ROUTING_EXPLAIN_PROMPT = """
你是智能客服意图识别专家。请分析用户问题并给出判断理由。

用户问题：%s

请判断用户意图并解释理由。输出格式：
意图：[识别的意图]
理由：[判断理由]
置信度：[高/中/低]
""";

private static final Pattern INTENT_PATTERN = Pattern.compile("(?:意图|PRODUCT_INQUIRY|PRICE_NEGOTIATION|ORDER_STATUS|LOGISTICS_QUERY|REFUND_REQUEST|RETURN_POLICY|COMPLAINT|HUMAN_HANDOFF|GENERAL_CHAT)",
Pattern.CASE_INSENSITIVE);

private final ChatClient chatClient;

/**
 * 创建 LLM 路由服务。
 * @param chatClient Spring AI ChatClient
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public LlmRoutingAgent(ChatClient chatClient) {
this.chatClient = chatClient;
}

/**
 * 使用 LLM 识别客服意图。
 * @param message 用户问题
 * @return 识别的意图
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public CustomerServiceIntent route(String message) {
if (message == null || message.isBlank()) {
return CustomerServiceIntent.GENERAL_CHAT;
}
String response = this.chatClient.prompt().user(String.format(ROUTING_PROMPT, message)).call().content();
return parseIntent(response);
}

/**
 * 使用 LLM 识别意图，并返回解释。
 * @param message 用户问题
 * @return 路由结果
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public RoutingResult routeWithExplain(String message) {
if (message == null || message.isBlank()) {
return new RoutingResult(CustomerServiceIntent.GENERAL_CHAT, "消息为空", "低");
}
String response = this.chatClient.prompt().user(String.format(ROUTING_EXPLAIN_PROMPT, message)).call().content();
return parseRoutingResult(response);
}

/**
 * 判断是否需要路由到专家处理。
 * @param intent 识别的意图
 * @return true 表示需要专家介入
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public boolean needsExpert(CustomerServiceIntent intent) {
return intent == CustomerServiceIntent.REFUND_REQUEST || intent == CustomerServiceIntent.COMPLAINT
|| intent == CustomerServiceIntent.HUMAN_HANDOFF;
}

/**
 * 获取专家路由建议。
 * @param intent 识别的意图
 * @return 建议的专家类型
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public ExpertType suggestExpert(CustomerServiceIntent intent) {
return switch (intent) {
case PRODUCT_INQUIRY, PRICE_NEGOTIATION -> ExpertType.PRODUCT;
case ORDER_STATUS, LOGISTICS_QUERY, REFUND_REQUEST, RETURN_POLICY -> ExpertType.ORDER;
case COMPLAINT, HUMAN_HANDOFF -> ExpertType.COMPLAINT;
default -> ExpertType.GENERAL;
};
}

/**
 * 解析 LLM 返回的意图。
 * @param response LLM 返回内容
 * @return 识别的意图
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
private CustomerServiceIntent parseIntent(String response) {
if (response == null) {
return CustomerServiceIntent.GENERAL_CHAT;
}
String normalized = response.toUpperCase().trim();
for (CustomerServiceIntent intent : CustomerServiceIntent.values()) {
if (normalized.contains(intent.name())) {
return intent;
}
}
return CustomerServiceIntent.GENERAL_CHAT;
}

/**
 * 解析 LLM 返回的路由结果。
 * @param response LLM 返回内容
 * @return 路由结果
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
private RoutingResult parseRoutingResult(String response) {
if (response == null) {
return new RoutingResult(CustomerServiceIntent.GENERAL_CHAT, "解析失败", "低");
}
CustomerServiceIntent intent = parseIntent(response);
String reason = extractBetween(response, "理由", "置信度");
String confidence = extractBetween(response, "置信度", null);
if (reason == null) {
reason = "根据语义分析判断";
}
if (confidence == null) {
confidence = "中";
}
return new RoutingResult(intent, reason.trim(), confidence.trim());
}

/**
 * 提取文本中两个标记之间的内容。
 * @param text 原文
 * @param start 起始标记
 * @param end 结束标记
 * @return 提取的内容
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
private String extractBetween(String text, String start, String end) {
if (text == null) {
return null;
}
int startIndex = text.indexOf(start);
if (startIndex < 0) {
return null;
}
startIndex += start.length();
if (end == null) {
return text.substring(startIndex).trim();
}
int endIndex = text.indexOf(end, startIndex);
if (endIndex < 0) {
return text.substring(startIndex).trim();
}
return text.substring(startIndex, endIndex).trim();
}

/**
 * 专家类型枚举。
 *
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public enum ExpertType {

/**
 * 商品专家，处理商品咨询和议价。
 */
PRODUCT,

/**
 * 订单专家，处理订单状态、物流、退款。
 */
ORDER,

/**
 * 投诉专家，处理投诉和升级。
 */
COMPLAINT,

/**
 * 通用，无需专家介入。
 */
GENERAL

}

/**
 * 路由结果，包含意图、判断理由和置信度。
 *
 * @param intent 识别的意图
 * @param reason 判断理由
 * @param confidence 置信度
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public record RoutingResult(CustomerServiceIntent intent, String reason, String confidence) {

}

}
