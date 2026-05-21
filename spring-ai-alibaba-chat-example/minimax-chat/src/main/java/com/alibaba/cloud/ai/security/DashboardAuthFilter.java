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

package com.alibaba.cloud.ai.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 工作台轻量鉴权过滤器，保护 dashboard 和运营调试接口，避免上线后裸露管理能力。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
@Component
public class DashboardAuthFilter extends OncePerRequestFilter {

	private static final String TOKEN_HEADER = "X-Dashboard-Token";

	private static final String TOKEN_COOKIE = "MINIMAX_DASHBOARD_TOKEN";

	private final boolean enabled;

	private final String token;

	private final List<String> protectedPrefixes = List.of("/dashboard.html", "/minimax/chat-client/report",
			"/minimax/chat-client/evaluation", "/minimax/chat-client/judge",
			"/minimax/chat-client/customer-service/rag", "/minimax/chat-client/customer-service/mcp",
			"/minimax/chat-client/customer-service/memory", "/minimax/chat-client/customer-service/context",
			"/minimax/chat-client/customer-service/storage", "/minimax/chat-client/customer-service/approval",
			"/minimax/chat-client/audit");

	/**
	 * 创建工作台鉴权过滤器。
	 * @param enabled 是否开启鉴权
	 * @param token 工作台访问令牌
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	public DashboardAuthFilter(@Value("${minimax.dashboard.auth.enabled:false}") boolean enabled,
			@Value("${minimax.dashboard.auth.token:}") String token) {
		this.enabled = enabled;
		this.token = token == null ? "" : token.trim();
	}

	/**
	 * 对工作台页面和管理接口执行简单令牌校验。
	 * @param request HTTP 请求
	 * @param response HTTP 响应
	 * @param filterChain 后续过滤链
	 * @throws ServletException Servlet 异常
	 * @throws IOException IO 异常
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!this.enabled || !isProtectedPath(request) || tokenAccepted(request)) {
			if (this.enabled && isProtectedPath(request)) {
				rememberTokenIfPresent(request, response);
			}
			filterChain.doFilter(request, response);
			return;
		}
		writeUnauthorized(response, request.getRequestURI());
	}

	/**
	 * 判断请求路径是否需要工作台鉴权。
	 * @param request HTTP 请求
	 * @return 是否需要鉴权
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private boolean isProtectedPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		return this.protectedPrefixes.stream().anyMatch(path::startsWith);
	}

	/**
	 * 判断请求是否携带正确的工作台令牌。
	 * @param request HTTP 请求
	 * @return 是否通过鉴权
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private boolean tokenAccepted(HttpServletRequest request) {
		if (this.token.isBlank()) {
			return false;
		}
		return this.token.equals(request.getHeader(TOKEN_HEADER))
				|| this.token.equals(request.getParameter("token"))
				|| this.token.equals(cookieToken(request));
	}

	/**
	 * 首次通过 URL token 访问 dashboard 时写入短期 Cookie，便于静态页面后续 fetch 自动携带。
	 * @param request HTTP 请求
	 * @param response HTTP 响应
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void rememberTokenIfPresent(HttpServletRequest request, HttpServletResponse response) {
		String queryToken = request.getParameter("token");
		if (queryToken == null || queryToken.isBlank() || !queryToken.equals(this.token)) {
			return;
		}
		Cookie cookie = new Cookie(TOKEN_COOKIE, queryToken);
		cookie.setPath("/");
		cookie.setHttpOnly(false);
		cookie.setMaxAge(60 * 60 * 8);
		response.addCookie(cookie);
	}

	/**
	 * 从 Cookie 中读取工作台令牌。
	 * @param request HTTP 请求
	 * @return Cookie 中的令牌
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private String cookieToken(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return "";
		}
		for (Cookie cookie : request.getCookies()) {
			if (TOKEN_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return "";
	}

	/**
	 * 输出统一的未授权响应。
	 * @param response HTTP 响应
	 * @param path 请求路径
	 * @throws IOException IO 异常
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"工作台需要访问令牌\",\"path\":\""
				+ path + "\"}");
	}

}
