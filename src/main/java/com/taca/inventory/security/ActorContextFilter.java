package com.taca.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts Gateway actor headers and validates {@code X-Internal-Service-Token} in constant time.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ActorContextFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";
    public static final String HEADER_USER_SHOP_SCOPE = "X-User-Shop-Scope";
    public static final String HEADER_MFA_STEP_UP = "X-MFA-Step-Up";
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final SecurityProperties securityProperties;

    public ActorContextFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String userId = trimToNull(request.getHeader(HEADER_USER_ID));
        Set<String> roles = parseCsv(request.getHeader(HEADER_USER_ROLES));
        Set<String> permissions = parseCsv(request.getHeader(HEADER_USER_PERMISSIONS));
        String shopScope = trimToNull(request.getHeader(HEADER_USER_SHOP_SCOPE));
        boolean mfaStepUp = "true".equalsIgnoreCase(trimToNull(request.getHeader(HEADER_MFA_STEP_UP)));
        boolean internalService = isValidInternalToken(request.getHeader(HEADER_INTERNAL_TOKEN));

        ActorContext context = new ActorContext(userId, roles, permissions, shopScope, mfaStepUp, internalService);
        ActorContext.set(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ActorContext.clear();
        }
    }

    private boolean isValidInternalToken(String providedToken) {
        String configuredToken = securityProperties.getInternalToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            return false;
        }
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static Set<String> parseCsv(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String token : rawHeader.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return Set.copyOf(values);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
