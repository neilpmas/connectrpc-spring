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
import org.springframework.stereotype.Component;

@Component
public class GreetServiceImpl extends GreetServiceGrpc.GreetServiceImplBase {

    @Override
    public void sayHello(SayHelloRequest request, StreamObserver<SayHelloResponse> responseObserver) {
        switch (request.getName()) {
            case "trigger-resource-exhausted" -> {
                responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("Quota exceeded").asRuntimeException());
                return;
            }
            case "trigger-cancelled" -> {
                responseObserver.onError(Status.CANCELLED
                    .withDescription("Request cancelled").asRuntimeException());
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            default -> { }
        }
        responseObserver.onNext(SayHelloResponse.newBuilder()
            .setGreeting("Hello, " + request.getName() + "!")
            .build());
        responseObserver.onCompleted();
    }
}
