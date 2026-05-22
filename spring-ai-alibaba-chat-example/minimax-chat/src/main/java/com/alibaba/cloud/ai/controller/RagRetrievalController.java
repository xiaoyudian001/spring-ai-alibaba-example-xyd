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

package com.alibaba.cloud.ai.controller;

import java.util.List;
import java.util.Set;

import com.alibaba.cloud.ai.customer.CustomerKnowledgeChunk;
import com.alibaba.cloud.ai.customer.CustomerKnowledgeDocumentV2;
import com.alibaba.cloud.ai.customer.KnowledgeManagementService;
import com.alibaba.cloud.ai.customer.KnowledgeManagementService.KnowledgeGroupOverview;
import com.alibaba.cloud.ai.customer.RagSearchResultV2;
import com.alibaba.cloud.ai.customer.RagSearchServiceV2;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能客服 RAG 检索增强 REST API 控制器，提供带召回分数、Chunk 粒度和 baseline 对比的检索功能。
 *
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@RestController
@RequestMapping("/minimax/rag")
public class RagRetrievalController {

private final RagSearchServiceV2 ragSearchService;

private final KnowledgeManagementService knowledgeService;

/**
 * 创建 RAG 检索增强控制器。
 * @param ragSearchService RAG 检索服务 V2
 * @param knowledgeService 知识管理服务
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
public RagRetrievalController(RagSearchServiceV2 ragSearchService, KnowledgeManagementService knowledgeService) {
this.ragSearchService = ragSearchService;
this.knowledgeService = knowledgeService;
}

/**
 * 执行 RAG 检索，返回命中的文档、Chunk、召回分数、召回率和模式对比信息。
 * @param query 检索问题
 * @param limit 返回结果数量
 * @param expectedTopics 期望命中的主题，逗号分隔
 * @return RAG 检索结果 V2
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public RagSearchResultV2 search(@RequestParam("query") String query,
@RequestParam(value = "limit", defaultValue = "5") int limit,
@RequestParam(value = "expectedTopics", required = false) String expectedTopics) {
Set<String> topics = parseTopics(expectedTopics);
return this.ragSearchService.search(query, limit, topics);
}

/**
 * 执行本地关键词检索（baseline），不启用向量库，用于和真实向量召回效果对比。
 * @param query 检索问题
 * @param limit 返回结果数量
 * @return 本地关键词命中的文档
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping(value = "/search/baseline", produces = MediaType.APPLICATION_JSON_VALUE)
public List<CustomerKnowledgeDocumentV2> searchBaseline(@RequestParam("query") String query,
@RequestParam(value = "limit", defaultValue = "5") int limit) {
return this.ragSearchService.localKeywordSearch(query, limit);
}

/**
 * 查询指定文档的 Chunk 列表，用于展示文档切分后的详细内容和来源。
 * @param documentId 文档 ID
 * @return 文档切分块列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping(value = "/documents/{documentId}/chunks", produces = MediaType.APPLICATION_JSON_VALUE)
public List<CustomerKnowledgeChunk> getDocumentChunks(@PathVariable("documentId") String documentId) {
return this.knowledgeService.findById(documentId).map(CustomerKnowledgeDocumentV2::chunks)
.orElse(List.of());
}

/**
 * 查询所有已启用的知识文档，用于展示当前知识库覆盖范围。
 * @return 已启用的知识文档列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public List<CustomerKnowledgeDocumentV2> getEnabledDocuments() {
return this.knowledgeService.findAllEnabled();
}

/**
 * 查询所有知识分组及其文档数量。
 * @return 分组概览列表
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping(value = "/groups", produces = MediaType.APPLICATION_JSON_VALUE)
public List<KnowledgeGroupOverview> getGroupOverviews() {
return this.knowledgeService.getGroupOverviews();
}

/**
 * 查询 RAG 当前运行状态，包括是否启用向量库、文档数量和检索模式。
 * @return RAG 运行状态
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
@GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
public RagSearchServiceV2.RagStatus getStatus() {
return this.ragSearchService.status();
}

/**
 * 解析逗号分隔的主题字符串。
 * @param topics 逗号分隔的主题字符串
 * @return 主题集合
 * @author xyd
 * @date 2026-05-22 12:30:00
 */
private Set<String> parseTopics(String topics) {
if (topics == null || topics.isBlank()) {
return Set.of();
}
return java.util.Arrays.stream(topics.split("[,，\\s]+")).map(String::trim).filter(s -> !s.isBlank())
.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
}

}
