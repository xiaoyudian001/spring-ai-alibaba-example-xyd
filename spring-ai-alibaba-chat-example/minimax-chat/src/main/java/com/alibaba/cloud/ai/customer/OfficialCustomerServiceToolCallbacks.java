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

import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * 智能客服官方 Agent Framework 工具适配器，将客服业务工具包装成 Spring AI ToolCallback。
 *
 * @author xyd
 * @date 2026-05-18 11:34:38
 */
@Component
public class OfficialCustomerServiceToolCallbacks {

	private final CustomerServiceTools customerServiceTools;

	/**
	 * 创建智能客服官方工具适配器。
	 * @param customerServiceTools 客服业务工具入口
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	public OfficialCustomerServiceToolCallbacks(CustomerServiceTools customerServiceTools) {
		this.customerServiceTools = customerServiceTools;
	}

	/**
	 * 返回智能客服官方 ReactAgent 可用的全部 ToolCallback。
	 * @return ToolCallback 数组
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	public ToolCallback[] all() {
		return new ToolCallback[] { productInfo(), orderInfo(), logisticsInfo(), pricePolicy(), refundEligibility(),
				afterSaleStatus(), customerPolicy(), customerPolicyRecall(), customerSkills(), customerSkillRead(),
				customerTicket(), humanHandoff() };
	}

	/**
	 * 构建商品信息查询工具。
	 * @return 商品信息查询工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback productInfo() {
		Function<ProductInfoRequest, String> function = request -> this.customerServiceTools
				.getProductInfo(request.productId());
		return FunctionToolCallback.builder("getProductInfo", function)
				.description("查询商品信息。当用户询问商品是否还在、价格、成色、库存、闲鱼议价时使用。")
				.inputType(ProductInfoRequest.class)
				.build();
	}

	/**
	 * 构建订单信息查询工具。
	 * @return 订单信息查询工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback orderInfo() {
		Function<OrderInfoRequest, String> function = request -> this.customerServiceTools
				.getOrderInfo(request.orderId());
		return FunctionToolCallback.builder("getOrderInfo", function)
				.description("查询订单信息。当用户询问订单状态、是否发货、退款条件或售后处理时使用。")
				.inputType(OrderInfoRequest.class)
				.build();
	}

	/**
	 * 构建物流信息查询工具。
	 * @return 物流信息查询工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback logisticsInfo() {
		Function<LogisticsInfoRequest, String> function = request -> this.customerServiceTools
				.getLogisticsInfo(request.orderId());
		return FunctionToolCallback.builder("getLogisticsInfo", function)
				.description("查询物流信息。当用户询问快递、包裹到哪、为什么没到或是否签收时使用。")
				.inputType(LogisticsInfoRequest.class)
				.build();
	}

	/**
	 * 构建商品议价策略查询工具。
	 * @return 商品议价策略查询工具
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private ToolCallback pricePolicy() {
		Function<ProductInfoRequest, String> function = request -> this.customerServiceTools
				.getPricePolicy(request.productId());
		return FunctionToolCallback.builder("getPricePolicy", function)
				.description("查询商品议价策略。当用户要求便宜、优惠、包邮、小刀或砍价时使用。")
				.inputType(ProductInfoRequest.class)
				.build();
	}

	/**
	 * 构建订单退款资格查询工具。
	 * @return 订单退款资格查询工具
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private ToolCallback refundEligibility() {
		Function<OrderInfoRequest, String> function = request -> this.customerServiceTools
				.getRefundEligibility(request.orderId());
		return FunctionToolCallback.builder("getRefundEligibility", function)
				.description("查询订单退款资格。当用户申请退款、退货、取消订单或询问售后条件时使用。")
				.inputType(OrderInfoRequest.class)
				.build();
	}

	/**
	 * 构建售后处理状态查询工具。
	 * @return 售后处理状态查询工具
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private ToolCallback afterSaleStatus() {
		Function<OrderInfoRequest, String> function = request -> this.customerServiceTools
				.getAfterSaleStatus(request.orderId());
		return FunctionToolCallback.builder("getAfterSaleStatus", function)
				.description("查询售后处理状态。当用户追问退款进度、投诉处理或售后工单状态时使用。")
				.inputType(OrderInfoRequest.class)
				.build();
	}

	/**
	 * 构建客服政策检索工具。
	 * @return 客服政策检索工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback customerPolicy() {
		Function<CustomerPolicyRequest, String> function = request -> this.customerServiceTools
				.searchCustomerPolicy(request.query(), request.limit());
		return FunctionToolCallback.builder("searchCustomerPolicy", function)
				.description("检索客服知识库。当用户询问退款政策、发货规则、闲鱼回复规范、微信客服规范或投诉处理时使用。")
				.inputType(CustomerPolicyRequest.class)
				.build();
	}

	/**
	 * 构建客服 RAG 召回率评估工具。
	 * @return 客服 RAG 召回率评估工具
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private ToolCallback customerPolicyRecall() {
		Function<CustomerPolicyRecallRequest, String> function = request -> this.customerServiceTools
				.evaluateCustomerPolicyRecall(request.query(), request.expectedTopics(), request.limit());
		return FunctionToolCallback.builder("evaluateCustomerPolicyRecall", function)
				.description("检索客服知识并输出召回率。当需要评估 RAG 命中主题、召回率或真实向量库状态时使用。")
				.inputType(CustomerPolicyRecallRequest.class)
				.build();
	}

	/**
	 * 构建客服技能列表工具。
	 * @return 客服技能列表工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback customerSkills() {
		Function<ListCustomerSkillsRequest, String> function = request -> this.customerServiceTools
				.listCustomerSkills();
		return FunctionToolCallback.builder("listCustomerSkills", function)
				.description("列出智能客服 Skills。当模型需要选择闲鱼回复、微信客服、议价、退款或投诉技能时使用。")
				.inputType(ListCustomerSkillsRequest.class)
				.build();
	}

	/**
	 * 构建客服技能读取工具。
	 * @return 客服技能读取工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback customerSkillRead() {
		Function<ReadCustomerSkillRequest, String> function = request -> this.customerServiceTools
				.readCustomerSkill(request.skillName());
		return FunctionToolCallback.builder("readCustomerSkill", function)
				.description("读取智能客服 Skill 完整内容。当模型已判断需要某个技能时使用，相当于 read_skill(skill_name)。")
				.inputType(ReadCustomerSkillRequest.class)
				.build();
	}

	/**
	 * 构建客服工单创建工具。
	 * @return 客服工单创建工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback customerTicket() {
		Function<CreateCustomerTicketRequest, String> function = request -> this.customerServiceTools
				.createCustomerTicket(request.conversationId(), request.summary());
		return FunctionToolCallback.builder("createCustomerTicket", function)
				.description("创建客服工单。当用户投诉、问题复杂、需要后续跟进或需要沉淀处理记录时使用。")
				.inputType(CreateCustomerTicketRequest.class)
				.build();
	}

	/**
	 * 构建人工接管工具。
	 * @return 人工接管工具
	 * @author xyd
	 * @date 2026-05-18 11:34:38
	 */
	private ToolCallback humanHandoff() {
		Function<HumanHandoffRequest, String> function = request -> this.customerServiceTools
				.requestHumanHandoff(request.conversationId(), request.reason());
		return FunctionToolCallback.builder("requestHumanHandoff", function)
				.description("请求人工接管。当用户要求人工客服，或涉及退款、赔偿、取消订单、投诉升级等高风险动作时使用。")
				.inputType(HumanHandoffRequest.class)
				.build();
	}

