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
import java.util.Map;

import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 智能客服模型可调用工具入口，负责暴露商品、订单、物流、RAG、Skills 和人工接管能力。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Component
public class CustomerServiceTools {

	private final CustomerMcpService customerMcpService;

	private final CustomerPolicyRagService policyRagService;

	private final CustomerSkillService skillService;

	private final ToolCallDebugRecorder debugRecorder;

	/**
	 * 创建智能客服工具入口，并注入 Mock 数据、客服 RAG、客服 Skills 和调试记录器。
	 * @param customerMcpService 智能客服 MCP 门面服务
	 * @param policyRagService 客服政策 RAG 服务
	 * @param skillService 客服技能服务
	 * @param debugRecorder 工具调用调试记录器
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerServiceTools(CustomerMcpService customerMcpService, CustomerPolicyRagService policyRagService,
			CustomerSkillService skillService, ToolCallDebugRecorder debugRecorder) {
		this.customerMcpService = customerMcpService;
		this.policyRagService = policyRagService;
		this.skillService = skillService;
		this.debugRecorder = debugRecorder;
	}

	/**
	 * 查询商品信息，适合商品咨询、闲鱼议价和售前问答。
	 * @param productId 商品 ID，例如 p-1001
	 * @return 商品信息
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "查询商品信息。当用户询问商品是否还在、价格、成色、库存、闲鱼议价时使用。")
	public String getProductInfo(@ToolParam(description = "商品 ID，例如 p-1001。用户没有提供时可使用 p-1001 测试。") String productId) {
		String result = this.customerMcpService.getProductInfo(productId);
		this.debugRecorder.record("getProductInfo", arguments("productId", productId), result);
		return result;
	}

	/**
	 * 查询订单信息，适合订单状态、售后和退款判断。
	 * @param orderId 订单 ID，例如 o-202605150001
	 * @return 订单信息
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "查询订单信息。当用户询问订单状态、是否发货、退款条件或售后处理时使用。")
	public String getOrderInfo(
			@ToolParam(description = "订单 ID，例如 o-202605150001。用户没有提供时可使用 o-202605150001 测试。") String orderId) {
		String result = this.customerMcpService.getOrderInfo(orderId);
		this.debugRecorder.record("getOrderInfo", arguments("orderId", orderId), result);
		return result;
	}

	/**
	 * 查询物流信息，适合用户询问快递、发货和包裹位置。
	 * @param orderId 订单 ID，例如 o-202605150001
	 * @return 物流信息
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "查询物流信息。当用户询问快递、包裹到哪、为什么没到或是否签收时使用。")
	public String getLogisticsInfo(
			@ToolParam(description = "订单 ID，例如 o-202605150001。用户没有提供时可使用 o-202605150001 测试。") String orderId) {
		String result = this.customerMcpService.getLogisticsInfo(orderId);
		this.debugRecorder.record("getLogisticsInfo", arguments("orderId", orderId), result);
		return result;
	}

	/**
	 * 检索客服政策和话术知识，适合退换货、发货、投诉和渠道规范问答。
	 * @param query 检索问题或关键词
	 * @param limit 返回结果数量
	 * @return 客服知识检索结果
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "检索客服知识库。当用户询问退款政策、发货规则、闲鱼回复规范、微信客服规范或投诉处理时使用。")
	public String searchCustomerPolicy(
			@ToolParam(description = "检索问题或关键词，例如 退款政策、闲鱼回复、微信客服、投诉处理。") String query,
			@ToolParam(description = "返回结果数量，建议 1 到 5。") Integer limit) {
		String result = this.policyRagService.search(query, limit);
		this.debugRecorder.record("searchCustomerPolicy", arguments("query", query, "limit", limit), result);
		return result;
	}

	/**
	 * 列出客服技能索引，模拟 SkillRegistry 暴露给 Agent 的技能列表。
	 * @return 客服技能列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "列出智能客服 Skills。当模型需要选择闲鱼回复、微信客服、议价、退款或投诉技能时使用。")
	public String listCustomerSkills() {
		String result = this.skillService.listSkills();
		this.debugRecorder.record("listCustomerSkills", arguments(), result);
		return result;
	}

	/**
	 * 读取指定客服技能内容，模拟 read_skill(skill_name) 的渐进式披露。
	 * @param skillName 技能名称
	 * @return 完整技能内容
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "读取智能客服 Skill 完整内容。当模型已判断需要某个技能时使用，相当于 read_skill(skill_name)。")
	public String readCustomerSkill(
			@ToolParam(description = "技能名称，例如 xianyu-reply、wechat-service、refund-handling、price-negotiation。") String skillName) {
		String result = this.skillService.readSkill(skillName);
		this.debugRecorder.record("readCustomerSkill", arguments("skillName", skillName), result);
		return result;
	}

	/**
	 * 创建客服工单，适合投诉、复杂售后或需要后续人工跟进的场景。
	 * @param conversationId 会话 ID
	 * @param summary 工单摘要
	 * @return 工单创建结果
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "创建客服工单。当用户投诉、问题复杂、需要后续跟进或需要沉淀处理记录时使用。")
	public String createCustomerTicket(
			@ToolParam(description = "会话 ID，没有时可使用当前用户 ID。") String conversationId,
			@ToolParam(description = "工单摘要，说明用户问题和建议处理动作。") String summary) {
		String result = this.customerMcpService.createTicket(conversationId, summary);
		this.debugRecorder.record("createCustomerTicket", arguments("conversationId", conversationId, "summary", summary),
				result);
		return result;
	}

	/**
	 * 生成人工接管请求，高风险客服动作必须通过该工具生成待处理任务。
	 * @param conversationId 会话 ID
	 * @param reason 人工接管原因
	 * @return 人工接管结果
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	@Tool(description = "请求人工接管。当用户要求人工客服，或涉及退款、赔偿、取消订单、投诉升级等高风险动作时使用。")
	public String requestHumanHandoff(
			@ToolParam(description = "会话 ID，没有时可使用当前用户 ID。") String conversationId,
			@ToolParam(description = "人工接管原因。") String reason) {
		String result = this.customerMcpService.requestHumanHandoff(conversationId, reason);
		this.debugRecorder.record("requestHumanHandoff",
				arguments("conversationId", conversationId, "reason", reason), result);
		return result;
	}

	/**
	 * 构造工具调试参数，便于前端展示本轮工具调用。
	 * @param pairs 参数键值对
	 * @return 参数 Map
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private Map<String, Object> arguments(Object... pairs) {
		Map<String, Object> arguments = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			arguments.put(String.valueOf(pairs[i]), pairs[i + 1] == null ? "" : pairs[i + 1]);
		}
		return arguments;
	}

}
