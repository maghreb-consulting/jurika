package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.application.WopiService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Édition bureautique fidèle — les points d'entrée WOPI et l'ouverture de séance.
 *
 * <p>Lot 3 (2026-09-07). Deux familles d'URL, avec deux régimes d'authentification
 * DIFFÉRENTS, et c'est voulu :
 *
 * <ul>
 *   <li><b>{@code /wopi/**}</b> — appelées par COLLABORA, depuis l'intérieur du
 *       réseau Docker, sans JWT. Ouvertes au niveau Spring Security, elles
 *       portent leur contrôle d'accès dans {@link WopiService} : jeton opaque à
 *       durée de vie courte, correspondance {@code fileId} / séance, et
 *       cloisonnement multi-tenant vérifié explicitement à chaque appel. Le
 *       {@code fileId} de l'URL n'accorde aucun droit.</li>
 *   <li><b>{@code /documents/{id}/edition-session}</b> — appelée par le
 *       NAVIGATEUR, avec le JWT de session. C'est le seul endroit où un jeton
 *       WOPI est délivré, et il n'est rendu qu'une fois.</li>
 * </ul>
 *
 * <p>Le JWT de session n'est jamais réutilisé comme jeton WOPI : il vivrait dans
 * une URL, donc dans les journaux de Collabora et l'historique du navigateur.
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Édition bureautique (WOPI)")
public class WopiController {

    private static final Logger log = LoggerFactory.getLogger(WopiController.class);

    private static final String TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final WopiService wopi;

    public WopiController(WopiService wopi) {
        this.wopi = wopi;
    }

    // =================================================================
    //  Ouverture / fermeture de séance — appelées par le navigateur (JWT)
    // =================================================================

    @PostMapping("/documents/{documentId}/edition-session")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Ouvre une séance d'édition bureautique",
            description = "Rend l'URL de l'éditeur, le WOPISrc et un jeton d'accès à durée de vie "
                    + "courte, propre à ce document et à cet utilisateur. Le jeton n'est rendu "
                    + "qu'une seule fois.")
    public WopiService.SeanceEdition ouvrirSeance(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable UUID documentId) {
        String nom = (user.email() == null || user.email().isBlank()) ? "Employé" : user.email();
        return wopi.ouvrir(documentId, user.userId(), nom, user.role());
    }

    @DeleteMapping("/edition-session/{sessionId}")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Ferme une séance d'édition",
            description = "La lecture est coupée immédiatement. L'écriture reste acceptée le temps "
                    + "de la fenêtre de grâce : Collabora enregistre de façon asynchrone et appelle "
                    + "souvent PutFile APRÈS la fermeture de l'onglet. Révoquer sèchement ferait "
                    + "perdre la dernière sauvegarde sans le dire.")
    public ResponseEntity<Void> fermerSeance(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable UUID sessionId) {
        wopi.fermer(sessionId, user.userId());
        return ResponseEntity.noContent().build();
    }

    // =================================================================
    //  WOPI — appelées par Collabora, sans JWT
    // =================================================================

    /** CheckFileInfo — métadonnées, droits, identité de l'utilisateur. */
    @GetMapping(value = "/wopi/files/{fileId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> checkFileInfo(@PathVariable UUID fileId,
                                            @RequestParam(name = "access_token", required = false) String token,
                                            @RequestHeader(name = "Origin", required = false) String origin) {
        try {
            return ResponseEntity.ok(wopi.checkFileInfo(fileId, token, origin));
        } catch (WopiService.WopiAccesRefuse e) {
            return refus(fileId, "CheckFileInfo", e);
        }
    }

    /** GetFile — le binaire .docx. */
    @GetMapping("/wopi/files/{fileId}/contents")
    public ResponseEntity<?> getFile(@PathVariable UUID fileId,
                                      @RequestParam(name = "access_token", required = false) String token) {
        try {
            ObjectStorage.DownloadResult r = wopi.getFile(fileId, token);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, TYPE_DOCX)
                    .contentLength(r.size())
                    .body(new InputStreamResource(r.stream()));
        } catch (WopiService.WopiAccesRefuse e) {
            return refus(fileId, "GetFile", e);
        }
    }

    /**
     * PutFile — le binaire modifié.
     *
     * <p>Les deux en-têtes que Collabora ajoute distinguent un enregistrement
     * automatique d'un geste de l'employé, et le dernier enregistrement — celui
     * qui arrive souvent après la fermeture de l'onglet. On les journalise ;
     * c'est ce qui permet de savoir, plus tard, si une sauvegarde tardive a bien
     * été acceptée.
     */
    @PostMapping("/wopi/files/{fileId}/contents")
    public ResponseEntity<?> putFile(@PathVariable UUID fileId,
                                      @RequestParam(name = "access_token", required = false) String token,
                                      @RequestHeader(name = "X-WOPI-Lock", required = false) String lockId,
                                      @RequestHeader(name = "X-COOL-WOPI-IsAutosave", required = false) String autosaveCool,
                                      @RequestHeader(name = "X-LOOL-WOPI-IsAutosave", required = false) String autosaveLool,
                                      @RequestHeader(name = "X-COOL-WOPI-IsExitSave", required = false) String exitCool,
                                      @RequestHeader(name = "X-LOOL-WOPI-IsExitSave", required = false) String exitLool,
                                      // `required = false` : sans cela, Spring rejette un corps
                                      // vide par un 400 AVANT d'entrer dans la methode, et un
                                      // jeton invalide recevrait 400 au lieu de 401 — la reponse
                                      // renseignerait sur ce qui a echoue.
                                      @RequestBody(required = false) byte[] contenu) {
        boolean autosave = vrai(autosaveCool) || vrai(autosaveLool);
        boolean exitSave = vrai(exitCool) || vrai(exitLool);
        try {
            // La validation du corps vit dans le service, APRÈS le contrôle
            // d'accès : un jeton invalide doit recevoir 401 quel que soit le
            // corps envoyé, sinon la réponse renseigne sur ce qui a échoué.
            UUID ecritSur = wopi.putFile(fileId, token, lockId, contenu, autosave, exitSave);
            // Collabora attend un JSON ; ItemVersion lui sert à détecter une
            // modification concurrente.
            return ResponseEntity.ok(Map.of("ItemVersion", ecritSur.toString()));
        } catch (WopiService.WopiVerrouConflit e) {
            return conflit(fileId, "PutFile", e);
        } catch (WopiService.WopiAccesRefuse e) {
            return refus(fileId, "PutFile", e);
        } catch (IllegalArgumentException e) {
            // Corps vide : refuser plutôt qu'écraser un acte par du vide.
            log.warn("WOPI PutFile file={} : {}", fileId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =================================================================
    //  Verrous d'édition (lot 4) — POST /wopi/files/{id} + X-WOPI-Override
    // =================================================================

    /**
     * Les quatre opérations de verrou partagent une URL et se distinguent par
     * l'en-tête {@code X-WOPI-Override} — c'est le protocole qui l'impose.
     *
     * <p>Sans ces opérations, deux employés pouvaient ouvrir le même acte et le
     * second écrasait le premier sans qu'aucun des deux ne l'apprenne. Sur un
     * acte juridique, une perte d'édition silencieuse est inacceptable.
     */
    @PostMapping("/wopi/files/{fileId}")
    public ResponseEntity<?> operationVerrou(
            @PathVariable UUID fileId,
            @RequestParam(name = "access_token", required = false) String token,
            @RequestHeader(name = "X-WOPI-Override", required = false) String operation,
            @RequestHeader(name = "X-WOPI-Lock", required = false) String lockId,
            @RequestHeader(name = "X-WOPI-OldLock", required = false) String ancienVerrou) {
        String op = operation == null ? "" : operation.toUpperCase(java.util.Locale.ROOT);
        try {
            switch (op) {
                // LOCK avec X-WOPI-OldLock, c'est UnlockAndRelock : même code,
                // le service compare l'ancien verrou au verrou en place.
                case "LOCK" -> wopi.lock(fileId, token, lockId, ancienVerrou);
                case "REFRESH_LOCK" -> wopi.refreshLock(fileId, token, lockId);
                case "UNLOCK" -> wopi.unlock(fileId, token, lockId);
                case "GET_LOCK" -> {
                    String courant = wopi.getLock(fileId, token);
                    return ResponseEntity.ok().header("X-WOPI-Lock", courant).build();
                }
                default -> {
                    log.warn("WOPI operation inconnue file={} override={}", fileId, op);
                    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
                }
            }
            return ResponseEntity.ok().build();
        } catch (WopiService.WopiVerrouConflit e) {
            return conflit(fileId, op, e);
        } catch (WopiService.WopiAccesRefuse e) {
            return refus(fileId, op, e);
        }
    }

    /**
     * Un conflit de verrou est un 409 qui PORTE le verrou en place, comme le
     * protocole l'exige : c'est ce qui permet à l'éditeur de basculer en lecture
     * seule au lieu de laisser croire que l'enregistrement a eu lieu.
     *
     * <p>Ce n'est pas une fuite : l'appelant a déjà prouvé son droit sur ce
     * document par son jeton.
     */
    private ResponseEntity<?> conflit(UUID fileId, String operation,
                                       WopiService.WopiVerrouConflit e) {
        log.warn("WOPI {} conflit de verrou file={} : {}", operation, fileId, e.getMessage());
        var reponse = ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-WOPI-Lock", e.verrouCourant())
                .header("X-WOPI-LockFailureReason", e.getMessage());
        return e.detenteur() == null
                ? reponse.build()
                : reponse.body(Map.of("detenteur", e.detenteur()));
    }

    /**
     * Un refus WOPI est un 401, jamais un 404 bavard : on ne confirme pas
     * l'existence d'un document à qui n'a pas le jeton. Le motif reste dans les
     * journaux du serveur, pas dans la réponse.
     */
    private ResponseEntity<?> refus(UUID fileId, String operation, WopiService.WopiAccesRefuse e) {
        log.warn("WOPI {} refuse file={} : {}", operation, fileId, e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private static boolean vrai(String v) {
        return v != null && v.equalsIgnoreCase("true");
    }
}
