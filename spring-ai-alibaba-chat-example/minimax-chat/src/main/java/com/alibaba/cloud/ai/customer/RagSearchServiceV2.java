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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.customer.RagSearchResultV2.ChunkHit;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 智能客服 RAG 检索服务 V2，支持本地关键词 baseline 检索和真实向量库检索，并返回 chunk 级别的命中和召回分数。
 * <p>
 * 该服务实现了完整的 RAG 流程：Document -> Chunk -> Embedding -> Retrieval，并保留了本地关键词召回作为 baseline 用于效果对比。
 *
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@Service
public class RagSearchServiceV2 {

private static final String LOCAL_KEYWORD_MODE = "LOCAL_KEYWORD";

private static final String VECTOR_STORE_MODE = "VECTOR_STORE";

private static final String HYBRID_MODE = "HYBRID";

private final ObjectProvider<VectorStore> vectorStoreProvider;

private final KnowledgeManagementService knowledgeService;

private final boolean vectorEnabled;

private final boolean compareBaseline;

/**
 * 创建 RAG 检索服务 V2。
 * @param vectorStoreProvider Spring AI VectorStore 提供器
 * @param knowledgeService 知识管理服务
 * @param vectorEnabled 是否启用真实向量库检索
 * @param compareBaseline 是否同时执行 baseline 检索用于对比
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public RagSearchServiceV2(ObjectProvider<VectorStore> vectorStoreProvider, KnowledgeManagementService knowledgeService,
@Value("${minimax.customer.rag.vector-enabled:false}") boolean vectorEnabled,
@Value("${minimax.customer.rag.compare-baseline:true}") boolean compareBaseline) {
this.vectorStoreProvider = vectorStoreProvider;
this.knowledgeService = knowledgeService;
this.vectorEnabled = vectorEnabled;
this.compareBaseline = compareBaseline;
}

/**
 * 初始化真实向量库数据，把 MySQL 中已启用的知识文档和 Chunk 写入 VectorStore。
 *
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
@PostConstruct
public void initializeVectorStore() {
if (!this.vectorEnabled) {
return;
}
this.vectorStoreProvider.ifAvailable(vectorStore -> {
List<Document> documents = this.knowledgeService.findAllEnabled().stream()
.flatMap(item -> toVectorDocuments(item).stream())
.toList();
if (!documents.isEmpty()) {
vectorStore.add(documents);
}
});
}

/**
 * 执行 RAG 检索，支持向量库检索和本地关键词 baseline 对比。
 * @param query 检索问题
 * @param limit 返回结果数量
 * @param expectedTopics 期望命中的主题
 * @return RAG 检索结果 V2
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public RagSearchResultV2 search(String query, int limit, Set<String> expectedTopics) {
if (query == null || query.isBlank()) {
return RagSearchResultV2.empty();
}
int safeLimit = Math.max(1, Math.min(limit, 10));
Set<String> safeExpected = normalizeTopics(expectedTopics);
Optional<VectorStore> vectorStore = getVectorStore();
List<String> recallModes = new ArrayList<>();
List<CustomerKnowledgeDocumentV2> allDocuments = new ArrayList<>();
List<ChunkHit> allChunkHits = new ArrayList<>();
if (vectorStore.isPresent()) {
recallModes.add("VECTOR_STORE");
SearchRequest request = SearchRequest.builder().query(query).topK(safeLimit).build();
List<Document> vectorResults = vectorStore.get().similaritySearch(request);
for (Document doc : vectorResults) {
String docId = extractDocumentId(doc);
this.knowledgeService.findById(docId).ifPresent(d -> {
allDocuments.add(d);
for (CustomerKnowledgeChunk chunk : d.chunks()) {
allChunkHits.add(ChunkHit.of(chunk, 1.0, Set.of(d.topic())));
}
});
}
}
if (this.compareBaseline || !vectorStore.isPresent()) {
recallModes.add("LOCAL_KEYWORD");
List<CustomerKnowledgeDocumentV2> baselineHits = localKeywordSearch(query, safeLimit);
for (CustomerKnowledgeDocumentV2 doc : baselineHits) {
if (!allDocuments.contains(doc)) {
allDocuments.add(doc);
}
for (CustomerKnowledgeChunk chunk : doc.chunks()) {
if (!containsChunk(allChunkHits, chunk)) {
allChunkHits.add(ChunkHit.of(chunk, safeExpected.contains(doc.topic()) ? 1.0 : 0.5,
Set.of(doc.topic())));
}
}
}
}
String mode = determineMode(vectorStore.isPresent(), recallModes);
double documentRecallRate = calculateRecallRate(safeExpected, allDocuments);
double chunkRecallRate = calculateChunkRecallRate(safeExpected, allChunkHits);
List<CustomerKnowledgeDocumentV2> baselineHits = this.compareBaseline && vectorStore.isPresent()
? localKeywordSearch(query, safeLimit) : List.of();
return new RagSearchResultV2(mode, query, safeExpected, allDocuments, allChunkHits, documentRecallRate,
chunkRecallRate, recallModes, baselineHits);
}

/**
 * 仅执行本地关键词检索（baseline），不启用向量库。
 * @param query 检索问题
 * @param limit 返回结果数量
 * @return 本地关键词命中的文档
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public List<CustomerKnowledgeDocumentV2> localKeywordSearch(String query, int limit) {
List<CustomerKnowledgeDocumentV2> enabledDocs = this.knowledgeService.findAllEnabled();
String normalizedQuery = query.toLowerCase();
return enabledDocs.stream()
.filter(doc -> matchesKeyword(doc, normalizedQuery) || matchesTopic(doc, normalizedQuery))
.sorted(Comparator.comparingInt((CustomerKnowledgeDocumentV2 doc) -> scoreDocument(doc, normalizedQuery))
.reversed())
.limit(limit)
.collect(Collectors.toList());
}

/**
 * 获取当前 RAG 状态。
 * @return RAG 运行状态
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public RagStatus status() {
boolean hasVectorStore = this.vectorStoreProvider.stream().findFirst().isPresent();
boolean vectorEnabled = this.vectorEnabled && hasVectorStore;
String mode = vectorEnabled ? VECTOR_STORE_MODE : LOCAL_KEYWORD_MODE;
String message = vectorEnabled ? "已启用真实向量库 + 本地关键词 baseline"
: "当前使用本地关键词检索（baseline）";
int documentCount = this.knowledgeService.findAllEnabled().size();
return new RagStatus(vectorEnabled, hasVectorStore, this.compareBaseline, mode, documentCount, message);
}

/**
 * 获取可用的 VectorStore。
 * @return VectorStore，不可用时返回空
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private Optional<VectorStore> getVectorStore() {
return this.vectorEnabled ? this.vectorStoreProvider.stream().findFirst() : Optional.empty();
}

/**
 * 判断文档是否匹配关键词或主题。
 * @param doc 知识文档
 * @param query 检索词（小写）
 * @return true 表示匹配
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private boolean matchesKeyword(CustomerKnowledgeDocumentV2 doc, String query) {
if (doc.keywords() != null) {
for (String keyword : doc.keywords()) {
if (keyword.toLowerCase().contains(query) || query.contains(keyword.toLowerCase())) {
return true;
}
}
}
return false;
}

/**
 * 判断文档是否匹配主题。
 * @param doc 知识文档
 * @param query 检索词（小写）
 * @return true 表示匹配
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private boolean matchesTopic(CustomerKnowledgeDocumentV2 doc, String query) {
return doc.topic().toLowerCase().contains(query) || doc.title().toLowerCase().contains(query)
|| doc.content().toLowerCase().contains(query);
}

/**
 * 计算文档匹配分数。
 * @param doc 知识文档
 * @param query 检索词（小写）
 * @return 匹配分数
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private int scoreDocument(CustomerKnowledgeDocumentV2 doc, String query) {
int score = 0;
if (doc.topic().toLowerCase().contains(query)) {
score += 10;
}
if (doc.title().toLowerCase().contains(query)) {
score += 5;
}
if (doc.keywords() != null) {
for (String keyword : doc.keywords()) {
if (keyword.toLowerCase().contains(query)) {
score += 3;
}
}
}
if (doc.content().toLowerCase().contains(query)) {
score += 1;
}
return score;
}

/**
 * 规范化主题集合。
 * @param topics 原始主题
 * @return 规范化后的主题集合
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private Set<String> normalizeTopics(Set<String> topics) {
if (topics == null || topics.isEmpty()) {
return Set.of();
}
return topics.stream().map(String::toLowerCase).map(String::trim).filter(s -> !s.isBlank())
.collect(Collectors.toCollection(LinkedHashSet::new));
}

/**
 * 判断是否已包含某个 chunk。
 * @param hits ChunkHit 列表
 * @param chunk 待检查的 chunk
 * @return true 表示已包含
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private boolean containsChunk(List<ChunkHit> hits, CustomerKnowledgeChunk chunk) {
return hits.stream().anyMatch(h -> h.chunk().id().equals(chunk.id()));
}

/**
 * 计算文档级别召回率。
 * @param expected 期望主题
 * @param documents 命中的文档
 * @return 召回率
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private double calculateRecallRate(Set<String> expected, List<CustomerKnowledgeDocumentV2> documents) {
if (expected.isEmpty()) {
return 1.0;
}
Set<String> hitTopics = documents.stream().map(CustomerKnowledgeDocumentV2::topic)
.map(String::toLowerCase).collect(Collectors.toCollection(LinkedHashSet::new));
long hitCount = expected.stream().filter(hitTopics::contains).count();
return (double) hitCount / expected.size();
}

/**
 * 计算 Chunk 级别召回率。
 * @param expected 期望主题
 * @param chunkHits 命中的 chunk
 * @return 召回率
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private double calculateChunkRecallRate(Set<String> expected, List<ChunkHit> chunkHits) {
if (expected.isEmpty()) {
return 1.0;
}
Set<String> hitTopics = chunkHits.stream().flatMap(h -> h.hitTopics().stream()).map(String::toLowerCase)
.collect(Collectors.toCollection(LinkedHashSet::new));
long hitCount = expected.stream().filter(hitTopics::contains).count();
return (double) hitCount / expected.size();
}

/**
 * 确定检索模式。
 * @param hasVectorStore 是否有向量库
 * @param recallModes 召回模式列表
 * @return 检索模式
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private String determineMode(boolean hasVectorStore, List<String> recallModes) {
if (hasVectorStore && recallModes.size() > 1) {
return HYBRID_MODE;
}
if (hasVectorStore) {
return VECTOR_STORE_MODE;
}
return LOCAL_KEYWORD_MODE;
}

/**
 * 将业务知识文档转换为 Spring AI VectorStore 可写入的 Document 列表。
 * @param document 业务知识文档
 * @return VectorStore 文档列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private List<Document> toVectorDocuments(CustomerKnowledgeDocumentV2 document) {
if (document.chunks() == null || document.chunks().isEmpty()) {
return List.of(new Document(document.id(), document.content(), Map.of("documentId", document.id(), "title",
document.title(), "topic", document.topic())));
}
return document.chunks().stream()
.map(chunk -> new Document(chunk.id(), chunk.content(), Map.of("documentId", document.id(), "chunkId",
chunk.id(), "title", document.title(), "topic", document.topic())))
.toList();
}

/**
 * 从 Spring AI Document 中提取业务文档 ID，优先读取 metadata.documentId，兼容旧的 document ID 写法。
 * @param document Spring AI Document
 * @return 业务文档 ID
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
private String extractDocumentId(Document document) {
if (document == null) {
return "";
}
Object metadataDocumentId = document.getMetadata().get("documentId");
String documentId = metadataDocumentId == null ? document.getId() : String.valueOf(metadataDocumentId);
int lastSlash = documentId.lastIndexOf('/');
if (lastSlash >= 0 && lastSlash < documentId.length() - 1) {
return documentId.substring(lastSlash + 1);
}
return documentId;
}

/**
 * RAG 运行状态。
 *
 * @param vectorEnabled 是否启用向量库
 * @param hasVectorStore 是否有可用向量库
 * @param compareBaseline 是否启用 baseline 对比
 * @param mode 当前检索模式
 * @param documentCount 已启用文档数量
 * @param message 状态描述
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record RagStatus(boolean vectorEnabled, boolean hasVectorStore, boolean compareBaseline, String mode,
int documentCount, String message) {

}

}
