package ma.jurika.supervision.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test pour la fuite cross-tenant decouverte 2026-06-05 :
 * {@code jurika_user} ayant {@code BYPASSRLS=true} sous le conteneur postgres
 * officiel, les COUNT(*) sans WHERE workspace_id retournaient les totaux
 * globaux dans le dashboard. Ce test fige le contrat : chaque requete native
 * passe par {@code setParameter(1, workspaceId)} pour filtrer explicitement.
 */
@ExtendWith(MockitoExtension.class)
class SupervisionServiceWorkspaceFilterTest {

    @Mock
    private EntityManager em;

    @Mock
    private Query query;

    @InjectMocks
    private SupervisionService service;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void workspaceKpis_returnsEmptyMap_whenNoTenant() {
        TenantContext.clear();
        Map<String, Object> kpis = service.workspaceKpis();
        assertThat(kpis).isEmpty();
    }

    @Test
    void workspaceKpis_appliesWorkspaceIdToEveryQuery() {
        UUID ws = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TenantContext.set(ws);

        // Make every native query return a single numeric value (count = 1).
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getResultList()).thenReturn(List.of((Object) Integer.valueOf(1)));

        Map<String, Object> kpis = service.workspaceKpis();

        // 9 KPIs scalaires interroges (tickets total + 4 statuts + dossiers
        // total + dossiers actifs + debours + demandes non traitees).
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(em, atLeastOnce()).createNativeQuery(sqlCap.capture());
        for (String sql : sqlCap.getAllValues()) {
            assertThat(sql)
                    .as("requete '%s' doit filtrer par workspace_id", sql)
                    .contains("workspace_id");
        }

        ArgumentCaptor<Object> paramCap = ArgumentCaptor.forClass(Object.class);
        verify(query, atLeastOnce()).setParameter(anyInt(), paramCap.capture());
        assertThat(paramCap.getAllValues()).contains(ws);

        // Le payload expose dossiersActifs + ticketsNouveaux + ticketsAnnules
        // (nouveau contrat post-fix RG-DASH multi-tenant).
        assertThat(kpis).containsKeys(
                "tickets", "ticketsNouveaux", "ticketsEnCours", "ticketsClotures",
                "ticketsAnnules", "dossiers", "dossiersActifs",
                "deboursTotalMad", "demandesNonTraitees", "computedAt");
        assertThat(kpis.get("deboursTotalMad")).isInstanceOf(BigDecimal.class);
    }

    @Test
    void ticketsPerType_isWorkspaceScoped_whenTenantSet() {
        UUID ws = UUID.randomUUID();
        TenantContext.set(ws);

        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        service.ticketsPerType();

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCap.capture());
        assertThat(sqlCap.getValue()).contains("workspace_id = ?1");
    }

    @Test
    void ticketsPerStatut_returnsEmptyList_whenNoTenant() {
        TenantContext.clear();
        assertThat(service.ticketsPerStatut()).isEmpty();
        // Verifie qu'on n'a meme pas tente de querier sans tenant
        verify(em, org.mockito.Mockito.never()).createNativeQuery(anyString());
    }

    @Test
    void ticketsPerEmploye_returnsEmptyList_whenNoTenant() {
        TenantContext.clear();
        assertThat(service.ticketsPerEmploye()).isEmpty();
        verify(em, org.mockito.Mockito.never()).createNativeQuery(anyString());
    }

    @Test
    void ticketsLast30Days_returnsEmptyList_whenNoTenant() {
        TenantContext.clear();
        assertThat(service.ticketsLast30Days()).isEmpty();
        verify(em, org.mockito.Mockito.never()).createNativeQuery(anyString());
    }
}
