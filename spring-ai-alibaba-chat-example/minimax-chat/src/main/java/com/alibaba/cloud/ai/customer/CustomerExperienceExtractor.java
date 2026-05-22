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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * 智能客服经验提取服务，从历史对话、审核任务、人工接管和评价结果中提取可复用的客服经验。
 * <p>
 * 经验提取来源：
 * <ul>
 *     <li>历史对话：从成功处理的对话中提取问题类型和处理方式</li>
 *     <li>审核任务：从高风险操作审核中归纳标准处理流程</li>
 *     <li>人工接管：客服人工处理后的典型案例归档</li>
 *     <li>评价结果：从好评或差评反馈中归纳处理经验</li>
 * </ul>
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@Service
public class CustomerExperienceExtractor {

private static final Pattern SENTENCE_SPLITTER = Pattern.compile("[。.!?！?]+");

private final ExperienceManagementService experienceService;

/**
 * 创建经验提取服务。
 * @param experienceService 经验管理服务
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public CustomerExperienceExtractor(ExperienceManagementService experienceService) {
this.experienceService = experienceService;
}

/**
 * 从对话历史中提取经验。
 * @param conversationId 对话 ID
 * @param messages 对话消息列表
 * @param intent 最终客服意图
 * @param finalAnswer 最终回答
 * @return 提取的经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public List<CustomerExperience> extractFromConversation(String conversationId,
List<CustomerConversationMessage> messages, CustomerServiceIntent intent, String finalAnswer) {
if (messages == null || messages.isEmpty()) {
return List.of();
}
List<CustomerExperience> experiences = new ArrayList<>();
String userMessage = messages.stream().filter(m -> "user".equals(m.role())).reduce((first, second) -> second)
.map(CustomerConversationMessage::content).orElse("");
CustomerExperience.ExperienceType type = toExperienceType(intent);
String title = buildTitle(intent, userMessage);
String content = buildExperienceContent(intent, userMessage, finalAnswer);
Set<String> triggerTopics = extractTopics(intent, userMessage);
Set<String> triggerPatterns = extractPatterns(userMessage);
CustomerExperience experience = CustomerExperience.of("conv-" + conversationId + "-" + System.currentTimeMillis(),
type, title, triggerTopics, triggerPatterns, content,
CustomerExperience.ExperienceSource.CONVERSATION, conversationId);
experiences.add(experience);
return experiences;
}

/**
 * 从审核任务中提取经验。
 * @param task 待审核任务
 * @param approved 是否审核通过
 * @param reviewNote 审核备注
 * @return 提取的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public CustomerExperience extractFromApprovalTask(PendingApprovalTask task, boolean approved, String reviewNote) {
if (task == null) {
return null;
}
CustomerServiceIntent intent = inferIntent(task.taskType());
String title = "【审核通过】" + buildTitle(intent, task.reason());
String content = buildApprovalContent(intent, task.taskType(), task.reason(), reviewNote);
Set<String> topics = extractTopics(intent, task.reason());
Set<String> patterns = extractPatterns(task.reason());
return CustomerExperience.of("approval-" + task.id(), toExperienceType(intent), title, topics, patterns,
content, CustomerExperience.ExperienceSource.APPROVAL_TASK, task.id());
}

/**
 * 从人工接管中提取经验。
 * @param conversationId 对话 ID
 * @param userMessage 用户原始问题
 * @param agentResponse 客服回复
 * @param success 是否成功处理
 * @return 提取的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public CustomerExperience extractFromHumanHandoff(String conversationId, String userMessage,
String agentResponse, boolean success) {
if (!success || agentResponse == null || agentResponse.isBlank()) {
return null;
}
String content = "【人工接管处理】用户问题：" + userMessage + "\n\n处理方式：" + agentResponse;
Set<String> topics = Set.of("human_handoff", "人工接管");
Set<String> patterns = extractPatterns(userMessage);
return CustomerExperience.of("handoff-" + conversationId + "-" + System.currentTimeMillis(),
CustomerExperience.ExperienceType.HUMAN_HANDOFF, "人工接管处理案例", topics, patterns, content,
CustomerExperience.ExperienceSource.HUMAN_HANDOFF, conversationId);
}

/**
 * 从评价结果中提取经验。
 * @param evaluationResult 评估结果
 * @return 提取的经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public List<CustomerExperience> extractFromEvaluation(AgentEvaluationResult evaluationResult) {
if (evaluationResult == null) {
return List.of();
}
List<CustomerExperience> experiences = new ArrayList<>();
if (evaluationResult.passed()) {
return experiences;
}
List<String> failedChecks = evaluationResult.checks().stream()
.filter(c -> c.applicable() && !c.passed())
.map(c -> c.checkName() + "：" + c.reason())
.toList();
if (failedChecks.isEmpty()) {
return experiences;
}
CustomerExperience exp = CustomerExperience.of(
"eval-" + evaluationResult.reportId() + "-" + System.currentTimeMillis(),
CustomerExperience.ExperienceType.GENERAL, "评估失败问题处理改进建议", Set.of("evaluation"),
Set.of("改进", "优化"), String.join("\n", failedChecks),
CustomerExperience.ExperienceSource.EVALUATION, evaluationResult.reportId());
experiences.add(exp);
return experiences;
}

/**
 * 保存提取的经验到数据库。
 * @param experiences 经验列表
 * @return 保存成功的数量
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public int saveExperiences(List<CustomerExperience> experiences) {
if (experiences == null || experiences.isEmpty()) {
return 0;
}
int saved = 0;
for (CustomerExperience exp : experiences) {
if (exp != null) {
this.experienceService.create(exp);
saved++;
}
}
return saved;
}

/**
 * 构建经验标题。
 * @param intent 客服意图
 * @param userMessage 用户消息
 * @return 标题
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private String buildTitle(CustomerServiceIntent intent, String userMessage) {
String prefix = switch (intent) {
case REFUND_REQUEST -> "退款处理案例";
case COMPLAINT -> "投诉处理案例";
case PRICE_NEGOTIATION -> "议价处理案例";
case LOGISTICS_QUERY -> "物流查询处理案例";
case ORDER_STATUS -> "订单状态处理案例";
case HUMAN_HANDOFF -> "人工接管案例";
case RETURN_POLICY -> "退换货政策咨询案例";
case PRODUCT_INQUIRY -> "商品咨询案例";
default -> "通用处理案例";
};
String shortMsg = userMessage != null && userMessage.length() > 20 ? userMessage.substring(0, 20) + "..."
: userMessage;
return prefix + "：" + shortMsg;
}

/**
 * 构建经验内容。
 * @param intent 客服意图
 * @param userMessage 用户消息
 * @param finalAnswer 最终回答
 * @return 经验内容
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private String buildExperienceContent(CustomerServiceIntent intent, String userMessage, String finalAnswer) {
return "【用户问题】" + (userMessage != null ? userMessage : "未知") + "\n\n" + "【处理方式】"
+ (finalAnswer != null ? finalAnswer : "未生成回答");
}

/**
 * 构建审核任务经验内容。
 * @param intent 客服意图
 * @param taskType 任务类型
 * @param reason 原因
 * @param reviewNote 审核备注
 * @return 经验内容
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private String buildApprovalContent(CustomerServiceIntent intent, String taskType, String reason,
String reviewNote) {
return "【任务类型】" + taskType + "\n\n" + "【触发原因】" + (reason != null ? reason : "未知") + "\n\n"
+ "【审核建议】" + (reviewNote != null ? reviewNote : "无");
}

/**
 * 从意图推断审核任务类型。
 * @param taskType 任务类型
 * @return 客服意图
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private CustomerServiceIntent inferIntent(String taskType) {
if (taskType == null) {
return CustomerServiceIntent.GENERAL_CHAT;
}
return switch (taskType.toUpperCase()) {
case "HUMAN_HANDOFF" -> CustomerServiceIntent.HUMAN_HANDOFF;
case "REFUND" -> CustomerServiceIntent.REFUND_REQUEST;
case "COMPLAINT" -> CustomerServiceIntent.COMPLAINT;
default -> CustomerServiceIntent.GENERAL_CHAT;
};
}

/**
 * 提取触发主题。
 * @param intent 客服意图
 * @param userMessage 用户消息
 * @return 主题集合
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private Set<String> extractTopics(CustomerServiceIntent intent, String userMessage) {
Set<String> topics = new HashSet<>();
if (intent != null) {
topics.add(intent.name().toLowerCase());
}
if (userMessage != null) {
String lower = userMessage.toLowerCase();
if (lower.contains("退款") || lower.contains("退货")) {
topics.add("refund");
}
if (lower.contains("投诉")) {
topics.add("complaint");
}
if (lower.contains("议价") || lower.contains("便宜")) {
topics.add("price");
}
if (lower.contains("物流") || lower.contains("发货")) {
topics.add("shipping");
}
}
return topics;
}

/**
 * 提取触发模式。
 * @param userMessage 用户消息
 * @return 模式集合
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private Set<String> extractPatterns(String userMessage) {
Set<String> patterns = new HashSet<>();
if (userMessage != null && userMessage.length() <= 100) {
patterns.add(userMessage);
}
if (userMessage != null) {
for (String sentence : SENTENCE_SPLITTER.split(userMessage)) {
String trimmed = sentence.trim();
if (trimmed.length() >= 4 && trimmed.length() <= 50) {
patterns.add(trimmed);
}
}
}
return patterns;
}

/**
 * 将客服意图转换为经验类型。
 * @param intent 客服意图
 * @return 经验类型
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private CustomerExperience.ExperienceType toExperienceType(CustomerServiceIntent intent) {
if (intent == null) {
return CustomerExperience.ExperienceType.GENERAL;
}
return switch (intent) {
case REFUND_REQUEST -> CustomerExperience.ExperienceType.REFUND;
case COMPLAINT -> CustomerExperience.ExperienceType.COMPLAINT;
case PRICE_NEGOTIATION -> CustomerExperience.ExperienceType.PRICE_NEGOTIATION;
case LOGISTICS_QUERY, ORDER_STATUS -> CustomerExperience.ExperienceType.SHIPPING_DELAY;
case HUMAN_HANDOFF -> CustomerExperience.ExperienceType.HUMAN_HANDOFF;
default -> CustomerExperience.ExperienceType.GENERAL;
};
}

}
