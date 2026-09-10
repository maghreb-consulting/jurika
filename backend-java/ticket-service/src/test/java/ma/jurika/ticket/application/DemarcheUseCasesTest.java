package ma.jurika.ticket.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.DelaiUnite;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.domain.service.EcheanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regles de cochage et points d'attention, verifiees sur des VALEURS.
 *
 * <p>Les doublures sont ecrites a la main plutot que mockees : le referentiel et
 * l'etat des demarches sont des donnees, pas des collaborateurs — les stubber
 * ligne a ligne rendrait les tests illisibles et masquerait ce qui est reellement
 * verifie.
 */
class DemarcheUseCasesTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID ACTEUR = UUID.randomUUID();

    private final Map<Integer, Demarche> referentiel = new LinkedHashMap<>();
    private final Map<UUID, TicketDemarche> etatsCourants = new HashMap<>();
    private final Map<UUID, DataroomDocumentLookup.DocumentVu> dataroom = new HashMap<>();
    private final List<String> journal = new ArrayList<>();

    private TicketStatut statutTicket = TicketStatut.DEROULEMENT_DEMARCHE;
    private DemarcheUseCases useCases;

    // -----------------------------------------------------------------
    //  Fabriques
    // -----------------------------------------------------------------

    private Demarche demarche(int ordre, TicketStatut statut, boolean obligatoire,
                               List<JustificatifAttendu> attendus) {
        return demarche(ordre, statut, obligatoire, attendus, null, null, null);
    }

    private Demarche demarche(int ordre, TicketStatut statut, boolean obligatoire,
                               List<JustificatifAttendu> attendus,
                               Integer delaiValeur, DelaiUnite unite, Integer refOrdre) {
        Demarche d = new Demarche(UUID.randomUUID(), "CREATION", ordre, "P5", "P5 Fiscal / RC",
                "Etape " + ordre, statut, "Cabinet", "DGI", obligatoire,
                obligatoire ? "Tous dossiers" : "Si applicable",
                null, null, "Justificatif de l'etape " + ordre, null,
                "Dans les 30 jours", null, null,
                delaiValeur, unite, refOrdre, attendus);
        referentiel.put(ordre, d);
        return d;
    }

    private UUID document(String type, UUID ticketId) {
        UUID id = UUID.randomUUID();
        dataroom.put(id, new DataroomDocumentLookup.DocumentVu(
                id, UUID.randomUUID(), ticketId, type, "Doc " + type));
        return id;
    }

    private void marquer(int ordre, DemarcheEtat etat, Instant cocheAt) {
        Demarche d = referentiel.get(ordre);
        etatsCourants.put(d.id(), new TicketDemarche(
                UUID.randomUUID(), null, etat, null, ACTEUR, cocheAt, List.of()));
    }

    @BeforeEach
    void setUp() {
        TicketRepository tickets = mock(TicketRepository.class);
        when(tickets.findById(any(), any())).thenAnswer(inv -> Optional.of(new Ticket(
                TICKET, WS, "T-2026-00841", "Creation SARL Atlas", TicketType.CREATION,
                statutTicket, TicketPriorite.NORMALE, UUID.randomUUID(), ACTEUR, ACTEUR,
                null, null, null, null, null, Instant.now())));

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

        TicketDemarcheRepository etats = new TicketDemarcheRepository() {
            @Override
            public Map<UUID, TicketDemarche> findByTicket(UUID workspaceId, UUID ticketId) {
                return Map.copyOf(etatsCourants);
            }

            @Override
            public UUID upsert(UUID workspaceId, UUID ticketId, UUID demarcheId, DemarcheEtat etat,
                                String motif, UUID acteurId, List<JustificatifDepose> justificatifs) {
                UUID id = UUID.randomUUID();
                etatsCourants.put(demarcheId, new TicketDemarche(id, null, etat, motif, acteurId,
                        etat == DemarcheEtat.A_FAIRE ? null : Instant.now(),
                        justificatifs.stream().map(JustificatifDepose::documentId).toList()));
                journal.add(etat + ":" + justificatifs.size());
                return id;
            }
        };

        DataroomDocumentLookup documents = (workspaceId, ids) ->
                ids.stream().map(dataroom::get).filter(java.util.Objects::nonNull).toList();

        CommentRepository comments = mock(CommentRepository.class);

        useCases = new DemarcheUseCases(tickets, ref, etats, documents, comments);
    }

    // =================================================================
    //  Enonce, test 2 : cochage sans justificatif -> refuse
    // =================================================================

    @Test
    @DisplayName("Cocher sans aucun justificatif est refuse, et le message dit lequel manque")
    void cochageSansJustificatifRefuse() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Certificat d'immatriculation")));

        assertThatThrownBy(() -> useCases.cocher(WS, TICKET, 21, List.of(), ACTEUR))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exige un justificatif")
                .hasMessageContaining("Justificatif de l'etape 21");

        assertThat(etatsCourants).as("aucun etat ne doit avoir ete persiste").isEmpty();
    }

    @Test
    @DisplayName("Un document du mauvais type ne valide pas la demarche")
    void cochageAvecMauvaisTypeRefuse() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Certificat d'immatriculation")));
        UUID horsSujet = document("CONTRAT_BAIL", TICKET);

        assertThatThrownBy(() -> useCases.cocher(WS, TICKET, 21, List.of(horsSujet), ACTEUR))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("CONTRAT_BAIL")
                .hasMessageContaining("attendu : RC");
    }

    @Test
    @DisplayName("Un document rattache a un AUTRE ticket ne vaut pas justificatif")
    void documentDUnAutreTicketRefuse() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Certificat d'immatriculation")));
        UUID ailleurs = document("RC", UUID.randomUUID());

        assertThatThrownBy(() -> useCases.cocher(WS, TICKET, 21, List.of(ailleurs), ACTEUR))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("n'est pas rattache a ce ticket");
    }

    @Test
    @DisplayName("Un groupe cumulatif non couvert bloque, meme si l'autre groupe l'est")
    void groupeCumulatifManquant() {
        demarche(17, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of(
                new JustificatifAttendu(1, "STATUTS", "Statuts enregistres"),
                new JustificatifAttendu(2, "ATTESTATION_ENREGISTREMENT", "Attestation")));
        UUID statuts = document("STATUTS", TICKET);

        assertThatThrownBy(() -> useCases.cocher(WS, TICKET, 17, List.of(statuts), ACTEUR))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ATTESTATION_ENREGISTREMENT");
    }

    @Test
    @DisplayName("Une alternative suffit : bail OU domiciliation OU titre de propriete")
    void alternativeSuffit() {
        demarche(5, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of(
                new JustificatifAttendu(1, "CONTRAT_BAIL", "Contrat signe"),
                new JustificatifAttendu(1, "CONTRAT_DOMICILIATION", "Contrat signe"),
                new JustificatifAttendu(1, "TITRE_PROPRIETE", "Contrat signe")));
        UUID domiciliation = document("CONTRAT_DOMICILIATION", TICKET);

        assertThatCode(() -> useCases.cocher(WS, TICKET, 5, List.of(domiciliation), ACTEUR))
                .doesNotThrowAnyException();
        assertThat(journal).containsExactly("COCHEE:1");
    }

    // =================================================================
    //  Enonce, test 3 : obligatoire marquee non applicable -> refuse
    // =================================================================

    @Test
    @DisplayName("Une demarche obligatoire ne peut pas etre marquee non applicable")
    void obligatoireNonApplicableRefuse() {
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Modele J")));

        assertThatThrownBy(() -> useCases.marquerNonApplicable(WS, TICKET, 21,
                "Le client refuse", ACTEUR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("obligatoire");

        assertThat(etatsCourants).isEmpty();
    }

    @Test
    @DisplayName("Une conditionnelle peut etre ecartee, mais le motif est obligatoire")
    void conditionnelleEcarteeAvecMotif() {
        demarche(30, TicketStatut.DEROULEMENT_DEMARCHE, false,
                List.of(new JustificatifAttendu(1, "AUTORISATION_SECTORIELLE", "Agrement")));
        Demarche d = referentiel.get(30);

        assertThatThrownBy(() -> useCases.marquerNonApplicable(WS, TICKET, 30, "  ", ACTEUR))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Motif obligatoire");

        useCases.marquerNonApplicable(WS, TICKET, 30, "Activite non reglementee", ACTEUR);

        TicketDemarche etat = etatsCourants.get(d.id());
        assertThat(etat.etat()).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etat.motif()).isEqualTo("Activite non reglementee");
        assertThat(etat.cocheAt()).as("un etat traite est toujours date").isNotNull();
    }

    @Test
    @DisplayName("Une demarche d'un autre statut que celui du ticket n'est pas actionnable")
    void demarcheHorsStatutCourant() {
        demarche(8, TicketStatut.GENERATION_DOCUMENTS, true,
                List.of(new JustificatifAttendu(1, "STATUTS", "Projet de statuts")));
        UUID statuts = document("STATUTS", TICKET);

        assertThatThrownBy(() -> useCases.cocher(WS, TICKET, 8, List.of(statuts), ACTEUR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("releve du statut GENERATION_DOCUMENTS")
                .hasMessageContaining("le ticket est au statut DEROULEMENT_DEMARCHE");
    }

    // =================================================================
    //  Points d'attention : delais
    // =================================================================

    @Test
    @DisplayName("Tant que l'etape de reference n'est pas cochee, aucune echeance n'est calculee")
    void pointDeDepartNonAtteintAucuneEcheance() {
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "STATUTS", "Statuts signes")));
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Modele J")),
                3, DelaiUnite.MOIS, 13);

        DemarcheUseCases.Vue vue = useCases.vue(WS, TICKET);

        assertThat(vue.avancement().pointsAttention()).isEmpty();
    }

    @Test
    @DisplayName("Une etape de reference NON APPLICABLE ne declenche pas non plus le delai")
    void referenceEcarteeAucuneEcheance() {
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, false,
                List.of(new JustificatifAttendu(1, "STATUTS", "Statuts signes")));
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Modele J")),
                3, DelaiUnite.MOIS, 13);
        marquer(13, DemarcheEtat.NON_APPLICABLE, Instant.now());

        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention()).isEmpty();
    }

    @Test
    @DisplayName("Les etapes 16, 19 et 26 ne levent jamais d'alerte : le guide ne donne aucun depart")
    void etapesSansDelaiCalculableJamaisDAlerte() {
        // Une reference cochee de longue date : si un delai etait calculable, il
        // serait largement depasse.
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        marquer(13, DemarcheEtat.COCHEE, Instant.now().minus(400, ChronoUnit.DAYS));

        for (int ordre : new int[] {16, 19, 26}) {
            demarche(ordre, TicketStatut.DEROULEMENT_DEMARCHE, true,
                    List.of(new JustificatifAttendu(1, "TP", "Attestation")),
                    null, null, null);
        }

        DemarcheUseCases.Vue vue = useCases.vue(WS, TICKET);

        assertThat(vue.avancement().pointsAttention())
                .as("aucun point de depart mecanisable -> aucune alerte, meme tres en retard")
                .isEmpty();
    }

    @Test
    @DisplayName("Delai depasse : severite DEPASSE et date d'echeance exacte")
    void delaiDepasse() {
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Modele J")),
                3, DelaiUnite.MOIS, 13);

        Instant signature = LocalDate.now(EcheanceCalculator.ZONE).minusMonths(4)
                .atStartOfDay(EcheanceCalculator.ZONE).toInstant();
        marquer(13, DemarcheEtat.COCHEE, signature);

        List<DemarcheUseCases.PointAttention> points =
                useCases.vue(WS, TICKET).avancement().pointsAttention();

        assertThat(points).hasSize(1);
        DemarcheUseCases.PointAttention p = points.get(0);
        assertThat(p.ordre()).isEqualTo(21);
        assertThat(p.severite()).isEqualTo("DEPASSE");
        assertThat(p.echeance())
                .isEqualTo(LocalDate.now(EcheanceCalculator.ZONE).minusMonths(4).plusMonths(3));
        assertThat(p.joursRestants()).isNegative();
    }

    /**
     * Cas de l'etape 20 apres l'arbitrage du cabinet.
     *
     * <p>Dans le guide, la declaration d'existence (20) PRECEDE l'immatriculation
     * (21) — le bulletin IF figure parmi les pieces du depot au RC. Mais son delai
     * legal ne court qu'a compter de l'immatriculation. Dans un dossier bien mene,
     * l'etape 20 est donc cochee AVANT que son delai ne commence a courir, et
     * l'alerte ne doit jamais se lever. Le delai n'est qu'un filet de securite.
     */
    @Test
    @DisplayName("Etape 20 cochee avant l'etape 21 : aucune alerte, a aucun moment")
    void etape20CocheeAvantSonPointDeDepart() {
        demarche(20, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "BULLETIN_IF", "Bulletin IF")),
                30, DelaiUnite.JOURS, 21);
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Modele J")),
                3, DelaiUnite.MOIS, 13);
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());

        // 1. Rien n'est fait : le point de depart de 20 n'existe pas.
        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .as("aucun jalon pose").isEmpty();

        // 2. L'etape 20 est accomplie, tres tot dans le dossier.
        marquer(20, DemarcheEtat.COCHEE, Instant.now().minus(120, ChronoUnit.DAYS));
        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .as("une demarche accomplie ne peut pas etre en retard").isEmpty();

        // 3. L'immatriculation arrive 120 jours plus tard : le delai de 30 jours
        //    de l'etape 20 serait deja « depasse » s'il etait calcule a rebours.
        marquer(21, DemarcheEtat.COCHEE, Instant.now().minus(60, ChronoUnit.DAYS));
        List<DemarcheUseCases.PointAttention> points =
                useCases.vue(WS, TICKET).avancement().pointsAttention();

        assertThat(points)
                .as("l'etape 20 est cochee : elle ne doit apparaitre a aucun moment")
                .noneMatch(p -> p.ordre() == 20);
    }

    @Test
    @DisplayName("Une demarche deja cochee ne figure plus dans les points d'attention")
    void demarcheCocheeSortDesAlertes() {
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RC", "Modele J")),
                3, DelaiUnite.MOIS, 13);
        marquer(13, DemarcheEtat.COCHEE,
                LocalDate.now(EcheanceCalculator.ZONE).minusMonths(4)
                        .atStartOfDay(EcheanceCalculator.ZONE).toInstant());
        marquer(21, DemarcheEtat.COCHEE, Instant.now());

        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention()).isEmpty();
    }

    // =================================================================
    //  Vue d'avancement
    // =================================================================

    @Test
    @DisplayName("La prochaine demarche a traiter est la premiere non faite du statut courant")
    void prochaineDemarche() {
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        demarche(14, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        demarche(15, TicketStatut.DEROULEMENT_DEMARCHE, false, List.of());
        marquer(13, DemarcheEtat.COCHEE, Instant.now());

        DemarcheUseCases.Avancement a = useCases.vue(WS, TICKET).avancement();

        assertThat(a.prochainOrdre()).isEqualTo(14);
        assertThat(a.traitees()).isEqualTo(1);
        assertThat(a.applicables()).isEqualTo(3);
        assertThat(a.position()).isEqualTo(3);
        assertThat(a.totalStatuts()).isEqualTo(4);
    }

    @Test
    @DisplayName("Une demarche cochee alors qu'une precedente ne l'est pas est signalee, pas interdite")
    void horsSequenceSignale() {
        demarche(13, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        demarche(14, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());
        marquer(14, DemarcheEtat.COCHEE, Instant.now());

        List<DemarcheUseCases.Ligne> lignes = useCases.vue(WS, TICKET).phases().get(0).demarches();

        assertThat(lignes).extracting(DemarcheUseCases.Ligne::ordre).containsExactly(13, 14);
        assertThat(lignes.get(0).horsSequence()).isFalse();
        assertThat(lignes.get(1).horsSequence())
                .as("l'etape 14 est cochee alors que la 13 ne l'est pas").isTrue();
    }

    // =================================================================
    //  Lot 5 — une reponse (« gerance statutaire ? »), trois demarches
    // =================================================================

    /** Les trois demarches du referentiel qui portent la condition « acte separe ». */
    private void referentielActeNomination() {
        demarche(9, TicketStatut.GENERATION_DOCUMENTS, false,
                List.of(new JustificatifAttendu(1, "ACTE_NOMINATION", "Acte valide par le client")));
        demarche(15, TicketStatut.DEROULEMENT_DEMARCHE, false,
                List.of(new JustificatifAttendu(1, "ACTE_NOMINATION", "Acte signe et legalise")));
        demarche(18, TicketStatut.DEROULEMENT_DEMARCHE, false,
                List.of(new JustificatifAttendu(1, "ACTE_NOMINATION", "Acte enregistre")));
    }

    private DemarcheEtat etatDe(int ordre) {
        TicketDemarche td = etatsCourants.get(referentiel.get(ordre).id());
        return td == null ? DemarcheEtat.A_FAIRE : td.etat();
    }

    private String motifDe(int ordre) {
        TicketDemarche td = etatsCourants.get(referentiel.get(ordre).id());
        return td == null ? null : td.motif();
    }

    @Test
    @DisplayName("Gerance statutaire : les demarches 9, 15 et 18 deviennent non applicables d'un coup")
    void geranceStatutaire_ecarteLesTroisDemarches() {
        referentielActeNomination();

        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        assertThat(etatDe(9)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etatDe(15)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etatDe(18)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        // Le motif cite la condition du guide : un ecartement sans motif probant
        // ne vaut rien devant un controle.
        assertThat(motifDe(9)).contains("Gerance designee dans les statuts");
        assertThat(motifDe(15)).isEqualTo(motifDe(9));
        assertThat(motifDe(18)).isEqualTo(motifDe(9));
    }

    @Test
    @DisplayName("La gerance redevient non statutaire : les trois demarches redeviennent a faire")
    void geranceNonStatutaire_lesTroisRedeviennentApplicables() {
        referentielActeNomination();
        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        useCases.appliquerConditionGerance(WS, TICKET, false, ACTEUR);

        assertThat(etatDe(9)).isEqualTo(DemarcheEtat.A_FAIRE);
        assertThat(etatDe(15)).isEqualTo(DemarcheEtat.A_FAIRE);
        assertThat(etatDe(18)).isEqualTo(DemarcheEtat.A_FAIRE);
        assertThat(motifDe(9)).isNull();
    }

    @Test
    @DisplayName("Un ecartement decide par l'employe n'est jamais defait par la propagation")
    void ecartementManuelPreserve() {
        referentielActeNomination();
        statutTicket = TicketStatut.GENERATION_DOCUMENTS;
        useCases.marquerNonApplicable(WS, TICKET, 9,
                "Le client fournit son propre acte de nomination", ACTEUR);

        useCases.appliquerConditionGerance(WS, TICKET, false, ACTEUR);

        assertThat(etatDe(9)).as("l'ecartement de l'employe fait foi")
                .isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(motifDe(9)).isEqualTo("Le client fournit son propre acte de nomination");
    }

    @Test
    @DisplayName("Une demarche deja cochee n'est pas ecartee par la propagation")
    void demarcheCocheeNonEcartee() {
        referentielActeNomination();
        statutTicket = TicketStatut.GENERATION_DOCUMENTS;
        UUID acte = document("ACTE_NOMINATION", TICKET);
        useCases.cocher(WS, TICKET, 9, List.of(acte), ACTEUR);

        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        assertThat(etatDe(9)).as("un fait accompli ne se reecrit pas")
                .isEqualTo(DemarcheEtat.COCHEE);
        // Les deux autres, elles, sont bien ecartees.
        assertThat(etatDe(15)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etatDe(18)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
    }

    @Test
    @DisplayName("Une demarche OBLIGATOIRE ne peut pas etre ecartee par la propagation")
    void demarcheObligatoireJamaisEcartee() {
        // Cas de garde : si une version du guide rendait l'etape 9 obligatoire,
        // la propagation ne doit pas passer outre.
        demarche(9, TicketStatut.GENERATION_DOCUMENTS, true, List.of());

        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        assertThat(etatDe(9)).isEqualTo(DemarcheEtat.A_FAIRE);
    }
}
