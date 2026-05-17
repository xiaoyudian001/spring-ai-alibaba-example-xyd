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
		this.skills.put("xianyu-reply", "闲鱼回复技能：回复要短、自然、像真人；优先确认商品事实；议价先查底价；高风险动作转人工。");
		this.skills.put("wechat-service", "微信客服技能：回复要完整礼貌；保留订单号、工单号；复杂问题说明处理时效。");
		this.skills.put("price-negotiation", "议价技能：先看商品底价；可接受范围内给出温和让步；低于底价时礼貌拒绝并说明原因。");
		this.skills.put("refund-handling", "退款处理技能：先查订单，再查退款政策；满足条件也只生成建议；真实退款必须人工确认。");
		this.skills.put("complaint-handling", "投诉处理技能：先安抚情绪，再复述问题，给出明确处理动作；必要时创建工单并转人工。");
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
