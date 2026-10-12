package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.DeboursSaisie;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup.DocumentVu;
import ma.jurika.ticket.domain.port.DeboursRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L5 (E6) : RG-DEB-02 / P7, chaque ligne ajoutee, modifiee ou supprimee est tracee avec
 * son ancienne et sa nouvelle valeur ; RG-DEB-01, la piece justificative est un document de
 * la Data Room DU DOSSIER du ticket.
 */
class DeboursUseCaseTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID EMPLOYE = UUID.randomUUID();
    private static final UUID LIGNE = UUID.randomUUID();
    private static final UUID PIECE = UUID.randomUUID();

    private final DeboursRepository repo = mock(DeboursRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final DataroomDocumentLookup documents = mock(DataroomDocumentLookup.class);
    private final AuditEventEmitter audit = mock(AuditEventEmitter.class);
    private final DeboursUseCase useCase = new DeboursUseCase(repo, tickets, documents, audit);

    {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(new Ticket(TICKET, WS, "T-2026-00012", "Cession",
                TicketType.MODIFICATION, TicketStatut.GENERATION_DOCUMENTS, TicketPriorite.NORMALE, DOSSIER, EMPLOYE,
                EMPLOYE, null, null, null, null, null, Instant.now())));
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    private static Debours ligne(String libelle, DeboursCategorie c, String montant, UUID piece, String fichier) {
        return new Debours(LIGNE, WS, TICKET, libelle, c, new BigDecimal(montant), LocalDate.of(2026, 10, 2),
                null, fichier, null, EMPLOYE, Instant.now(), null, piece);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> trace(String action) {
        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(audit).emit(eq(WS), eq(EMPLOYE), eq(action), eq("ticket"), eq(TICKET), meta.capture());
        return meta.getValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ajout_trace_la_nouvelle_valeur() {
        Debours cree = ligne("Frais de greffe", DeboursCategorie.FRAIS_GREFFE, "350.00", null, null);
        when(repo.create(eq(WS), eq(TICKET), any(), eq(EMPLOYE))).thenReturn(cree);

        useCase.ajouter(WS, EMPLOYE, TICKET, new DeboursSaisie("Frais de greffe", DeboursCategorie.FRAIS_GREFFE,
                new BigDecimal("350.00"), LocalDate.of(2026, 10, 2), null, null, null));

        Map<String, Object> m = trace("DEBOURS_AJOUTE");
        assertThat(m).containsEntry("deboursId", LIGNE.toString()).containsEntry("avant", null);
        assertThat((Map<String, Object>) m.get("apres")).containsEntry("libelle", "Frais de greffe")
                .containsEntry("type", "FRAIS_GREFFE").containsEntry("montant", "350.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void modification_trace_ancienne_et_nouvelle_valeur() {
        when(repo.findById(WS, LIGNE)).thenReturn(Optional.of(ligne("Timbres", DeboursCategorie.TIMBRES, "40.00",
                null, null)));
        when(repo.update(eq(WS), eq(LIGNE), any())).thenReturn(ligne("Timbres fiscaux", DeboursCategorie.TIMBRES,
                "60.00", null, null));

        useCase.modifier(WS, EMPLOYE, TICKET, LIGNE, new DeboursSaisie("Timbres fiscaux", DeboursCategorie.TIMBRES,
                new BigDecimal("60.00"), LocalDate.of(2026, 10, 2), null, null, null));

        Map<String, Object> m = trace("DEBOURS_MODIFIE");
        assertThat((Map<String, Object>) m.get("avant")).containsEntry("libelle", "Timbres")
                .containsEntry("montant", "40.00");
        assertThat((Map<String, Object>) m.get("apres")).containsEntry("libelle", "Timbres fiscaux")
                .containsEntry("montant", "60.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void suppression_trace_l_ancienne_valeur() {
        when(repo.findById(WS, LIGNE)).thenReturn(Optional.of(ligne("Annonce", DeboursCategorie.ANNONCE_LEGALE,
                "450.00", PIECE, "facture-jal.pdf")));

        useCase.supprimer(WS, EMPLOYE, TICKET, LIGNE);

        verify(repo).delete(WS, LIGNE);
        Map<String, Object> m = trace("DEBOURS_SUPPRIME");
        assertThat((Map<String, Object>) m.get("avant")).containsEntry("piece", "facture-jal.pdf")
                .containsEntry("pieceDocumentId", PIECE.toString());
        assertThat(m).containsEntry("apres", null);
    }

    @Test
    void ligne_d_un_autre_ticket_404() {
        Debours ailleurs = new Debours(LIGNE, WS, UUID.randomUUID(), "x", DeboursCategorie.AUTRE, BigDecimal.ONE,
                LocalDate.of(2026, 10, 2), null, null, null, EMPLOYE, Instant.now(), null, null);
        when(repo.findById(WS, LIGNE)).thenReturn(Optional.of(ailleurs));
        assertThatThrownBy(() -> useCase.supprimer(WS, EMPLOYE, TICKET, LIGNE)).isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any(), any());
    }

    @Test
    void piece_du_dossier_acceptee_nom_lu_dans_la_data_room_a_defaut() {
        when(documents.findByIds(WS, List.of(PIECE))).thenReturn(List.of(
                new DocumentVu(PIECE, DOSSIER, TICKET, "AUTRE", "Quittance greffe.pdf", true)));
        when(repo.create(any(), any(), any(), any())).thenReturn(ligne("Greffe", DeboursCategorie.FRAIS_GREFFE,
                "300.00", PIECE, "Quittance greffe.pdf"));
        ArgumentCaptor<DeboursSaisie> s = ArgumentCaptor.forClass(DeboursSaisie.class);

        useCase.ajouter(WS, EMPLOYE, TICKET, new DeboursSaisie("Greffe", DeboursCategorie.FRAIS_GREFFE,
                new BigDecimal("300.00"), LocalDate.of(2026, 10, 2), PIECE, " ", null));

        verify(repo).create(eq(WS), eq(TICKET), s.capture(), eq(EMPLOYE));
        assertThat(s.getValue().pieceJointeFilename()).isEqualTo("Quittance greffe.pdf");
    }

    @Test
    void piece_d_un_autre_dossier_refusee() {
        when(documents.findByIds(WS, List.of(PIECE))).thenReturn(List.of(
                new DocumentVu(PIECE, UUID.randomUUID(), null, "AUTRE", "x.pdf", true)));
        assertThatThrownBy(() -> useCase.ajouter(WS, EMPLOYE, TICKET, new DeboursSaisie("Greffe",
                DeboursCategorie.FRAIS_GREFFE, BigDecimal.TEN, LocalDate.of(2026, 10, 2), PIECE, "x.pdf", null)))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).create(any(), any(), any(), any());
    }

    @Test
    void piece_sur_un_ticket_sans_dossier_refusee() {
        UUID sansDossier = UUID.randomUUID();
        when(tickets.findById(WS, sansDossier)).thenReturn(Optional.of(new Ticket(sansDossier, WS, "T-2", "T",
                TicketType.MODIFICATION, TicketStatut.CREATION_TICKET, TicketPriorite.NORMALE, null, EMPLOYE,
                EMPLOYE, null, null, null, null, null, Instant.now())));
        assertThatThrownBy(() -> useCase.ajouter(WS, EMPLOYE, sansDossier, new DeboursSaisie("Greffe",
                DeboursCategorie.FRAIS_GREFFE, BigDecimal.TEN, LocalDate.of(2026, 10, 2), PIECE, "x.pdf", null)))
                .isInstanceOf(BusinessException.class);
    }
}
