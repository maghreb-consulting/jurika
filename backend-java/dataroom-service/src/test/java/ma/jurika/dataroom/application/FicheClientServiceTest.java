package ma.jurika.dataroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.UserViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressRow;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.access.AccessDeniedException;

/**
 * Assemblage de la Fiche client : contrôle d'accès (responsable), sous-type
 * précis des modifications, documents en vigueur (courants uniquement),
 * résolution de l'auteur.
 */
class FicheClientServiceTest {

    private final DocumentJpaRepository documents = mock(DocumentJpaRepository.class);
    private final DossierViewJpaRepository dossiers = mock(DossierViewJpaRepository.class);
    private final TicketViewJpaRepository tickets = mock(TicketViewJpaRepository.class);
    private final WorkflowProgressViewJpaRepository workflows = mock(WorkflowProgressViewJpaRepository.class);
    private final UserViewJpaRepository users = mock(UserViewJpaRepository.class);
    private final WorkspaceViewJpaRepository workspaces = mock(WorkspaceViewJpaRepository.class);

    private FicheClientService service;

    private final UUID ws = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private final UUID responsable = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FicheClientService(documents, dossiers, tickets, workflows,
                users, workspaces, new ObjectMapper());
        TenantContext.set(ws);
        // Important : construire chaque double AVANT le when(...) qui l'utilise,
        // sinon Mockito lève « unfinished stubbing » (when() imbriqués).
        WorkspaceViewEntity wsMock = workspace("Maghreb Consulting");
        lenient().when(workspaces.findById(ws)).thenReturn(Optional.of(wsMock));
        lenient().when(tickets.findAllByDossierIdAndStatutNot(dossierId, "ANNULE"))
                .thenReturn(List.of());
        lenient().when(documents.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossierId))
                .thenReturn(List.of());
        lenient().when(workflows.findRowsByWorkspaceAndTickets(any(), any()))
                .thenReturn(List.of());
        lenient().when(users.findAllByWorkspaceIdAndIdIn(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Accès ─────────────────────────────────────────────────────────────

    @Test
    void employe_non_responsable_est_refuse() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.assemble(dossierId, Role.EMPLOYE, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void employe_responsable_est_autorise() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        FicheClientView v = service.assemble(dossierId, Role.EMPLOYE, responsable);
        assertThat(v.identity().raisonSociale()).isEqualTo("ACME SARL");
        assertThat(v.cabinet().name()).isEqualTo("Maghreb Consulting");
    }

    @Test
    void client_est_refuse_meme_en_service() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.assemble(dossierId, Role.CLIENT, responsable))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superviseur_est_autorise_sans_etre_responsable() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        FicheClientView v = service.assemble(dossierId, Role.SUPERVISEUR, UUID.randomUUID());
        assertThat(v).isNotNull();
    }

    @Test
    void utilise_le_nom_affiche_du_cabinet_si_present() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        WorkspaceViewEntity wsMock = mock(WorkspaceViewEntity.class);
        lenient().when(wsMock.getNomAfficheDocuments()).thenReturn("Cabinet Alaoui");
        lenient().when(wsMock.getName()).thenReturn("Maghreb Consulting");
        when(workspaces.findById(ws)).thenReturn(Optional.of(wsMock));
        FicheClientView v = service.assemble(dossierId, Role.SUPERVISEUR, null);
        assertThat(v.cabinet().name()).isEqualTo("Cabinet Alaoui");
    }

    // ── Section 2 : sous-type + dates ──────────────────────────────────────

    @Test
    void modification_expose_le_sous_type_precis_et_la_date_d_acte() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        UUID ticketId = UUID.randomUUID();
        TicketViewEntity t = ticket(ticketId, "MODIFICATION", "CLOTURE_DOSSIER", "TCK-14",
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"));
        when(tickets.findAllByDossierIdAndStatutNot(dossierId, "ANNULE")).thenReturn(List.of(t));
        String dataJson = "{\"step1\":{\"selectedTypes\":[\"DESIGNATION_GERANT\"],"
                + "\"datePV\":\"2026-03-15\"}}";
        WorkflowProgressRow r = row(ticketId, "MODIFICATION", "TERMINE", dataJson);
        when(workflows.findRowsByWorkspaceAndTickets(eq(ws), any())).thenReturn(List.of(r));

        FicheClientView v = service.assemble(dossierId, Role.SUPERVISEUR, null);
        assertThat(v.operations()).hasSize(1);
        Operation op = v.operations().get(0);
        assertThat(op.typeLabel()).isEqualTo("Modification statutaire");
        assertThat(op.sousTypeLabel()).contains("nomination d'un gérant");
        assertThat(op.dateActe()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(op.finalisee()).isTrue();
    }

    @Test
    void operation_en_cours_a_une_finalisation_nulle() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        UUID ticketId = UUID.randomUUID();
        TicketViewEntity t = ticket(ticketId, "DISSOLUTION", "GENERATION_DOCUMENTS", "TCK-88",
                Instant.parse("2026-05-01T00:00:00Z"), null);
        when(tickets.findAllByDossierIdAndStatutNot(dossierId, "ANNULE")).thenReturn(List.of(t));

        FicheClientView v = service.assemble(dossierId, Role.SUPERVISEUR, null);
        Operation op = v.operations().get(0);
        assertThat(op.finalisee()).isFalse();
        assertThat(op.dateFinalisation()).isNull();
        assertThat(op.typeLabel()).isEqualTo("Dissolution");
    }

    // ── Section 3/4 : documents ────────────────────────────────────────────

    @Test
    void documents_en_vigueur_ne_contient_que_les_versions_courantes() {
        DossierViewEntity d = dossier("ACTIVE", responsable);
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        UUID author = UUID.randomUUID();
        DocumentEntity current = doc("STATUTS", "Statuts", (short) 2, true, author, Instant.now(), null);
        DocumentEntity old = doc("STATUTS", "Statuts", (short) 1, false, author,
                Instant.now().minusSeconds(1000), "Remplacé");
        when(documents.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossierId))
                .thenReturn(List.of(current, old));
        UserViewEntity u = user(author, "Karim", "Alaoui");
        when(users.findAllByWorkspaceIdAndIdIn(eq(ws), any())).thenReturn(List.of(u));

        FicheClientView v = service.assemble(dossierId, Role.SUPERVISEUR, null);
        assertThat(v.documentsEnVigueur()).hasSize(1);
        assertThat(v.documentsEnVigueur().get(0).version()).isEqualTo((short) 2);
        // L'historique conserve les deux versions, avec l'auteur résolu.
        assertThat(v.historiqueDocuments()).hasSize(2);
        assertThat(v.historiqueDocuments()).anySatisfy(h -> {
            assertThat(h.auteur()).isEqualTo("Karim Alaoui");
        });
        assertThat(v.historiqueDocuments()).anySatisfy(h ->
                assertThat(h.action()).isEqualTo("AJOUT"));
        assertThat(v.historiqueDocuments()).anySatisfy(h ->
                assertThat(h.action()).isEqualTo("REMPLACEMENT"));
    }

    // ── Fabriques de doubles ───────────────────────────────────────────────

    private DossierViewEntity dossier(String statut, UUID resp) {
        DossierViewEntity d = mock(DossierViewEntity.class);
        lenient().when(d.getRaisonSociale()).thenReturn("ACME SARL");
        lenient().when(d.getFormeJuridique()).thenReturn("SARL");
        lenient().when(d.getStatut()).thenReturn(statut);
        lenient().when(d.getResponsableId()).thenReturn(resp);
        return d;
    }

    private TicketViewEntity ticket(UUID id, String type, String statut, String ref,
                                    Instant created, Instant cloture) {
        TicketViewEntity t = mock(TicketViewEntity.class);
        lenient().when(t.getId()).thenReturn(id);
        lenient().when(t.getType()).thenReturn(type);
        lenient().when(t.getStatut()).thenReturn(statut);
        lenient().when(t.getReference()).thenReturn(ref);
        lenient().when(t.getCreatedAt()).thenReturn(created);
        lenient().when(t.getClotureAt()).thenReturn(cloture);
        return t;
    }

    private WorkflowProgressRow row(UUID ticketId, String type, String statut, String dataJson) {
        WorkflowProgressRow r = mock(WorkflowProgressRow.class);
        lenient().when(r.getTicketId()).thenReturn(ticketId);
        lenient().when(r.getWorkflowType()).thenReturn(type);
        lenient().when(r.getStatut()).thenReturn(statut);
        lenient().when(r.getDataJson()).thenReturn(dataJson);
        return r;
    }

    private DocumentEntity doc(String type, String title, short version, boolean current,
                              UUID author, Instant createdAt, String motif) {
        DocumentEntity d = new DocumentEntity();
        d.setWorkspaceId(ws);
        d.setDossierId(dossierId);
        d.setDocumentType(type);
        d.setTitle(title);
        d.setVersion(version);
        d.setCurrent(current);
        d.setUploadedBy(author);
        d.setCreatedAt(createdAt);
        d.setMotif(motif);
        return d;
    }

    private UserViewEntity user(UUID id, String first, String last) {
        UserViewEntity u = mock(UserViewEntity.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.displayName()).thenReturn(first + " " + last);
        return u;
    }

    private WorkspaceViewEntity workspace(String name) {
        WorkspaceViewEntity w = mock(WorkspaceViewEntity.class);
        lenient().when(w.getName()).thenReturn(name);
        return w;
    }
}
