package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.AccessLogPage;
import ma.jurika.dataroom.api.dto.DataroomDtos.ClientLinkResponse;
import ma.jurika.dataroom.api.dto.DataroomDtos.ClientPermissionsView;
import ma.jurika.dataroom.api.dto.DataroomDtos.SettingsView;
import ma.jurika.dataroom.api.dto.DataroomDtos.ToggleSuspensionRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdatePermissionsRequest;
import ma.jurika.dataroom.application.DataroomSettingsService;
import ma.jurika.dataroom.application.EmployeDataroomGuard;
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

    @Value("${jurika.dataroom.client-link-base-url:http://localhost:5173/client/dataroom}")
    private String clientLinkBaseUrl;

    private final EmployeDataroomGuard employeGuard;

    public SettingsController(DataroomSettingsService settings,
                              ClientAccessLogQueryService accessLogQuery,
                              EmployeDataroomGuard employeGuard) {
        this.settings = settings;
        this.accessLogQuery = accessLogQuery;
        this.employeGuard = employeGuard;
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
                s.isPermDownload(), s.isPermPrint(), s.isPermDepot(),
                s.isPermConsultation(), s.isPermDemandes());
    }

    // Lot L0 (E4) : gestion de l'acces client ouverte EXPLICITEMENT au
    // superviseur (RG-CLI-01, CDC section 3.2), independamment de la
    // hierarchie de roles.
    @PatchMapping("/dossiers/{dossierId}/settings/permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public SettingsView updatePermissions(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID dossierId,
                                           @Valid @RequestBody UpdatePermissionsRequest req) {
        // Lot L1 (RG-CLI-01) : l'employe responsable du dossier, ou le superviseur.
        employeGuard.assertResponsable(dossierId, user);
        return toView(settings.updatePermissions(dossierId, user.userId(), req.permDownload(), req.permPrint(),
                req.permDepot(), req.permConsultation(), req.permDemandes()));
    }

    @PatchMapping("/dossiers/{dossierId}/settings/suspension")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public SettingsView toggleSuspension(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable UUID dossierId,
                                          @Valid @RequestBody ToggleSuspensionRequest req) {
        employeGuard.assertResponsable(dossierId, user); // Lot L1, RG-DOS-01
        return toView(settings.toggleSuspension(dossierId, req.suspended()));
    }

    @PostMapping("/dossiers/{dossierId}/settings/regenerate-link")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public ClientLinkResponse regenerateLink(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID dossierId) {
        employeGuard.assertResponsable(dossierId, user); // Lot L1, RG-DOS-01
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

    // Lot L1 : la suppression d'une Data Room (DELETE /dossiers/{dossierId}, destruction
    // physique des documents et du dossier) est retiree : absente du CDC (3.2), contraire a
    // la conservation de dix ans (RG-DP-03), et bloquee par les cles RESTRICT de V28.

    private SettingsView toView(SettingsEntity s) {
        // 2026-07-04 -- "Acces client" = NOMBRE DE CLIENTS ayant acces au dossier
        // (0 ou 1 : un seul client par dataroom), et NON le nombre de consultations.
        // lastAccessedAt reste la date reelle du dernier acces (derivee du log).
        int clientsWithAccess = accessLogQuery.clientCount(s.getDossierId());
        var stats = accessLogQuery.clientAccessStats(s.getDossierId());
        return new SettingsView(s.getDossierId(), s.getAccessStatus(),
                s.isPermDownload(), s.isPermPrint(), s.isPermDepot(), s.getClientLinkToken(),
                clientsWithAccess, stats.lastAt(), s.isPermConsultation(), s.isPermDemandes());
    }
}
