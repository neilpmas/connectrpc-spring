# connectrpc-spring

[![CI](https://github.com/neilpmas/connectrpc-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/neilpmas/connectrpc-spring/actions/workflows/ci.yml)

Serves the [Connect protocol](https://connectrpc.com) from your existing gRPC service definitions, on plain Spring WebFlux — no code changes to the service itself. Connect is wire-compatible with ordinary HTTP, so a browser `fetch()` or a plain `curl` can call a gRPC service directly, without a gRPC-Web proxy or a hand-written REST controller.

This is the framework-agnostic core, with no Spring Boot dependency, mirroring [`spring-grpc`](https://github.com/spring-projects/spring-grpc)'s real split between its core and Boot integration. Most users building a Spring Boot application want the autoconfigured starter instead: [`connectrpc-spring-boot`](https://github.com/neilpmas/connectrpc-spring-boot).

## Install

```xml
<dependency>
    <groupId>dev.neilmason</groupId>
    <artifactId>connectrpc-spring-core</artifactId>
    <version>0.1.2</version>
</dependency>
```

Available on [Maven Central](https://central.sonatype.com/artifact/dev.neilmason/connectrpc-spring-core).

## Getting Started

The [`gs-connect-rpc`](https://github.com/neilpmas/gs-connect-rpc) guide walks through adding the Spring Boot starter to an existing gRPC service and calling it over HTTP in about 15 minutes.

## License

[Apache License, Version 2.0](LICENSE)
