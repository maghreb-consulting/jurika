package ma.jurika.ai.workflow;

import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.TemplateManifest;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.identity.SocieteIdentityEnricher;
import ma.jurika.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoint L4 : génération d'un document workflow.
 *
 * <p>Flux :
 * <ol>
 *   <li>Le frontend / orchestrateur appelle
 *       {@code POST /api/v1/ai/workflows/{workflowCode}/documents/{templateCode}}
 *       avec le payload métier (toutes les données nécessaires — aucune lecture DB
 *       côté ai-service).</li>
 *   <li>Le {@link WorkflowDocumentMappingService} route vers le bon
 *       {@link WorkflowDocumentMapper} et produit le {@code Map<String,Object>} de
 *       variables alignées sur le manifest L3.</li>
 *   <li>Le {@link DocxTemplateEngine} rend le {@code .docx} via le manifest.</li>
 *   <li>La réponse 200 OK porte le binaire avec les headers
 *       {@code Content-Disposition: attachment; filename=...} et
 *       {@code Content-Type} approprié.</li>
 * </ol>
 *
 * <p>Gestion erreurs :
 * <ul>
 *   <li><b>422 Unprocessable Entity</b> — {@link IllegalArgumentException}
 *       (workflow ou template inconnu côté mapper).</li>
 *   <li><b>404 Not Found</b> — template absent du manifest L3 + aucun fichier
 *       fallback (détecté via {@code DocumentResult.templateFound() == false}).</li>
 *   <li><b>500 Internal Server Error</b> — autres exceptions (gérées par
 *       le handler Spring par défaut).</li>
 * </ul>
 *
 * <p>Ce contrôleur cohabite avec
 * {@link ma.jurika.ai.api.DocumentController} (qui reste responsable de
 * {@code /api/v1/ai/documents/**}) : aucun conflit de routage.
 */
@RestController
@RequestMapping("/api/v1/ai/workflows")
public class WorkflowDocumentController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDocumentController.class);

    private final WorkflowDocumentMappingService mappingService;
    private final DocxTemplateEngine docxTemplateEngine;
    private final TemplateManifestLoader manifestLoader;
    private final SocieteIdentityEnricher identityEnricher;

    public WorkflowDocumentController(WorkflowDocumentMappingService mappingService,
                                       DocxTemplateEngine docxTemplateEngine,
                                       TemplateManifestLoader manifestLoader,
                                       SocieteIdentityEnricher identityEnricher) {
        this.mappingService = mappingService;
        this.docxTemplateEngine = docxTemplateEngine;
        this.manifestLoader = manifestLoader;
        this.identityEnricher = identityEnricher;
    }

    /**
     * Information de découverte d'un template, retournée par
     * {@link #listTemplates(String)}.
     *
     * @param code             code unique du template (résolu via manifest L3).
     * @param documentKind     libellé humain ({@code document_kind} du manifest).
     * @param file             nom du fichier {@code .docx} sous-jacent.
     * @param origin           {@code "directeur"} | {@code "interne"} | {@code "hérité"}
     *                         (les alias rapportent {@code "hérité"}).
     * @param deprecated       {@code true} pour les alias hérités ou les templates
     *                         marqués deprecated dans le manifest.
     * @param placeholderStyle style de placeholder ({@code uppercase_dollar} ou autre).
     */
    public record TemplateInfo(
            String code,
            String documentKind,
            String file,
            String origin,
            boolean deprecated,
            String placeholderStyle) {
    }

    /**
     * Endpoint de découverte L4 : retourne la liste des templates connus pour ce
     * workflow, à partir du manifest L3. Inclut les alias hérités.
     *
     * <p>Si aucun mapper n'est enregistré pour {@code workflowCode}, renvoie une
     * liste vide (200) — le frontend affiche alors "Aucun document disponible".
     */
    @GetMapping("/{workflowCode}/templates")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<TemplateInfo>> listTemplates(@PathVariable String workflowCode) {
        if (!mappingService.hasMapper(workflowCode)) {
            log.debug("listTemplates : workflow {} sans mapper → liste vide", workflowCode);
            return ResponseEntity.ok(List.of());
        }

        List<TemplateInfo> result = new ArrayList<>();
        for (TemplateManifest.TemplateEntry entry : manifestLoader.allEntries()) {
            if (entry.aliasOf() == null) {
                // Entrée directe : on garde ssi son workflow match.
                if (workflowCode.equals(entry.workflow())) {
                    result.add(toTemplateInfo(entry, false));
                }
            } else {
                // Alias : on résout la cible et on garde ssi la cible match.
                Optional<TemplateManifest.TemplateEntry> resolved = manifestLoader.resolve(entry.code());
                if (resolved.isEmpty()) continue;
                TemplateManifest.TemplateEntry target = resolved.get();
                if (workflowCode.equals(target.workflow())) {
                    // L'alias hérite des métadonnées (kind, file, style) de sa cible mais
                    // reste flaggé "hérité" + deprecated.
                    result.add(new TemplateInfo(
                            entry.code(),
                            target.documentKind(),
                            target.file(),
                            "hérité",
                            true,
                            target.placeholderStyle()));
                }
            }
        }

        log.debug("listTemplates workflow={} → {} entrée(s)", workflowCode, result.size());
        return ResponseEntity.ok(result);
    }

    private static TemplateInfo toTemplateInfo(TemplateManifest.TemplateEntry entry, boolean forcedDeprecated) {
        return new TemplateInfo(
                entry.code(),
                entry.documentKind(),
                entry.file(),
                entry.origin(),
                forcedDeprecated || entry.deprecated(),
                entry.placeholderStyle());
    }

    @PostMapping("/{workflowCode}/documents/{templateCode}")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> generate(@PathVariable String workflowCode,
                                            @PathVariable String templateCode,
                                            @AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        log.debug("Generate doc workflow={} template={}", workflowCode, templateCode);

        // Enrichissement identite societe depuis la BD (point unique, tous workflows PV).
        // Best-effort : si dossierId absent ou identite indisponible, payload inchange.
        UUID workspaceId = user == null ? null : user.workspaceId();
        Map<String, Object> enriched = identityEnricher.enrich(payload, workspaceId);

        Map<String, Object> variables = mappingService.map(
                workflowCode, templateCode, enriched);

        refuserSiControleBloquant(workflowCode, templateCode, variables);

        DocumentResult result = docxTemplateEngine.generate(templateCode, variables);

        if (!result.templateFound()) {
            // Manifest L3 inconnu + pas de fichier classpath direct : 404.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Template inconnu (manifest L3 + classpath) : " + templateCode);
        }

        refuserSiTrouGrammatical(workflowCode, templateCode, result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Workflow-Code", workflowCode)
                .header("X-Template-Code", templateCode)
                .header("X-Missing-Variables", String.join(",", result.missingVariables()))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.bytes());
    }

    /**
     * Lot B (2026-09-11) — LES TROIS CONTRÔLES BLOQUANTS DU § 18.
     *
     * <p>Certificat négatif, pièces d'identité et de capacité des associés et des
     * gérants, rapport du commissaire aux apports lorsque les apports en nature y
     * sont soumis. Le parcours du 9 septembre en a fait des CONTRÔLES et non des
     * étapes : ils ne se cochent pas, ils interrogent les variables résolues.
     *
     * <p>Ils s'exécutent après la résolution — les valeurs interrogées sont
     * exactement celles qui seraient imprimées — et avant le rendu : produire un
     * document pour le refuser ensuite n'aurait pas de sens.
     *
     * <p>Portée : les statuts, et eux seuls. Le dictionnaire est explicite —
     * « contrôles bloquants au lancement de la génération des statuts ». Les huit
     * autres workflows ne sont pas concernés.
     *
     * @see ControlesBloquantsStatuts
     */
    private void refuserSiControleBloquant(String workflowCode, String templateCode,
                                            Map<String, Object> variables) {
        if (!"CREATION_SARL".equals(workflowCode)) return;
        if (!ControlesBloquantsStatuts.concerne(templateCode)) return;
        String motif = ControlesBloquantsStatuts.motifDeRefus(variables);
        if (motif == null) return;
        log.warn("Controle bloquant {} / {} : {}", workflowCode, templateCode, motif);
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, motif);
    }

    /**
     * Lot 5 (2026-09-07) — CONTRÔLE DE COMPLÉTUDE À LA GÉNÉRATION.
     *
     * <p>Le point de contrôle « jeu de variables complet » du guide vivait jusqu'ici
     * dans {@code GuideTransitionChecks}, côté ticket-service, et portait sur DIX
     * colonnes du dossier — jamais sur les variables réellement consommées par les
     * modèles. C'est ce trou qui a laissé sortir « né le  à , demeurant à  ».
     *
     * <p>Le contrôle porte désormais sur le document RENDU : une variable dont le
     * vide s'imprime au milieu d'une phrase fait échouer la génération, avec son nom
     * ET la ligne où elle se trouve. Une case administrative laissée blanche passe :
     * un formulaire DGI incomplet reste un formulaire recevable — c'est la
     * classification posée au lot 5.
     *
     * <p>Portée volontairement limitée au workflow CRÉATION : les huit autres
     * conservent le comportement historique (marqueur rouge dans le document).
     */
    private void refuserSiTrouGrammatical(String workflowCode, String templateCode,
                                           DocumentResult result) {
        if (!"CREATION_SARL".equals(workflowCode)) return;
        String motif = ma.jurika.ai.document.ControleCompletude.motifDeRefus(result);
        if (motif == null) return;
        log.warn("Refus generation {} / {} : {}", workflowCode, templateCode, motif);
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, motif);
    }

    /**
     * Workflow ou template non supporté par un mapper : 422 Unprocessable Entity.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("422 sur generate workflow doc : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "status", 422,
                        "error", "Unprocessable Entity",
                        "message", ex.getMessage()));
    }
}
