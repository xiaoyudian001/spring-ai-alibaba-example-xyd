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

import java.util.ArrayList;
import java.util.List;

import com.alibaba.cloud.ai.agent.LearningAgentResult;
import com.alibaba.cloud.ai.agent.LearningAgentService.LearningAgentMessage;
import com.alibaba.cloud.ai.memory.LearningMemory;
import com.alibaba.cloud.ai.memory.LearningMemoryService;
import com.alibaba.cloud.ai.multiagent.LearningPlannerAgent.PlannerOutput;
import com.alibaba.cloud.ai.multiagent.LearningResearchAgent.ResearchOutput;
import com.alibaba.cloud.ai.multiagent.LearningReviewerAgent.ReviewerOutput;
import org.springframework.stereotype.Service;

/**
 * Coordinates the serial multi-agent learning flow.
 */
@Service
public class LearningCoordinatorAgent {

	private final LearningMemoryService memoryService;

	private final LearningPlannerAgent plannerAgent;

	private final LearningResearchAgent researchAgent;

	private final LearningTeacherAgent teacherAgent;

	private final LearningReviewerAgent reviewerAgent;

	public LearningCoordinatorAgent(LearningMemoryService memoryService, LearningPlannerAgent plannerAgent,
			LearningResearchAgent researchAgent, LearningTeacherAgent teacherAgent,
			LearningReviewerAgent reviewerAgent) {
		this.memoryService = memoryService;
		this.plannerAgent = plannerAgent;
		this.researchAgent = researchAgent;
		this.teacherAgent = teacherAgent;
		this.reviewerAgent = reviewerAgent;
	}

	public MultiAgentResult chat(String userId, String message, List<LearningAgentMessage> history) {
		List<MultiAgentStep> steps = new ArrayList<>();
		steps.add(step("Coordinator", "CoordinatorAgent", "DONE", "接收学习任务，按 Planner -> Research -> Teacher -> Reviewer 串行协作。"));
		LearningMemory memoryBefore = this.memoryService.read(userId);
		PlannerOutput plan = this.plannerAgent.plan(message, memoryBefore);
		steps.add(step("PlannerAgent", "PlannerAgent", "DONE", plan.detail()));
		ResearchOutput research = this.researchAgent.research(userId, message);
		steps.add(step("ResearchAgent", "ResearchAgent", "DONE", research.detail()));
		LearningAgentResult teacherResult = this.teacherAgent.teach(userId, message, history);
		steps.add(step("TeacherAgent", "TeacherAgent", "DONE",
				"复用 LearningAgentService 生成教学回答，并保留 Tool、RAG、MCP、Memory 调用能力。"));
		ReviewerOutput review = this.reviewerAgent.review(message, teacherResult);
		steps.add(step("ReviewerAgent", "ReviewerAgent", "DONE", review.detail() + review.advice()));
		steps.add(step("Coordinator", "CoordinatorAgent", "DONE", "汇总角色产物，输出最终回答和可观察的多 Agent 调试步骤。"));
		String content = withSummary(teacherResult.content(), plan, research, review);
		return new MultiAgentResult(content, teacherResult.intent(), teacherResult.memoryBefore(),
				teacherResult.memoryAfter(), List.copyOf(steps), teacherResult.graphSteps(),
				teacherResult.agentSteps(), teacherResult.toolCalls(), teacherResult.mcpDebugInfo());
	}

	private MultiAgentStep step(String name, String role, String status, String detail) {
		return new MultiAgentStep(name, role, status, detail);
	}

	private String withSummary(String answer, PlannerOutput plan, ResearchOutput research, ReviewerOutput review) {
		return (answer == null ? "" : answer) + """

				---
				### Multi-Agent 协作摘要
				- PlannerAgent：%s
				- ResearchAgent：%s
				- ReviewerAgent：%s
				""".formatted(plan.intent(), research.detail(), review.advice());
	}

}
