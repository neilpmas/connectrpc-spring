package dev.neilmason.connect.testapp;

import dev.neilmason.connect.test.greet.v1.GreetServiceGrpc;
import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.test.greet.v1.SayHelloResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class GreetServiceImpl extends GreetServiceGrpc.GreetServiceImplBase {

    @Override
    public void sayHello(SayHelloRequest request, StreamObserver<SayHelloResponse> responseObserver) {
        responseObserver.onNext(SayHelloResponse.newBuilder()
            .setGreeting("Hello, " + request.getName() + "!")
            .build());
        responseObserver.onCompleted();
    }
}
