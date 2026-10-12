package ma.jurika.ticket.api;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.application.AccesDeboursTicket;
import ma.jurika.ticket.application.DeboursUseCase;
import ma.jurika.ticket.application.EtatDeboursService;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.PermissionsClientLookup;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot L0 (E22), lot L1 (E11) puis lot L5 (E6) : la lecture des debours (liste et etat Word)
 * est ouverte a l'employe, au superviseur et au client ; le controle fin (dossier de
 * l'employe, dossier et permissions du client) est fait par AccesDeboursTicket
 * (AccesDeboursTicketTest). L'ecriture est refusee au superviseur, au client et a l'equipe
 * JURIKA. Gardes evaluees avec le gestionnaire reel.
 */
class DeboursControllerSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {"list", "etatWord"})
    void lecture_client_employe_superviseur_autorises_super_admin_refuse(String action) {
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_CLIENT", methode(action))).isTrue();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_EMPLOYE", methode(action))).isTrue();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_SUPERVISEUR", methode(action))).isTrue();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_SUPER_ADMIN", methode(action))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete"})
    void ecriture_reservee_a_l_employe(String action) {
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_EMPLOYE", methode(action))).isTrue();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_SUPERVISEUR", methode(action))).isFalse();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_CLIENT", methode(action))).isFalse();
        assertThat(GardesSuperviseurTicketTest.autorise("ROLE_SUPER_ADMIN", methode(action))).isFalse();
    }

    @Test
    void sortie_pdf_retiree() {
        // P5 : l'etat des debours ne sort plus qu'en Word.
        assertThat(Arrays.stream(DeboursController.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("exportPdf");
    }

    @Test
    void liste_d_un_ticket_d_un_autre_workspace_refusee() {
        TicketRepository tickets = mock(TicketRepository.class);
        DeboursUseCase debours = mock(DeboursUseCase.class);
        when(tickets.findById(any(), any())).thenReturn(Optional.empty());
        DeboursController controleur = new DeboursController(debours, mock(EtatDeboursService.class), null,
                new AccesDeboursTicket(tickets, mock(DossierRepository.class), mock(PermissionsClientLookup.class)),
                Clock.systemUTC());
        AuthenticatedUser employe = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "e@a.test", Role.EMPLOYE);

        assertThatThrownBy(() -> controleur.list(employe, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> controleur.etatWord(employe, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(debours);
    }

    private static Method methode(String nom) {
        return GardesSuperviseurTicketTest.methode(DeboursController.class, nom);
    }
}
