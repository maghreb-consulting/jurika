package ma.jurika.ticket.application;

import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.EtatDebours;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DeboursRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.EtatDeboursDocument;
import ma.jurika.ticket.domain.port.ReferenceDossierLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Lot L5 (E6, RG-DEB-02) : assemblage de l'etat des debours et refus nommes. */
class EtatDeboursServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final CabinetIdentity CABINET = new CabinetIdentity("Cabinet", null, null, null, null, null,
            null, null, null, null);
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 12);

    private final DeboursRepository debours = mock(DeboursRepository.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final ReferenceDossierLookup references = mock(ReferenceDossierLookup.class);
    private final EtatDeboursDocument document = mock(EtatDeboursDocument.class);
    private final EtatDeboursService service = new EtatDeboursService(debours, dossiers, references, document);

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    private static Ticket ticket(UUID dossierId) {
        return new Ticket(UUID.randomUUID(), WS, "T-1", "T", TicketType.MODIFICATION, TicketStatut.CLOTURE_DOSSIER,
                TicketPriorite.NORMALE, dossierId, null, null, null, null, null, null, null, Instant.now());
    }

    @Test
    void ticket_sans_dossier_refuse_avec_la_donnee_nommee() {
        assertThatThrownBy(() -> service.etablir(WS, ticket(null), CABINET, JOUR))
                .isInstanceOf(BusinessException.class).hasMessageContaining("aucun dossier");
        verifyNoInteractions(document);
    }

    @Test
    void ticket_sans_debours_refuse() {
        Ticket t = ticket(DOSSIER);
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));
        when(debours.findByTicket(WS, t.id())).thenReturn(List.of());
        assertThatThrownBy(() -> service.etablir(WS, t, CABINET, JOUR))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Aucun d\u00e9bours");
        verifyNoInteractions(document);
    }

    @Test
    void ticket_clos_regenerable_total_calcule() {
        Ticket t = ticket(DOSSIER);
        when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));
        when(references.reference(WS, DOSSIER)).thenReturn("2026-0001 \u2014 ACME");
        when(debours.findByTicket(WS, t.id())).thenReturn(List.of(
                ligne(t.id(), "1000.25"), ligne(t.id(), "234.25")));
        when(document.rendre(any())).thenReturn(new byte[] {1});

        assertThat(service.etablir(WS, t, CABINET, JOUR)).containsExactly(1);

        ArgumentCaptor<EtatDebours> e = ArgumentCaptor.forClass(EtatDebours.class);
        verify(document).rendre(e.capture());
        assertThat(e.getValue().total()).isEqualByComparingTo("1234.50");
        assertThat(e.getValue().referenceDossier()).isEqualTo("2026-0001 \u2014 ACME");
        assertThat(e.getValue().dateEtat()).isEqualTo(JOUR);
    }

    private static Debours ligne(UUID ticketId, String montant) {
        return new Debours(UUID.randomUUID(), WS, ticketId, "x", DeboursCategorie.TIMBRES, new BigDecimal(montant),
                JOUR, null, null, null, null, Instant.now(), null, null);
    }

    private static EntrepriseDossier dossier() {
        Instant now = Instant.now();
        return new EntrepriseDossier(DOSSIER, WS, "ACME", FormeJuridique.SARL, null, null, null, null, null, null,
                null, null, null, null, DossierStatut.ACTIVE, null, null, now, now);
    }
}
