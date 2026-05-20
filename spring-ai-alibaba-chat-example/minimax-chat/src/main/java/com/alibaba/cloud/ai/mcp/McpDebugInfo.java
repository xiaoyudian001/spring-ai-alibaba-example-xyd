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

package com.alibaba.cloud.ai.mcp;

import java.util.List;

/**
 * MCP 调试信息，记录智能客服本轮是否命中真实 MCP 工具以及 fallback 原因。
 *
 * @param mode MCP 调用模式
 * @param realMcpAvailable 是否发现真实 MCP 工具
 * @param selectedToolName 本轮实际选择的 MCP 工具名称
 * @param availableToolNames 当前已发现的 MCP 工具名称列表
 * @param fallbackReason 未命中真实 MCP 时的兜底原因
 * @param query 本轮逻辑工具或检索查询
 * @param limit 本轮检索限制数量
 * @author xyd
 * @date 2026-05-20 09:15:00
 */
public record McpDebugInfo(String mode, boolean realMcpAvailable, String selectedToolName,
		List<String> availableToolNames, String fallbackReason, String query, Integer limit) {

	/**
	 * 创建未使用 MCP 时的空调试信息。
	 * @return 空 MCP 调试信息
	 * @author xyd
	 * @date 2026-05-20 09:15:00
	 */
	public static McpDebugInfo none() {
		return new McpDebugInfo("NOT_USED", false, "", List.of(), "", "", null);
	}

}
