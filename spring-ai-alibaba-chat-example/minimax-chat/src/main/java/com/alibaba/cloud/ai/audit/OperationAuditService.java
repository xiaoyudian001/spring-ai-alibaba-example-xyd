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

package com.alibaba.cloud.ai.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 操作审计服务，持久化工作台 Memory、RAG、报告、审核任务等关键操作。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
@Service
public class OperationAuditService {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	/**
	 * 创建操作审计服务。
	 * @param jdbcTemplate 数据库访问模板
	 * @param objectMapper JSON 序列化工具
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public OperationAuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 初始化审计事件表。
	 *
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@PostConstruct
	public void initializeSchema() {
		this.jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS operation_audit_event (
					id VARCHAR(128) PRIMARY KEY,
					user_id VARCHAR(128),
					action VARCHAR(128) NOT NULL,
					target VARCHAR(256),
					success BOOLEAN NOT NULL,
					payload TEXT NOT NULL,
					created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				)
				""");
	}

	/**
	 * 记录一条操作审计事件。
	 * @param userId 用户 ID
	 * @param action 操作名称
	 * @param target 操作目标
	 * @param detail 操作详情
	 * @param success 是否成功
	 * @return 已保存的审计事件
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public OperationAuditEvent record(String userId, String action, String target, String detail, boolean success) {
		OperationAuditEvent event = new OperationAuditEvent("audit-" + UUID.randomUUID(), safe(userId), safe(action),
				safe(target), safe(detail), success, Instant.now());
		write(event);
		return event;
	}

	/**
	 * 查询最近的审计事件。
	 * @param limit 最大返回数量
	 * @return 审计事件列表
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public List<OperationAuditEvent> latest(int limit) {
		int safeLimit = limit <= 0 ? 30 : Math.min(limit, 100);
		return this.jdbcTemplate.queryForList(
				"SELECT payload FROM operation_audit_event ORDER BY created_at DESC LIMIT ?", String.class, safeLimit)
				.stream()
				.map(this::readEvent)
				.toList();
	}

	/**
	 * 把审计事件写入数据库。
	 * @param event 审计事件
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void write(OperationAuditEvent event) {
		try {
			String payload = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
			this.jdbcTemplate.update("""
					INSERT INTO operation_audit_event (id, user_id, action, target, success, payload, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", event.id(), event.userId(), event.action(), event.target(), event.success(), payload,
					event.createdAt());
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize audit event: " + event.id(), ex);
		}
	}

	/**
	 * 从数据库 JSON 载荷恢复审计事件。
	 * @param payload 数据库存储的 JSON 载荷
	 * @return 审计事件
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private OperationAuditEvent readEvent(String payload) {
		try {
			return this.objectMapper.readValue(payload, OperationAuditEvent.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize audit event", ex);
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
		return value == null ? "" : value.trim();
	}

}
