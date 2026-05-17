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
 * 客服物流信息模型，用于回答用户关于快递、发货和签收状态的问题。
 *
 * @param orderId 订单 ID
 * @param carrier 快递公司
 * @param trackingNo 快递单号
 * @param status 物流状态
 * @param latestEvent 最新物流事件
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public record LogisticsInfo(String orderId, String carrier, String trackingNo, String status, String latestEvent) {
}
