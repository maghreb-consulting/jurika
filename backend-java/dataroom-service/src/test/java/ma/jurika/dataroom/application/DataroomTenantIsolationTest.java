package ma.jurika.dataroom.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.AccessLogPage;
import ma.jurika.dataroom.application.access.ClientAccessLogQueryService;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DemandeEntity;
import ma.jurika.dataroom.infrastructure.persistence.DemandeJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SnapshotJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

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
 * Regression tests pour la fuite cross-tenant 2026-06-05 : sous le conteneur
 * postgres officiel, {@code jurika_user} a {@code BYPASSRLS} et la RLS Postgres
 * NE filtre PAS par workspace. Les queries des repositories dataroom qui ne
 * filtraient que par {@code dossier_id} ou {@code ticket_id} pouvaient fuiter
 * cross-tenant si l'attaquant devinait un UUID.
 *
 * Ces tests figent les contrats :
 *   (1) toutes les methodes utilisent la variante {@code findByWorkspaceIdAnd...}
 *   (2) sans TenantContext, retour vide / 404 (jamais de fuite globale)
 *   (3) les variantes {@code findBy<Dossier|Ticket>Id} originelles ne sont
 *       PLUS appelees par le code de production.
 */
@ExtendWith(MockitoExtension.class)
class DataroomTenantIsolationTest {

    @Mock private ClientAccessLogJpaRepository accessLogRepo;
    @Mock private ma.jurika.dataroom.application.access.ClientAccessLogger accessLogger;
    @Mock private DemandeJpaRepository demandeRepo;
    @Mock private DocumentJpaRepository documentRepo;
    @Mock private DossierViewJpaRepository dossierRepo;
    @Mock private TicketViewJpaRepository ticketRepo;
    @Mock private SnapshotJpaRepository snapshotRepo;
    @Mock private ObjectStorage storage;
    @Mock private DataroomEventPublisher events;
    @Mock private BusinessMetrics metrics;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ─── ClientAccessLogQueryService ──────────────────────────────────────

    @Test
    void clientAccessLog_returnsEmpty_whenNoTenant() {
        TenantContext.clear();
        ClientAccessLogQueryService svc = new ClientAccessLogQueryService(accessLogRepo, dossierRepo);

        AccessLogPage page = svc.findByDossier(UUID.randomUUID(), 50, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        // Le repo deprecated NE doit PAS etre appele
        verify(accessLogRepo, never()).findAllByDossierIdOrderByCreatedAtDesc(any(), any());
        // Aucune query client-scoped non plus (fail-closed, pas de resolution client)
        verify(accessLogRepo, never()).findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                any(), any(), any(), any());
        verify(dossierRepo, never()).findByWorkspaceIdAndId(any(), any());
    }

