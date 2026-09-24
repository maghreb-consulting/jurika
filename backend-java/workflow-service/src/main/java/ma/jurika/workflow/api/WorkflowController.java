package ma.jurika.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.workflow.application.ConstructeurChargeUtileCreation;
import ma.jurika.workflow.application.DossierIdentityQueryService;
import ma.jurika.workflow.application.MagasinVariables;
import ma.jurika.workflow.application.WorkflowUseCases;
import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.domain.strategy.ModificationWorkflow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowUseCases useCases;
    private final DossierIdentityQueryService dossierIdentity;
    /** Lot C — le magasin de variables du dossier, source unique de la génération. */
    private final MagasinVariables magasin;

    public WorkflowController(WorkflowUseCases useCases,
                              DossierIdentityQueryService dossierIdentity,
                              MagasinVariables magasin) {
        this.useCases = useCases;
        this.dossierIdentity = dossierIdentity;
        this.magasin = magasin;
    }

    /**
     * Parties prenantes (associes + gerants) d'un dossier, lues depuis la fiche
     * structuree — pour peupler cote front les selecteurs « associes a convoquer »
     * (etape 1 Modification) et les dropdowns de saisie, SANS re-saisie d'identite.
     * Disponible des la selection de la societe (avant toute validation d'etape).
     *
     * <p>Lot « Liquidation 4 etapes » (2026-08-13) : la reponse porte aussi les faits de
     * DISSOLUTION deja en base — {@code liquidateur} (nomme a l'etape 1 du workflow
     * Dissolution), {@code siegeLiquidation} et {@code dateDissolution}. Le workflow
     * LIQUIDATION les affiche en LECTURE SEULE : aucune de ces donnees n'est re-saisie.
     * Absents (dossier dissous avant cette evolution) = cas de secours cote front.
     */
    @GetMapping("/dossiers/{dossierId}/parties")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> dossierParties(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable UUID dossierId) {
        Map<String, Object> identity = dossierIdentity.identity(actor.workspaceId(), dossierId);
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("associes", identity.getOrDefault("associes", java.util.List.of()));
        out.put("gerants", identity.getOrDefault("gerants", java.util.List.of()));
        // 2026-08-14 — Ajout de l'identite de la SOCIETE (siege, capital, RC, greffe,
        // nombre de parts). Elle etait deja calculee par DossierIdentityQueryService
        // mais n'etait pas recopiee ici : le front ne la recevait donc pas, et les
        // formulaires de seance laissaient vides le « Lieu » de l'assemblee (defaut =
        // siege social) et le « Lieu de signature » (defaut = ville du greffe) alors
        // que la base les connait. Constate en auditant l'etape 1 de Dissolution.
        for (String key : new String[]{"denomination", "formeJuridique", "statut",
                "liquidateur", "siegeLiquidation", "dateDissolution",
                "siegeSocial", "villeGreffe", "capitalSocial", "rcNumero", "nombreParts"}) {
            Object v = identity.get(key);
            if (v != null) out.put(key, v);
        }
        return out;
    }

    public record StartRequest(@NotNull WorkflowType type) {}
    public record SaveRequest(@Min(1) int currentStep, @NotNull Map<String, Object> data) {}
    public record StepRequest(@Min(1) int step, @NotNull Map<String, Object> payload) {}

    /**
     * P2 2026-06-04 : enregistrement d'une piece jointe persistant cross-step.
     * MVP : on persiste les metadonnees dans {@code workflow_progress.data.pieces[code]}
     * — le code reste utilisable depuis n'importe quelle etape du wizard.
     * Le fichier lui-meme peut etre stocke par dataroom-service en parallele
     * (out of scope ici, le registry suffit pour eviter le re-upload UX).
     */
    public record RegisterPieceRequest(
            @jakarta.validation.constraints.NotBlank String code,
            @jakarta.validation.constraints.NotBlank String label,
            String filename,
            Long sizeBytes,
            String contentType,
            /** Etape d'origine (1..N) -- info de tracabilite. */
            @Min(1) Integer uploadedAtStep
    ) {}

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> catalog() {
        Map<String, Integer> stepsByType = new java.util.LinkedHashMap<>();
        for (WorkflowType t : WorkflowType.values()) {
            stepsByType.put(t.name(), t.totalSteps());
        }
        return Map.of(
                "types", stepsByType,
                // Voie directeur unifiee : les resolutionTypes snake_case remplacent
                // l'ancien set UPPERCASE (retire). Le front utilise en pratique le
                // catalogue officialModificationDecisions.ts.
                "modificationTypes", ModificationWorkflow.RESOLUTION_TYPES.stream().sorted().toList());
    }

    @PostMapping("/{ticketId}/start")
    // SUPERVISEUR = oversight only : aucune progression de workflow. Denial explicite
    // car la hierarchie (SUPERVISEUR > EMPLOYE) l'autoriserait sinon (super_admin exclu aussi).
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<WorkflowProgress> start(@AuthenticationPrincipal AuthenticatedUser actor,
                                                   @PathVariable UUID ticketId,
                                                   @Valid @RequestBody StartRequest req) {
        WorkflowProgress p = useCases.startOrResume(actor.workspaceId(), ticketId, req.type(), actor.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("isAuthenticated()")
    public WorkflowProgress get(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable UUID ticketId) {
        return useCases.get(actor.workspaceId(), ticketId);
    }

    /**
     * LA CHARGE UTILE D'UN DOSSIER, CONSTRUITE À PARTIR DU MAGASIN.
     *
     * <p>Point d'entrée unique de la génération documentaire du parcours de
     * création. Le navigateur ne construit plus rien : il demande ici ce que le
     * dossier contient, et le transmet tel quel au moteur.
     *
     * <p>C'est ce qui rend la génération déterministe. Auparavant, deux
     * constructeurs vivaient dans le navigateur — l'un mort et testé, l'autre
     * vivant et amputé de 29 clés sur 54 — et le document produit dépendait de
     * l'écran qui l'avait demandé.
     *
     * <p>La charge utile rendue est <b>profondément non modifiable</b> : elle est
     * strictement dérivée du magasin, jamais un endroit où une valeur s'écrit.
     * Corriger une valeur se fait au magasin.
     */
    @GetMapping("/{ticketId}/charge-utile")
    @PreAuthorize("hasAnyRole('EMPLOYE','SUPERVISEUR')")
    public Map<String, Object> chargeUtile(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable UUID ticketId) {
        return ConstructeurChargeUtileCreation.construire(
                magasin.lirePourGeneration(actor.workspaceId(), ticketId));
    }

    /** Les variables du dossier avec leur provenance — affichage « en lecture » de l'étape 7. */
    @GetMapping("/{ticketId}/variables")
    @PreAuthorize("hasAnyRole('EMPLOYE','SUPERVISEUR')")
    public Object variables(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable UUID ticketId) {
        return magasin.lire(actor.workspaceId(), ticketId);
    }

    @PostMapping("/{ticketId}/save")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public WorkflowProgress save(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable UUID ticketId,
                                  @Valid @RequestBody SaveRequest req) {
        return useCases.save(actor.workspaceId(), ticketId, req.currentStep(), req.data(),
                actor.userId());
    }

    @PostMapping("/{ticketId}/execute-step")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public Map<String, Object> executeStep(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable UUID ticketId,
                                            @Valid @RequestBody StepRequest req) {
        var r = useCases.executeStep(actor.workspaceId(), ticketId, req.step(),
                req.payload(), actor.userId());
        return Map.of(
                "progress", r.progress(),
                "stepData", r.result().stepData(),
                "advanced", r.result().canAdvance());
    }

    /**
     * P2 — Enregistre / met a jour une piece jointe cross-step. Le code (ex "CN",
     * "JUSTIFICATIF_SIEGE") est unique par ticket -- re-enregistrer le meme code
     * met a jour la metadonnee (filename, taille...) sans dupliquer.
     */
    @PostMapping("/{ticketId}/pieces")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public WorkflowProgress registerPiece(@AuthenticationPrincipal AuthenticatedUser actor,
                                           @PathVariable UUID ticketId,
                                           @Valid @RequestBody RegisterPieceRequest req) {
        return useCases.registerPiece(actor.workspaceId(), ticketId, req);
    }

    /**
     * P2 — Supprime l'enregistrement d'une piece (UI bouton "Retirer").
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{ticketId}/pieces/{code}")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public WorkflowProgress unregisterPiece(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @PathVariable UUID ticketId,
                                             @PathVariable String code) {
        return useCases.unregisterPiece(actor.workspaceId(), ticketId, code);
    }
}
