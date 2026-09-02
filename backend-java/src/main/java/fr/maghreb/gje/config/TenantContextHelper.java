package fr.maghreb.gje.config;

import jakarta.persistence.EntityManager;

import java.util.UUID;

public class TenantContextHelper {

    /**
     * Sets the tenant context for the current transaction.
     * This avoids code duplication across all services that need tenant isolation.
     */
    public static void setTenant(UUID workspaceId, EntityManager entityManager) {
        TenantContext.setTenantId(workspaceId.toString());
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '" + workspaceId.toString() + "'")
            .executeUpdate();
    }
}