    @Test
    void clientAccessLog_returnsEmpty_whenNoClientAssigned() {
        // 2026-07-01 -- "Activite client" = actions du CLIENT du dossier. Si
        // aucun client n'est rattache (client_id null), la vue est vide par
        // definition (empty-state front "Aucune activite enregistree").
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getClientId()).thenReturn(null);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        ClientAccessLogQueryService svc = new ClientAccessLogQueryService(accessLogRepo, dossierRepo);
        AccessLogPage page = svc.findByDossier(dossier, 50, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        verify(accessLogRepo, never()).findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                any(), any(), any(), any());
    }

    @Test
    void clientAccessLog_filtersToClientOfDossier() {
        // Le drawer "Activite client" doit cibler user_id = client_id du dossier,
        // JAMAIS tous les roles (l'employe/superviseur qui consultent ne sont
        // pas de "l'activite client").
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getClientId()).thenReturn(client);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        Page<ClientAccessLogEntity> empty = new PageImpl<>(List.of());
        when(accessLogRepo.findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                eq(ws), eq(dossier), eq(client), any())).thenReturn(empty);

        ClientAccessLogQueryService svc = new ClientAccessLogQueryService(accessLogRepo, dossierRepo);
        svc.findByDossier(dossier, 50, 0);

        // Ni le repo deprecated ni la variante "tous roles" ne doivent etre appeles
        verify(accessLogRepo, never()).findAllByDossierIdOrderByCreatedAtDesc(any(), any());
        verify(accessLogRepo, never()).findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(
                any(), any(), any());
        verify(accessLogRepo, times(1))
                .findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                        eq(ws), eq(dossier), eq(client), any());
    }

    @Test
    void clientAccessStats_derivesCountAndLastFromClientLog() {
        // Compteur "Acces client" REEL derive du log (source de verite), pas de
        // la colonne access_count (jamais incrementee -> restait a 0).
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        java.time.Instant last = java.time.Instant.parse("2026-07-01T09:15:00Z");
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getClientId()).thenReturn(client);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        when(accessLogRepo.countByWorkspaceIdAndDossierIdAndUserId(ws, dossier, client))
                .thenReturn(3L);
        ClientAccessLogEntity lastEntry = new ClientAccessLogEntity();
        lastEntry.setCreatedAt(last);
        when(accessLogRepo.findFirstByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                ws, dossier, client)).thenReturn(Optional.of(lastEntry));

        ClientAccessLogQueryService svc = new ClientAccessLogQueryService(accessLogRepo, dossierRepo);
        var stats = svc.clientAccessStats(dossier);

        assertThat(stats.count()).isEqualTo(3L);
        assertThat(stats.lastAt()).isEqualTo(last);
    }

    @Test
    void clientAccessStats_zeroWhenNoTenantOrNoClient() {
        // Pas de tenant -> (0, null)
        TenantContext.clear();
        ClientAccessLogQueryService svc = new ClientAccessLogQueryService(accessLogRepo, dossierRepo);
        var noTenant = svc.clientAccessStats(UUID.randomUUID());
        assertThat(noTenant.count()).isZero();
        assertThat(noTenant.lastAt()).isNull();
        verify(accessLogRepo, never()).countByWorkspaceIdAndDossierIdAndUserId(any(), any(), any());
    }

    // ─── DemandesClientService ────────────────────────────────────────────

    @Test
    void demandesListByDossier_returnsEmpty_whenNoTenant() {
        TenantContext.clear();
        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));

        assertThat(svc.listByDossier(UUID.randomUUID(), UUID.randomUUID(), Role.SUPERVISEUR)).isEmpty();

        verify(demandeRepo, never()).findAllByDossierIdOrderByCreatedAtDesc(any());
        verify(demandeRepo, never()).findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void demandesListByDossier_passesWorkspaceIdFromTenantContext() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        when(demandeRepo.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossier))
                .thenReturn(List.of());

        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));
        // SUPERVISEUR : non restreint -> le test cible bien le contrat
        // "findByWorkspaceIdAnd..." sans dependre de l'ownership du dossier.
        svc.listByDossier(dossier, UUID.randomUUID(), Role.SUPERVISEUR);

        verify(demandeRepo, never()).findAllByDossierIdOrderByCreatedAtDesc(any());
        verify(demandeRepo, times(1)).findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossier);
    }

    @Test
    void demandesListByDossier_employeNonResponsable_isDenied() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID responsable = UUID.randomUUID();
        UUID autreEmploye = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getResponsableId()).thenReturn(responsable);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));

        // Un EMPLOYE qui n'est PAS le responsable du dossier -> 403, et ne lit
        // JAMAIS les demandes (la fuite "la demande arrive aux 2 employes").
        assertThatThrownBy(() -> svc.listByDossier(dossier, autreEmploye, Role.EMPLOYE))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(demandeRepo, never())
                .findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void demandesListByDossier_employeResponsable_isAllowed() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID responsable = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getResponsableId()).thenReturn(responsable);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));
        when(demandeRepo.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossier))
                .thenReturn(List.of());

        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));
        svc.listByDossier(dossier, responsable, Role.EMPLOYE); // ne throw pas

        verify(demandeRepo, times(1))
                .findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossier);
    }

    @Test
    void demandesListByDossier_clientForeignDossier_isDenied() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID autreClient = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getClientId()).thenReturn(owner);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));

        assertThatThrownBy(() -> svc.listByDossier(dossier, autreClient, Role.CLIENT))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(demandeRepo, never())
                .findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void demandeUpdateStatus_blockedWhenNoTenant() {
        TenantContext.clear();
        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));

        assertThatThrownBy(() -> svc.updateStatus(
                UUID.randomUUID(),
                new ma.jurika.dataroom.api.dto.DataroomDtos.UpdateDemandeStatusRequest(
                        "EN_COURS", null, null),
                UUID.randomUUID(), Role.SUPER_ADMIN))
                .isInstanceOf(NotFoundException.class);
        verify(demandeRepo, never()).findById(any());
        verify(demandeRepo, never()).findByWorkspaceIdAndId(any(), any());
    }

    @Test
    void demandeUpdateStatus_usesFindByWorkspaceIdAndId() {
        UUID ws = UUID.randomUUID();
        UUID demandeId = UUID.randomUUID();
        TenantContext.set(ws);
        DemandeEntity e = new DemandeEntity();
        e.setId(demandeId);
        e.setWorkspaceId(ws);
        e.setStatut("NON_TRAITEE");
        when(demandeRepo.findByWorkspaceIdAndId(ws, demandeId)).thenReturn(Optional.of(e));
        when(demandeRepo.save(any())).thenReturn(e);

        DemandesClientService svc = new DemandesClientService(demandeRepo, dossierRepo, org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository.class), accessLogger, org.mockito.Mockito.mock(ma.jurika.common.notification.NotificationPublisher.class), org.mockito.Mockito.mock(DossierArchiveGuard.class));
        // Role SUPER_ADMIN : pas de controle d'ownership -> le test cible bien le
        // contrat "findByWorkspaceIdAndId" sans dependre du responsable du dossier.
        svc.updateStatus(
                demandeId,
                new ma.jurika.dataroom.api.dto.DataroomDtos.UpdateDemandeStatusRequest(
                        "EN_COURS", null, null),
                UUID.randomUUID(), Role.SUPER_ADMIN);

        verify(demandeRepo, never()).findById(any());
        verify(demandeRepo, times(1)).findByWorkspaceIdAndId(ws, demandeId);
    }

    // ─── DataroomJuridiqueService.view ────────────────────────────────────

    @Test
    void juridiqueView_blockedWhenNoTenant() {
        TenantContext.clear();
        DataroomJuridiqueService svc = new DataroomJuridiqueService(
                documentRepo, dossierRepo, ticketRepo,
                org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                snapshotRepo, storage, events, metrics,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));

        assertThatThrownBy(() -> svc.view(UUID.randomUUID(), null, null, null))
                .isInstanceOf(NotFoundException.class);
        // findById brut NE doit JAMAIS etre appele
        verify(dossierRepo, never()).findById(any());
        verify(documentRepo, never())
                .findAllByDossierIdAndCurrentTrueOrderByCreatedAtDesc(any());
    }

    @Test
    void juridiqueView_usesWorkspaceScopedQueries() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity entity = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(entity.getId()).thenReturn(dossier);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier))
                .thenReturn(Optional.of(entity));
        when(documentRepo.findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc(
                ws, dossier))
                .thenReturn(List.<DocumentEntity>of());
        when(ticketRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());

        DataroomJuridiqueService svc = new DataroomJuridiqueService(
                documentRepo, dossierRepo, ticketRepo,
                org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                snapshotRepo, storage, events, metrics,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));
        svc.view(dossier, null, null, null);

        // findById brut NE doit JAMAIS etre appele
        verify(dossierRepo, never()).findById(any());
        verify(dossierRepo, times(1)).findByWorkspaceIdAndId(ws, dossier);
        verify(documentRepo, never())
                .findAllByDossierIdAndCurrentTrueOrderByCreatedAtDesc(any());
        verify(documentRepo, times(1))
                .findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc(ws, dossier);
    }

    @Test
    void juridiqueAssertClientAccess_blockedWhenNoTenant() {
        TenantContext.clear();
        DataroomJuridiqueService svc = new DataroomJuridiqueService(
                documentRepo, dossierRepo, ticketRepo,
                org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                snapshotRepo, storage, events, metrics,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));

        assertThatThrownBy(() -> svc.assertClientAccess(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        verify(dossierRepo, never()).findById(any());
    }

    @Test
    void juridiqueAssertClientAccess_usesFindByWorkspaceIdAndId() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        TenantContext.set(ws);
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        when(d.getClientId()).thenReturn(client);
        when(dossierRepo.findByWorkspaceIdAndId(ws, dossier)).thenReturn(Optional.of(d));

        DataroomJuridiqueService svc = new DataroomJuridiqueService(
                documentRepo, dossierRepo, ticketRepo,
                org.mockito.Mockito.mock(ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                snapshotRepo, storage, events, metrics,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));
        svc.assertClientAccess(dossier, client); // ne throw pas

        verify(dossierRepo, never()).findById(any());
        verify(dossierRepo, times(1)).findByWorkspaceIdAndId(ws, dossier);
    }

    // ─── Audit assertion : la signature workspace-scoped existe bien ──────

    @Test
    void repositoryContractsExposeWorkspaceScopedVariants() throws NoSuchMethodException {
        // Fail-fast si quelqu'un retire une de ces methodes : la safety net
        // defense-in-depth tombe sans qu'un test fonctionnel ne le detecte.
        ClientAccessLogJpaRepository.class.getMethod(
                "findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc",
                UUID.class, UUID.class, Pageable.class);
        // 2026-07-01 -- variantes client-scoped ("Activite client" reelle)
        ClientAccessLogJpaRepository.class.getMethod(
                "findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc",
                UUID.class, UUID.class, UUID.class, Pageable.class);
        ClientAccessLogJpaRepository.class.getMethod(
                "countByWorkspaceIdAndDossierIdAndUserId",
                UUID.class, UUID.class, UUID.class);
        DemandeJpaRepository.class.getMethod(
                "findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc",
                UUID.class, UUID.class);
        DemandeJpaRepository.class.getMethod(
                "findByWorkspaceIdAndId", UUID.class, UUID.class);
        DocumentJpaRepository.class.getMethod(
                "findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc",
                UUID.class, UUID.class);
        DocumentJpaRepository.class.getMethod(
                "findAllByWorkspaceIdAndTicketIdOrderByCreatedAtAsc",
                UUID.class, UUID.class);
        DocumentJpaRepository.class.getMethod(
                "findByWorkspaceIdAndId", UUID.class, UUID.class);
        DossierViewJpaRepository.class.getMethod(
                "findByWorkspaceIdAndId", UUID.class, UUID.class);
        SnapshotJpaRepository.class.getMethod(
                "findAllByWorkspaceIdAndTicketId", UUID.class, UUID.class);
    }
}
