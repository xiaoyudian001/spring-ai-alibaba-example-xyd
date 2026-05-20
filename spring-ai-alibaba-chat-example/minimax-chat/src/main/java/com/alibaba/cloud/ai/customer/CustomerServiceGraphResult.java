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

import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;

/**
 * 智能客服官方 StateGraph 响应结果，用于展示客服 Graph 节点、工具调用、MCP 和 Memory 信息。
 *
 * @param content 模型生成的客服回复
 * @param intent 客服业务意图
 * @param memoryBefore 调用前客服记忆
 * @param memoryAfter 调用后客服记忆
 * @param graphSteps 官方 StateGraph 节点步骤
 * @param agentSteps 智能客服角色步骤
 * @param toolCalls 本轮工具调用明细
 * @param mcpDebugInfo MCP 调试信息
 * @param rawState 官方 StateGraph 原始状态
 * @param graphDefinition 官方 StateGraph Mermaid 定义
 * @author xyd
 * @date 2026-05-19 00:20:26
 */
public record CustomerServiceGraphResult(String content, CustomerServiceIntent intent, CustomerMemory memoryBefore,
		CustomerMemory memoryAfter, List<CustomerServiceStep> graphSteps, List<CustomerServiceStep> agentSteps,
		List<ToolCallDebugRecorder.ToolCallDebug> toolCalls, McpDebugInfo mcpDebugInfo,
		Map<String, Object> rawState, String graphDefinition) {
}
