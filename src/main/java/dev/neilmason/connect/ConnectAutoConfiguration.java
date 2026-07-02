package dev.neilmason.connect;

import io.grpc.BindableService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ConnectProperties.class)
@ConditionalOnClass(BindableService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "connect", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConnectAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConnectServiceRegistry connectServiceRegistry(List<BindableService> services) {
        return new ConnectServiceRegistry(services);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectFilter connectFilter(ConnectServiceRegistry registry, ConnectProperties properties) {
        return new ConnectFilter(registry, properties);
    }
}
