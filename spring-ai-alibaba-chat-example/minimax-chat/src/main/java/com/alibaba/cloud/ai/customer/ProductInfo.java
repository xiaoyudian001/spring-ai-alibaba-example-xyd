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
 * 客服商品信息模型，用于商品咨询、议价和闲鱼回复场景中的事实依据。
 *
 * @param productId 商品 ID
 * @param title 商品标题
 * @param status 商品状态
 * @param price 商品当前售价
 * @param floorPrice 商品最低可接受价格
 * @param description 商品说明
 * @author xyd
 * @date 2026-05-15 14:57:11
 */
public record ProductInfo(String productId, String title, String status, int price, int floorPrice,
		String description) {
}
