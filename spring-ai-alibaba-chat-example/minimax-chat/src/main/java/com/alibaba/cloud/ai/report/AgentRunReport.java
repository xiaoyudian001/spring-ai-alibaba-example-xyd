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

package com.alibaba.cloud.ai.report;

import java.time.Instant;
import java.util.List;

/**
 * 智能客服一次 Agent、Graph 或 Multi-Agent 执行后的持久化报告快照。
 *
 * @param id 报告唯一 ID
 * @param createdAt 报告创建时间
 * @param userId 用户唯一标识
 * @param chainMode 本次执行链路模式
 * @param message 用户原始问题
 * @param historySize 本轮携带的历史消息数量
 * @param intent 识别到的客服业务意图
 * @param answerSummary 回答摘要
 * @param answerContent 完整回答内容
 * @param mcpMode MCP 调用模式
 * @param pendingMcpWrite 历史兼容字段，v1.0 客服链路固定为 false
 * @param toolCallCount 工具调用次数
 * @param agentStepCount Agent 执行步骤数量
 * @param graphStepCount Graph 节点数量
 * @param channel 客服渠道
 * @param memoryBefore 调用前客服长期记忆
 * @param memoryAfter 调用后客服长期记忆
 * @param agentSteps Agent 执行步骤明细
 * @param graphSteps Graph 执行节点明细
 * @param toolCalls 工具调用明细
 * @param mcpDebugInfo MCP 调试信息
 * @author xyd
 * @date 2026-05-22 15:00:00
 */
public record AgentRunReport(String id, Instant createdAt, String userId, String chainMode, String message,
		int historySize, String intent, String answerSummary, String answerContent, String mcpMode,
		boolean pendingMcpWrite, int toolCallCount, int agentStepCount, int graphStepCount, String channel,
		Object memoryBefore, Object memoryAfter, List<?> agentSteps, List<?> graphSteps, List<?> toolCalls,
		Object mcpDebugInfo) {

}
