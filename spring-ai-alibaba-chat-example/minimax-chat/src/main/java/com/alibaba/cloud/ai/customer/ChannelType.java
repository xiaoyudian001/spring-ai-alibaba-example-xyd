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
 * 客服消息来源渠道，用于把网页、闲鱼、微信等平台消息统一适配为内部会话请求。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public enum ChannelType {

	/**
	 * 网页客服渠道，当前前端聊天页面默认使用该渠道，适合本地开发和端到端调试。
	 */
	WEB,

	/**
	 * 闲鱼客服渠道，第一阶段使用 Mock 消息模拟买家咨询，后续可替换为官方授权接口。
	 */
	XIANYU,

	/**
	 * 微信公众号客服渠道，用于承接公众号用户发来的客服咨询消息。
	 */
	WECHAT_OFFICIAL_ACCOUNT,

	/**
	 * 企业微信客服渠道，用于承接企业微信或微信客服体系中的客户咨询。
	 */
	WECHAT_WORK,

	/**
	 * 微信小程序客服渠道，用于承接小程序内发起的客服会话。
	 */
	WECHAT_MINI_PROGRAM

}
