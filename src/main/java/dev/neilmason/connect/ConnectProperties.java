package dev.neilmason.connect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

@ConfigurationProperties("connect")
public class ConnectProperties {

    private boolean enabled = true;
    private String pathPrefix = "/connect";
    private DataSize maxMessageSize = DataSize.ofMegabytes(4);
    private boolean corsEnabled = true;
    private List<String> corsAllowedOrigins = List.of("*");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public DataSize getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(DataSize maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public void setCorsEnabled(boolean corsEnabled) {
        this.corsEnabled = corsEnabled;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }
}
