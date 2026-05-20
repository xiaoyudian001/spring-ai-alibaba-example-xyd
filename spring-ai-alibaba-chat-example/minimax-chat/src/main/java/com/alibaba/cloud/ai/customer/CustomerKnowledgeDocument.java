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

import java.util.Set;

/**
 * 智能客服知识文档，用于统一承载 RAG 检索、召回率评估和后续向量库入库所需的内容。
 *
 * @param id 文档唯一标识
 * @param title 文档标题
 * @param topic 业务主题
 * @param content 文档内容
 * @param keywords 召回关键词集合
 * @author xyd
 * @date 2026-05-19 13:31:27
 */
public record CustomerKnowledgeDocument(String id, String title, String topic, String content, Set<String> keywords) {
}
