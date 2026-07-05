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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

public class ConnectServiceRegistry {

	private final Map<String, MethodEntry> methods = new ConcurrentHashMap<>();

	public ConnectServiceRegistry(List<BindableService> services) {
		this(services, List.of());
	}

	// Mirrors spring-grpc-core's DefaultGrpcServiceConfigurer: discovers beans annotated
	// @GlobalConnectInterceptor, filters to actual ServerInterceptor instances (silently
	// skipping
	// any that aren't -- validating bean shape isn't this library's job), and sorts them
	// respecting
	// @Order/Ordered via AnnotationAwareOrderComparator, the same utility Spring's real
	// implementation uses for this.
	public ConnectServiceRegistry(List<BindableService> services, ApplicationContext applicationContext) {
		this(services, findGlobalInterceptors(applicationContext));
	}

	private ConnectServiceRegistry(List<BindableService> services, List<ServerInterceptor> interceptors) {
		for (BindableService service : services) {
			ServerServiceDefinition definition = service.bindService();
			// interceptForward wraps the definition so each interceptor's interceptCall()
			// runs (in
			// order) before the underlying ServerCallHandler -- the same handler
			// ConnectFilter drives
			// via SynthesizedServerCall -- ever sees the call.
			if (!interceptors.isEmpty()) {
				definition = ServerInterceptors.interceptForward(definition, interceptors);
			}
			for (ServerMethodDefinition<?, ?> methodDef : definition.getMethods()) {
				MethodDescriptor<?, ?> descriptor = methodDef.getMethodDescriptor();
				// fullMethodName is "package.Service/MethodName"
				this.methods.put(descriptor.getFullMethodName(), new MethodEntry(methodDef, descriptor));
			}
		}
	}

	private static List<ServerInterceptor> findGlobalInterceptors(ApplicationContext applicationContext) {
		List<ServerInterceptor> interceptors = new ArrayList<>();
		applicationContext.getBeansWithAnnotation(GlobalConnectInterceptor.class).values().forEach((bean) -> {
			if (bean instanceof ServerInterceptor serverInterceptor) {
				interceptors.add(serverInterceptor);
			}
		});
		AnnotationAwareOrderComparator.sort(interceptors);
		return interceptors;
	}

	public @Nullable MethodEntry lookup(String serviceName, String methodName) {
		return this.methods.get(serviceName + "/" + methodName);
	}

	// Holds the real ServerMethodDefinition so dispatch can invoke through its
	// ServerCallHandler
	// (the same handler a real gRPC server would use), rather than reaching into the
	// service
	// implementation via reflection.
	public record MethodEntry(ServerMethodDefinition<?, ?> methodDefinition, MethodDescriptor<?, ?> descriptor) {
	}

}
