package com.taca.inventory.security;

import com.taca.inventory.common.error.ErrorCode;
import com.taca.inventory.common.error.InventoryException;
import org.springframework.stereotype.Component;

/**
 * Enforces authentication, internal service, seller shop-scope, and admin MFA step-up policies.
 */
@Component
public class AuthorizationPolicy {

    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_SELLER_STAFF = "SELLER_STAFF";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Requires an authenticated user identity in the current {@link ActorContext}.
     *
     * @return current authenticated {@link ActorContext}
     */
    public ActorContext requireAuthenticated() {
        ActorContext ctx = ActorContext.current();
        if (ctx == null || ctx.userId() == null || ctx.userId().isBlank()) {
            throw new InventoryException(ErrorCode.INVENTORY_UNAUTHENTICATED, "Authentication required");
        }
        return ctx;
    }

    /**
     * Requires valid internal service-to-service token authentication.
     *
     * @return current {@link ActorContext}
     */
    public ActorContext requireInternalService() {
        ActorContext ctx = ActorContext.current();
        if (ctx == null || !ctx.internalService()) {
            throw new InventoryException(
                    ErrorCode.INVENTORY_UNAUTHENTICATED,
                    "Internal service authentication required"
            );
        }
        return ctx;
    }

    /**
     * Requires seller or seller-staff role with a non-blank shop scope (for shop-scoped list endpoints).
     *
     * @return current {@link ActorContext}
     */
    public ActorContext requireSellerShopScope() {
        return requireSellerShopScope(null);
    }

    /**
     * Requires seller or seller-staff role with a non-blank shop scope matching {@code expectedShopId} if supplied.
     *
     * @param expectedShopId target resource shop identifier, or {@code null} when filtering by actor scope
     * @return current {@link ActorContext}
     */
    public ActorContext requireSellerShopScope(String expectedShopId) {
        ActorContext ctx = requireAuthenticated();
        if (!ctx.hasRole(ROLE_SELLER) && !ctx.hasRole(ROLE_SELLER_STAFF)) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Seller role required");
        }
        if (ctx.shopScope() == null || ctx.shopScope().isBlank()) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Seller shop scope is missing");
        }
        if (expectedShopId != null && !expectedShopId.equals(ctx.shopScope())) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Access denied for target shop");
        }
        return ctx;
    }

    /**
     * Requires {@code ADMIN} or {@code SUPER_ADMIN} role, active MFA step-up, and the specified permission.
     *
     * @param requiredPermission required permission string
     * @return current {@link ActorContext}
     */
    public ActorContext requireAdminStepUp(String requiredPermission) {
        ActorContext ctx = requireAuthenticated();
        if (!ctx.hasRole(ROLE_ADMIN) && !ctx.hasRole(ROLE_SUPER_ADMIN)) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Admin role required");
        }
        if (!ctx.mfaStepUp()) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "MFA step-up required");
        }
        if (requiredPermission == null || requiredPermission.isBlank() || !ctx.hasPermission(requiredPermission)) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Missing required admin permission");
        }
        return ctx;
    }
}
