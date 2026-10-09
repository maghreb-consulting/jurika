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
 * Lot L0 (E22) puis lot L1 (E11, RG-DEB-03) : la lecture des debours (liste et
 * export PDF) est ouverte a l'employe, au superviseur et au client ; le controle
 * fin (dossier de l'employe, dossier et permission de consultation du client) est
 * fait par AccesDeboursTicket (AccesDeboursTicketTest). L'equipe JURIKA
 * (SUPER_ADMIN) n'y a pas acces. Gardes evaluees avec le gestionnaire reel.
 */
class DeboursControllerSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {"list", "exportPdf"})
    void client_autorise_par_la_garde_super_admin_refuse(String action) {
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_CLIENT", methode(action))).isTrue();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_SUPER_ADMIN", methode(action))).isFalse();
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
        DeboursController controleur = new DeboursController(debours, tickets, null, null,
                new ma.jurika.ticket.application.AccesDeboursTicket(tickets,
                        mock(ma.jurika.ticket.domain.port.DossierRepository.class),
                        mock(ma.jurika.ticket.domain.port.PermissionsClientLookup.class)));
        AuthenticatedUser employe = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "e@a.test", Role.EMPLOYE);

        assertThatThrownBy(() -> controleur.list(employe, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(debours);
    }

    private static Method methode(String nom) {
        return GardesSuperviseurTicketTest.methode(DeboursController.class, nom);
    }
}
