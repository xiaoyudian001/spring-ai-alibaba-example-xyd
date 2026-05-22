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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能客服知识管理 REST API 控制器，提供知识的增删改查、分组管理和检索功能。
 *
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@RestController
@RequestMapping("/minimax/knowledge")
public class KnowledgeManagementController {

private final KnowledgeManagementService knowledgeService;

/**
 * 创建知识管理控制器。
 * @param knowledgeService 知识管理服务
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public KnowledgeManagementController(KnowledgeManagementService knowledgeService) {
this.knowledgeService = knowledgeService;
}

/**
 * 获取所有知识分组概览。
 * @return 分组列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@GetMapping("/groups")
public List<KnowledgeGroupOverview> getGroupOverviews() {
return this.knowledgeService.getGroupOverviews();
}

/**
 * 查询指定分组下的所有知识文档。
 * @param groupId 分组 ID
 * @return 知识文档列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@GetMapping("/documents")
public List<CustomerKnowledgeDocumentV2> getDocumentsByGroup(@RequestParam("groupId") String groupId) {
return this.knowledgeService.findByGroupId(groupId);
}

/**
 * 查询所有知识文档。
 * @return 知识文档列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@GetMapping("/documents/all")
public List<CustomerKnowledgeDocumentV2> getAllDocuments() {
return this.knowledgeService.findAll();
}

/**
 * 获取指定知识文档详情。
 * @param id 文档 ID
 * @return 知识文档，不存在时返回空
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@GetMapping("/documents/{id}")
public CustomerKnowledgeDocumentV2 getDocument(@PathVariable("id") String id) {
return this.knowledgeService.findById(id).orElse(null);
}

/**
 * 获取指定文档的 Chunk 列表。
 * @param documentId 文档 ID
 * @return Chunk 列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@GetMapping("/documents/{documentId}/chunks")
public List<CustomerKnowledgeChunk> getDocumentChunks(@PathVariable("documentId") String documentId) {
return this.knowledgeService.findById(documentId).map(CustomerKnowledgeDocumentV2::chunks).orElse(List.of());
}

/**
 * 创建新的知识文档，自动切分 chunk 并入库。
 * @param request 知识文档创建请求
 * @return 创建后的文档
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
public CustomerKnowledgeDocumentV2 createDocument(@RequestBody KnowledgeDocumentRequest request) {
CustomerKnowledgeDocumentV2 document = CustomerKnowledgeDocumentV2.of(request.id(), request.groupId(),
request.title(), request.topic(), request.content(), request.keywords(), request.maintainer());
return this.knowledgeService.createDocument(document);
}

/**
 * 更新知识文档内容，重新切分 chunk。
 * @param id 文档 ID
 * @param request 更新请求
 * @return 更新后的文档
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@PutMapping(value = "/documents/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
public CustomerKnowledgeDocumentV2 updateDocument(@PathVariable("id") String id,
@RequestBody KnowledgeDocumentRequest request) {
CustomerKnowledgeDocumentV2 document = CustomerKnowledgeDocumentV2.of(request.id(), request.groupId(),
request.title(), request.topic(), request.content(), request.keywords(),
request.version() != null ? request.version() : "1.0", true, request.maintainer());
return this.knowledgeService.updateDocument(id, document).orElse(null);
}

/**
 * 删除知识文档。
 * @param id 文档 ID
 * @return 删除成功返回 true
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@DeleteMapping("/documents/{id}")
public boolean deleteDocument(@PathVariable("id") String id) {
return this.knowledgeService.deleteDocument(id);
}

/**
 * 启用或禁用知识文档。
 * @param id 文档 ID
 * @param enabled 是否启用
 * @return 更新成功返回 true
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@PutMapping("/documents/{id}/enabled")
public boolean setDocumentEnabled(@PathVariable("id") String id, @RequestParam("enabled") boolean enabled) {
return this.knowledgeService.setEnabled(id, enabled);
}

/**
 * 知识文档请求体，用于创建和更新接口。
 *
 * @param id 文档 ID，格式建议使用 kebab-case
 * @param groupId 分组 ID，例如 refund、shipping、xianyu、wechat
 * @param title 文档标题
 * @param topic 业务主题，用于检索匹配
 * @param content 文档正文
 * @param keywords 关键词集合
 * @param version 版本号，默认 1.0
 * @param maintainer 维护人
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record KnowledgeDocumentRequest(String id, String groupId, String title, String topic, String content,
Set<String> keywords, String version, String maintainer) {

}

}
