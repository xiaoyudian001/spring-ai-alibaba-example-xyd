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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 智能客服知识管理服务，使用 MySQL 持久化文档和 chunk 元数据，支持知识的增删改查和分组管理。
 *
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@Service
public class KnowledgeManagementService {

private static final TypeReference<java.util.Set<String>> SET_TYPE_REF = new TypeReference<>() {
};

private final JdbcTemplate jdbcTemplate;

private final ObjectMapper objectMapper;

private final DocumentChunkingService chunkingService;

/**
 * 创建知识管理服务。
 * @param jdbcTemplate 数据库访问模板
 * @param objectMapper JSON 序列化工具
 * @param chunkingService 文档切分服务
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public KnowledgeManagementService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
DocumentChunkingService chunkingService) {
this.jdbcTemplate = jdbcTemplate;
this.objectMapper = objectMapper;
this.chunkingService = chunkingService;
}

/**
 * 初始化知识库表结构，包括文档表和 chunk 表。
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
@PostConstruct
public void initializeSchema() {
this.jdbcTemplate.execute("""
CREATE TABLE IF NOT EXISTS knowledge_documents (
id VARCHAR(128) PRIMARY KEY,
group_id VARCHAR(64) NOT NULL,
title VARCHAR(256) NOT NULL,
topic VARCHAR(128) NOT NULL,
content TEXT NOT NULL,
keywords TEXT,
version VARCHAR(32) NOT NULL DEFAULT '\''1.0'\'',
enabled BOOLEAN NOT NULL DEFAULT TRUE,
maintainer VARCHAR(128),
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
INDEX idx_group_id (group_id),
INDEX idx_topic (topic),
INDEX idx_enabled (enabled)
)
""");
this.jdbcTemplate.execute("""
CREATE TABLE IF NOT EXISTS knowledge_chunks (
id VARCHAR(256) PRIMARY KEY,
document_id VARCHAR(128) NOT NULL,
chunk_index INT NOT NULL,
content TEXT NOT NULL,
keywords TEXT,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
INDEX idx_document_id (document_id),
FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
)
""");
seedDefaultDocuments();
}

/**
 * 创建新的知识文档，自动切分为 chunk 并写入数据库。
 * @param document 知识文档
 * @return 保存后的文档，包含切分后的 chunk
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized CustomerKnowledgeDocumentV2 createDocument(CustomerKnowledgeDocumentV2 document) {
String safeId = document.id() == null ? generateId(document.title()) : document.id();
CustomerKnowledgeDocumentV2 safeDocument = new CustomerKnowledgeDocumentV2(safeId, document.groupId(),
document.title(), document.topic(), document.content(), document.keywords(), document.version(),
document.enabled(), document.maintainer(), Instant.now(), Instant.now(), List.of());
this.jdbcTemplate.update("""
INSERT INTO knowledge_documents (id, group_id, title, topic, content, keywords, version, enabled, maintainer, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""", safeDocument.id(), safeDocument.groupId(), safeDocument.title(), safeDocument.topic(),
safeDocument.content(), toJson(safeDocument.keywords()), safeDocument.version(), safeDocument.enabled(),
safeDocument.maintainer(), safeDocument.createdAt(), safeDocument.updatedAt());
List<CustomerKnowledgeChunk> chunks = this.chunkingService.chunkByParagraph(safeDocument);
for (CustomerKnowledgeChunk chunk : chunks) {
saveChunk(chunk);
}
return new CustomerKnowledgeDocumentV2(safeDocument.id(), safeDocument.groupId(), safeDocument.title(),
safeDocument.topic(), safeDocument.content(), safeDocument.keywords(), safeDocument.version(),
safeDocument.enabled(), safeDocument.maintainer(), safeDocument.createdAt(), safeDocument.updatedAt(),
chunks);
}

/**
 * 更新知识文档内容，重新切分 chunk 并更新数据库。
 * @param id 文档 ID
 * @param document 更新后的文档内容
 * @return 更新后的文档，不存在时返回空
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized Optional<CustomerKnowledgeDocumentV2> updateDocument(String id,
CustomerKnowledgeDocumentV2 document) {
if (id == null || id.isBlank()) {
return Optional.empty();
}
Optional<CustomerKnowledgeDocumentV2> existing = findById(id);
if (existing.isEmpty()) {
return Optional.empty();
}
CustomerKnowledgeDocumentV2 safeDocument = new CustomerKnowledgeDocumentV2(id, document.groupId(),
document.title(), document.topic(), document.content(), document.keywords(),
document.version() != null ? document.version() : existing.get().version(), document.enabled(),
document.maintainer() != null ? document.maintainer() : existing.get().maintainer(),
existing.get().createdAt(), Instant.now(), List.of());
this.jdbcTemplate.update("""
UPDATE knowledge_documents SET group_id = ?, title = ?, topic = ?, content = ?, keywords = ?, version = ?, enabled = ?, maintainer = ?, updated_at = ?
WHERE id = ?
""", safeDocument.groupId(), safeDocument.title(), safeDocument.topic(), safeDocument.content(),
toJson(safeDocument.keywords()), safeDocument.version(), safeDocument.enabled(),
safeDocument.maintainer(), safeDocument.updatedAt(), id);
this.jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_id = ?", id);
List<CustomerKnowledgeChunk> chunks = this.chunkingService.chunkByParagraph(safeDocument);
for (CustomerKnowledgeChunk chunk : chunks) {
saveChunk(chunk);
}
return Optional.of(new CustomerKnowledgeDocumentV2(safeDocument.id(), safeDocument.groupId(),
safeDocument.title(), safeDocument.topic(), safeDocument.content(), safeDocument.keywords(),
safeDocument.version(), safeDocument.enabled(), safeDocument.maintainer(), safeDocument.createdAt(),
safeDocument.updatedAt(), chunks));
}

/**
 * 根据 ID 查询知识文档及其 chunk。
 * @param id 文档 ID
 * @return 知识文档，不存在时返回空
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized Optional<CustomerKnowledgeDocumentV2> findById(String id) {
List<CustomerKnowledgeDocumentV2> results = this.jdbcTemplate.query(
"SELECT * FROM knowledge_documents WHERE id = ?", (rs, rowNum) -> toDocument(rs), id);
if (results.isEmpty()) {
return Optional.empty();
}
CustomerKnowledgeDocumentV2 doc = results.get(0);
List<CustomerKnowledgeChunk> chunks = findChunksByDocumentId(id);
return Optional.of(new CustomerKnowledgeDocumentV2(doc.id(), doc.groupId(), doc.title(), doc.topic(),
doc.content(), doc.keywords(), doc.version(), doc.enabled(), doc.maintainer(), doc.createdAt(),
doc.updatedAt(), chunks));
}

/**
 * 查询指定分组下的所有知识文档。
 * @param groupId 分组 ID
 * @return 知识文档列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized List<CustomerKnowledgeDocumentV2> findByGroupId(String groupId) {
return this.jdbcTemplate.query("SELECT * FROM knowledge_documents WHERE group_id = ? ORDER BY updated_at DESC",
(rs, rowNum) -> toDocument(rs), groupId);
}

/**
 * 查询所有已启用的知识文档，用于检索和向量入库。
 * @return 已启用的知识文档列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized List<CustomerKnowledgeDocumentV2> findAllEnabled() {
return this.jdbcTemplate.query(
"SELECT * FROM knowledge_documents WHERE enabled = TRUE ORDER BY updated_at DESC",
(rs, rowNum) -> toDocument(rs));
}

/**
 * 查询所有知识文档。
 * @return 知识文档列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized List<CustomerKnowledgeDocumentV2> findAll() {
return this.jdbcTemplate.query("SELECT * FROM knowledge_documents ORDER BY updated_at DESC",
(rs, rowNum) -> toDocument(rs));
}

/**
 * 初始化默认客服知识文档，保证 MySQL 知识库首次启动时具备基础政策、话术和渠道规范。
 *
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
private void seedDefaultDocuments() {
if (countDocuments() > 0) {
return;
}
for (CustomerKnowledgeDocumentV2 document : defaultDocuments()) {
createDocument(document);
}
}

/**
 * 查询当前知识文档总数，用于判断是否需要初始化内置知识。
 * @return 知识文档总数
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
private int countDocuments() {
Integer count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_documents", Integer.class);
return count == null ? 0 : count;
}

/**
 * 构建智能客服默认知识集合，作为 MySQL RAG 的初始化数据。
 * @return 默认知识文档列表
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
private List<CustomerKnowledgeDocumentV2> defaultDocuments() {
List<CustomerKnowledgeDocumentV2> docs = new ArrayList<>();
docs.add(defaultDocument("refund-policy", "refund", "退货退款政策", "refund",
"签收 7 天内且商品不影响二次销售时，可引导用户申请退货退款；超过 7 天需要说明平台规则和可选售后路径；涉及赔偿或直接退款时必须先查询订单事实，再给出合规解释。",
"退款", "退货", "售后", "7天", "七天", "赔偿"));
docs.add(defaultDocument("shipping-policy", "shipping", "发货与物流政策", "shipping",
"已付款订单默认 48 小时内发货；已发货订单应先查询物流；待发货订单应说明预计发货时间并创建提醒；物流异常时应提供快递单号、最新节点和后续处理时效。",
"发货", "物流", "快递", "签收", "运输", "没到"));
docs.add(defaultDocument("price-policy", "price", "议价与价格策略", "price",
"闲鱼议价先查询商品底价和库存；可接受范围内给出温和让步；低于底价时礼貌拒绝并说明商品状态、稀缺性或包邮成本；不得承诺超出策略的优惠。",
"便宜", "优惠", "包邮", "议价", "小刀", "底价"));
docs.add(defaultDocument("xianyu-reply-guide", "xianyu", "闲鱼回复规范", "xianyu",
"闲鱼回复要短、自然、像真人；常用表达包括“还在的”“可以小刀”“发货前会检查”；不要承诺无法确认的信息；遇到退款、赔付、取消订单时先解释规则和下一步。",
"闲鱼", "买家", "小刀", "还在", "自然", "二手"));
docs.add(defaultDocument("wechat-service-guide", "wechat", "微信客服规范", "wechat",
"微信客服回复要完整、礼貌、可追踪；需要保留订单号和工单号；复杂售后建议创建工单并告知处理时效；不要使用过于随意的闲鱼话术。",
"微信", "公众号", "企业微信", "小程序", "工单", "处理时效"));
docs.add(defaultDocument("complaint-handling", "complaint", "投诉处理规范", "complaint",
"投诉处理先表达理解和歉意，再复述问题，随后给出可执行处理动作；态度激烈、差评威胁、监管投诉等场景应创建工单，记录诉求和证据。",
"投诉", "差评", "生气", "举报", "升级", "安抚"));
docs.add(defaultDocument("address-change-policy", "address", "地址修改规范", "address",
"用户要求改地址时必须先查询订单状态；未发货可提示用户提供新地址并记录工单；已发货只能建议联系快递或等待派送前改派，不能直接承诺一定修改成功。",
"地址", "改地址", "收货人", "电话", "派送", "改派"));
docs.add(defaultDocument("invoice-policy", "invoice", "发票与凭证规范", "invoice",
"用户索要发票、购买凭证或交易截图时，应先确认订单号和支付状态；二手闲置交易通常提供交易凭证，不默认承诺正式发票。",
"发票", "凭证", "截图", "支付", "交易记录"));
docs.add(defaultDocument("product-quality-policy", "quality", "商品质量与验货规范", "quality",
"商品质量咨询应说明成色、瑕疵、配件和测试情况；发货前可承诺再次检查；收到后争议需结合签收时间、开箱证据和商品说明处理。",
"质量", "成色", "瑕疵", "配件", "验货", "开箱"));
docs.add(defaultDocument("conversation-style", "tone", "通用客服语气规范", "tone",
"客服回复应简洁友好、先解决问题再解释规则；事实不明确时先询问商品号或订单号；不要编造物流、库存、退款状态或不存在的政策。",
"语气", "礼貌", "简洁", "不要编造", "事实"));
return docs;
}

/**
 * 构建默认知识文档。
 * @param id 文档 ID
 * @param groupId 分组 ID
 * @param title 标题
 * @param topic 主题
 * @param content 内容
 * @param keywords 关键词
 * @return 默认知识文档
 * @author xyd
 * @date 2026-05-22 11:36:13
 */
private CustomerKnowledgeDocumentV2 defaultDocument(String id, String groupId, String title, String topic,
String content, String... keywords) {
return CustomerKnowledgeDocumentV2.of(id, groupId, title, topic, content,
Set.of(keywords), "1.0", true, "system");
}

/**
 * 删除知识文档及其关联的 chunk。
 * @param id 文档 ID
 * @return 删除成功返回 true
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized boolean deleteDocument(String id) {
int chunks = this.jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_id = ?", id);
int docs = this.jdbcTemplate.update("DELETE FROM knowledge_documents WHERE id = ?", id);
return docs > 0;
}

/**
 * 启用或禁用知识文档。
 * @param id 文档 ID
 * @param enabled 是否启用
 * @return 更新成功返回 true
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized boolean setEnabled(String id, boolean enabled) {
int updated = this.jdbcTemplate.update("UPDATE knowledge_documents SET enabled = ? WHERE id = ?", enabled,
id);
return updated > 0;
}

/**
 * 查询所有知识分组及其文档数量。
 * @return 分组信息列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public synchronized List<KnowledgeGroupOverview> getGroupOverviews() {
return this.jdbcTemplate.query("""
SELECT group_id, COUNT(*) as doc_count, SUM(CASE WHEN enabled THEN 1 ELSE 0 END) as enabled_count
FROM knowledge_documents GROUP BY group_id ORDER BY group_id
""", (rs, rowNum) -> new KnowledgeGroupOverview(rs.getString("group_id"), rs.getInt("doc_count"),
rs.getInt("enabled_count")));
}

/**
 * 保存单个 chunk 到数据库。
 * @param chunk 知识切分块
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private void saveChunk(CustomerKnowledgeChunk chunk) {
this.jdbcTemplate.update("""
INSERT INTO knowledge_chunks (id, document_id, chunk_index, content, keywords, created_at)
VALUES (?, ?, ?, ?, ?, ?)
""", chunk.id(), chunk.documentId(), chunk.chunkIndex(), chunk.content(), toJson(chunk.keywords()),
chunk.createdAt());
}

/**
 * 根据文档 ID 查询所有 chunk。
 * @param documentId 文档 ID
 * @return 知识切分块列表
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private List<CustomerKnowledgeChunk> findChunksByDocumentId(String documentId) {
return this.jdbcTemplate.query("SELECT * FROM knowledge_chunks WHERE document_id = ? ORDER BY chunk_index",
(rs, rowNum) -> new CustomerKnowledgeChunk(rs.getString("id"), rs.getString("document_id"),
rs.getInt("chunk_index"), rs.getString("content"), parseSet(rs.getString("keywords")),
rs.getTimestamp("created_at").toInstant()), documentId);
}

/**
 * 将 ResultSet 转换为知识文档。
 * @param rs 数据库结果集
 * @return 知识文档
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private CustomerKnowledgeDocumentV2 toDocument(java.sql.ResultSet rs) throws java.sql.SQLException {
return new CustomerKnowledgeDocumentV2(rs.getString("id"), rs.getString("group_id"), rs.getString("title"),
rs.getString("topic"), rs.getString("content"), parseSet(rs.getString("keywords")),
rs.getString("version"), rs.getBoolean("enabled"), rs.getString("maintainer"),
rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), List.of());
}

/**
 * 生成文档 ID。
 * @param title 文档标题
 * @return 规范化后的文档 ID
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private String generateId(String title) {
if (title == null || title.isBlank()) {
return "doc-" + System.currentTimeMillis();
}
return title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
}

/**
 * 将 Set 序列化为 JSON 字符串。
 * @param set 集合
 * @return JSON 字符串
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private String toJson(Set<String> set) {
if (set == null || set.isEmpty()) {
return "[]";
}
try {
return this.objectMapper.writeValueAsString(set);
}
catch (JsonProcessingException ex) {
return "[]";
}
}

/**
 * 解析 JSON 字符串为 Set。
 * @param json JSON 字符串
 * @return 集合
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
private Set<String> parseSet(String json) {
if (json == null || json.isBlank()) {
return Set.of();
}
try {
return this.objectMapper.readValue(json, SET_TYPE_REF);
}
catch (JsonProcessingException ex) {
return Set.of();
}
}

/**
 * 知识分组概览，包含分组 ID、文档总数和启用数量。
 *
 * @param groupId 分组 ID
 * @param totalCount 文档总数
 * @param enabledCount 已启用文档数
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public record KnowledgeGroupOverview(String groupId, int totalCount, int enabledCount) {

/**
 * 返回已禁用的文档数量。
 * @return 已禁用数量
 * @author xyd
 * @date 2026-05-22 10:00:00
 */
public int disabledCount() {
return this.totalCount - this.enabledCount;
}

}

}
