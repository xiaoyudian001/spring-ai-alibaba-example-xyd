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

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 客服意图规划器，基于关键词先提供确定性意图，后续可替换为模型分类或专门 Planner Agent。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Component
public class CustomerServiceIntentPlanner {

	/**
	 * 根据用户输入识别客服业务意图，并驱动后续 Tool、RAG、Skill 和 Workflow 策略。
	 * @param message 用户原始输入
	 * @return 客服业务意图
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerServiceIntent plan(String message) {
		String text = normalize(message);
		if (containsAny(text, "人工", "客服", "真人", "转人工")) {
			return CustomerServiceIntent.HUMAN_HANDOFF;
		}
		if (containsAny(text, "投诉", "差评", "太差", "生气", "举报")) {
			return CustomerServiceIntent.COMPLAINT;
		}
		if (containsAny(text, "退款", "退货", "售后", "取消订单", "赔偿")) {
			return CustomerServiceIntent.REFUND_REQUEST;
		}
		if (containsAny(text, "7天", "七天", "能退", "退换", "政策", "规则", "运费谁")) {
			return CustomerServiceIntent.RETURN_POLICY;
		}
		if (containsAny(text, "物流", "快递", "到哪", "没到", "签收", "发货", "多久到")) {
			return CustomerServiceIntent.LOGISTICS_QUERY;
		}
		if (containsAny(text, "订单", "支付", "状态", "处理到哪")) {
			return CustomerServiceIntent.ORDER_STATUS;
		}
		if (containsAny(text, "便宜", "优惠", "包邮", "少点", "降价", "刀", "议价")) {
			return CustomerServiceIntent.PRICE_NEGOTIATION;
		}
		if (containsAny(text, "商品", "还在", "有货", "成色", "规格", "尺寸", "颜色", "价格")) {
			return CustomerServiceIntent.PRODUCT_INQUIRY;
		}
		return CustomerServiceIntent.GENERAL_CHAT;
	}

	/**
	 * 根据客服意图生成给模型的策略提示，约束模型何时使用 Tool、RAG、Skill 或人工确认。
	 * @param intent 客服业务意图
	 * @return 策略提示
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String instructionFor(CustomerServiceIntent intent) {
		return switch (intent) {
			case PRODUCT_INQUIRY -> "本轮是商品咨询，优先读取闲鱼或客服回复 Skill，必要时调用 getProductInfo 查询商品事实。";
			case PRICE_NEGOTIATION -> "本轮是议价咨询，优先读取 price-negotiation Skill，不能承诺超出底价策略的优惠。";
			case ORDER_STATUS -> "本轮是订单状态查询，优先调用 getOrderInfo 获取实时订单事实。";
			case LOGISTICS_QUERY -> "本轮是物流查询，优先调用 getOrderInfo 和 getLogisticsInfo 获取订单与物流事实。";
			case REFUND_REQUEST -> "本轮是退款请求，优先查询订单并检索退款政策，涉及真实退款动作时必须生成人工确认建议。";
			case RETURN_POLICY -> "本轮是退换货政策咨询，优先调用 searchCustomerPolicy 检索政策知识库。";
			case COMPLAINT -> "本轮是投诉咨询，优先读取 complaint-handling Skill，语气要安抚，并建议创建工单或转人工。";
			case HUMAN_HANDOFF -> "本轮需要人工接管，优先调用 requestHumanHandoff 生成人工处理任务。";
			case GENERAL_CHAT -> "本轮是一般客服对话，保持简洁友好，必要时主动询问商品号或订单号。";
		};
	}

	/**
	 * 规范化用户输入，便于关键词匹配。
	 * @param message 用户原始输入
	 * @return 规范化文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private String normalize(String message) {
		return message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}

	/**
	 * 判断文本是否包含任一关键词。
	 * @param text 待检测文本
	 * @param keywords 关键词列表
	 * @return 是否命中关键词
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

}
