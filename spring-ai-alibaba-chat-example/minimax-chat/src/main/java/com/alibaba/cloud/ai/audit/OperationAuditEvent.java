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

package com.alibaba.cloud.ai.audit;

import java.time.Instant;

/**
 * 工作台操作审计事件，记录用户、动作、目标对象和执行结果，方便上线后追踪关键操作。
 *
 * @param id 审计事件唯一标识
 * @param userId 操作涉及的用户 ID
 * @param action 操作名称
 * @param target 操作目标
 * @param detail 操作详情
 * @param success 是否成功
 * @param createdAt 事件创建时间
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
public record OperationAuditEvent(String id, String userId, String action, String target, String detail,
		boolean success, Instant createdAt) {
}
