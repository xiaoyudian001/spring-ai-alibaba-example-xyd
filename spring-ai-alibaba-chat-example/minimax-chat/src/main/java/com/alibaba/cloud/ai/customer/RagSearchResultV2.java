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
 * 智能客服 RAG 检索结果增强版，包含文档、chunk、召回分数、召回模式和召回主题，用于工作台调试展示。
 *
 * @param mode 检索模式，例如 LOCAL_KEYWORD 或 VECTOR_STORE
 * @param query 检索问题
 * @param expectedTopics 期望命中的主题集合
 * @param documents 命中的知识文档列表
 * @param chunkHits 命中的切分块列表，包含召回分数
 * @param documentRecallRate 文档级别召回率
 * @param chunkRecallRate Chunk 级别召回率
 * @param recallModes 使用的召回模式列表
 * @param baselineHits 本地关键词 baseline 命中的文档，用于和向量召回对比
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record RagSearchResultV2(String mode, String query, Set<String> expectedTopics,
List<CustomerKnowledgeDocumentV2> documents, List<ChunkHit> chunkHits, double documentRecallRate,
double chunkRecallRate, List<String> recallModes, List<CustomerKnowledgeDocumentV2> baselineHits) {

/**
 * 创建一个空的检索结果。
 * @return 空检索结果
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public static RagSearchResultV2 empty() {
return new RagSearchResultV2("NONE", "", Set.of(), List.of(), List.of(), 0.0, 0.0, List.of(), List.of());
}

/**
 * 生成适合前端调试展示的检索摘要。
 * @return 检索摘要
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public String summary() {
StringBuilder sb = new StringBuilder();
sb.append("检索模式：").append(this.mode).append("\n");
sb.append("检索问题：").append(this.query).append("\n");
sb.append("期望主题：")
.append(this.expectedTopics.isEmpty() ? "暂无" : String.join("、", this.expectedTopics))
.append("\n");
sb.append("文档召回率：").append(String.format("%.2f%%", this.documentRecallRate * 100)).append("\n");
sb.append("Chunk召回率：").append(String.format("%.2f%%", this.chunkRecallRate * 100)).append("\n");
sb.append("命中文档数：").append(this.documents.size()).append("\n");
sb.append("命中Chunk数：").append(this.chunkHits.size()).append("\n");
if (!this.recallModes.isEmpty()) {
sb.append("召回模式：").append(String.join(" + ", this.recallModes)).append("\n");
}
sb.append("\n=== 命中文档 ===\n");
for (int i = 0; i < this.documents.size(); i++) {
CustomerKnowledgeDocumentV2 doc = this.documents.get(i);
sb.append((i + 1)).append(". ").append(doc.title()).append(" [").append(doc.groupId()).append("]\n");
sb.append("   ID：").append(doc.id()).append("\n");
sb.append("   主题：").append(doc.topic()).append("\n");
sb.append("   版本：").append(doc.version()).append("\n");
sb.append("   维护人：").append(doc.maintainer() != null ? doc.maintainer() : "未知").append("\n");
sb.append("   摘要：").append(doc.summary()).append("\n");
}
if (!this.chunkHits.isEmpty()) {
sb.append("\n=== 命中 Chunk ===\n");
for (int i = 0; i < this.chunkHits.size(); i++) {
ChunkHit hit = this.chunkHits.get(i);
sb.append((i + 1)).append(". [分数：").append(String.format("%.4f", hit.score()))
.append("] ")
.append(hit.chunk().documentId()).append("-").append(hit.chunk().chunkIndex())
.append("\n");
sb.append("   内容：").append(hit.chunk().summary()).append("\n");
}
}
if (this.documents.isEmpty() && this.chunkHits.isEmpty()) {
sb.append("未命中明确客服知识，建议补充商品说明、售后政策或渠道回复规范。\n");
}
if (!this.baselineHits.isEmpty()) {
sb.append("\n=== Baseline 对比（本地关键词召回）===\n");
sb.append("Baseline 命中文档数：").append(this.baselineHits.size()).append("\n");
}
return sb.toString();
}

/**
 * 判断是否有检索结果。
 * @return true 表示有命中的文档或 chunk
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public boolean hasResults() {
return !this.documents.isEmpty() || !this.chunkHits.isEmpty();
}

/**
 * 判断是否使用了真实向量库。
 * @return true 表示使用了 VECTOR_STORE 模式
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public boolean isVectorStoreMode() {
return "VECTOR_STORE".equals(this.mode);
}

/**
 * 命中的切分块，包含 chunk 本身和召回分数。
 *
 * @param chunk 知识切分块
 * @param score 召回分数，0.0 到 1.0 之间
 * @param hitTopics 命中的主题集合
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record ChunkHit(CustomerKnowledgeChunk chunk, double score, Set<String> hitTopics) {

/**
 * 创建召回分数为 1.0 的 chunk hit。
 * @param chunk 知识切分块
 * @param hitTopics 命中的主题集合
 * @return ChunkHit
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public static ChunkHit of(CustomerKnowledgeChunk chunk, Set<String> hitTopics) {
return new ChunkHit(chunk, 1.0, hitTopics);
}

/**
 * 创建指定召回分数的 chunk hit。
 * @param chunk 知识切分块
 * @param score 召回分数
 * @param hitTopics 命中的主题集合
 * @return ChunkHit
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public static ChunkHit of(CustomerKnowledgeChunk chunk, double score, Set<String> hitTopics) {
return new ChunkHit(chunk, score, hitTopics);
}

}

}
