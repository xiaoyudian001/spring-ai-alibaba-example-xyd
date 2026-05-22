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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.customer.RagSearchServiceV2.RagStatus;
import org.springframework.stereotype.Service;

/**
 * 客服 RAG 兼容门面，保留早期接口形态，但底层统一使用 MySQL 知识库、Chunk 和 RAG V2 检索。
 *
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
@Service
public class CustomerPolicyRagService {

	private final KnowledgeManagementService knowledgeManagementService;

	private final RagSearchServiceV2 ragSearchService;

	/**
	 * 创建客服 RAG 兼容门面。
	 * @param knowledgeManagementService MySQL 知识治理服务
	 * @param ragSearchService RAG V2 检索服务
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public CustomerPolicyRagService(KnowledgeManagementService knowledgeManagementService,
			RagSearchServiceV2 ragSearchService) {
		this.knowledgeManagementService = knowledgeManagementService;
		this.ragSearchService = ragSearchService;
	}

	/**
	 * 根据用户问题检索客服政策、平台规则或话术知识。
	 * @param query 用户问题或检索关键词
	 * @param limit 返回结果数量
	 * @return 检索结果摘要
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public String search(String query, Integer limit) {
		return this.ragSearchService.search(query, safeLimit(limit), Set.of()).summary();
	}

	/**
	 * 根据用户问题检索客服知识，并返回兼容早期页面的召回率、命中主题和向量库状态。
	 * @param query 用户问题或检索关键词
	 * @param limit 返回结果数量
	 * @param expectedTopics 期望命中的主题集合
	 * @return 客服 RAG 检索结果
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public CustomerPolicySearchResult searchWithMetrics(String query, Integer limit, Set<String> expectedTopics) {
		RagSearchResultV2 result = this.ragSearchService.search(query, safeLimit(limit), expectedTopics);
		Set<String> hitTopics = result.documents().stream()
				.map(CustomerKnowledgeDocumentV2::topic)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		boolean realVectorStoreUsed = result.recallModes().stream().anyMatch("VECTOR_STORE"::equals);
		return new CustomerPolicySearchResult(result.mode(), realVectorStoreUsed, result.query(),
				result.expectedTopics(), hitTopics, result.documentRecallRate(), toLegacyDocuments(result.documents()));
	}

	/**
	 * 返回当前知识库覆盖的主题，用于调试知识覆盖率。
	 * @return 知识主题集合
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public Set<String> topics() {
		return this.knowledgeManagementService.findAllEnabled().stream()
				.map(CustomerKnowledgeDocumentV2::topic)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * 返回当前客服知识库中的全部知识文档，兼容早期页面的文档结构。
	 * @return 客服知识文档列表
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public synchronized List<CustomerKnowledgeDocument> documents() {
		return toLegacyDocuments(this.knowledgeManagementService.findAll());
	}

	/**
	 * 查询当前客服 RAG 运行状态，用于工作台确认本地关键词检索、真实 VectorStore 和 MySQL 知识库状态。
	 * @return 客服 RAG 运行状态
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public CustomerPolicyRagStatus status() {
		RagStatus status = this.ragSearchService.status();
		int topicCount = topics().size();
		return new CustomerPolicyRagStatus(status.vectorEnabled(), status.hasVectorStore(), status.mode(),
				"MYSQL:knowledge_documents/knowledge_chunks", status.documentCount(), topicCount, status.message());
	}

	/**
	 * 新增或更新一条自定义客服知识，并写入 MySQL 文档表和 Chunk 表。
	 * @param request 知识新增或更新请求
	 * @return 保存后的客服知识文档兼容视图
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public synchronized CustomerKnowledgeDocument upsertCustomDocument(CustomerKnowledgeUpsertRequest request) {
		CustomerKnowledgeDocumentV2 document = toV2Document(request);
		CustomerKnowledgeDocumentV2 saved = this.knowledgeManagementService.findById(document.id())
				.flatMap(existing -> this.knowledgeManagementService.updateDocument(existing.id(), document))
				.orElseGet(() -> this.knowledgeManagementService.createDocument(document));
		return toLegacyDocument(saved);
	}

	/**
	 * 删除一条客服知识文档，并同步删除关联 Chunk。
	 * @param id 文档唯一标识
	 * @return 删除成功返回 true
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public synchronized boolean deleteCustomDocument(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		return this.knowledgeManagementService.deleteDocument(id.trim());
	}

	private int safeLimit(Integer limit) {
		return limit == null || limit <= 0 ? 5 : Math.min(limit, 10);
	}

	/**
	 * 将 V2 知识文档列表转换为旧版页面兼容结构。
	 * @param documents V2 知识文档列表
	 * @return 旧版客服知识文档列表
	 * @author xyd
	 * @date 2026-05-22 12:12:30
	 */
	private List<CustomerKnowledgeDocument> toLegacyDocuments(List<CustomerKnowledgeDocumentV2> documents) {
		return documents.stream().map(this::toLegacyDocument).toList();
	}

	/**
	 * 将单个 V2 知识文档转换为旧版页面兼容结构。
	 * @param document V2 知识文档
	 * @return 旧版客服知识文档
	 * @author xyd
	 * @date 2026-05-22 12:12:30
	 */
	private CustomerKnowledgeDocument toLegacyDocument(CustomerKnowledgeDocumentV2 document) {
		return new CustomerKnowledgeDocument(document.id(), document.title(), document.topic(), document.content(),
				document.keywords());
	}

	/**
	 * 将旧版知识新增请求转换为 V2 知识文档，统一写入 MySQL 文档和 Chunk 流程。
	 * @param request 旧版知识新增请求
	 * @return V2 知识文档
	 * @author xyd
	 * @date 2026-05-22 12:12:30
	 */
	private CustomerKnowledgeDocumentV2 toV2Document(CustomerKnowledgeUpsertRequest request) {
		String topic = normalize(request == null ? "" : request.topic(), "custom");
		String title = blankDefault(request == null ? "" : request.title(), "自定义客服知识");
		String content = blankDefault(request == null ? "" : request.content(), "暂无内容");
		String id = normalize(request == null ? "" : request.id(), "");
		if (id.isBlank()) {
			id = "custom-" + Math.abs((title + topic + content).hashCode());
		}
		Set<String> keywords = request == null || request.keywords() == null ? Set.of()
				: request.keywords().stream().map(item -> normalize(item, ""))
						.filter(item -> !item.isBlank())
						.collect(Collectors.toCollection(LinkedHashSet::new));
		if (keywords.isEmpty()) {
			keywords = new LinkedHashSet<>(List.of(topic, title));
		}
		return CustomerKnowledgeDocumentV2.of(id, topic, title, topic, content, keywords, "1.0", true, "dashboard");
	}

	/**
	 * 规范化字符串值，主要用于 ID、主题和关键词生成。
	 * @param value 原始值
	 * @param defaultValue 默认值
	 * @return 规范化后的字符串
	 * @author xyd
	 * @date 2026-05-22 12:12:30
	 */
	private String normalize(String value, String defaultValue) {
		String text = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
		return text.isBlank() ? defaultValue : text;
	}

	/**
	 * 当字符串为空时返回默认值，否则返回去除首尾空白后的原值。
	 * @param value 原始值
	 * @param defaultValue 默认值
	 * @return 非空字符串
	 * @author xyd
	 * @date 2026-05-22 12:12:30
	 */
	private String blankDefault(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	/**
	 * 客服 RAG 运行状态，用于前端展示真实向量库开关、可用性和知识库规模。
	 *
	 * @param vectorEnabled 是否开启真实向量库检索开关
	 * @param realVectorStoreAvailable 是否存在真实 VectorStore Bean
	 * @param mode 当前实际检索模式
	 * @param knowledgeFile 知识库位置，合并后固定为 MySQL 表说明
	 * @param documentCount 当前启用知识文档数量
	 * @param topicCount 当前知识主题数量
	 * @param message 状态说明
	 * @author xyd
	 * @date 2026-05-22 11:36:13
	 */
	public record CustomerPolicyRagStatus(boolean vectorEnabled, boolean realVectorStoreAvailable, String mode,
			String knowledgeFile, int documentCount, int topicCount, String message) {
	}

}
