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

import com.alibaba.cloud.ai.memory.LearningMemory;
import com.alibaba.cloud.ai.planner.LearningIntent;
import com.alibaba.cloud.ai.planner.LearningIntentPlanner;
import org.springframework.stereotype.Component;

/**
 * Plans the learning task for the multi-agent chain.
 */
@Component
public class LearningPlannerAgent {

	private final LearningIntentPlanner intentPlanner;

	public LearningPlannerAgent(LearningIntentPlanner intentPlanner) {
		this.intentPlanner = intentPlanner;
	}

	public PlannerOutput plan(String message, LearningMemory memory) {
		LearningIntent intent = this.intentPlanner.plan(message);
		String detail = "识别意图：" + intent + "；学习状态：" + (memory == null ? "暂无 Memory" : memory.summary())
				+ "；子任务：明确学习目标、收集项目证据、组织学习回答、评审回答质量。";
		return new PlannerOutput(intent, detail);
	}

	public record PlannerOutput(LearningIntent intent, String detail) {
	}

}
