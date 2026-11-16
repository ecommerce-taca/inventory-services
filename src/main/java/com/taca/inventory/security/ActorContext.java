package com.taca.inventory.security;

import java.util.Set;

/**
 * Request-scoped actor and internal-service authentication context parsed from Gateway or internal headers.
 *
 * @param userId authenticated user identifier
 * @param roles assigned actor roles
 * @param permissions assigned fine-grained permissions
 * @param shopScope single shop identifier scoped to seller/staff actors
 * @param mfaStepUp whether MFA step-up verification succeeded
 * @param internalService whether internal service token authentication succeeded
 */
public record ActorContext(
        String userId,
        Set<String> roles,
        Set<String> permissions,
        String shopScope,
        boolean mfaStepUp,
        boolean internalService
) {

    private static final ThreadLocal<ActorContext> HOLDER = new ThreadLocal<>();

    public ActorContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * Returns whether the current context carries an authenticated user or internal service identity.
     *
     * @return {@code true} if authenticated
     */
    public boolean isAuthenticated() {
        return internalService || (userId != null && !userId.isBlank());
    }

    /**
     * Checks whether the actor has the specified role.
     *
     * @param role role name
     * @return {@code true} if present
     */
    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    /**
     * Checks whether the actor has the specified permission.
     *
     * @param permission permission name
     * @return {@code true} if present
     */
    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    /**
     * Returns the current thread's {@link ActorContext}, or {@code null} if unset.
     *
     * @return current actor context or {@code null}
     */
    public static ActorContext current() {
        return HOLDER.get();
    }

    /**
     * Binds the given {@link ActorContext} to the current thread.
     *
     * @param context actor context to bind
     */
    public static void set(ActorContext context) {
        if (context == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(context);
        }
    }

    /**
     * Clears the current thread's {@link ActorContext}.
     */
    public static void clear() {
        HOLDER.remove();
    }
}
