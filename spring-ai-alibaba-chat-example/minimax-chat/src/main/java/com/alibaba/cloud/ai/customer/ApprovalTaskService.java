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
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 高风险动作审核任务服务，统一创建、查询和更新客服待审核任务。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
@Service
public class ApprovalTaskService {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	/**
	 * 创建审核任务服务。
	 * @param jdbcTemplate 数据库访问模板
	 * @param objectMapper JSON 序列化工具
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public ApprovalTaskService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 初始化审核任务表。
	 *
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@PostConstruct
	public void initializeSchema() {
		this.jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS customer_approval_task (
					id VARCHAR(128) PRIMARY KEY,
					action_type VARCHAR(64) NOT NULL,
					conversation_id VARCHAR(160) NOT NULL,
					status VARCHAR(32) NOT NULL,
					payload TEXT NOT NULL,
					created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
					updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				)
				""");
	}

	/**
	 * 创建一条待审核任务，高风险动作只记录诉求，不直接执行真实系统操作。
	 * @param actionType 高风险动作类型
	 * @param conversationId 会话或用户标识
	 * @param reason 创建审核任务的原因
	 * @return 新建的待审核任务
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public synchronized PendingApprovalTask create(String actionType, String conversationId, String reason) {
		Instant now = Instant.now();
		PendingApprovalTask task = new PendingApprovalTask("approval-" + UUID.randomUUID(), safe(actionType),
				safe(conversationId), safe(reason), ApprovalTaskStatus.PENDING, now, now);
		write(task);
		return task;
	}

	/**
	 * 查询最近的审核任务，用于工作台查看待人工处理的高风险动作。
	 * @param limit 最大返回数量
	 * @return 审核任务列表
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public List<PendingApprovalTask> latest(int limit) {
		int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
		return this.jdbcTemplate.queryForList(
				"SELECT payload FROM customer_approval_task ORDER BY created_at DESC LIMIT ?", String.class, safeLimit)
				.stream()
				.map(this::readTask)
				.toList();
	}

	/**
	 * 更新审核任务状态，供工作台人工通过或拒绝。
	 * @param id 审核任务唯一标识
	 * @param status 目标状态
	 * @return 更新后的审核任务
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public synchronized PendingApprovalTask updateStatus(String id, ApprovalTaskStatus status) {
		String safeId = safe(id);
		List<String> rows = this.jdbcTemplate.queryForList(
				"SELECT payload FROM customer_approval_task WHERE id = ?", String.class, safeId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("审核任务不存在：" + safeId);
		}
		PendingApprovalTask old = readTask(rows.get(0));
		PendingApprovalTask updated = new PendingApprovalTask(old.id(), old.actionType(), old.conversationId(),
				old.reason(), status == null ? ApprovalTaskStatus.PENDING : status, old.createdAt(), Instant.now());
		write(updated);
		return updated;
	}

	/**
	 * 把审核任务写入数据库。
	 * @param task 审核任务
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void write(PendingApprovalTask task) {
		try {
			String payload = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
			int updated = this.jdbcTemplate.update("""
					UPDATE customer_approval_task
					SET action_type = ?, conversation_id = ?, status = ?, payload = ?, updated_at = ?
					WHERE id = ?
					""", task.actionType(), task.conversationId(), task.status().name(), payload, task.updatedAt(),
					task.id());
			if (updated == 0) {
				this.jdbcTemplate.update("""
						INSERT INTO customer_approval_task
						(id, action_type, conversation_id, status, payload, created_at, updated_at)
						VALUES (?, ?, ?, ?, ?, ?, ?)
						""", task.id(), task.actionType(), task.conversationId(), task.status().name(), payload,
						task.createdAt(), task.updatedAt());
			}
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize approval task: " + task.id(), ex);
		}
	}

	/**
	 * 从数据库 JSON 载荷恢复审核任务。
	 * @param payload 数据库存储的 JSON 载荷
	 * @return 审核任务
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private PendingApprovalTask readTask(String payload) {
		try {
			return this.objectMapper.readValue(payload, PendingApprovalTask.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize approval task", ex);
		}
	}

	/**
	 * 安全文本处理。
	 * @param value 原始文本
	 * @return 非空文本
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private String safe(String value) {
		return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
	}

}
