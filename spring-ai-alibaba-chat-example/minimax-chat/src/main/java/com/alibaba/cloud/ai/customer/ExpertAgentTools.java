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
 * See the the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.customer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 智能客服专家 Agent 工具集，把售后专家、物流专家、投诉专家封装为主客服 Agent 可调用的工具。
 * <p>
 * 专家工具说明：
 * <ul>
 *     <li>productExpertTool：商品专家，处理商品咨询、议价、规格对比等</li>
 *     <li>orderExpertTool：订单专家，处理订单状态、物流、退款进度等</li>
 *     <li>complaintExpertTool：投诉专家，处理投诉升级、安抚技巧、赔偿方案等</li>
 *     <li>logisticsExpertTool：物流专家，处理发货延迟、物流异常、签收问题等</li>
 * </ul>
 *
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Component
public class ExpertAgentTools {

private final CustomerMcpService customerMcpService;

private final CustomerPolicyRagService policyRagService;

private final ToolCallDebugRecorder debugRecorder;

/**
 * 创建专家工具集。
 * @param customerMcpService 客服 MCP 服务
 * @param policyRagService 客服 RAG 服务
 * @param debugRecorder 调试记录器
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public ExpertAgentTools(CustomerMcpService customerMcpService, CustomerPolicyRagService policyRagService,
ToolCallDebugRecorder debugRecorder) {
this.customerMcpService = customerMcpService;
this.policyRagService = policyRagService;
this.debugRecorder = debugRecorder;
}

/**
 * 商品专家工具，处理商品咨询、议价、规格对比。
 * @param query 商品相关问题
 * @param productId 商品 ID
 * @return 商品专家回复
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Tool(description = "商品专家工具。当用户询问商品是否还在、价格、规格、成色、库存、议价、优惠时使用。需要时传入商品ID。")
public String productExpertTool(
@ToolParam(description = "商品相关问题，例如 商品还在吗、价格多少、规格是什么") String query,
@ToolParam(description = "商品 ID，例如 p-1001。不确定时可使用 p-1001") String productId) {
StringBuilder result = new StringBuilder();
result.append("【商品专家回复】\n\n");
if (productId != null && !productId.isBlank()) {
String productInfo = this.customerMcpService.getProductInfo(productId);
result.append("商品信息：").append(productInfo).append("\n\n");
this.debugRecorder.record("productExpertTool", Map.of("query", query, "productId", productId), productInfo);
}
String ragInfo = this.policyRagService.search(query + " 商品", 2);
result.append("商品政策参考：").append(ragInfo);
return result.toString();
}

/**
 * 订单专家工具，处理订单状态、退款进度、售后问题。
 * @param query 订单相关问题
 * @param orderId 订单 ID
 * @return 订单专家回复
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Tool(description = "订单专家工具。当用户询问订单状态、是否发货、退款进度、退货政策、售后问题时使用。需要时传入订单ID。")
public String orderExpertTool(
@ToolParam(description = "订单相关问题，例如 订单状态、退款进度、能否退货") String query,
@ToolParam(description = "订单 ID，例如 o-202605150001。不确定时可使用 o-202605150001") String orderId) {
StringBuilder result = new StringBuilder();
result.append("【订单专家回复】\n\n");
if (orderId != null && !orderId.isBlank()) {
String orderInfo = this.customerMcpService.getOrderInfo(orderId);
result.append("订单信息：").append(orderInfo).append("\n\n");
this.debugRecorder.record("orderExpertTool", Map.of("query", query, "orderId", orderId), orderInfo);
}
String refundInfo = orderId != null ? this.customerMcpService.getRefundEligibility(orderId) : "";
if (!refundInfo.isBlank()) {
result.append("退款资格：").append(refundInfo).append("\n\n");
}
String ragInfo = this.policyRagService.search(query + " 退款 退货 售后", 2);
result.append("退款政策参考：").append(ragInfo);
return result.toString();
}

/**
 * 物流专家工具，处理发货延迟、物流异常、签收问题。
 * @param query 物流相关问题
 * @param orderId 订单 ID
 * @return 物流专家回复
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Tool(description = "物流专家工具。当用户询问发货时间、物流位置、快递延迟、签收问题时使用。需要时传入订单ID。")
public String logisticsExpertTool(
@ToolParam(description = "物流相关问题，例如 发货了吗、物流到哪了、为什么还没到") String query,
@ToolParam(description = "订单 ID，例如 o-202605150001。不确定时可使用 o-202605150001") String orderId) {
StringBuilder result = new StringBuilder();
result.append("【物流专家回复】\n\n");
if (orderId != null && !orderId.isBlank()) {
String logisticsInfo = this.customerMcpService.getLogisticsInfo(orderId);
result.append("物流信息：").append(logisticsInfo).append("\n\n");
this.debugRecorder.record("logisticsExpertTool", Map.of("query", query, "orderId", orderId), logisticsInfo);
}
String ragInfo = this.policyRagService.search(query + " 发货 物流 快递", 2);
result.append("发货政策参考：").append(ragInfo);
return result.toString();
}

/**
 * 投诉专家工具，处理投诉升级、安抚技巧、赔偿方案。
 * @param query 投诉相关问题
 * @param orderId 订单 ID
 * @param customerMood 客户情绪状态：angry（愤怒）、upset（不满）、neutral（中立）
 * @return 投诉专家回复
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Tool(description = "投诉专家工具。当用户表达强烈不满、投诉、差评威胁、要求赔偿、情绪激动时使用。")
public String complaintExpertTool(
@ToolParam(description = "投诉相关问题，例如 商品有瑕疵、迟迟不到、服务态度差") String query,
@ToolParam(description = "订单 ID，例如 o-202605150001。不确定时可使用 o-202605150001") String orderId,
@ToolParam(description = "客户情绪状态：angry（愤怒）、upset（不满）、neutral（中立）。根据用户语气判断。") String customerMood) {
StringBuilder result = new StringBuilder();
result.append("【投诉专家回复】\n\n");
String calmingPhrase = generateCalmingPhrase(customerMood);
result.append("安抚话术：").append(calmingPhrase).append("\n\n");
this.debugRecorder.record("complaintExpertTool",
Map.of("query", query, "orderId", orderId, "mood", customerMood), calmingPhrase);
if (orderId != null && !orderId.isBlank()) {
String orderInfo = this.customerMcpService.getOrderInfo(orderId);
result.append("订单信息：").append(orderInfo).append("\n\n");
}
String ragInfo = this.policyRagService.search(query + " 投诉 赔偿 服务态度", 3);
result.append("投诉处理参考：").append(ragInfo);
return result.toString();
}

/**
 * 售后专家工具，处理退换货、质量问题、维修咨询。
 * @param query 售后相关问题
 * @param orderId 订单 ID
 * @param productId 商品 ID
 * @return 售后专家回复
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
@Tool(description = "售后专家工具。当用户询问退换货、维修、质量问题、售后政策时使用。")
public String afterSalesExpertTool(
@ToolParam(description = "售后相关问题，例如 商品坏了能退吗、怎么申请售后") String query,
@ToolParam(description = "订单 ID，例如 o-202605150001") String orderId,
@ToolParam(description = "商品 ID，例如 p-1001") String productId) {
StringBuilder result = new StringBuilder();
result.append("【售后专家回复】\n\n");
if (orderId != null && !orderId.isBlank()) {
String afterSaleStatus = this.customerMcpService.getAfterSaleStatus(orderId);
result.append("售后状态：").append(afterSaleStatus).append("\n\n");
this.debugRecorder.record("afterSalesExpertTool",
Map.of("query", query, "orderId", orderId, "productId", productId), afterSaleStatus);
}
String ragInfo = this.policyRagService.search(query + " 退换货 售后 维修 质量", 3);
result.append("售后政策参考：").append(ragInfo);
return result.toString();
}

/**
 * 生成安抚话术。
 * @param mood 情绪状态
 * @return 安抚话术
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
private String generateCalmingPhrase(String mood) {
if ("angry".equalsIgnoreCase(mood)) {
return "非常抱歉给您带来了不好的体验，我非常理解您现在的心情。请您先消消气，我一定会认真对待您的问题，帮您妥善处理。";
}
else if ("upset".equalsIgnoreCase(mood)) {
return "抱歉给您带来困扰了，我理解您的心情。请放心，我会尽全力帮您解决问题。";
}
else {
return "感谢您的反馈，我会认真了解情况并尽快为您处理。";
}
}

/**
 * 获取所有专家工具描述。
 * @return 工具描述
 * @author xyd
 * @date 2026-05-22 14:00:00
 */
public String listExpertTools() {
return """
【智能客服专家工具列表】

1. productExpertTool - 商品专家
   适用场景：商品咨询、议价、规格对比
   参数：query（问题）、productId（商品ID）

2. orderExpertTool - 订单专家
   适用场景：订单状态、退款进度、售后问题
   参数：query（问题）、orderId（订单ID）

3. logisticsExpertTool - 物流专家
   适用场景：发货延迟、物流异常、签收问题
   参数：query（问题）、orderId（订单ID）

4. complaintExpertTool - 投诉专家
   适用场景：投诉升级、安抚技巧、赔偿方案
   参数：query（问题）、orderId（订单ID）、customerMood（情绪）

5. afterSalesExpertTool - 售后专家
   适用场景：退换货、质量问题、维修咨询
   参数：query（问题）、orderId（订单ID）、productId（商品ID）
""";
}

}
