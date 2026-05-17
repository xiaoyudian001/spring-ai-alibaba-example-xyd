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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 客服长期记忆服务，按用户 ID 持久化渠道、最近商品、最近订单、意图和风险标记。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Service
public class CustomerMemoryService {

	private final ObjectMapper objectMapper;

	private final Path memoryFile;

	/**
	 * 创建客服长期记忆服务，并指定 JSON 持久化文件位置。
	 * @param objectMapper JSON 序列化工具
	 * @param memoryFile 客服记忆文件路径
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerMemoryService(ObjectMapper objectMapper,
			@Value("${minimax.customer.memory.file:memory/customer-memory.json}") String memoryFile) {
		this.objectMapper = objectMapper;
		this.memoryFile = Path.of(memoryFile);
	}

	/**
	 * 读取指定用户的客服长期记忆，不存在时返回默认记忆。
	 * @param userId 用户唯一标识
	 * @return 客服长期记忆
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public synchronized CustomerMemory read(String userId) {
		Map<String, CustomerMemory> memories = readAll();
		return memories.computeIfAbsent(normalizeUserId(userId), this::newMemory);
	}

	/**
	 * 根据本轮客服消息更新用户长期记忆，并写回 JSON 文件。
	 * @param userId 用户唯一标识
	 * @param channel 当前客服渠道
	 * @param message 用户原始输入
	 * @param intent 本轮客服意图
	 * @return 更新后的客服长期记忆
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public synchronized CustomerMemory update(String userId, ChannelType channel, String message,
			CustomerServiceIntent intent) {
		Map<String, CustomerMemory> memories = readAll();
		String safeUserId = normalizeUserId(userId);
		CustomerMemory memory = memories.computeIfAbsent(safeUserId, this::newMemory);
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
		memories.put(safeUserId, memory);
		writeAll(memories);
		return memory;
	}

	/**
	 * 清空指定用户的客服长期记忆，并写回 JSON 文件。
	 * @param userId 用户唯一标识
	 * @return 重置后的客服长期记忆
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public synchronized CustomerMemory clear(String userId) {
		Map<String, CustomerMemory> memories = readAll();
		CustomerMemory memory = newMemory(normalizeUserId(userId));
		memories.put(memory.getUserId(), memory);
		writeAll(memories);
		return memory;
	}

	/**
	 * 读取全部用户客服记忆，文件不存在或解析失败时返回空集合。
	 * @return 全部用户客服记忆
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private Map<String, CustomerMemory> readAll() {
		if (!Files.exists(this.memoryFile)) {
			return new LinkedHashMap<>();
		}
		try {
			return new LinkedHashMap<>(this.objectMapper.readValue(this.memoryFile.toFile(),
					new TypeReference<Map<String, CustomerMemory>>() {
					}));
		}
		catch (IOException ex) {
			return new LinkedHashMap<>();
		}
	}

	/**
	 * 把全部用户客服记忆写回 JSON 文件。
	 * @param memories 全部用户客服记忆
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private void writeAll(Map<String, CustomerMemory> memories) {
		try {
			Path parent = this.memoryFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.memoryFile.toFile(), memories);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write customer memory file: " + this.memoryFile, ex);
		}
	}

	/**
	 * 创建指定用户的默认客服记忆对象。
	 * @param userId 用户唯一标识
	 * @return 默认客服记忆
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private CustomerMemory newMemory(String userId) {
		CustomerMemory memory = new CustomerMemory();
		memory.setUserId(normalizeUserId(userId));
		return memory;
	}

	/**
	 * 从用户文本中识别商品号或订单号，并写入最近咨询列表。
	 * @param memory 客服长期记忆
	 * @param message 用户原始输入
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private void rememberDetectedIds(CustomerMemory memory, String message) {
		String text = message == null ? "" : message;
		for (String token : text.split("[\\s，。,.；;：:]+")) {
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
	 * 向列表中追加唯一值，避免重复记录同一商品、订单或风险标记。
	 * @param values 目标列表
	 * @param value 待追加值
	 * @author xyd
	 * @date 2026-05-15 14:57:11
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
	 * @date 2026-05-15 14:57:11
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
	 * @date 2026-05-15 14:57:11
	 */
	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

}
