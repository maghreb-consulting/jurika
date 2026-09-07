package ma.jurika.ticket.api;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.application.CreateTicketUseCase;
import ma.jurika.ticket.application.SearchTicketsUseCase;
import ma.jurika.ticket.application.TransitionTicketUseCase;
import ma.jurika.ticket.application.UpdateTicketUseCase;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketFilter;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.infrastructure.persistence.DossierJpaRepository;
import ma.jurika.ticket.infrastructure.persistence.UserViewEntity;
import ma.jurika.ticket.infrastructure.persistence.UserViewJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Visibilite par responsable (2026-07-03).
 *
 * <p>Apres un transfert de dossier, le nouvel employe RESPONSABLE doit voir les
 * tickets de SES dossiers — y compris l'historique CLOTURE/ANNULE que
 * {@code applyTransfer} ne reassigne pas (il ne touche que NOUVEAU/EN_COURS).
 * On prouve ici que la recherche EMPLOYE propage
 * {@code responsableId = userId} au {@link TicketFilter} (le predicat OR sur
 * {@code entreprise_dossiers.responsable_id} est ensuite construit par
 * {@code TicketSpecifications}), et que le detail d'un ticket clos reste
 * ouvrable par le responsable du dossier.
 */
@ExtendWith(MockitoExtension.class)
class TicketSearchScopingTest {

    @Mock CreateTicketUseCase createTicketUseCase;
    @Mock UpdateTicketUseCase updateTicketUseCase;
    @Mock TransitionTicketUseCase transitionTicketUseCase;
    @Mock SearchTicketsUseCase searchTicketsUseCase;
    @Mock TicketRepository ticketRepository;
    @Mock DossierRepository dossierRepository;
    @Mock CommentRepository commentRepository;
    @Mock UserViewJpaRepository userDirectory;
    @Mock DossierJpaRepository dossierDirectory;

    private TicketController controller() {
        return new TicketController(createTicketUseCase, updateTicketUseCase,
                transitionTicketUseCase, searchTicketsUseCase, ticketRepository,
                dossierRepository, commentRepository, userDirectory, dossierDirectory);
    }

    private static UserViewEntity userView(UUID id, UUID workspaceId, String first, String last) {
        UserViewEntity u = mock(UserViewEntity.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.getWorkspaceId()).thenReturn(workspaceId);
        lenient().when(u.getFirstName()).thenReturn(first);
        lenient().when(u.getLastName()).thenReturn(last);
        return u;
    }

    private static DossierJpaRepository.DossierResponsableView dossierResponsableView(UUID id, UUID responsableId) {
        DossierJpaRepository.DossierResponsableView v = mock(DossierJpaRepository.DossierResponsableView.class);
        lenient().when(v.getId()).thenReturn(id);
        lenient().when(v.getResponsableId()).thenReturn(responsableId);
        return v;
    }

    private static AuthenticatedUser user(Role role, UUID id) {
        return new AuthenticatedUser(id, UUID.randomUUID(), "x@jurika.ma", role);
    }

    private static AuthenticatedUser user(Role role, UUID id, UUID workspaceId) {
        return new AuthenticatedUser(id, workspaceId, "x@jurika.ma", role);
    }

    private TicketFilter captureSearchFilter(AuthenticatedUser actor) {
        when(searchTicketsUseCase.execute(any(), anyInt(), anyInt()))
                .thenReturn(new SearchTicketsUseCase.Result(List.of(), 0));
        controller().search(actor, null, null, null, null, null, null, "createdAt", true, 20, 0);
        ArgumentCaptor<TicketFilter> captor = ArgumentCaptor.forClass(TicketFilter.class);
        verify(searchTicketsUseCase).execute(captor.capture(), anyInt(), anyInt());
        return captor.getValue();
    }

    @Test
    void search_employe_propageResponsableId_pourVoirSesDossiers() {
        UUID me = UUID.randomUUID();
        TicketFilter f = captureSearchFilter(user(Role.EMPLOYE, me));

        // (assigne_id = me OR cree_par_id = me OR dossier.responsable_id = me)
        assertThat(f.assigneOrCreeParId()).isEqualTo(me);
        assertThat(f.responsableId()).isEqualTo(me);
    }

