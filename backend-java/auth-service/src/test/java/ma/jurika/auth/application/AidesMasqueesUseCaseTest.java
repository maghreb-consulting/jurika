package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AidesMasqueesRegistre;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AidesMasqueesUseCaseTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID WS = UUID.randomUUID();

    private final AidesMasqueesRegistre registre = mock(AidesMasqueesRegistre.class);
    private final AidesMasqueesUseCase useCase = new AidesMasqueesUseCase(registre);

    @AfterEach
    void vider() {
        TenantContext.clear();
    }

    @Test
    void masquer_pose_le_workspace_de_l_appelant_et_retient_la_cle() {
        useCase.masquer(USER, WS, "visite-employe");
        verify(registre).masquer(USER, WS, "visite-employe");
        assertThat(TenantContext.get()).isEqualTo(WS);
    }

    @Test
    void lister_rend_les_cles_du_registre() {
        when(registre.cles(USER, WS)).thenReturn(List.of("a", "b"));
        assertThat(useCase.cles(USER, WS)).containsExactly("a", "b");
    }

    @Test
    void une_cle_mal_formee_est_refusee_sans_toucher_au_registre() {
        for (String mauvaise : new String[] {"", " ", "a b", "-debut", "x".repeat(121), "aide/../x"}) {
            assertThatThrownBy(() -> useCase.masquer(USER, WS, mauvaise))
                    .as(mauvaise).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> useCase.reafficher(USER, WS, mauvaise))
                    .as(mauvaise).isInstanceOf(ValidationException.class);
        }
        verify(registre, never()).masquer(any(), any(), anyString());
        verify(registre, never()).reafficher(any(), any(), anyString());
    }

    @Test
    void au_dela_du_maximum_masquer_est_refuse_avec_un_message_qui_dit_quoi_faire() {
        when(registre.compter(USER, WS)).thenReturn(AidesMasqueesUseCase.MAXIMUM);
        assertThatThrownBy(() -> useCase.masquer(USER, WS, "page-ticket"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("r\u00e9affichez les aides depuis \u00ab Mon compte \u00bb");
        verify(registre, never()).masquer(any(), any(), anyString());
    }

    @Test
    void reafficher_tout_vide_le_compte_de_l_appelant() {
        useCase.reafficherTout(USER, WS);
        verify(registre).reafficherTout(USER, WS);
    }
}
