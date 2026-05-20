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
 * 客服 Skills 服务，模拟 SkillRegistry 和 read_skill 能力，后续可替换为官方 SkillsInterceptor。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Service
public class CustomerSkillService {

	private final Map<String, String> skills = new LinkedHashMap<>();

	/**
	 * 初始化客服技能内容，覆盖闲鱼回复、微信客服、议价、退款和投诉处理。
	 *
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerSkillService() {
		this.skills.put("xianyu-reply",
				"闲鱼回复技能：回复要短、自然、像真人；先查商品事实，再回答“还在/成色/能否小刀”；议价必须查 getPricePolicy；不要承诺无法确认的信息。");
		this.skills.put("wechat-service",
				"微信客服技能：回复要完整礼貌，可追踪；订单类问题保留订单号、物流单号、工单号；复杂售后说明处理时效和下一步。");
		this.skills.put("price-negotiation",
				"议价技能：先调用 getProductInfo 和 getPricePolicy；高于底价可温和让步；低于底价要礼貌拒绝，给出商品成色、成本或包邮成本解释。");
		this.skills.put("refund-handling",
				"退款处理技能：先查 getOrderInfo，再查 getRefundEligibility 和 searchCustomerPolicy；回答要说明订单状态、适用规则、用户下一步操作。");
		this.skills.put("complaint-handling",
				"投诉处理技能：先安抚情绪，再复述问题，随后调用 createCustomerTicket 记录诉求；回复要给出处理时效和可追踪编号。");
		this.skills.put("logistics-follow-up",
				"物流跟进技能：先查 getOrderInfo 和 getLogisticsInfo；如果运输中，说明最新节点和预计处理；如果无物流，说明待发货或等待揽收。");
		this.skills.put("address-change",
				"地址修改技能：先查订单状态；待发货可记录新地址并创建工单；已发货只建议联系快递或尝试改派，不承诺一定成功。");
		this.skills.put("quality-dispute",
				"质量争议技能：先确认商品说明、签收时间、开箱证据和问题描述；检索质量/售后政策；回复要避免直接判责。");
	}

	/**
	 * 返回可注入系统提示的技能列表，模拟渐进式披露中的技能索引。
	 * @return 技能列表摘要
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String listSkills() {
		StringBuilder builder = new StringBuilder();
		this.skills.forEach((name, content) -> builder.append("- ").append(name).append("：")
				.append(content.length() > 40 ? content.substring(0, 40) + "..." : content).append("\n"));
		return builder.toString().trim();
	}

	/**
	 * 按技能名读取完整技能内容，模拟模型调用 read_skill(skill_name) 的效果。
	 * @param skillName 技能名
	 * @return 技能内容
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String readSkill(String skillName) {
		String safeName = skillName == null ? "" : skillName.trim().toLowerCase(Locale.ROOT);
		return this.skills.getOrDefault(safeName, "未找到技能：" + skillName + "。可用技能：\n" + listSkills());
	}

	/**
	 * 根据渠道和意图选择最适合本轮客服任务的技能。
	 * @param channel 客服渠道
	 * @param intent 客服意图
	 * @return 技能名
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String selectSkill(ChannelType channel, CustomerServiceIntent intent) {
		if (intent == CustomerServiceIntent.COMPLAINT) {
			return "complaint-handling";
		}
		if (intent == CustomerServiceIntent.REFUND_REQUEST || intent == CustomerServiceIntent.RETURN_POLICY) {
			return "refund-handling";
		}
		if (intent == CustomerServiceIntent.PRICE_NEGOTIATION) {
			return "price-negotiation";
		}
		if (intent == CustomerServiceIntent.LOGISTICS_QUERY || intent == CustomerServiceIntent.ORDER_STATUS) {
			return "logistics-follow-up";
		}
		if (channel == ChannelType.XIANYU) {
			return "xianyu-reply";
		}
		if (channel == ChannelType.WECHAT_OFFICIAL_ACCOUNT || channel == ChannelType.WECHAT_WORK
				|| channel == ChannelType.WECHAT_MINI_PROGRAM) {
			return "wechat-service";
		}
		return "xianyu-reply";
	}

}
