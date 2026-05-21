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
 * 待审核任务状态，用于约束退款、赔付、人工接管等高风险动作必须先进入人工确认队列。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
public enum ApprovalTaskStatus {

	/**
	 * 待审核，模型或工具只创建了任务，尚未由运营人员确认。
	 */
	PENDING,

	/**
	 * 已通过，运营人员确认可以继续执行后续线下或真实系统动作。
	 */
	APPROVED,

	/**
	 * 已拒绝，运营人员判断该高风险动作不能执行或需要重新补充信息。
	 */
	REJECTED

}
