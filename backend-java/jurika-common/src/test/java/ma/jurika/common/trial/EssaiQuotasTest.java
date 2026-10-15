package ma.jurika.common.trial;

import ma.jurika.common.exception.PlanLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Lot L7 (E4), RG-ESS-04 : limites de l'essai ; configuration du filtre (motif 9). */
class EssaiQuotasTest {

    private static final UUID WS = UUID.randomUUID();
    private String statut = "TRIAL_ACTIVE";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EssaiQuotas quotas = new EssaiQuotas(ws -> Optional.of(
            new TrialAccessChecker.TrialState(ws, statut, 3, Instant.now(), null)), jdbc);

    @BeforeEach
    void transaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void fin() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void compte(String fragment, long n) {
        when(jdbc.queryForObject(contains(fragment), eq(Long.class), any(Object[].class))).thenReturn(n);
    }

    @Test
    void troisieme_dossier_permis_quatrieme_refuse() {
        compte("entreprise_dossiers", 2);
        quotas.verifierNouveauDossier(WS);
        compte("entreprise_dossiers", 3);
        assertThatThrownBy(() -> quotas.verifierNouveauDossier(WS))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("3 dossiers")
                .satisfies(e -> assertThat(((PlanLimitException) e).code()).isEqualTo(EssaiQuotas.CODE_DOSSIERS));
    }

    @Test
    void un_seul_employe() {
        compte("role = 'EMPLOYE'", 0);
        quotas.verifierNouvelEmploye(WS);
        compte("role = 'EMPLOYE'", 1);
        assertThatThrownBy(() -> quotas.verifierNouvelEmploye(WS)).hasMessageContaining("1 employ\u00e9");
    }

    @Test
    void stockage_500_mo() {
        compte("dataroom_documents", 400L * 1024 * 1024);
        compte("dataroom_depots", 50L * 1024 * 1024);
        quotas.verifierStockage(WS, 50L * 1024 * 1024);
        assertThatThrownBy(() -> quotas.verifierStockage(WS, 50L * 1024 * 1024 + 1))
                .hasMessageContaining("500 Mo").hasMessageContaining("450 Mo");
    }

    @Test
    void hors_essai_aucun_comptage() {
        statut = "CONVERTED";
        quotas.verifierNouveauDossier(WS);
        quotas.verifierStockage(WS, Long.MAX_VALUE);
        verifyNoInteractions(jdbc);
    }

    @Test
    void hors_transaction_refuse_plutot_que_compter_zero() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(() -> quotas.verifierNouveauDossier(WS)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filtre_de_l_essai_jamais_enregistre_comme_filtre_de_servlet() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EssaiAutoConfiguration.class))
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .withBean(JdbcTemplate.class, () -> jdbc)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TrialAccessChecker.class);
                    assertThat(ctx).hasSingleBean(TrialSoftLockFilter.class);
                    assertThat(ctx).hasSingleBean(EssaiQuotas.class);
                    FilterRegistrationBean<?> r = ctx.getBean(FilterRegistrationBean.class);
                    assertThat(r.getFilter()).isInstanceOf(TrialSoftLockFilter.class);
                    assertThat(r.isEnabled()).isFalse();
                });
    }
}
