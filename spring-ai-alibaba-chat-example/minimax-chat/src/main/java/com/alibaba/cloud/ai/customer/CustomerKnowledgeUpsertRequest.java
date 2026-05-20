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

/**
 * 智能客服知识新增或更新请求，用于页面向客服 RAG 知识库写入自定义业务知识。
 *
 * @param id 文档唯一标识，空值时由服务端自动生成
 * @param title 文档标题
 * @param topic 业务主题，例如 refund、shipping、price、xianyu、wechat
 * @param content 文档内容
 * @param keywords 召回关键词列表
 * @author xyd
 * @date 2026-05-19 23:48:12
 */
public record CustomerKnowledgeUpsertRequest(String id, String title, String topic, String content,
		List<String> keywords) {
}
