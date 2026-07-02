package dev.neilmason.connect;

import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "connect.max-message-size=10B"
)
class ConnectFilterMaxMessageSizeTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void oversizedRequest_shouldReturn413WithResourceExhaustedCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

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
