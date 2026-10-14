package ma.jurika.auth.api;

import ma.jurika.auth.application.IssueCredentialsUseCase;
import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.billing.PlanLimitsService.UsageSnapshot;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.trial.TrialAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 12 — endpoint INTERNAL pour le statut workspace (trial + subscription).
 *
 * <p>Consomme par :
 *  - {@code RemoteTrialAccessChecker} (jurika-common, 5 microservices)
 *  - {@code AuthWorkspaceStatusUpdater} (billing-service, transitions
 *    de status apres webhook Stripe)
 *
 * <p>Securite : endpoint sous {@code /internal/**} — par convention,
 * gateway rejette les requetes externes vers ce path (filtrage edge).
 * Sprint 12 V1 : pas de X-Internal-Token (sera ajoute Sprint 14 / hardening).
 */
@RestController
@RequestMapping("/internal/workspaces")
public class InternalWorkspaceStatusController {

    private static final Logger log = LoggerFactory.getLogger(InternalWorkspaceStatusController.class);

    private final WorkspaceJpaRepository workspaceRepo;
    private final TrialAccessChecker trialChecker;
    private final PlanLimitsService planLimitsService;
    private final IssueCredentialsUseCase issueCredentialsUseCase;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public InternalWorkspaceStatusController(WorkspaceJpaRepository workspaceRepo,
                                              TrialAccessChecker trialChecker,
                                              @Autowired(required = false) PlanLimitsService planLimitsService,
                                              IssueCredentialsUseCase issueCredentialsUseCase,
                                              org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.workspaceRepo = workspaceRepo;
        this.trialChecker = trialChecker;
        this.planLimitsService = planLimitsService;
        this.issueCredentialsUseCase = issueCredentialsUseCase;
        this.jdbc = jdbc;
    }

    public record WorkspaceStatusDto(
            UUID workspaceId,
            String trialStatus,
            long daysRemaining,
            Instant trialEndsAt,
            String selectedPlan,
            String subscriptionStatus,
            Instant subscriptionEndsAt
    ) {}

    // Lot L0 (E13b) : en transaction, pour que le workspace du chemin (pose par
    // ContexteWorkspaceCheminConfig) atteigne la RLS.
    @GetMapping("/{workspaceId}/status")
    @Transactional(readOnly = true)
    public ResponseEntity<WorkspaceStatusDto> getStatus(@PathVariable UUID workspaceId) {
        WorkspaceEntity ws = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace inconnu: " + workspaceId));

        TrialAccessChecker.TrialState trial = trialChecker.getState(workspaceId).orElse(null);

        // Sprint 12 — pas de table subscription dans auth-service (vit dans billing-service).
        // Le subscription_status est lu depuis WorkspaceEntity si une colonne dediee
        // est ajoutee Sprint 12+ (V20 — cf. PLAN §9). En attendant, le champ est null.
        String subscriptionStatus = resolveSubscriptionStatus(ws);
        Instant subscriptionEndsAt = resolveSubscriptionEndsAt(ws);

        WorkspaceStatusDto dto = new WorkspaceStatusDto(
                workspaceId,
                trial != null ? trial.status() : null,
                trial != null ? trial.daysRemaining() : 0L,
                trial != null ? trial.endsAt() : null,
                trial != null ? trial.selectedPlan() : null,
                subscriptionStatus,
                subscriptionEndsAt
        );
        return ResponseEntity.ok(dto);
    }

    /**
     * PUT /internal/workspaces/{id}/status : transitions d'abonnement propagees par billing-service.
     *
     * <p>Lot L7 (E5) :
     * <ul>
     *   <li>{@code ACTIVATED} : forfait ({@code planCode}, code du catalogue, refuse s'il est
     *       inconnu), fin de la periode payee ({@code accessUntil}), debut de l'abonnement
     *       ({@code abonnementDebut}, ancre du mois d'abonnement RG-PLN-01), rythme
     *       ({@code rythme}). Un workspace ARCHIVE est reactive dans l'etat exact ou il a ete
     *       laisse (RG-TAR-08) : seul son statut change, aucune donnee n'est touchee.</li>
     *   <li>{@code ARCHIVED} : forfait arrete ({@code motif} IMPAYE, FIN_ABONNEMENT, RESILIATION,
     *       DECISION) : statut ARCHIVED, rien n'est supprime (RG-TAR-07, RG-PLN-07).</li>
     *   <li>{@code CANCELLED}, {@code PAST_DUE} : inchanges.</li>
     * </ul>
     * Idempotent. Une transition inconnue est REFUSEE (400) : avant, elle etait ignoree en silence.
     */
    @PutMapping("/{workspaceId}/status")
    @Transactional
    public ResponseEntity<Void> updateStatus(@PathVariable UUID workspaceId,
                                              @RequestParam String transition,
                                              @RequestParam(required = false) String planCode,
                                              @RequestParam(required = false) String accessUntil,
                                              @RequestParam(required = false) String abonnementDebut,
                                              @RequestParam(required = false) String rythme,
                                              @RequestParam(required = false) String motif) {
        WorkspaceEntity ws = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace inconnu: " + workspaceId));

        Instant accessUntilParsed = parseInstant(accessUntil);
        Instant now = Instant.now();

        switch (transition) {
            case "ACTIVATED" -> {
                ws.setSubscriptionStatus("active");
                if (accessUntilParsed != null) {
                    ws.setSubscriptionEndsAt(accessUntilParsed);
                }
                if (ws.getActiveSince() == null) {
                    ws.setActiveSince(now);
                }
                ws.setCancelledAt(null);
                if (planCode != null && !planCode.isBlank()) {
                    ws.setSelectedPlan(PlanCatalog.findByCode(planCode)
                            .orElseThrow(() -> new ValidationException("Forfait inconnu : " + planCode))
                            .internalCode());
                }
                if (abonnementDebut != null && !abonnementDebut.isBlank()) {
                    ws.setAbonnementDebut(LocalDate.parse(abonnementDebut));
                }
                if (rythme != null && !rythme.isBlank()) {
                    ws.setRythmePaiement(PlanCatalog.BillingPeriod.fromString(rythme).codeContrat());
                }
                // RG-TAR-08 : reactivation dans l'etat exact (seul le statut change).
                if ("ARCHIVED".equals(ws.getStatus())) {
                    ws.setStatus("ACTIVE");
                }
                ws.setArchiveLe(null);
                ws.setArchiveMotif(null);
                // RG-BL05 : le trial se cloture en "CONVERTED" pour lever le bandeau cote UI.
                if (!"CONVERTED".equals(ws.getTrialStatus())) {
                    ws.setTrialStatus("CONVERTED");
                }
            }
            case "ARCHIVED" -> {
                if (motif == null || !MOTIFS_ARCHIVAGE.contains(motif)) {
                    throw new ValidationException("Motif d'archivage attendu : " + MOTIFS_ARCHIVAGE);
                }
                if (!"ARCHIVED".equals(ws.getStatus())) {
                    ws.setStatus("ARCHIVED");
                    ws.setArchiveLe(now);
                    ws.setArchiveMotif(motif);
                }
                ws.setSubscriptionStatus("IMPAYE".equals(motif) ? "unpaid" : "cancelled");
                if (ws.getCancelledAt() == null) ws.setCancelledAt(now);
            }
            case "CANCELLED" -> {
                ws.setSubscriptionStatus("cancelled");
                ws.setCancelledAt(now);
                if (accessUntilParsed != null) {
                    // RG-BL07 : acces preserve jusqu'a la fin de la periode payee.
                    ws.setSubscriptionEndsAt(accessUntilParsed);
                }
            }
            case "PAST_DUE" -> {
                ws.setSubscriptionStatus("past_due");
                if (accessUntilParsed != null) {
                    ws.setSubscriptionEndsAt(accessUntilParsed);
                }
            }
            default -> throw new ValidationException("Transition inconnue : " + transition);
        }

        workspaceRepo.save(ws);
        log.info("Transition workspace status persistee workspace={} transition={} plan={} subStatus={} endsAt={}",
                workspaceId, transition, planCode, ws.getSubscriptionStatus(), ws.getSubscriptionEndsAt());
        return ResponseEntity.noContent().build();
    }

    /**
     * Lot L7 (E5), RG-PAY-02 et RG-PLN-02 : super-admins actifs (identifiant et workspace), pour le
     * canal du super-admin (paiement hors ligne a enregistrer). Fonction auth V44, hors workspace.
     */
    public record SuperAdminDto(UUID id, UUID workspaceId) {}

    @GetMapping("/super-admins")
    @Transactional(readOnly = true)
    public java.util.List<SuperAdminDto> superAdmins() {
        return jdbc.query("SELECT id, workspace_id FROM auth_super_admins_actifs()",
                (rs, n) -> new SuperAdminDto((UUID) rs.getObject("id"), (UUID) rs.getObject("workspace_id")));
    }

    /** Lot L7 (E5) : motifs d'archivage (auth V44). */
    static final java.util.Set<String> MOTIFS_ARCHIVAGE =
            java.util.Set.of("IMPAYE", "FIN_ABONNEMENT", "RESILIATION", "DECISION");

    /**
     * Lot L7 (E5), RG-PAY-03 : identite du cabinet pour la facture (denomination, ICE, IF, RC,
     * adresse, ville). Valeurs telles qu'en base, vides comprises : la facture nomme ce qui manque.
     */
    public record IdentiteFacturationDto(UUID workspaceId, String denomination, String ice, String identifiantFiscal,
                                         String rc, String adresse, String ville, String email,
                                         String statut, String forfait) {}

    @GetMapping("/{workspaceId}/identite-facturation")
    @Transactional(readOnly = true)
    public ResponseEntity<IdentiteFacturationDto> identiteFacturation(@PathVariable UUID workspaceId) {
        WorkspaceEntity ws = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace inconnu: " + workspaceId));
        return ResponseEntity.ok(new IdentiteFacturationDto(ws.getId(), ws.getName(), ws.getIce(), ws.getIfFiscal(),
                ws.getRcNumber(), ws.getAdresse(), ws.getCity(), ws.getContactEmail(), ws.getStatus(),
                ws.getSelectedPlan()));
    }

    /**
     * BUG 8 (2026-06-07) — snapshot consommation reelle (employes / dossiers /
     * stockage) pour permettre a billing-service ChangePlanUseCase de
     * controler les downgrades. {@code 204 No Content} si le service est
     * desactive (property {@code jurika.plan-limits.enabled=false}).
     */
    @GetMapping("/{workspaceId}/usage")
    @Transactional(readOnly = true)
    public ResponseEntity<UsageSnapshot> getUsage(@PathVariable UUID workspaceId) {
        if (!workspaceRepo.existsById(workspaceId)) {
            throw new NotFoundException("Workspace inconnu: " + workspaceId);
        }
        if (planLimitsService == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(planLimitsService.snapshot(workspaceId));
    }

    /**
     * BUG 14 (2026-06-07) — re-emission des identifiants apres validation
     * d'un paiement hors-ligne (virement / cheque / espece) ou apres
     * conversion CARD si le signup avait differe l'envoi. Genere un
     * nouveau MDP temp + renvoie l'email welcome.
     */
    @PostMapping("/{workspaceId}/issue-credentials")
    public ResponseEntity<Void> issueCredentials(@PathVariable UUID workspaceId,
                                                  @RequestParam(required = false) String reason) {
        issueCredentialsUseCase.execute(workspaceId, reason);
        return ResponseEntity.noContent().build();
    }

    private String resolveSubscriptionStatus(WorkspaceEntity ws) {
        // V26 (Sprint 12 finition, 2026-06-04) : colonne subscription_status persistee.
        return ws.getSubscriptionStatus();
    }

    private Instant resolveSubscriptionEndsAt(WorkspaceEntity ws) {
        return ws.getSubscriptionEndsAt();
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (RuntimeException e) {
            log.warn("accessUntil non parsable ignore : '{}' ({})", iso, e.getMessage());
            return null;
        }
    }
}
