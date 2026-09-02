package ma.jurika.billing.application.workspace;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 — port : remonte les transitions de status workspace vers
 * auth-service (qui possede la table {@code workspaces}).
 *
 * <p>Sprint 12 T7 fournira une impl Feign (POST /internal/workspaces/{id}/status).
 * En attendant, un {@code LoggingWorkspaceStatusUpdater} no-op est wire par
 * defaut pour ne pas bloquer le build.
 */
public interface WorkspaceStatusUpdater {

    /**
     * Workspace converti trial → active suite a un checkout reussi.
     * RG-BL04.
     */
    void activate(UUID workspaceId, String planCode, Instant accessUntil);

    /**
     * Workspace annule. Conserve l'acces jusqu'a {@code accessUntil}
     * (= current_period_end Stripe). RG-BL07.
     */
    void markCancelled(UUID workspaceId, Instant accessUntil);

    /**
     * Workspace en past_due (paiement refuse — RG-BL06). L'acces reste
     * actif quelques jours (Stripe retry automatique).
     */
    void markPastDue(UUID workspaceId);

    /**
     * BUG 14 (2026-06-07) — declenche la re-emission des identifiants
     * (workspace code + mot de passe temporaire) apres validation d'un
     * paiement hors-ligne ou conversion CARD. Best-effort : log + return
     * si auth-service injoignable. {@code reason} ex.
     * {@code PAYMENT_VALIDATED_CARD}, {@code PAYMENT_VALIDATED_BANK_TRANSFER}.
     */
    default void issueCredentials(UUID workspaceId, String reason) {
        // Default no-op pour les impls qui n'ont pas le canal (tests, no-op).
    }
}
