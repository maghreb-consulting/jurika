package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.PreferencesCompteRegistre;
import ma.jurika.auth.domain.port.PreferencesCompteRegistre.Preferences;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L6b (E2) : preferences d'affichage du COMPTE (theme, assistant Juri masque). Chaque
 * operation porte sur le compte de l'appelant ; une valeur invalide ou une cle inconnue est
 * refusee avec la donnee nommee, sans toucher au registre (jamais ignoree en silence).
 */
class PreferencesCompteUseCaseTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID WS = UUID.randomUUID();

    private final PreferencesCompteRegistre registre = mock(PreferencesCompteRegistre.class);
    private final PreferencesCompteUseCase useCase = new PreferencesCompteUseCase(registre);

    @AfterEach
    void vider() {
        TenantContext.clear();
    }

    @Test
    void lire_rend_les_preferences_du_compte_et_pose_son_workspace() {
        when(registre.lire(USER, WS)).thenReturn(Optional.of(new Preferences("SOMBRE", true)));
        assertThat(useCase.lire(USER, WS)).isEqualTo(new Preferences("SOMBRE", true));
        assertThat(TenantContext.get()).isEqualTo(WS);
    }

    @Test
    void lire_un_compte_introuvable_est_un_refus_nomme() {
        when(registre.lire(USER, WS)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.lire(USER, WS)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void modifier_le_theme_seul_laisse_juri_inchange() {
        when(registre.modifier(USER, WS, "CLAIR", null)).thenReturn(Optional.of(new Preferences("CLAIR", false)));
        assertThat(useCase.modifier(USER, WS, Map.of("theme", "CLAIR"))).isEqualTo(new Preferences("CLAIR", false));
        verify(registre).modifier(USER, WS, "CLAIR", null);
        assertThat(TenantContext.get()).isEqualTo(WS);
    }

    @Test
    void modifier_juri_seul_laisse_le_theme_inchange() {
        when(registre.modifier(USER, WS, null, true)).thenReturn(Optional.of(new Preferences("SYSTEME", true)));
        assertThat(useCase.modifier(USER, WS, Map.of("juriMasque", true))).isEqualTo(new Preferences("SYSTEME", true));
    }

    @Test
    void les_trois_themes_sont_acceptes() {
        for (String t : List.of("SYSTEME", "CLAIR", "SOMBRE")) {
            when(registre.modifier(USER, WS, t, null)).thenReturn(Optional.of(new Preferences(t, false)));
            assertThat(useCase.modifier(USER, WS, Map.of("theme", t)).theme()).isEqualTo(t);
        }
    }

    @Test
    void corps_vide_rend_les_preferences_courantes_sans_ecrire() {
        when(registre.lire(USER, WS)).thenReturn(Optional.of(new Preferences("SYSTEME", false)));
        assertThat(useCase.modifier(USER, WS, Map.of())).isEqualTo(new Preferences("SYSTEME", false));
        verify(registre, never()).modifier(any(), any(), any(), any());
    }

    @Test
    void un_theme_invalide_est_refuse_et_nomme() {
        Map<String, Object> nul = new HashMap<>();
        nul.put("theme", null);
        for (Map<String, Object> corps : List.of(Map.<String, Object>of("theme", "dark"), Map.<String, Object>of("theme", "sombre"),
                Map.<String, Object>of("theme", ""), Map.<String, Object>of("theme", 3), nul)) {
            assertThatThrownBy(() -> useCase.modifier(USER, WS, corps))
                    .as(String.valueOf(corps))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("th\u00e8me")
                    .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("PREFERENCE_THEME_INVALIDE"));
        }
        verify(registre, never()).modifier(any(), any(), any(), any());
    }

    @Test
    void juri_masque_non_booleen_est_refuse_et_nomme() {
        Map<String, Object> nul = new HashMap<>();
        nul.put("juriMasque", null);
        for (Map<String, Object> corps : List.of(Map.<String, Object>of("juriMasque", "oui"),
                Map.<String, Object>of("juriMasque", "true"), Map.<String, Object>of("juriMasque", 1), nul)) {
            assertThatThrownBy(() -> useCase.modifier(USER, WS, corps))
                    .as(String.valueOf(corps))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Juri")
                    .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("PREFERENCE_JURI_INVALIDE"));
        }
        verify(registre, never()).modifier(any(), any(), any(), any());
    }

    @Test
    void une_cle_inconnue_est_refusee_et_nommee_meme_avec_une_cle_valide() {
        assertThatThrownBy(() -> useCase.modifier(USER, WS, Map.of("theme", "CLAIR", "juri_masque", true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("juri_masque")
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("PREFERENCE_INCONNUE"));
        verify(registre, never()).modifier(any(), any(), any(), any());
    }

    @Test
    void un_corps_absent_est_refuse() {
        assertThatThrownBy(() -> useCase.modifier(USER, WS, null)).isInstanceOf(BusinessException.class);
    }
}
