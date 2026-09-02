package ma.jurika.dataroom.api;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.api.dto.IdentityDtos.ExtractedIdentityDto;
import ma.jurika.dataroom.api.dto.IdentityDtos.IdentityType;
import ma.jurika.dataroom.application.identity.IdentityExtractionService;
import ma.jurika.dataroom.domain.port.KieServiceClient.KieServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests focalisés sur la logique du controller : translation des exceptions
 * en codes HTTP + check SUPERVISEUR-archive interdit.
 * <p>
 * Le check sur le rôle {@code CLIENT} est délégué à {@code @PreAuthorize}
 * (configuré statiquement) et n'est pas testé ici — il est garanti par
 * Spring Security.
 */
@ExtendWith(MockitoExtension.class)
class IdentityControllerTest {

    @Mock IdentityExtractionService service;
    @InjectMocks IdentityController controller;

    private final UUID dossierId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    private MultipartFile png(String name) {
        return new MockMultipartFile(name, name + ".png", "image/png", new byte[]{1, 2, 3});
    }

    private AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(userId, workspaceId, "u@test.ma", role);
    }

    @Test
    void supervisor_cannot_archive_returns_403() {
        AccessDeniedException ex = (AccessDeniedException) catchThrowable(
                () -> controller.extract(user(Role.SUPERVISEUR), png("r"), png("v"),
                        IdentityType.NOUVELLE, dossierId, true));

        assertThat(ex).hasMessageContaining("SUPERVISEUR");
        verify(service, never()).extract(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void supervisor_can_extract_in_read_only_mode_without_archive() {
        ExtractedIdentityDto expected = new ExtractedIdentityDto(
                IdentityType.NOUVELLE,
                Map.of("nom", "BENATIK"),
                "kie",
                List.of(),
                null);
        when(service.extract(eq(IdentityType.NOUVELLE), any(), any(), eq(dossierId), eq(false), eq(userId)))
                .thenReturn(expected);

        ExtractedIdentityDto out = controller.extract(user(Role.SUPERVISEUR), png("r"), png("v"),
                IdentityType.NOUVELLE, dossierId, false);

        assertThat(out).isSameAs(expected);
    }

    @Test
    void employee_with_dossierId_and_archive_true_calls_service_with_archive_true() {
        ExtractedIdentityDto expected = new ExtractedIdentityDto(
                IdentityType.CN,
                Map.of("numero_cn", "98765"),
                "kie",
                List.of(),
                UUID.randomUUID());
        when(service.extract(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenReturn(expected);

        ExtractedIdentityDto out = controller.extract(user(Role.EMPLOYE), png("r"), null,
                IdentityType.CN, dossierId, true);

        assertThat(out).isSameAs(expected);
        ArgumentCaptor<Boolean> archiveFlag = ArgumentCaptor.forClass(Boolean.class);
        verify(service).extract(eq(IdentityType.CN), any(), any(),
                eq(dossierId), archiveFlag.capture(), eq(userId));
        assertThat(archiveFlag.getValue()).isTrue();
    }

    @Test
    void employee_without_dossierId_never_archives_even_if_archive_true() {
        when(service.extract(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenReturn(new ExtractedIdentityDto(IdentityType.CN, Map.of(), "kie", List.of(), null));

        controller.extract(user(Role.EMPLOYE), png("r"), null,
                IdentityType.CN, null, true);

        ArgumentCaptor<Boolean> archiveFlag = ArgumentCaptor.forClass(Boolean.class);
        verify(service).extract(eq(IdentityType.CN), any(), any(),
                eq(null), archiveFlag.capture(), eq(userId));
        assertThat(archiveFlag.getValue()).isFalse();
    }

    @Test
    void kie_unavailable_is_remapped_to_503() {
        when(service.extract(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenThrow(new KieServiceUnavailableException("kie down"));

        ResponseStatusException ex = (ResponseStatusException) catchThrowable(
                () -> controller.extract(user(Role.EMPLOYE), png("r"), null,
                        IdentityType.CN, dossierId, false));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getReason()).contains("kie-service indisponible");
    }

    @Test
    void illegal_argument_is_remapped_to_400() {
        when(service.extract(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenThrow(new IllegalArgumentException("Recto requis"));

        ResponseStatusException ex = (ResponseStatusException) catchThrowable(
                () -> controller.extract(user(Role.EMPLOYE), png("r"), null,
                        IdentityType.CN, dossierId, false));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static Throwable catchThrowable(ThrowingRunnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Throwable; }
}
