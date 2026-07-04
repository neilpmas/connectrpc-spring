package dev.neilmason.connect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a bean (or @Bean method) as an io.grpc.ServerInterceptor that should be applied to every
// dispatched call, mirroring spring-grpc-core's @GlobalServerInterceptor
// (org.springframework.grpc.server.GlobalServerInterceptor): a pure marker with no members. Bean
// discovery happens in ConnectServiceRegistry via ApplicationContext.getBeansWithAnnotation().
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GlobalConnectInterceptor {
}
