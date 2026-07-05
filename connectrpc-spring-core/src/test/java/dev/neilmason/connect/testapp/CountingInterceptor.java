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

import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

// Lets calls through but counts how many times it was invoked, proving the interceptor runs on
// every dispatched call (not just the first).
public class CountingInterceptor implements ServerInterceptor {

	private final AtomicInteger count = new AtomicInteger();

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		this.count.incrementAndGet();
		return next.startCall(call, headers);
	}

	public int callCount() {
		return this.count.get();
	}

}
