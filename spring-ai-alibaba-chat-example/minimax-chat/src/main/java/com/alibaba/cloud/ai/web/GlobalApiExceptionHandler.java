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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局 API 异常处理器，把后端异常转换成前端可展示的统一 JSON 结构。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

	/**
	 * 处理参数或业务校验异常。
	 * @param ex 业务异常
	 * @param request HTTP 请求
	 * @return 统一错误响应
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
	public ResponseEntity<ApiErrorResponse> handleBusinessException(RuntimeException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse("BAD_REQUEST", friendlyMessage(ex), request.getRequestURI(),
						Instant.now()));
	}

	/**
	 * 兜底处理未预期异常，避免前端只看到空白或原始堆栈。
	 * @param ex 未预期异常
	 * @param request HTTP 请求
	 * @return 统一错误响应
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnknownException(Exception ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiErrorResponse("INTERNAL_ERROR", friendlyMessage(ex), request.getRequestURI(),
						Instant.now()));
	}

	/**
	 * 生成适合前端展示的错误消息。
	 * @param ex 异常对象
	 * @return 友好错误消息
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private String friendlyMessage(Exception ex) {
		String message = ex.getMessage();
		return message == null || message.isBlank() ? "服务暂时不可用，请稍后重试。" : message;
	}

}
