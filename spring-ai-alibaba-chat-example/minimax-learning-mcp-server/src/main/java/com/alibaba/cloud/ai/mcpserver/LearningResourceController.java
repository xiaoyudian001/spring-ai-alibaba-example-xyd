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

package com.alibaba.cloud.ai.mcpserver;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/learning-mcp")
public class LearningResourceController {

	private final LearningResourceRepository repository;

	public LearningResourceController(LearningResourceRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/health")
	public Map<String, Object> health() {
		return Map.of(
				"status", "UP",
				"server", "minimax-learning-mcp-server",
				"mcpSseUrl", "http://localhost:19000/sse",
				"mcpMessageEndpoint", "/mcp/messages",
				"resourceSource", this.repository.resourceSource(),
				"resourceFile", this.repository.resourceFilePath().toAbsolutePath().normalize().toString(),
				"resourceCount", this.repository.all().size(),
				"topics", this.repository.listTopics());
	}

	@GetMapping("/resources")
	public List<LearningResource> all() {
		return this.repository.all();
	}

	@GetMapping("/resources/{id}")
	public LearningResource get(@PathVariable String id) {
		return this.repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习资源不存在：" + id));
	}

	@PostMapping("/resources")
	@ResponseStatus(HttpStatus.CREATED)
	public LearningResource create(@RequestBody LearningResource resource) {
		try {
			return this.repository.create(resource);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	@PutMapping("/resources/{id}")
	public LearningResource update(@PathVariable String id, @RequestBody LearningResource resource) {
		try {
			return this.repository.update(id, resource)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习资源不存在：" + id));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	@DeleteMapping("/resources/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String id) {
		if (!this.repository.delete(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学习资源不存在：" + id);
		}
	}

	@GetMapping("/resources/search")
	public List<LearningResource> search(@RequestParam(value = "query", defaultValue = "") String query,
			@RequestParam(value = "limit", defaultValue = "3") Integer limit) {
		return this.repository.search(query, limit);
	}

}
