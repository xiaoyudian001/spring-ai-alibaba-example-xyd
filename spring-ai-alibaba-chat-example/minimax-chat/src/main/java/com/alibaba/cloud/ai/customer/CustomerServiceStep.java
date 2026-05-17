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
 * 智能客服链路中的可观察步骤，用于前端调试区展示 Workflow 或 Multi-Agent 处理过程。
 *
 * @param name 步骤名称
 * @param detail 步骤说明
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public record CustomerServiceStep(String name, String detail) {
}
