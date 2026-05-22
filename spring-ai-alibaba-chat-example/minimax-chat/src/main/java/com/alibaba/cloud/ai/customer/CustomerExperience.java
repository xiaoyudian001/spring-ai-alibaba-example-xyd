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

import java.time.Instant;
import java.util.Set;

/**
 * 智能客服经验，记录从历史对话、审核任务、人工接管和评价结果中提取的可复用客服处理经验。
 * <p>
 * 经验分为不同类型：
 * <ul>
 *     <li>REFUND - 退款处理经验</li>
 *     <li>COMPLAINT - 投诉处理经验</li>
 *     <li>PRICE_NEGOTIATION - 议价处理经验</li>
 *     <li>SHIPPING_DELAY - 发货延迟处理经验</li>
 *     <li>HUMAN_HANDOFF - 人工接管场景经验</li>
 *     <li>GENERAL - 通用处理经验</li>
 * </ul>
 *
 * @param id 经验唯一标识
 * @param type 经验类型
 * @param title 经验标题，简短描述
 * @param triggerTopics 触发主题集合，例如 refund、shipping、xianyu
 * @param triggerPatterns 触发匹配模式，例如 "退款" 或 "申请退款"
 * @param experienceContent 经验内容，包含问题描述和处理建议
 * @param source 来源类型：CONVERSATION、HUMAN_HANDOFF、APPROVAL_TASK、EVALUATION
 * @param sourceId 来源ID，例如对话ID或任务ID
 * @param usageCount 使用次数
 * @param lastUsedAt 最后使用时间
 * @param enabled 是否启用，false 时不参与经验注入
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public record CustomerExperience(String id, ExperienceType type, String title, Set<String> triggerTopics,
Set<String> triggerPatterns, String experienceContent, ExperienceSource source, String sourceId,
int usageCount, Instant lastUsedAt, boolean enabled, Instant createdAt, Instant updatedAt) {

/**
 * 经验类型枚举。
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public enum ExperienceType {

/**
 * 退款处理经验，用户申请退款时的标准处理流程和话术。
 */
REFUND,

/**
 * 投诉处理经验，用户表达不满时的安抚和处理策略。
 */
COMPLAINT,

/**
 * 议价处理经验，用户砍价时的应对策略和权限边界。
 */
PRICE_NEGOTIATION,

/**
 * 发货延迟处理经验，用户询问发货时间或催促时的标准回复。
 */
SHIPPING_DELAY,

/**
 * 人工接管场景经验，需要升级人工时的处理方式。
 */
HUMAN_HANDOFF,

/**
 * 质量争议处理经验，用户反馈商品质量问题时的处理流程。
 */
QUALITY_DISPUTE,

/**
 * 地址修改处理经验，用户要求修改收货地址时的处理方式。
 */
ADDRESS_CHANGE,

/**
 * 通用处理经验，不属于特定类型时的通用参考。
 */
GENERAL

}

/**
 * 经验来源类型枚举。
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public enum ExperienceSource {

/**
 * 来源于历史对话，模型从成功处理的对话中提取。
 */
CONVERSATION,

/**
 * 来源于人工接管，客服人员成功处理后归档的经验。
 */
HUMAN_HANDOFF,

/**
 * 来源于审核任务，高风险操作经审核通过后的标准处理方式。
 */
APPROVAL_TASK,

/**
 * 来源于评价结果，用户好评或差评反馈后归纳的经验。
 */
EVALUATION,

/**
 * 人工录入，运营人员手动创建的专家经验。
 */
MANUAL

}

/**
 * 创建经验，默认启用，使用次数为 0。
 * @param id 经验 ID
 * @param type 经验类型
 * @param title 经验标题
 * @param triggerTopics 触发主题
 * @param triggerPatterns 触发模式
 * @param experienceContent 经验内容
 * @param source 来源
 * @param sourceId 来源 ID
 * @return 客服经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public static CustomerExperience of(String id, ExperienceType type, String title, Set<String> triggerTopics,
Set<String> triggerPatterns, String experienceContent, ExperienceSource source, String sourceId) {
Instant now = Instant.now();
return new CustomerExperience(id, type, title, triggerTopics, triggerPatterns, experienceContent, source,
sourceId, 0, null, true, now, now);
}

/**
 * 创建通用经验。
 * @param title 标题
 * @param triggerTopics 触发主题
 * @param experienceContent 经验内容
 * @return 客服经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public static CustomerExperience ofGeneral(String title, Set<String> triggerTopics, String experienceContent) {
return of("exp-" + System.currentTimeMillis(), ExperienceType.GENERAL, title, triggerTopics, Set.of(),
experienceContent, ExperienceSource.MANUAL, "manual");
}

/**
 * 判断当前经验是否匹配给定的意图和消息。
 * @param intent 客服意图
 * @param message 用户消息
 * @return true 表示匹配
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public boolean matches(CustomerServiceIntent intent, String message) {
if (!this.enabled) {
return false;
}
ExperienceType intentType = toExperienceType(intent);
if (this.type != ExperienceType.GENERAL && intentType != this.type) {
return false;
}
if (this.triggerTopics.contains(intent.name().toLowerCase())) {
return true;
}
if (message != null && this.triggerPatterns != null) {
String lowerMessage = message.toLowerCase();
for (String pattern : this.triggerPatterns) {
if (lowerMessage.contains(pattern.toLowerCase())) {
return true;
}
}
}
return false;
}

/**
 * 获取经验摘要，用于提示词注入。
 * @return 经验摘要
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public String summary() {
return "【" + this.title() + "】" + this.experienceContent();
}

/**
 * 记录一次使用。
 * @return 使用次数 +1 后的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public CustomerExperience recordUsage() {
return new CustomerExperience(this.id(), this.type(), this.title(), this.triggerTopics(),
this.triggerPatterns(), this.experienceContent(), this.source(), this.sourceId(), this.usageCount() + 1,
Instant.now(), this.enabled(), this.createdAt(), Instant.now());
}

/**
 * 将客服意图转换为经验类型。
 * @param intent 客服意图
 * @return 对应的经验类型
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private ExperienceType toExperienceType(CustomerServiceIntent intent) {
return switch (intent) {
case REFUND_REQUEST -> ExperienceType.REFUND;
case COMPLAINT -> ExperienceType.COMPLAINT;
case PRICE_NEGOTIATION -> ExperienceType.PRICE_NEGOTIATION;
case LOGISTICS_QUERY, ORDER_STATUS -> ExperienceType.SHIPPING_DELAY;
case HUMAN_HANDOFF -> ExperienceType.HUMAN_HANDOFF;
default -> ExperienceType.GENERAL;
};
}

}
