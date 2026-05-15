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

package com.alibaba.cloud.ai.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.alibaba.cloud.ai.agent.LearningAgentResult;
import com.alibaba.cloud.ai.agent.LearningAgentService;
import com.alibaba.cloud.ai.agent.LearningAgentService.LearningAgentMessage;
import com.alibaba.cloud.ai.mcp.McpDebugInfo;
import com.alibaba.cloud.ai.memory.LearningMemory;
import com.alibaba.cloud.ai.planner.LearningIntent;
import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.stereotype.Service;

/**
 * Learning-oriented workflow wrapper around the existing learning agent.
 */
@Service
public class LearningWorkflowService {

	private final LearningAgentService learningAgentService;

	public LearningWorkflowService(LearningAgentService learningAgentService) {
		this.learningAgentService = learningAgentService;
	}

	public LearningWorkflowResult chat(String userId, String message, List<LearningAgentMessage> history) {
		LearningAgentResult agentResult = this.learningAgentService.chat(userId, message, history);
		LearningIntent intent = safeIntent(agentResult.intent());
		List<LearningWorkflowStep> steps = learningSteps(message, intent, agentResult);
		return new LearningWorkflowResult(agentResult.content(), safeIntent(agentResult.intent()),
				agentResult.memoryBefore(), agentResult.memoryAfter(), List.copyOf(steps), agentResult.graphSteps(),
				agentResult.agentSteps(), agentResult.toolCalls(), agentResult.mcpDebugInfo());
	}

	private List<LearningWorkflowStep> learningSteps(String message, LearningIntent intent, LearningAgentResult result) {
		List<LearningWorkflowStep> steps = new ArrayList<>();
		steps.add(step("analyze_goal", "识别学习目标", "DONE", analyzeGoal(message, intent)));
		steps.add(step("check_foundation", "判断当前学习阶段", "DONE", checkFoundation(result.memoryBefore())));
		steps.add(step("collect_project_context", "判断是否需要项目上下文", "DONE",
				collectProjectContext(message, result.toolCalls(), result.mcpDebugInfo())));
		steps.add(step("choose_learning_path", "选择学习路径", "DONE", chooseLearningPath(message, intent)));
		steps.add(step("generate_learning_plan", "生成学习计划", "DONE", generateLearningPlan(intent)));
		steps.add(step("define_validation_task", "给出验证任务", "DONE",
				defineValidationTask(intent, result.toolCalls(), result.mcpDebugInfo())));
		steps.add(step("recommend_next_step", "生成下一步建议", "DONE", recommendNextStep(message, intent, result)));
		return steps;
	}

	private LearningWorkflowStep step(String id, String name, String status, String detail) {
		return new LearningWorkflowStep(id, name, status, detail);
	}

	private LearningIntent safeIntent(LearningIntent intent) {
		return intent == null ? LearningIntent.GENERAL_CHAT : intent;
	}

	private String analyzeGoal(String message, LearningIntent intent) {
		String topic = learningTopic(message);
		return switch (intent) {
			case CONCEPT_EXPLAIN -> "识别到用户正在理解概念或区别，当前重点是：" + topic + "。Workflow 需要先把概念讲清楚，再给出对比和实践入口。";
			case LEARNING_ADVICE -> "识别到用户需要学习建议，当前重点是：" + topic + "。Workflow 需要输出学习路径、实践顺序和下一步任务。";
			case DAILY_PLAN -> "识别到用户需要具体学习计划，当前重点是：" + topic + "。Workflow 需要把目标拆成可执行时间块和验收任务。";
			case TIME_QUERY -> "识别到问题包含真实时间诉求，同时可能需要结合学习安排。Workflow 需要先获得准确时间，再生成学习建议。";
			case MIXED -> "识别到用户问题包含多个学习目标，当前重点是：" + topic + "。Workflow 需要组合概念解释、项目上下文、学习计划和验证任务。";
			case GENERAL_CHAT -> "识别到用户在进行一般学习对话，当前重点是：" + topic + "。Workflow 需要保持回答简洁，并引导到可实践任务。";
		};
	}

	private String checkFoundation(LearningMemory memory) {
		if (memory == null) {
			return "当前没有可用 Memory，按初学者处理，优先给出低门槛路径和可运行测试。";
		}
		String topics = memory.getTopics().isEmpty() ? "暂无" : String.join("、", memory.getTopics());
		return "根据 Memory，用户阶段为「" + memory.getLevel() + "」，已关注主题：" + topics + "，历史对话轮次："
				+ memory.getConversationCount() + "。适合继续从已跑通能力进入更真实的 Workflow / Multi-Agent 实践。";
	}

