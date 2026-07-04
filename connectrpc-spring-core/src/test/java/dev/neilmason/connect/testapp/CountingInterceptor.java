package dev.neilmason.connect.testapp;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

// Lets calls through but counts how many times it was invoked, proving the interceptor runs on
// every dispatched call (not just the first).
public class CountingInterceptor implements ServerInterceptor {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        count.incrementAndGet();
        return next.startCall(call, headers);
    }

    public int callCount() {
        return count.get();
    }
}
