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

import java.util.List;
import java.util.UUID;

import com.alibaba.cloud.ai.tool.ToolCallDebugRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 智能客服调用链路日志埋点，统一把 ReactAgent、StateGraph 和 Multi-Agent 执行过程输出到 IDEA 控制台。
 *
 * @author xyd
 * @date 2026-05-19 00:20:26
 */
@Component
public class CustomerServiceTraceLogger {

	private static final Logger logger = LoggerFactory.getLogger(CustomerServiceTraceLogger.class);

	/**
	 * 记录一次智能客服链路开始，并返回本轮 traceId。
	 * @param chainMode 链路模式
	 * @param userId 用户唯一标识
	 * @param channel 客服渠道
	 * @param message 用户原始输入
	 * @return 本轮 traceId
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public String start(String chainMode, String userId, ChannelType channel, String message) {
		String traceId = "customer-trace-" + UUID.randomUUID();
		logger.info("[客服链路][{}][{}] START userId={}, channel={}, message={}", chainMode, traceId,
				normalize(userId), channel == null ? ChannelType.WEB : channel, normalize(message));
		return traceId;
	}

	/**
	 * 记录智能客服链路中的一个步骤。
	 * @param chainMode 链路模式
	 * @param traceId 本轮 traceId
	 * @param stepName 步骤名称
	 * @param detail 步骤详情
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public void step(String chainMode, String traceId, String stepName, String detail) {
		logger.debug("[客服链路][{}][{}] STEP {} -> {}", chainMode, normalize(traceId), normalize(stepName),
				normalize(detail));
	}

	/**
	 * 记录智能客服链路中的工具调用汇总。
	 * @param chainMode 链路模式
	 * @param traceId 本轮 traceId
	 * @param toolCalls 工具调用明细
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public void tools(String chainMode, String traceId, List<ToolCallDebugRecorder.ToolCallDebug> toolCalls) {
		int count = toolCalls == null ? 0 : toolCalls.size();
		String names = toolCalls == null || toolCalls.isEmpty() ? "none"
				: String.join(",", toolCalls.stream().map(ToolCallDebugRecorder.ToolCallDebug::name).toList());
		logger.debug("[客服链路][{}][{}] TOOLS count={}, names={}", chainMode, normalize(traceId), count, names);
	}

	/**
	 * 记录智能客服链路正常结束。
	 * @param chainMode 链路模式
	 * @param traceId 本轮 traceId
	 * @param intent 客服意图
	 * @param answer 模型回答
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public void finish(String chainMode, String traceId, CustomerServiceIntent intent, String answer) {
		logger.info("[客服链路][{}][{}] FINISH intent={}, answerSummary={}", chainMode, normalize(traceId), intent,
				summary(answer));
	}

	/**
	 * 记录智能客服链路异常结束。
	 * @param chainMode 链路模式
	 * @param traceId 本轮 traceId
	 * @param ex 异常对象
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	public void error(String chainMode, String traceId, Exception ex) {
		logger.error("[客服链路][{}][{}] ERROR {}", chainMode, normalize(traceId),
				ex == null ? "unknown" : ex.getMessage(), ex);
	}

	/**
	 * 规范化日志文本，避免空值和换行破坏控制台可读性。
	 * @param value 原始文本
	 * @return 规范化文本
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String normalize(String value) {
		return value == null || value.isBlank() ? "-" : value.replaceAll("\\s+", " ").trim();
	}

	/**
	 * 生成适合控制台展示的回答摘要。
	 * @param answer 模型回答
	 * @return 回答摘要
	 * @author xyd
	 * @date 2026-05-19 00:20:26
	 */
	private String summary(String answer) {
		String text = normalize(answer);
		return text.length() <= 120 ? text : text.substring(0, 120) + "...";
	}

}
