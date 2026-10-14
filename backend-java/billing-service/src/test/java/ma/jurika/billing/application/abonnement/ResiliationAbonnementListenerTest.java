package ma.jurika.billing.application.abonnement;

import ma.jurika.billing.application.contrat.ContratEvenements;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (integration E5 / E6) : une resiliation (RG-CTR-08) arrete le forfait a sa date d'effet ;
 * l'archivage planifie fait le reste (motif RESILIATION), jamais une suppression.
 */
class ResiliationAbonnementListenerTest {

    private static final UUID WS = UUID.randomUUID();

    private static PlatformTransactionManager tm() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return tm;
    }

    private static ContratEvenements.ResiliationEnregistree resiliation(LocalDate effet) {
        return new ContratEvenements.ResiliationEnregistree(WS, UUID.randomUUID(), effet, "Cessation", "SUPERVISEUR");
    }

    @Test
    void la_periode_payee_est_bornee_a_la_date_d_effet_et_le_renouvellement_coupe() {
        SubscriptionJpaRepository repo = mock(SubscriptionJpaRepository.class);
        SubscriptionEntity s = new SubscriptionEntity();
        s.periode(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));
        when(repo.verrouillerActif(WS)).thenReturn(Optional.of(s));

        new ResiliationAbonnementListener(repo, tm()).surResiliation(resiliation(LocalDate.of(2027, 3, 1)));

        assertThat(s.isCancelAtPeriodEnd()).isTrue();
        assertThat(s.getPeriodeFin()).isEqualTo(LocalDate.of(2027, 3, 1));
        verify(repo).save(s);
    }

    @Test
    void une_date_d_effet_apres_la_periode_payee_ne_la_prolonge_pas() {
        SubscriptionJpaRepository repo = mock(SubscriptionJpaRepository.class);
        SubscriptionEntity s = new SubscriptionEntity();
        s.periode(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        when(repo.verrouillerActif(WS)).thenReturn(Optional.of(s));

        new ResiliationAbonnementListener(repo, tm()).surResiliation(resiliation(LocalDate.of(2027, 11, 1)));

        assertThat(s.isCancelAtPeriodEnd()).isTrue();
        assertThat(s.getPeriodeFin()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void sans_abonnement_actif_rien_n_est_ecrit() {
        SubscriptionJpaRepository repo = mock(SubscriptionJpaRepository.class);
        when(repo.verrouillerActif(WS)).thenReturn(Optional.empty());
        new ResiliationAbonnementListener(repo, tm()).surResiliation(resiliation(LocalDate.of(2027, 3, 1)));
        verify(repo, never()).save(any());
    }
}
