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

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 智能客服 MCP Server 启动类，负责把商品、订单、物流、售后和工单能力暴露为 MCP 工具。
 *
 * @author xyd
 * @date 2026-05-22 02:32:00
 */
@SpringBootApplication
public class MiniMaxCustomerMcpServerApplication {

	/**
	 * 启动智能客服 MCP Server。
	 * @param args JVM 启动参数
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	public static void main(String[] args) {
		SpringApplication.run(MiniMaxCustomerMcpServerApplication.class, args);
	}

	/**
	 * 注册智能客服 MCP 工具回调，供 Spring AI MCP Server 对外暴露工具列表。
	 * @param customerMcpTool 智能客服工具实现
	 * @return MCP 工具回调提供器
	 * @author xyd
	 * @date 2026-05-22 02:32:00
	 */
	@Bean
	public ToolCallbackProvider customerServiceTools(CustomerMcpTool customerMcpTool) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(customerMcpTool)
				.build();
	}

}
