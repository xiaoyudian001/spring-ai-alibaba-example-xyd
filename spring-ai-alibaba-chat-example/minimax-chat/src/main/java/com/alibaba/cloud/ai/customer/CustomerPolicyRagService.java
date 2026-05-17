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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * 客服知识库检索服务，第一阶段使用本地规则文档模拟 RAG，后续可替换为 PGVector 或 Milvus。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Service
public class CustomerPolicyRagService {

	private final Map<String, String> documents = new LinkedHashMap<>();

	/**
	 * 初始化客服政策和话术知识，保证不接向量库时也能验证 RAG 在链路中的位置。
	 *
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerPolicyRagService() {
		this.documents.put("refund-policy",
				"退款政策：签收 7 天内且商品不影响二次销售时，可引导用户申请退货退款；超过 7 天需人工审核；涉及赔偿或直接退款必须人工确认。");
		this.documents.put("shipping-policy",
				"发货政策：已付款订单默认 48 小时内发货；若已发货，应先查询物流；若待发货，可说明预计发货时间并创建提醒。");
		this.documents.put("xianyu-reply-guide",
				"闲鱼回复规范：回复要短、自然、像真人；可以说“还在的”“可以小刀”；不要承诺无法确认的信息；涉及退款、赔付、取消订单时转人工确认。");
		this.documents.put("wechat-service-guide",
				"微信客服规范：回复要完整、礼貌、可追踪；需要保留订单号和工单号；复杂售后建议创建工单并告知用户处理时效。");
		this.documents.put("complaint-handling",
				"投诉处理规范：先表达理解和歉意，再复述问题，随后给出可执行处理动作；高风险投诉需要创建工单并转人工跟进。");
	}

	/**
	 * 根据用户问题检索客服政策、平台规则或话术知识。
	 * @param query 用户问题或检索关键词
	 * @param limit 返回结果数量
	 * @return 检索结果摘要
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String search(String query, Integer limit) {
		String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
		int max = limit == null || limit <= 0 ? 3 : Math.min(limit, 5);
		StringBuilder builder = new StringBuilder();
		int count = 0;
		for (Map.Entry<String, String> entry : this.documents.entrySet()) {
			if (matches(text, entry.getKey(), entry.getValue())) {
				builder.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
				count++;
			}
			if (count >= max) {
				break;
			}
		}
		if (count == 0) {
			return "未命中明确客服知识，建议补充商品说明、售后政策或渠道回复规范。";
		}
		return builder.toString().trim();
	}

	/**
	 * 判断检索问题是否命中指定知识文档。
	 * @param query 检索问题
	 * @param id 文档 ID
	 * @param content 文档内容
	 * @return 是否命中
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private boolean matches(String query, String id, String content) {
		String haystack = (id + " " + content).toLowerCase(Locale.ROOT);
		return query.isBlank() || query.contains("退") && id.contains("refund")
				|| query.contains("发货") && id.contains("shipping") || query.contains("物流") && id.contains("shipping")
				|| query.contains("闲鱼") && id.contains("xianyu") || query.contains("微信") && id.contains("wechat")
				|| query.contains("投诉") && id.contains("complaint") || haystack.contains(query);
	}

}
