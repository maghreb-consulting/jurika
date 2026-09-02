package ma.jurika.auth.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2026-07-01 — getDossierClient renvoie le client lie / null ; jamais de fuite
 * cross-workspace.
 */
@ExtendWith(MockitoExtension.class)
class GetDossierClientUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock EntityManager em;
    @Mock Query query;

    GetDossierClientUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetDossierClientUseCase(userRepository);
        ReflectionTestUtils.setField(useCase, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyInt(), any())).thenReturn(query);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private User client(UUID id, UUID ws) {
        return new User(id, ws, "oussama.benatik@jurika.ma", "oussama.benatik@jurika.ma",
                "oussama@perso.ma", "hash", "Oussama", "BENATIK", "+212600000000",
                Role.CLIENT, null, false, false, null,
                Instant.now(), null, null, (short) 0, null, null,
                UserStatus.ACTIVE, Instant.now());
    }

    @Test
    void returnsClientInfo_whenDossierHasLinkedClient() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(clientId));
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client(clientId, ws)));

        var info = useCase.execute(new GetDossierClientUseCase.Command(ws, dossier));

        assertThat(info).isNotNull();
        assertThat(info.userId()).isEqualTo(clientId);
        assertThat(info.firstName()).isEqualTo("Oussama");
        assertThat(info.lastName()).isEqualTo("BENATIK");
        // email = email de contact (destinataire de l'invitation), pas le login_email
        assertThat(info.email()).isEqualTo("oussama@perso.ma");
        assertThat(info.status()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsNull_whenDossierHasNoClient() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        // Dossier existe mais client_id IS NULL -> une ligne, valeur null.
        when(query.getResultList()).thenReturn(Collections.singletonList(null));

        var info = useCase.execute(new GetDossierClientUseCase.Command(ws, dossier));

        assertThat(info).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void throwsNotFound_whenDossierMissing() {
        UUID ws = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(new GetDossierClientUseCase.Command(ws, dossier)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsNull_whenResolvedClientBelongsToAnotherWorkspace() {
        UUID ws = UUID.randomUUID();
        UUID otherWs = UUID.randomUUID();
        UUID dossier = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(query.getResultList()).thenReturn(Arrays.asList((Object) clientId));
        // Le compte resolu appartient a un AUTRE workspace -> defense-in-depth.
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client(clientId, otherWs)));

        var info = useCase.execute(new GetDossierClientUseCase.Command(ws, dossier));

        assertThat(info).isNull();
    }
}
