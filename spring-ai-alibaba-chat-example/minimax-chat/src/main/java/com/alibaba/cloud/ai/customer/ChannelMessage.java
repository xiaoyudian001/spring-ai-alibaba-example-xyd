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

import java.util.Map;

/**
 * 多渠道客服消息的统一输入模型，屏蔽网页、闲鱼、微信等平台原始消息格式差异。
 *
 * @param channel 消息来源渠道
 * @param userId 用户唯一标识
 * @param conversationId 会话唯一标识
 * @param messageId 消息唯一标识
 * @param text 用户原始文本
 * @param metadata 渠道扩展元数据
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public record ChannelMessage(ChannelType channel, String userId, String conversationId, String messageId, String text,
		Map<String, Object> metadata) {
}
