package ma.jurika.dataroom.application.access;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Fiabilisation "Activite client vide" (2026-07-03).
 *
 * Prouve que :
 *  (1) une action client resout le workspace SUR LE THREAD REQUETE (via
 *      user.workspaceId()) et persiste une ligne user_id = client -> le compteur
 *      "Acces client" derive de client_access_log se remplit ;
 *  (2) le dispatcher pose bien le TenantContext (RLS) avant l'insert ;
 *  (3) sans workspace resolvable, l'insert est saute (best-effort, jamais NPE).
 */
@ExtendWith(MockitoExtension.class)
class ClientAccessLoggerTest {

    @Mock private ClientAccessLogDispatcher dispatcher;
    @Mock private ClientAccessLogWriter writer;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void clientAction_persistsRowWithClientUserIdAndWorkspace() {
        UUID ws = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AuthenticatedUser client =
                new AuthenticatedUser(clientId, ws, "client@corp.ma", Role.CLIENT);

        ClientAccessLogger logger = new ClientAccessLogger(dispatcher);
        logger.log(dossierId, documentId, "PREVIEW_DOC", client);

        ArgumentCaptor<ClientAccessLogEntity> captor =
                ArgumentCaptor.forClass(ClientAccessLogEntity.class);
        verify(dispatcher, times(1)).persistAsync(captor.capture());
        ClientAccessLogEntity e = captor.getValue();
        // La ligne porte le workspace (jamais null) + user_id = client -> alimente
        // le panneau/compteur qui filtre user_id = dossier.client_id.
        assertThat(e.getWorkspaceId()).isEqualTo(ws);
        assertThat(e.getUserId()).isEqualTo(clientId);
        assertThat(e.getDossierId()).isEqualTo(dossierId);
        assertThat(e.getDocumentId()).isEqualTo(documentId);
        assertThat(e.getAction()).isEqualTo("PREVIEW_DOC");
    }

    @Test
    void log_fallsBackToTenantContext_whenUserNull() {
        UUID ws = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        TenantContext.set(ws);

        ClientAccessLogger logger = new ClientAccessLogger(dispatcher);
        logger.log(dossierId, null, "VIEW_DOSSIER", null);

        ArgumentCaptor<ClientAccessLogEntity> captor =
                ArgumentCaptor.forClass(ClientAccessLogEntity.class);
        verify(dispatcher, times(1)).persistAsync(captor.capture());
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo(ws);
    }

    @Test
    void log_skips_whenNoWorkspaceResolvable() {
        TenantContext.clear();
        ClientAccessLogger logger = new ClientAccessLogger(dispatcher);

        logger.log(UUID.randomUUID(), null, "VIEW_DOSSIER", null);

        verify(dispatcher, never()).persistAsync(any());
    }

    @Test
    void dispatcher_setsTenantContext_thenPersists() {
        UUID ws = UUID.randomUUID();
        ClientAccessLogEntity e = new ClientAccessLogEntity();
        e.setWorkspaceId(ws);
        e.setDossierId(UUID.randomUUID());
        e.setUserId(UUID.randomUUID());
        e.setAction("DOWNLOAD_DOC");

        // Capture le TenantContext AU MOMENT de l'insert : le RlsAspect en depend.
        final UUID[] tenantAtInsert = new UUID[1];
        doAnswer(inv -> {
            tenantAtInsert[0] = TenantContext.get();
            return null;
        }).when(writer).persist(any());

        ClientAccessLogDispatcher d = new ClientAccessLogDispatcher(writer);
        d.persistAsync(e);

        verify(writer, times(1)).persist(e);
        assertThat(tenantAtInsert[0]).isEqualTo(ws);
        // Le contexte est restaure (ici null : aucun tenant pre-existant).
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void dispatcher_swallowsWriterException() {
        ClientAccessLogEntity e = new ClientAccessLogEntity();
        e.setWorkspaceId(UUID.randomUUID());
        e.setAction("PREVIEW_DOC");
        doAnswer(inv -> { throw new RuntimeException("boom RLS"); })
                .when(writer).persist(any());

        ClientAccessLogDispatcher d = new ClientAccessLogDispatcher(writer);
        // Best-effort : ne propage jamais.
        d.persistAsync(e);

        verify(writer, times(1)).persist(e);
    }
}
