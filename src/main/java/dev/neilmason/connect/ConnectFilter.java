package dev.neilmason.connect;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class ConnectFilter implements WebFilter {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    // Per the Connect protocol spec (https://connectrpc.com/docs/protocol#unary-request), the only
    // defined protocol version is "1", and Timeout-Milliseconds is a positive integer as an ASCII
    // string of at most 10 digits.
    private static final String SUPPORTED_PROTOCOL_VERSION = "1";
    private static final Pattern TIMEOUT_MS_PATTERN = Pattern.compile("[0-9]{1,10}");

    // CORS preflight handling per https://connectrpc.com/docs/cors/. This filter only serves the
    // unary POST variant of the Connect protocol (no gRPC-Web), so the allowed method list and
    // headers are scoped accordingly; gRPC-Web trailer headers like Grpc-Status don't apply here.
    private static final String CORS_ALLOWED_METHODS = "POST";
    private static final String CORS_ALLOWED_HEADERS =
        "Content-Type, Connect-Protocol-Version, Connect-Timeout-Ms, X-User-Agent";
    private static final String CORS_MAX_AGE = "7200"; // 2 hours: the modern Chrome cap.
    private static final String CORS_VARY = "Origin, Access-Control-Request-Method, Access-Control-Request-Headers";

    // Mapping per the Connect protocol spec: https://connectrpc.com/docs/protocol#error-codes
    private static final Map<Status.Code, ConnectError> STATUS_MAP = Map.ofEntries(
        Map.entry(Status.Code.CANCELLED, new ConnectError("canceled", HttpStatusCode.valueOf(499))),
        Map.entry(Status.Code.UNKNOWN, new ConnectError("unknown", HttpStatus.INTERNAL_SERVER_ERROR)),
        Map.entry(Status.Code.INVALID_ARGUMENT, new ConnectError("invalid_argument", HttpStatus.BAD_REQUEST)),
        Map.entry(Status.Code.DEADLINE_EXCEEDED, new ConnectError("deadline_exceeded", HttpStatus.GATEWAY_TIMEOUT)),
        Map.entry(Status.Code.NOT_FOUND, new ConnectError("not_found", HttpStatus.NOT_FOUND)),
        Map.entry(Status.Code.ALREADY_EXISTS, new ConnectError("already_exists", HttpStatus.CONFLICT)),
        Map.entry(Status.Code.PERMISSION_DENIED, new ConnectError("permission_denied", HttpStatus.FORBIDDEN)),
        Map.entry(Status.Code.RESOURCE_EXHAUSTED, new ConnectError("resource_exhausted", HttpStatus.TOO_MANY_REQUESTS)),
        Map.entry(Status.Code.FAILED_PRECONDITION, new ConnectError("failed_precondition", HttpStatus.BAD_REQUEST)),
        Map.entry(Status.Code.ABORTED, new ConnectError("aborted", HttpStatus.CONFLICT)),
        Map.entry(Status.Code.OUT_OF_RANGE, new ConnectError("out_of_range", HttpStatus.BAD_REQUEST)),
        Map.entry(Status.Code.UNIMPLEMENTED, new ConnectError("unimplemented", HttpStatus.NOT_IMPLEMENTED)),
        Map.entry(Status.Code.INTERNAL, new ConnectError("internal", HttpStatus.INTERNAL_SERVER_ERROR)),
        Map.entry(Status.Code.UNAVAILABLE, new ConnectError("unavailable", HttpStatus.SERVICE_UNAVAILABLE)),
        Map.entry(Status.Code.DATA_LOSS, new ConnectError("data_loss", HttpStatus.INTERNAL_SERVER_ERROR)),
        Map.entry(Status.Code.UNAUTHENTICATED, new ConnectError("unauthenticated", HttpStatus.UNAUTHORIZED))
    );

    private final ConnectServiceRegistry registry;
    private final String pathPrefix;
    private final long maxMessageSizeBytes;
    private final boolean corsEnabled;
    private final List<String> corsAllowedOrigins;

    public ConnectFilter(
            ConnectServiceRegistry registry,
            String pathPrefix,
            long maxMessageSizeBytes,
            boolean corsEnabled,
            List<String> corsAllowedOrigins) {
        this.registry = registry;
        this.pathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        this.maxMessageSizeBytes = maxMessageSizeBytes;
        this.corsEnabled = corsEnabled;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (corsEnabled && HttpMethod.OPTIONS.equals(request.getMethod()) && path.startsWith(pathPrefix)) {
            return handleCorsPreflight(exchange);
        }

        if (!HttpMethod.POST.equals(request.getMethod()) || !path.startsWith(pathPrefix)) {
            return chain.filter(exchange);
        }

        if (corsEnabled) {
            applyCorsAllowOrigin(exchange.getRequest(), exchange.getResponse());
        }

        String remaining = path.substring(pathPrefix.length());
        int lastSlash = remaining.lastIndexOf('/');
        if (lastSlash <= 0) {
            return writeConnectError(exchange.getResponse(), "unimplemented", "Invalid path", HttpStatus.NOT_FOUND);
        }

        String serviceName = remaining.substring(0, lastSlash);
        String methodName = remaining.substring(lastSlash + 1);

        ConnectServiceRegistry.MethodEntry entry = registry.lookup(serviceName, methodName);
        if (entry == null) {
            return writeConnectError(exchange.getResponse(), "unimplemented",
                "Method not found: " + serviceName + "/" + methodName, HttpStatus.NOT_FOUND);
        }

        // Servers may reject requests with an unsupported protocol version with HTTP 400:
        // https://connectrpc.com/docs/protocol#unary-request
        String protocolVersion = request.getHeaders().getFirst("Connect-Protocol-Version");
        if (protocolVersion != null && !SUPPORTED_PROTOCOL_VERSION.equals(protocolVersion)) {
            return writeConnectError(exchange.getResponse(), "invalid_argument",
                "Unsupported Connect-Protocol-Version: " + protocolVersion, HttpStatus.BAD_REQUEST);
        }

        // Unary content types are "application/" followed by the codec name ("proto" or "json").
        // "If the server doesn't support the specified Message-Codec, it must respond with an HTTP
        // status code of 415 Unsupported Media Type": https://connectrpc.com/docs/protocol#unary-request
        MediaType contentType = request.getHeaders().getContentType();
        Codec codec;
        if (contentType != null && APPLICATION_PROTO.isCompatibleWith(contentType)) {
            codec = Codec.PROTO;
        } else if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            codec = Codec.JSON;
        } else {
            return writeConnectError(exchange.getResponse(), "unknown",
                "Unsupported Content-Type: " + contentType, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        String timeoutHeader = request.getHeaders().getFirst("Connect-Timeout-Ms");
        Duration timeout = null;
        if (timeoutHeader != null) {
            if (!TIMEOUT_MS_PATTERN.matcher(timeoutHeader).matches()) {
                return writeConnectError(exchange.getResponse(), "invalid_argument",
                    "Invalid Connect-Timeout-Ms: " + timeoutHeader, HttpStatus.BAD_REQUEST);
            }
            timeout = Duration.ofMillis(Long.parseLong(timeoutHeader));
        }
        Duration invocationTimeout = timeout;

        return DataBufferUtils.join(request.getBody())
            .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
            .flatMap(dataBuffer -> {
                int byteCount = dataBuffer.readableByteCount();
                if (byteCount > maxMessageSizeBytes) {
                    DataBufferUtils.release(dataBuffer);
                    return writeConnectError(exchange.getResponse(), "resource_exhausted",
                        "Message size " + byteCount + " exceeds maximum " + maxMessageSizeBytes + " bytes",
                        HttpStatus.CONTENT_TOO_LARGE);
                }
                byte[] bodyBytes = new byte[byteCount];
                dataBuffer.read(bodyBytes);
                DataBufferUtils.release(dataBuffer);
                return invokeMethod(exchange, entry, bodyBytes, codec, invocationTimeout);
            });
    }

    // CORS preflight response per https://connectrpc.com/docs/cors/: no gRPC method is invoked,
    // we just describe what the actual request is allowed to do and return directly.
    private Mono<Void> handleCorsPreflight(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        applyCorsAllowOrigin(exchange.getRequest(), response);
        response.getHeaders().add("Access-Control-Allow-Methods", CORS_ALLOWED_METHODS);
        response.getHeaders().add("Access-Control-Allow-Headers", CORS_ALLOWED_HEADERS);
        response.getHeaders().add("Access-Control-Max-Age", CORS_MAX_AGE);
        response.getHeaders().add("Vary", CORS_VARY);
        response.setStatusCode(HttpStatus.NO_CONTENT);
        return response.setComplete();
    }

    // Resolves Access-Control-Allow-Origin against the configured allow-list and sets it on the
    // response, if any origin is allowed. If the list contains "*", any origin is allowed (echoing
    // the request's Origin header back, or "*" literally if there's no Origin header). Otherwise,
    // only an exact match against the configured list is allowed. An unmatched origin simply gets no
    // CORS header at all (not an error) -- the browser then blocks the response client-side.
    private void applyCorsAllowOrigin(ServerHttpRequest request, ServerHttpResponse response) {
        String origin = request.getHeaders().getFirst("Origin");
        if (corsAllowedOrigins.contains("*")) {
            response.getHeaders().add("Access-Control-Allow-Origin", origin != null ? origin : "*");
        } else if (origin != null && corsAllowedOrigins.contains(origin)) {
            response.getHeaders().add("Access-Control-Allow-Origin", origin);
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> invokeMethod(
            ServerWebExchange exchange,
            ConnectServiceRegistry.MethodEntry entry,
            byte[] bodyBytes,
            Codec codec,
            @Nullable Duration timeout) {

        MethodDescriptor<Object, Object> descriptor =
            (MethodDescriptor<Object, Object>) entry.descriptor();

        Object requestMessage;
        try {
            requestMessage = switch (codec) {
                case PROTO -> descriptor.parseRequest(new ByteArrayInputStream(bodyBytes));
                case JSON -> parseJsonRequest(descriptor, bodyBytes);
            };
        } catch (Exception e) {
            return writeConnectError(exchange.getResponse(), "invalid_argument",
                "Failed to parse request: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        return ReactiveSecurityContextHolder.getContext()
            .defaultIfEmpty(new SecurityContextImpl())
            .flatMap(securityContext -> {
                CompletableFuture<Object> future = new CompletableFuture<>();

                StreamObserver<Object> responseObserver = new StreamObserver<>() {
                    // Only unset if a misbehaving service calls onCompleted() without onNext() first;
                    // well-behaved unary gRPC methods always populate this before completing.
                    private @Nullable Object value;

                    @Override
                    public void onNext(Object resp) {
                        this.value = resp;
                    }

                    @Override
                    public void onError(Throwable t) {
                        future.completeExceptionally(t);
                    }

                    @Override
                    @SuppressWarnings("DataFlowIssue") // CompletableFuture.complete(null) is well-defined;
                    // Mono.fromFuture treats a null-valued completion as an empty Mono, not an error.
                    public void onCompleted() {
                        future.complete(value);
                    }
                };

                Mono<Object> invocation = Mono.fromCallable(() -> {
                        SecurityContextHolder.setContext(securityContext);
                        try {
                            entry.javaMethod().invoke(entry.service(), requestMessage, responseObserver);
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            future.completeExceptionally(cause);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                        return future;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(Mono::fromFuture);

                if (timeout != null) {
                    invocation = invocation.timeout(timeout);
                }

                return invocation
                    .flatMap(response -> switch (codec) {
                        case PROTO -> writeProtoResponse(exchange.getResponse(), descriptor, response);
                        case JSON -> writeJsonResponse(exchange.getResponse(), response);
                    })
                    .onErrorResume(throwable -> handleError(exchange.getResponse(), throwable));
            });
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    private Mono<Void> writeProtoResponse(
            ServerHttpResponse response,
            MethodDescriptor<Object, Object> descriptor,
            Object message) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(APPLICATION_PROTO);
        // This runs on Schedulers.boundedElastic() (see the subscribeOn() upstream in invokeMethod()),
        // which is designed for exactly this kind of bounded blocking work. streamResponse() for
        // protobuf messages also just backs onto an in-memory ByteArrayInputStream, not real I/O, so
        // readAllBytes() never actually blocks on anything. IntelliJ's inspection doesn't trace
        // scheduler context across the flatMap chain, hence the false positive here.
        try (var stream = descriptor.streamResponse(message)) {
            byte[] bytes = stream.readAllBytes();
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return writeConnectError(response, "internal",
                "Failed to serialize response", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Protobuf-based marshallers (the only kind this library supports) implement PrototypeMarshaller,
    // exposing the default instance of the concrete Message type for JSON parsing.
    @SuppressWarnings("DataFlowIssue") // PrototypeMarshaller contract guarantees a non-null prototype
    private Object parseJsonRequest(MethodDescriptor<Object, Object> descriptor, byte[] bodyBytes)
            throws Exception {
        MethodDescriptor.PrototypeMarshaller<Object> marshaller =
            (MethodDescriptor.PrototypeMarshaller<Object>) descriptor.getRequestMarshaller();
        Message prototype = (Message) marshaller.getMessagePrototype();
        Message.Builder builder = prototype.newBuilderForType();
        JsonFormat.parser().merge(new String(bodyBytes, StandardCharsets.UTF_8), builder);
        return builder.build();
    }

    private Mono<Void> writeJsonResponse(ServerHttpResponse response, Object message) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            String json = JsonFormat.printer().print((Message) message);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return writeConnectError(response, "internal",
                "Failed to serialize response", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Void> handleError(ServerHttpResponse response, Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            // "Deadline expired before RPC could complete": deadline_exceeded maps to HTTP 504 per
            // https://connectrpc.com/docs/protocol#error-codes
            return writeConnectError(response, "deadline_exceeded",
                "The operation timed out", HttpStatus.GATEWAY_TIMEOUT);
        }
        if (throwable instanceof StatusRuntimeException sre) {
            Status.Code code = sre.getStatus().getCode();
            ConnectError error = STATUS_MAP.getOrDefault(code,
                new ConnectError("unknown", HttpStatus.INTERNAL_SERVER_ERROR));
            String message = sre.getStatus().getDescription();
            if (message == null) {
                message = sre.getMessage();
            }
            // StatusProto.fromThrowable() decodes the binary grpc-status-details-bin trailer (if the
            // service attached one via StatusProto.toStatusRuntimeException()) back into a
            // com.google.rpc.Status; it returns null when there are no such trailers.
            com.google.rpc.Status rpcStatus = StatusProto.fromThrowable(sre);
            List<Any> details = rpcStatus != null ? rpcStatus.getDetailsList() : List.of();
            return writeConnectError(response, error.code(), message, error.httpStatus(), details);
        }
        return writeConnectError(response, "internal",
            throwable.getMessage() != null ? throwable.getMessage() : "Internal error",
            HttpStatus.INTERNAL_SERVER_ERROR, List.of());
    }

    private Mono<Void> writeConnectError(
            ServerHttpResponse response, String code, @Nullable String message, HttpStatusCode status) {
        return writeConnectError(response, code, message, status, List.of());
    }

    // Structured error details per https://connectrpc.com/docs/protocol#error-codes: each detail's
    // "type" is the fully-qualified Protobuf message name (the Any type URL with the
    // "type.googleapis.com/" prefix stripped), and "value" is the unpadded, standard base64 encoding
    // of the message's serialized bytes.
    private Mono<Void> writeConnectError(
            ServerHttpResponse response, String code, @Nullable String message, HttpStatusCode status,
            List<Any> details) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        StringBuilder json = new StringBuilder("{\"code\":\"").append(escapeJson(code))
            .append("\",\"message\":\"").append(escapeJson(message)).append("\"");
        if (!details.isEmpty()) {
            json.append(",\"details\":[");
            for (int i = 0; i < details.size(); i++) {
                if (i > 0) json.append(",");
                Any any = details.get(i);
                String typeUrl = any.getTypeUrl();
                String type = typeUrl.substring(typeUrl.lastIndexOf('/') + 1);
                String value = Base64.getEncoder().withoutPadding().encodeToString(any.getValue().toByteArray());
                json.append("{\"type\":\"").append(escapeJson(type))
                    .append("\",\"value\":\"").append(escapeJson(value)).append("\"}");
            }
            json.append("]");
        }
        json.append("}");
        DataBuffer buffer = response.bufferFactory().wrap(json.toString().getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    private String escapeJson(@Nullable String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum Codec { PROTO, JSON }

    private record ConnectError(String code, HttpStatusCode httpStatus) {}
}
