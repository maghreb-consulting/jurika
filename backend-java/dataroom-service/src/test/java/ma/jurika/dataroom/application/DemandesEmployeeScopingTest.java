package ma.jurika.dataroom.application;

import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DemandeSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdateDemandeStatusRequest;
import ma.jurika.dataroom.infrastructure.persistence.DemandeEntity;
import ma.jurika.dataroom.infrastructure.persistence.DemandeJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scoping des demandes client par responsable (2026-07-03).
 *
 * <p>Bug corrige : apres transfert d'un dossier a un NOUVEL employe
 * ({@code entreprise_dossiers.responsable_id} mis a jour), ce nouvel employe ne
 * voyait/traitait PAS les demandes du dossier (l'ancien endpoint listait tout le
 * workspace, sans filtre responsable). Desormais :
 * <ul>
 *   <li>{@code listAllForEmployee} ne renvoie que les demandes des dossiers dont
 *       l'employe est responsable ;</li>
 *   <li>{@code updateStatus} refuse (403) le traitement par un EMPLOYE qui n'est
 *       pas le responsable du dossier ;</li>
 *   <li>SUPERVISEUR / SUPER_ADMIN gardent la vue globale ({@code listAll}).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DemandesEmployeeScopingTest {

    @Mock private DemandeJpaRepository demandeRepo;
    @Mock private DossierViewJpaRepository dossierRepo;
    @Mock private ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository userRepo;
    @Mock private ma.jurika.dataroom.application.access.ClientAccessLogger accessLogger;
    @Mock private ma.jurika.common.notification.NotificationPublisher notifier;

    private DemandesClientService service() {
        return new DemandesClientService(demandeRepo, dossierRepo, userRepo, accessLogger, notifier,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static DossierViewEntity dossierWithId(UUID id) {
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        org.mockito.Mockito.lenient().when(d.getId()).thenReturn(id);
        return d;
    }

    private static DemandeEntity demandeOnDossier(UUID dossierId) {
        DemandeEntity e = new DemandeEntity();
        e.setId(UUID.randomUUID());
        e.setDossierId(dossierId);
        e.setStatut("NON_TRAITEE");
        e.setSujet("sujet");
        e.setDescription("desc");
        return e;
    }

    // ─── listAllForEmployee ───────────────────────────────────────────────

    @Test
    void listAllForEmployee_scopesToResponsableDossiers() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);

        DossierViewEntity d = dossierWithId(dossier);
        when(dossierRepo.findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, employe, "RADIE"))
                .thenReturn(List.of(d));
        when(demandeRepo.findAllByWorkspaceIdAndDossierIdInOrderByCreatedAtDesc(eq(ws), any()))
                .thenReturn(List.of(demandeOnDossier(dossier)));

        List<DemandeSummary> vus = service().listAllForEmployee(employe, null);

        assertThat(vus).hasSize(1);
        assertThat(vus.get(0).dossierId()).isEqualTo(dossier);
        // La liste workspace-wide (fuite cross-employe) NE doit PAS etre appelee.
        verify(demandeRepo, never()).findAllByWorkspaceIdOrderByCreatedAtDesc(any());
        verify(demandeRepo, never()).findAllByWorkspaceIdAndStatutOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listAllForEmployee_withStatut_usesStatutFinder() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);

        DossierViewEntity d = dossierWithId(dossier);
        when(dossierRepo.findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, employe, "RADIE"))
                .thenReturn(List.of(d));
        when(demandeRepo.findAllByWorkspaceIdAndDossierIdInAndStatutOrderByCreatedAtDesc(
                eq(ws), any(), eq("EN_COURS")))
                .thenReturn(List.of());

        service().listAllForEmployee(employe, "EN_COURS");

        verify(demandeRepo, times(1)).findAllByWorkspaceIdAndDossierIdInAndStatutOrderByCreatedAtDesc(
                eq(ws), any(), eq("EN_COURS"));
        verify(demandeRepo, never()).findAllByWorkspaceIdAndDossierIdInOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listAllForEmployee_noDossier_returnsEmpty_neverQueriesDemandes() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        TenantContext.set(ws);
        when(dossierRepo.findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, employe, "RADIE"))
                .thenReturn(List.of());

        assertThat(service().listAllForEmployee(employe, null)).isEmpty();

        // Aucun IN () invalide : on court-circuite avant de toucher au repo demandes.
        verify(demandeRepo, never()).findAllByWorkspaceIdAndDossierIdInOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listAllForEmployee_failClosed_whenNoTenantOrNoIdentity() {
        TenantContext.clear();
        assertThat(service().listAllForEmployee(UUID.randomUUID(), null)).isEmpty();

        TenantContext.set(UUID.randomUUID());
        assertThat(service().listAllForEmployee(null, null)).isEmpty();

        verify(dossierRepo, never())
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(any(), any(), any());
    }

    // ─── updateStatus ownership ───────────────────────────────────────────

    @Test
    void updateStatus_employeResponsable_treats() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID demandeId = UUID.randomUUID();
        TenantContext.set(ws);

        DemandeEntity e = demandeOnDossier(dossier);
        e.setId(demandeId);
        e.setWorkspaceId(ws);
        when(demandeRepo.findByWorkspaceIdAndId(ws, demandeId)).thenReturn(Optional.of(e));

        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getResponsableId()).thenReturn(employe);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        when(demandeRepo.save(any())).thenReturn(e);

        DemandeSummary out = service().updateStatus(
                demandeId, new UpdateDemandeStatusRequest("EN_COURS", null, null),
                employe, Role.EMPLOYE);

        assertThat(out.statut()).isEqualTo("EN_COURS");
        assertThat(out.prisEnChargePar()).isEqualTo(employe);
        verify(demandeRepo, times(1)).save(any());
    }

    @Test
    void updateStatus_autreEmploye_forbidden_403() {
        UUID ws = UUID.randomUUID();
        UUID responsable = UUID.randomUUID();
        UUID autreEmploye = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID demandeId = UUID.randomUUID();
        TenantContext.set(ws);

        DemandeEntity e = demandeOnDossier(dossier);
        e.setId(demandeId);
        e.setWorkspaceId(ws);
        when(demandeRepo.findByWorkspaceIdAndId(ws, demandeId)).thenReturn(Optional.of(e));

        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getResponsableId()).thenReturn(responsable);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service().updateStatus(
                demandeId, new UpdateDemandeStatusRequest("EN_COURS", null, null),
                autreEmploye, Role.EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);

        // Un traitement refuse ne persiste rien.
        verify(demandeRepo, never()).save(any());
    }

    @Test
    void updateStatus_superAdmin_notBlockedByOwnership() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID demandeId = UUID.randomUUID();
        TenantContext.set(ws);

        DemandeEntity e = demandeOnDossier(dossier);
        e.setId(demandeId);
        e.setWorkspaceId(ws);
        when(demandeRepo.findByWorkspaceIdAndId(ws, demandeId)).thenReturn(Optional.of(e));
        when(demandeRepo.save(any())).thenReturn(e);

        service().updateStatus(
                demandeId, new UpdateDemandeStatusRequest("TRAITEE", null, null),
                UUID.randomUUID(), Role.SUPER_ADMIN);

        // Pas de resolution du responsable pour un SUPER_ADMIN.
        verify(dossierRepo, never()).findByWorkspaceIdAndId(any(), any());
        verify(demandeRepo, times(1)).save(any());
    }

    // ─── listAllEnriched (vue SUPERVISEUR : raisonSociale + nom employe) ──────

    @Test
    void listAllEnriched_resolvesRaisonSocialeAndResponsableName_byJoin() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID responsable = UUID.randomUUID();
        TenantContext.set(ws);

        DemandeEntity e = demandeOnDossier(dossier);
        when(demandeRepo.findAllByWorkspaceIdOrderByCreatedAtDesc(ws))
                .thenReturn(List.of(e));

        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getId()).thenReturn(dossier);
        when(d.getRaisonSociale()).thenReturn("ACME SARL");
        when(d.getResponsableId()).thenReturn(responsable);
        when(dossierRepo.findAllByWorkspaceId(ws)).thenReturn(List.of(d));

        var u = org.mockito.Mockito.mock(
                ma.jurika.dataroom.infrastructure.persistence.UserViewEntity.class);
        when(u.getId()).thenReturn(responsable);
        when(u.displayName()).thenReturn("Karim Benani");
        when(userRepo.findAllByWorkspaceIdAndIdIn(eq(ws), any())).thenReturn(List.of(u));

        var rows = service().listAllEnriched("CLIENT_TO_EMPLOYE", null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).raisonSociale()).isEqualTo("ACME SARL");
        assertThat(rows.get(0).responsableNom()).isEqualTo("Karim Benani");
        assertThat(rows.get(0).dossierId()).isEqualTo(dossier);
    }

    @Test
    void listAllEnriched_filtersByDirection() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);

        DemandeEntity demande = demandeOnDossier(dossier);          // CLIENT_TO_EMPLOYE (defaut)
        DemandeEntity requete = demandeOnDossier(dossier);
        requete.setDirection("EMPLOYE_TO_CLIENT");
        when(demandeRepo.findAllByWorkspaceIdOrderByCreatedAtDesc(ws))
                .thenReturn(List.of(demande, requete));
        when(dossierRepo.findAllByWorkspaceId(ws)).thenReturn(List.of());

        var demandes = service().listAllEnriched("CLIENT_TO_EMPLOYE", null);
        var requetes = service().listAllEnriched("EMPLOYE_TO_CLIENT", null);

        assertThat(demandes).hasSize(1);
        assertThat(demandes.get(0).direction()).isEqualTo("CLIENT_TO_EMPLOYE");
        assertThat(requetes).hasSize(1);
        assertThat(requetes.get(0).direction()).isEqualTo("EMPLOYE_TO_CLIENT");
    }

    @Test
    void listAllEnriched_noTenant_returnsEmpty() {
        TenantContext.clear();
        assertThat(service().listAllEnriched("CLIENT_TO_EMPLOYE", null)).isEmpty();
        verify(demandeRepo, never()).findAllByWorkspaceIdOrderByCreatedAtDesc(any());
    }
}
