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
import java.util.Map;

import com.alibaba.cloud.ai.customer.CustomerConversationContextService.CustomerConversationContextStatus;
import com.alibaba.cloud.ai.customer.CustomerMcpService.CustomerMcpStatus;
import com.alibaba.cloud.ai.customer.CustomerPolicyRagService.CustomerPolicyRagStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 智能客服存储管理服务，汇总 MySQL 表状态，并提供本地测试数据清理能力。
 *
 * @author xyd
 * @date 2026-05-21 12:20:00
 */
@Service
public class CustomerStorageAdminService {

	private static final List<String> BUSINESS_TABLES = List.of("customer_memory", "customer_approval_task",
			"operation_audit_event");

	private final JdbcTemplate jdbcTemplate;

	private final CustomerMemoryService memoryService;

	private final CustomerConversationContextService contextService;

	private final CustomerPolicyRagService ragService;

	private final CustomerMcpService mcpService;

	/**
	 * 创建智能客服存储管理服务。
	 * @param jdbcTemplate 数据库访问模板
	 * @param memoryService 客服长期记忆服务
	 * @param contextService Redis 短期上下文服务
	 * @param ragService 客服 RAG 服务
	 * @param mcpService 客服 MCP 门面服务
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public CustomerStorageAdminService(JdbcTemplate jdbcTemplate, CustomerMemoryService memoryService,
			CustomerConversationContextService contextService, CustomerPolicyRagService ragService,
			CustomerMcpService mcpService) {
		this.jdbcTemplate = jdbcTemplate;
		this.memoryService = memoryService;
		this.contextService = contextService;
		this.ragService = ragService;
		this.mcpService = mcpService;
	}

	/**
	 * 汇总 MySQL、Redis、RAG 和 MCP 的关键运行状态，作为工作台和自动化测试的统一健康检查入口。
	 * @return 存储与外部能力状态
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public CustomerStorageStatus status() {
		boolean mysqlAvailable = mysqlAvailable();
		CustomerConversationContextStatus contextStatus = this.contextService.status();
		CustomerPolicyRagStatus ragStatus = this.ragService.status();
		CustomerMcpStatus mcpStatus = this.mcpService.status();
		return new CustomerStorageStatus(mysqlAvailable, contextStatus.redisAvailable(), this.memoryService.backend(),
				contextStatus.mode(), ragStatus.mode(), mcpStatus.mode(), mysqlAvailable ? "MySQL 可用" : "MySQL 不可用");
	}

	/**
	 * 查询智能客服核心 MySQL 表概览，包含记录数、最近更新时间和最近 5 条数据。
	 * @return MySQL 表概览列表
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public List<CustomerMysqlTableOverview> tables() {
		return BUSINESS_TABLES.stream().map(this::tableOverview).toList();
	}

	/**
	 * 清理本地联调用测试数据，仅删除带测试用户前缀或测试标识的数据，不清空真实业务表。
	 * @return 清理结果
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public CustomerStorageCleanupResult clearTestData() {
		int memoryDeleted = this.jdbcTemplate.update("""
				DELETE FROM customer_memory
				WHERE user_id LIKE 'test-%'
				   OR user_id LIKE 'test_%'
				   OR user_id IN ('direct-user', 'agent-user', 'xianyu-user')
				""");
		int approvalDeleted = this.jdbcTemplate.update("""
				DELETE FROM customer_approval_task
				WHERE conversation_id LIKE 'test-%'
				   OR conversation_id LIKE 'test_%'
				   OR conversation_id IN ('direct-user', 'agent-user', 'xianyu-user', 'conversation-1')
				""");
		int auditDeleted = this.jdbcTemplate.update("""
				DELETE FROM operation_audit_event
				WHERE user_id LIKE 'test-%'
				   OR user_id LIKE 'test_%'
				   OR user_id IN ('direct-user', 'agent-user', 'xianyu-user', 'dashboard')
				""");
		return new CustomerStorageCleanupResult(memoryDeleted, approvalDeleted, auditDeleted,
				memoryDeleted + approvalDeleted + auditDeleted);
	}

	/**
	 * 检查 MySQL 是否可执行基础查询。
	 * @return MySQL 可用返回 true
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	private boolean mysqlAvailable() {
		try {
			Integer value = this.jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return Integer.valueOf(1).equals(value);
		}
		catch (Exception ex) {
			return false;
		}
	}

	/**
	 * 查询单张业务表的状态概览。
	 * @param tableName 表名
	 * @return 表状态概览
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	private CustomerMysqlTableOverview tableOverview(String tableName) {
		long count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
		String timeColumn = "operation_audit_event".equals(tableName) ? "created_at" : "updated_at";
		Object latestTime = this.jdbcTemplate.queryForObject("SELECT MAX(" + timeColumn + ") FROM " + tableName,
				Object.class);
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
				"SELECT * FROM " + tableName + " ORDER BY " + timeColumn + " DESC LIMIT 5");
		return new CustomerMysqlTableOverview(tableName, count, latestTime == null ? "" : String.valueOf(latestTime),
				rows);
	}

	/**
	 * 统一存储健康检查响应。
	 *
	 * @param mysqlAvailable MySQL 是否可用
	 * @param redisAvailable Redis 是否可用
	 * @param memoryBackend Memory 后端类型
	 * @param contextMode 短期上下文模式
	 * @param ragMode RAG 模式
	 * @param mcpMode MCP 模式
	 * @param message 状态说明
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public record CustomerStorageStatus(boolean mysqlAvailable, boolean redisAvailable, String memoryBackend,
			String contextMode, String ragMode, String mcpMode, String message) {
	}

	/**
	 * MySQL 表概览响应。
	 *
	 * @param tableName 表名
	 * @param recordCount 记录数
	 * @param latestUpdatedAt 最近更新时间
	 * @param latestRows 最近 5 条数据
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public record CustomerMysqlTableOverview(String tableName, long recordCount, String latestUpdatedAt,
			List<Map<String, Object>> latestRows) {
	}

	/**
	 * 本地测试数据清理结果。
	 *
	 * @param memoryDeleted Memory 删除数量
	 * @param approvalDeleted 审核任务删除数量
	 * @param auditDeleted 审计事件删除数量
	 * @param totalDeleted 总删除数量
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public record CustomerStorageCleanupResult(int memoryDeleted, int approvalDeleted, int auditDeleted,
			int totalDeleted) {
	}

}
