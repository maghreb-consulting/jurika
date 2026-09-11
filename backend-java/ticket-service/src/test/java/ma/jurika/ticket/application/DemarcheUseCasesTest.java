package ma.jurika.ticket.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.DelaiUnite;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.DemarcheEvenement;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DemarcheJournalRepository;
import ma.jurika.ticket.domain.port.SaisieDossierLookup;
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
                delaiValeur, unite, refOrdre, null, null, null, attendus);
        referentiel.put(ordre, d);
        return d;
    }

    /**
     * Lot B — une demarche dont le delai part d'une DONNEE du dossier, et non du
     * cochage d'une autre ligne. C'est le cas de la taxe professionnelle et de
     * l'affiliation CNSS, qui courent depuis le debut d'activite.
     */
    private Demarche demarcheDelaiSurDonnee(int ordre, int valeur, DelaiUnite unite,
                                             String nomDonnee) {
        Demarche d = new Demarche(UUID.randomUUID(), "CREATION", ordre, "S3",
                "Déroulement de la démarche", "Etape " + ordre,
                TicketStatut.DEROULEMENT_DEMARCHE, "Cabinet", "DGI", true, "Tous dossiers",
                null, null, "Justificatif de l'etape " + ordre, null,
                "Dans les 30 jours du début d'activité", null, null,
                valeur, unite, null, nomDonnee, null, null, List.of());
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

    /** Le journal, par identifiant de ligne `ticket_demarches`. */
    private final Map<UUID, List<DemarcheEvenement>> evenements = new java.util.LinkedHashMap<>();

    /**
     * Lot B — les dates saisies au parcours, par nom de variable du corpus. Vide
     * par defaut : un dossier ou rien n'est saisi ne doit lever aucune alerte sur
     * les delais qui partent d'une donnee.
     */
    private final Map<String, java.time.LocalDate> datesSaisies = new java.util.LinkedHashMap<>();

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
                // UPSERT, pas INSERT : la ligne `ticket_demarches` d'une demarche est
                // UNIQUE (ticket_id, demarche_id) et son identifiant survit aux
                // gestes successifs. C'est lui qui porte le journal — en tirer un
                // nouveau a chaque appel ferait disparaitre l'historique.
                TicketDemarche existant = etatsCourants.get(demarcheId);
                UUID id = existant != null && existant.id() != null
                        ? existant.id() : UUID.randomUUID();
                etatsCourants.put(demarcheId, new TicketDemarche(id, null, etat, motif, acteurId,
                        etat == DemarcheEtat.A_FAIRE ? null : Instant.now(),
                        justificatifs.stream().map(JustificatifDepose::documentId).toList()));
                journal.add(etat + ":" + justificatifs.size());
                return id;
            }
        };

        DataroomDocumentLookup documents = new DataroomDocumentLookup() {
            @Override
            public List<DocumentVu> findByIds(UUID workspaceId, List<UUID> ids) {
                return ids.stream().map(dataroom::get)
                        .filter(java.util.Objects::nonNull).toList();
            }

            @Override
            public List<DocumentVu> findByTicket(UUID workspaceId, UUID ticketId) {
                return dataroom.values().stream()
                        .filter(d -> ticketId.equals(d.ticketId())).toList();
            }
        };

        CommentRepository comments = mock(CommentRepository.class);

        // Lot B — le journal de cochage. On en garde la trace en memoire : les tests
        // qui portent sur « les deux horodatages conserves » l'interrogent.
        DemarcheJournalRepository journalRepo = new DemarcheJournalRepository() {
            @Override
            public UUID enregistrer(UUID workspaceId, UUID ticketDemarcheId,
                                     DemarcheEvenement.Type type, String motif, UUID acteurId,
                                     int justificatifs) {
                UUID id = UUID.randomUUID();
                evenements.computeIfAbsent(ticketDemarcheId, k -> new java.util.ArrayList<>())
                        .add(new DemarcheEvenement(id, ticketDemarcheId, type, motif, acteurId,
                                Instant.now(), justificatifs));
                return id;
            }

            @Override
            public Map<UUID, List<DemarcheEvenement>> parDemarche(
                    UUID workspaceId, java.util.Collection<UUID> ticketDemarcheIds) {
                Map<UUID, List<DemarcheEvenement>> out = new java.util.LinkedHashMap<>();
                for (UUID id : ticketDemarcheIds) {
                    List<DemarcheEvenement> l = evenements.get(id);
                    if (l != null) out.put(id, List.copyOf(l));
                }
                return out;
            }
        };

        // Lot B — les saisies du parcours, telles que le domaine les lit pour faire
        // courir un delai qui ne part d'aucun cochage. Une donnee absente de la carte
        // renvoie vide : c'est l'etat normal d'un dossier ou la date n'est pas saisie,
        // et aucune echeance ne doit alors etre calculee.
        SaisieDossierLookup saisiesStub = (ws, tid, nom) ->
                Optional.ofNullable(datesSaisies.get(nom));

        useCases = new DemarcheUseCases(tickets, ref, etats, documents, comments, journalRepo,
                saisiesStub);
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
    @DisplayName("Une etape sans point de depart mecanisable ne leve jamais d'alerte")
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
    //  Lot B — LES DEUX DELAIS QUI PARTENT D'UNE DONNEE, ET NON D'UN COCHAGE
    //
    //  La taxe professionnelle (ligne 23) et l'affiliation CNSS (ligne 32)
    //  courent « dans les 30 jours du debut d'activite ». Le debut d'activite
    //  n'est pas une etape qu'on coche : c'est une date declaree. Ces deux
    //  delais legaux sont restes AVEUGLES jusqu'a ce que le champ existe.
    //
    //  La regle du lot 1 ne change pas : tant que la date n'est pas saisie,
    //  aucune date n'est fabriquee et aucune alerte n'est levee.
    // =================================================================

    /** La ligne 23 du parcours : « Dans les 30 jours du debut d'activite ». */
    private void taxeProfessionnelle() {
        demarcheDelaiSurDonnee(23, 30, DelaiUnite.JOURS, "DATE_DEBUT_ACTIVITE");
    }

    private void debutActivite(long joursAvantAujourdhui) {
        datesSaisies.put("DATE_DEBUT_ACTIVITE",
                LocalDate.now(EcheanceCalculator.ZONE).minusDays(joursAvantAujourdhui));
    }

    @Test
    @DisplayName("Date de debut d'activite NON SAISIE : aucune alerte, aucune date fabriquee")
    void delaiSurDonneeNonSaisieAucuneAlerte() {
        taxeProfessionnelle();
        // Rien dans `datesSaisies` : c'est l'etat d'un dossier ou la date n'a pas
        // encore ete renseignee, et c'est un etat NORMAL, pas une anomalie.

        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .as("une echeance devinee vaut moins que pas d'echeance : la premiere "
                        + "se croit, la seconde se voit")
                .isEmpty();
    }

    @Test
    @DisplayName("Date saisie et delai depasse : l'alerte se leve enfin, et dit de combien")
    void delaiSurDonneeDepasse() {
        taxeProfessionnelle();
        debutActivite(40); // 40 jours d'activite pour un delai de 30

        List<DemarcheUseCases.PointAttention> points =
                useCases.vue(WS, TICKET).avancement().pointsAttention();

        assertThat(points).hasSize(1);
        assertThat(points.get(0).ordre()).isEqualTo(23);
        assertThat(points.get(0).severite()).isEqualTo("DEPASSE");
        assertThat(points.get(0).joursRestants()).isEqualTo(-10);
        assertThat(points.get(0).echeance())
                .as("l'echeance se compte depuis la DATE SAISIE, pas depuis un cochage")
                .isEqualTo(LocalDate.now(EcheanceCalculator.ZONE).minusDays(10));
        assertThat(points.get(0).delai())
                .as("le texte du parcours est rendu tel quel, sans reformulation")
                .isEqualTo("Dans les 30 jours du début d'activité");
    }

    @Test
    @DisplayName("Les seuils J-15 et J-3 valent pour un delai sur donnee comme pour les autres")
    void delaiSurDonneeSeuils() {
        taxeProfessionnelle();

        debutActivite(20); // echeance a J+10
        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .singleElement()
                .extracting(DemarcheUseCases.PointAttention::severite).isEqualTo("APPROCHE");

        debutActivite(28); // echeance a J+2
        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .singleElement()
                .extracting(DemarcheUseCases.PointAttention::severite).isEqualTo("CRITIQUE");

        debutActivite(1); // echeance a J+29 : encore loin
        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .as("un delai qui court mais qui n'approche pas n'est pas un point d'attention")
                .isEmpty();
    }

    @Test
    @DisplayName("Une activite qui n'a pas encore commence ne fait courir aucun delai depasse")
    void debutActiviteDansLeFutur() {
        taxeProfessionnelle();
        datesSaisies.put("DATE_DEBUT_ACTIVITE",
                LocalDate.now(EcheanceCalculator.ZONE).plusDays(60));

        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .as("le client declare demarrer dans deux mois : le delai de 30 jours "
                        + "part de la, et l'echeance est encore loin")
                .isEmpty();
    }

    @Test
    @DisplayName("Une SEULE date fait courir les DEUX delais — taxe professionnelle et CNSS")
    void uneDateDeuxDelais() {
        taxeProfessionnelle();
        demarcheDelaiSurDonnee(32, 30, DelaiUnite.JOURS, "DATE_DEBUT_ACTIVITE");
        debutActivite(35);

        List<DemarcheUseCases.PointAttention> points =
                useCases.vue(WS, TICKET).avancement().pointsAttention();

        assertThat(points)
                .as("deux obligations legales distinctes, un seul point de depart")
                .extracting(DemarcheUseCases.PointAttention::ordre)
                .containsExactly(23, 32);
        assertThat(points).allSatisfy(p -> {
            assertThat(p.severite()).isEqualTo("DEPASSE");
            assertThat(p.joursRestants()).isEqualTo(-5);
        });
    }

    @Test
    @DisplayName("Une demarche ECARTEE n'alerte pas, meme si la date est saisie")
    void delaiSurDonneeDemarcheEcartee() {
        taxeProfessionnelle();
        debutActivite(90);
        marquer(23, DemarcheEtat.NON_APPLICABLE, Instant.now());

        assertThat(useCases.vue(WS, TICKET).avancement().pointsAttention())
                .as("ecartee, pas oubliee : elle ne cree plus d'obligation")
                .isEmpty();
    }

    @Test
    @DisplayName("Une demarche COCHEE n'alerte plus, meme si la date est saisie")
    void delaiSurDonneeDemarcheCochee() {
        taxeProfessionnelle();
        debutActivite(90);
        marquer(23, DemarcheEtat.COCHEE, Instant.now());

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
    /**
     * Lot B — les lignes du parcours du 9 septembre qui portent « si la gerance
     * n'est pas designee dans les statuts » : la ligne 5 (acte de nomination) et
     * les lignes 21 et 22 (enregistrement de cet acte, depot puis retrait).
     *
     * <p>L'ancien guide les numerotait 9, 15 et 18. La ligne 14 (legalisation des
     * signatures) ne porte plus la condition : le parcours l'a elargie aux statuts
     * et au pouvoir, elle vaut « tous dossiers ».
     */
    private void referentielActeNomination() {
        demarche(5, TicketStatut.GENERATION_DOCUMENTS, false,
                List.of(new JustificatifAttendu(1, "ACTE_NOMINATION", "Acte valide par le client")));
        demarche(21, TicketStatut.DEROULEMENT_DEMARCHE, false,
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Recepisse de depot")));
        demarche(22, TicketStatut.DEROULEMENT_DEMARCHE, false,
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
    @DisplayName("Gerance statutaire : les demarches 5, 21 et 22 deviennent non applicables d'un coup")
    void geranceStatutaire_ecarteLesTroisDemarches() {
        referentielActeNomination();

        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        assertThat(etatDe(5)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etatDe(21)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etatDe(22)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        // Le motif cite la condition du parcours : un ecartement sans motif probant
        // ne vaut rien devant un controle.
        assertThat(motifDe(5)).contains("Gerance designee dans les statuts");
        assertThat(motifDe(21)).isEqualTo(motifDe(5));
        assertThat(motifDe(22)).isEqualTo(motifDe(5));
    }

    @Test
    @DisplayName("La gerance redevient non statutaire : les trois demarches redeviennent a faire")
    void geranceNonStatutaire_lesTroisRedeviennentApplicables() {
        referentielActeNomination();
        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        useCases.appliquerConditionGerance(WS, TICKET, false, ACTEUR);

        assertThat(etatDe(5)).isEqualTo(DemarcheEtat.A_FAIRE);
        assertThat(etatDe(21)).isEqualTo(DemarcheEtat.A_FAIRE);
        assertThat(etatDe(22)).isEqualTo(DemarcheEtat.A_FAIRE);
        // Lot B — la reprise porte desormais son propre motif : le journal exige un
        // motif sur une reprise comme sur une annulation, et l'ecran doit pouvoir
        // dire pourquoi la demarche est redevenue « a faire ».
        assertThat(motifDe(5)).contains("redevient applicable");
    }

    @Test
    @DisplayName("Un ecartement decide par l'employe n'est jamais defait par la propagation")
    void ecartementManuelPreserve() {
        referentielActeNomination();
        statutTicket = TicketStatut.GENERATION_DOCUMENTS;
        useCases.marquerNonApplicable(WS, TICKET, 5,
                "Le client fournit son propre acte de nomination", ACTEUR);

        useCases.appliquerConditionGerance(WS, TICKET, false, ACTEUR);

        assertThat(etatDe(5)).as("l'ecartement de l'employe fait foi")
                .isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(motifDe(5)).isEqualTo("Le client fournit son propre acte de nomination");
    }

    @Test
    @DisplayName("Une demarche deja cochee n'est pas ecartee par la propagation")
    void demarcheCocheeNonEcartee() {
        referentielActeNomination();
        statutTicket = TicketStatut.GENERATION_DOCUMENTS;
        UUID acte = document("ACTE_NOMINATION", TICKET);
        useCases.cocher(WS, TICKET, 5, List.of(acte), ACTEUR);

        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        assertThat(etatDe(5)).as("un fait accompli ne se reecrit pas")
                .isEqualTo(DemarcheEtat.COCHEE);
        // Les deux autres, elles, sont bien ecartees.
        assertThat(etatDe(21)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
        assertThat(etatDe(22)).isEqualTo(DemarcheEtat.NON_APPLICABLE);
    }

    @Test
    @DisplayName("Une demarche OBLIGATOIRE ne peut pas etre ecartee par la propagation")
    void demarcheObligatoireJamaisEcartee() {
        // Cas de garde : si une version du parcours rendait la ligne 5 obligatoire,
        // la propagation ne doit pas passer outre.
        demarche(5, TicketStatut.GENERATION_DOCUMENTS, true, List.of());

        useCases.appliquerConditionGerance(WS, TICKET, true, ACTEUR);

        assertThat(etatDe(5)).isEqualTo(DemarcheEtat.A_FAIRE);
    }
    // =================================================================
    //  Lot B — LE COCHAGE EST UN JOURNAL, PAS UNE CASE
    // =================================================================

    /** La derniere vue produite, pour lire ce que l'ecran afficherait. */
    private DemarcheUseCases.Ligne ligneDe(DemarcheUseCases.Vue vue, int ordre) {
        return vue.phases().stream()
                .flatMap(p -> p.demarches().stream())
                .filter(l -> l.ordre() == ordre)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ligne " + ordre + " absente de la vue"));
    }

    @Test
    @DisplayName("Cocher enregistre la date, l'heure ET l'auteur — enonce 8")
    void cochageHorodateEtSigne() {
        demarche(15, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Recepisse de depot")));
        UUID recepisse = document("RECEPISSE_DEPOT", TICKET);
        Instant avant = Instant.now().minusSeconds(1);

        DemarcheUseCases.Vue vue = useCases.cocher(WS, TICKET, 15, List.of(recepisse), ACTEUR);

        DemarcheUseCases.Ligne ligne = ligneDe(vue, 15);
        assertThat(ligne.etat()).isEqualTo(DemarcheEtat.COCHEE);
        assertThat(ligne.cocheAt()).as("la date de cochage").isAfter(avant);
        assertThat(ligne.journal()).hasSize(1);

        DemarcheEvenement e = ligne.journal().get(0);
        assertThat(e.type()).isEqualTo(DemarcheEvenement.Type.COCHAGE);
        assertThat(e.acteurId()).as("QUI a coche").isEqualTo(ACTEUR);
        assertThat(e.survenuLe()).isAfter(avant);
        assertThat(e.justificatifs()).isEqualTo(1);
    }

    @Test
    @DisplayName("Annuler un cochage SANS motif est refuse — enonce 9")
    void annulationSansMotifRefusee() {
        demarche(15, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Recepisse de depot")));
        useCases.cocher(WS, TICKET, 15, List.of(document("RECEPISSE_DEPOT", TICKET)), ACTEUR);

        assertThatThrownBy(() -> useCases.decocher(WS, TICKET, 15, "   ", ACTEUR))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Motif obligatoire");
    }

    @Test
    @DisplayName("Annuler conserve LES DEUX horodatages, et recocher fait trois evenements — enonce 9")
    void annulationConserveLesDeuxHorodatages() {
        demarche(15, TicketStatut.DEROULEMENT_DEMARCHE, true,
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Recepisse de depot")));
        UUID recepisse = document("RECEPISSE_DEPOT", TICKET);

        useCases.cocher(WS, TICKET, 15, List.of(recepisse), ACTEUR);
        Instant premierCochage = ligneDe(useCases.vue(WS, TICKET), 15).journal().get(0).survenuLe();

        useCases.decocher(WS, TICKET, 15, "Recepisse illisible, redepot demande", ACTEUR);
        DemarcheUseCases.Vue apresAnnulation = useCases.vue(WS, TICKET);

        // L'etat courant est revenu a « a faire »...
        assertThat(ligneDe(apresAnnulation, 15).etat()).isEqualTo(DemarcheEtat.A_FAIRE);
        // ...mais le journal garde LES DEUX dates.
        List<DemarcheEvenement> journalApres = ligneDe(apresAnnulation, 15).journal();
        assertThat(journalApres).hasSize(2);
        assertThat(journalApres.get(0).type()).isEqualTo(DemarcheEvenement.Type.COCHAGE);
        assertThat(journalApres.get(0).survenuLe())
                .as("l'horodatage du cochage ne se perd pas")
                .isEqualTo(premierCochage);
        assertThat(journalApres.get(1).type()).isEqualTo(DemarcheEvenement.Type.ANNULATION);
        assertThat(journalApres.get(1).motif()).isEqualTo("Recepisse illisible, redepot demande");
        assertThat(journalApres.get(1).survenuLe())
                .as("l'annulation porte son propre horodatage")
                .isAfterOrEqualTo(premierCochage);

        // Recocher : trois evenements, pas un etat qui aurait tout efface.
        useCases.cocher(WS, TICKET, 15, List.of(recepisse), ACTEUR);
        assertThat(ligneDe(useCases.vue(WS, TICKET), 15).journal())
                .as("cochee, annulee, recochee : trois evenements")
                .hasSize(3)
                .extracting(DemarcheEvenement::type)
                .containsExactly(DemarcheEvenement.Type.COCHAGE,
                        DemarcheEvenement.Type.ANNULATION,
                        DemarcheEvenement.Type.COCHAGE);
    }

    @Test
    @DisplayName("Ecarter une demarche puis la reprendre distingue les deux gestes au journal")
    void repriseEtAnnulationNeSeConfondentPas() {
        statutTicket = TicketStatut.GENERATION_DOCUMENTS;
        demarche(5, TicketStatut.GENERATION_DOCUMENTS, false, List.of());

        useCases.marquerNonApplicable(WS, TICKET, 5, "Gerance designee dans les statuts", ACTEUR);
        useCases.decocher(WS, TICKET, 5, "Le client change d'avis : acte separe", ACTEUR);

        assertThat(ligneDe(useCases.vue(WS, TICKET), 5).journal())
                .extracting(DemarcheEvenement::type)
                .as("ecarter puis reprendre n'est pas cocher puis annuler")
                .containsExactly(DemarcheEvenement.Type.HORS_PERIMETRE,
                        DemarcheEvenement.Type.REPRISE);
    }

    @Test
    @DisplayName("Annuler ce qui n'a jamais ete coche est refuse : il n'y a rien a annuler")
    void annulationSansCochagePrealableRefusee() {
        demarche(15, TicketStatut.DEROULEMENT_DEMARCHE, true, List.of());

        assertThatThrownBy(() -> useCases.decocher(WS, TICKET, 15, "un motif", ACTEUR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("rien a annuler");
    }

    // =================================================================
    //  Lot B — DEPOT ET RETRAIT : LE DELAI DU SECOND COURT DEPUIS LE PREMIER
    // =================================================================

    /** Une formalite scindee : ligne de depot, puis ligne de retrait. */
    private void formaliteScindee(int depot, int retrait, String code,
                                   Integer delaiValeur, DelaiUnite unite) {
        referentiel.put(depot, new Demarche(UUID.randomUUID(), "CREATION", depot, "S3",
                "Déroulement de la démarche", "Formalite " + code + " — depot",
                TicketStatut.DEROULEMENT_DEMARCHE, null, null, true, "Tous dossiers",
                null, null, "Recepisse de depot", null, "Dans les 30 jours", null, null,
                null, null, null, null, code, ma.jurika.ticket.domain.model.FormaliteVolet.DEPOT,
                List.of(new JustificatifAttendu(1, "RECEPISSE_DEPOT", "Recepisse de depot"))));
        referentiel.put(retrait, new Demarche(UUID.randomUUID(), "CREATION", retrait, "S3",
                "Déroulement de la démarche", "Formalite " + code + " — retrait",
                TicketStatut.DEROULEMENT_DEMARCHE, null, null, true, "Tous dossiers",
                null, null, "Piece retiree", null, "Selon le delai du service", null, null,
                delaiValeur, unite, delaiValeur == null ? null : depot, null,
                code, ma.jurika.ticket.domain.model.FormaliteVolet.RETRAIT,
                List.of(new JustificatifAttendu(1, "RC", "Modele J"))));
    }

    @Test
    @DisplayName("La ligne de retrait dit depuis quand elle attend : la date du DEPOT — enonce 12")
    void leRetraitCourtDepuisLeDepot() {
        formaliteScindee(27, 28, "IMMATRICULATION_RC", 3, DelaiUnite.JOURS);

        // Tant que le depot n'est pas coche, le retrait n'attend rien : on ne
        // fabrique pas une date de depart.
        DemarcheUseCases.Ligne avant = ligneDe(useCases.vue(WS, TICKET), 28);
        assertThat(avant.formaliteVolet())
                .isEqualTo(ma.jurika.ticket.domain.model.FormaliteVolet.RETRAIT);
        assertThat(avant.depotOrdre()).isEqualTo(27);
        assertThat(avant.deposeLe()).as("aucun depot, aucune attente").isNull();

        useCases.cocher(WS, TICKET, 27, List.of(document("RECEPISSE_DEPOT", TICKET)), ACTEUR);

        DemarcheUseCases.Ligne apres = ligneDe(useCases.vue(WS, TICKET), 28);
        assertThat(apres.deposeLe())
                .as("la ligne de retrait porte la date de cochage du depot")
                .isNotNull()
                .isEqualTo(ligneDe(useCases.vue(WS, TICKET), 27).cocheAt());
    }

    @Test
    @DisplayName("L'echeance du retrait se calcule depuis le depot, jamais depuis l'ouverture")
    void echeanceDuRetraitCalculeeDepuisLeDepot() {
        formaliteScindee(27, 28, "IMMATRICULATION_RC", 3, DelaiUnite.JOURS);
        // Le depot a ete coche il y a quatre jours : le retrait est en retard.
        Demarche depot = referentiel.get(27);
        etatsCourants.put(depot.id(), new TicketDemarche(UUID.randomUUID(), null,
                DemarcheEtat.COCHEE, null, ACTEUR,
                Instant.now().minus(4, ChronoUnit.DAYS), List.of()));

        DemarcheUseCases.Vue vue = useCases.vue(WS, TICKET);

        assertThat(vue.avancement().pointsAttention())
                .as("le point d'attention nomme la ligne de retrait")
                .extracting(DemarcheUseCases.PointAttention::ordre)
                .contains(28);
        DemarcheUseCases.PointAttention point = vue.avancement().pointsAttention().stream()
                .filter(p -> p.ordre() == 28).findFirst().orElseThrow();
        assertThat(point.echeance())
                .as("3 jours apres le cochage du depot")
                .isEqualTo(EcheanceCalculator.echeance(
                        etatsCourants.get(depot.id()).cocheAt(), 3, DelaiUnite.JOURS));
        assertThat(point.severite()).isEqualTo("DEPASSE");
    }

    @Test
    @DisplayName("Un retrait sans duree chiffree ne fabrique AUCUNE echeance")
    void retraitSansDureeNeFabriquePasDEcheance() {
        formaliteScindee(19, 20, "ENREGISTREMENT_STATUTS", null, null);
        Demarche depot = referentiel.get(19);
        etatsCourants.put(depot.id(), new TicketDemarche(UUID.randomUUID(), null,
                DemarcheEtat.COCHEE, null, ACTEUR,
                Instant.now().minus(90, ChronoUnit.DAYS), List.of()));

        DemarcheUseCases.Vue vue = useCases.vue(WS, TICKET);

        assertThat(vue.avancement().pointsAttention())
                .as("« selon le delai du service » ne se calcule pas : aucune alerte")
                .extracting(DemarcheUseCases.PointAttention::ordre)
                .doesNotContain(20);
        // Mais la date du depot, elle, est bien rendue : « depuis quand ? » a une reponse.
        assertThat(ligneDe(vue, 20).deposeLe()).isNotNull();
    }
}

