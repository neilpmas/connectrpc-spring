package dev.neilmason.connect;

import dev.neilmason.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.connect.testapp.CountingInterceptor;
import dev.neilmason.connect.testapp.GreetServiceImpl;
import dev.neilmason.connect.testapp.OrderRecordingInterceptor;
import dev.neilmason.connect.testapp.RejectingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the @GlobalConnectInterceptor discovery/ordering/wrapping mechanism in
// ConnectServiceRegistry actually runs interceptors on every dispatched call. This repo's tests
// don't use Spring Boot (see ConnectFilterEndToEndTest), so a plain AnnotationConfigApplicationContext
// stands in for the real ApplicationContext that getBeansWithAnnotation() needs.
class GlobalConnectInterceptorEndToEndTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    @Test
    void globalInterceptor_shouldRunBeforeServiceMethod() {
        try (var context = new AnnotationConfigApplicationContext(RejectingInterceptorConfig.class)) {
            ConnectServiceRegistry registry = new ConnectServiceRegistry(
                List.of(new GreetServiceImpl()), context);
            WebTestClient webTestClient = webTestClient(registry);

            webTestClient
                .post()
                .uri("/connect/greet.v1.GreetService/SayHello")
                .contentType(APPLICATION_PROTO)
                .bodyValue(SayHelloRequest.newBuilder().setName("World").build().toByteArray())
                .exchange()
                .expectStatus().isEqualTo(403)
                .expectBody()
                .jsonPath("$.code").isEqualTo("permission_denied")
                .jsonPath("$.message").isEqualTo("rejected by test interceptor");
        }
    }

    @Test
    void globalInterceptor_shouldRunOnEveryCall() {
        try (var context = new AnnotationConfigApplicationContext(CountingInterceptorConfig.class)) {
            CountingInterceptor interceptor = context.getBean(CountingInterceptor.class);
            ConnectServiceRegistry registry = new ConnectServiceRegistry(
                List.of(new GreetServiceImpl()), context);
            WebTestClient webTestClient = webTestClient(registry);

            for (int i = 0; i < 3; i++) {
                webTestClient
                    .post()
                    .uri("/connect/greet.v1.GreetService/SayHello")
                    .contentType(APPLICATION_PROTO)
                    .bodyValue(SayHelloRequest.newBuilder().setName("World").build().toByteArray())
                    .exchange()
                    .expectStatus().isOk();
            }

            assertThat(interceptor.callCount()).isEqualTo(3);
        }
    }

    @Test
    @SuppressWarnings("unchecked") // getBean(name, Class) can't express List<String> due to erasure;
    // the bean's actual runtime type is guaranteed by the @Bean method below.
    void globalInterceptors_shouldRunInOrderAnnotationOrder() {
        try (var context = new AnnotationConfigApplicationContext(OrderedInterceptorsConfig.class)) {
            List<String> invocationOrder = context.getBean("invocationOrder", List.class);
            ConnectServiceRegistry registry = new ConnectServiceRegistry(
                List.of(new GreetServiceImpl()), context);
            WebTestClient webTestClient = webTestClient(registry);

            webTestClient
                .post()
                .uri("/connect/greet.v1.GreetService/SayHello")
                .contentType(APPLICATION_PROTO)
                .bodyValue(SayHelloRequest.newBuilder().setName("World").build().toByteArray())
                .exchange()
                .expectStatus().isOk();

            assertThat(invocationOrder).containsExactly("first", "second");
        }
    }

    private static WebTestClient webTestClient(ConnectServiceRegistry registry) {
        ConnectFilter filter = new ConnectFilter(
            registry, "/connect", DataSize.ofMegabytes(4).toBytes(), false, List.of());
        return WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
            .webFilter(filter)
            .build();
    }

    @Configuration
    static class RejectingInterceptorConfig {
        @Bean
        @GlobalConnectInterceptor
        RejectingInterceptor rejectingInterceptor() {
            return new RejectingInterceptor();
        }
    }

    @Configuration
    static class CountingInterceptorConfig {
        @Bean
        @GlobalConnectInterceptor
        CountingInterceptor countingInterceptor() {
            return new CountingInterceptor();
        }
    }

    @Configuration
    static class OrderedInterceptorsConfig {
        @Bean
        List<String> invocationOrder() {
            return new ArrayList<>();
        }

        @Bean
        @GlobalConnectInterceptor
        OrderRecordingInterceptor secondInterceptor(List<String> invocationOrder) {
            return new OrderRecordingInterceptor("second", invocationOrder, 2);
        }

        @Bean
        @GlobalConnectInterceptor
        OrderRecordingInterceptor firstInterceptor(List<String> invocationOrder) {
            return new OrderRecordingInterceptor("first", invocationOrder, 1);
        }
    }
}
