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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 客服短期会话上下文服务，启用 Redis 后按用户保存最近多轮对话，未启用或 Redis 不可用时回退到前端传入历史。
 *
 * @author xyd
 * @date 2026-05-20 15:10:00
 */
@Service
public class CustomerConversationContextService {

	private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

	private final ObjectMapper objectMapper;

	private final boolean redisEnabled;

	private final String redisPrefix;

	private final int maxMessages;

	private final long ttlSeconds;

	/**
	 * 创建客服短期上下文服务。
	 * @param redisTemplateProvider Redis 字符串模板提供器
	 * @param objectMapper JSON 序列化工具
	 * @param redisEnabled 是否启用 Redis 上下文
	 * @param redisPrefix Redis Key 前缀
	 * @param maxMessages 每个用户保留的最大短期消息数量
	 * @param ttlSeconds Redis 上下文过期秒数
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public CustomerConversationContextService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
			ObjectMapper objectMapper,
			@Value("${minimax.customer.context.redis-enabled:false}") boolean redisEnabled,
			@Value("${minimax.customer.context.redis-prefix:minimax:customer:conversation}") String redisPrefix,
			@Value("${minimax.customer.context.max-messages:20}") int maxMessages,
			@Value("${minimax.customer.context.redis-ttl-seconds:43200}") long ttlSeconds) {
		this.redisTemplateProvider = redisTemplateProvider;
		this.objectMapper = objectMapper;
		this.redisEnabled = redisEnabled;
		this.redisPrefix = redisPrefix == null || redisPrefix.isBlank()
				? "minimax:customer:conversation" : redisPrefix.trim();
		this.maxMessages = Math.max(4, maxMessages);
		this.ttlSeconds = Math.max(60, ttlSeconds);
	}

	/**
	 * 读取用户短期上下文；Redis 有数据时优先使用 Redis，否则使用前端传入历史。
	 * @param userId 用户唯一标识
	 * @param requestHistory 前端传入的历史消息
	 * @return 可用于模型提示词的短期上下文
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public List<CustomerConversationMessage> loadHistory(String userId,
			List<CustomerConversationMessage> requestHistory) {
		if (!this.redisEnabled) {
			return sanitize(requestHistory);
		}
		try {
			String value = redisTemplate().opsForValue().get(key(userId));
			if (value == null || value.isBlank()) {
				return sanitize(requestHistory);
			}
			List<CustomerConversationMessage> redisHistory = this.objectMapper.readValue(value,
					new TypeReference<List<CustomerConversationMessage>>() {
					});
			return limit(sanitize(redisHistory));
		}
		catch (Exception ex) {
			return sanitize(requestHistory);
		}
	}

	/**
	 * 在模型完成本轮回答后追加用户问题和助手回答，启用 Redis 时写回 Redis。
	 * @param userId 用户唯一标识
	 * @param previousHistory 本轮调用前使用的短期上下文
	 * @param userMessage 用户问题
	 * @param assistantMessage 助手回答
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public void appendTurn(String userId, List<CustomerConversationMessage> previousHistory, String userMessage,
			String assistantMessage) {
		if (!this.redisEnabled) {
			return;
		}
		List<CustomerConversationMessage> messages = new ArrayList<>(sanitize(previousHistory));
		if (userMessage != null && !userMessage.isBlank()) {
			messages.add(new CustomerConversationMessage("user", userMessage.trim()));
		}
		if (assistantMessage != null && !assistantMessage.isBlank()) {
			messages.add(new CustomerConversationMessage("assistant", assistantMessage.trim()));
		}
		write(userId, limit(messages));
	}

	/**
	 * 清空指定用户的 Redis 短期上下文。
	 * @param userId 用户唯一标识
	 * @return 是否删除成功
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public boolean clear(String userId) {
		if (!this.redisEnabled) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(redisTemplate().delete(key(userId)));
		}
		catch (Exception ex) {
			return false;
		}
	}

	/**
	 * 查看指定用户的 Redis 短期上下文内容和剩余 TTL，用于工作台区分短期上下文和长期 Memory。
	 * @param userId 用户唯一标识
	 * @return 用户短期上下文详情
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public CustomerConversationContextView view(String userId) {
		String redisKey = key(userId);
		if (!this.redisEnabled) {
			return new CustomerConversationContextView(normalizeUserId(userId), redisKey, false, false, 0, -1,
					List.of(), "Redis 短期上下文未启用");
		}
		try {
			String value = redisTemplate().opsForValue().get(redisKey);
			Long ttl = redisTemplate().getExpire(redisKey);
			if (value == null || value.isBlank()) {
				return new CustomerConversationContextView(normalizeUserId(userId), redisKey, true, true, 0,
						ttl == null ? -1 : ttl, List.of(), "Redis 中暂无该用户上下文");
			}
			List<CustomerConversationMessage> messages = this.objectMapper.readValue(value,
					new TypeReference<List<CustomerConversationMessage>>() {
					});
			List<CustomerConversationMessage> cleaned = sanitize(messages);
			return new CustomerConversationContextView(normalizeUserId(userId), redisKey, true, true, cleaned.size(),
					ttl == null ? -1 : ttl, cleaned, "Redis 短期上下文读取成功");
		}
		catch (Exception ex) {
			return new CustomerConversationContextView(normalizeUserId(userId), redisKey, true, false, 0, -1,
					List.of(), ex.getMessage());
		}
	}

	/**
	 * 查询短期上下文后端状态。
	 * @return Redis 上下文状态
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public CustomerConversationContextStatus status() {
		if (!this.redisEnabled) {
			return new CustomerConversationContextStatus(false, false, "REQUEST_HISTORY", this.redisPrefix,
					this.maxMessages, this.ttlSeconds, "Redis 短期上下文未启用");
		}
		try {
			redisTemplate().hasKey(this.redisPrefix + ":health-check");
			return new CustomerConversationContextStatus(true, true, "REDIS", this.redisPrefix, this.maxMessages,
					this.ttlSeconds, "Redis 短期上下文可用");
		}
		catch (Exception ex) {
			return new CustomerConversationContextStatus(true, false, "REQUEST_HISTORY_FALLBACK", this.redisPrefix,
					this.maxMessages, this.ttlSeconds, ex.getMessage());
		}
	}

	/**
	 * 写入 Redis 短期上下文。
	 * @param userId 用户唯一标识
	 * @param messages 待写入消息
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	private void write(String userId, List<CustomerConversationMessage> messages) {
		try {
			String payload = this.objectMapper.writeValueAsString(messages);
			redisTemplate().opsForValue().set(key(userId), payload, Duration.ofSeconds(this.ttlSeconds));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize customer conversation context", ex);
		}
		catch (Exception ex) {
			// Redis 是短期上下文增强能力，写入失败不影响主对话链路。
		}
	}

	/**
	 * 清洗历史消息，去除空消息和异常角色。
	 * @param messages 原始消息列表
	 * @return 清洗后的消息列表
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	private List<CustomerConversationMessage> sanitize(List<CustomerConversationMessage> messages) {
		if (messages == null || messages.isEmpty()) {
			return List.of();
		}
		List<CustomerConversationMessage> cleaned = new ArrayList<>();
		for (CustomerConversationMessage message : messages) {
			if (message == null || message.content() == null || message.content().isBlank()) {
				continue;
			}
			String role = "assistant".equalsIgnoreCase(message.role()) ? "assistant" : "user";
			cleaned.add(new CustomerConversationMessage(role, message.content().trim()));
		}
		return limit(cleaned);
	}

	/**
	 * 限制短期上下文消息数量。
	 * @param messages 原始消息列表
	 * @return 截断后的消息列表
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	private List<CustomerConversationMessage> limit(List<CustomerConversationMessage> messages) {
		if (messages == null || messages.size() <= this.maxMessages) {
			return messages == null ? List.of() : List.copyOf(messages);
		}
		return List.copyOf(messages.subList(messages.size() - this.maxMessages, messages.size()));
	}

	/**
	 * 获取 Redis 操作模板。
	 * @return Redis 字符串模板
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	private StringRedisTemplate redisTemplate() {
		return this.redisTemplateProvider.getObject();
	}

	/**
	 * 构建用户短期上下文 Redis Key。
	 * @param userId 用户唯一标识
	 * @return Redis Key
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	private String key(String userId) {
		return this.redisPrefix + ":" + normalizeUserId(userId);
	}

	/**
	 * 规范化用户 ID，保证 Redis Key 和响应结构使用同一规则。
	 * @param userId 原始用户 ID
	 * @return 规范化用户 ID
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	private String normalizeUserId(String userId) {
		return userId == null || userId.isBlank() ? "default-user" : userId.trim();
	}

	/**
	 * 客服短期上下文后端状态。
	 *
	 * @param redisEnabled 是否启用 Redis 上下文
	 * @param redisAvailable Redis 当前是否可用
	 * @param mode 当前短期上下文模式
	 * @param keyPrefix Redis Key 前缀
	 * @param maxMessages 最大消息数量
	 * @param ttlSeconds Redis 过期秒数
	 * @param message 状态说明
	 * @author xyd
	 * @date 2026-05-20 15:10:00
	 */
	public record CustomerConversationContextStatus(boolean redisEnabled, boolean redisAvailable, String mode,
			String keyPrefix, int maxMessages, long ttlSeconds, String message) {
	}

	/**
	 * 客服短期上下文详情，用于工作台查看 Redis 中某个用户最近多轮对话。
	 *
	 * @param userId 用户唯一标识
	 * @param redisKey Redis Key
	 * @param redisEnabled 是否启用 Redis 上下文
	 * @param redisAvailable Redis 当前是否可用
	 * @param messageCount 当前消息数量
	 * @param ttlSeconds Redis Key 剩余过期秒数
	 * @param messages 最近多轮上下文消息
	 * @param message 状态说明
	 * @author xyd
	 * @date 2026-05-21 12:20:00
	 */
	public record CustomerConversationContextView(String userId, String redisKey, boolean redisEnabled,
			boolean redisAvailable, int messageCount, long ttlSeconds, List<CustomerConversationMessage> messages,
			String message) {
	}

}
