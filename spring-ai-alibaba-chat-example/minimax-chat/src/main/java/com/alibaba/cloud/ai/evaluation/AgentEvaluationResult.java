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

package com.alibaba.cloud.ai.evaluation;

import java.time.Instant;
import java.util.List;

/**
 * Rule-based evaluation result for one persisted agent run report.
 */
public record AgentEvaluationResult(String id, String reportId, Instant createdAt, String userId, String chainMode,
		String intent, int score, int maxScore, String level, boolean passed, List<EvaluationCheck> checks) {

	public record EvaluationCheck(String name, boolean applicable, boolean passed, String detail) {
	}

}