	@JsonClassDescription("查询商品信息的请求参数")
	/**
	 * 查询商品信息工具的结构化入参。
	 *
	 * @param productId 商品 ID
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record ProductInfoRequest(
			@JsonProperty(value = "productId", required = true)
			@JsonPropertyDescription("商品 ID，例如 p-1001。") String productId) {
	}

	@JsonClassDescription("查询订单信息的请求参数")
	/**
	 * 查询订单信息和退款资格工具的结构化入参。
	 *
	 * @param orderId 订单 ID
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record OrderInfoRequest(
			@JsonProperty(value = "orderId", required = true)
			@JsonPropertyDescription("订单 ID，例如 o-202605150001。") String orderId) {
	}

	@JsonClassDescription("查询物流信息的请求参数")
	/**
	 * 查询物流信息工具的结构化入参。
	 *
	 * @param orderId 订单 ID
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record LogisticsInfoRequest(
			@JsonProperty(value = "orderId", required = true)
			@JsonPropertyDescription("订单 ID，例如 o-202605150001。") String orderId) {
	}

	@JsonClassDescription("检索客服知识库的请求参数")
	/**
	 * 检索客服知识库工具的结构化入参。
	 *
	 * @param query 检索问题或关键词
	 * @param limit 返回结果数量
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record CustomerPolicyRequest(
			@JsonProperty(value = "query", required = true)
			@JsonPropertyDescription("检索问题或关键词，例如 退款政策、闲鱼回复、微信客服、投诉处理。") String query,
			@JsonProperty(value = "limit")
			@JsonPropertyDescription("返回结果数量，建议 1 到 5。") Integer limit) {
	}

	@JsonClassDescription("评估客服 RAG 召回率的请求参数")
	/**
	 * 评估客服 RAG 召回率工具的结构化入参。
	 *
	 * @param query 检索问题或关键词
	 * @param expectedTopics 期望命中的主题
	 * @param limit 返回结果数量
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record CustomerPolicyRecallRequest(
			@JsonProperty(value = "query", required = true)
			@JsonPropertyDescription("检索问题或关键词，例如 超过 7 天能退吗、物流怎么还没到。") String query,
			@JsonProperty(value = "expectedTopics")
			@JsonPropertyDescription("期望命中的主题，逗号分隔，例如 refund,shipping,price,xianyu,wechat。") String expectedTopics,
			@JsonProperty(value = "limit")
			@JsonPropertyDescription("返回结果数量，建议 1 到 8。") Integer limit) {
	}

	@JsonClassDescription("列出智能客服技能的请求参数")
	/**
	 * 列出智能客服技能工具的结构化入参。
	 *
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record ListCustomerSkillsRequest() {
	}

	@JsonClassDescription("读取智能客服技能的请求参数")
	/**
	 * 读取智能客服技能工具的结构化入参。
	 *
	 * @param skillName 技能名称
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record ReadCustomerSkillRequest(
			@JsonProperty(value = "skillName", required = true)
			@JsonPropertyDescription("技能名称，例如 xianyu-reply、wechat-service、refund-handling。") String skillName) {
	}

	@JsonClassDescription("创建客服工单的请求参数")
	/**
	 * 创建客服工单工具的结构化入参。
	 *
	 * @param conversationId 会话 ID
	 * @param summary 工单摘要
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record CreateCustomerTicketRequest(
			@JsonProperty(value = "conversationId", required = true)
			@JsonPropertyDescription("会话 ID，没有时可使用当前用户 ID。") String conversationId,
			@JsonProperty(value = "summary", required = true)
			@JsonPropertyDescription("工单摘要，说明用户问题和建议处理动作。") String summary) {
	}

	@JsonClassDescription("请求人工接管的请求参数")
	/**
	 * 请求人工接管工具的结构化入参。
	 *
	 * @param conversationId 会话 ID
	 * @param reason 人工接管原因
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public record HumanHandoffRequest(
			@JsonProperty(value = "conversationId", required = true)
			@JsonPropertyDescription("会话 ID，没有时可使用当前用户 ID。") String conversationId,
			@JsonProperty(value = "reason", required = true)
			@JsonPropertyDescription("人工接管原因。") String reason) {
	}

}
