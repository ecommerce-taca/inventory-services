package com.taca.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ActorContextFilterTest {

    private static final String CONFIGURED_TOKEN = "super-secret-internal-token";

    private ActorContextFilter createFilter(String configuredToken) {
        SecurityProperties properties = new SecurityProperties();
        properties.setInternalToken(configuredToken);
        return new ActorContextFilter(properties);
    }

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void parsesGatewayActorHeadersAndValidInternalTokenAndCleansUpAfterChain() throws Exception {
        ActorContextFilter filter = createFilter(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seller/inventory");
        request.addHeader("X-User-ID", "user-123");
        request.addHeader("X-User-Roles", " SELLER , SELLER_STAFF ");
        request.addHeader("X-User-Permissions", "INVENTORY_READ, INVENTORY_ADJUST");
        request.addHeader("X-User-Shop-Scope", "shop-99");
        request.addHeader("X-MFA-Step-Up", "TRUE");
        request.addHeader("X-Internal-Service-Token", CONFIGURED_TOKEN);

        AtomicReference<ActorContext> captured = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured.set(ActorContext.current());
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ActorContext ctx = captured.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.userId()).isEqualTo("user-123");
        assertThat(ctx.roles()).containsExactlyInAnyOrder("SELLER", "SELLER_STAFF");
        assertThat(ctx.permissions()).containsExactlyInAnyOrder("INVENTORY_READ", "INVENTORY_ADJUST");
        assertThat(ctx.shopScope()).isEqualTo("shop-99");
        assertThat(ctx.mfaStepUp()).isTrue();
        assertThat(ctx.internalService()).isTrue();

        assertThat(ActorContext.current()).isNull();
    }

    @Test
    void rejectsWrongOrBlankInternalToken() throws Exception {
        ActorContextFilter filter = createFilter(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/inventory/availability");
        request.addHeader("X-Internal-Service-Token", "wrong-token");

        AtomicReference<ActorContext> captured = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured.set(ActorContext.current());
            }
        });

        assertThat(captured.get().internalService()).isFalse();
        assertThat(captured.get().isAuthenticated()).isFalse();
    }

    @Test
    void emptyConfiguredInternalTokenNeverMatchesEmptyHeader() throws Exception {
        ActorContextFilter filter = createFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/inventory/availability");
        request.addHeader("X-Internal-Service-Token", "");

        AtomicReference<ActorContext> captured = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured.set(ActorContext.current());
            }
        });

        assertThat(captured.get().internalService()).isFalse();
    }

    @Test
    void rejectsNullAndBlankInternalTokenWhenConfiguredAndTreatsFalseMfaStepUpAsFalse() throws Exception {
        ActorContextFilter filter = createFilter(CONFIGURED_TOKEN);

        MockHttpServletRequest nullTokenRequest = new MockHttpServletRequest("GET", "/internal/inventory/availability");
        nullTokenRequest.addHeader("X-MFA-Step-Up", "false");
        AtomicReference<ActorContext> nullTokenCaptured = new AtomicReference<>();
        filter.doFilter(nullTokenRequest, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                nullTokenCaptured.set(ActorContext.current());
            }
        });

        assertThat(nullTokenCaptured.get().internalService()).isFalse();
        assertThat(nullTokenCaptured.get().mfaStepUp()).isFalse();

        MockHttpServletRequest blankTokenRequest = new MockHttpServletRequest("GET", "/internal/inventory/availability");
        blankTokenRequest.addHeader("X-Internal-Service-Token", "   ");
        blankTokenRequest.addHeader("X-MFA-Step-Up", "false");
        AtomicReference<ActorContext> blankTokenCaptured = new AtomicReference<>();
        filter.doFilter(blankTokenRequest, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                blankTokenCaptured.set(ActorContext.current());
            }
        });

        assertThat(blankTokenCaptured.get().internalService()).isFalse();
        assertThat(blankTokenCaptured.get().mfaStepUp()).isFalse();
    }
}
