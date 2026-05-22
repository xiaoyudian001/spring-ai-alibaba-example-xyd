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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 智能客服经验管理服务，使用 MySQL 持久化客服经验，支持经验的新增、查询、启用/禁用车等功能。
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@Service
public class ExperienceManagementService {

private static final TypeReference<Set<String>> SET_TYPE_REF = new TypeReference<>() {
};

private final JdbcTemplate jdbcTemplate;

private final ObjectMapper objectMapper;

/**
 * 创建经验管理服务。
 * @param jdbcTemplate 数据库访问模板
 * @param objectMapper JSON 序列化工具
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public ExperienceManagementService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
this.jdbcTemplate = jdbcTemplate;
this.objectMapper = objectMapper;
}

/**
 * 初始化经验表结构。
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@PostConstruct
public void initializeSchema() {
this.jdbcTemplate.execute("""
CREATE TABLE IF NOT EXISTS customer_experiences (
id VARCHAR(128) PRIMARY KEY,
type VARCHAR(32) NOT NULL,
title VARCHAR(256) NOT NULL,
trigger_topics TEXT,
trigger_patterns TEXT,
experience_content TEXT NOT NULL,
source VARCHAR(32) NOT NULL,
source_id VARCHAR(128),
usage_count INT DEFAULT 0,
last_used_at TIMESTAMP NULL,
enabled BOOLEAN NOT NULL DEFAULT TRUE,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
INDEX idx_type (type),
INDEX idx_enabled (enabled),
INDEX idx_usage_count (usage_count)
)
""");
}

/**
 * 创建新的客服经验。
 * @param experience 经验对象
 * @return 保存后的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized CustomerExperience create(CustomerExperience experience) {
String id = experience.id() != null ? experience.id() : "exp-" + System.currentTimeMillis();
CustomerExperience safeExp = new CustomerExperience(id, experience.type(), experience.title(),
experience.triggerTopics(), experience.triggerPatterns(), experience.experienceContent(),
experience.source(), experience.sourceId(), 0, null, experience.enabled(), Instant.now(),
Instant.now());
this.jdbcTemplate.update("""
INSERT INTO customer_experiences (id, type, title, trigger_topics, trigger_patterns, experience_content, source, source_id, usage_count, enabled, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""", safeExp.id(), safeExp.type().name(), safeExp.title(), toJson(safeExp.triggerTopics()),
toJson(safeExp.triggerPatterns()), safeExp.experienceContent(), safeExp.source().name(),
safeExp.sourceId(), safeExp.usageCount(), safeExp.enabled(), safeExp.createdAt(),
safeExp.updatedAt());
return safeExp;
}

/**
 * 根据 ID 查询经验。
 * @param id 经验 ID
 * @return 经验对象，不存在返回空
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized Optional<CustomerExperience> findById(String id) {
List<CustomerExperience> results = this.jdbcTemplate.query(
"SELECT * FROM customer_experiences WHERE id = ?", (rs, rowNum) -> toExperience(rs), id);
return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
}

/**
 * 查询所有已启用的经验。
 * @return 已启用经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized List<CustomerExperience> findAllEnabled() {
return this.jdbcTemplate.query(
"SELECT * FROM customer_experiences WHERE enabled = TRUE ORDER BY usage_count DESC, updated_at DESC",
(rs, rowNum) -> toExperience(rs));
}

/**
 * 查询指定类型的经验。
 * @param type 经验类型
 * @return 经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized List<CustomerExperience> findByType(CustomerExperience.ExperienceType type) {
return this.jdbcTemplate.query(
"SELECT * FROM customer_experiences WHERE type = ? AND enabled = TRUE ORDER BY usage_count DESC",
(rs, rowNum) -> toExperience(rs), type.name());
}

/**
 * 查询所有经验。
 * @return 经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized List<CustomerExperience> findAll() {
return this.jdbcTemplate.query("SELECT * FROM customer_experiences ORDER BY usage_count DESC, updated_at DESC",
(rs, rowNum) -> toExperience(rs));
}

/**
 * 根据意图和消息匹配经验。
 * @param intent 客服意图
 * @param message 用户消息
 * @return 匹配的经验列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized List<CustomerExperience> findMatching(CustomerServiceIntent intent, String message) {
return findAllEnabled().stream().filter(exp -> exp.matches(intent, message))
.collect(Collectors.toList());
}

/**
 * 记录经验被使用。
 * @param id 经验 ID
 * @return 更新后的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized Optional<CustomerExperience> recordUsage(String id) {
this.jdbcTemplate.update(
"UPDATE customer_experiences SET usage_count = usage_count + 1, last_used_at = ? WHERE id = ?",
Instant.now(), id);
return findById(id);
}

/**
 * 启用或禁用经验。
 * @param id 经验 ID
 * @param enabled 是否启用
 * @return 更新后的经验
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized Optional<CustomerExperience> setEnabled(String id, boolean enabled) {
this.jdbcTemplate.update("UPDATE customer_experiences SET enabled = ? WHERE id = ?", enabled, id);
return findById(id);
}

/**
 * 删除经验。
 * @param id 经验 ID
 * @return 删除成功返回 true
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized boolean delete(String id) {
int deleted = this.jdbcTemplate.update("DELETE FROM customer_experiences WHERE id = ?", id);
return deleted > 0;
}

/**
 * 获取经验统计信息。
 * @return 经验统计
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public synchronized ExperienceStats getStats() {
Integer totalCount = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_experiences", Integer.class);
Integer enabledCount = this.jdbcTemplate.queryForObject(
"SELECT COUNT(*) FROM customer_experiences WHERE enabled = TRUE", Integer.class);
Integer totalUsage = this.jdbcTemplate.queryForObject(
"SELECT COALESCE(SUM(usage_count), 0) FROM customer_experiences", Integer.class);
List<String> topTypes = this.jdbcTemplate.queryForList(
"SELECT type, COUNT(*) as cnt FROM customer_experiences GROUP BY type ORDER BY cnt DESC LIMIT 5",
String.class);
return new ExperienceStats(totalCount != null ? totalCount : 0, enabledCount != null ? enabledCount : 0,
totalUsage != null ? totalUsage : 0);
}

/**
 * 将 ResultSet 转换为经验对象。
 * @param rs 数据库结果集
 * @return 经验对象
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private CustomerExperience toExperience(java.sql.ResultSet rs) throws java.sql.SQLException {
return new CustomerExperience(rs.getString("id"),
CustomerExperience.ExperienceType.valueOf(rs.getString("type")), rs.getString("title"),
parseSet(rs.getString("trigger_topics")), parseSet(rs.getString("trigger_patterns")),
rs.getString("experience_content"),
CustomerExperience.ExperienceSource.valueOf(rs.getString("source")), rs.getString("source_id"),
rs.getInt("usage_count"),
rs.getTimestamp("last_used_at") != null ? rs.getTimestamp("last_used_at").toInstant() : null,
rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant(),
rs.getTimestamp("updated_at").toInstant());
}

/**
 * 将 Set 序列化为 JSON 字符串。
 * @param set 集合
 * @return JSON 字符串
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private String toJson(Set<String> set) {
if (set == null || set.isEmpty()) {
return "[]";
}
try {
return this.objectMapper.writeValueAsString(set);
}
catch (JsonProcessingException ex) {
return "[]";
}
}

/**
 * 解析 JSON 字符串为 Set。
 * @param json JSON 字符串
 * @return 集合
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private Set<String> parseSet(String json) {
if (json == null || json.isBlank()) {
return Set.of();
}
try {
return this.objectMapper.readValue(json, SET_TYPE_REF);
}
catch (JsonProcessingException ex) {
return Set.of();
}
}

/**
 * 经验统计信息。
 *
 * @param totalCount 经验总数
 * @param enabledCount 已启用数量
 * @param totalUsageCount 累计使用次数
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public record ExperienceStats(int totalCount, int enabledCount, int totalUsageCount) {

}

}