    @Test
    void search_superviseur_nApplique_niAssigne_niResponsable() {
        TicketFilter f = captureSearchFilter(user(Role.SUPERVISEUR, UUID.randomUUID()));

        assertThat(f.assigneOrCreeParId()).isNull();
        assertThat(f.responsableId()).isNull();
    }

    @Test
    void search_superviseur_resout_leNomDuResponsable_dansLeDto() {
        UUID ws = UUID.randomUUID();
        UUID assigne = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket assigned = new Ticket(ticketId, ws, "T-2026-00042", "Creation SARL",
                TicketType.CREATION, TicketStatut.GENERATION_DOCUMENTS, TicketPriorite.NORMALE,
                UUID.randomUUID(), assigne, UUID.randomUUID(),
                null, null, null, Instant.now(), null, null, Instant.now());
        when(searchTicketsUseCase.execute(any(), anyInt(), anyInt()))
                .thenReturn(new SearchTicketsUseCase.Result(List.of(assigned), 1));

        UserViewEntity karim = userView(assigne, ws, "Karim", "Bennani");
        when(userDirectory.findAllByWorkspaceIdAndIdIn(eq(ws), any()))
                .thenReturn(List.of(karim));

        var page = controller().search(user(Role.SUPERVISEUR, UUID.randomUUID(), ws),
                null, null, null, null, null, null, "createdAt", true, 20, 0);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).assignePrenom()).isEqualTo("Karim");
        assertThat(page.items().get(0).assigneNom()).isEqualTo("Bennani");
    }

    @Test
    void search_sansAssigne_retombe_surLeResponsableDuDossier() {
        UUID ws = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        UUID responsable = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        // Ticket SANS assigne_id mais porte par un dossier ayant un responsable.
        Ticket t = new Ticket(ticketId, ws, "T-2026-00050", "Modification",
                TicketType.MODIFICATION, TicketStatut.GENERATION_DOCUMENTS, TicketPriorite.NORMALE,
                dossierId, null, UUID.randomUUID(),
                null, null, null, Instant.now(), null, null, Instant.now());
        when(searchTicketsUseCase.execute(any(), anyInt(), anyInt()))
                .thenReturn(new SearchTicketsUseCase.Result(List.of(t), 1));

        var view = dossierResponsableView(dossierId, responsable);
        var nadia = userView(responsable, ws, "Nadia", "El Fassi");
        when(dossierDirectory.findResponsablesByWorkspaceAndIdIn(eq(ws), any()))
                .thenReturn(List.of(view));
        when(userDirectory.findAllByWorkspaceIdAndIdIn(eq(ws), any()))
                .thenReturn(List.of(nadia));

        var page = controller().search(user(Role.SUPERVISEUR, UUID.randomUUID(), ws),
                null, null, null, null, null, null, "createdAt", true, 20, 0);

        assertThat(page.items().get(0).assignePrenom()).isEqualTo("Nadia");
        assertThat(page.items().get(0).assigneNom()).isEqualTo("El Fassi");
    }

    @Test
    void search_nouveauSansAssigneNiDossier_retombe_surLeCreateur() {
        UUID ws = UUID.randomUUID();
        UUID createur = UUID.randomUUID();
        // Ticket NOUVEAU sans assigne_id ni dossier : le createur est le responsable.
        Ticket nouveau = new Ticket(UUID.randomUUID(), ws, "T-2026-00043", "Import",
                TicketType.IMPORT, TicketStatut.CREATION_TICKET, TicketPriorite.NORMALE,
                null, null, createur,
                null, null, null, Instant.now(), null, null, Instant.now());
        when(searchTicketsUseCase.execute(any(), anyInt(), anyInt()))
                .thenReturn(new SearchTicketsUseCase.Result(List.of(nouveau), 1));

        var youssef = userView(createur, ws, "Youssef", "Alaoui");
        when(userDirectory.findAllByWorkspaceIdAndIdIn(eq(ws), any()))
                .thenReturn(List.of(youssef));

        var page = controller().search(user(Role.SUPERVISEUR, UUID.randomUUID(), ws),
                null, null, null, null, null, null, "createdAt", true, 20, 0);

        assertThat(page.items().get(0).assignePrenom()).isEqualTo("Youssef");
        assertThat(page.items().get(0).assigneNom()).isEqualTo("Alaoui");
    }

    @Test
    void search_responsableInconnu_donneUnNomNull_jamaisUnUuid() {
        UUID ws = UUID.randomUUID();
        // Createur purge : introuvable dans l'annuaire -> nom null -> "Non assigne".
        Ticket orphan = new Ticket(UUID.randomUUID(), ws, "T-2026-00044", "Import",
                TicketType.IMPORT, TicketStatut.CREATION_TICKET, TicketPriorite.NORMALE,
                null, null, UUID.randomUUID(),
                null, null, null, Instant.now(), null, null, Instant.now());
        when(searchTicketsUseCase.execute(any(), anyInt(), anyInt()))
                .thenReturn(new SearchTicketsUseCase.Result(List.of(orphan), 1));
        when(userDirectory.findAllByWorkspaceIdAndIdIn(eq(ws), any()))
                .thenReturn(List.of()); // compte purge

        var page = controller().search(user(Role.SUPERVISEUR, UUID.randomUUID(), ws),
                null, null, null, null, null, null, "createdAt", true, 20, 0);

        assertThat(page.items().get(0).assignePrenom()).isNull();
        assertThat(page.items().get(0).assigneNom()).isNull();
    }

    @Test
    void search_transfert_afficheLeNouveauResponsableDuDossier_paLancienAssigne() {
        UUID ws = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        UUID ancienAssigne = UUID.randomUUID();
        UUID nouveauResponsable = UUID.randomUUID();
        // Ticket repris via transfert : garde son ancien assigne_id, mais le dossier
        // a un NOUVEAU responsable -> c'est lui qui doit s'afficher.
        Ticket transfere = new Ticket(UUID.randomUUID(), ws, "T-2026-00045", "Modification",
                TicketType.MODIFICATION, TicketStatut.GENERATION_DOCUMENTS, TicketPriorite.NORMALE,
                dossierId, ancienAssigne, UUID.randomUUID(),
                null, null, null, Instant.now(), null, null, Instant.now());
        when(searchTicketsUseCase.execute(any(), anyInt(), anyInt()))
                .thenReturn(new SearchTicketsUseCase.Result(List.of(transfere), 1));

        var view = dossierResponsableView(dossierId, nouveauResponsable);
        var sara = userView(nouveauResponsable, ws, "Sara", "Chraibi");
        when(dossierDirectory.findResponsablesByWorkspaceAndIdIn(eq(ws), any()))
                .thenReturn(List.of(view));
        when(userDirectory.findAllByWorkspaceIdAndIdIn(eq(ws), any()))
                .thenReturn(List.of(sara));

        var page = controller().search(user(Role.SUPERVISEUR, UUID.randomUUID(), ws),
                null, null, null, null, null, null, "createdAt", true, 20, 0);

        // Le nouveau responsable (dossier) prime sur l'ancien assigne (transfert-aware).
        assertThat(page.items().get(0).assignePrenom()).isEqualTo("Sara");
        assertThat(page.items().get(0).assigneNom()).isEqualTo("Chraibi");
    }

    @Test
    void get_ticketClotureDunDossierTransfere_estVisibleParLeNouveauResponsable() {
        UUID ws = UUID.randomUUID();
        UUID nouveau = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        // Ticket CLOTURE assigne a l'ANCIEN employe (non reassigne au transfert).
        Ticket clos = new Ticket(ticketId, ws, "T-2026-00009", "Creation SARL",
                TicketType.CREATION, TicketStatut.CLOTURE_DOSSIER, TicketPriorite.NORMALE,
                dossierId, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, Instant.now(), null, null, Instant.now());
        when(ticketRepository.findById(ws, ticketId)).thenReturn(Optional.of(clos));

        // Le dossier est desormais sous la responsabilite du NOUVEL employe.
        EntrepriseDossier dossier = EntrepriseDossier.creationStub(
                ws, "ACME SARL", FormeJuridique.SARL, nouveau);
        when(dossierRepository.findById(ws, dossierId))
                .thenReturn(Optional.of(dossier));

        var dto = controller().get(user(Role.EMPLOYE, nouveau, ws), ticketId);
        assertThat(dto.id()).isEqualTo(ticketId);
    }

    @Test
    void get_autreEmploye_niAssigne_niResponsable_est404() {
        UUID ws = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket clos = new Ticket(ticketId, ws, "T-2026-00010", "Creation SARL",
                TicketType.CREATION, TicketStatut.CLOTURE_DOSSIER, TicketPriorite.NORMALE,
                dossierId, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, Instant.now(), null, null, Instant.now());
        when(ticketRepository.findById(ws, ticketId)).thenReturn(Optional.of(clos));

        // Dossier appartenant a quelqu'un d'autre -> pas de fuite (RG-U08).
        EntrepriseDossier dossier = EntrepriseDossier.creationStub(
                ws, "ACME SARL", FormeJuridique.SARL, UUID.randomUUID());
        when(dossierRepository.findById(ws, dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> controller().get(user(Role.EMPLOYE, autre, ws), ticketId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_ticketRepris_exposeLeMotifDeRepriseLePlusRecent() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        // Ticket repris : EN_COURS, assigne a l'employe (RG-U08 ok).
        Ticket repris = new Ticket(ticketId, ws, "T-2026-00011", "Creation SARL",
                TicketType.CREATION, TicketStatut.GENERATION_DOCUMENTS, TicketPriorite.NORMALE,
                null, employe, employe, null, null, "Annulation initiale", null, null, Instant.now());
        when(ticketRepository.findById(ws, ticketId)).thenReturn(Optional.of(repris));

        // Historique (deja trie du plus recent au plus ancien par l'adapter) :
        // une reprise recente, une annulation, une premiere reprise plus ancienne.
        when(commentRepository.findByTicket(ws, ticketId)).thenReturn(List.of(
                comment(ws, ticketId, "Reprise pour complement de piece", "ANNULE", "GENERATION_DOCUMENTS"),
                comment(ws, ticketId, "Annulation initiale", "GENERATION_DOCUMENTS", "ANNULE"),
                comment(ws, ticketId, "Ancienne reprise", "ANNULE", "GENERATION_DOCUMENTS")));

        var dto = controller().get(user(Role.EMPLOYE, employe, ws), ticketId);
        assertThat(dto.repriseMotif()).isEqualTo("Reprise pour complement de piece");
    }

    @Test
    void get_ticketAnnule_nExposePasDeMotifDeReprise() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket annule = new Ticket(ticketId, ws, "T-2026-00012", "Creation SARL",
                TicketType.CREATION, TicketStatut.ANNULE, TicketPriorite.NORMALE,
                null, employe, employe, null, null, "Doublon", null, Instant.now(), Instant.now());
        when(ticketRepository.findById(ws, ticketId)).thenReturn(Optional.of(annule));

        var dto = controller().get(user(Role.EMPLOYE, employe, ws), ticketId);
        assertThat(dto.repriseMotif()).isNull();
        // Statut ANNULE : on n'interroge meme pas les commentaires.
        verify(commentRepository, org.mockito.Mockito.never()).findByTicket(any(), any());
    }

    private static ma.jurika.ticket.domain.model.TicketComment comment(
            UUID ws, UUID ticketId, String contenu, String from, String to) {
        return new ma.jurika.ticket.domain.model.TicketComment(
                UUID.randomUUID(), ws, ticketId, UUID.randomUUID(),
                ma.jurika.ticket.domain.model.TicketCommentType.TRANSITION_STATUT,
                contenu, java.util.Map.of("from", from, "to", to), Instant.now());
    }
}
