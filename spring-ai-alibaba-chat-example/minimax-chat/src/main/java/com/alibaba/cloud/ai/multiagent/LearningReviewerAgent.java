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

import com.alibaba.cloud.ai.agent.LearningAgentResult;
import org.springframework.stereotype.Component;

/**
 * Reviews whether the answer is useful enough for the learner.
 */
@Component
public class LearningReviewerAgent {

	public ReviewerOutput review(String message, LearningAgentResult result) {
		boolean hasAnswer = result.content() != null && !result.content().isBlank();
		boolean hasPractice = contains(result.content(), "测试") || contains(result.content(), "实践")
				|| contains(result.content(), "下一步");
		boolean hasTools = result.toolCalls() != null && !result.toolCalls().isEmpty();
		String detail = "回答非空：" + yesNo(hasAnswer) + "；包含实践/测试/下一步：" + yesNo(hasPractice)
				+ "；本轮工具调用：" + (result.toolCalls() == null ? 0 : result.toolCalls().size()) + " 次。";
		String advice = hasAnswer && hasPractice ? "ReviewerAgent 认为回答可以交付。"
				: "ReviewerAgent 建议补充更明确的实践任务、测试方法或下一步优化方向。";
		if (isProjectQuestion(message) && !hasTools) {
			advice = advice + " 当前问题涉及项目上下文，但工具调用较少，后续可加强 RAG/MCP 检索。";
		}
		return new ReviewerOutput(detail, advice);
	}

	private boolean contains(String text, String keyword) {
		return text != null && text.contains(keyword);
	}

	private boolean isProjectQuestion(String message) {
		return contains(message, "项目") || contains(message, "源码") || contains(message, "当前")
				|| contains(message, "Workflow") || contains(message, "Multi-Agent");
	}

	private String yesNo(boolean value) {
		return value ? "是" : "否";
	}

	public record ReviewerOutput(String detail, String advice) {
	}

}
