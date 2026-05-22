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

/**
 * 客服事实包，承载后端在模型调用前主动预取的商品、订单、物流、售后和 RAG 知识事实。
 *
 * @param productId 本轮识别或回退使用的商品 ID
 * @param orderId 本轮识别或回退使用的订单 ID
 * @param productInfo 商品事实信息
 * @param orderInfo 订单事实信息
 * @param logisticsInfo 物流事实信息
 * @param pricePolicy 议价策略事实
 * @param refundEligibility 退款资格事实
 * @param afterSaleStatus 售后状态事实
 * @param ragSummary RAG 知识召回摘要
 * @param sourceNames 事实来源列表，例如 BACKEND_PRE_FETCH:getProductInfo
 * @param missingFacts 未能确定或缺失的事实说明
 * @author xyd
 * @date 2026-05-22 02:32:00
 */
public record CustomerFactBundle(String productId, String orderId, String productInfo, String orderInfo,
		String logisticsInfo, String pricePolicy, String refundEligibility, String afterSaleStatus, String ragSummary,
		List<String> sourceNames, List<String> missingFacts) {

	/**
	 * 创建空事实包，用于一般寒暄或无需业务事实的场景。
	 * @return 空事实包
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public static CustomerFactBundle empty() {
		return new CustomerFactBundle("", "", "", "", "", "", "", "", "", List.of(), List.of());
	}

	/**
	 * 判断本轮是否已经收集到任一业务事实。
	 * @return 是否存在业务事实
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public boolean hasFacts() {
		return hasText(this.productInfo) || hasText(this.orderInfo) || hasText(this.logisticsInfo)
				|| hasText(this.pricePolicy) || hasText(this.refundEligibility) || hasText(this.afterSaleStatus)
				|| hasText(this.ragSummary);
	}

	/**
	 * 生成适合放入模型提示词的事实摘要，要求模型优先依据这些事实回复。
	 * @return 模型提示词事实摘要
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public String summaryForPrompt() {
		if (!hasFacts()) {
			return this.missingFacts == null || this.missingFacts.isEmpty() ? "暂无后端预取事实。"
					: "暂无后端预取事实；缺失：" + String.join("；", this.missingFacts);
		}
		StringBuilder builder = new StringBuilder();
		append(builder, "商品事实", this.productInfo);
		append(builder, "订单事实", this.orderInfo);
		append(builder, "物流事实", this.logisticsInfo);
		append(builder, "议价策略", this.pricePolicy);
		append(builder, "退款资格", this.refundEligibility);
		append(builder, "售后状态", this.afterSaleStatus);
		append(builder, "RAG知识", this.ragSummary);
		if (this.sourceNames != null && !this.sourceNames.isEmpty()) {
			builder.append("\n事实来源：").append(String.join("、", this.sourceNames));
		}
		if (this.missingFacts != null && !this.missingFacts.isEmpty()) {
			builder.append("\n缺失事实：").append(String.join("；", this.missingFacts));
		}
		return builder.toString().trim();
	}

	private void append(StringBuilder builder, String title, String value) {
		if (hasText(value)) {
			builder.append("- ").append(title).append("：").append(value.trim()).append("\n");
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
