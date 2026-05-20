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
import java.util.Set;

/**
 * 智能客服 RAG 检索结果，包含召回模式、召回率、命中文档和向量库状态。
 *
 * @param mode 检索模式，例如 LOCAL_KEYWORD 或 VECTOR_STORE
 * @param realVectorStoreAvailable 是否存在真实 VectorStore
 * @param query 检索问题
 * @param expectedTopics 期望命中的主题集合
 * @param hitTopics 实际命中的主题集合
 * @param recallRate 召回率
 * @param documents 命中的知识文档
 * @author xyd
 * @date 2026-05-19 13:31:27
 */
public record CustomerPolicySearchResult(String mode, boolean realVectorStoreAvailable, String query,
		Set<String> expectedTopics, Set<String> hitTopics, double recallRate, List<CustomerKnowledgeDocument> documents) {

	/**
	 * 生成适合模型和前端调试区展示的检索摘要。
	 * @return 检索摘要
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public String summary() {
		StringBuilder builder = new StringBuilder();
		builder.append("RAG模式：").append(this.mode)
				.append("；真实向量库：").append(this.realVectorStoreAvailable)
				.append("；召回率：").append(String.format("%.2f", this.recallRate))
				.append("；命中主题：").append(this.hitTopics.isEmpty() ? "暂无" : String.join("、", this.hitTopics))
				.append("\n");
		for (CustomerKnowledgeDocument document : this.documents) {
			builder.append("- ").append(document.id()).append("｜").append(document.title())
					.append("｜主题：").append(document.topic()).append("\n")
					.append("  ").append(document.content()).append("\n");
		}
		if (this.documents.isEmpty()) {
			builder.append("未命中明确客服知识，建议补充商品说明、售后政策或渠道回复规范。");
		}
		return builder.toString().trim();
	}

}
