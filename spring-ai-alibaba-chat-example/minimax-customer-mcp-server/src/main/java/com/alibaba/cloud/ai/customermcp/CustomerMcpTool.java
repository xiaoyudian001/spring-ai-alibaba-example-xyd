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

package com.alibaba.cloud.ai.customermcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 智能客服 MCP 工具实现，模拟真实商品、订单、物流、售后和工单系统的工具入口。
 *
 * @author xyd
 * @date 2026-05-22 02:32:00
 */
@Service
public class CustomerMcpTool {

	private final Map<String, String> products = new LinkedHashMap<>();

	private final Map<String, String> orders = new LinkedHashMap<>();

	private final Map<String, String> logistics = new LinkedHashMap<>();

	private final Map<String, String> pricePolicies = new LinkedHashMap<>();

	private final Map<String, String> refundPolicies = new LinkedHashMap<>();

	private final Map<String, String> afterSaleStatuses = new LinkedHashMap<>();

	/**
	 * 初始化智能客服 MCP 测试数据，便于本地无需真实闲鱼或订单系统即可验证链路。
	 *
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public CustomerMcpTool() {
		this.products.put("p-1001", "商品ID：p-1001；标题：九成新机械键盘；状态：在售；售价：199 元；说明：青轴，九成新，键帽轻微使用痕迹，功能正常。");
		this.products.put("p-1002", "商品ID：p-1002；标题：二手 Java 进阶书籍套装；状态：在售；售价：88 元；说明：包含 Spring、JVM、并发编程书籍。");
		this.orders.put("o-202605150001",
				"订单ID：o-202605150001；商品ID：p-1001；用户：default-user；状态：已发货；实付：199 元；支付时间：2026-05-14 21:30:00；发货时间：2026-05-15 10:20:00。");
		this.orders.put("o-202605150002",
				"订单ID：o-202605150002；商品ID：p-1002；用户：user-a；状态：待发货；实付：88 元；支付时间：2026-05-15 09:10:00；发货时间：暂未发货。");
		this.logistics.put("o-202605150001",
				"订单ID：o-202605150001；快递：顺丰速运；单号：SF123456789CN；状态：运输中；最新动态：2026-05-15 13:40:00 包裹已到达上海转运中心。");
		this.pricePolicies.put("p-1001", "商品ID：p-1001；当前售价：199 元；底价：170 元；策略：可小刀，低于底价需礼貌拒绝，不默认包邮。");
		this.pricePolicies.put("p-1002", "商品ID：p-1002；当前售价：88 元；底价：75 元；策略：可让利 5 到 13 元，优先建议用户直接拍下。");
		this.refundPolicies.put("o-202605150001", "订单ID：o-202605150001；退款资格：已发货订单需结合签收状态和商品问题判断，建议先查看物流并按平台售后流程处理。");
		this.refundPolicies.put("o-202605150002", "订单ID：o-202605150002；退款资格：待发货订单可引导用户申请退款，卖家确认后处理。");
		this.afterSaleStatuses.put("o-202605150001", "订单ID：o-202605150001；售后状态：暂无进行中的售后单；如用户明确申请，可创建客服工单。");
		this.afterSaleStatuses.put("o-202605150002", "订单ID：o-202605150002；售后状态：暂无进行中的售后单；当前处于待发货阶段。");
	}

	/**
	 * 查询商品详情，适合回答商品是否在售、成色、价格和基础介绍。
	 * @param productId 商品 ID，例如 p-1001
	 * @return 商品详情文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "查询智能客服商品详情。适合回答商品是否在售、商品成色、价格、描述和基础介绍。")
	public String getProductInfo(@ToolParam(description = "商品 ID，例如 p-1001。") String productId) {
		return find(this.products, productId, "p-1001", "未找到商品：%s。请提示用户提供正确商品编号。");
	}

	/**
	 * 查询订单详情，适合回答订单状态、支付金额、支付时间和发货时间。
	 * @param orderId 订单 ID，例如 o-202605150001
	 * @return 订单详情文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "查询智能客服订单详情。适合回答订单状态、支付金额、支付时间和发货时间。")
	public String getOrderInfo(@ToolParam(description = "订单 ID，例如 o-202605150001。") String orderId) {
		return find(this.orders, orderId, "o-202605150001", "未找到订单：%s。请提示用户提供正确订单编号。");
	}

	/**
	 * 查询物流详情，适合回答快递公司、运单号、运输状态和最新物流动态。
	 * @param orderId 订单 ID，例如 o-202605150001
	 * @return 物流详情文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "查询智能客服物流详情。适合回答快递公司、运单号、运输状态和最新物流动态。")
	public String getLogisticsInfo(@ToolParam(description = "订单 ID，例如 o-202605150001。") String orderId) {
		return find(this.logistics, orderId, "o-202605150001", "该订单暂未产生物流信息：%s。");
	}

	/**
	 * 查询议价策略，适合判断用户报价是否可以接受以及如何回复议价请求。
	 * @param productId 商品 ID，例如 p-1001
	 * @return 议价策略文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "查询智能客服商品议价策略。适合判断用户报价是否可以接受，以及如何回复议价请求。")
	public String getPricePolicy(@ToolParam(description = "商品 ID，例如 p-1001。") String productId) {
		return find(this.pricePolicies, productId, "p-1001", "未找到商品议价策略：%s。");
	}

	/**
	 * 查询退款资格，适合回答用户是否可以退款、退货和售后处理建议。
	 * @param orderId 订单 ID，例如 o-202605150001
	 * @return 退款资格文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "查询智能客服订单退款资格。适合回答用户是否可以退款、退货和售后处理建议。")
	public String getRefundEligibility(@ToolParam(description = "订单 ID，例如 o-202605150001。") String orderId) {
		return find(this.refundPolicies, orderId, "o-202605150001", "未找到订单退款资格：%s。");
	}

	/**
	 * 查询售后状态，适合回答售后单是否存在、是否处理中和下一步处理方式。
	 * @param orderId 订单 ID，例如 o-202605150001
	 * @return 售后状态文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "查询智能客服售后处理状态。适合回答售后单是否存在、是否处理中和下一步处理方式。")
	public String getAfterSaleStatus(@ToolParam(description = "订单 ID，例如 o-202605150001。") String orderId) {
		return find(this.afterSaleStatuses, orderId, "o-202605150001", "未找到售后状态：%s。");
	}

	/**
	 * 创建客服工单，适合记录复杂咨询、售后投诉和需要人工处理的问题。
	 * @param conversationId 会话 ID
	 * @param summary 工单摘要
	 * @return 工单创建结果
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "创建智能客服工单。适合记录复杂咨询、售后投诉和需要人工处理的问题。")
	public String createCustomerTicket(@ToolParam(description = "会话 ID。") String conversationId,
			@ToolParam(description = "工单摘要。") String summary) {
		String ticketId = "ticket-" + Math.abs((safe(conversationId, "conversation") + safe(summary, "")).hashCode());
		return "已创建真实 MCP 客服工单：" + ticketId + "；摘要：" + safe(summary, "用户需要客服跟进") + "；创建时间："
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	/**
	 * 请求人工接管，适合处理高风险退款、投诉升级、赔付和模型不能直接执行的动作。
	 * @param conversationId 会话 ID
	 * @param reason 人工接管原因
	 * @return 人工接管请求结果
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Tool(description = "请求人工客服接管。适合处理高风险退款、投诉升级、赔付和模型不能直接执行的动作。")
	public String requestHumanHandoff(@ToolParam(description = "会话 ID。") String conversationId,
			@ToolParam(description = "人工接管原因。") String reason) {
		String handoffId = "handoff-" + Math.abs((safe(conversationId, "conversation") + safe(reason, "")).hashCode());
		return "已创建真实 MCP 人工接管请求：" + handoffId + "；原因：" + safe(reason, "用户需要人工处理") + "；状态：待客服确认。";
	}

	/**
	 * 从指定数据表中查询业务文本，空 ID 时使用默认测试数据。
	 * @param data 数据表
	 * @param key 查询 ID
	 * @param defaultKey 默认测试 ID
	 * @param missingTemplate 未命中时的提示模板
	 * @return 业务事实文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	private String find(Map<String, String> data, String key, String defaultKey, String missingTemplate) {
		String normalized = safe(key, defaultKey).toLowerCase();
		return data.getOrDefault(normalized, missingTemplate.formatted(key));
	}

	/**
	 * 规范化文本，空值时返回默认值。
	 * @param value 原始文本
	 * @param defaultValue 默认文本
	 * @return 可安全使用的文本
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	private String safe(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

}
