package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.AccessLogPage;
import ma.jurika.dataroom.api.dto.DataroomDtos.ClientLinkResponse;
import ma.jurika.dataroom.api.dto.DataroomDtos.ClientPermissionsView;
import ma.jurika.dataroom.api.dto.DataroomDtos.SettingsView;
import ma.jurika.dataroom.api.dto.DataroomDtos.ToggleSuspensionRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdateAccountantNotifRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdatePermissionsRequest;
import ma.jurika.dataroom.application.DataroomSettingsService;
import ma.jurika.dataroom.application.DeleteDataroomUseCase;
import ma.jurika.dataroom.application.access.ClientAccessLogQueryService;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Sprint 7 / TASK 6.2 -- Refactor : controller dedie aux settings du
 * Data Room (permissions, suspension, lien client, notif comptable,
 * et le log d'acces detaille -- ces concerns sont regroupes car ils
 * concernent tous la "gouvernance" d'acces au dossier).
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Settings", description = "Permissions client, suspension, lien client, notif comptable, log d'acces")
public class SettingsController {

    private final DataroomSettingsService settings;
    private final ClientAccessLogQueryService accessLogQuery;
    private final DeleteDataroomUseCase deleteDataroom;

    @Value("${jurika.dataroom.client-link-base-url:http://localhost:5173/client/dataroom}")
    private String clientLinkBaseUrl;

    public SettingsController(DataroomSettingsService settings,
                              ClientAccessLogQueryService accessLogQuery,
                              DeleteDataroomUseCase deleteDataroom) {
        this.settings = settings;
        this.accessLogQuery = accessLogQuery;
        this.deleteDataroom = deleteDataroom;
    }

    @GetMapping("/dossiers/{dossierId}/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public SettingsView getSettings(@PathVariable UUID dossierId) {
        return toView(settings.getOrCreate(dossierId));
    }

    /**
     * 2026-06-30 -- Permissions du client SUR SON dossier (lecture seule, vue
     * allegee). Ouvre l'acces au CLIENT pour qu'il affiche l'etat REEL de ses
     * droits (download/print/depot) et active/desactive ses boutons. Ne fuit
     * jamais le token de lien ni l'email comptable (vs {@link #getSettings}).
     */
    @GetMapping("/dossiers/{dossierId}/settings/my-permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public ClientPermissionsView myPermissions(@PathVariable UUID dossierId) {
        SettingsEntity s = settings.getOrCreate(dossierId);
        return new ClientPermissionsView(s.getDossierId(), s.getAccessStatus(),
                s.isPermDownload(), s.isPermPrint(), s.isPermDepot());
    }

    @PatchMapping("/dossiers/{dossierId}/settings/permissions")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public SettingsView updatePermissions(@PathVariable UUID dossierId,
                                           @Valid @RequestBody UpdatePermissionsRequest req) {
        return toView(settings.updatePermissions(dossierId, req.permDownload(), req.permPrint(), req.permDepot()));
    }

    @PatchMapping("/dossiers/{dossierId}/settings/suspension")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public SettingsView toggleSuspension(@PathVariable UUID dossierId,
                                          @Valid @RequestBody ToggleSuspensionRequest req) {
        return toView(settings.toggleSuspension(dossierId, req.suspended()));
    }

    @PostMapping("/dossiers/{dossierId}/settings/regenerate-link")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public ClientLinkResponse regenerateLink(@PathVariable UUID dossierId) {
        SettingsEntity s = settings.regenerateLinkToken(dossierId);
        return new ClientLinkResponse(clientLinkBaseUrl + "?token=" + s.getClientLinkToken(),
                s.getClientLinkToken());
    }

    @GetMapping("/dossiers/{dossierId}/settings/client-link")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public ClientLinkResponse getClientLink(@PathVariable UUID dossierId) {
        SettingsEntity s = settings.getOrCreate(dossierId);
        return new ClientLinkResponse(clientLinkBaseUrl + "?token=" + s.getClientLinkToken(),
                s.getClientLinkToken());
    }

    /** RG-DC27 : config notif comptable a chaque upload comptable. */
    @PatchMapping("/dossiers/{dossierId}/settings/accountant-notif")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public SettingsView updateAccountantNotif(@PathVariable UUID dossierId,
                                                @Valid @RequestBody UpdateAccountantNotifRequest req) {
        return toView(settings.updateAccountantNotification(dossierId, req.accountantEmail(), req.enabled()));
    }

    /**
     * Sprint 7 / TASK 5 -- Drawer "Activite client" : 50 dernieres actions.
     * Tri DESC sur created_at, pagination simple.
     */
    @GetMapping("/dossiers/{dossierId}/access-log")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    @Operation(summary = "Log d'acces client (drawer Activite) -- 50 dernieres actions paginees",
            description = "Sprint 7 / TASK 5 -- VIEW_DOSSIER / PREVIEW_DOC / DOWNLOAD_DOC / PRINT_DOC")
    public AccessLogPage getAccessLog(
            @PathVariable UUID dossierId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return accessLogQuery.findByDossier(dossierId, limit, offset);
    }

    /**
     * Fix 2026-06-07 (BUG 3) -- Suppression complete d'un dataroom.
     *
     * <p>RBAC EMPLOYE / SUPERVISEUR du workspace courant. Idempotent (204
     * meme si deja supprime). Refus 409 si un ticket actif (NOUVEAU/EN_COURS)
     * reference encore le dossier.
     */
    @DeleteMapping("/dossiers/{dossierId}")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    @Operation(summary = "Supprime un dataroom (destructif, idempotent)",
            description = "Supprime dataroom_settings + tous les documents/demandes/snapshots/access-log/exercices/echeances/alertes "
                    + "rattaches. Le dossier passe en RADIE si des tickets historiques le referencent, "
                    + "sinon il est DELETE physique. Refuse 409 si un ticket actif (NOUVEAU/EN_COURS) "
                    + "est encore rattache.")
    public ResponseEntity<Void> deleteDataroom(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable UUID dossierId,
                                                HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");
        deleteDataroom.execute(new DeleteDataroomUseCase.Command(
                user.workspaceId(), dossierId, user.userId(), ip, ua));
        return ResponseEntity.noContent().build();
    }

    private SettingsView toView(SettingsEntity s) {
        // 2026-07-04 -- "Acces client" = NOMBRE DE CLIENTS ayant acces au dossier
        // (0 ou 1 : un seul client par dataroom), et NON le nombre de consultations.
        // lastAccessedAt reste la date reelle du dernier acces (derivee du log).
        int clientsWithAccess = accessLogQuery.clientCount(s.getDossierId());
        var stats = accessLogQuery.clientAccessStats(s.getDossierId());
        return new SettingsView(s.getDossierId(), s.getAccessStatus(),
                s.isPermDownload(), s.isPermPrint(), s.isPermDepot(), s.getClientLinkToken(),
                clientsWithAccess, stats.lastAt(),
                s.getAccountantEmail(), s.isNotifyAccountantOnUpload());
    }
}
