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
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * 智能客服经验注入服务，在相似问题再次出现时，把历史经验注入 Agent 提示词。
 * <p>
 * 经验注入逻辑：
 * <ol>
 *     <li>根据当前意图和消息匹配已启用的经验</li>
 *     <li>按类型和使用次数排序，优先注入高频使用的经验</li>
 *     <li>生成可注入提示词的经验内容摘要</li>
 *     <li>记录经验被使用，更新使用次数</li>
 * </ol>
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@Service
public class CustomerExperienceProvider {

private static final int MAX_EXPERIENCES_IN_PROMPT = 5;

private static final int MAX_EXPERIENCE_CONTENT_LENGTH = 500;

private final ExperienceManagementService experienceService;

/**
 * 创建经验注入服务。
 * @param experienceService 经验管理服务
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public CustomerExperienceProvider(ExperienceManagementService experienceService) {
this.experienceService = experienceService;
}

/**
 * 根据当前意图和消息获取匹配的经验，注入到 Agent 提示词中。
 * @param intent 当前客服意图
 * @param message 用户消息
 * @return 经验注入内容，用于追加到提示词
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public String provideForPrompt(CustomerServiceIntent intent, String message) {
List<CustomerExperience> matched = findMatchedExperiences(intent, message);
return formatForPrompt(matched);
}

/**
 * 根据当前意图和消息获取匹配的经验对象列表。
 * @param intent 当前客服意图
 * @param message 用户消息
 * @return 匹配的经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public List<CustomerExperience> findMatchedExperiences(CustomerServiceIntent intent, String message) {
List<CustomerExperience> all = this.experienceService.findMatching(intent, message);
return all.stream().limit(MAX_EXPERIENCES_IN_PROMPT).collect(Collectors.toList());
}

/**
 * 格式化经验列表为提示词注入内容。
 * @param experiences 经验列表
 * @return 提示词注入内容
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public String formatForPrompt(List<CustomerExperience> experiences) {
if (experiences == null || experiences.isEmpty()) {
return "";
}
StringBuilder prompt = new StringBuilder();
prompt.append("\n\n=== 历史处理经验参考 ===\n");
for (int i = 0; i < experiences.size(); i++) {
CustomerExperience exp = experiences.get(i);
prompt.append((i + 1)).append(". ").append(exp.summary()).append("\n");
this.experienceService.recordUsage(exp.id());
}
prompt.append("=========================\n");
return prompt.toString();
}

/**
 * 获取经验的简洁摘要列表。
 * @param intent 当前客服意图
 * @param message 用户消息
 * @return 经验摘要列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public List<String> getSummaries(CustomerServiceIntent intent, String message) {
return findMatchedExperiences(intent, message).stream().map(CustomerExperience::summary)
.collect(Collectors.toList());
}

/**
 * 获取经验总数和已启用数量。
 * @return 经验统计信息
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public ExperienceManagementService.ExperienceStats getStats() {
return this.experienceService.getStats();
}

/**
 * 判断当前问题是否有匹配的经验。
 * @param intent 当前客服意图
 * @param message 用户消息
 * @return true 表示有匹配经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public boolean hasMatchingExperience(CustomerServiceIntent intent, String message) {
return !findMatchedExperiences(intent, message).isEmpty();
}

/**
 * 获取指定类型的高频经验。
 * @param type 经验类型
 * @param limit 返回数量
 * @return 经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public List<CustomerExperience> getTopExperiences(CustomerExperience.ExperienceType type, int limit) {
List<CustomerExperience> all = this.experienceService.findByType(type);
return all.stream().limit(limit).collect(Collectors.toList());
}

}
