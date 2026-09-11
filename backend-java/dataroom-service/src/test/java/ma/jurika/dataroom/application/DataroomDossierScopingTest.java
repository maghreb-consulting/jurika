package ma.jurika.dataroom.application;

import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SnapshotJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scoping "un employe ne voit que SES dossiers" (2026-07-03).
 *
 * <p>Un nouvel employe ne doit plus voir les Data Rooms de l'ancien employe :
 * la liste des dossiers d'un EMPLOYE est filtree par
 * {@code entreprise_dossiers.responsable_id = userId}. SUPERVISEUR / SUPER_ADMIN
 * gardent la vue globale du workspace ; CLIENT reste filtre par client_id.
 */
@ExtendWith(MockitoExtension.class)
class DataroomDossierScopingTest {

    @Mock private DocumentJpaRepository documentRepo;
    @Mock private DossierViewJpaRepository dossierRepo;
    @Mock private TicketViewJpaRepository ticketRepo;
    @Mock private SnapshotJpaRepository snapshotRepo;
    @Mock private ObjectStorage storage;
    @Mock private DataroomEventPublisher events;
    @Mock private BusinessMetrics metrics;

    private DataroomJuridiqueService service() {
        return new DataroomJuridiqueService(
                documentRepo, dossierRepo, ticketRepo,
                org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                snapshotRepo, storage, events, metrics,
                org.mockito.Mockito.mock(DossierArchiveGuard.class),
                // Lot B — le journal des changements de visibilite client (V32).
                org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence
                        .VisibiliteEvenementJpaRepository.class));
    }

    private static DossierViewEntity dossierOwnedBy(UUID responsableId) {
        // lenient : selon le test, getId()/getResponsableId() ne sont pas
        // forcement lus (containsExactly compare par reference) -> on evite
        // UnnecessaryStubbingException sous la strictness Mockito par defaut.
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        org.mockito.Mockito.lenient().when(d.getId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.lenient().when(d.getResponsableId()).thenReturn(responsableId);
        return d;
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void employe_neVoitPas_lesDossiersDunAutreEmploye() {
        UUID ws = UUID.randomUUID();
        UUID employeA = UUID.randomUUID();
        UUID employeB = UUID.randomUUID();
        TenantContext.set(ws);

        // Le repo scope A -> ne renvoie QUE le dossier de A ; celui de B n'y est pas.
        DossierViewEntity dossierDeA = dossierOwnedBy(employeA);
        when(dossierRepo.findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, employeA, "RADIE"))
                .thenReturn(List.of(dossierDeA));

        List<DossierViewEntity> vus = service().listDossiers(Role.EMPLOYE, employeA);

        assertThat(vus).containsExactly(dossierDeA);
        assertThat(vus).noneMatch(d -> employeB.equals(d.getResponsableId()));
        // La variante workspace-wide (fuite cross-employe) NE doit PAS etre appelee.
        verify(dossierRepo, never()).findAllByWorkspaceIdAndStatutNot(any(), any());
        verify(dossierRepo, times(1))
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, employeA, "RADIE");
    }

    @Test
    void superviseur_voitTousLesDossiersDuWorkspace() {
        UUID ws = UUID.randomUUID();
        UUID superviseur = UUID.randomUUID();
        TenantContext.set(ws);

        DossierViewEntity d1 = dossierOwnedBy(UUID.randomUUID());
        DossierViewEntity d2 = dossierOwnedBy(UUID.randomUUID());
        when(dossierRepo.findAllByWorkspaceIdAndStatutNot(ws, "RADIE"))
                .thenReturn(List.of(d1, d2));

        List<DossierViewEntity> vus = service().listDossiers(Role.SUPERVISEUR, superviseur);

        assertThat(vus).containsExactly(d1, d2);
        // Le superviseur ne doit PAS etre restreint a ses propres dossiers.
        verify(dossierRepo, never())
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(any(), any(), any());
        verify(dossierRepo, times(1)).findAllByWorkspaceIdAndStatutNot(ws, "RADIE");
    }

    @Test
    void superAdmin_voitTousLesDossiersDuWorkspace() {
        UUID ws = UUID.randomUUID();
        TenantContext.set(ws);
        when(dossierRepo.findAllByWorkspaceIdAndStatutNot(ws, "RADIE")).thenReturn(List.of());

        service().listDossiers(Role.SUPER_ADMIN, UUID.randomUUID());

        verify(dossierRepo, never())
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(any(), any(), any());
        verify(dossierRepo, times(1)).findAllByWorkspaceIdAndStatutNot(ws, "RADIE");
    }

    @Test
    void client_estFiltreParClientId() {
        UUID ws = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        TenantContext.set(ws);

        DossierViewEntity sien = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(sien.getClientId()).thenReturn(client);
        DossierViewEntity autre = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(autre.getClientId()).thenReturn(UUID.randomUUID());
        when(dossierRepo.findAllByWorkspaceIdAndStatutNot(ws, "RADIE"))
                .thenReturn(List.of(sien, autre));

        List<DossierViewEntity> vus = service().listDossiers(Role.CLIENT, client);

        assertThat(vus).containsExactly(sien);
        // Le scoping employe ne doit pas etre utilise pour un CLIENT.
        verify(dossierRepo, never())
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(any(), any(), any());
    }

    @Test
    void employe_sansIdentite_neVoitRien_failClosed() {
        UUID ws = UUID.randomUUID();
        TenantContext.set(ws);

        assertThat(service().listDossiers(Role.EMPLOYE, null)).isEmpty();

        verify(dossierRepo, never()).findAllByWorkspaceIdAndStatutNot(any(), any());
        verify(dossierRepo, never())
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(any(), any(), any());
    }

    @Test
    void sansTenant_retourVide_jamaisDeFuiteGlobale() {
        TenantContext.clear();

        assertThat(service().listDossiers(Role.EMPLOYE, UUID.randomUUID())).isEmpty();
        assertThat(service().listDossiers(Role.SUPERVISEUR, UUID.randomUUID())).isEmpty();

        verify(dossierRepo, never()).findAllByWorkspaceIdAndStatutNot(any(), any());
        verify(dossierRepo, never())
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(any(), any(), any());
    }
}
