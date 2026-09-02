package ma.jurika.common.trial;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 — client Feign vers auth-service pour le statut workspace.
 *
 * <p>Utilise par :
 *  - {@code RemoteTrialAccessChecker} (jurika-common) pour 5 microservices
 *    qui wirent le {@code TrialSoftLockFilter} (T7)
 *  - {@code AuthWorkspaceStatusUpdater} (billing-service) pour notifier les
 *    transitions de status (T7)
 *
 * <p>L'endpoint cote auth-service est {@code /internal/workspaces/{id}/status}
 * — securise par convention X-Internal-Token (ou network-only via service mesh
 * en prod). En dev local, le filtrage est passe au gateway.
 */
@FeignClient(name = "auth-service", url = "${jurika.auth.internal-url:http://auth-service}")
public interface WorkspaceStatusFeignClient {

    @GetMapping("/internal/workspaces/{workspaceId}/status")
    WorkspaceStatusResponse getStatus(@PathVariable("workspaceId") UUID workspaceId);

    @PutMapping("/internal/workspaces/{workspaceId}/status")
    void updateStatus(@PathVariable("workspaceId") UUID workspaceId,
                       @RequestParam("transition") String transition,
                       @RequestParam(value = "planCode", required = false) String planCode,
                       @RequestParam(value = "accessUntil", required = false) String accessUntilIso);

    /**
     * BUG 8 (2026-06-07) — snapshot consommation reelle d'un workspace
     * (employes / dossiers / stockage) servi depuis auth-service via
     * {@link ma.jurika.common.billing.PlanLimitsService}. Appele par
     * {@code ChangePlanUseCase} (billing-service) pour interdire un
     * downgrade qui ferait passer le workspace au-dessus du quota cible.
     */
    @GetMapping("/internal/workspaces/{workspaceId}/usage")
    UsageSnapshotDto getUsage(@PathVariable("workspaceId") UUID workspaceId);

    /**
     * BUG 14 (2026-06-07) — re-emission des identifiants (workspace code +
     * mot de passe temporaire) apres validation d'un paiement hors-ligne
     * (virement / cheque / espece) ou apres conversion CARD. Le caller
     * indique la raison ({@code reason=PAYMENT_COMPLETED}) pour traçabilite
     * audit. 204 No Content si l'envoi a ete declenche (best-effort SMTP).
     */
    @PostMapping("/internal/workspaces/{workspaceId}/issue-credentials")
    void issueCredentials(@PathVariable("workspaceId") UUID workspaceId,
                           @RequestParam(value = "reason", required = false) String reason);

    /**
     * 2026-06-30 — résout le rôle d'un membre interne (EMPLOYE/SUPERVISEUR) du
     * workspace. Utilisé par ticket-service (DossierTransferService) pour
     * refuser un transfert de dossier vers un non-EMPLOYE (défense en
     * profondeur ; le front filtre déjà). {@code 404} (FeignException.NotFound)
     * si l'userId n'est pas un membre interne connu (CLIENT/SUPER_ADMIN/inconnu).
     */
    @GetMapping("/internal/workspaces/{workspaceId}/users/{userId}/role")
    MemberRoleDto getUserRole(@PathVariable("workspaceId") UUID workspaceId,
                              @PathVariable("userId") UUID userId);

    /** Mirror de InternalWorkspaceUsersController.MemberRoleDto (auth-service). */
    record MemberRoleDto(UUID userId, String role, String status) {}

    /** BUG 8 — DTO miroir de PlanLimitsService.UsageSnapshot pour Feign. */
    record UsageSnapshotDto(
            String planCode,
            String planLabel,
            int users, int maxUsers, boolean usersUnlimited,
            int dossiers, int maxDossiers, boolean dossiersUnlimited,
            long storageBytes, int maxStorageGb, boolean storageUnlimited
    ) {}

    /**
     * DTO de reponse — mirror de TrialAccessChecker.TrialState + champs
     * supplementaires pour le subscription (Sprint 12).
     */
    record WorkspaceStatusResponse(
            UUID workspaceId,
            String trialStatus,         // TRIAL_ACTIVE | TRIAL_EXPIRED | CONVERTED | CANCELLED
            long daysRemaining,
            Instant trialEndsAt,
            String selectedPlan,
            String subscriptionStatus,  // null | 'active' | 'past_due' | 'cancelled' | 'incomplete'
            Instant subscriptionEndsAt
    ) {}
}
