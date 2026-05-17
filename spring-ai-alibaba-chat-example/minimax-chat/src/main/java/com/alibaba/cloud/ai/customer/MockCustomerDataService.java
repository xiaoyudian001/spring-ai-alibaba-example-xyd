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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * 智能客服 Mock 业务数据服务，第一阶段替代真实闲鱼、微信、订单、物流和工单系统。
 *
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
@Service
public class MockCustomerDataService {

	private final Map<String, ProductInfo> products = new LinkedHashMap<>();

	private final Map<String, OrderInfo> orders = new LinkedHashMap<>();

	private final Map<String, LogisticsInfo> logistics = new LinkedHashMap<>();

	/**
	 * 初始化客服 Mock 商品、订单和物流数据，保证本地无需外部系统即可测试。
	 *
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public MockCustomerDataService() {
		this.products.put("p-1001", new ProductInfo("p-1001", "九成新机械键盘", "在售", 199, 170,
				"闲置机械键盘，青轴，九成新，支持小刀但不包邮。"));
		this.products.put("p-1002", new ProductInfo("p-1002", "二手 Java 进阶书籍套装", "在售", 88, 75,
				"Spring、JVM、并发编程书籍套装，适合 Java 开发者进阶学习。"));
		this.orders.put("o-202605150001", new OrderInfo("o-202605150001", "p-1001", "default-user", "已发货", 199,
				"2026-05-14 21:30:00", "2026-05-15 10:20:00"));
		this.orders.put("o-202605150002", new OrderInfo("o-202605150002", "p-1002", "user-a", "待发货", 88,
				"2026-05-15 09:10:00", ""));
		this.logistics.put("o-202605150001", new LogisticsInfo("o-202605150001", "顺丰速运", "SF123456789CN", "运输中",
				"2026-05-15 13:40:00 包裹已到达上海转运中心。"));
	}

	/**
	 * 根据商品 ID 查询商品事实信息。
	 * @param productId 商品 ID
	 * @return 商品事实信息文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String getProductInfo(String productId) {
		ProductInfo product = this.products.get(normalize(productId, "p-1001"));
		if (product == null) {
			return "未找到商品：" + productId + "。可提示用户提供正确商品编号。";
		}
		return "商品ID：" + product.productId() + "；标题：" + product.title() + "；状态：" + product.status()
				+ "；售价：" + product.price() + " 元；底价：" + product.floorPrice() + " 元；说明："
				+ product.description();
	}

	/**
	 * 根据订单 ID 查询订单事实信息。
	 * @param orderId 订单 ID
	 * @return 订单事实信息文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String getOrderInfo(String orderId) {
		OrderInfo order = this.orders.get(normalize(orderId, "o-202605150001"));
		if (order == null) {
			return "未找到订单：" + orderId + "。可提示用户提供正确订单编号。";
		}
		return "订单ID：" + order.orderId() + "；商品ID：" + order.productId() + "；用户：" + order.userId()
				+ "；状态：" + order.status() + "；实付：" + order.paidAmount() + " 元；支付时间：" + order.paidAt()
				+ "；发货时间：" + blankDefault(order.shippedAt(), "暂未发货");
	}

	/**
	 * 根据订单 ID 查询物流事实信息。
	 * @param orderId 订单 ID
	 * @return 物流事实信息文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String getLogisticsInfo(String orderId) {
		LogisticsInfo info = this.logistics.get(normalize(orderId, "o-202605150001"));
		if (info == null) {
			return "该订单暂未产生物流信息：" + orderId + "。";
		}
		return "订单ID：" + info.orderId() + "；快递：" + info.carrier() + "；单号：" + info.trackingNo()
				+ "；状态：" + info.status() + "；最新动态：" + info.latestEvent();
	}

	/**
	 * 创建客服工单的 Mock 结果，后续可替换为真实工单系统或 MCP Tool。
	 * @param conversationId 会话 ID
	 * @param summary 工单摘要
	 * @return 工单创建结果
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	public String createTicket(String conversationId, String summary) {
		return "已创建客服工单：ticket-" + Math.abs((conversationId + summary).hashCode()) + "；摘要：" + summary;
	}

	/**
	 * 规范化业务 ID，空值时使用默认值支持快速测试。
	 * @param value 原始业务 ID
	 * @param defaultValue 默认业务 ID
	 * @return 规范化业务 ID
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private String normalize(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value.trim().toLowerCase();
	}

	/**
	 * 为空字符串提供默认展示文本。
	 * @param value 原始文本
	 * @param defaultValue 默认文本
	 * @return 可展示文本
	 * @author xyd
	 * @date 2026-05-15 14:57:11
	 */
	private String blankDefault(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value;
	}

}
