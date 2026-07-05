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

package dev.neilmason.connect.testapp;

import java.util.List;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import org.springframework.core.Ordered;

// Appends its name to a shared list before delegating, proving @Order/Ordered is respected when
// multiple global interceptors are registered. Implements Ordered directly (rather than relying on
// @Order on a @Bean factory method, which AnnotationAwareOrderComparator.sort() does not resolve
// from a bare list of bean instances) since that's the precedence AnnotationAwareOrderComparator
// actually inspects on the instance itself.
public class OrderRecordingInterceptor implements ServerInterceptor, Ordered {

	private final String name;

	private final List<String> invocationOrder;

	private final int order;

	public OrderRecordingInterceptor(String name, List<String> invocationOrder, int order) {
		this.name = name;
		this.invocationOrder = invocationOrder;
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		this.invocationOrder.add(this.name);
		return next.startCall(call, headers);
	}

}
