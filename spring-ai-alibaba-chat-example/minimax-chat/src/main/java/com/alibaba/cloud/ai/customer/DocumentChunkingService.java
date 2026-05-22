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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能客服文档切分服务，将长文档按段落或固定长度切分为多个 chunk，便于精确检索和向量入库。
 * <p>
 * 切分策略支持按段落切分和按固定字数切分两种模式，优先使用段落切分以保持语义完整。
 * 每个 chunk 会继承文档的关键词，并尝试从 chunk 内容中提取额外关键词。
 *
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public class DocumentChunkingService {

private static final int DEFAULT_CHUNK_SIZE = 500;

private static final int DEFAULT_CHUNK_OVERLAP = 50;

private static final int MIN_CHUNK_LENGTH = 50;

private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("[\\n\\r]+");

private static final Pattern SENTENCE_SPLITTER = Pattern.compile("[。.!?！?]+");

private final int chunkSize;

private final int chunkOverlap;

/**
 * 创建文档切分服务，使用默认切分参数。
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public DocumentChunkingService() {
this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
}

/**
 * 创建文档切分服务，指定切分参数。
 * @param chunkSize 每个 chunk 的最大字数
 * @param chunkOverlap 相邻 chunk 之间的重叠字数，用于保持上下文连续性
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public DocumentChunkingService(int chunkSize, int chunkOverlap) {
this.chunkSize = chunkSize > MIN_CHUNK_LENGTH ? chunkSize : DEFAULT_CHUNK_SIZE;
this.chunkOverlap = Math.min(chunkOverlap, this.chunkSize / 4);
}

/**
 * 将知识文档切分为多个 chunk。
 * @param document 知识文档
 * @return 切分后的 chunk 列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public List<CustomerKnowledgeChunk> chunk(CustomerKnowledgeDocumentV2 document) {
if (document == null || document.content() == null || document.content().isBlank()) {
return List.of();
}
String content = document.content();
List<String> paragraphs = splitParagraphs(content);
List<CustomerKnowledgeChunk> chunks = new ArrayList<>();
int chunkIndex = 0;
StringBuilder buffer = new StringBuilder();
Set<String> inheritedKeywords = document.keywords() != null ? document.keywords() : Set.of();
for (String paragraph : paragraphs) {
if (paragraph.isBlank()) {
continue;
}
if (buffer.length() + paragraph.length() <= this.chunkSize) {
buffer.append(paragraph).append("\n");
}
else {
if (buffer.length() > MIN_CHUNK_LENGTH) {
chunks.add(createChunk(document.id(), chunkIndex++, buffer.toString().trim(), inheritedKeywords));
}
String overlapText = buffer.length() <= this.chunkOverlap ? buffer.toString()
: buffer.substring(buffer.length() - this.chunkOverlap);
buffer = new StringBuilder(overlapText).append(paragraph).append("\n");
}
}
if (buffer.length() > MIN_CHUNK_LENGTH) {
chunks.add(createChunk(document.id(), chunkIndex, buffer.toString().trim(), inheritedKeywords));
}
return chunks;
}

/**
 * 将知识文档按段落切分为多个 chunk，优先保持段落语义完整。
 * @param document 知识文档
 * @return 切分后的 chunk 列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public List<CustomerKnowledgeChunk> chunkByParagraph(CustomerKnowledgeDocumentV2 document) {
if (document == null || document.content() == null || document.content().isBlank()) {
return List.of();
}
List<String> paragraphs = splitParagraphs(document.content());
List<CustomerKnowledgeChunk> chunks = new ArrayList<>();
Set<String> inheritedKeywords = document.keywords() != null ? document.keywords() : Set.of();
String currentChunk = "";
int chunkIndex = 0;
for (String paragraph : paragraphs) {
if (paragraph.isBlank()) {
continue;
}
if (currentChunk.isEmpty()) {
currentChunk = paragraph;
}
else if (currentChunk.length() + paragraph.length() + 1 <= this.chunkSize) {
currentChunk = currentChunk + "\n" + paragraph;
}
else {
chunks.add(createChunk(document.id(), chunkIndex++, currentChunk, inheritedKeywords));
currentChunk = paragraph;
}
}
if (!currentChunk.isBlank()) {
chunks.add(createChunk(document.id(), chunkIndex, currentChunk, inheritedKeywords));
}
return chunks;
}

/**
 * 按固定字数切分文档，适用于内容结构不清晰的文档。
 * @param content 文档内容
 * @param documentId 文档 ID
 * @param keywords 文档关键词
 * @return 切分后的 chunk 列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public List<CustomerKnowledgeChunk> chunkByFixedSize(String content, String documentId, Set<String> keywords) {
if (content == null || content.isBlank()) {
return List.of();
}
List<CustomerKnowledgeChunk> chunks = new ArrayList<>();
int length = content.length();
int index = 0;
while (index < length) {
int end = Math.min(index + this.chunkSize, length);
if (end < length) {
int nearestBreak = findNearestBreak(content, index, end);
if (nearestBreak > index) {
end = nearestBreak;
}
}
String chunkContent = content.substring(index, end).trim();
if (chunkContent.length() >= MIN_CHUNK_LENGTH) {
chunks.add(createChunk(documentId, index / this.chunkSize, chunkContent, keywords));
}
index = end - this.chunkOverlap;
if (index <= 0) {
index = end;
}
if (index >= length) {
break;
}
}
return chunks;
}

/**
 * 创建单个 chunk，自动提取内容关键词。
 * @param documentId 文档 ID
 * @param chunkIndex 切分块序号
 * @param content 切分块内容
 * @param inheritedKeywords 继承自文档的关键词
 * @return 知识切分块
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private CustomerKnowledgeChunk createChunk(String documentId, int chunkIndex, String content,
Set<String> inheritedKeywords) {
String id = documentId + "-" + chunkIndex;
Set<String> keywords = extractKeywords(content, inheritedKeywords);
return CustomerKnowledgeChunk.of(id, documentId, chunkIndex, content, keywords);
}

/**
 * 按段落分隔文本。
 * @param content 文档内容
 * @return 段落列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private List<String> splitParagraphs(String content) {
return Arrays.stream(PARAGRAPH_SPLITTER.split(content))
.map(String::trim)
.filter(s -> !s.isBlank())
.collect(Collectors.toList());
}

/**
 * 从文本内容中提取关键词，结合继承关键词。
 * @param content 文本内容
 * @param inheritedKeywords 继承关键词
 * @return 合并后的关键词集合
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private Set<String> extractKeywords(String content, Set<String> inheritedKeywords) {
Set<String> keywords = new HashSet<>();
if (inheritedKeywords != null) {
keywords.addAll(inheritedKeywords);
}
if (content != null && content.length() > 20) {
String shortContent = content.length() > 2000 ? content.substring(0, 2000) : content;
for (String sentence : SENTENCE_SPLITTER.split(shortContent)) {
String trimmed = sentence.trim();
if (trimmed.length() >= 4 && trimmed.length() <= 30) {
keywords.add(trimmed);
}
}
}
return keywords;
}

/**
 * 找到最接近 end 的断点，优先在句号处切分。
 * @param text 文本内容
 * @param start 起始位置
 * @param end 目标结束位置
 * @return 实际结束位置
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private int findNearestBreak(String text, int start, int end) {
int bestBreak = end;
for (int i = start + this.chunkSize / 2; i < end; i++) {
char c = text.charAt(i);
if (c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?') {
bestBreak = i + 1;
break;
}
}
return bestBreak;
}

}
