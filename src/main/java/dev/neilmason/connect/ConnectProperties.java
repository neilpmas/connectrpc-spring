package dev.neilmason.connect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("connect")
public class ConnectProperties {

    private boolean enabled = true;
    private String pathPrefix = "/connect";
    private DataSize maxMessageSize = DataSize.ofMegabytes(4);

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
}
