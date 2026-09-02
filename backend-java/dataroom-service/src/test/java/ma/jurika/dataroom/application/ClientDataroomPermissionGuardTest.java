package ma.jurika.dataroom.application;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la garde de permissions client (defense en profondeur,
 * en plus du RBAC @PreAuthorize). Couvre la decision 2026-06-30 : les flags
 * perm_download / perm_depot deviennent reellement appliques cote backend.
 */
class ClientDataroomPermissionGuardTest {

    private final DataroomSettingsService settings = mock(DataroomSettingsService.class);
    private final ClientDataroomPermissionGuard guard = new ClientDataroomPermissionGuard(settings);

    private final UUID dossierId = UUID.randomUUID();

    private static AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "u@jurika.ma", role);
    }

    private SettingsEntity settingsWith(boolean download, boolean depot) {
        SettingsEntity s = new SettingsEntity();
        s.setDossierId(dossierId);
        s.setAccessStatus("ACTIVE");
        s.setPermDownload(download);
        s.setPermDepot(depot);
        when(settings.getOrCreate(dossierId)).thenReturn(s);
        return s;
    }

    // ---- DOWNLOAD ----

    @Test
    void client_sans_perm_download_recoit_403() {
        settingsWith(false, false);
        assertThatThrownBy(() -> guard.assertCanDownload(dossierId, user(Role.CLIENT)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void client_avec_perm_download_passe() {
        settingsWith(true, false);
        assertThatCode(() -> guard.assertCanDownload(dossierId, user(Role.CLIENT)))
                .doesNotThrowAnyException();
    }

    // ---- DEPOT (upload) ----

    @Test
    void client_sans_perm_depot_recoit_403() {
        settingsWith(true, false);
        assertThatThrownBy(() -> guard.assertCanDepot(dossierId, user(Role.CLIENT)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void client_avec_perm_depot_passe() {
        settingsWith(true, true);
        assertThatCode(() -> guard.assertCanDepot(dossierId, user(Role.CLIENT)))
                .doesNotThrowAnyException();
    }

    // ---- PREVIEW (Lot AA) : consultation NON gatee par perm_download ----

    @Test
    void client_sans_perm_download_peut_previsualiser() {
        // Le visionnage est un droit de consultation : perm_download=false ne bloque PAS.
        settingsWith(false, false);
        assertThatCode(() -> guard.assertCanPreview(dossierId, user(Role.CLIENT)))
                .doesNotThrowAnyException();
    }

    @Test
    void client_sur_dataroom_suspendu_ne_peut_pas_previsualiser() {
        SettingsEntity s = settingsWith(false, false);
        doThrow(new ValidationException("Data Room suspendu")).when(settings).ensureActive(s);
        assertThatThrownBy(() -> guard.assertCanPreview(dossierId, user(Role.CLIENT)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void non_client_previsualise_sans_lecture_settings() {
        assertThatCode(() -> {
            guard.assertCanPreview(dossierId, user(Role.EMPLOYE));
            guard.assertCanPreview(dossierId, user(Role.SUPERVISEUR));
            guard.assertCanPreview(dossierId, user(Role.SUPER_ADMIN));
        }).doesNotThrowAnyException();
        verify(settings, never()).getOrCreate(any());
    }

    // ---- SUSPENDED conserve ----

    @Test
    void client_sur_dataroom_suspendu_est_bloque() {
        SettingsEntity s = settingsWith(true, true);
        doThrow(new ValidationException("Data Room suspendu")).when(settings).ensureActive(s);
        assertThatThrownBy(() -> guard.assertCanDownload(dossierId, user(Role.CLIENT)))
                .isInstanceOf(ValidationException.class);
    }

    // ---- NON-REGRESSION : la garde ne s'applique qu'au CLIENT ----

    @Test
    void employe_jamais_bloque_meme_si_perms_off() {
        // Aucune lecture de settings pour un non-CLIENT : garde no-op.
        assertThatCode(() -> {
            guard.assertCanDownload(dossierId, user(Role.EMPLOYE));
            guard.assertCanDepot(dossierId, user(Role.EMPLOYE));
        }).doesNotThrowAnyException();
        verify(settings, never()).getOrCreate(any());
    }

    @Test
    void superviseur_et_super_admin_jamais_bloques() {
        assertThatCode(() -> {
            guard.assertCanDownload(dossierId, user(Role.SUPERVISEUR));
            guard.assertCanDownload(dossierId, user(Role.SUPER_ADMIN));
        }).doesNotThrowAnyException();
        verify(settings, never()).getOrCreate(any());
    }
}
