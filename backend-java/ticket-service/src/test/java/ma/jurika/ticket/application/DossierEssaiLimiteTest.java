package ma.jurika.ticket.application;

import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.trial.EssaiQuotas;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DossierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7 (E4), RG-ESS-04 : un 4e dossier est refuse pendant l'essai, avant toute ecriture. */
class DossierEssaiLimiteTest {

    @Test
    @SuppressWarnings("unchecked")
    void quatrieme_dossier_refuse_pendant_l_essai() {
        UUID ws = UUID.randomUUID();
        DossierRepository repo = mock(DossierRepository.class);
        when(repo.findAliveByRaisonSociale(any(), any())).thenReturn(Optional.empty());
        EssaiQuotas quotas = mock(EssaiQuotas.class);
        doThrow(new PlanLimitException(EssaiQuotas.CODE_DOSSIERS, "Limite de l\u2019essai gratuit atteinte : 3 dossiers",
                "dossiers", 3, 3, "PROFESSIONNEL", "")).when(quotas).verifierNouveauDossier(ws);
        ObjectProvider<EssaiQuotas> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(quotas);
        DossierIdempotenceLookup lookup = new DossierIdempotenceLookup(repo);
        lookup.setEssaiQuotas(p);
        assertThatThrownBy(() -> lookup.getOrCreateAlive(ws, "QUATRIEME SARL", FormeJuridique.values()[0],
                TicketType.CREATION, UUID.randomUUID()))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("3 dossiers");
        verify(repo, never()).save(any());
    }
}
