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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 客服知识库检索服务，支持本地高召回关键词检索，并在存在 VectorStore Bean 时自动切换到真实向量库。
 *
 * @author xyd
 * @date 2026-05-19 13:31:27
 */
@Service
public class CustomerPolicyRagService {

	private static final String LOCAL_KEYWORD_MODE = "LOCAL_KEYWORD";

	private static final String VECTOR_STORE_MODE = "VECTOR_STORE";

	private final ObjectProvider<VectorStore> vectorStoreProvider;

	private final ObjectMapper objectMapper;

	private final Path knowledgeFile;

	private final boolean vectorEnabled;

	private final List<CustomerKnowledgeDocument> documents = new ArrayList<>();

	/**
	 * 创建客服 RAG 服务。
	 * @param vectorStoreProvider Spring AI VectorStore 提供器，存在真实向量库 Bean 时自动使用
	 * @param objectMapper JSON 序列化工具
	 * @param knowledgeFile 自定义客服知识文件路径
	 * @param vectorEnabled 是否启用真实向量库检索
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public CustomerPolicyRagService(ObjectProvider<VectorStore> vectorStoreProvider, ObjectMapper objectMapper,
			@Value("${minimax.customer.rag.knowledge-file:spring-ai-alibaba-chat-example/minimax-chat/memory/customer-knowledge.json}") String knowledgeFile,
			@Value("${minimax.customer.rag.vector-enabled:false}") boolean vectorEnabled) {
		this.vectorStoreProvider = vectorStoreProvider;
		this.objectMapper = objectMapper;
		this.knowledgeFile = Path.of(knowledgeFile);
		this.vectorEnabled = vectorEnabled;
	}

	/**
	 * 初始化客服业务知识文档，并在可用时写入真实向量库。
	 *
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	@PostConstruct
	public void initializeKnowledgeBase() {
		reloadDocuments();
		if (this.vectorEnabled) {
			this.vectorStoreProvider.ifAvailable(this::seedVectorStore);
		}
	}

	/**
	 * 根据用户问题检索客服政策、平台规则或话术知识。
	 * @param query 用户问题或检索关键词
	 * @param limit 返回结果数量
	 * @return 检索结果摘要
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public String search(String query, Integer limit) {
		return searchWithMetrics(query, limit, Set.of()).summary();
	}

	/**
	 * 根据用户问题检索客服知识，并返回召回率、命中主题和向量库状态。
	 * @param query 用户问题或检索关键词
	 * @param limit 返回结果数量
	 * @param expectedTopics 期望命中的主题集合
	 * @return 客服 RAG 检索结果
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public CustomerPolicySearchResult searchWithMetrics(String query, Integer limit, Set<String> expectedTopics) {
		int max = limit == null || limit <= 0 ? 5 : Math.min(limit, 8);
		Optional<VectorStore> vectorStore = this.vectorEnabled ? this.vectorStoreProvider.stream().findFirst()
				: Optional.empty();
		List<CustomerKnowledgeDocument> hits = vectorStore
				.map(store -> vectorSearch(store, query, max))
				.filter(items -> !items.isEmpty())
				.orElseGet(() -> localSearch(query, max));
		String mode = vectorStore.isPresent() && this.vectorEnabled ? VECTOR_STORE_MODE : LOCAL_KEYWORD_MODE;
		Set<String> hitTopics = hits.stream().map(CustomerKnowledgeDocument::topic)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<String> safeExpectedTopics = normalizeTopics(expectedTopics);
		return new CustomerPolicySearchResult(mode, vectorStore.isPresent(), normalize(query), safeExpectedTopics,
				hitTopics, recallRate(safeExpectedTopics, hitTopics), hits);
	}

	/**
	 * 返回当前知识库覆盖的主题，用于调试知识覆盖率。
	 * @return 知识主题集合
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	public Set<String> topics() {
		return this.documents.stream().map(CustomerKnowledgeDocument::topic)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * 返回当前客服知识库中的全部知识文档，包含内置知识和 JSON 自定义知识。
	 * @return 客服知识文档列表
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	public synchronized List<CustomerKnowledgeDocument> documents() {
		return List.copyOf(this.documents);
	}

	/**
	 * 查询当前客服 RAG 运行状态，用于工作台确认本地关键词检索或真实 VectorStore 是否生效。
	 * @return 客服 RAG 运行状态
	 * @author xyd
	 * @date 2026-05-21 00:00:00
	 */
	public CustomerPolicyRagStatus status() {
		boolean realVectorStoreAvailable = this.vectorEnabled && this.vectorStoreProvider.stream().findFirst().isPresent();
		String mode = realVectorStoreAvailable ? VECTOR_STORE_MODE : LOCAL_KEYWORD_MODE;
		String message = realVectorStoreAvailable ? "已启用真实 VectorStore 检索"
				: this.vectorEnabled ? "已开启向量检索开关，但未发现 VectorStore Bean，当前回退本地关键词检索"
						: "当前使用本地高召回关键词检索";
		return new CustomerPolicyRagStatus(this.vectorEnabled, realVectorStoreAvailable, mode,
				this.knowledgeFile.toString(), this.documents.size(), topics().size(), message);
	}

