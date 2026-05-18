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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 智能客服 MCP 门面服务，优先调用真实 MCP 工具，失败时回退到本地 Mock 客服业务数据。
 *
 * @author xyd
 * @date 2026-05-17 10:43:52
 */
@Service
public class CustomerMcpService {

	private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;

	private final ObjectMapper objectMapper;

	private final MockCustomerDataService mockCustomerDataService;

	private final ThreadLocal<McpDebugInfo> debugInfoHolder = ThreadLocal.withInitial(McpDebugInfo::none);

	/**
	 * 创建智能客服 MCP 门面服务。
	 * @param toolCallbackProvider Spring AI MCP ToolCallbackProvider
	 * @param objectMapper JSON 序列化工具
	 * @param mockCustomerDataService Mock 客服数据服务
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public CustomerMcpService(ObjectProvider<ToolCallbackProvider> toolCallbackProvider, ObjectMapper objectMapper,
			MockCustomerDataService mockCustomerDataService) {
		this.toolCallbackProvider = toolCallbackProvider;
		this.objectMapper = objectMapper;
		this.mockCustomerDataService = mockCustomerDataService;
	}

	/**
	 * 查询商品信息，优先调用真实 MCP 商品工具，失败时回退到 Mock 商品数据。
	 * @param productId 商品 ID
	 * @return 商品信息文本
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public String getProductInfo(String productId) {
		return invokeOrFallback("getProductInfo", Map.of("productId", safeText(productId)),
				() -> this.mockCustomerDataService.getProductInfo(productId));
	}

	/**
	 * 查询订单信息，优先调用真实 MCP 订单工具，失败时回退到 Mock 订单数据。
	 * @param orderId 订单 ID
	 * @return 订单信息文本
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public String getOrderInfo(String orderId) {
		return invokeOrFallback("getOrderInfo", Map.of("orderId", safeText(orderId)),
				() -> this.mockCustomerDataService.getOrderInfo(orderId));
	}

	/**
	 * 查询物流信息，优先调用真实 MCP 物流工具，失败时回退到 Mock 物流数据。
	 * @param orderId 订单 ID
	 * @return 物流信息文本
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public String getLogisticsInfo(String orderId) {
		return invokeOrFallback("getLogisticsInfo", Map.of("orderId", safeText(orderId)),
				() -> this.mockCustomerDataService.getLogisticsInfo(orderId));
	}

	/**
	 * 创建客服工单，优先调用真实 MCP 工单工具，失败时回退到 Mock 工单结果。
	 * @param conversationId 会话 ID
	 * @param summary 工单摘要
	 * @return 工单创建结果
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public String createTicket(String conversationId, String summary) {
		return invokeOrFallback("createCustomerTicket",
				Map.of("conversationId", safeText(conversationId), "summary", safeText(summary)),
				() -> this.mockCustomerDataService.createTicket(conversationId, summary));
	}

	/**
	 * 生成人工接管请求，优先调用真实 MCP 人工接管工具，失败时生成本地待处理提示。
	 * @param conversationId 会话 ID
	 * @param reason 人工接管原因
	 * @return 人工接管结果
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public String requestHumanHandoff(String conversationId, String reason) {
		return invokeOrFallback("requestHumanHandoff",
				Map.of("conversationId", safeText(conversationId), "reason", safeText(reason)),
				() -> "已生成待人工确认任务：handoff-" + Math.abs((safeText(conversationId) + safeText(reason)).hashCode())
						+ "；原因：" + reason + "。模型不得直接执行退款、赔付、取消订单等高风险动作。");
	}

	/**
	 * 获取智能客服 MCP 当前状态，用于判断是否已经发现真实 MCP 工具。
	 * @return 智能客服 MCP 状态
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public CustomerMcpStatus status() {
		List<String> toolNames = availableToolNames();
		List<String> customerToolNames = toolNames.stream()
				.filter(this::isCustomerToolName)
				.toList();
		return new CustomerMcpStatus(!customerToolNames.isEmpty(), customerToolNames.size(), customerToolNames,
				customerToolNames.isEmpty() ? "CUSTOMER_MOCK_FALLBACK" : "REAL_CUSTOMER_MCP_READY");
	}

	/**
	 * 获取本轮客服 MCP 调试信息。
	 * @return MCP 调试信息
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public McpDebugInfo snapshotDebugInfo() {
		return this.debugInfoHolder.get();
	}

	/**
	 * 清理当前线程的客服 MCP 调试信息。
	 *
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public void clearDebugInfo() {
		this.debugInfoHolder.remove();
	}

	/**
	 * 调用指定客服 MCP 工具，真实调用失败时执行 fallback。
	 * @param logicalToolName 逻辑工具名
	 * @param arguments 工具参数
	 * @param fallback Mock 兜底逻辑
	 * @return 工具调用结果
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private String invokeOrFallback(String logicalToolName, Map<String, Object> arguments, Fallback fallback) {
		List<String> toolNames = availableToolNames();
		Optional<ToolCallback> callback = selectCustomerTool(logicalToolName);
		if (callback.isPresent()) {
			try {
				String content = callback.get().call(this.objectMapper.writeValueAsString(arguments));
				if (content != null && !content.isBlank()) {
					recordDebug("REAL_CUSTOMER_MCP", true, callback.get().getToolDefinition().name(), toolNames, "",
							logicalToolName);
					return content;
				}
				return fallback(logicalToolName, toolNames, "真实 MCP 返回空内容", fallback);
			}
			catch (JsonProcessingException ex) {
				return fallback(logicalToolName, toolNames, "MCP 参数序列化失败：" + ex.getMessage(), fallback);
			}
			catch (Exception ex) {
				return fallback(logicalToolName, toolNames, "真实 MCP 调用失败：" + ex.getMessage(), fallback);
			}
		}
		return fallback(logicalToolName, toolNames, "未发现匹配的智能客服 MCP 工具", fallback);
	}

	/**
	 * 执行 Mock 兜底逻辑，并记录 MCP fallback 调试信息。
	 * @param logicalToolName 逻辑工具名
	 * @param toolNames 当前可用 MCP 工具名
	 * @param fallbackReason 兜底原因
	 * @param fallback Mock 兜底逻辑
	 * @return Mock 工具结果
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private String fallback(String logicalToolName, List<String> toolNames, String fallbackReason, Fallback fallback) {
		recordDebug("CUSTOMER_MOCK_FALLBACK", false, "", toolNames, fallbackReason, logicalToolName);
		return fallback.get();
	}

	/**
	 * 按逻辑工具名选择真实 MCP 工具。
	 * @param logicalToolName 逻辑工具名
	 * @return 真实 MCP 工具回调
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private Optional<ToolCallback> selectCustomerTool(String logicalToolName) {
		List<String> aliases = aliases(logicalToolName);
		return Arrays.stream(toolCallbacks())
				.filter(callback -> {
					String name = normalize(callback.getToolDefinition().name());
					String description = normalize(callback.getToolDefinition().description());
					return aliases.stream().anyMatch(alias -> name.contains(alias) || description.contains(alias));
				})
				.findFirst();
	}

	/**
	 * 根据逻辑工具名生成可兼容真实 MCP Server 的候选工具关键词。
	 * @param logicalToolName 逻辑工具名
	 * @return 工具关键词列表
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private List<String> aliases(String logicalToolName) {
		String name = normalize(logicalToolName);
		if (name.contains("product")) {
			return List.of("getproductinfo", "getproduct", "product", "商品");
		}
		if (name.contains("logistics")) {
			return List.of("getlogisticsinfo", "getlogistics", "logistics", "shipping", "物流");
		}
		if (name.contains("order")) {
			return List.of("getorderinfo", "getorder", "order", "订单");
		}
		if (name.contains("ticket")) {
			return List.of("createcustomerticket", "createticket", "ticket", "工单");
		}
		if (name.contains("handoff")) {
			return List.of("requesthumanhandoff", "humanhandoff", "handoff", "人工");
		}
		return List.of(name);
	}

	/**
	 * 获取 Spring AI 当前发现的所有 MCP ToolCallback。
	 * @return MCP ToolCallback 数组
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private ToolCallback[] toolCallbacks() {
		return this.toolCallbackProvider.orderedStream()
				.flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
				.toArray(ToolCallback[]::new);
	}

	/**
	 * 获取当前可用 MCP 工具名列表。
	 * @return MCP 工具名列表
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private List<String> availableToolNames() {
		return Arrays.stream(toolCallbacks())
				.map(callback -> callback.getToolDefinition().name())
				.toList();
	}

	/**
	 * 判断工具名是否属于智能客服工具。
	 * @param toolName 工具名
	 * @return 是否为智能客服工具
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private boolean isCustomerToolName(String toolName) {
		String name = normalize(toolName);
		return name.contains("product") || name.contains("order") || name.contains("logistics")
				|| name.contains("ticket") || name.contains("handoff") || name.contains("customer");
	}

	/**
	 * 记录客服 MCP 调试信息，供前端调试区展示调用来源。
	 * @param mode MCP 模式
	 * @param realMcpAvailable 真实 MCP 是否可用
	 * @param selectedToolName 选中的 MCP 工具名
	 * @param availableToolNames 可用 MCP 工具名
	 * @param fallbackReason 兜底原因
	 * @param query 本轮逻辑工具名
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private void recordDebug(String mode, boolean realMcpAvailable, String selectedToolName,
			List<String> availableToolNames, String fallbackReason, String query) {
		this.debugInfoHolder.set(new McpDebugInfo(mode, realMcpAvailable, selectedToolName, availableToolNames,
				fallbackReason, query, null, false, "disabled", null));
	}

	/**
	 * 把工具名规范化为小写无空白字符串。
	 * @param value 原始工具名
	 * @return 规范化工具名
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
	}

	/**
	 * 将驼峰逻辑工具名转换成下划线风格，兼容 MCP Server 暴露的工具名。
	 * @param value 驼峰工具名
	 * @return 下划线工具名
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	/**
	 * 空字符串安全处理。
	 * @param value 原始文本
	 * @return 安全文本
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	private String safeText(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * Mock 兜底逻辑接口，避免为每个工具重复写 try/catch 和调试记录。
	 *
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	@FunctionalInterface
	private interface Fallback {

		/**
		 * 执行 Mock 兜底逻辑。
		 * @return Mock 结果
		 * @author xyd
		 * @date 2026-05-17 10:43:52
		 */
		String get();

	}

	/**
	 * 智能客服 MCP 状态，用于前端或接口测试判断当前是否接入真实 MCP。
	 *
	 * @param realMcpAvailable 是否发现真实 MCP 客服工具
	 * @param toolCount 客服 MCP 工具数量
	 * @param toolNames 客服 MCP 工具名列表
	 * @param mode MCP 模式
	 * @author xyd
	 * @date 2026-05-17 10:43:52
	 */
	public record CustomerMcpStatus(boolean realMcpAvailable, int toolCount, List<String> toolNames, String mode) {
	}

}
