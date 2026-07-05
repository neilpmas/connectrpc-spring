/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.neilmason.connect;

import java.util.List;

import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.testapp.GreetServiceImpl;
import io.grpc.BindableService;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

class ConnectFilterMaxMessageSizeTests {

	private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

	@Test
	void oversizedRequest_shouldReturn413WithResourceExhaustedCode() {
		List<BindableService> services = List.of(new GreetServiceImpl());
		ConnectServiceRegistry registry = new ConnectServiceRegistry(services);
		ConnectFilter filter = new ConnectFilter(registry, "/connect", DataSize.ofBytes(10).toBytes(), true,
				List.of("*"));
		WebTestClient webTestClient = WebTestClient.bindToWebHandler((exchange) -> exchange.getResponse().setComplete())
			.webFilter(filter)
			.build();

		SayHelloRequest request = SayHelloRequest.newBuilder().setName("a".repeat(100)).build();

		webTestClient.post()
			.uri("/connect/greet.v1.GreetService/SayHello")
			.contentType(APPLICATION_PROTO)
			.bodyValue(request.toByteArray())
			.exchange()
			.expectStatus()
			.isEqualTo(413)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("resource_exhausted");
	}

}
