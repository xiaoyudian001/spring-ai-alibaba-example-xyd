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
 * 智能客服会话历史消息，用于在官方 ReactAgent 调用前构造业务上下文摘要。
 *
 * @param role 消息角色，例如 user 或 assistant
 * @param content 消息内容
 * @author xyd
 * @date 2026-05-18 11:34:38
 */
public record CustomerConversationMessage(String role, String content) {
}
