package ma.jurika.ticket.application;

import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Points de controle du guide (onglet 2), evalues cote serveur.
 *
 * <p>Enonce, test 4 : passer en « Cloture de dossier » alors qu'une demarche
 * obligatoire n'est pas cochee doit etre REFUSE — et le refus doit nommer la
 * demarche fautive, sans quoi l'employe ne sait pas quoi faire.
 */
class GuideTransitionChecksTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();

    private final Map<Integer, Demarche> referentiel = new LinkedHashMap<>();
    private final Map<UUID, TicketDemarche> etats = new HashMap<>();
    private EntrepriseDossier dossier;
    private GuideTransitionChecks checks;

    private Demarche demarche(int ordre, TicketStatut statut, boolean obligatoire, String variables) {
        Demarche d = new Demarche(UUID.randomUUID(), "CREATION", ordre, "P5", "P5 Fiscal / RC",
                "Etape " + ordre, statut, "Cabinet", "DGI", obligatoire,
                obligatoire ? "Tous dossiers" : "Si applicable",
                null, null, "Justificatif " + ordre, null, null, null, variables,
                null, null, null, List.of());
        referentiel.put(ordre, d);
        return d;
    }

    private void marquer(int ordre, DemarcheEtat etat) {
        Demarche d = referentiel.get(ordre);
        etats.put(d.id(), new TicketDemarche(UUID.randomUUID(), null, etat,
                etat == DemarcheEtat.NON_APPLICABLE ? "Sans objet" : null,
                UUID.randomUUID(), Instant.now(), List.of()));
    }

    private Ticket ticket(TicketStatut statut) {
        return new Ticket(TICKET, WS, "T-2026-00841", "Creation SARL Atlas", TicketType.CREATION,
                statut, TicketPriorite.NORMALE, DOSSIER, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, null, null, Instant.now());
    }

    @BeforeEach
    void setUp() {
        dossier = new EntrepriseDossier(DOSSIER, WS, "ATLAS TRADING", FormeJuridique.SARL,
                null, null, null, null, null, null, "12 rue Demo", "Casablanca",
                100000d, null, DossierStatut.EN_CONSTITUTION, null, null,
                Instant.now(), Instant.now());

        DemarcheReferentielRepository ref = new DemarcheReferentielRepository() {
            @Override
            public List<Demarche> findByWorkflow(String workflowType) {
                return List.copyOf(referentiel.values());
            }

            @Override
            public List<Demarche> findByWorkflowAndStatut(String workflowType, TicketStatut statut) {
                return referentiel.values().stream().filter(d -> d.statutTicket() == statut).toList();
            }

            @Override
            public Optional<Demarche> findByWorkflowAndOrdre(String workflowType, int ordre) {
                return Optional.ofNullable(referentiel.get(ordre));
            }
        };

        TicketDemarcheRepository etatsRepo = new TicketDemarcheRepository() {
            @Override
            public Map<UUID, TicketDemarche> findByTicket(UUID workspaceId, UUID ticketId) {
                return Map.copyOf(etats);
            }

            @Override
            public UUID upsert(UUID w, UUID t, UUID d, DemarcheEtat e, String m, UUID a,
                                List<JustificatifDepose> j) {
                throw new UnsupportedOperationException();
            }
        };

        DossierRepository dossiers = mock(DossierRepository.class);
        when(dossiers.findById(any(), any())).thenAnswer(inv -> Optional.of(dossier));

        checks = new GuideTransitionChecks(ref, etatsRepo, dossiers);
    }

    // =================================================================
    //  Enonce, test 4
    // =================================================================

    @Test
    @DisplayName("Cloture refusee tant qu'une demarche obligatoire n'est pas cochee, et elle est nommee")
    void clotureRefuseeSiObligatoireNonCochee() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true, null);
        demarche(24, TicketStatut.DEROULEMENT_DEMARCHE, true, null);
        marquer(21, DemarcheEtat.COCHEE);

        List<String> obstacles = checks.obstacles(
                ticket(TicketStatut.DEROULEMENT_DEMARCHE), TicketStatut.CLOTURE_DOSSIER);

        assertThat(obstacles).hasSize(1);
        assertThat(obstacles.get(0))
                .contains("Demarche 24")
                .contains("obligatoire")
                .contains("non cochee");
    }

    @Test
    @DisplayName("Une conditionnelle ni cochee ni ecartee bloque aussi, avec un message distinct")
    void conditionnelleNonTraiteeBloque() {
        demarche(30, TicketStatut.DEROULEMENT_DEMARCHE, false, null);

        List<String> obstacles = checks.obstacles(
                ticket(TicketStatut.DEROULEMENT_DEMARCHE), TicketStatut.CLOTURE_DOSSIER);

        assertThat(obstacles).singleElement().asString()
                .contains("conditionnelle")
                .contains("ni cochee ni ecartee");
    }

    @Test
    @DisplayName("Une conditionnelle explicitement ecartee ne bloque pas")
    void conditionnelleEcarteeNeBloquePas() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true, null);
        demarche(30, TicketStatut.DEROULEMENT_DEMARCHE, false, null);
        marquer(21, DemarcheEtat.COCHEE);
        marquer(30, DemarcheEtat.NON_APPLICABLE);

        assertThat(checks.obstacles(ticket(TicketStatut.DEROULEMENT_DEMARCHE),
                TicketStatut.CLOTURE_DOSSIER)).isEmpty();
    }

    // =================================================================
    //  Jeu de variables (controle 2 -> 3 du guide)
    // =================================================================

    @Test
    @DisplayName("Une variable produite par une etape cochee mais vide en base bloque le passage")
    void variableManquanteBloque() {
        demarche(4, TicketStatut.GENERATION_DOCUMENTS, true, "$DENOMINATION, $SIEGE_ADRESSE");
        marquer(4, DemarcheEtat.COCHEE);
        dossier = new EntrepriseDossier(DOSSIER, WS, "ATLAS TRADING", FormeJuridique.SARL,
                null, null, null, null, null, null, "   ", "Casablanca",
                100000d, null, DossierStatut.EN_CONSTITUTION, null, null,
                Instant.now(), Instant.now());

        List<String> obstacles = checks.obstacles(
                ticket(TicketStatut.GENERATION_DOCUMENTS), TicketStatut.DEROULEMENT_DEMARCHE);

        assertThat(obstacles).singleElement().asString()
                .contains("$SIEGE_ADRESSE")
                .contains("jeu de variables complet");
    }

    @Test
    @DisplayName("Les variables d'une etape NON cochee ne sont pas exigees")
    void variablesDUneEtapeNonFaiteNonExigees() {
        demarche(4, TicketStatut.GENERATION_DOCUMENTS, false, "$ICE");

        List<String> obstacles = checks.obstacles(
                ticket(TicketStatut.GENERATION_DOCUMENTS), TicketStatut.DEROULEMENT_DEMARCHE);

        assertThat(obstacles).as("seule la demarche non traitee bloque, pas sa variable")
                .singleElement().asString().contains("ni cochee ni ecartee");
    }

    @Test
    @DisplayName("Les familles a joker ne sont jamais exigees : le guide ne dit pas combien")
    void famillesAJokerIgnorees() {
        demarche(2, TicketStatut.GENERATION_DOCUMENTS, true, "$ASSOCIE_*, $GERANT_*");
        marquer(2, DemarcheEtat.COCHEE);

        assertThat(checks.obstacles(ticket(TicketStatut.GENERATION_DOCUMENTS),
                TicketStatut.DEROULEMENT_DEMARCHE)).isEmpty();
    }

    @Test
    @DisplayName("Les variables sans contrepartie en base sont signalees, jamais bloquantes")
    void variablesNonVerifiablesSignalees() {
        demarche(4, TicketStatut.GENERATION_DOCUMENTS, true,
                "$CERTIFICAT_NEGATIF_NUMERO, $DENOMINATION, $SIGLE");
        marquer(4, DemarcheEtat.COCHEE);

        assertThat(checks.obstacles(ticket(TicketStatut.GENERATION_DOCUMENTS),
                TicketStatut.DEROULEMENT_DEMARCHE)).isEmpty();
        assertThat(checks.variablesNonVerifiables(ticket(TicketStatut.GENERATION_DOCUMENTS)))
                .containsExactlyInAnyOrder("$CERTIFICAT_NEGATIF_NUMERO", "$SIGLE");
    }

    // =================================================================
    //  Perimetre
    // =================================================================

    @Test
    @DisplayName("L'annulation n'attend aucune demarche")
    void annulationSansObstacle() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true, null);

        assertThat(checks.obstacles(ticket(TicketStatut.DEROULEMENT_DEMARCHE),
                TicketStatut.ANNULE)).isEmpty();
    }

    @Test
    @DisplayName("La reprise d'un ticket annule ne rejoue pas les controles du statut vise")
    void repriseSansObstacle() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true, null);

        assertThat(checks.obstacles(ticket(TicketStatut.ANNULE),
                TicketStatut.CLOTURE_DOSSIER)).isEmpty();
    }

    @Test
    @DisplayName("Un workflow sans referentiel ne produit aucun obstacle")
    void workflowSansReferentiel() {
        Ticket dissolution = new Ticket(TICKET, WS, "T-2026-00900", "Dissolution",
                TicketType.DISSOLUTION, TicketStatut.DEROULEMENT_DEMARCHE, TicketPriorite.NORMALE,
                DOSSIER, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, null, null, Instant.now());

        assertThat(checks.obstacles(dissolution, TicketStatut.CLOTURE_DOSSIER)).isEmpty();
    }
}
