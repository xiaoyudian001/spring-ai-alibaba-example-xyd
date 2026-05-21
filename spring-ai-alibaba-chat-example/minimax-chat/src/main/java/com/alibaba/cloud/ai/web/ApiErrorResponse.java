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

package com.alibaba.cloud.ai.web;

import java.time.Instant;

/**
 * 统一 API 错误响应，供前端展示友好错误提示。
 *
 * @param code 错误码
 * @param message 用户可读错误消息
 * @param path 请求路径
 * @param timestamp 错误发生时间
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
public record ApiErrorResponse(String code, String message, String path, Instant timestamp) {
}
