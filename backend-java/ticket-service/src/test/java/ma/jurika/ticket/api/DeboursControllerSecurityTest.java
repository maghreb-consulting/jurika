package ma.jurika.ticket.api;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.application.DeboursUseCase;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot L0, etape E22 (perimetre § E, arb. 8) : la lecture des debours (liste et
 * export PDF) est reservee aux roles du cabinet, EMPLOYE et SUPERVISEUR, dans
 * leur workspace. Aucun acces client en L0 (RG-DEB-03 : lot L1). Gardes evaluees
 * avec le gestionnaire d'expressions reel (GardesSuperviseurTicketTest).
 */
class DeboursControllerSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {"list", "exportPdf"})
    void client_refuse(String action) {
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_CLIENT", methode(action))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "exportPdf"})
    void employe_et_superviseur_autorises(String action) {
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_EMPLOYE", methode(action))).isTrue();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_SUPERVISEUR", methode(action))).isTrue();
    }

    @Test
    void liste_d_un_ticket_d_un_autre_workspace_refusee() {
        TicketRepository tickets = mock(TicketRepository.class);
        DeboursUseCase debours = mock(DeboursUseCase.class);
        when(tickets.findById(any(), any())).thenReturn(Optional.empty());
        when(debours.listForTicket(any(), any())).thenReturn(new DeboursUseCase.Summary(List.of(), BigDecimal.ZERO));
        DeboursController controleur = new DeboursController(debours, tickets, null, null);
        AuthenticatedUser employe = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "e@a.test", Role.EMPLOYE);

        assertThatThrownBy(() -> controleur.list(employe, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(debours);
    }

    private static Method methode(String nom) {
        return GardesSuperviseurTicketTest.methode(DeboursController.class, nom);
    }
}
