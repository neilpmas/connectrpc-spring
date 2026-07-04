package dev.neilmason.connect.testapp;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.core.Ordered;

import java.util.List;

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
        return order;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        invocationOrder.add(name);
        return next.startCall(call, headers);
    }
}
