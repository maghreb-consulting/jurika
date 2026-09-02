package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierBrief;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sprint 7 / TASK 6.2 -- Refactor : controller dedie a la liste des dossiers.
 * Extrait de l'ancien DataroomController. Single Responsibility.
 *
 * Scoping par role (2026-07-03) :
 *  - CLIENT     : seuls les dossiers attaches a son client_id ;
 *  - EMPLOYE    : seuls SES dossiers (responsable_id = userId) ;
 *  - SUPERVISEUR/SUPER_ADMIN : tous les dossiers du workspace.
 * La logique vit dans DataroomJuridiqueService.listDossiers(role, userId).
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Dossiers", description = "Liste des dossiers accessibles a l'utilisateur")
public class DossiersController {

    private final DataroomJuridiqueService juridique;
    private final SettingsJpaRepository settingsRepo;

    public DossiersController(DataroomJuridiqueService juridique,
                              SettingsJpaRepository settingsRepo) {
        this.juridique = juridique;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping("/dossiers")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Liste les dossiers du workspace (filtre par client si role CLIENT)")
    public List<DossierBrief> listDossiers(@AuthenticationPrincipal AuthenticatedUser user) {
        var list = juridique.listDossiers(user.role(), user.userId());

        // Fix 2026-06-08 — Joint l'accessStatus du dataroom_settings pour
        // permettre au front de classer les dossiers entre ACTIF / SUSPENDU.
        // 1 query batch (findAllById sur les PK dossier_id) au lieu de N+1.
        List<UUID> ids = list.stream().map(d -> d.getId()).toList();
        Map<UUID, String> statusByDossier = ids.isEmpty() ? Map.of()
                : settingsRepo.findAllById(ids).stream()
                        .collect(Collectors.toMap(SettingsEntity::getDossierId,
                                s -> s.getAccessStatus() == null ? "ACTIVE" : s.getAccessStatus()));

        return list.stream().map(d ->
                new DossierBrief(d.getId(), d.getRaisonSociale(), d.getFormeJuridique(),
                        d.getIce(), d.getVille(), d.getStatut(),
                        statusByDossier.getOrDefault(d.getId(), "ACTIVE"),
                        d.getDateDissolution() == null ? null
                                : d.getDateDissolution().toString(),
                        d.getOrigine(), d.getPays(), d.getFormeJuridiqueOrigine())).toList();
    }
}
