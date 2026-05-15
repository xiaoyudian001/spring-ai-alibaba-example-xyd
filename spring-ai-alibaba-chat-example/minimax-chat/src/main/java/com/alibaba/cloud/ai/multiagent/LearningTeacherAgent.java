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

package com.alibaba.cloud.ai.multiagent;

import java.util.List;

import com.alibaba.cloud.ai.agent.LearningAgentResult;
import com.alibaba.cloud.ai.agent.LearningAgentService;
import com.alibaba.cloud.ai.agent.LearningAgentService.LearningAgentMessage;
import com.alibaba.cloud.ai.multiagent.LearningPlannerAgent.PlannerOutput;
import com.alibaba.cloud.ai.multiagent.LearningResearchAgent.ResearchOutput;
import org.springframework.stereotype.Component;

/**
 * Produces the teaching answer by reusing the existing learning agent.
 */
@Component
public class LearningTeacherAgent {

	private final LearningAgentService learningAgentService;

	public LearningTeacherAgent(LearningAgentService learningAgentService) {
		this.learningAgentService = learningAgentService;
	}

	public LearningAgentResult teach(String userId, String message, List<LearningAgentMessage> history,
			PlannerOutput plan, ResearchOutput research) {
		return this.learningAgentService.chat(userId, augmentedMessage(message, plan, research), history);
	}

	private String augmentedMessage(String message, PlannerOutput plan, ResearchOutput research) {
		return """
				%s

				【Multi-Agent 上下文】
				PlannerAgent 识别意图：%s
				PlannerAgent 规划：%s
				ResearchAgent 判断范围：%s

				ResearchAgent RAG 摘要：
				%s

				ResearchAgent MCP 摘要：
				%s

				【回答范围规则】
				- 如果范围是 GENERAL_CONCEPT：请先解释通用 AI 工程概念，不要默认绑定 minimax-chat 项目结构；当前项目只能作为“本项目可以如何落地”的补充小节。
				- 如果范围是 PROJECT_IMPLEMENTATION：请优先结合当前项目源码、RAG/MCP 上下文和调试信息解释实现细节。

				请基于上述上下文回答用户原始问题。回答必须包含：
				1. 完整学习路线
				2. 具体实践任务
				3. 可执行测试方法
				4. 如果真实 MCP 不可用且本轮确实需要 MCP，请明确说明 fallback，并给出备选操作
				""".formatted(message, plan.intent(), plan.detail(), research.scope(), research.ragSummary(),
				research.mcpSummary());
	}

}
