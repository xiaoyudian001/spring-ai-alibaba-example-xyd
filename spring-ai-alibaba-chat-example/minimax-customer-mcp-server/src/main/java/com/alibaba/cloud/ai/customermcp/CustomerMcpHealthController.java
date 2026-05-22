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

package com.alibaba.cloud.ai.customermcp;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能客服 MCP Server 健康检查接口，用于本地启动后快速确认工具服务已经运行。
 *
 * @author xyd
 * @date 2026-05-22 02:32:00
 */
@RestController
public class CustomerMcpHealthController {

	/**
	 * 返回智能客服 MCP Server 的健康状态和可用工具名称。
	 * @return MCP Server 健康状态
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@GetMapping("/customer-mcp/health")
	public Map<String, Object> health() {
		return Map.of("status", "UP", "server", "minimax-customer-mcp-server", "sseUrl", "http://localhost:19001/sse",
				"tools", List.of("getProductInfo", "getOrderInfo", "getLogisticsInfo", "getPricePolicy",
						"getRefundEligibility", "getAfterSaleStatus", "createCustomerTicket",
						"requestHumanHandoff"));
	}

}
