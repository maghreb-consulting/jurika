package ma.jurika.billing.application.abonnement;

import ma.jurika.billing.api.dto.BillingDtos.AbonnementVue;
import ma.jurika.billing.api.dto.BillingDtos.FonctionVue;
import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.infrastructure.cmi.CmiProperties;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.EditeurParametresJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.common.trial.TrialAccessChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Recette R1 (point 3), RG-ESS-07 : pendant l'essai, l'ecran de facturation affiche le forfait
 * "Essai gratuit (fonctions du forfait Professionnel)" et les fonctions du forfait Professionnel ;
 * avant, "aucun forfait" et toutes les fonctions refusees (la messagerie etait bloquee a l'ecran).
 */
class AbonnementEssaiTest {

    static final UUID WS = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private AbonnementService service(String statutEssai) {
        SubscriptionJpaRepository abonnements = mock(SubscriptionJpaRepository.class);
        when(abonnements.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(abonnements.findFirstByWorkspaceIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        PaymentJpaRepository paiements = mock(PaymentJpaRepository.class);
        when(paiements.findAllByWorkspaceIdAndStatus(any(), any())).thenReturn(List.of());
        TrialAccessChecker essais = id -> Optional.of(new TrialAccessChecker.TrialState(
                id, statutEssai, 5, null, null));
        ObjectProvider<TrialAccessChecker> fournisseur = mock(ObjectProvider.class);
        when(fournisseur.getIfAvailable()).thenReturn(essais);
        return new AbonnementService(abonnements, paiements, mock(EditeurParametresJpaRepository.class),
                mock(CabinetsPort.class), mock(ObjectProvider.class), mock(CmiProperties.class),
                mock(FacturationHorloge.class), mock(ImpayeJournal.class), fournisseur);
    }

    @Test
    void pendant_l_essai_le_forfait_affiche_est_l_essai_avec_les_fonctions_du_professionnel() {
        AbonnementVue v = service("TRIAL_ACTIVE").etat(WS);
        assertThat(v.statut()).isEqualTo("AUCUN");
        assertThat(v.forfait()).isEqualTo("essai");
        assertThat(v.forfaitLibelle()).isEqualTo("Essai gratuit (fonctions du forfait Professionnel)");
        assertThat(v.fonctions()).filteredOn(f -> f.code().equals("MESSAGERIE_INTERNE"))
                .extracting(FonctionVue::incluse).containsExactly(true);
    }

    @Test
    void sans_essai_ni_abonnement_aucun_forfait() {
        AbonnementVue v = service("CONVERTED").etat(WS);
        assertThat(v.forfait()).isNull();
        assertThat(v.forfaitLibelle()).isNull();
    }
}
