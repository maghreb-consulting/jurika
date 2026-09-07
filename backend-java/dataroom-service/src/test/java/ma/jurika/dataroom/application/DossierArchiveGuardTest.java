package ma.jurika.dataroom.application;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Lot DIVERS §A (2026-08-13) — regle « societe dissoute -> Data Room en lecture
 * seule », et sa seule derogation : les depots du workflow LIQUIDATION.
 */
@ExtendWith(MockitoExtension.class)
class DossierArchiveGuardTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();

    @Mock private DossierViewJpaRepository dossiers;
    @Mock private TicketViewJpaRepository tickets;

    private DossierArchiveGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DossierArchiveGuard(dossiers, tickets);
        TenantContext.set(WS);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void dossierStatut(String statut) {
        DossierViewEntity d = org.mockito.Mockito.mock(DossierViewEntity.class);
        lenient().when(d.getStatut()).thenReturn(statut);
        lenient().when(dossiers.findById(DOSSIER)).thenReturn(Optional.of(d));
    }

    private TicketViewEntity ticket(String type, UUID dossierId) {
        TicketViewEntity t = org.mockito.Mockito.mock(TicketViewEntity.class);
        lenient().when(t.getType()).thenReturn(type);
        lenient().when(t.getDossierId()).thenReturn(dossierId);
        return t;
    }

    // ------------------------------------------------------------------
    //  Lecture seule
    // ------------------------------------------------------------------

    @Test
    void dossierActif_resteEcrivable() {
        dossierStatut("ACTIVE");

        assertThat(guard.isReadOnly(DOSSIER)).isFalse();
        assertThatCode(() -> guard.assertWritable(DOSSIER)).doesNotThrowAnyException();
    }

    @Test
    void dossierDissous_passeEnLectureSeule() {
        dossierStatut("DISSOUTE");

        assertThat(guard.isReadOnly(DOSSIER)).isTrue();
        assertThatThrownBy(() -> guard.assertWritable(DOSSIER))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("DOSSIER_ARCHIVED_READ_ONLY")
                .hasMessageContaining("DISSOUTE");
    }

    @Test
    void tousLesStatutsArchivants_sontCouverts() {
        for (String statut : DossierArchiveGuard.ARCHIVED_STATUS) {
            dossierStatut(statut);
            assertThat(guard.isReadOnly(DOSSIER))
                    .as("statut %s doit figer la Data Room", statut)
                    .isTrue();
        }
    }

    @Test
    void dossierInconnu_neBloquePas() {
        when(dossiers.findById(DOSSIER)).thenReturn(Optional.empty());

        // Le garde ne doit pas prendre de vitesse les 404 / scoping de l'appelant.
        assertThatCode(() -> guard.assertWritable(DOSSIER)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    //  Derogation LIQUIDATION (par ticket)
    // ------------------------------------------------------------------

    @Test
    void depotPorteParUnTicketLiquidation_estAutoriseSurDossierDissous() {
        dossierStatut("DISSOUTE");
        UUID ticketId = UUID.randomUUID();
        TicketViewEntity t = ticket("LIQUIDATION", DOSSIER);
        when(tickets.findByWorkspaceIdAndId(WS, ticketId)).thenReturn(Optional.of(t));

        assertThatCode(() -> guard.assertWritable(DOSSIER, ticketId))
                .doesNotThrowAnyException();
    }

    @Test
    void ticketLiquidationSansDossierLie_estAutorise() {
        // Le lien ticket -> dossier n'est pose qu'a la finalisation du workflow :
        // les documents sont deposes AVANT, avec dossier_id encore null.
        dossierStatut("DISSOUTE");
        UUID ticketId = UUID.randomUUID();
        TicketViewEntity t = ticket("LIQUIDATION", null);
        when(tickets.findByWorkspaceIdAndId(WS, ticketId)).thenReturn(Optional.of(t));

        assertThatCode(() -> guard.assertWritable(DOSSIER, ticketId))
                .doesNotThrowAnyException();
    }

    @Test
    void ticketDunAutreType_neDerogePas() {
        dossierStatut("DISSOUTE");
        UUID ticketId = UUID.randomUUID();
        TicketViewEntity t = ticket("MODIFICATION", DOSSIER);
        when(tickets.findByWorkspaceIdAndId(WS, ticketId)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> guard.assertWritable(DOSSIER, ticketId))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void ticketLiquidationDunAutreDossier_neDerogePas() {
        dossierStatut("DISSOUTE");
        UUID ticketId = UUID.randomUUID();
        TicketViewEntity t = ticket("LIQUIDATION", UUID.randomUUID());
        when(tickets.findByWorkspaceIdAndId(WS, ticketId)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> guard.assertWritable(DOSSIER, ticketId))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void ticketInconnuDansLeWorkspace_neDerogePas() {
        // Defense-in-depth multi-tenant : un ticketId d'un autre workspace ne doit
        // pas ouvrir la Data Room d'un dossier dissous.
        dossierStatut("DISSOUTE");
        UUID ticketId = UUID.randomUUID();
        when(tickets.findByWorkspaceIdAndId(WS, ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.assertWritable(DOSSIER, ticketId))
                .isInstanceOf(ValidationException.class);
    }

    // ------------------------------------------------------------------
    //  Derogation LIQUIDATION (par dossier — chemins sans ticket)
    // ------------------------------------------------------------------

    @Test
    void archivageIdentite_autoriseSiLiquidationOuverte() {
        dossierStatut("DISSOUTE");
        TicketViewEntity t = ticket("LIQUIDATION", DOSSIER);
        lenient().when(tickets.findAllByDossierIdAndStatutOrderByClotureAtDesc(DOSSIER, "CREATION_TICKET"))
                .thenReturn(List.of());
        lenient().when(tickets.findAllByDossierIdAndStatutOrderByClotureAtDesc(DOSSIER, "GENERATION_DOCUMENTS"))
                .thenReturn(List.of(t));

        assertThatCode(() -> guard.assertWritableForLiquidationCapableWrite(DOSSIER))
                .doesNotThrowAnyException();
    }

    @Test
    void archivageIdentite_refuseSansLiquidationOuverte() {
        dossierStatut("DISSOUTE");
        when(tickets.findAllByDossierIdAndStatutOrderByClotureAtDesc(any(UUID.class), anyString()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> guard.assertWritableForLiquidationCapableWrite(DOSSIER))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("DOSSIER_ARCHIVED_READ_ONLY");
    }

    @Test
    void archivageIdentite_sansDossier_passe() {
        // Cas CREATION : le dossier n'existe pas encore.
        assertThatCode(() -> guard.assertWritableForLiquidationCapableWrite(null))
                .doesNotThrowAnyException();
    }
}
