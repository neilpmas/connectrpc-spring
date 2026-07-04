package dev.neilmason.connect;

import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.testapp.GreetServiceImpl;
import io.grpc.BindableService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

import java.util.List;

class ConnectFilterMaxMessageSizeTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    @Test
    void oversizedRequest_shouldReturn413WithResourceExhaustedCode() {
        List<BindableService> services = List.of(new GreetServiceImpl());
        ConnectServiceRegistry registry = new ConnectServiceRegistry(services);
        ConnectFilter filter = new ConnectFilter(
            registry, "/connect", DataSize.ofBytes(10).toBytes(), true, List.of("*"));
        WebTestClient webTestClient = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
            .webFilter(filter)
            .build();

        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("a".repeat(100))
            .build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isEqualTo(413)
            .expectBody()
            .jsonPath("$.code").isEqualTo("resource_exhausted");
    }
}
