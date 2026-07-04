package dev.neilmason.connect.testapp;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

// Rejects every call before it reaches the service method, proving the interceptor actually runs
// ahead of the underlying ServerCallHandler.
public class RejectingInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        call.close(Status.PERMISSION_DENIED.withDescription("rejected by test interceptor"), new Metadata());
        return new ServerCall.Listener<>() {};
    }
}
