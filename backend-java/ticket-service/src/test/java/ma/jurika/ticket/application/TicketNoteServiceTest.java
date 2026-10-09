package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketNote;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketNoteRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L1, etape E6 : note de ticket (RG-TKT-07). Ouverte des la creation (note vide
 * sans ligne), validable meme vide, modifiable par l'employe en charge, lisible par
 * le superviseur en observation, fermee en ecriture sur un ticket clos (RG-TKT-11).
 */
@ExtendWith(MockitoExtension.class)
class TicketNoteServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID RESPONSABLE = UUID.randomUUID();
    private static final UUID AUTRE = UUID.randomUUID();

    @Mock TicketRepository tickets;
    @Mock DossierRepository dossiers;
    @Mock TicketNoteRepository notes;
    @Mock AuditEventEmitter audit;

    private TicketNoteService service() {
        return new TicketNoteService(tickets, dossiers, notes, audit);
    }

    @Test
    void note_vide_des_la_creation() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.CREATION_TICKET)));
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));
        when(notes.find(WS, TICKET)).thenReturn(Optional.empty());

        TicketNote n = service().lire(WS, RESPONSABLE, false, TICKET);
        assertThat(n.contenu()).isEmpty();
        assertThat(n.modifieLe()).isNull();
    }

    @Test
    void le_responsable_enregistre_meme_une_note_vide() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.GENERATION_DOCUMENTS)));
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));
        when(notes.save(eq(WS), eq(TICKET), eq(""), eq(RESPONSABLE)))
                .thenReturn(new TicketNote(TICKET, "", RESPONSABLE, Instant.now()));

        TicketNote n = service().enregistrer(WS, RESPONSABLE, TICKET, null);
        assertThat(n.contenu()).isEmpty();
        verify(audit).emit(eq(WS), eq(RESPONSABLE), eq("TICKET_NOTE_MODIFIEE"), eq("ticket"), eq(TICKET), anyMap());
    }

    @Test
    void un_autre_employe_ne_lit_ni_n_ecrit_la_note() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.CREATION_TICKET)));
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));

        assertThatThrownBy(() -> service().lire(WS, AUTRE, false, TICKET)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service().enregistrer(WS, AUTRE, TICKET, "x")).isInstanceOf(NotFoundException.class);
        verify(notes, never()).save(any(), any(), any(), any());
    }

    @Test
    void le_superviseur_lit_en_observation() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.CREATION_TICKET)));
        when(notes.find(WS, TICKET)).thenReturn(Optional.of(new TicketNote(TICKET, "a faire", RESPONSABLE, Instant.now())));

        assertThat(service().lire(WS, AUTRE, true, TICKET).contenu()).isEqualTo("a faire");
    }

    @Test
    void ticket_clos_note_en_lecture_seule() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.CLOTURE_DOSSIER)));
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));

        assertThatThrownBy(() -> service().enregistrer(WS, RESPONSABLE, TICKET, "x"))
                .isInstanceOf(ConflictException.class);
        verify(notes, never()).save(any(), any(), any(), any());
    }

    private Ticket ticket(TicketStatut statut) {
        return new Ticket(TICKET, WS, "T-2026-00042", "Ticket", TicketType.CREATION, statut,
                TicketPriorite.NORMALE, DOSSIER, RESPONSABLE, RESPONSABLE, null, null, null, null, null, Instant.now());
    }

    private EntrepriseDossier dossier() {
        Instant now = Instant.now();
        return new EntrepriseDossier(DOSSIER, WS, "ACME SARL", FormeJuridique.SARL,
                null, null, null, null, null, null, null, null, null, null,
                DossierStatut.ACTIVE, null, RESPONSABLE, now, now);
    }
}
