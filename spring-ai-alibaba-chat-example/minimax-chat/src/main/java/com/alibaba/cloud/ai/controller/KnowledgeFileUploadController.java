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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.cloud.ai.customer.CustomerKnowledgeChunk;
import com.alibaba.cloud.ai.customer.CustomerKnowledgeDocumentV2;
import com.alibaba.cloud.ai.customer.DocumentChunkingService;
import com.alibaba.cloud.ai.customer.DocumentParserService;
import com.alibaba.cloud.ai.customer.KnowledgeManagementService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 智能客服知识文件上传控制器，支持 TXT 和 Markdown 文件导入，自动切分为 chunk 并入库。
 *
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
@RestController
@RequestMapping("/minimax/knowledge/upload")
public class KnowledgeFileUploadController {

private static final Pattern TITLE_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$|^第[一二三四五六七八九十\\d]+[章节条点部分]\\s*.+$",
Pattern.MULTILINE);

private static final Pattern KEYWORD_SENTENCE = Pattern.compile("[。.!?！?]+");

private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("text/plain", "text/markdown", "text/x-markdown",
"application/octet-stream");

private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "markdown", "text", "docx", "doc",
			"pdf");

	private final KnowledgeManagementService knowledgeService;

	private final DocumentChunkingService chunkingService;

	private final DocumentParserService documentParserService;

	/**
	 * 创建知识文件上传控制器。
	 * @param knowledgeService 知识管理服务
	 * @param chunkingService 文档切分服务
	 * @param documentParserService 文档解析服务
	 * @author xyd
	 * @date 2026-05-22 11:40:00
	 */
	public KnowledgeFileUploadController(KnowledgeManagementService knowledgeService,
			DocumentChunkingService chunkingService, DocumentParserService documentParserService) {
		this.knowledgeService = knowledgeService;
		this.chunkingService = chunkingService;
		this.documentParserService = documentParserService;
	}

