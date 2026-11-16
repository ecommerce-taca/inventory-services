package com.taca.inventory.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security configuration properties for internal service-to-service authentication.
 */
@ConfigurationProperties(prefix = "inventory.security")
public class SecurityProperties {

    private String internalToken = "";

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken == null ? "" : internalToken;
    }
}
