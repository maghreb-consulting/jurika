package ma.jurika.ticket.application;

import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.DemarcheEvenement;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DemarcheJournalRepository;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
 * Lot B — LE RÉCAPITULATIF DE CLÔTURE, ET SURTOUT CE QUI MANQUE.
 *
 * <p>Le statut « Clôture de dossier » ne se contente pas d'un bouton : il montre
 * d'abord ce que le dossier contient. Le point qui compte, et que ces tests
 * éprouvent sur des valeurs, c'est la liste des justificatifs <b>manquants</b> —
 * un récapitulatif qui n'annonce que ce qui est présent laisse clore un dossier
 * incomplet sans que personne s'en aperçoive.
 */
class RecapitulatifClotureServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID ACTEUR = UUID.randomUUID();

    private final Map<Integer, Demarche> referentiel = new LinkedHashMap<>();
    private final Map<UUID, TicketDemarche> etats = new LinkedHashMap<>();
    private final List<DataroomDocumentLookup.DocumentVu> documents = new ArrayList<>();

    private EntrepriseDossier dossier;
    private RecapitulatifClotureService service;

    private Demarche demarche(int ordre, String libelle, List<JustificatifAttendu> attendus) {
        Demarche d = new Demarche(UUID.randomUUID(), "CREATION", ordre, "S3",
                "Déroulement de la démarche", libelle, TicketStatut.DEROULEMENT_DEMARCHE,
                null, null, true, "Tous dossiers", null, null,
                attendus.isEmpty() ? null : attendus.get(0).libelle(), null,
                null, null, null, null, null, null, null, null, null, attendus);
        referentiel.put(ordre, d);
        return d;
    }

    private void marquer(int ordre, DemarcheEtat etat, String motif) {
        Demarche d = referentiel.get(ordre);
        etats.put(d.id(), new TicketDemarche(UUID.randomUUID(), d, etat, motif, ACTEUR,
                etat == DemarcheEtat.A_FAIRE ? null : Instant.now(), List.of()));
    }

    private void document(String type) {
        documents.add(new DataroomDocumentLookup.DocumentVu(
                UUID.randomUUID(), DOSSIER, TICKET, type, "Piece " + type, true));
    }

    @BeforeEach
    void setUp() {
        dossier = new EntrepriseDossier(DOSSIER, WS, "PARACOSME", FormeJuridique.SARL,
                "001234567000089", "RC 445221", "Casablanca", "IF 40221188",
                "TP 77120033", "CNSS 9912004", "101 bd Zerktouni", "Casablanca",
                100000.0, null, DossierStatut.ACTIVE, null, null, Instant.now(), Instant.now());

        TicketRepository tickets = mock(TicketRepository.class);
        when(tickets.findById(any(), any())).thenAnswer(inv -> Optional.of(new Ticket(
                TICKET, WS, "T-2026-00841", "Creation SARL PARACOSME", TicketType.CREATION,
                TicketStatut.DEROULEMENT_DEMARCHE, TicketPriorite.NORMALE, DOSSIER,
                ACTEUR, ACTEUR, null, null, null, null, null, Instant.now())));

        DemarcheReferentielRepository ref = new DemarcheReferentielRepository() {
            @Override
            public List<Demarche> findByWorkflow(String workflowType) {
                return List.copyOf(referentiel.values());
            }

            @Override
            public List<Demarche> findByWorkflowAndStatut(String workflowType, TicketStatut s) {
                return referentiel.values().stream().filter(d -> d.statutTicket() == s).toList();
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
            public UUID upsert(UUID workspaceId, UUID ticketId, UUID demarcheId, DemarcheEtat etat,
                                String motif, UUID acteurId, List<JustificatifDepose> j) {
                throw new UnsupportedOperationException("lecture seule");
            }
        };

        DemarcheJournalRepository journal = new DemarcheJournalRepository() {
            @Override
            public UUID enregistrer(UUID ws, UUID id, DemarcheEvenement.Type t, String m,
                                     UUID a, int j) {
                throw new UnsupportedOperationException("lecture seule");
            }

            @Override
            public Map<UUID, List<DemarcheEvenement>> parDemarche(UUID ws, Collection<UUID> ids) {
                return Map.of();
            }
        };

        DataroomDocumentLookup lookup = new DataroomDocumentLookup() {
            @Override
            public List<DocumentVu> findByIds(UUID ws, List<UUID> ids) {
                return List.of();
            }

            @Override
            public List<DocumentVu> findByTicket(UUID ws, UUID ticketId) {
                return List.copyOf(documents);
            }
        };

        DossierRepository dossiers = mock(DossierRepository.class);
        when(dossiers.findById(any(), any())).thenAnswer(inv -> Optional.of(dossier));

        service = new RecapitulatifClotureService(tickets, ref, etatsRepo, journal, lookup, dossiers);
    }

    // =================================================================

    @Test
    @DisplayName("Le récapitulatif liste les justificatifs MANQUANTS, et les nomme — énoncé 14")
    void listeLesJustificatifsManquants() {
        demarche(19, "Enregistrement des statuts — dépôt",
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Récépissé de dépôt")));
        demarche(28, "Immatriculation — retrait du modèle J",
                List.of(new JustificatifAttendu(1, "RC", "Certificat d'immatriculation")));
        marquer(19, DemarcheEtat.COCHEE, null);
        marquer(28, DemarcheEtat.COCHEE, null);
        document("RECEPISSE_DEPOT");   // le modèle J, lui, n'est pas archivé

        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.manquants())
                .as("un récapitulatif qui n'annonce que le présent laisse clore un dossier troué")
                .hasSize(1)
                .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("Ligne 28")
                .contains("Certificat d'immatriculation")
                .contains("RC");
        assertThat(recap.clotureEnvisageable()).isFalse();
    }

    @Test
    @DisplayName("Une démarche ÉCARTÉE avec motif n'attend aucune pièce : ce n'est pas un manque")
    void demarcheEcarteeNeCreePasDeManque() {
        demarche(39, "Déclaration CNDP — dépôt",
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Accusé de dépôt")));
        marquer(39, DemarcheEtat.NON_APPLICABLE, "La société ne traite aucune donnée personnelle");

        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.manquants())
                .as("réclamer la pièce d'une formalité qu'on a décidé de ne pas faire est un "
                        + "faux manque — et un faux manque fait qu'on cesse de lire la liste")
                .isEmpty();
        assertThat(recap.clotureEnvisageable()).isTrue();
    }

    @Test
    @DisplayName("Un groupe d'ALTERNATIVES est satisfait dès qu'une des pièces est là")
    void alternativesSatisfaitesParUneSeulePiece() {
        demarche(2, "Contrat de bail ou de domiciliation", List.of(
                new JustificatifAttendu(1, "CONTRAT_BAIL", "Contrat signé"),
                new JustificatifAttendu(1, "CONTRAT_DOMICILIATION", "Contrat signé"),
                new JustificatifAttendu(1, "TITRE_PROPRIETE", "Contrat signé")));
        marquer(2, DemarcheEtat.COCHEE, null);
        document("CONTRAT_DOMICILIATION");

        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.manquants())
                .as("annoncer les trois comme manquantes serait faux : l'une suffit")
                .isEmpty();
        assertThat(recap.justificatifs())
                .filteredOn(j -> j.ordreDemarche() == 2)
                .singleElement()
                .satisfies(j -> {
                    assertThat(j.archive()).isTrue();
                    assertThat(j.alternatives())
                            .containsExactlyInAnyOrder("CONTRAT_BAIL", "CONTRAT_DOMICILIATION",
                                    "TITRE_PROPRIETE");
                });
    }

    @Test
    @DisplayName("Deux groupes CUMULATIFS : satisfaire l'un ne dispense pas de l'autre")
    void groupesCumulatifs() {
        demarche(20, "Enregistrement des statuts — retrait", List.of(
                new JustificatifAttendu(1, "STATUTS", "Statuts enregistrés"),
                new JustificatifAttendu(2, "ATTESTATION_ENREGISTREMENT",
                        "Attestation d'enregistrement")));
        marquer(20, DemarcheEtat.COCHEE, null);
        document("STATUTS");

        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.manquants())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("ATTESTATION_ENREGISTREMENT");
    }

    @Test
    @DisplayName("Les cinq identifiants obtenus sont rendus, et comptés")
    void identifiantsObtenus() {
        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.identifiants().rcNumero()).isEqualTo("RC 445221");
        assertThat(recap.identifiants().identifiantFiscal()).isEqualTo("IF 40221188");
        assertThat(recap.identifiants().ice()).isEqualTo("001234567000089");
        assertThat(recap.identifiants().taxeProfessionnelle()).isEqualTo("TP 77120033");
        assertThat(recap.identifiants().cnss()).isEqualTo("CNSS 9912004");
        assertThat(recap.identifiants().obtenus()).isEqualTo(5);
    }

    @Test
    @DisplayName("Les documents produits sont rendus avec leur visibilité client")
    void documentsProduitsAvecVisibilite() {
        documents.add(new DataroomDocumentLookup.DocumentVu(
                UUID.randomUUID(), DOSSIER, TICKET, "AUTRE", "Note interne", false));
        document("STATUTS");

        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.documents())
                .as("remettre un dossier dont des pièces restent masquées est une décision")
                .extracting(RecapitulatifClotureService.DocumentProduit::documentType,
                        RecapitulatifClotureService.DocumentProduit::visibleClient)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("AUTRE", false),
                        org.assertj.core.groups.Tuple.tuple("STATUTS", true));
    }

    @Test
    @DisplayName("Les démarches accomplies sont rendues avec leur date")
    void demarchesAccompliesDatees() {
        demarche(13, "Signature des statuts", List.of());
        demarche(14, "Légalisation des signatures", List.of());
        marquer(13, DemarcheEtat.COCHEE, null);
        // La 14 reste à faire : elle ne figure pas parmi les accomplies.

        var recap = service.recapitulatif(WS, TICKET);

        assertThat(recap.demarches())
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.ordre()).isEqualTo(13);
                    assertThat(d.etat()).isEqualTo(DemarcheEtat.COCHEE);
                    assertThat(d.cocheAt()).isNotNull();
                });
    }
}
