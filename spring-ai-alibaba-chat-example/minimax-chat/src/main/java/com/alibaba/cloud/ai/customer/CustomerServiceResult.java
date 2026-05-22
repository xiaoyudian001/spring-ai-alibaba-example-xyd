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

import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;

/**
 * 智能客服助手响应结果，兼容当前前端调试区所需的 content、intent、Memory、步骤和 Tool 调用信息。
 *
 * @param content 模型生成的客服回复
 * @param intent 客服业务意图
 * @param memoryBefore 调用前客服记忆
 * @param memoryAfter 调用后客服记忆
 * @param workflowSteps 客服 Workflow 步骤
 * @param multiAgentSteps 客服 Multi-Agent 角色步骤
 * @param toolCalls 本轮工具调用明细
 * @param mcpDebugInfo MCP 调试信息，第一阶段可为空
 * @param factBundle 后端在模型调用前预取的业务事实包
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public record CustomerServiceResult(String content, CustomerServiceIntent intent, CustomerMemory memoryBefore,
		CustomerMemory memoryAfter, List<CustomerServiceStep> workflowSteps, List<CustomerServiceStep> multiAgentSteps,
		List<ToolCallDebugRecorder.ToolCallDebug> toolCalls, McpDebugInfo mcpDebugInfo,
		CustomerFactBundle factBundle) {
}
