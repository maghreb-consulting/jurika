package ma.jurika.dashboard.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.common.trial.TrialSoftLockFilter;
import ma.jurika.dashboard.application.DashboardQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Decision du 2026-10-14 (RG-ESS-05) : dashboard-service porte de nouveau le filtre commun de
 * l'essai ; pendant l'essai, l'export CSV (fichier) est refuse ; hors essai, inchange.
 */
class ExportEssaiTest {

    private static final UUID WS_ESSAI = UUID.randomUUID();

    @AfterEach
    void vider() {
        TenantContext.clear();
    }

    @SuppressWarnings("unchecked")
    private MockMvc mvc() {
        TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
                WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE" : "CONVERTED", 3, Instant.now(), null));
        DashboardController c = new DashboardController(mock(DashboardQueryService.class), mock(ObjectProvider.class));
        return MockMvcBuilders.standaloneSetup(c)
                .addFilters(new TrialSoftLockFilter(checker, new ObjectMapper())).build();
    }

    @Test
    void essai_export_csv_refuse() throws Exception {
        TenantContext.set(WS_ESSAI);
        MvcResult r = mvc().perform(get("/api/v1/dashboards/export/csv").param("metric", "inconnue")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(402);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("day,count");
    }

    @Test
    void cabinet_abonne_export_csv_inchange() throws Exception {
        TenantContext.set(UUID.randomUUID());
        MvcResult r = mvc().perform(get("/api/v1/dashboards/export/csv").param("metric", "inconnue")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).startsWith("day,count");
    }
}
