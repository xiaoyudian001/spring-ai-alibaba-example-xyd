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
 * 面向客户主聊天页面的统一客服响应结果，隐藏 ReactAgent、Graph、Multi-Agent 等技术实现差异。
 *
 * @param content 最终返回给客户的客服回复
 * @param intent 后端识别到的客服业务意图
 * @param memoryBefore 调用前客服长期记忆
 * @param memoryAfter 调用后客服长期记忆
 * @param workflowSteps 后端自动路由或工作流步骤
 * @param multiAgentSteps 多智能体执行步骤，普通客服 Agent 场景可为空
 * @param toolCalls 本轮工具调用明细
 * @param mcpDebugInfo MCP 调试信息
 * @param chainMode 后端实际选择的执行链路，仅用于调试页和报告评估
 * @author xyd
 * @date 2026-05-20 09:38:00
 */
public record CustomerServiceAssistantResult(String content, CustomerServiceIntent intent, CustomerMemory memoryBefore,
		CustomerMemory memoryAfter, List<CustomerServiceStep> workflowSteps, List<CustomerServiceStep> multiAgentSteps,
		List<ToolCallDebugRecorder.ToolCallDebug> toolCalls, McpDebugInfo mcpDebugInfo, String chainMode) {

	/**
	 * 将普通客服 ReactAgent 结果转换为统一客服响应。
	 * @param result 普通客服 Agent 响应
	 * @param routeStep 后端自动路由说明
	 * @return 统一客服响应
	 * @author xyd
	 * @date 2026-05-20 09:38:00
	 */
	public static CustomerServiceAssistantResult fromAgent(CustomerServiceResult result, CustomerServiceStep routeStep) {
		return new CustomerServiceAssistantResult(result.content(), result.intent(), result.memoryBefore(),
				result.memoryAfter(), prepend(routeStep, result.workflowSteps()), result.multiAgentSteps(),
				result.toolCalls(), result.mcpDebugInfo(), "CUSTOMER_SERVICE_ASSISTANT_AGENT");
	}

	/**
	 * 将客服 Multi-Agent 结果转换为统一客服响应。
	 * @param result 多智能体客服响应
	 * @param routeStep 后端自动路由说明
	 * @return 统一客服响应
	 * @author xyd
	 * @date 2026-05-20 09:38:00
	 */
	public static CustomerServiceAssistantResult fromMultiAgent(CustomerServiceMultiAgentResult result,
			CustomerServiceStep routeStep) {
		return new CustomerServiceAssistantResult(result.content(), result.intent(), result.memoryBefore(),
				result.memoryAfter(), List.of(routeStep), result.agentSteps(), result.toolCalls(), result.mcpDebugInfo(),
				"CUSTOMER_SERVICE_ASSISTANT_MULTI_AGENT");
	}

	/**
	 * 将轻量直连大模型结果转换为统一客服响应。
	 * @param content 大模型直接生成的客服回复
	 * @param memoryBefore 调用前客服长期记忆
	 * @param memoryAfter 调用后客服长期记忆，简单对话通常不更新长期记忆
	 * @param routeStep 后端自动路由说明
	 * @return 统一客服响应
	 * @author xyd
	 * @date 2026-05-21 11:20:00
	 */
	public static CustomerServiceAssistantResult fromDirect(String content, CustomerMemory memoryBefore,
			CustomerMemory memoryAfter, CustomerServiceStep routeStep) {
		return new CustomerServiceAssistantResult(content, CustomerServiceIntent.GENERAL_CHAT, memoryBefore,
				memoryAfter, List.of(routeStep), List.of(), List.of(), null, "CUSTOMER_SERVICE_DIRECT_LLM");
	}

	/**
	 * 在已有步骤前追加后端自动路由步骤，便于调试页观察系统为何选择某条链路。
	 * @param first 自动路由步骤
	 * @param rest 原始步骤
	 * @return 合并后的步骤列表
	 * @author xyd
	 * @date 2026-05-20 09:38:00
	 */
	private static List<CustomerServiceStep> prepend(CustomerServiceStep first, List<CustomerServiceStep> rest) {
		if (rest == null || rest.isEmpty()) {
			return List.of(first);
		}
		return java.util.stream.Stream.concat(java.util.stream.Stream.of(first), rest.stream()).toList();
	}

}
