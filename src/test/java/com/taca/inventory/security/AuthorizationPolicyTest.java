package com.taca.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taca.inventory.common.error.ErrorCode;
import com.taca.inventory.common.error.InventoryException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthorizationPolicyTest {

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void requireAuthenticatedThrows401WhenMissingOrBlankUser() {
        ActorContext.clear();
        assertThatThrownBy(policy::requireAuthenticated)
                .isInstanceOf(InventoryException.class)
                .satisfies(ex -> {
                    InventoryException ie = (InventoryException) ex;
                    assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_UNAUTHENTICATED);
                    assertThat(ie.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });

        ActorContext.set(new ActorContext("  ", Set.of("SELLER"), Set.of(), "shop-1", false, false));
        assertThatThrownBy(policy::requireAuthenticated)
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_UNAUTHENTICATED);
    }

    @Test
    void requireInternalServiceAllowsValidTokenAndThrows401Otherwise() {
        ActorContext.set(new ActorContext(null, Set.of(), Set.of(), null, false, true));
        policy.requireInternalService();

        ActorContext.set(new ActorContext("user-1", Set.of("SELLER"), Set.of(), "shop-1", false, false));
        assertThatThrownBy(policy::requireInternalService)
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_UNAUTHENTICATED);
    }

    @Test
    void requireSellerShopScopeAllowsSellerAndSellerStaffForMatchingShop() {
        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of(), "shop-1", false, false));
        assertThat(policy.requireSellerShopScope("shop-1").shopScope()).isEqualTo("shop-1");

        ActorContext.set(new ActorContext("staff-1", Set.of("SELLER_STAFF"), Set.of(), "shop-1", false, false));
        assertThat(policy.requireSellerShopScope("shop-1").userId()).isEqualTo("staff-1");
        assertThat(policy.requireSellerShopScope(null).shopScope()).isEqualTo("shop-1");
    }

    @Test
    void requireSellerShopScopeThrows403OnWrongRoleMissingScopeOrShopMismatch() {
        ActorContext.set(new ActorContext("buyer-1", Set.of("BUYER"), Set.of(), "shop-1", false, false));
        assertThatThrownBy(() -> policy.requireSellerShopScope("shop-1"))
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);

        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of(), null, false, false));
        assertThatThrownBy(() -> policy.requireSellerShopScope("shop-1"))
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);

        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of(), "shop-1", false, false));
        assertThatThrownBy(() -> policy.requireSellerShopScope("shop-2"))
                .isInstanceOf(InventoryException.class)
                .satisfies(ex -> {
                    InventoryException ie = (InventoryException) ex;
                    assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    assertThat(ie.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void requireAdminStepUpAllowsSuperAdminAndAdminWithStepUpAndPermission() {
        ActorContext.set(new ActorContext("admin-1", Set.of("ADMIN"), Set.of("INVENTORY_RECONCILIATION_READ"), null, true, false));
        assertThat(policy.requireAdminStepUp("INVENTORY_RECONCILIATION_READ").userId()).isEqualTo("admin-1");

        ActorContext.set(new ActorContext("super-1", Set.of("SUPER_ADMIN"), Set.of("INVENTORY_RECONCILIATION_READ"), null, true, false));
        assertThat(policy.requireAdminStepUp("INVENTORY_RECONCILIATION_READ").userId()).isEqualTo("super-1");
    }

    @Test
    void requireAdminStepUpThrows403WhenMissingRoleStepUpOrPermission() {
        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of("INVENTORY_RECONCILIATION_READ"), "shop-1", true, false));
        assertThatThrownBy(() -> policy.requireAdminStepUp("INVENTORY_RECONCILIATION_READ"))
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);

        ActorContext.set(new ActorContext("admin-1", Set.of("ADMIN"), Set.of("INVENTORY_RECONCILIATION_READ"), null, false, false));
        assertThatThrownBy(() -> policy.requireAdminStepUp("INVENTORY_RECONCILIATION_READ"))
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);

        ActorContext.set(new ActorContext("admin-1", Set.of("ADMIN"), Set.of("OTHER_PERMISSION"), null, true, false));
        assertThatThrownBy(() -> policy.requireAdminStepUp("INVENTORY_RECONCILIATION_READ"))
                .isInstanceOf(InventoryException.class)
                .extracting(ex -> ((InventoryException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
    }

    @Test
    void requireSellerShopScopeNoArgAllowsValidSellerAndFailsClosedOtherwise() {
        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of(), "shop-1", false, false));
        assertThat(policy.requireSellerShopScope().shopScope()).isEqualTo("shop-1");

        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of(), "  ", false, false));
        assertThatThrownBy(policy::requireSellerShopScope)
                .isInstanceOf(InventoryException.class)
                .satisfies(ex -> {
                    InventoryException ie = (InventoryException) ex;
                    assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    assertThat(ie.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void requireSellerShopScopeFailsClosedOnBlankExpectedShopId() {
        ActorContext.set(new ActorContext("seller-1", Set.of("SELLER"), Set.of(), "shop-1", false, false));

        for (String blankShopId : new String[] {"", "  "}) {
            assertThatThrownBy(() -> policy.requireSellerShopScope(blankShopId))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                        assertThat(ie.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }
    }

    @Test
    void requireAdminStepUpFailsClosedOnNullOrBlankRequiredPermission() {
        ActorContext.set(new ActorContext("admin-1", Set.of("ADMIN"), Set.of("INVENTORY_RECONCILIATION_READ"), null, true, false));

        for (String invalidPermission : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> policy.requireAdminStepUp(invalidPermission))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                        assertThat(ie.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }
    }
}