	/**
	 * 新增或更新一条自定义客服知识，并写回 JSON 文件；启用 VectorStore 时同步写入向量库。
	 * @param request 知识新增或更新请求
	 * @return 保存后的客服知识文档
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	public synchronized CustomerKnowledgeDocument upsertCustomDocument(CustomerKnowledgeUpsertRequest request) {
		CustomerKnowledgeDocument document = toDocument(request);
		Map<String, CustomerKnowledgeDocument> customDocuments = readCustomDocumentMap();
		customDocuments.put(document.id(), document);
		writeCustomDocuments(customDocuments);
		reloadDocuments();
		if (this.vectorEnabled) {
			this.vectorStoreProvider.ifAvailable(store -> store.add(List.of(toVectorDocument(document))));
		}
		return document;
	}

	/**
	 * 删除一条自定义客服知识；内置知识不会被删除。
	 * @param id 文档唯一标识
	 * @return 删除成功返回 true，不存在或为内置知识时返回 false
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	public synchronized boolean deleteCustomDocument(String id) {
		String safeId = normalize(id);
		if (safeId.isBlank()) {
			return false;
		}
		Map<String, CustomerKnowledgeDocument> customDocuments = readCustomDocumentMap();
		CustomerKnowledgeDocument removed = customDocuments.remove(safeId);
		if (removed == null) {
			return false;
		}
		writeCustomDocuments(customDocuments);
		reloadDocuments();
		return true;
	}

	/**
	 * 把本地客服知识文档写入真实向量库。
	 * @param vectorStore 真实向量库
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private void seedVectorStore(VectorStore vectorStore) {
		List<Document> vectorDocuments = this.documents.stream()
				.map(this::toVectorDocument)
				.toList();
		vectorStore.add(vectorDocuments);
	}

	/**
	 * 将客服知识文档转换为 Spring AI VectorStore 可写入的 Document。
	 * @param document 客服知识文档
	 * @return 向量库文档
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private Document toVectorDocument(CustomerKnowledgeDocument document) {
		return new Document(document.id(), document.content(), Map.of("id", document.id(), "title", document.title(),
				"topic", document.topic(), "keywords", String.join(",", document.keywords())));
	}

	/**
	 * 使用真实向量库执行相似度检索，并把结果映射回客服知识文档。
	 * @param vectorStore 真实向量库
	 * @param query 用户问题
	 * @param limit 返回数量
	 * @return 命中的客服知识文档
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private List<CustomerKnowledgeDocument> vectorSearch(VectorStore vectorStore, String query, int limit) {
		SearchRequest request = SearchRequest.builder()
				.query(normalize(query))
				.topK(limit)
				.similarityThresholdAll()
				.build();
		return vectorStore.similaritySearch(request).stream()
				.map(document -> documentById(String.valueOf(document.getMetadata().get("id"))))
				.flatMap(Optional::stream)
				.distinct()
				.limit(limit)
				.toList();
	}

	/**
	 * 使用本地高召回关键词策略检索客服知识。
	 * @param query 用户问题
	 * @param limit 返回数量
	 * @return 命中的客服知识文档
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private List<CustomerKnowledgeDocument> localSearch(String query, int limit) {
		Set<String> queryTokens = tokens(query);
		return this.documents.stream()
				.map(document -> Map.entry(document, score(document, queryTokens)))
				.filter(entry -> entry.getValue() > 0 || queryTokens.isEmpty())
				.sorted(Map.Entry.<CustomerKnowledgeDocument, Integer>comparingByValue(Comparator.reverseOrder()))
				.limit(limit)
				.map(Map.Entry::getKey)
				.toList();
	}

	/**
	 * 根据用户问题关键词计算单个客服知识文档的命中分数。
	 * @param document 客服知识文档
	 * @param queryTokens 用户问题拆分后的关键词
	 * @return 文档命中分数
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private int score(CustomerKnowledgeDocument document, Set<String> queryTokens) {
		if (queryTokens.isEmpty()) {
			return 1;
		}
		int score = 0;
		String text = normalize(document.id() + " " + document.title() + " " + document.topic() + " "
				+ document.content() + " " + String.join(" ", document.keywords()));
		for (String token : queryTokens) {
			if (text.contains(token)) {
				score += document.keywords().contains(token) ? 3 : 1;
			}
		}
		return score;
	}

	/**
	 * 根据文档 ID 从本地知识库中查找客服知识文档。
	 * @param id 文档唯一标识
	 * @return 匹配的客服知识文档
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private Optional<CustomerKnowledgeDocument> documentById(String id) {
		return this.documents.stream().filter(document -> document.id().equals(id)).findFirst();
	}

	/**
	 * 根据期望主题和实际命中主题计算本轮 RAG 召回率。
	 * @param expectedTopics 期望命中的主题集合
	 * @param hitTopics 实际命中的主题集合
	 * @return 召回率，范围为 0 到 1
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private double recallRate(Set<String> expectedTopics, Set<String> hitTopics) {
		if (expectedTopics == null || expectedTopics.isEmpty()) {
			return hitTopics.isEmpty() ? 0.0 : 1.0;
		}
		long hitCount = expectedTopics.stream().filter(hitTopics::contains).count();
		return (double) hitCount / expectedTopics.size();
	}

	/**
	 * 归一化召回评估的期望主题，避免大小写或空白导致误判。
	 * @param topics 原始主题集合
	 * @return 归一化后的主题集合
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private Set<String> normalizeTopics(Set<String> topics) {
		if (topics == null || topics.isEmpty()) {
			return Set.of();
		}
		return topics.stream().map(this::normalize).filter(text -> !text.isBlank())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * 从用户问题中提取检索关键词，并补充命中的业务关键词。
	 * @param query 用户问题
	 * @return 检索关键词集合
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private Set<String> tokens(String query) {
		String text = normalize(query);
		Set<String> tokens = new LinkedHashSet<>();
		for (String token : text.split("[\\s,，。；;：:、/\\\\()（）\\[\\]{}<>《》\"']+")) {
			if (!token.isBlank()) {
				tokens.add(token);
			}
		}
		for (CustomerKnowledgeDocument document : this.documents) {
			for (String keyword : document.keywords()) {
				if (text.contains(keyword)) {
					tokens.add(keyword);
				}
			}
		}
		return tokens;
	}

	/**
	 * 对文本做基础归一化，统一用于关键词匹配和主题比较。
	 * @param value 原始文本
	 * @return 小写、去除多余空白后的文本
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	/**
	 * 重新加载内置知识和 JSON 自定义知识，保持内存检索集合与持久化文件一致。
	 *
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private void reloadDocuments() {
		Map<String, CustomerKnowledgeDocument> merged = new LinkedHashMap<>();
		for (CustomerKnowledgeDocument document : seedDocuments()) {
			merged.put(document.id(), document);
		}
		merged.putAll(readCustomDocumentMap());
		this.documents.clear();
		this.documents.addAll(merged.values());
	}

	/**
	 * 从 JSON 文件读取自定义客服知识，读取失败时返回空集合以保证应用可启动。
	 * @return 自定义客服知识映射
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private Map<String, CustomerKnowledgeDocument> readCustomDocumentMap() {
		if (!Files.exists(this.knowledgeFile)) {
			return new LinkedHashMap<>();
		}
		try {
			List<CustomerKnowledgeDocument> items = this.objectMapper.readValue(this.knowledgeFile.toFile(),
					new TypeReference<List<CustomerKnowledgeDocument>>() {
					});
			Map<String, CustomerKnowledgeDocument> map = new LinkedHashMap<>();
			for (CustomerKnowledgeDocument item : items) {
				if (item != null && item.id() != null && !item.id().isBlank()) {
					map.put(normalize(item.id()), normalizeDocument(item));
				}
			}
			return map;
		}
		catch (IOException ex) {
			return new LinkedHashMap<>();
		}
	}

	/**
	 * 把自定义客服知识写回 JSON 文件，作为页面知识管理和 RAG 检索的数据源。
	 * @param customDocuments 自定义客服知识映射
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private void writeCustomDocuments(Map<String, CustomerKnowledgeDocument> customDocuments) {
		try {
			Path parent = this.knowledgeFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.knowledgeFile.toFile(),
					new ArrayList<>(customDocuments.values()));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write customer knowledge file: " + this.knowledgeFile, ex);
		}
	}

	/**
	 * 将页面提交的知识请求转换为规范化客服知识文档。
	 * @param request 知识新增或更新请求
	 * @return 规范化客服知识文档
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private CustomerKnowledgeDocument toDocument(CustomerKnowledgeUpsertRequest request) {
		String content = request == null ? "" : request.content();
		String title = request == null ? "" : request.title();
		String topic = request == null ? "" : request.topic();
		String id = request == null ? "" : request.id();
		Set<String> keywords = new LinkedHashSet<>();
		if (request != null && request.keywords() != null) {
			for (String keyword : request.keywords()) {
				String safeKeyword = normalize(keyword);
				if (!safeKeyword.isBlank()) {
					keywords.add(safeKeyword);
				}
			}
		}
		if (keywords.isEmpty()) {
			keywords.add(normalize(topic));
			keywords.add(normalize(title));
		}
		String safeId = normalize(id);
		if (safeId.isBlank()) {
			safeId = "custom-" + Math.abs((normalize(title) + normalize(topic) + normalize(content)).hashCode());
		}
		return normalizeDocument(new CustomerKnowledgeDocument(safeId, blankDefault(title, "自定义客服知识"),
				blankDefault(topic, "custom"), blankDefault(content, "暂无内容"), keywords));
	}

	/**
	 * 规范化客服知识文档的 ID、主题和关键词。
	 * @param document 原始客服知识文档
	 * @return 规范化后的客服知识文档
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private CustomerKnowledgeDocument normalizeDocument(CustomerKnowledgeDocument document) {
		Set<String> keywords = document.keywords() == null ? Set.of() : document.keywords().stream()
				.map(this::normalize)
				.filter(text -> !text.isBlank())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return new CustomerKnowledgeDocument(normalize(document.id()), blankDefault(document.title(), "客服知识"),
				normalize(blankDefault(document.topic(), "custom")), blankDefault(document.content(), "暂无内容"),
				keywords);
	}

	/**
	 * 当文本为空时返回默认值，用于保证页面写入的知识具备可展示内容。
	 * @param value 原始文本
	 * @param defaultValue 默认文本
	 * @return 非空文本
	 * @author xyd
	 * @date 2026-05-19 23:48:12
	 */
	private String blankDefault(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	/**
	 * 初始化智能客服内置知识文档，作为本地 RAG 和向量库入库的数据源。
	 * @return 客服知识文档列表
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private List<CustomerKnowledgeDocument> seedDocuments() {
		List<CustomerKnowledgeDocument> docs = new ArrayList<>();
		docs.add(doc("refund-policy", "退货退款政策", "refund",
				"签收 7 天内且商品不影响二次销售时，可引导用户申请退货退款；超过 7 天需要说明平台规则和可选售后路径；涉及赔偿或直接退款时必须先查询订单事实，再给出合规解释。",
				"退款", "退货", "售后", "7天", "七天", "赔偿"));
		docs.add(doc("shipping-policy", "发货与物流政策", "shipping",
				"已付款订单默认 48 小时内发货；已发货订单应先查询物流；待发货订单应说明预计发货时间并创建提醒；物流异常时应提供快递单号、最新节点和后续处理时效。",
				"发货", "物流", "快递", "签收", "运输", "没到"));
		docs.add(doc("price-policy", "议价与价格策略", "price",
				"闲鱼议价先查询商品底价和库存；可接受范围内给出温和让步；低于底价时礼貌拒绝并说明商品状态、稀缺性或包邮成本；不得承诺超出策略的优惠。",
				"便宜", "优惠", "包邮", "议价", "小刀", "底价"));
		docs.add(doc("xianyu-reply-guide", "闲鱼回复规范", "xianyu",
				"闲鱼回复要短、自然、像真人；常用表达包括“还在的”“可以小刀”“发货前会检查”；不要承诺无法确认的信息；遇到退款、赔付、取消订单时先解释规则和下一步。",
				"闲鱼", "买家", "小刀", "还在", "自然", "二手"));
		docs.add(doc("wechat-service-guide", "微信客服规范", "wechat",
				"微信客服回复要完整、礼貌、可追踪；需要保留订单号和工单号；复杂售后建议创建工单并告知处理时效；不要使用过于随意的闲鱼话术。",
				"微信", "公众号", "企业微信", "小程序", "工单", "处理时效"));
		docs.add(doc("complaint-handling", "投诉处理规范", "complaint",
				"投诉处理先表达理解和歉意，再复述问题，随后给出可执行处理动作；态度激烈、差评威胁、监管投诉等场景应创建工单，记录诉求和证据。",
				"投诉", "差评", "生气", "举报", "升级", "安抚"));
		docs.add(doc("address-change-policy", "地址修改规范", "address",
				"用户要求改地址时必须先查询订单状态；未发货可提示用户提供新地址并记录工单；已发货只能建议联系快递或等待派送前改派，不能直接承诺一定修改成功。",
				"地址", "改地址", "收货人", "电话", "派送", "改派"));
		docs.add(doc("invoice-policy", "发票与凭证规范", "invoice",
				"用户索要发票、购买凭证或交易截图时，应先确认订单号和支付状态；二手闲置交易通常提供交易凭证，不默认承诺正式发票。",
				"发票", "凭证", "截图", "支付", "交易记录"));
		docs.add(doc("product-quality-policy", "商品质量与验货规范", "quality",
				"商品质量咨询应说明成色、瑕疵、配件和测试情况；发货前可承诺再次检查；收到后争议需结合签收时间、开箱证据和商品说明处理。",
				"质量", "成色", "瑕疵", "配件", "验货", "开箱"));
		docs.add(doc("conversation-style", "通用客服语气规范", "tone",
				"客服回复应简洁友好、先解决问题再解释规则；事实不明确时先询问商品号或订单号；不要编造物流、库存、退款状态或不存在的政策。",
				"语气", "礼貌", "简洁", "不要编造", "事实"));
		return docs;
	}

	/**
	 * 构建客服知识文档，并统一归一化主题和关键词。
	 * @param id 文档唯一标识
	 * @param title 文档标题
	 * @param topic 业务主题
	 * @param content 文档内容
	 * @param keywords 召回关键词
	 * @return 客服知识文档
	 * @author xyd
	 * @date 2026-05-19 13:31:27
	 */
	private CustomerKnowledgeDocument doc(String id, String title, String topic, String content, String... keywords) {
		Set<String> keywordSet = new LinkedHashSet<>();
		for (String keyword : keywords) {
			keywordSet.add(normalize(keyword));
		}
		return new CustomerKnowledgeDocument(id, title, normalize(topic), content, keywordSet);
	}

	/**
	 * 客服 RAG 运行状态，用于前端展示真实向量库开关、可用性和知识库规模。
	 *
	 * @param vectorEnabled 是否开启真实向量库检索开关
	 * @param realVectorStoreAvailable 是否存在真实 VectorStore Bean
	 * @param mode 当前实际检索模式
	 * @param knowledgeFile 本地知识库文件路径
	 * @param documentCount 当前知识文档数量
	 * @param topicCount 当前知识主题数量
	 * @param message 状态说明
	 * @author xyd
	 * @date 2026-05-21 00:00:00
	 */
	public record CustomerPolicyRagStatus(boolean vectorEnabled, boolean realVectorStoreAvailable, String mode,
			String knowledgeFile, int documentCount, int topicCount, String message) {
	}

}
