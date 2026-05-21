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
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 客服长期记忆服务，使用数据库表持久化用户画像、最近商品、最近订单、意图和风险标记。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
@Service
public class CustomerMemoryService {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	/**
	 * 创建基于数据库的客服 Memory 服务。
	 * @param jdbcTemplate 数据库访问模板
	 * @param objectMapper JSON 序列化工具
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public CustomerMemoryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 初始化客服 Memory 表，确保应用启动后不再依赖 JSON 文件保存长期记忆。
	 *
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@PostConstruct
	public void initializeSchema() {
		this.jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS customer_memory (
					user_id VARCHAR(128) PRIMARY KEY,
					payload TEXT NOT NULL,
					updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				)
				""");
	}

	/**
	 * 读取指定用户的客服长期记忆；不存在时返回默认记忆，但不会立即写库。
	 * @param userId 用户唯一标识
	 * @return 客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public synchronized CustomerMemory read(String userId) {
		String safeUserId = normalizeUserId(userId);
		List<String> rows = this.jdbcTemplate.queryForList(
				"SELECT payload FROM customer_memory WHERE user_id = ?", String.class, safeUserId);
		if (rows.isEmpty()) {
			return newMemory(safeUserId);
		}
		try {
			CustomerMemory memory = this.objectMapper.readValue(rows.get(0), CustomerMemory.class);
			memory.setUserId(safeUserId);
			ensureCollections(memory);
			return memory;
		}
		catch (JsonProcessingException ex) {
			return newMemory(safeUserId);
		}
	}

	/**
	 * 根据本轮客服对话更新用户长期记忆，并写入数据库。
	 * @param userId 用户唯一标识
	 * @param channel 当前客服渠道
	 * @param message 用户原始输入
	 * @param intent 本轮客服意图
	 * @return 更新后的客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public synchronized CustomerMemory update(String userId, ChannelType channel, String message,
			CustomerServiceIntent intent) {
		CustomerMemory memory = read(userId);
		memory.setChannel(channel == null ? ChannelType.WEB : channel);
		memory.setLastIntent(intent == null ? CustomerServiceIntent.GENERAL_CHAT : intent);
		memory.setLastQuestion(message == null ? "" : message);
		memory.setConversationCount(memory.getConversationCount() + 1);
		rememberDetectedIds(memory, message);
		if (intent == CustomerServiceIntent.COMPLAINT) {
			addUnique(memory.getRiskFlags(), "complaint");
		}
		if (intent == CustomerServiceIntent.REFUND_REQUEST) {
			addUnique(memory.getRiskFlags(), "refund_request");
		}
		save(memory);
		return memory;
	}

	/**
	 * 直接保存页面编辑后的客服 Memory，用于工作台可视化修正用户画像。
	 * @param userId 用户唯一标识
	 * @param memory 页面提交的客服长期记忆
	 * @return 保存后的客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public synchronized CustomerMemory save(String userId, CustomerMemory memory) {
		CustomerMemory safeMemory = memory == null ? newMemory(userId) : memory;
		safeMemory.setUserId(normalizeUserId(userId));
		ensureCollections(safeMemory);
		save(safeMemory);
		return safeMemory;
	}

	/**
	 * 清空指定用户的客服长期记忆，并把重置后的默认记忆写入数据库。
	 * @param userId 用户唯一标识
	 * @return 重置后的客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public synchronized CustomerMemory clear(String userId) {
		CustomerMemory memory = newMemory(normalizeUserId(userId));
		save(memory);
		return memory;
	}

	/**
	 * 返回当前 Memory 后端类型，方便工作台确认是否仍依赖 JSON。
	 * @return Memory 后端类型
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public String backend() {
		return "MYSQL_DATABASE";
	}

	/**
	 * 把客服 Memory 序列化后写入数据库，使用 upsert 保持同一用户只有一条长期记忆。
	 * @param memory 客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void save(CustomerMemory memory) {
		try {
			String payload = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(memory);
			int updated = this.jdbcTemplate.update(
					"UPDATE customer_memory SET payload = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
					payload, memory.getUserId());
			if (updated == 0) {
				this.jdbcTemplate.update(
						"INSERT INTO customer_memory (user_id, payload, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
						memory.getUserId(), payload);
			}
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize customer memory: " + memory.getUserId(), ex);
		}
	}

	/**
	 * 创建指定用户的默认客服记忆对象。
	 * @param userId 用户唯一标识
	 * @return 默认客服记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private CustomerMemory newMemory(String userId) {
		CustomerMemory memory = new CustomerMemory();
		memory.setUserId(normalizeUserId(userId));
		ensureCollections(memory);
		return memory;
	}

	/**
	 * 从用户文本中识别商品号或订单号，并写入最近咨询列表。
	 * @param memory 客服长期记忆
	 * @param message 用户原始输入
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void rememberDetectedIds(CustomerMemory memory, String message) {
		String text = message == null ? "" : message;
		for (String token : text.split("[\\s,，。；;！!？?]+")) {
			if (token.startsWith("p-") || token.startsWith("P-")) {
				addUnique(memory.getRecentProductIds(), token.toLowerCase());
			}
			if (token.startsWith("o-") || token.startsWith("O-")) {
				addUnique(memory.getRecentOrderIds(), token.toLowerCase());
			}
		}
		memory.setRecentProductIds(limit(memory.getRecentProductIds(), 5));
		memory.setRecentOrderIds(limit(memory.getRecentOrderIds(), 5));
	}

	/**
	 * 向列表头部追加唯一值，避免重复记录同一商品、订单或风险标记。
	 * @param values 目标列表
	 * @param value 待追加值
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void addUnique(List<String> values, String value) {
		if (value != null && !value.isBlank() && !values.contains(value)) {
			values.add(0, value);
		}
	}

	/**
	 * 限制列表最大长度，避免长期记忆无限膨胀。
	 * @param values 原始列表
	 * @param maxSize 最大长度
	 * @return 截断后的列表
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private List<String> limit(List<String> values, int maxSize) {
		if (values == null || values.size() <= maxSize) {
			return values == null ? new ArrayList<>() : values;
		}
		return new ArrayList<>(values.subList(0, maxSize));
	}

	/**
	 * 规范化用户 ID，空值统一映射为 default-user。
	 * @param userId 原始用户 ID
	 * @return 规范化用户 ID
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

	/**
	 * 补齐反序列化后可能为空的集合字段，避免后续更新记忆时报空指针。
	 * @param memory 客服长期记忆
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void ensureCollections(CustomerMemory memory) {
		if (memory.getRecentProductIds() == null) {
			memory.setRecentProductIds(new ArrayList<>());
		}
		if (memory.getRecentOrderIds() == null) {
			memory.setRecentOrderIds(new ArrayList<>());
		}
		if (memory.getRiskFlags() == null) {
			memory.setRiskFlags(new ArrayList<>());
		}
		if (memory.getChannel() == null) {
			memory.setChannel(ChannelType.WEB);
		}
		if (memory.getLastIntent() == null) {
			memory.setLastIntent(CustomerServiceIntent.GENERAL_CHAT);
		}
	}

}
