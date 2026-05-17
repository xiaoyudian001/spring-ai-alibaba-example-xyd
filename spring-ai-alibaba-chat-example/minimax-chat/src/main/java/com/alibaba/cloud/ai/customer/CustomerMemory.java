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

/**
 * 智能客服长期记忆，用于记录用户最近咨询渠道、商品、订单、回复偏好和风险标记。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public class CustomerMemory {

	private String userId = "default-user";

	private ChannelType channel = ChannelType.WEB;

	private List<String> recentProductIds = new ArrayList<>();

	private List<String> recentOrderIds = new ArrayList<>();

	private String preferredTone = "简洁友好";

	private CustomerServiceIntent lastIntent = CustomerServiceIntent.GENERAL_CHAT;

	private List<String> riskFlags = new ArrayList<>();

	private int conversationCount;

	private String lastQuestion = "";

	/**
	 * 获取用户唯一标识。
	 * @return 用户唯一标识
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String getUserId() {
		return this.userId;
	}

	/**
	 * 设置用户唯一标识。
	 * @param userId 用户唯一标识
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setUserId(String userId) {
		this.userId = userId;
	}

	/**
	 * 获取最近一次客服渠道。
	 * @return 最近一次客服渠道
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public ChannelType getChannel() {
		return this.channel;
	}

	/**
	 * 设置最近一次客服渠道。
	 * @param channel 最近一次客服渠道
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setChannel(ChannelType channel) {
		this.channel = channel;
	}

	/**
	 * 获取用户最近咨询的商品 ID 列表。
	 * @return 最近商品 ID 列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public List<String> getRecentProductIds() {
		return this.recentProductIds;
	}

	/**
	 * 设置用户最近咨询的商品 ID 列表。
	 * @param recentProductIds 最近商品 ID 列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setRecentProductIds(List<String> recentProductIds) {
		this.recentProductIds = recentProductIds;
	}

	/**
	 * 获取用户最近咨询的订单 ID 列表。
	 * @return 最近订单 ID 列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public List<String> getRecentOrderIds() {
		return this.recentOrderIds;
	}

	/**
	 * 设置用户最近咨询的订单 ID 列表。
	 * @param recentOrderIds 最近订单 ID 列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setRecentOrderIds(List<String> recentOrderIds) {
		this.recentOrderIds = recentOrderIds;
	}

	/**
	 * 获取用户偏好的客服回复语气。
	 * @return 回复语气
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String getPreferredTone() {
		return this.preferredTone;
	}

	/**
	 * 设置用户偏好的客服回复语气。
	 * @param preferredTone 回复语气
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setPreferredTone(String preferredTone) {
		this.preferredTone = preferredTone;
	}

	/**
	 * 获取最近一次客服意图。
	 * @return 最近一次客服意图
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public CustomerServiceIntent getLastIntent() {
		return this.lastIntent;
	}

	/**
	 * 设置最近一次客服意图。
	 * @param lastIntent 最近一次客服意图
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setLastIntent(CustomerServiceIntent lastIntent) {
		this.lastIntent = lastIntent;
	}

	/**
	 * 获取用户风险标记。
	 * @return 风险标记列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public List<String> getRiskFlags() {
		return this.riskFlags;
	}

	/**
	 * 设置用户风险标记。
	 * @param riskFlags 风险标记列表
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setRiskFlags(List<String> riskFlags) {
		this.riskFlags = riskFlags;
	}

	/**
	 * 获取历史客服对话轮次。
	 * @return 历史客服对话轮次
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public int getConversationCount() {
		return this.conversationCount;
	}

	/**
	 * 设置历史客服对话轮次。
	 * @param conversationCount 历史客服对话轮次
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setConversationCount(int conversationCount) {
		this.conversationCount = conversationCount;
	}

	/**
	 * 获取用户最近一次问题。
	 * @return 最近一次问题
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String getLastQuestion() {
		return this.lastQuestion;
	}

	/**
	 * 设置用户最近一次问题。
	 * @param lastQuestion 最近一次问题
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public void setLastQuestion(String lastQuestion) {
		this.lastQuestion = lastQuestion;
	}

	/**
	 * 生成适合放入系统提示和调试区的客服记忆摘要。
	 * @return 客服记忆摘要
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String summary() {
		return "渠道：" + this.channel + "；最近商品：" + emptyText(this.recentProductIds) + "；最近订单："
				+ emptyText(this.recentOrderIds) + "；语气：" + this.preferredTone + "；风险标记："
				+ emptyText(this.riskFlags) + "；轮次：" + this.conversationCount + "；上次意图：" + this.lastIntent;
	}

	/**
	 * 把列表转换成摘要文本，空列表返回“暂无”。
	 * @param values 待转换列表
	 * @return 摘要文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private String emptyText(List<String> values) {
		return values == null || values.isEmpty() ? "暂无" : String.join("、", values);
	}

}
