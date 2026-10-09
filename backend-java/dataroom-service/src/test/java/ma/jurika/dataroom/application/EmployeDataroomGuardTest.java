package ma.jurika.dataroom.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L1, etape E8 : suppression d'un document en Data Room (RG-DR-06) reservee a
 * l'employe responsable du dossier (RG-DOS-01, 404 sinon) qui a recu du superviseur
 * le droit de suppression (403 sinon).
 */
class EmployeDataroomGuardTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID DOC = UUID.randomUUID();
    private static final UUID RESPONSABLE = UUID.randomUUID();

    private final DocumentJpaRepository documents = mock(DocumentJpaRepository.class);
    private final DossierViewJpaRepository dossiers = mock(DossierViewJpaRepository.class);
    private final DroitSuppressionLookup droits = mock(DroitSuppressionLookup.class);
    private final ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository tickets =
            mock(ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository.class);
    private final EmployeDataroomGuard guard = new EmployeDataroomGuard(documents, dossiers, droits, tickets);

    @BeforeEach
    void setUp() {
        TenantContext.set(WS);
        DocumentEntity doc = mock(DocumentEntity.class);
        when(doc.getWorkspaceId()).thenReturn(WS);
        when(doc.getDossierId()).thenReturn(DOSSIER);
        when(documents.findById(DOC)).thenReturn(Optional.of(doc));
        DossierViewEntity d = mock(DossierViewEntity.class);
        when(d.getResponsableId()).thenReturn(RESPONSABLE);
        when(dossiers.findByWorkspaceIdAndId(WS, DOSSIER)).thenReturn(Optional.of(d));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static AuthenticatedUser employe(UUID id) {
        return new AuthenticatedUser(id, WS, "e@x.ma", Role.EMPLOYE);
    }

    @Test
    void responsable_avec_le_droit_supprime() {
        when(droits.aLeDroit(WS, RESPONSABLE)).thenReturn(true);
        assertThatCode(() -> guard.assertPeutSupprimerDocument(DOC, employe(RESPONSABLE))).doesNotThrowAnyException();
    }

    @Test
    void responsable_sans_le_droit_403() {
        when(droits.aLeDroit(WS, RESPONSABLE)).thenReturn(false);
        assertThatThrownBy(() -> guard.assertPeutSupprimerDocument(DOC, employe(RESPONSABLE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("droit de suppression");
    }

    @Test
    void employe_non_responsable_404_meme_avec_le_droit() {
        UUID autre = UUID.randomUUID();
        when(droits.aLeDroit(WS, autre)).thenReturn(true);
        assertThatThrownBy(() -> guard.assertPeutSupprimerDocument(DOC, employe(autre)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void document_d_un_autre_cabinet_404() {
        TenantContext.set(UUID.randomUUID());
        assertThatThrownBy(() -> guard.assertPeutSupprimerDocument(DOC, employe(RESPONSABLE)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void superviseur_ne_supprime_pas() {
        assertThatThrownBy(() -> guard.assertPeutSupprimerDocument(DOC,
                new AuthenticatedUser(RESPONSABLE, WS, "s@x.ma", Role.SUPERVISEUR)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Lot L1, etape E9 : acces directs (RG-DOS-01) ----

    @Test
    void acces_direct_au_dossier_d_un_autre_employe_404_superviseur_et_client_non_concernes() {
        assertThatCode(() -> guard.assertResponsable(DOSSIER, employe(RESPONSABLE))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertResponsable(DOSSIER, employe(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
        assertThatCode(() -> guard.assertResponsable(DOSSIER,
                new AuthenticatedUser(UUID.randomUUID(), WS, "s@x.ma", Role.SUPERVISEUR))).doesNotThrowAnyException();
    }

    @Test
    void acces_direct_par_ticket() {
        UUID ticket = UUID.randomUUID();
        var t = mock(ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity.class);
        when(t.getDossierId()).thenReturn(DOSSIER);
        when(tickets.findByWorkspaceIdAndId(WS, ticket)).thenReturn(Optional.of(t));
        assertThatCode(() -> guard.assertResponsableDuTicket(ticket, employe(RESPONSABLE))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertResponsableDuTicket(ticket, employe(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> guard.assertResponsableDuTicket(UUID.randomUUID(), employe(RESPONSABLE)))
                .isInstanceOf(NotFoundException.class);
    }
}