/**
 * 上传知识文件（支持 TXT 和 Markdown），自动提取标题、主题、关键词，并切分为 chunk 入库。
 * @param file 上传的文件
 * @param groupId 分组 ID，不提供时自动从文件名推断
 * @param topic 业务主题，不提供时自动从文件内容提取
 * @param maintainer 维护人
 * @return 上传结果
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public KnowledgeFileUploadResult uploadFile(@RequestParam("file") MultipartFile file,
@RequestParam(value = "groupId", required = false) String groupId,
@RequestParam(value = "topic", required = false) String topic,
@RequestParam(value = "maintainer", required = false, defaultValue = "system") String maintainer) {
validateFile(file);
String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded-file";
String content = readFileContent(file);
String id = generateDocumentId(fileName);
String safeGroupId = groupId != null && !groupId.isBlank() ? groupId : inferGroupId(fileName);
String title = extractTitle(content, fileName);
String safeTopic = topic != null && !topic.isBlank() ? topic : inferTopic(content);
Set<String> keywords = extractKeywords(content);
CustomerKnowledgeDocumentV2 document = CustomerKnowledgeDocumentV2.of(id, safeGroupId, title, safeTopic, content,
keywords, maintainer);
CustomerKnowledgeDocumentV2 saved = this.knowledgeService.createDocument(document);
return new KnowledgeFileUploadResult(true, saved.id(), saved.title(), saved.chunks().size(), safeGroupId,
safeTopic, "文件上传成功，已自动切分为 " + saved.chunks().size() + " 个 chunk 并入库。");
}

/**
 * 批量上传多个知识文件。
 * @param files 上传的文件列表
 * @param groupId 分组 ID
 * @param topic 业务主题
 * @param maintainer 维护人
 * @return 批量上传结果
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
@PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public List<KnowledgeFileUploadResult> uploadFiles(
@RequestParam("files") List<MultipartFile> files,
@RequestParam(value = "groupId", required = false) String groupId,
@RequestParam(value = "topic", required = false) String topic,
@RequestParam(value = "maintainer", required = false, defaultValue = "system") String maintainer) {
List<KnowledgeFileUploadResult> results = new ArrayList<>();
for (MultipartFile file : files) {
if (file.isEmpty()) {
continue;
}
try {
results.add(uploadFile(file, groupId, topic, maintainer));
}
catch (Exception ex) {
String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
results.add(new KnowledgeFileUploadResult(false, fileName, "", 0, groupId, topic,
"上传失败：" + ex.getMessage()));
}
}
return results;
}

/**
 * 验证上传的文件。
 * @param file 上传的文件
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private void validateFile(MultipartFile file) {
if (file == null || file.isEmpty()) {
throw new IllegalArgumentException("上传文件不能为空");
}
String fileName = file.getOriginalFilename();
if (fileName == null || fileName.isBlank()) {
throw new IllegalArgumentException("文件名不能为空");
}
String extension = getFileExtension(fileName);
if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
throw new IllegalArgumentException(
		"不支持的文件类型：" + extension + "，仅支持 TXT、Markdown、Word (.docx)、PDF");
}
}

/**
 * 读取文件内容，根据文件类型选择合适的解析方式。
 * @param file 上传的文件
 * @return 文件内容
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
private String readFileContent(MultipartFile file) {
String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
String extension = getFileExtension(fileName);
if (extension.equals("docx") || extension.equals("doc") || extension.equals("pdf")) {
try {
return this.documentParserService.parse(file.getInputStream(), fileName);
}
catch (IOException ex) {
throw new IllegalArgumentException("解析文档失败：" + ex.getMessage());
}
}
try {
byte[] bytes = file.getBytes();
String content = new String(bytes, StandardCharsets.UTF_8);
if (content.length() > 50000) {
content = content.substring(0, 50000);
}
return content.trim();
}
catch (IOException ex) {
throw new IllegalArgumentException("读取文件内容失败：" + ex.getMessage());
}
}

/**
 * 生成文档 ID。
 * @param fileName 文件名
 * @return 文档 ID
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private String generateDocumentId(String fileName) {
String baseName = fileName;
int lastDot = fileName.lastIndexOf('.');
if (lastDot > 0) {
baseName = fileName.substring(0, lastDot);
}
return baseName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
}

/**
 * 推断分组 ID。
 * @param fileName 文件名
 * @return 分组 ID
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private String inferGroupId(String fileName) {
String lower = fileName.toLowerCase();
if (lower.contains("refund") || lower.contains("退款") || lower.contains("售后")) {
return "refund";
}
if (lower.contains("shipping") || lower.contains("物流") || lower.contains("发货")) {
return "shipping";
}
if (lower.contains("xianyu") || lower.contains("闲鱼")) {
return "xianyu";
}
if (lower.contains("wechat") || lower.contains("微信")) {
return "wechat";
}
if (lower.contains("product") || lower.contains("商品")) {
return "product";
}
return "general";
}

/**
 * 从内容中提取标题。
 * @param content 文件内容
 * @param fileName 文件名
 * @return 标题
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private String extractTitle(String content, String fileName) {
var matcher = TITLE_PATTERN.matcher(content);
if (matcher.find()) {
return matcher.group(1).trim();
}
int lastDot = fileName.lastIndexOf('.');
String title = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
return title.replaceAll("[_-]+", " ").trim();
}

/**
 * 推断业务主题。
 * @param content 文件内容
 * @return 业务主题
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private String inferTopic(String content) {
String lower = content.toLowerCase();
if (lower.contains("退款") || lower.contains("退货") || lower.contains("售后")) {
return "refund";
}
if (lower.contains("物流") || lower.contains("发货") || lower.contains("快递")) {
return "shipping";
}
if (lower.contains("价格") || lower.contains("优惠") || lower.contains("议价")) {
return "price";
}
if (lower.contains("质量") || lower.contains("瑕疵") || lower.contains("描述")) {
return "quality";
}
if (lower.contains("地址") || lower.contains("改地址")) {
return "address";
}
return "general";
}

/**
 * 从内容中提取关键词。
 * @param content 文件内容
 * @return 关键词集合
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private Set<String> extractKeywords(String content) {
Set<String> keywords = new HashSet<>();
for (String sentence : KEYWORD_SENTENCE.split(content)) {
String trimmed = sentence.trim();
if (trimmed.length() >= 4 && trimmed.length() <= 30) {
keywords.add(trimmed);
}
}
return keywords;
}

/**
 * 获取文件扩展名。
 * @param fileName 文件名
 * @return 扩展名
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
private String getFileExtension(String fileName) {
int lastDot = fileName.lastIndexOf('.');
if (lastDot >= 0 && lastDot < fileName.length() - 1) {
return fileName.substring(lastDot + 1);
}
return "";
}

/**
 * 知识文件上传结果。
 *
 * @param success 是否成功
 * @param documentId 文档 ID
 * @param title 文档标题
 * @param chunkCount 切分的 chunk 数量
 * @param groupId 分组 ID
 * @param topic 业务主题
 * @param message 上传结果消息
 * @author xyd
 * @date 2026-05-22 11:40:00
 */
public record KnowledgeFileUploadResult(boolean success, String documentId, String title, int chunkCount,
String groupId, String topic, String message) {

}

}
