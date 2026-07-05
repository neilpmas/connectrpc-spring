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

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.rpc.Code;
import dev.neilmason.connect.test.greet.v1.GreetServiceGrpc;
import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.test.greet.v1.SayHelloResponse;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

public class GreetServiceImpl extends GreetServiceGrpc.GreetServiceImplBase {

	@Override
	public void sayHello(SayHelloRequest request, StreamObserver<SayHelloResponse> responseObserver) {
		switch (request.getName()) {
			case "trigger-resource-exhausted" -> {
				responseObserver
					.onError(Status.RESOURCE_EXHAUSTED.withDescription("Quota exceeded").asRuntimeException());
				return;
			}
			case "trigger-cancelled" -> {
				responseObserver.onError(Status.CANCELLED.withDescription("Request cancelled").asRuntimeException());
				return;
			}
			case "trigger-details" -> {
				Any detail = Any.pack(StringValue.of("retry-after-1s"));
				com.google.rpc.Status rpcStatus = com.google.rpc.Status.newBuilder()
					.setCode(Code.UNAVAILABLE_VALUE)
					.setMessage("overloaded: back off and retry")
					.addDetails(detail)
					.build();
				responseObserver.onError(StatusProto.toStatusRuntimeException(rpcStatus));
				return;
			}
			case "trigger-slow" -> {
				try {
					Thread.sleep(300);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			default -> {
			}
		}
		responseObserver.onNext(SayHelloResponse.newBuilder().setGreeting("Hello, " + request.getName() + "!").build());
		responseObserver.onCompleted();
	}

}
