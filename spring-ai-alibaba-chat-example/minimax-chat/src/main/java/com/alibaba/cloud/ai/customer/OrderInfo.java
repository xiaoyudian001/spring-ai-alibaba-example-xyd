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

/**
 * 客服订单信息模型，用于订单状态、物流查询、售后政策判断和人工确认场景。
 *
 * @param orderId 订单 ID
 * @param productId 商品 ID
 * @param userId 用户 ID
 * @param status 订单状态
 * @param paidAmount 实付金额
 * @param paidAt 支付时间
 * @param shippedAt 发货时间
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public record OrderInfo(String orderId, String productId, String userId, String status, int paidAmount, String paidAt,
		String shippedAt) {
}
