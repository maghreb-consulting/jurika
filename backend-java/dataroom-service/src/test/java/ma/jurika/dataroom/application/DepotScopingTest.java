package ma.jurika.dataroom.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DepotSummary;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DepotEntity;
import ma.jurika.dataroom.infrastructure.persistence.DepotJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot V -- scoping de l'espace « Depots » (miroir de
 * {@link DemandesEmployeeScopingTest}).
 *
 * <ul>
 *   <li>CLIENT : voit / telecharge SES depots ; ne voit pas ceux d'un autre dossier (403).</li>
 *   <li>EMPLOYE responsable : voit / telecharge ; EMPLOYE non responsable -> 403 sur list
 *       ET sur download/preview (getForStream).</li>
 *   <li>SUPERVISEUR / SUPER_ADMIN : non restreints (pas de resolution d'ownership).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DepotScopingTest {

    @Mock private DepotJpaRepository repo;
    @Mock private ObjectStorage storage;
    @Mock private DossierViewJpaRepository dossierRepo;
    @Mock private ma.jurika.dataroom.application.access.ClientAccessLogger accessLogger;

    private DataroomDepotService service() {
        return new DataroomDepotService(repo, storage, dossierRepo, accessLogger,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static DepotEntity depotOn(UUID id, UUID dossierId) {
        DepotEntity e = new DepotEntity();
        e.setId(id);
        e.setDossierId(dossierId);
        e.setFilename("piece.pdf");
        e.setTitle("piece.pdf");
        e.setObjectKey("ws/x/dossier/y/depots/1_piece.pdf");
        e.setContentType("application/pdf");
        e.setSizeBytes(10);
        e.setUploadedBy(UUID.randomUUID());
        e.setCreatedAt(Instant.now());
        return e;
    }

    /** Mock construit sur sa propre ligne pour eviter le stubbing imbrique. */
    private static DossierViewEntity dossierWith(UUID clientId, UUID responsableId) {
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        lenient().when(d.getClientId()).thenReturn(clientId);
        lenient().when(d.getResponsableId()).thenReturn(responsableId);
        return d;
    }

    // ─── list ─────────────────────────────────────────────────────────────

    @Test
    void list_clientOwnDossier_ok() {
        UUID ws = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = dossierWith(client, UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        when(repo.findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(dossier))
                .thenReturn(List.of(depotOn(UUID.randomUUID(), dossier)));

        List<DepotSummary> out = service().list(dossier, client, Role.CLIENT);

        assertThat(out).hasSize(1);
    }

    @Test
    void list_clientOtherDossier_forbidden() {
        UUID ws = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        // dossier appartient a un AUTRE client
        DossierViewEntity d = dossierWith(UUID.randomUUID(), UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service().list(dossier, client, Role.CLIENT))
                .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    void list_employeResponsable_ok() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = dossierWith(UUID.randomUUID(), employe);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        when(repo.findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(dossier))
                .thenReturn(List.of());

        assertThat(service().list(dossier, employe, Role.EMPLOYE)).isEmpty();
    }

    @Test
    void list_employeNonResponsable_forbidden() {
        UUID ws = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = dossierWith(UUID.randomUUID(), UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service().list(dossier, autre, Role.EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    void list_superviseur_notRestricted() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        when(repo.findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(dossier))
                .thenReturn(List.of());

        assertThat(service().list(dossier, UUID.randomUUID(), Role.SUPERVISEUR)).isEmpty();
        // Pas de resolution d'ownership pour un superviseur.
        verify(dossierRepo, never()).findByWorkspaceIdAndId(any(), any());
    }

    // ─── getForStream (download / preview) ────────────────────────────────

    @Test
    void getForStream_employeNonResponsable_forbidden() {
        UUID ws = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID depotId = UUID.randomUUID();
        TenantContext.set(ws);
        when(repo.findByIdAndDeletedAtIsNull(depotId))
                .thenReturn(Optional.of(depotOn(depotId, dossier)));
        DossierViewEntity d = dossierWith(UUID.randomUUID(), UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service().getForStream(depotId, autre, Role.EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getForStream_clientOwnDepot_ok() {
        UUID ws = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID depotId = UUID.randomUUID();
        TenantContext.set(ws);
        when(repo.findByIdAndDeletedAtIsNull(depotId))
                .thenReturn(Optional.of(depotOn(depotId, dossier)));
        DossierViewEntity d = dossierWith(client, UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        DepotEntity e = service().getForStream(depotId, client, Role.CLIENT);
        assertThat(e.getId()).isEqualTo(depotId);
    }

    @Test
    void getForStream_missing_404() {
        UUID ws = UUID.randomUUID();
        UUID depotId = UUID.randomUUID();
        TenantContext.set(ws);
        when(repo.findByIdAndDeletedAtIsNull(depotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getForStream(depotId, UUID.randomUUID(), Role.SUPERVISEUR))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── softDelete ───────────────────────────────────────────────────────

    @Test
    void softDelete_clientOwnDepot_ok() {
        UUID ws = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID depotId = UUID.randomUUID();
        TenantContext.set(ws);
        when(repo.findByIdAndDeletedAtIsNull(depotId))
                .thenReturn(Optional.of(depotOn(depotId, dossier)));
        DossierViewEntity d = dossierWith(client, UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        when(repo.softDelete(eq(depotId), any())).thenReturn(1);

        service().softDelete(depotId, client, Role.CLIENT);
        verify(repo).softDelete(eq(depotId), any());
    }

    @Test
    void softDelete_employeNonResponsable_forbidden_noPersist() {
        UUID ws = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID depotId = UUID.randomUUID();
        TenantContext.set(ws);
        when(repo.findByIdAndDeletedAtIsNull(depotId))
                .thenReturn(Optional.of(depotOn(depotId, dossier)));
        DossierViewEntity d = dossierWith(UUID.randomUUID(), UUID.randomUUID());
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service().softDelete(depotId, autre, Role.EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).softDelete(any(), any());
    }
}
