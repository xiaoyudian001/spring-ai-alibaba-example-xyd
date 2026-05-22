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

package com.alibaba.cloud.ai.controller;

import java.util.List;
import java.util.Set;

import com.alibaba.cloud.ai.customer.CustomerExperience;
import com.alibaba.cloud.ai.customer.CustomerExperience.ExperienceType;
import com.alibaba.cloud.ai.customer.CustomerServiceIntent;
import com.alibaba.cloud.ai.customer.ExperienceManagementService;
import com.alibaba.cloud.ai.customer.ExperienceManagementService.ExperienceStats;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能客服经验管理 REST API 控制器，提供经验的增删改查和启用/禁用功能。
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@RestController
@RequestMapping("/minimax/experience")
public class ExperienceController {

private final ExperienceManagementService experienceService;

/**
 * 创建经验管理控制器。
 * @param experienceService 经验管理服务
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public ExperienceController(ExperienceManagementService experienceService) {
this.experienceService = experienceService;
}

/**
 * 获取经验统计信息。
 * @return 经验统计
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping("/stats")
public ExperienceStats getStats() {
return this.experienceService.getStats();
}

/**
 * 查询所有经验。
 * @return 经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping("/all")
public List<CustomerExperience> getAllExperiences() {
return this.experienceService.findAll();
}

/**
 * 查询已启用的经验。
 * @return 经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping
public List<CustomerExperience> getEnabledExperiences() {
return this.experienceService.findAllEnabled();
}

/**
 * 查询指定类型的经验。
 * @param type 经验类型
 * @return 经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping("/type/{type}")
public List<CustomerExperience> getExperiencesByType(@PathVariable("type") String type) {
try {
ExperienceType expType = ExperienceType.valueOf(type.toUpperCase());
return this.experienceService.findByType(expType);
}
catch (IllegalArgumentException ex) {
return List.of();
}
}

/**
 * 获取指定经验详情。
 * @param id 经验 ID
 * @return 经验对象，不存在返回空
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping("/{id}")
public CustomerExperience getExperience(@PathVariable("id") String id) {
return this.experienceService.findById(id).orElse(null);
}

/**
 * 根据意图和消息匹配经验。
 * @param intent 客服意图
 * @param message 用户消息
 * @return 匹配的经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping("/match")
public List<CustomerExperience> matchExperiences(@RequestParam("intent") String intent,
@RequestParam("message") String message) {
try {
CustomerServiceIntent serviceIntent = CustomerServiceIntent.valueOf(intent.toUpperCase());
return this.experienceService.findMatching(serviceIntent, message);
}
catch (IllegalArgumentException ex) {
return List.of();
}
}

/**
 * 创建新经验。
 * @param request 经验创建请求
 * @return 创建后的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
public CustomerExperience createExperience(@RequestBody ExperienceRequest request) {
CustomerExperience experience = CustomerExperience.of(request.id(),
request.type() != null ? request.type() : ExperienceType.GENERAL, request.title(),
request.triggerTopics() != null ? request.triggerTopics() : Set.of(),
request.triggerPatterns() != null ? request.triggerPatterns() : Set.of(),
request.experienceContent(), CustomerExperience.ExperienceSource.MANUAL,
request.id() != null ? request.id() : "manual-" + System.currentTimeMillis());
return this.experienceService.create(experience);
}

/**
 * 启用或禁用经验。
 * @param id 经验 ID
 * @param enabled 是否启用
 * @return 更新后的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@PutMapping("/{id}/enabled")
public CustomerExperience setEnabled(@PathVariable("id") String id, @RequestParam("enabled") boolean enabled) {
return this.experienceService.setEnabled(id, enabled).orElse(null);
}

/**
 * 删除经验。
 * @param id 经验 ID
 * @return 删除成功返回 true
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@DeleteMapping("/{id}")
public boolean deleteExperience(@PathVariable("id") String id) {
return this.experienceService.delete(id);
}

/**
 * 经验创建请求体。
 *
 * @param id 经验 ID
 * @param type 经验类型
 * @param title 经验标题
 * @param triggerTopics 触发主题
 * @param triggerPatterns 触发模式
 * @param experienceContent 经验内容
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public record ExperienceRequest(String id, ExperienceType type, String title, Set<String> triggerTopics,
Set<String> triggerPatterns, String experienceContent) {

}

}
