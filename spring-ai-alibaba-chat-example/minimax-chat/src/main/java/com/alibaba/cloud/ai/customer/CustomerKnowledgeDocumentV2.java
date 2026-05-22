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
import java.util.List;
import java.util.Set;

/**
 * 智能客服知识文档 V2 版本，支持知识分组、版本管理、启停控制等知识治理能力。
 *
 * @param id 文档唯一标识，格式建议使用 kebab-case，例如 refund-policy-v1
 * @param groupId 知识分组 ID，用于按业务领域归类知识，例如 refund、shipping、xianyu、wechat
 * @param title 文档标题，简洁明了，例如 退款政策、闲鱼回复规范
 * @param topic 业务主题，用于召回率统计和检索匹配
 * @param content 文档正文内容，支持多段落
 * @param keywords 召回关键词集合，用于本地高召回关键词检索
 * @param version 文档版本号，格式建议 major.minor，例如 1.0、1.1、2.0
 * @param enabled 是否启用，false 时该文档不参与检索和向量入库
 * @param maintainer 维护人，填写工号或姓名，便于知识归属追踪
 * @param createdAt 文档创建时间
 * @param updatedAt 文档更新时间
 * @param chunks 关联的文档切分块列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record CustomerKnowledgeDocumentV2(String id, String groupId, String title, String topic, String content,
		Set<String> keywords, String version, boolean enabled, String maintainer, Instant createdAt,
		Instant updatedAt, List<CustomerKnowledgeChunk> chunks) {

	/**
	 * 创建已启用的知识文档，默认版本为 1.0，创建和更新时间相同。
	 * @param id 文档唯一标识
	 * @param groupId 知识分组 ID
	 * @param title 文档标题
	 * @param topic 业务主题
	 * @param content 文档正文
	 * @param keywords 召回关键词
	 * @param maintainer 维护人
	 * @return 已启用的知识文档
	 * @author xyd
	 * @date 2026-05-22 10:00:00
	 */
	public static CustomerKnowledgeDocumentV2 of(String id, String groupId, String title, String topic, String content,
			Set<String> keywords, String maintainer) {
		Instant now = Instant.now();
		return new CustomerKnowledgeDocumentV2(id, groupId, title, topic, content, keywords, "1.0", true, maintainer,
				now, now, List.of());
	}

	/**
	 * 创建指定版本和启用状态的知识文档。
	 * @param id 文档唯一标识
	 * @param groupId 知识分组 ID
	 * @param title 文档标题
	 * @param topic 业务主题
	 * @param content 文档正文
	 * @param keywords 召回关键词
	 * @param version 文档版本
	 * @param enabled 是否启用
	 * @param maintainer 维护人
	 * @return 知识文档
	 * @author xyd
	 * @date 2026-05-22 10:00:00
	 */
	public static CustomerKnowledgeDocumentV2 of(String id, String groupId, String title, String topic, String content,
			Set<String> keywords, String version, boolean enabled, String maintainer) {
		Instant now = Instant.now();
		return new CustomerKnowledgeDocumentV2(id, groupId, title, topic, content, keywords, version, enabled,
				maintainer, now, now, List.of());
	}

	/**
	 * 判断该文档是否参与检索。
	 * @return true 表示文档已启用且内容非空
	 * @author xyd
	 * @date 2026-05-22 10:00:00
	 */
	public boolean isSearchable() {
		return this.enabled && this.content != null && !this.content.isBlank();
	}

	/**
	 * 生成适合检索的摘要文本。
	 * @return 标题加内容前200字的摘要
	 * @author xyd
	 * @date 2026-05-22 10:00:00
	 */
	public String summary() {
		String preview = this.content == null ? "" : this.content.substring(0, Math.min(200, this.content.length()));
		return this.title() + "：" + preview + (this.content != null && this.content.length() > 200 ? "..." : "");
	}

}