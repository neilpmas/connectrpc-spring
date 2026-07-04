package dev.neilmason.connect;

import com.google.protobuf.StringValue;
import com.google.protobuf.util.JsonFormat;
import com.jayway.jsonpath.JsonPath;
import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.test.greet.v1.SayHelloResponse;
import dev.neilmason.connect.testapp.GreetServiceImpl;
import io.grpc.BindableService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectFilterEndToEndTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    private final WebTestClient webTestClient = webTestClient();

    private static WebTestClient webTestClient() {
        List<BindableService> services = List.of(new GreetServiceImpl());
        ConnectServiceRegistry registry = new ConnectServiceRegistry(services);
        ConnectFilter filter = new ConnectFilter(
            registry, "/connect", DataSize.ofMegabytes(4).toBytes(), true, List.of("*"));
        return WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
            .webFilter(filter)
            .build();
    }

    @Test
    void sayHello_shouldReturnProtobufResponse() throws Exception {
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
        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .exchange()
            .expectStatus().isEqualTo(415);
    }

    @Test
    void unknownMethod_shouldReturn404WithUnimplementedCode() {
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

    @Test
    void corsPreflight_shouldReturn204WithAllowHeaders() {
        webTestClient
            .options()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .expectStatus().isNoContent()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://example.com")
            .expectHeader().valueEquals("Access-Control-Allow-Methods", "POST")
            .expectHeader().valueEquals("Access-Control-Allow-Headers",
                "Content-Type, Connect-Protocol-Version, Connect-Timeout-Ms, X-User-Agent")
            .expectHeader().valueEquals("Access-Control-Max-Age", "7200")
            .expectHeader().valueEquals("Vary", "Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
    }

    @Test
    void postRequest_shouldIncludeAccessControlAllowOrigin() {
        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("World")
            .build();

        webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .header("Origin", "https://example.com")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://example.com");
    }

    @Test
    void triggerDetails_shouldReturnUnavailableWithStructuredDetails() throws Exception {
        SayHelloRequest request = SayHelloRequest.newBuilder()
            .setName("trigger-details")
            .build();

        byte[] responseBytes = webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.code").isEqualTo("unavailable")
            .jsonPath("$.message").isEqualTo("overloaded: back off and retry")
            .jsonPath("$.details[0].type").isEqualTo("google.protobuf.StringValue")
            .returnResult()
            .getResponseBody();

        assertThat(responseBytes).isNotNull();
        String json = new String(responseBytes);
        String value = JsonPath.read(json, "$.details[0].value");
        byte[] decoded = Base64.getDecoder().decode(value);
        StringValue detail = StringValue.parseFrom(decoded);
        assertThat(detail.getValue()).isEqualTo("retry-after-1s");
    }
}