	private String collectProjectContext(String message, List<ToolCallDebugRecorder.ToolCallDebug> toolCalls,
			McpDebugInfo mcpDebugInfo) {
		boolean projectQuestion = isProjectImplementationQuestion(message);
		boolean usedRag = hasTool(toolCalls, "searchLearningDocs");
		boolean usedMcp = hasTool(toolCalls, "searchMcpLearningResources") || hasTool(toolCalls, "createMcpLearningResource")
				|| hasTool(toolCalls, "updateMcpLearningResource") || isMcpUsed(mcpDebugInfo);
		if (!projectQuestion) {
			return "本轮属于通用概念学习问题，Workflow 不强制收集当前项目上下文；应先输出通用模型、核心区别和适用场景，再按需补充本项目落地方式。";
		}
		if (usedRag && usedMcp) {
			return "本轮已同时使用本地 RAG 和 MCP 上下文，适合回答当前项目实现、外部学习资源和下一步实践。";
		}
		if (usedRag) {
			return "本轮已使用本地 RAG 检索当前项目资料，适合解释代码结构、README 路线和调用链。";
		}
		if (usedMcp) {
			return "本轮已使用 MCP 学习资源上下文，适合补充外部化资源、沉淀学习条目或验证 MCP 接入。";
		}
		return "本轮问题涉及项目上下文，但未明显触发 RAG/MCP。后续可优化 Planner，让项目类问题优先检索资料。";
	}

	private boolean isProjectImplementationQuestion(String message) {
		return containsAny(message, "当前项目", "这个项目", "本项目", "项目中", "项目里", "项目里面", "当前实现",
				"源码", "代码", "readme", "minimax-chat", "application.yml", "controller", "service", "接口",
				"类", "包", "文件");
	}

	private String chooseLearningPath(String message, LearningIntent intent) {
		String topic = learningTopic(message);
		if (containsAny(message, "multi-agent", "multi_agent", "多智能体")) {
			return "选择 Multi-Agent 学习路径：先理解角色分工、消息传递和协作策略，再按需映射到 Coordinator、PlannerAgent、ResearchAgent、TeacherAgent、ReviewerAgent。";
		}
		if (containsAny(message, "workflow", "工作流")) {
			return "选择 Workflow 学习路径：先理解固定步骤、状态流转、输入输出契约和失败处理，再按需把复杂节点升级为 Agent 或 Graph 节点。";
		}
		if (containsAny(message, "agentgraph", "graph", "stategraph")) {
			return "选择 AgentGraph 学习路径：重点理解节点、边、共享状态、条件路由和循环控制如何承载 Workflow 或 Multi-Agent。";
		}
		return switch (intent) {
			case DAILY_PLAN -> "选择计划型学习路径：围绕「" + topic + "」拆成时间块、产出物和测试点。";
			case CONCEPT_EXPLAIN -> "选择概念型学习路径：围绕「" + topic + "」先解释通用定义，再对比区别，最后给出可选项目落地映射。";
			case LEARNING_ADVICE, MIXED -> "选择进阶型学习路径：围绕「" + topic + "」先补齐概念，再进入代码实现和评估复盘。";
			default -> "选择通用学习路径：先明确目标，再补项目上下文，最后给出一个小步可验证任务。";
		};
	}

	private String generateLearningPlan(LearningIntent intent) {
		return switch (intent) {
			case DAILY_PLAN -> "建议按 30 分钟执行：10 分钟读 Workflow 步骤，10 分钟运行接口和前端，10 分钟查看 Report/Evaluation/Judge 结果。";
			case CONCEPT_EXPLAIN -> "建议按三段学习：先画对比表，再看当前项目对应类，最后用同一问题对比 Agent、Graph、Workflow 输出。";
			case LEARNING_ADVICE, MIXED -> "建议按实践路线推进：先完善 Workflow 场景，再实现 Multi-Agent 角色拆分，最后把 Multi-Agent 放进 Graph 节点。";
			case TIME_QUERY -> "建议先完成时间工具验证，再把当前时间作为学习计划起点，生成今日剩余学习安排。";
			case GENERAL_CHAT -> "建议先提出一个明确学习目标，再选择 Tool、RAG、MCP、Workflow 或 Multi-Agent 中的一个能力做小步实践。";
		};
	}

