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

import java.util.concurrent.CompletableFuture;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.jspecify.annotations.Nullable;

// A synthetic, in-process ServerCall<ReqT, RespT> that lets ConnectFilter drive a real gRPC
// ServerCallHandler for a single unary invocation, instead of reflecting into the service
// implementation directly. There's no real transport underneath this call: no network connection,
// no deadline propagation, no client-driven cancellation, and no flow control, since this library
// only ever drives one request message through one handler and waits for the one response.
//
// The response side of the ServerCall contract (sendHeaders/sendMessage/close) is what the
// generated service code calls back into -- via ServerCalls.asyncUnaryCall(), wired up by
// bindService() -- when the application's responseObserver.onNext()/onCompleted()/onError() fires.
// This class captures that callback into the same CompletableFuture-based completion signal the
// rest of ConnectFilter's reactive chain already expects.
final class SynthesizedServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {

	private final MethodDescriptor<ReqT, RespT> methodDescriptor;

	private final CompletableFuture<RespT> future;

	// Only unset if a misbehaving service calls close() without sendMessage() first;
	// well-behaved
	// unary gRPC methods always send exactly one message before closing with Status.OK.
	private @Nullable RespT message;

	SynthesizedServerCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CompletableFuture<RespT> future) {
		this.methodDescriptor = methodDescriptor;
		this.future = future;
	}

	@Override
	public void sendHeaders(Metadata headers) {
		// No transport to write response headers to; nothing observes them in this flow.
	}

	@Override
	public void sendMessage(RespT message) {
		this.message = message;
	}

	@Override
	@SuppressWarnings("DataFlowIssue") // CompletableFuture.complete(null) is
										// well-defined;
	// a well-behaved unary call always sent a message before closing OK, but this can't
	// be proven statically.
	public void close(Status status, Metadata trailers) {
		if (status.isOk()) {
			this.future.complete(this.message);
		}
		else {
			this.future.completeExceptionally(status.asRuntimeException(trailers));
		}
	}

	@Override
	public boolean isCancelled() {
		// No real client connection, so this call is never cancelled out from under the
		// handler.
		return false;
	}

	@Override
	public void request(int numMessages) {
		// No-op: flow control has no meaning for a synthetic, single-message in-process
		// call.
	}

	@Override
	public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
		return this.methodDescriptor;
	}

}
