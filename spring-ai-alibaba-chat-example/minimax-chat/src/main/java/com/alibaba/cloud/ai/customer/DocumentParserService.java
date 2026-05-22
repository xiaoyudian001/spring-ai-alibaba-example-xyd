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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

/**
 * 智能客服文档解析服务，支持解析 Word (.docx) 和 PDF (.pdf) 文件转换为文本内容。
 * <p>
 * 支持的文件格式：
 * <ul>
 *     <li>TXT - 纯文本文件，直接读取字节流</li>
 *     <li>Markdown - Markdown 文件，直接读取字节流</li>
 *     <li>DOCX - Word 2007+ 文档，提取段落文本</li>
 *     <li>PDF - PDF 文档，提取文本内容</li>
 * </ul>
 *
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
@Service
public class DocumentParserService {

private static final int MAX_CONTENT_LENGTH = 50000;

/**
 * 解析上传的文档文件，根据文件扩展名选择合适的解析器。
 * @param inputStream 文件输入流
 * @param fileName 文件名
 * @return 解析后的文本内容
 * @throws IOException 文件读取或解析失败时抛出
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public String parse(InputStream inputStream, String fileName) throws IOException {
String extension = getFileExtension(fileName);
return switch (extension.toLowerCase()) {
case "docx", "doc" -> parseDocx(inputStream);
case "pdf" -> parsePdf(inputStream);
case "txt", "text", "md", "markdown" -> parseText(inputStream);
default -> throw new IOException("不支持的文件格式：" + extension);
};
}

/**
 * 解析 Word 2007+ 文档（.docx）。
 * @param inputStream 文件输入流
 * @return 文档文本内容
 * @throws IOException 解析失败时抛出
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public String parseDocx(InputStream inputStream) throws IOException {
try (XWPFDocument document = new XWPFDocument(inputStream)) {
StringBuilder content = new StringBuilder();
List<XWPFParagraph> paragraphs = document.getParagraphs();
for (XWPFParagraph paragraph : paragraphs) {
String text = paragraph.getText();
if (text != null && !text.isBlank()) {
if (content.length() > 0) {
content.append("\n");
}
content.append(text);
}
}
return truncate(content.toString());
}
}

/**
 * 解析 PDF 文档。
 * @param inputStream 文件输入流
 * @return PDF 文本内容
 * @throws IOException 解析失败时抛出
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public String parsePdf(InputStream inputStream) throws IOException {
try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
PDFTextStripper stripper = new PDFTextStripper();
stripper.setSortByPosition(true);
String content = stripper.getText(document);
return truncate(content);
}
}

/**
 * 解析纯文本或 Markdown 文件。
 * @param inputStream 文件输入流
 * @return 文件文本内容
 * @throws IOException 读取失败时抛出
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public String parseText(InputStream inputStream) throws IOException {
byte[] bytes = inputStream.readAllBytes();
String content = new String(bytes, StandardCharsets.UTF_8);
return truncate(content);
}

/**
 * 获取文件扩展名。
 * @param fileName 文件名
 * @return 扩展名（小写，不含点）
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public String getFileExtension(String fileName) {
if (fileName == null || fileName.isBlank()) {
return "";
}
int lastDot = fileName.lastIndexOf('.');
if (lastDot >= 0 && lastDot < fileName.length() - 1) {
return fileName.substring(lastDot + 1).toLowerCase();
}
return "";
}

/**
 * 判断是否为支持的文档格式。
 * @param fileName 文件名
 * @return true 表示支持
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public boolean isSupported(String fileName) {
String extension = getFileExtension(fileName);
return extension.equals("docx") || extension.equals("doc") || extension.equals("pdf")
|| extension.equals("txt") || extension.equals("text") || extension.equals("md")
|| extension.equals("markdown");
}

/**
 * 获取支持的文档类型描述。
 * @return 支持的文件类型描述
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
public String supportedTypes() {
return "TXT、Markdown、Word (.docx)、PDF";
}

/**
 * 截断过长的内容。
 * @param content 原始内容
 * @return 截断后的内容
 * @author xyd
 * @date 2026-05-22 12:00:00
 */
private String truncate(String content) {
if (content == null) {
return "";
}
content = content.trim();
if (content.length() > MAX_CONTENT_LENGTH) {
return content.substring(0, MAX_CONTENT_LENGTH);
}
return content;
}

}
