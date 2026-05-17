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

/**
 * 智能客服业务意图，用于驱动 Tool、RAG、Skills、Workflow 和 Multi-Agent 的后续策略选择。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public enum CustomerServiceIntent {

	/**
	 * 商品咨询，用户询问商品是否还在、规格、价格、成色、库存等售前问题。
	 */
	PRODUCT_INQUIRY,

	/**
	 * 议价咨询，用户表达降价、优惠、包邮或价格协商诉求。
	 */
	PRICE_NEGOTIATION,

	/**
	 * 订单状态查询，用户询问订单是否创建、是否支付、是否发货或当前处理状态。
	 */
	ORDER_STATUS,

	/**
	 * 物流查询，用户询问快递、发货时间、签收状态或包裹延迟原因。
	 */
	LOGISTICS_QUERY,

	/**
	 * 退款请求，用户明确表达退款、退货、取消订单或售后处理诉求。
	 */
	REFUND_REQUEST,

	/**
	 * 退换货政策咨询，用户询问是否支持退货、超过期限能否退、运费由谁承担等规则问题。
	 */
	RETURN_POLICY,

	/**
	 * 投诉咨询，用户表达强烈不满、投诉、差评威胁或需要升级处理。
	 */
	COMPLAINT,

	/**
	 * 人工接管，用户明确要求人工客服，或当前问题涉及高风险动作需要人工确认。
	 */
	HUMAN_HANDOFF,

	/**
	 * 一般对话，用户问题暂未命中明确客服业务意图时使用。
	 */
	GENERAL_CHAT

}
