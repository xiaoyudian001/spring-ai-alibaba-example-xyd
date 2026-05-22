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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * 客服事实收集服务，在大模型生成回答前先由后端确定性查询商品、订单、物流、售后和 RAG 知识。
 *
 * @author xyd
 * @date 2026-05-22 02:32:00
 */
@Service
public class CustomerFactCollectorService {

	private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("(?i)\\bp-[a-z0-9]+\\b");

	private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)\\bo-[a-z0-9]+\\b");

	private final CustomerMcpService customerMcpService;

	private final CustomerPolicyRagService policyRagService;

	/**
	 * 创建客服事实收集服务。
	 * @param customerMcpService 客服 MCP 门面服务
	 * @param policyRagService 客服 RAG 检索服务
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public CustomerFactCollectorService(CustomerMcpService customerMcpService,
			CustomerPolicyRagService policyRagService) {
		this.customerMcpService = customerMcpService;
		this.policyRagService = policyRagService;
	}

	/**
	 * 根据客服意图、用户问题和长期记忆收集本轮必需事实。
	 * @param intent 客服意图
	 * @param message 用户原始问题
	 * @param memory 客服长期记忆
	 * @return 本轮事实包
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public CustomerFactBundle collect(CustomerServiceIntent intent, String message, CustomerMemory memory) {
		CustomerServiceIntent safeIntent = intent == null ? CustomerServiceIntent.GENERAL_CHAT : intent;
		String safeMessage = message == null ? "" : message;
		String productId = resolveProductId(safeMessage, memory, needsProductFact(safeIntent, safeMessage));
		String orderId = resolveOrderId(safeMessage, memory, needsOrderFact(safeIntent, safeMessage));
		List<String> sources = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		String productInfo = "";
		String orderInfo = "";
		String logisticsInfo = "";
		String pricePolicy = "";
		String refundEligibility = "";
		String afterSaleStatus = "";
		String ragSummary = "";

		if (needsProductFact(safeIntent, safeMessage)) {
			if (hasText(productId)) {
				productInfo = this.customerMcpService.getProductInfo(productId);
				sources.add("BACKEND_PRE_FETCH:getProductInfo");
			}
			else {
				missing.add("缺少商品 ID，无法预取商品事实");
			}
		}
		if (needsPriceFact(safeIntent, safeMessage)) {
			if (hasText(productId)) {
				pricePolicy = this.customerMcpService.getPricePolicy(productId);
				sources.add("BACKEND_PRE_FETCH:getPricePolicy");
			}
			else {
				missing.add("缺少商品 ID，无法预取议价策略");
			}
		}
		if (needsOrderFact(safeIntent, safeMessage)) {
			if (hasText(orderId)) {
				orderInfo = this.customerMcpService.getOrderInfo(orderId);
				sources.add("BACKEND_PRE_FETCH:getOrderInfo");
			}
			else {
				missing.add("缺少订单 ID，无法预取订单事实");
			}
		}
		if (needsLogisticsFact(safeIntent, safeMessage)) {
			if (hasText(orderId)) {
				logisticsInfo = this.customerMcpService.getLogisticsInfo(orderId);
				sources.add("BACKEND_PRE_FETCH:getLogisticsInfo");
			}
			else {
				missing.add("缺少订单 ID，无法预取物流事实");
			}
		}
		if (needsRefundFact(safeIntent, safeMessage)) {
			if (hasText(orderId)) {
				refundEligibility = this.customerMcpService.getRefundEligibility(orderId);
				afterSaleStatus = this.customerMcpService.getAfterSaleStatus(orderId);
				sources.add("BACKEND_PRE_FETCH:getRefundEligibility");
				sources.add("BACKEND_PRE_FETCH:getAfterSaleStatus");
			}
			else {
				missing.add("缺少订单 ID，无法预取退款资格和售后状态");
			}
		}
		if (needsRagFact(safeIntent, safeMessage)) {
			CustomerPolicySearchResult result = this.policyRagService.searchWithMetrics(safeMessage, 4,
					expectedTopics(safeIntent));
			ragSummary = result.summary();
			sources.add("BACKEND_PRE_FETCH:searchCustomerPolicy");
		}
		return new CustomerFactBundle(nullToEmpty(productId), nullToEmpty(orderId), productInfo, orderInfo,
				logisticsInfo, pricePolicy, refundEligibility, afterSaleStatus, ragSummary, List.copyOf(sources),
				List.copyOf(missing));
	}

	private String resolveProductId(String message, CustomerMemory memory, boolean allowDefault) {
		String detected = firstMatch(PRODUCT_ID_PATTERN, message);
		if (hasText(detected)) {
			return detected;
		}
		if (memory != null && memory.getRecentProductIds() != null && !memory.getRecentProductIds().isEmpty()) {
			return memory.getRecentProductIds().get(0);
		}
		return allowDefault ? "p-1001" : "";
	}

	private String resolveOrderId(String message, CustomerMemory memory, boolean allowDefault) {
		String detected = firstMatch(ORDER_ID_PATTERN, message);
		if (hasText(detected)) {
			return detected;
		}
		if (memory != null && memory.getRecentOrderIds() != null && !memory.getRecentOrderIds().isEmpty()) {
			return memory.getRecentOrderIds().get(0);
		}
		return allowDefault ? "o-202605150001" : "";
	}

	private String firstMatch(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text == null ? "" : text);
		return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
	}

	private boolean needsProductFact(CustomerServiceIntent intent, String message) {
		return intent == CustomerServiceIntent.PRODUCT_INQUIRY || intent == CustomerServiceIntent.PRICE_NEGOTIATION
				|| hasText(firstMatch(PRODUCT_ID_PATTERN, message));
	}

	private boolean needsPriceFact(CustomerServiceIntent intent, String message) {
		String text = normalize(message);
		return intent == CustomerServiceIntent.PRICE_NEGOTIATION
				|| (hasText(firstMatch(PRODUCT_ID_PATTERN, message)) && containsAny(text, "便宜", "优惠", "包邮", "刀"));
	}

	private boolean needsOrderFact(CustomerServiceIntent intent, String message) {
		return intent == CustomerServiceIntent.ORDER_STATUS || intent == CustomerServiceIntent.LOGISTICS_QUERY
				|| intent == CustomerServiceIntent.REFUND_REQUEST || hasText(firstMatch(ORDER_ID_PATTERN, message));
	}

	private boolean needsLogisticsFact(CustomerServiceIntent intent, String message) {
		String text = normalize(message);
		return intent == CustomerServiceIntent.LOGISTICS_QUERY
				|| (hasText(firstMatch(ORDER_ID_PATTERN, message)) && containsAny(text, "物流", "快递", "发货", "签收"));
	}

	private boolean needsRefundFact(CustomerServiceIntent intent, String message) {
		String text = normalize(message);
		return intent == CustomerServiceIntent.REFUND_REQUEST
				|| (hasText(firstMatch(ORDER_ID_PATTERN, message)) && containsAny(text, "退款", "退货", "售后", "取消"));
	}

	private boolean needsRagFact(CustomerServiceIntent intent, String message) {
		String text = normalize(message);
		return intent == CustomerServiceIntent.RETURN_POLICY || intent == CustomerServiceIntent.REFUND_REQUEST
				|| intent == CustomerServiceIntent.COMPLAINT || intent == CustomerServiceIntent.HUMAN_HANDOFF
				|| containsAny(text, "政策", "规则", "投诉", "七天", "7天", "售后");
	}

	private Set<String> expectedTopics(CustomerServiceIntent intent) {
		return switch (intent) {
			case REFUND_REQUEST, RETURN_POLICY -> Set.of("refund");
			case LOGISTICS_QUERY -> Set.of("shipping");
			case PRICE_NEGOTIATION -> Set.of("price", "xianyu");
			case COMPLAINT, HUMAN_HANDOFF -> Set.of("complaint");
			default -> Set.of();
		};
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}

	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
