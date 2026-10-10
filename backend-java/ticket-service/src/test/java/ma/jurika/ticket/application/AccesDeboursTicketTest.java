package ma.jurika.ticket.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.PermissionsClientLookup;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L1, etape E11 : etat des debours.
 * <ul>
 *   <li>RG-DEB-03 : visible par le client du dossier si ses permissions le prevoient
 *       (consultation, RG-CLI-01) ;</li>
 *   <li>RG-DOS-01 : l'employe ne lit et n'ecrit que les debours de ses dossiers ;</li>
 *   <li>le superviseur lit, en observation.</li>
 * </ul>
 */
class AccesDeboursTicketTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID RESPONSABLE = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final PermissionsClientLookup permissions = mock(PermissionsClientLookup.class);
    private final AccesDeboursTicket acces = new AccesDeboursTicket(tickets, dossiers, permissions);

    {
        Instant now = Instant.now();
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(new Ticket(TICKET, WS, "T-1", "T", TicketType.CREATION,
                TicketStatut.DEROULEMENT_DEMARCHE, TicketPriorite.NORMALE, DOSSIER, RESPONSABLE, RESPONSABLE,
                null, null, null, null, null, now)));
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(new EntrepriseDossier(DOSSIER, WS, "ACME",
                FormeJuridique.SARL, null, null, null, null, null, null, null, null, null, null,
                DossierStatut.ACTIVE, CLIENT, RESPONSABLE, now, now)));
    }

    private static AuthenticatedUser u(UUID id, Role role) {
        return new AuthenticatedUser(id, WS, "x@y.ma", role);
    }

    @Test
    void lecture_superviseur_responsable_et_client_autorise() {
        when(permissions.consultationPermise(WS, DOSSIER)).thenReturn(true);
        assertThat(acces.lecture(u(UUID.randomUUID(), Role.SUPERVISEUR), TICKET).id()).isEqualTo(TICKET);
        assertThat(acces.lecture(u(RESPONSABLE, Role.EMPLOYE), TICKET).id()).isEqualTo(TICKET);
        assertThat(acces.lecture(u(CLIENT, Role.CLIENT), TICKET).id()).isEqualTo(TICKET);
    }

    @Test
    void client_sans_consultation_refuse() {
        when(permissions.consultationPermise(WS, DOSSIER)).thenReturn(false);
        assertThatThrownBy(() -> acces.lecture(u(CLIENT, Role.CLIENT), TICKET))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void autre_client_et_autre_employe_404() {
        assertThatThrownBy(() -> acces.lecture(u(UUID.randomUUID(), Role.CLIENT), TICKET))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> acces.lecture(u(UUID.randomUUID(), Role.EMPLOYE), TICKET))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ecriture_reservee_au_responsable() {
        acces.ecriture(u(RESPONSABLE, Role.EMPLOYE), TICKET);
        assertThatThrownBy(() -> acces.ecriture(u(UUID.randomUUID(), Role.EMPLOYE), TICKET))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> acces.ecriture(u(CLIENT, Role.CLIENT), TICKET))
                .isInstanceOf(AccessDeniedException.class);
    }
}
