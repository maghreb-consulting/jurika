package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.DroitSuppressionDataroomRepository;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L1, etape E8 : droit de suppression en Data Room accorde / retire par le superviseur. */
class SetDroitSuppressionDataroomUseCaseTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID SUP = UUID.randomUUID();
    private static final UUID EMP = UUID.randomUUID();

    private final UserRepository users = mock(UserRepository.class);
    private final DroitSuppressionDataroomRepository droits = mock(DroitSuppressionDataroomRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final SetDroitSuppressionDataroomUseCase useCase = new SetDroitSuppressionDataroomUseCase(users, droits, audit);

    private SetDroitSuppressionDataroomUseCase.Command cmd(boolean accorde) {
        return new SetDroitSuppressionDataroomUseCase.Command(WS, SUP, EMP, accorde, "ip", "ua");
    }

    private User user(Role role, UUID ws) {
        User u = mock(User.class);
        when(u.id()).thenReturn(EMP);
        when(u.role()).thenReturn(role);
        when(u.workspaceId()).thenReturn(ws);
        when(u.email()).thenReturn("e@x.ma");
        return u;
    }

    @Test
    void accorde_le_droit_a_un_employe_et_trace() {
        User emp = user(Role.EMPLOYE, WS);
        when(users.findById(EMP)).thenReturn(Optional.of(emp));
        when(droits.lire(WS, EMP)).thenReturn(false);

        var r = useCase.execute(cmd(true));

        assertThat(r.droitSuppressionDataroom()).isTrue();
        assertThat(r.changed()).isTrue();
        verify(droits).definir(WS, EMP, true);
        verify(audit).log(eq(WS), eq(SUP), eq("DROIT_SUPPRESSION_DATAROOM_ACCORDE"), eq("user"), eq(EMP),
                any(), any(), anyMap());
    }

    @Test
    void retire_le_droit_et_trace() {
        User emp = user(Role.EMPLOYE, WS);
        when(users.findById(EMP)).thenReturn(Optional.of(emp));
        when(droits.lire(WS, EMP)).thenReturn(true);

        useCase.execute(cmd(false));

        verify(droits).definir(WS, EMP, false);
        verify(audit).log(eq(WS), eq(SUP), eq("DROIT_SUPPRESSION_DATAROOM_RETIRE"), any(), any(), any(), any(), anyMap());
    }

    @Test
    void cible_hors_du_cabinet_404_et_non_employe_409() {
        User autreCabinet = user(Role.EMPLOYE, UUID.randomUUID());
        when(users.findById(EMP)).thenReturn(Optional.of(autreCabinet));
        assertThatThrownBy(() -> useCase.execute(cmd(true))).isInstanceOf(NotFoundException.class);

        User client = user(Role.CLIENT, WS);
        when(users.findById(EMP)).thenReturn(Optional.of(client));
        assertThatThrownBy(() -> useCase.execute(cmd(true))).isInstanceOf(ConflictException.class);
        verify(droits, never()).definir(any(), any(), anyBoolean());
    }
}
