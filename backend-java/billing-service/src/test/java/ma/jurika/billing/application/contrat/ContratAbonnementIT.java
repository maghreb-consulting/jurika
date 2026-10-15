package ma.jurika.billing.application.contrat;

import com.zaxxer.hikari.HikariDataSource;
import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (E6) : contrat d'abonnement sur une vraie base (migrations billing V1 a V16) :
 * parcours complet (ouverture en attente, acceptation, exemplaire, changement, resiliation,
 * renouvellement, nouvelle version), preuve inalterable (declencheurs et droits de jurika_app),
 * RLS par cabinet, trace des parametres de l'editeur.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratAbonnementIT {

    private static final String MODELE = ContratPlanification.modeleInitial();

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_billing_it")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("contrat-it-init.sql");

    private HikariDataSource proprietaire;
    private JdbcTemplate jdbc;
    private ContratService service;
    private ContratHorloge horloge;
    private final List<Object> evenements = new ArrayList<>();
    private final List<String[]> courriels = new ArrayList<>();
    private EditeurParametresEntity editeur;
    /** Reponse d auth sur l essai du cabinet (L7 E13) : aucun essai par defaut. */
    private final java.util.concurrent.atomic.AtomicReference<java.util.function.Supplier<java.util.Optional<EssaiCabinetSource.AcceptationEssai>>> essai =
            new java.util.concurrent.atomic.AtomicReference<>(java.util.Optional::empty);

    @BeforeAll
    void demarrer() {
        pg.start();
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_history_billing")
                .load()
                .migrate();
        proprietaire = new HikariDataSource();
        proprietaire.setJdbcUrl(pg.getJdbcUrl());
        proprietaire.setUsername(pg.getUsername());
        proprietaire.setPassword(pg.getPassword());
        jdbc = new JdbcTemplate(proprietaire);

        horloge = new ContratHorloge();
        // La version 1 entre en vigueur le jour de son chargement : meme horloge figee que les tests
        // (sinon, des le lendemain du 2026-10-14, elle serait "a venir" a la date des tests).
        horloge.regler(Clock.fixed(Instant.parse("2026-10-14T09:30:00Z"), ZoneOffset.UTC));
        editeur = new EditeurParametresEntity();
        EditeurParametresService editeurService = mock(EditeurParametresService.class);
        when(editeurService.identite()).thenAnswer(i -> editeur);
        ContratCourriel courriel = new ContratCourriel(null, null, new BillingProperties()) {
            @Override
            public Resultat envoyer(String destinataire, String objet, String gabarit, Map<String, Object> variables,
                                    PieceJointe piece) {
                courriels.add(new String[]{destinataire, gabarit, piece == null ? "" : piece.nom()});
                return new Resultat(null);
            }
        };
        IdentiteCabinetSource identites = jeton -> new IdentiteCabinetSource.Identite("Cabinet Atlas Conseil",
                "12 boulevard d\u2019Anfa, Casablanca", "123456", "001234567000089", "contact@atlas.ma",
                "Bennani", "Salma", "salma.bennani@jurika.ma");
        service = new ContratService(new ContratDepot(jdbc), editeurService, identites, jeton -> essai.get().get(),
                courriel, horloge,
                evenements::add, proprietes(), new DataSourceTransactionManager(proprietaire));
        service.initialiserVersionInitiale(ContratPlanification.VERSION_INITIALE, ContratPlanification.sourceInitiale());
    }

    @BeforeEach
    void raz() {
        horloge.regler(Clock.fixed(Instant.parse("2026-10-14T09:30:00Z"), ZoneOffset.UTC));
        evenements.clear();
        courriels.clear();
        essai.set(java.util.Optional::empty);
    }

    @AfterAll
    void arreter() {
        if (proprietaire != null) proprietaire.close();
        pg.stop();
    }

    private static ContratService.Requerant superviseur(UUID userId) {
        return new ContratService.Requerant(userId, "SUPERVISEUR", "Bearer x", "196.200.1.2", "196.200.1.2 ; 10.0.0.5");
    }

    private static ContratService.CommandeAcceptation commande(String empreinte, boolean caseCochee, boolean confirmation) {
        return new ContratService.CommandeAcceptation("Mme", "g\u00e9rante", "SARL", "Casablanca", empreinte,
                caseCochee, confirmation);
    }

    private UUID ouvrir(UUID ws, String forfait, String rythme, String prix) {
        return service.ouvrir(new ContratAbonnementPort.ConditionsParticulieres(ws, forfait, rythme, new BigDecimal(prix),
                LocalDate.of(2026, 11, 1), null, UUID.randomUUID()), "VIREMENT", "SUPER_ADMIN");
    }

    private ContratService.AcceptationVue accepter(UUID ws, UUID user) {
        ContratService.Apercu a = service.apercu(ws, superviseur(user), commande(null, false, false));
        return service.accepter(ws, superviseur(user), commande(a.empreinte(), true, true));
    }

    @Test
    void parcours_complet_du_contrat() {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        assertThat(service.etat(ws).statut()).isEqualTo("AUCUN");

        // RG-CTR-03 : conditions particulieres ouvertes EN ATTENTE.
        UUID cp = ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
        assertThat(service.acceptationEnAttente(ws)).isTrue();
        ContratService.Etat etat = service.etat(ws);
        assertThat(etat.statut()).isEqualTo("EN_ATTENTE");
        assertThat(etat.objetAAccepter()).isEqualTo(ContratService.OBJET_CP);
        assertThat(etat.enAttente().reference()).matches("CP-2026-\\d{6}");
        assertThat(etat.enAttente().dateFin()).isEqualTo(LocalDate.of(2027, 11, 1));

        // Apercu : texte, empreinte, mentions de l'editeur nommees (parametres vides).
        ContratService.Apercu apercu = service.apercu(ws, superviseur(user), commande(null, false, false));
        assertThat(apercu.empreinte()).isEqualTo(ContratTexte.sha256(apercu.texte()));
        assertThat(apercu.manquants()).contains("ICE de l\u2019\u00c9diteur", "forme juridique de l\u2019\u00c9diteur");
        assertThat(apercu.texte()).contains("| Forfait | Professionnel |", "| Mode de paiement choisi | virement bancaire |");

        // RG-CTR-01 : case non cochee, confirmation absente, texte modifie -> refus, rien n'est enregistre.
        assertThatThrownBy(() -> service.accepter(ws, superviseur(user), commande(apercu.empreinte(), false, true)))
                .isInstanceOf(ContratException.class).hasMessageContaining("Cochez la case");
        assertThatThrownBy(() -> service.accepter(ws, superviseur(user), commande(apercu.empreinte(), true, false)))
                .isInstanceOf(ContratException.class).hasMessageContaining("Confirmez");
        assertThatThrownBy(() -> service.accepter(ws, superviseur(user), commande("0".repeat(64), true, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.accepter(ws, new ContratService.Requerant(user, "EMPLOYE", "Bearer x",
                "1.2.3.4", null), commande(apercu.empreinte(), true, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_acceptations WHERE workspace_id = ?", Integer.class, ws))
                .isZero();

        // Acceptation : preuve complete (RG-CTR-02).
        ContratService.AcceptationVue acc = service.accepter(ws, superviseur(user), commande(apercu.empreinte(), true, true));
        Map<String, Object> preuve = jdbc.queryForMap("SELECT * FROM contrat_acceptations WHERE id = ?", acc.id());
        assertThat(preuve.get("empreinte_contrat").toString().trim()).isEqualTo(apercu.empreinte());
        assertThat(preuve.get("texte_accepte")).isEqualTo(apercu.texte());
        assertThat(preuve.get("version_code")).isEqualTo("1");
        assertThat(preuve.get("signataire_compte")).isEqualTo("salma.bennani@jurika.ma");
        assertThat(preuve.get("signataire_qualite")).isEqualTo("g\u00e9rante");
        assertThat(preuve.get("signataire_role")).isEqualTo("SUPERVISEUR");
        assertThat(preuve.get("accepte_le_casablanca")).isEqualTo("2026-10-14 10:30:00");
        assertThat(preuve.get("adresse_ip")).isEqualTo("196.200.1.2");
        assertThat(preuve.get("cabinet_email")).isEqualTo("contact@atlas.ma");
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        assertThat(service.etat(ws).statut()).isEqualTo("ACCEPTE");
        assertThat(evenements).hasSize(1).first().isInstanceOf(ContratEvenements.ContratAccepte.class);

        // RG-CTR-04 : exemplaire PDF et copie par courriel (journalisee).
        ContratService.Exemplaire ex = service.exemplaire(ws, acc.id(), false);
        assertThat(new String(ex.pdf(), 0, 5)).isEqualTo("%PDF-");
        assertThat(service.exemplaire(ws, acc.id(), false).pdf()).isEqualTo(ex.pdf());
        assertThat(courriels).hasSize(1);
        assertThat(courriels.get(0)[0]).isEqualTo("contact@atlas.ma");
        assertThat(courriels.get(0)[2]).endsWith(".pdf");
        assertThat(jdbc.queryForObject("SELECT statut FROM contrat_avis WHERE workspace_id = ? AND type = 'COPIE_CONTRAT'",
                String.class, ws)).isEqualTo("ENVOYE");
        // Un autre cabinet ne lit pas cet exemplaire (filtrage applicatif).
        assertThatThrownBy(() -> service.exemplaire(UUID.randomUUID(), acc.id(), false))
                .isInstanceOf(ContratException.class);

        // RG-CTR-05 : changement de forfait -> nouvelles conditions en attente ; l'ancien contrat reste
        // accepte jusqu'a l'acceptation, puis il est remplace.
        UUID cp2 = ouvrir(ws, "MAX", "MENSUEL", "1500");
        assertThat(service.acceptationEnAttente(ws)).isTrue();
        assertThat(jdbc.queryForObject("SELECT statut FROM conditions_particulieres WHERE id = ?", String.class, cp))
                .isEqualTo("ACCEPTE");
        accepter(ws, user);
        assertThat(jdbc.queryForObject("SELECT statut FROM conditions_particulieres WHERE id = ?", String.class, cp))
                .isEqualTo("REMPLACE");
        assertThat(jdbc.queryForObject("SELECT statut FROM conditions_particulieres WHERE id = ?", String.class, cp2))
                .isEqualTo("ACCEPTE");

        // RG-CTR-09 : historique complet.
        ContratService.Historique h = service.historique(ws, false);
        assertThat(h.conditions()).hasSize(2);
        assertThat(h.acceptations()).hasSize(2);

        // RG-CTR-08 : resiliation par le superviseur, effet a l'echeance, arret du forfait signale.
        assertThatThrownBy(() -> service.resilierParCabinet(ws, user, " "))
                .isInstanceOf(ContratException.class).hasMessageContaining("motif");
        ContratService.ResiliationVue res = service.resilierParCabinet(ws, user, "Cessation d\u2019activit\u00e9");
        assertThat(res.dateEffet()).isEqualTo(LocalDate.of(2027, 11, 1));
        // Arret du forfait : signale a l'abonnement par l'evenement (ResiliationAbonnementListener).
        assertThat(evenements).last().isInstanceOfSatisfying(ContratEvenements.ResiliationEnregistree.class,
                ev -> assertThat(ev.dateEffet()).isEqualTo(LocalDate.of(2027, 11, 1)));
        assertThat(evenements).last().isInstanceOf(ContratEvenements.ResiliationEnregistree.class);
        assertThat(service.etat(ws).statut()).isEqualTo("RESILIE");
        // Rien n'est supprime.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_acceptations WHERE workspace_id = ?", Integer.class, ws))
                .isEqualTo(2);
    }

    @Test
    void renouvellement_annuel_et_avis_trente_jours_avant() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "ANNUEL", "3750");
        accepter(ws, UUID.randomUUID());
        courriels.clear();

        // 2027-10-05 : echeance du 2027-11-01 a 27 jours -> avis au cabinet et au super-admin, une seule fois.
        horloge.regler(Clock.fixed(Instant.parse("2027-10-05T06:00:00Z"), ZoneOffset.UTC));
        service.executerEcheances();
        assertThat(courriels).extracting(c -> c[0]).contains("contact@atlas.ma", "ventes@editeur.test");
        assertThat(courriels).allMatch(c -> c[1].equals("email/contrat-echeance"));
        int avis = courriels.size();
        service.executerEcheances();
        assertThat(courriels).hasSize(avis);

        // Echeance passee : renouvellement pour un an, trace.
        horloge.regler(Clock.fixed(Instant.parse("2027-11-02T06:00:00Z"), ZoneOffset.UTC));
        ContratService.BilanEcheances b = service.executerEcheances();
        assertThat(b.renouvellements()).isGreaterThanOrEqualTo(1);
        assertThat(service.etat(ws).courant().echeanceCourante()).isEqualTo(LocalDate.of(2028, 11, 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_renouvellements WHERE workspace_id = ?",
                Integer.class, ws)).isEqualTo(1);
    }

    @Test
    void nouvelle_version_publiee_trente_jours_avant_puis_acceptee() {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "MENSUEL", "375");
        accepter(ws, user);
        courriels.clear();
        String texte = MODELE.replace("## Article 17 \u2014 Notifications", "## Article 17 \u2014 Notifications et avis");
        assertThatThrownBy(() -> service.publierVersion("2026-12-01", texte, LocalDate.of(2026, 11, 1), UUID.randomUUID()))
                .isInstanceOf(ContratException.class).hasMessageContaining("au moins 30 jours");
        String code = "v-" + UUID.randomUUID().toString().substring(0, 8);
        service.publierVersion(code, texte, LocalDate.of(2026, 11, 20), UUID.randomUUID());
        assertThat(courriels).extracting(c -> c[1]).contains("email/contrat-nouvelle-version");
        // Avant l'entree en vigueur : rien a accepter, la version a venir est annoncee.
        ContratService.Etat avant = service.etat(ws);
        assertThat(avant.acceptationRequise()).isFalse();
        assertThat(avant.versionAVenir().code()).isEqualTo(code);
        // Apres : acceptation demandee a la connexion suivante (sans bloquer le cabinet).
        horloge.regler(Clock.fixed(Instant.parse("2026-11-21T08:00:00Z"), ZoneOffset.UTC));
        ContratService.Etat apres = service.etat(ws);
        assertThat(apres.objetAAccepter()).isEqualTo(ContratService.OBJET_VERSION);
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        ContratService.AcceptationVue acc = accepter(ws, user);
        assertThat(acc.versionCode()).isEqualTo(code);
        assertThat(service.etat(ws).acceptationRequise()).isFalse();
        // Article 16 : la reponse "Accepter" est enregistree, liee a la preuve de l'acceptation.
        Map<String, Object> rep = jdbc.queryForMap("SELECT * FROM contrat_reponses_versions WHERE workspace_id = ?", ws);
        assertThat(rep.get("reponse")).isEqualTo("ACCEPTEE");
        assertThat(rep.get("version_code")).isEqualTo(code);
        assertThat(rep.get("acceptation_id")).isEqualTo(acc.id());
        assertThat(rep.get("signataire_compte")).isEqualTo("salma.bennani@jurika.ma");
        assertThat(rep.get("repondu_le_casablanca")).isEqualTo("2026-11-21 09:00:00");
        assertThat(rep.get("adresse_ip")).isEqualTo("196.200.1.2");
        assertThat(service.historique(ws, false).reponses()).extracting(ContratService.ReponseVue::reponse)
                .containsExactly("ACCEPTEE");
        // Pas de double reponse : un refus apres l'acceptation est refuse, rien n'est resilie.
        assertThatThrownBy(() -> service.refuserNouvelleVersion(ws, superviseur(user),
                new ContratService.CommandeRefus(code, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_resiliations WHERE workspace_id = ?", Integer.class, ws))
                .isZero();
        // Toutes les versions sont conservees.
        assertThat(service.versions(true)).extracting(ContratService.VersionVue::code).contains("1", code).doesNotContain("2026-10-12");
    }

    /** Publie une nouvelle version (en vigueur le 20 novembre 2026) et place l'horloge au lendemain. */
    private String nouvelleVersionEnVigueur() {
        String texte = MODELE.replace("## Article 17 \u2014 Notifications", "## Article 17 \u2014 Notifications et avis");
        String code = "v-" + UUID.randomUUID().toString().substring(0, 8);
        service.publierVersion(code, texte, LocalDate.of(2026, 11, 20), UUID.randomUUID());
        horloge.regler(Clock.fixed(Instant.parse("2026-11-21T08:00:00Z"), ZoneOffset.UTC));
        return code;
    }

    @Test
    void refus_de_la_nouvelle_version_vaut_resiliation_a_l_echeance_et_reponse_unique() {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "ANNUEL", "3750");
        accepter(ws, user);
        String code = nouvelleVersionEnVigueur();
        evenements.clear();
        assertThat(service.etat(ws).objetAAccepter()).isEqualTo(ContratService.OBJET_VERSION);
        // Pas de blocage (article 16) : la passerelle laisse passer le cabinet.
        assertThat(service.acceptationEnAttente(ws)).isFalse();

        // Confirmation exigee, version lue verifiee, superviseur seul.
        assertThatThrownBy(() -> service.refuserNouvelleVersion(ws, superviseur(user), new ContratService.CommandeRefus(code, false)))
                .isInstanceOf(ContratException.class).hasMessageContaining("Confirmez");
        assertThatThrownBy(() -> service.refuserNouvelleVersion(ws, superviseur(user), new ContratService.CommandeRefus("1", true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).code()).isEqualTo("VERSION_CHANGEE"));
        assertThatThrownBy(() -> service.refuserNouvelleVersion(ws, new ContratService.Requerant(user, "EMPLOYE", "Bearer x",
                "1.2.3.4", null), new ContratService.CommandeRefus(code, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_reponses_versions WHERE workspace_id = ?", Integer.class, ws))
                .isZero();

        ContratService.ReponseVue rep = service.refuserNouvelleVersion(ws, superviseur(user),
                new ContratService.CommandeRefus(code, true));
        assertThat(rep.reponse()).isEqualTo("REFUSEE");
        assertThat(rep.versionCode()).isEqualTo(code);
        Map<String, Object> ligne = jdbc.queryForMap("SELECT * FROM contrat_reponses_versions WHERE id = ?", rep.id());
        assertThat(ligne.get("signataire_compte")).isEqualTo("salma.bennani@jurika.ma");
        assertThat(ligne.get("signataire_user_id")).isEqualTo(user);
        assertThat(ligne.get("repondu_le_casablanca")).isEqualTo("2026-11-21 09:00:00");
        assertThat(ligne.get("adresse_ip")).isEqualTo("196.200.1.2");
        assertThat(ligne.get("acceptation_id")).isNull();

        // Resiliation a l'echeance courante, par le circuit existant (evenement pour l'abonnement).
        Map<String, Object> res = jdbc.queryForMap("SELECT * FROM contrat_resiliations WHERE workspace_id = ?", ws);
        assertThat(res.get("motif")).isEqualTo("Refus des conditions g\u00e9n\u00e9rales version " + code);
        assertThat(res.get("date_effet").toString()).isEqualTo("2027-11-01");
        assertThat(res.get("demandeur_role")).isEqualTo("SUPERVISEUR");
        assertThat(evenements).singleElement().isInstanceOfSatisfying(ContratEvenements.ResiliationEnregistree.class, ev -> {
            assertThat(ev.dateEffet()).isEqualTo(LocalDate.of(2027, 11, 1));
            assertThat(ev.motif()).isEqualTo("Refus des conditions g\u00e9n\u00e9rales version " + code);
        });
        ContratService.Etat etat = service.etat(ws);
        assertThat(etat.statut()).isEqualTo("RESILIE");
        assertThat(etat.acceptationRequise()).isFalse();
        assertThat(etat.resiliation().dateEffet()).isEqualTo(LocalDate.of(2027, 11, 1));

        // Pas de double reponse : ni second refus, ni acceptation apres le refus.
        assertThatThrownBy(() -> service.refuserNouvelleVersion(ws, superviseur(user), new ContratService.CommandeRefus(code, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> accepter(ws, user)).isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.CONFLICT));
        UUID cp = (UUID) ligne.get("conditions_id");
        UUID version = (UUID) ligne.get("version_id");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO contrat_reponses_versions (workspace_id, conditions_id, version_id, "
                + "version_code, reponse, signataire_user_id, signataire_compte, signataire_role, repondu_le, "
                + "repondu_le_casablanca, adresse_ip) VALUES (?, ?, ?, ?, 'REFUSEE', ?, 'x', 'SUPERVISEUR', now(), "
                + "'2026-11-21 09:00:00', '1.1.1.1')", ws, cp, version, code, user))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        // Super-admin : la reponse est visible par cabinet.
        ContratService.LigneAdmin admin = service.listeAdmin().stream().filter(l -> l.workspaceId().equals(ws))
                .findFirst().orElseThrow();
        assertThat(admin.derniereReponse().reponse()).isEqualTo("REFUSEE");
        assertThat(admin.versionSansReponse()).isNull();
        assertThat(service.historique(ws, true).reponses()).extracting(ContratService.ReponseVue::reponse)
                .containsExactly("REFUSEE");

        // Reponse inalterable, meme pour le proprietaire.
        assertThatThrownBy(() -> jdbc.update("UPDATE contrat_reponses_versions SET reponse = 'ACCEPTEE' WHERE id = ?", rep.id()))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM contrat_reponses_versions WHERE id = ?", rep.id()))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE contrat_reponses_versions"))
                .rootCause().hasMessageContaining("inalterable");
    }

    @Test
    void sans_reponse_le_super_admin_voit_la_version_en_attente_de_reponse() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "MENSUEL", "375");
        accepter(ws, UUID.randomUUID());
        String code = nouvelleVersionEnVigueur();
        ContratService.LigneAdmin admin = service.listeAdmin().stream().filter(l -> l.workspaceId().equals(ws))
                .findFirst().orElseThrow();
        assertThat(admin.versionSansReponse()).isNotNull();
        assertThat(admin.derniereReponse()).isNull();
        // Chaque lecture de l'etat (chaque connexion) redemande la reponse, sans blocage.
        assertThat(service.etat(ws).objetAAccepter()).isEqualTo(ContratService.OBJET_VERSION);
        assertThat(service.etat(ws).objetAAccepter()).isEqualTo(ContratService.OBJET_VERSION);
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        assertThat(code).isNotBlank();
    }

    @Test
    void une_modification_des_parametres_de_l_editeur_ne_change_pas_le_texte_accepte() {
        UUID ws = UUID.randomUUID();
        editeur.setRaisonSociale("Maghreb Consulting");
        editeur.setFormeJuridique("SARL");
        editeur.setIce("000000000000099");
        try {
            ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
            ContratService.AcceptationVue acc = accepter(ws, UUID.randomUUID());
            String texte = jdbc.queryForObject("SELECT texte_accepte FROM contrat_acceptations WHERE id = ?", String.class, acc.id());
            assertThat(texte).contains("**Maghreb Consulting**, SARL au capital de", "ICE 000000000000099");
            byte[] avant = service.exemplaire(ws, acc.id(), false).pdf();

            editeur.setRaisonSociale("Autre D\u00e9nomination");
            editeur.setFormeJuridique("SA");
            editeur.setIce("111111111111111");

            assertThat(jdbc.queryForObject("SELECT texte_accepte FROM contrat_acceptations WHERE id = ?", String.class, acc.id()))
                    .isEqualTo(texte);
            assertThat(jdbc.queryForObject("SELECT empreinte_contrat FROM contrat_acceptations WHERE id = ?", String.class,
                    acc.id()).trim()).isEqualTo(acc.empreinteContrat()).isEqualTo(ContratTexte.sha256(texte));
            assertThat(service.exemplaire(ws, acc.id(), false).pdf()).as("exemplaire inchange").isEqualTo(avant);
        } finally {
            editeur.setRaisonSociale(null);
            editeur.setFormeJuridique(null);
            editeur.setIce(null);
        }
    }

    @Test
    void base_vierge_seule_la_version_1_est_chargee_et_en_vigueur() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions WHERE origine = 'INITIALE'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT code FROM contrat_versions WHERE origine = 'INITIALE'", String.class))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions_retraits", Integer.class)).isZero();
        assertThat(service.etat(UUID.randomUUID()).versionEnVigueur().code()).isEqualTo("1");
        // Second demarrage : rien n'est recharge.
        service.initialiserVersionInitiale(ContratPlanification.VERSION_INITIALE, ContratPlanification.sourceInitiale());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions WHERE code = '1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void la_preuve_est_inalterable_meme_pour_le_proprietaire() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "ANNUEL", "3750");
        ContratService.AcceptationVue acc = accepter(ws, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("UPDATE contrat_acceptations SET adresse_ip = '1.1.1.1' WHERE id = ?", acc.id()))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM contrat_acceptations WHERE id = ?", acc.id()))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE contrat_acceptations CASCADE"))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("UPDATE conditions_particulieres SET prix_ht = 1 WHERE workspace_id = ?", ws))
                .rootCause().hasMessageContaining("figes");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM conditions_particulieres WHERE workspace_id = ?", ws))
                .rootCause().hasMessageContaining("suppression refusee");
        assertThatThrownBy(() -> jdbc.update("UPDATE contrat_versions SET code = 'x'"))
                .rootCause().hasMessageContaining("inalterable");
    }

    @Test
    void jurika_app_ni_update_ni_delete_sur_la_preuve_et_rls_par_cabinet() throws Exception {
        UUID wsA = UUID.randomUUID();
        UUID wsB = UUID.randomUUID();
        ouvrir(wsA, "ESSENTIEL", "ANNUEL", "3750");
        accepter(wsA, UUID.randomUUID());
        try (Connection app = DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", "jurika_app_it")) {
            app.setAutoCommit(false);
            try (Statement st = app.createStatement()) {
                st.execute("SELECT set_config('app.current_workspace_id', '" + wsA + "', true)");
                var rs = st.executeQuery("SELECT count(*) FROM contrat_acceptations");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            assertThat(sqlState(app, "UPDATE contrat_acceptations SET signataire_qualite = 'x'"))
                    .as("UPDATE refuse a jurika_app (REVOKE)").isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM contrat_acceptations")).isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM conditions_particulieres")).isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM contrat_resiliations")).isEqualTo("42501");
            assertThat(sqlState(app, "UPDATE contrat_versions SET code = code")).isEqualTo("42501");
            assertThat(sqlState(app, "UPDATE contrat_reponses_versions SET reponse = reponse")).isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM contrat_reponses_versions")).isEqualTo("42501");
            assertThat(sqlState(app, "INSERT INTO contrat_versions_retraits (version_id, motif) "
                    + "SELECT id, 'x' FROM contrat_versions LIMIT 1")).isEqualTo("42501");
            app.rollback();
            // RLS : le cabinet B ne voit rien du cabinet A.
            try (Statement st = app.createStatement()) {
                st.execute("SELECT set_config('app.current_workspace_id', '" + wsB + "', true)");
                var rs = st.executeQuery("SELECT count(*) FROM conditions_particulieres WHERE workspace_id = '" + wsA + "'");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
                rs = st.executeQuery("SELECT count(*) FROM contrat_acceptations");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            app.rollback();
            // Portee globale (super-admin, taches planifiees, passerelle) : lecture transverse.
            try (Statement st = app.createStatement()) {
                st.execute("SELECT set_config('app.contrat_portee', 'globale', true)");
                var rs = st.executeQuery("SELECT count(*) FROM conditions_particulieres WHERE workspace_id = '" + wsA + "'");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            app.rollback();
        }
    }

    @Test
    void chaque_modification_des_parametres_de_l_editeur_est_tracee() {
        UUID auteur = UUID.randomUUID();
        jdbc.update("UPDATE editeur_parametres SET ice = '001122334455667', forme_juridique = 'SARL', modifie_par = ? "
                + "WHERE id = 1", auteur);
        List<Map<String, Object>> traces = jdbc.queryForList(
                "SELECT champ, ancienne_valeur, nouvelle_valeur, modifie_par FROM editeur_parametres_historique "
                        + "WHERE modifie_par = ? ORDER BY champ", auteur);
        assertThat(traces).extracting(t -> t.get("champ")).containsExactly("forme_juridique", "ice");
        assertThat(traces.get(1).get("ancienne_valeur")).isNull();
        assertThat(traces.get(1).get("nouvelle_valeur")).isEqualTo("001122334455667");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM editeur_parametres_historique"))
                .rootCause().hasMessageContaining("inalterable");
        jdbc.update("UPDATE editeur_parametres SET ice = NULL, forme_juridique = NULL, modifie_par = ? WHERE id = 1", auteur);
    }

    private static String sqlState(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SAVEPOINT s");
            try {
                st.executeUpdate(sql);
                return "OK";
            } catch (SQLException ex) {
                st.execute("ROLLBACK TO SAVEPOINT s");
                return ex.getSQLState();
            }
        }
    }

    // -- Lot L7 (E13) : mention de l'acceptation des conditions de l'essai --------------------

    @Test
    void cabinet_avec_essai_mention_figee_dans_la_preuve_et_exemplaire_regenere_a_l_identique() throws Exception {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
        essai.set(() -> java.util.Optional.of(ContratTexteTest.ESSAI));
        ContratService.Apercu apercu = service.apercu(ws, superviseur(UUID.randomUUID()), commande(null, false, false));
        assertThat(apercu.texte()).contains(ContratTexteTest.MENTION_ESSAI);
        ContratService.AcceptationVue acc = service.accepter(ws, superviseur(UUID.randomUUID()),
                commande(apercu.empreinte(), true, true));
        Map<String, Object> preuve = jdbc.queryForMap("SELECT * FROM contrat_acceptations WHERE id = ?", acc.id());
        assertThat(preuve.get("essai_conditions_version")).isEqualTo("D-V1");
        assertThat(((java.sql.Timestamp) preuve.get("essai_conditions_acceptees_le")).toInstant())
                .isEqualTo(ContratTexteTest.ESSAI.accepteesLe());
        assertThat(preuve.get("texte_accepte").toString()).contains(ContratTexteTest.MENTION_ESSAI);
        assertThat(preuve.get("empreinte_contrat").toString().trim()).isEqualTo(apercu.empreinte());

        // Regeneration depuis la preuve seule : auth n'est plus interroge (injoignable ici).
        essai.set(() -> { throw new AssertionError("auth ne doit pas etre lu pour l'exemplaire"); });
        ContratService.Exemplaire ex = service.exemplaire(ws, acc.id(), false);
        assertThat(service.exemplaire(ws, acc.id(), false).pdf()).isEqualTo(ex.pdf());
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(ex.pdf())) {
            String extrait = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replace('\u2019', '\'')
                    .replaceAll("\\s+", " ");
            assertThat(extrait).contains(ContratTexteTest.MENTION_ESSAI);
        }
        // La colonne nouvelle reste sous les declencheurs d'inalterabilite.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE contrat_acceptations SET essai_conditions_version = 'D-V2' WHERE id = ?", acc.id()))
                .rootCause().hasMessageContaining("inalterable");
    }

    @Test
    void cabinet_sans_essai_aucune_mention_et_colonnes_vides() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
        ContratService.AcceptationVue acc = accepter(ws, UUID.randomUUID());
        Map<String, Object> preuve = jdbc.queryForMap("SELECT * FROM contrat_acceptations WHERE id = ?", acc.id());
        assertThat(preuve.get("essai_conditions_version")).isNull();
        assertThat(preuve.get("essai_conditions_acceptees_le")).isNull();
        assertThat(preuve.get("texte_accepte").toString()).doesNotContain("essai gratuit (version");
    }

    @Test
    void auth_injoignable_acceptation_refusee_rien_n_est_enregistre() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
        ContratService.Apercu apercu = service.apercu(ws, superviseur(UUID.randomUUID()), commande(null, false, false));
        ContratException injoignable = EssaiCabinetAuth.indisponible();
        essai.set(() -> { throw injoignable; });
        assertThatThrownBy(() -> service.accepter(ws, superviseur(UUID.randomUUID()),
                commande(apercu.empreinte(), true, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).code()).isEqualTo("ESSAI_INDISPONIBLE"))
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> service.apercu(ws, superviseur(UUID.randomUUID()), commande(null, false, false)))
                .isInstanceOf(ContratException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_acceptations WHERE workspace_id = ?", Integer.class, ws))
                .isZero();
        assertThat(service.acceptationEnAttente(ws)).isTrue();
    }

    @Test
    void essai_annonce_apres_lecture_sans_essai_le_texte_change_et_doit_etre_relu() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
        ContratService.Apercu apercu = service.apercu(ws, superviseur(UUID.randomUUID()), commande(null, false, false));
        essai.set(() -> java.util.Optional.of(ContratTexteTest.ESSAI));
        assertThatThrownBy(() -> service.accepter(ws, superviseur(UUID.randomUUID()),
                commande(apercu.empreinte(), true, true)))
                .isInstanceOf(ContratException.class).hasMessageContaining("relisez");
    }

    private static BillingProperties proprietes() {
        BillingProperties p = new BillingProperties();
        p.setSalesEmail("ventes@editeur.test");
        return p;
    }
}