	private String defineValidationTask(LearningIntent intent, List<ToolCallDebugRecorder.ToolCallDebug> toolCalls,
			McpDebugInfo mcpDebugInfo) {
		String toolSummary = toolCalls == null || toolCalls.isEmpty() ? "本轮没有工具调用" : "本轮工具调用 " + toolCalls.size() + " 次";
		String mcpSummary = mcpDebugInfo == null ? "MCP 未使用" : "MCP 模式：" + mcpDebugInfo.mode();
		return switch (intent) {
			case CONCEPT_EXPLAIN, LEARNING_ADVICE, MIXED -> "验证任务：用 Workflow 模式提问同一个问题，再切换官方 Graph 对比 graphSteps；检查 "
					+ toolSummary + "，" + mcpSummary + "。";
			case DAILY_PLAN -> "验证任务：确认回答中包含时间块、实践动作和验收标准；再查看 Evaluation Dashboard 是否更新。";
			case TIME_QUERY -> "验证任务：检查 toolCalls 是否包含 getCurrentTime，并确认最终回答没有硬编码时间。";
			case GENERAL_CHAT -> "验证任务：查看 report/agent-runs.json 是否新增 LEARNING_WORKFLOW 记录，并确认 Memory 是否合理更新。";
		};
	}

	private String recommendNextStep(String message, LearningIntent intent, LearningAgentResult result) {
		if (containsAny(message, "multi-agent", "multi_agent", "多智能体")) {
			return isProjectImplementationQuestion(message)
					? "下一步建议：继续完善当前 multiagent 包，让 CoordinatorAgent、ResearchAgent、TeacherAgent、ReviewerAgent 的输入输出更清晰。"
					: "下一步建议：先画出 Multi-Agent 通用协作图，明确每个角色的职责、输入、输出和失败兜底，再决定是否落到当前项目代码。";
		}
		if (containsAny(message, "workflow", "工作流")) {
			return isProjectImplementationQuestion(message)
					? "下一步建议：把 Workflow 的 collect_project_context 节点从说明型升级为显式 RAG/MCP 调用节点，并记录每个节点输入输出。"
					: "下一步建议：先用一个真实学习场景设计 Workflow 节点，例如目标识别、阶段判断、路径选择、计划生成和验证任务，再考虑代码实现。";
		}
		if (result.mcpDebugInfo() != null && result.mcpDebugInfo().pendingWrite() != null) {
			return "下一步建议：先确认或取消本轮 MCP 写入草稿，再观察 Report、Evaluation 和 Judge 对写入流程的评价。";
		}
		return switch (intent) {
			case CONCEPT_EXPLAIN -> "下一步建议：用一个具体问题分别测试手写 Agent、官方 Agent、官方 Graph 和 Workflow，比较四种链路的调试信息。";
			case LEARNING_ADVICE, MIXED -> "下一步建议：进入 Multi-Agent 实现，把学习建议拆成规划、研究、讲解、评审四个角色。";
			case DAILY_PLAN -> "下一步建议：按计划完成一次代码修改，并用 HTTP 用例验证接口、Report 和 Evaluation。";
			case TIME_QUERY -> "下一步建议：把时间工具和学习计划组合成一个固定 Workflow 节点，观察工具调用是否稳定。";
			case GENERAL_CHAT -> "下一步建议：提出一个明确学习主题，例如 Workflow、Multi-Agent 或 AgentGraph，然后让 Workflow 输出路径和验证任务。";
		};
	}

	private String learningTopic(String message) {
		if (containsAny(message, "multi-agent", "multi_agent", "多智能体")) {
			return "Multi-Agent 多角色协作";
		}
		if (containsAny(message, "workflow", "工作流")) {
			return "Workflow 学习流程编排";
		}
		if (containsAny(message, "agentgraph", "graph", "stategraph")) {
			return "AgentGraph / StateGraph 图编排";
		}
		if (containsAny(message, "mcp")) {
			return "MCP 工具与资源接入";
		}
		if (containsAny(message, "rag")) {
			return "RAG 项目知识检索";
		}
		if (containsAny(message, "tool", "工具")) {
			return "Tool Calling 工具调用";
		}
		return "Spring AI Alibaba Agent 学习";
	}

	private boolean hasTool(List<ToolCallDebugRecorder.ToolCallDebug> toolCalls, String toolName) {
		return toolCalls != null && toolCalls.stream().anyMatch(toolCall -> toolName.equals(toolCall.name()));
	}

	private boolean isMcpUsed(McpDebugInfo mcpDebugInfo) {
		return mcpDebugInfo != null && mcpDebugInfo.mode() != null && !"NOT_USED".equals(mcpDebugInfo.mode());
	}

	private boolean containsAny(String text, String... keywords) {
		String safeText = text == null ? "" : text.toLowerCase(Locale.ROOT);
		for (String keyword : keywords) {
			if (safeText.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

}
