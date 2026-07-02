package dev.neilmason.connect;

import com.google.protobuf.util.JsonFormat;
import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.test.greet.v1.SayHelloResponse;
import dev.neilmason.connect.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
class ConnectFilterEndToEndTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void sayHello_shouldReturnProtobufResponse() throws Exception {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("World")
            .build();

        byte[] responseBytes = webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_PROTO)
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBytes).isNotNull();
        SayHelloResponse response = SayHelloResponse.parseFrom(responseBytes);
        assertThat(response.getGreeting()).isEqualTo("Hello, World!");
    }

    @Test
    void sayHello_withJsonContentType_shouldReturnJsonResponse() throws Exception {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        String responseJson = webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"World\"}")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(responseJson).isNotNull();
        SayHelloResponse.Builder builder = SayHelloResponse.newBuilder();
        JsonFormat.parser().merge(responseJson, builder);
        assertThat(builder.getGreeting()).isEqualTo("Hello, World!");
    }

    @Test
    void malformedJsonBody_shouldReturn400WithInvalidArgumentCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        //language=none
        String malformedJson = "{not valid json";

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(malformedJson)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_argument");
    }

    @Test
    void unsupportedContentType_shouldReturn415() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("hello")
            .exchange()
            .expectStatus().isEqualTo(415);
    }

    @Test
    void missingContentType_shouldReturn415() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .exchange()
            .expectStatus().isEqualTo(415);
    }

    @Test
    void unknownMethod_shouldReturn404WithUnimplementedCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/NonExistent")
            .contentType(APPLICATION_PROTO)
            .bodyValue(new byte[0])
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.code").isEqualTo("unimplemented");
    }

    @Test
    void resourceExhausted_shouldReturn429WithResourceExhaustedCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("trigger-resource-exhausted")
            .build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.code").isEqualTo("resource_exhausted")
            .jsonPath("$.message").isEqualTo("Quota exceeded");
    }

    @Test
    void validProtocolVersionHeader_shouldStillSucceed() throws Exception {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("World")
            .build();

        byte[] responseBytes = webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .header("Connect-Protocol-Version", "1")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBytes).isNotNull();
        assertThat(SayHelloResponse.parseFrom(responseBytes).getGreeting()).isEqualTo("Hello, World!");
    }

    @Test
    void unsupportedProtocolVersion_shouldReturn400WithInvalidArgumentCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .header("Connect-Protocol-Version", "2")
            .contentType(APPLICATION_PROTO)
            .bodyValue(SayHelloRequest.newBuilder().setName("World").build().toByteArray())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_argument");
    }

    @Test
    void malformedTimeoutHeader_shouldReturn400WithInvalidArgumentCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .header("Connect-Timeout-Ms", "not-a-number")
            .contentType(APPLICATION_PROTO)
            .bodyValue(SayHelloRequest.newBuilder().setName("World").build().toByteArray())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_argument");
    }

    @Test
    void timeoutExceeded_shouldReturn504WithDeadlineExceededCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .header("Connect-Timeout-Ms", "50")
            .contentType(APPLICATION_PROTO)
            .bodyValue(SayHelloRequest.newBuilder().setName("trigger-slow").build().toByteArray())
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectBody()
            .jsonPath("$.code").isEqualTo("deadline_exceeded");
    }

    @Test
    void cancelled_shouldReturn499WithCanceledCode() {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("trigger-cancelled")
            .build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isEqualTo(499)
            .expectBody()
            .jsonPath("$.code").isEqualTo("canceled")
            .jsonPath("$.message").isEqualTo("Request cancelled");
    }
}
