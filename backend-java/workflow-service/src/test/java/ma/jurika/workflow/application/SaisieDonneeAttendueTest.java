package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import ma.jurika.workflow.domain.model.VariableDuDossier.Origine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L4a (E1d) : une donnee attendue d'un organisme se SAISIT la ou elle est reclamee.
 * Avant, la date et le numero du depot legal n'avaient aucun ecran : l'acte portait
 * "A OBTENIR" et ne pouvait jamais se regenerer.
 */
class SaisieDonneeAttendueTest {

    private final MagasinVariables magasin = mock(MagasinVariables.class);
    private final EntityManager em = mock(EntityManager.class);
    private final Query requete = mock(Query.class);
    private DonneesAttenduesService service;
    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID employe = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new DonneesAttenduesService(magasin);
        Field f = DonneesAttenduesService.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(service, em);
        when(em.createNativeQuery(anyString())).thenReturn(requete);
        when(requete.setParameter(anyInt(), any())).thenReturn(requete);
    }

    @Test
    void une_donnee_reclamee_se_saisit_et_atteint_le_magasin() {
        when(requete.getResultList()).thenReturn(List.of(1));
        service.saisir(ws, ticket, employe, "DATE_DEPOT_LEGAL", "2026-10-20");
        verify(magasin).poser(eq(ws), eq(ticket), eq("DATE_DEPOT_LEGAL"), eq("2026-10-20"),
                eq(Origine.SAISIE), eq(employe), anyString());
    }

    @Test
    void une_donnee_non_reclamee_est_refusee() {
        when(requete.getResultList()).thenReturn(List.of());
        assertThatThrownBy(() -> service.saisir(ws, ticket, employe, "DENOMINATION", "X"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("n\u2019est pas attendue");
        verify(magasin, never()).poser(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void un_identifiant_de_la_fiche_se_saisit_dans_la_fiche() {
        assertThatThrownBy(() -> service.saisir(ws, ticket, employe, "ICE", "001122334000055"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Identifiants de la soci");
        verify(magasin, never()).poser(any(), any(), any(), any(), any(), any(), any());
    }
}
