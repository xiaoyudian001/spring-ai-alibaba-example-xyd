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

import java.time.Instant;

/**
 * 智能客服知识文档切分块，代表文档切分后的单个 chunk，用于精确检索和召回展示。
 *
 * @param id Chunk 唯一标识，格式建议 documentId-chunkIndex，例如 refund-policy-v1-0
 * @param documentId 所属文档 ID，用于关联父文档
 * @param chunkIndex 切分块序号，从 0 开始，用于保持块顺序
 * @param content 切分块正文内容
 * @param keywords 切分块关键词，用于本地关键词检索匹配
 * @param createdAt 创建时间
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record CustomerKnowledgeChunk(String id, String documentId, int chunkIndex, String content,
java.util.Set<String> keywords, Instant createdAt) {

/**
 * 创建知识切分块，默认使用当前时间作为创建时间。
 * @param id Chunk 唯一标识
 * @param documentId 所属文档 ID
 * @param chunkIndex 切分块序号
 * @param content 切分块正文
 * @param keywords 切分块关键词
 * @return 知识切分块
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public static CustomerKnowledgeChunk of(String id, String documentId, int chunkIndex, String content,
java.util.Set<String> keywords) {
return new CustomerKnowledgeChunk(id, documentId, chunkIndex, content, keywords, Instant.now());
}

/**
 * 生成切分块的检索摘要。
 * @return 内容前150字的摘要
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public String summary() {
if (this.content == null || this.content.isBlank()) {
return "";
}
int len = Math.min(150, this.content.length());
return this.content.substring(0, len) + (this.content.length() > 150 ? "..." : "");
}

}
