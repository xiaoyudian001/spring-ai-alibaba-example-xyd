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

package com.alibaba.cloud.ai.mcp;

import java.time.LocalDateTime;
import java.util.Map;

public record PendingMcpWrite(String userId, String operation, String resourceId, String topic, String title,
		String summary, String nextAction, LocalDateTime createdAt) {

	public Map<String, Object> arguments() {
		return Map.of(
				"id", this.resourceId,
				"topic", this.topic,
				"title", this.title,
				"summary", this.summary,
				"nextAction", this.nextAction);
	}

}
