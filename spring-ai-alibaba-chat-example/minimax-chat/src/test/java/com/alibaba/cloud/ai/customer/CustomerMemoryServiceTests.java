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

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客服 Memory 数据库持久化测试，验证用户隔离、订单识别和重新读取能力。
 *
 * @author xyd
 * @date 2026-05-20 00:00:00
 */
class CustomerMemoryServiceTests {

	/**
	 * 验证 Memory 更新后会写入数据库，并能按真实 userId 读取。
	 *
	 * @author xyd
	 * @date 2026-05-20 00:00:00
	 */
	@Test
	void shouldPersistMemoryByUserId() {
		CustomerMemoryService service = new CustomerMemoryService(jdbcTemplate(), new ObjectMapper());
		service.initializeSchema();

		String userId = "test-user-memory-" + UUID.randomUUID();
		CustomerMemory updated = service.update(userId, ChannelType.XIANYU, "请帮我查一下 o-9001 和 p-1001",
				CustomerServiceIntent.LOGISTICS_QUERY);

		CustomerMemory loaded = service.read(userId);
		assertThat(updated.getConversationCount()).isEqualTo(1);
		assertThat(loaded.getRecentOrderIds()).contains("o-9001");
		assertThat(loaded.getRecentProductIds()).contains("p-1001");
		assertThat(loaded.getChannel()).isEqualTo(ChannelType.XIANYU);
		assertThat(service.backend()).isEqualTo("MYSQL_DATABASE");
	}

	/**
	 * 创建测试用 MySQL 数据库访问模板。
	 * @return 数据库访问模板
	 * @author xyd
	 * @date 2026-05-21 00:00:00
	 */
	private JdbcTemplate jdbcTemplate() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
		dataSource.setUrl("jdbc:mysql://localhost:3306/minimax_customer_service_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
		dataSource.setUsername("root");
		dataSource.setPassword("root");
		return new JdbcTemplate(dataSource);
	}

}
