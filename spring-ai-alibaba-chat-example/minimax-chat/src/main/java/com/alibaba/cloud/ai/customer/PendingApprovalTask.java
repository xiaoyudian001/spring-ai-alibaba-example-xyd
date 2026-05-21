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

import java.time.Instant;

/**
 * 高风险客服动作的待审核任务，承接模型识别出的退款、赔付、人工接管等动作请求。
 *
 * @param id 审核任务唯一标识
 * @param actionType 高风险动作类型
 * @param conversationId 会话或用户标识
 * @param reason 创建审核任务的业务原因
 * @param status 当前审核状态
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
public record PendingApprovalTask(String id, String actionType, String conversationId, String reason,
		ApprovalTaskStatus status, Instant createdAt, Instant updatedAt) {
}
