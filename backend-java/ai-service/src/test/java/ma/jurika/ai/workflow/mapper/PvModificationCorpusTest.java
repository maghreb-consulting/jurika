package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.corpus.CorpusCharge;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11c) : le proces-verbal de modification est rendu par les modeles du corpus du
 * cabinet, PV_AG_SARL (SARL) et PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU (SARL AU), avec la sortie
 * du mapper (meme chaine qu'en production : mapper, reprise de la fiche, modele choisi par
 * {@code gabarit()}).
 *
 * <p>Pour CHAQUE decision que l'ecran de la modification propose : aucune variable interne
 * manquante, aucun $VAR ni marqueur de bloc residuel, et la phrase attendue de la branche du
 * modele. Les rendus sont ecrits sous {@code target/output/pv_modification_corpus/} (docx et
 * texte) pour relecture. Sans corpus reel (CI), ces tests sont ignores (assumption).
 */
class PvModificationCorpusTest {

    private static final Path CORPUS = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
    private static final Path OUT = Path.of("target", "output", "pv_modification_corpus");
    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");

    private static DocxTemplateEngine engine;
    private final ModificationMapper mapper = new ModificationMapper();

    @BeforeAll
    static void charger() throws Exception {
        if (!Files.isDirectory(CORPUS)) return;
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        CorpusCharge corpus = ChargeurCorpus.charger(CORPUS);
        engine = new DocxTemplateEngine(loader, null, corpus);
        Files.createDirectories(OUT);
    }

    private static void corpusRequis() {
        Assumptions.assumeTrue(engine != null, "corpus reel absent : " + CORPUS);
    }

    // -- Choix du modele --------------------------------------------------

    @Test
    void le_pv_de_modification_est_rendu_par_les_modeles_du_corpus() {
        assertThat(mapper.gabarit("PV_MODIFICATION_SARL", Map.of())).isEqualTo("PV_AG_SARL");
        assertThat(mapper.gabarit("PV_MODIFICATION_SARL_AU", Map.of()))
                .isEqualTo("PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU");
        // Les autres documents du parcours gardent leur code.
        assertThat(mapper.gabarit("ANNONCE_LEGALE_MODIFICATION_SARL", Map.of()))
                .isEqualTo("ANNONCE_LEGALE_MODIFICATION_SARL");
        assertThat(mapper.gabarit("STATUTS_REFONDUS_SARL", Map.of())).isEqualTo("STATUTS_REFONDUS_SARL");
    }

    // -- Fixture de la verification des documents -------------------------

    @Test
    @SuppressWarnings("unchecked")
    void fixture_rendue_sans_variable_manquante() throws Exception {
        corpusRequis();
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/workflow-fixtures/modification.json")) {
            root = new ObjectMapper().readValue(in, Map.class);
        }
        String sarl = rendre("PV_MODIFICATION_SARL", "fixture_SARL",
                (Map<String, Object>) root.get("PV_MODIFICATION_SARL"));
        assertThat(sarl).contains("PROC\u00c8S-VERBAL DE L'ASSEMBL\u00c9E G\u00c9N\u00c9RALE EXTRAORDINAIRE",
                "45 avenue Hassan II, Casablanca", "\u00ab PARACOSME GROUP \u00bb",
                "en quatre exemplaires originaux").doesNotContain("TABLE DE CORRESPONDANCE");
        String au = rendre("PV_MODIFICATION_SARL_AU", "fixture_SARL_AU",
                (Map<String, Object>) root.get("PV_MODIFICATION_SARL_AU"));
        assertThat(au).contains("PROC\u00c8S-VERBAL DES D\u00c9CISIONS DE L'ASSOCI\u00c9 UNIQUE",
                "La g\u00e9rance est exerc\u00e9e par M. Karim BENALI, g\u00e9rant.",
                "sous le n\u00b0 12, feuillet 24", "en quatre exemplaires originaux");
    }

    /**
     * Ecart du CORPUS (A_DECIDER cabinet, CLAUDE.md, lot L4a) : le modele de l'associe unique
     * imprime $ASSOCIE_UNIQUE, drapeau "oui" / "non" du dictionnaire, a la place de l'identite
     * de l'associe unique (comparution et signature). Caracterisation : le jour ou le cabinet
     * corrige le modele, ce test echoue et se retire.
     */
    @Test
    void ecart_du_corpus_le_drapeau_associe_unique_s_imprime_a_la_place_de_son_identite() throws Exception {
        corpusRequis();
        String au = rendre("PV_MODIFICATION_SARL_AU", "ecart_associe_unique",
                pvAu(List.of(res("pouvoirs_formalites"))));
        assertThat(au).contains("\noui, de nationalit\u00e9 marocaine").contains("L'associ\u00e9 unique\noui\n");
    }

    // -- SARL : une resolution par decision proposee ----------------------

    @Test
    void sarl_comptes_et_distributions() throws Exception {
        corpusRequis();
        assertThat(sarl("approbation_comptes", ajout(res("approbation_comptes"),
                "exerciceClosDate", "2025-12-31", "resultatSens", "b\u00e9n\u00e9fice", "resultatMontant", 200_000)))
                .contains("faisant appara\u00eetre un b\u00e9n\u00e9fice de 200\u202f000 dirhams");
        assertThat(sarl("affectation_resultat", ajout(res("affectation_resultat"),
                "exerciceClosDate", "2025-12-31", "resultatMontant", 200_000,
                "affectations", List.of(Map.of("poste", "R\u00e9serve l\u00e9gale", "montant", 10_000),
                        Map.of("poste", "Report \u00e0 nouveau", "montant", 190_000)))))
                .contains("R\u00e9serve l\u00e9gale : 10\u202f000 dirhams ;", "Report \u00e0 nouveau : 190\u202f000 dirhams ;");
        assertThat(sarl("distribution_dividendes", ajout(res("distribution_dividendes"),
                "dividendeTotal", 100_000, "dividendeParPart", 100, "dateMisePaiement", "2026-09-30")))
                .contains("la somme globale de 100\u202f000 dirhams, soit 100 dirhams par part sociale");
        assertThat(sarl("distribution_reserves", ajout(res("distribution_reserves"),
                "prelevementMontant", 50_000, "prelevementPoste", "R\u00e9serves facultatives",
                "dividendeParPart", 50, "dateMisePaiement", "2026-09-30")))
                .contains("pr\u00e9lev\u00e9e sur le poste \u00ab R\u00e9serves facultatives \u00bb");
        assertThat(sarl("acompte_dividendes", ajout(res("acompte_dividendes"),
                "acompteMontant", 20_000, "acompteParPart", 20)))
                .contains("acompte sur dividendes d'un montant global de 20\u202f000 dirhams");
    }

    @Test
    void sarl_gerance() throws Exception {
        corpusRequis();
        assertThat(sarl("nomination_gerant", ajout(res("nomination_gerant"),
                "gerantSortant", "oui", "gerantSortantNom", "M. Karim BENALI", "gerantSortantMotif", "d\u00e9mission",
                "gerantSortantDateEffet", "2026-06-30", "dureeGerance", "trois ans",
                "gerants", List.of(gerantNomme()))))
                .contains("cessation des fonctions de M. Karim BENALI, g\u00e9rant, par suite de d\u00e9mission",
                        "pour une dur\u00e9e de trois ans", "M. Youssef TAZI, de nationalit\u00e9 marocaine");
        assertThat(sarl("renouvellement_gerant", ajout(res("renouvellement_gerant"),
                "gerantNom", "M. Karim BENALI", "dureeGerance", "trois ans")))
                .contains("renouveler le mandat de M. Karim BENALI, g\u00e9rant, pour une dur\u00e9e de trois ans");
        assertThat(sarl("revocation_gerant", ajout(res("revocation_gerant"), "gerantSortantNom", "M. Karim BENALI")))
                .contains("r\u00e9voquer M. Karim BENALI de ses fonctions de g\u00e9rant");
        assertThat(sarl("remuneration_gerant", ajout(res("remuneration_gerant"),
                "gerantNom", "M. Karim BENALI", "remunerationPeriodicite", "mensuelle",
                "remunerationMontant", 15_000, "remunerationDateEffet", "2026-08-01")))
                .contains("une r\u00e9mun\u00e9ration fixe mensuelle de 15\u202f000 dirhams, \u00e0 compter du 01/08/2026");
        assertThat(sarl("autorisation_gerance", ajout(res("autorisation_gerance"),
                "autorisationObjet", "consentir une caution au profit de la banque BMCE")))
                .contains("autorise la g\u00e9rance \u00e0 consentir une caution");
    }

    @Test
    void sarl_controle_et_formation() throws Exception {
        corpusRequis();
        assertThat(sarl("conventions_reglementees", res("conventions_reglementees")))
                .contains("rapport sp\u00e9cial de la g\u00e9rance sur les conventions");
        assertThat(sarl("cac_nomination", ajout(res("commissaire_comptes"), "cacAction", "nomination",
                "commissaireComptesNom", "Cabinet AUDIT CONSEIL", "dureeMandatCac", "trois",
                "cacFinMandatDate", "2028-12-31")))
                .contains("nomme Cabinet AUDIT CONSEIL en qualit\u00e9 de commissaire aux comptes de la soci\u00e9t\u00e9, pour une dur\u00e9e de trois exercices");
        assertThat(sarl("cac_renouvellement", ajout(res("commissaire_comptes"), "cacAction", "renouvellement",
                "commissaireComptesNom", "Cabinet AUDIT CONSEIL", "dureeMandatCac", "trois",
                "cacFinMandatDate", "2028-12-31")))
                .contains("renouvelle le mandat de Cabinet AUDIT CONSEIL");
        assertThat(sarl("cac_fin", ajout(res("commissaire_comptes"), "cacAction", "fin de mandat",
                "commissaireComptesNom", "Cabinet AUDIT CONSEIL")))
                .contains("met fin aux fonctions de Cabinet AUDIT CONSEIL");
        assertThat(sarl("ratification_actes_formation", ajout(res("ratification_actes_formation"),
                "engagementsMandat", "bail du local du 2 janvier 2026")))
                .contains("relat\u00e9s dans l'\u00e9tat annex\u00e9 aux statuts (bail du local du 2 janvier 2026)");
        assertThat(sarl("pouvoirs_formalites", res("pouvoirs_formalites")))
                .contains("conf\u00e8re tous pouvoirs au porteur d'un original");
    }

    @Test
    void sarl_augmentations_de_capital() throws Exception {
        corpusRequis();
        String numeraire = sarl("augmentation_numeraire", ajout(res("augmentation_capital_numeraire"),
                "augcapMontantChiffres", 50_000, "augcapNouveauCapital", 150_000,
                "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapNbPartsNouvelles", 500,
                "augcapLiberationMode", "num\u00e9raire", "primeEmission", "oui", "primeEmissionMontant", 20,
                "dpsSuppression", "oui", "articlesModifies", "6 et 7",
                "beneficiairesDps", List.of(Map.of("nom", "M. Youssef TAZI", "nbParts", 300),
                        Map.of("nom", "Mme Salma SAADI", "nbParts", 200))));
        assertThat(numeraire).contains(
                "augmenter le capital social d'une somme globale de 50\u202f000 dirhams (CINQUANTE MILLE), pour le porter de 100\u202f000 dirhams \u00e0 150\u202f000 dirhams",
                "par apport en num\u00e9raire, \u00e0 concurrence de 50\u202f000 dirhams",
                "cr\u00e9ation de 500 parts sociales nouvelles de 100 dirhams de valeur nominale chacune, num\u00e9rot\u00e9es de 1\u202f001 \u00e0 1\u202f500",
                "prime d'\u00e9mission de 20 dirhams par part, soit une prime globale de 10\u202f000 dirhams",
                "M. Youssef TAZI, \u00e0 concurrence de 300 parts", "articles 6 et 7");
        String compensation = sarl("augmentation_compensation", ajout(res("augmentation_capital_numeraire"),
                "augcapMontantChiffres", 50_000, "augcapMode", "cr\u00e9ation de parts nouvelles",
                "augcapNbPartsNouvelles", 500, "augcapLiberationMode", "compensation",
                "primeEmission", "non", "dpsSuppression", "non", "articlesModifies", "6 et 7"));
        assertThat(compensation).contains("par compensation avec des cr\u00e9ances liquides et exigibles",
                "b\u00e9n\u00e9ficient d'un droit pr\u00e9f\u00e9rentiel de souscription");
        String nature = sarl("augmentation_nature", ajout(res("augmentation_capital_nature"),
                "commissaireApportsNom", "M. Omar FASSI", "apporteurNom", "M. Youssef TAZI",
                "apportNatureDescription", "un v\u00e9hicule utilitaire", "augcapMontantChiffres", 30_000,
                "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapNbPartsNouvelles", 300,
                "fondsCommerce", "non", "primeEmission", "non", "dpsSuppression", "oui", "articlesModifies", "6 et 7"));
        assertThat(nature).contains("par apport en nature, \u00e0 concurrence de 30\u202f000 dirhams, portant sur un v\u00e9hicule utilitaire, apport\u00e9 par M. Youssef TAZI",
                "rapport du commissaire aux apports, M. Omar FASSI",
                "M. Youssef TAZI, \u00e0 concurrence de 300 parts");
    }

    /**
     * Non resolu (a faire corriger par le cabinet) : PV_AG_SARL imprime dans toute augmentation
     * le droit preferentiel de souscription et "la souscription integrale des parts nouvelles" ;
     * le PV de l'associe unique, "l'associe unique souscrira seul la totalite des parts
     * nouvelles". Une incorporation de reserves (attribution, pas de souscription) et une
     * elevation du nominal (aucune part nouvelle) y sortiraient avec une phrase fausse : refus.
     */
    @Test
    void incorporation_et_elevation_du_nominal_refusees_phrase_du_modele_fausse() {
        Map<String, Object> incorporation = ajout(res("augmentation_capital_incorporation"),
                "augcapMontantChiffres", 100_000, "incorporationPoste", "Report \u00e0 nouveau",
                "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapNbPartsNouvelles", 1_000,
                "dpsSuppression", "non", "articlesModifies", "6 et 7");
        assertRefus(pvSarl(List.of(incorporation)), "AUGMENTATION_MODALITE_TYPE");
        assertRefus(pvAu(List.of(incorporation)), "AUGMENTATION_MODALITE_TYPE");
        Map<String, Object> elevation = ajout(res("augmentation_capital_numeraire"),
                "augcapMontantChiffres", 100_000, "augcapMode", "\u00e9l\u00e9vation du nominal",
                "nouvelleValeurNominale", 200, "augcapLiberationMode", "num\u00e9raire",
                "primeEmission", "non", "dpsSuppression", "non", "souscriptionTiers", "non");
        assertRefus(pvSarl(List.of(elevation)), "AUGMENTATION_REALISATION");
        assertRefus(pvAu(List.of(elevation)), "AUGMENTATION_REALISATION");
    }

    /** Non resolu : PV_AG_SARL imprime une note de redaction dans la resolution de l'article 86. */
    @Test
    void capitaux_propres_sarl_refuses_note_de_redaction_du_modele() {
        assertRefus(pvSarl(List.of(ajout(res("capitaux_propres_art86"), "exerciceClosDate", "2025-12-31"))),
                "RESOLUTION_TYPE");
        assertRefus(pvAu(List.of(ajout(res("capitaux_propres_art86"), "exerciceClosDate", "2025-12-31",
                "approbationComptesDate", "2026-01-10", "situationNetteMontant", 20_000))),
                "ASSEMBLEE_APPROBATION_COMPTES_DATE");
    }

    @Test
    void sarl_reductions_de_capital() throws Exception {
        corpusRequis();
        assertThat(sarl("reduction_pertes", ajout(res("reduction_capital"), "redcapMotif", "pertes",
                "redcapMontant", 30_000, "redcapNouveauCapital", 70_000, "redcapMode", "diminution du nominal",
                "nouvelleValeurNominale", 70, "articlesModifies", "6 et 7")))
                .contains("afin d'apurer les pertes figurant au bilan, de r\u00e9duire le capital social d'une somme de 30\u202f000 dirhams (TRENTE MILLE), pour le ramener de 100\u202f000 dirhams \u00e0 70\u202f000 dirhams,",
                        "ramen\u00e9e de 100 dirhams \u00e0 70 dirhams", "n'ouvre pas aux cr\u00e9anciers le droit d'opposition");
        assertThat(sarl("reduction_rachat", ajout(res("reduction_capital"),
                "redcapMotif", "non motiv\u00e9e par des pertes", "redcapMontant", 20_000,
                "redcapMode", "annulation de parts rachet\u00e9es", "rachatPrixPart", 150,
                "rachatDateLimite", "2026-09-30", "primeRachatImputation", "le poste \u00ab R\u00e9serves facultatives \u00bb",
                "articlesModifies", "6 et 7")))
                .contains("au prix unitaire de 150 dirhams par part", "jusqu'au 30/09/2026",
                        "par voie d'annulation de 200 parts sociales de 100 dirhams chacune, le nombre de parts composant le capital \u00e9tant ainsi ramen\u00e9 de 1\u202f000 \u00e0 800",
                        "soit 10\u202f000 dirhams, est imput\u00e9e sur le poste \u00ab R\u00e9serves facultatives \u00bb",
                        "d\u00e9lai de trente jours ouvert aux cr\u00e9anciers");
    }

    @Test
    void sarl_identite_et_statuts() throws Exception {
        corpusRequis();
        assertThat(sarl("denomination", ajout(res("modification_denomination"),
                "nouvelleDenomination", "PARACOSME GROUP", "dateEffet", "2026-08-01", "enseigneModifiee", "non",
                "articlesModifies", "2")))
                .contains("qui devient, \u00e0 compter du 01/08/2026 : \u00ab PARACOSME GROUP \u00bb.")
                .doesNotContain("certificat n\u00e9gatif").doesNotContain("mettre en harmonie l'enseigne");
        assertThat(sarl("objet_extension", ajout(res("modification_objet"), "objetAction", "extension",
                "objetModification", "le conseil en organisation", "dateEffet", "2026-08-01", "articlesModifies", "3")))
                .contains("d\u00e9cide d'\u00e9tendre, \u00e0 compter du 01/08/2026, l'objet social aux op\u00e9rations suivantes : le conseil en organisation.");
        assertThat(sarl("objet_suppression", ajout(res("modification_objet"), "objetAction", "suppression",
                "objetModification", "le n\u00e9goce de mat\u00e9riaux", "dateEffet", "2026-08-01", "articlesModifies", "3")))
                .contains("retrancher de l'objet social");
        assertThat(sarl("siege_autre_prefecture", ajout(res("transfert_siege"), "siegeMemePrefecture", "non",
                "nouveauSiege", "45 avenue Mohammed V, Rabat", "villeGreffeArrivee", "Rabat",
                "dateEffet", "2026-08-01", "articlesModifies", "4")))
                .contains("dans une autre pr\u00e9fecture ou province, \u00e0 l'adresse suivante : 45 avenue Mohammed V, Rabat",
                        "\u00e0 Casablanca, qu'\u00e0 celui du nouveau si\u00e8ge, \u00e0 Rabat");
        assertThat(sarl("prorogation", ajout(res("prorogation_duree"), "dateExpirationInitiale", "2030-01-15",
                "prorogationDuree", "99", "nouvelleDureeEcheance", "2129-01-15", "articlesModifies", "5")))
                .contains("qui devait expirer le 15/01/2030, pour une nouvelle p\u00e9riode de 99 ann\u00e9es, soit jusqu'au 15/01/2129");
        assertThat(sarl("exercice", ajout(res("modification_exercice"), "nouvelleDateCloture", "30 juin",
                "exerciceTransitoireDuree", "18", "exerciceTransitoireDebut", "2026-01-01",
                "exerciceTransitoireFin", "2027-06-30", "articlesModifies", "24")))
                .contains("fix\u00e9e au 30 juin de chaque ann\u00e9e", "une dur\u00e9e de 18 mois, du 01/01/2026 au 30/06/2027");
        assertThat(sarl("mise_harmonie", res("mise_harmonie_statuts")))
                .contains("mise en harmonie des statuts de la soci\u00e9t\u00e9 avec les dispositions de la loi n\u00b0 5-96");
        assertThat(sarl("statuts_autre", ajout(res("modification_statuts_autre"), "articlesModifies", "15",
                "statutsNouveauLibelle", "\u00ab La soci\u00e9t\u00e9 est g\u00e9r\u00e9e par un ou plusieurs g\u00e9rants. \u00bb")))
                .contains("modifier les articles 15 des statuts, qui seront d\u00e9sormais libell\u00e9s");
    }

    @Test
    void sarl_parts_sociales() throws Exception {
        corpusRequis();
        assertThat(sarl("agrement_cession", ajout(res("agrement_cession"), "cessionNbParts", 100,
                "cessionPartsDe", 1, "cessionPartsA", 100, "cedantNom", "M. Karim BENALI",
                "cessionnaireNom", "M. Youssef TAZI", "cessionPrix", 100_000, "cessionnaireTiers", "oui")))
                .contains("agr\u00e9e express\u00e9ment la cession de CENT (100) parts sociales, num\u00e9rot\u00e9es de 1 \u00e0 100, envisag\u00e9e par M. Karim BENALI au profit de M. Youssef TAZI, au prix global de 100\u202f000 dirhams");
        assertThat(sarl("agrement_transmission", ajout(res("agrement_transmission"), "cessionNbParts", "400",
                "transmissionOrigine", "la succession de Mme Sofia ALAOUI", "cessionnaireNom", "M. Adam ALAOUI",
                "articlesModifies", "6 et 7")))
                .contains("transmission de 400 parts sociales d\u00e9pendant de la succession de Mme Sofia ALAOUI au profit de M. Adam ALAOUI");
        assertThat(sarl("nantissement", ajout(res("nantissement_parts"), "nantissementNbParts", "200",
                "nantissementConstituantNom", "M. Karim BENALI", "nantissementCreancierNom", "la banque CIH",
                "nantissementCreanceObjet", "la cr\u00e9ance n\u00e9e d'un pr\u00eat de 500 000 dirhams")))
                .contains("nantissement de 200 parts sociales appartenant \u00e0 M. Karim BENALI, au profit de la banque CIH");
    }

    @Test
    void sarl_transformation_et_restructuration() throws Exception {
        corpusRequis();
        assertThat(sarl("transformation", ajout(res("transformation"), "commissaireTransformationExiste", "oui",
                "commissaireTransformationNom", "M. Omar FASSI", "transformationForme", "soci\u00e9t\u00e9 anonyme",
                "transformationOrganes", "un conseil d'administration de trois membres")))
                .contains("du commissaire \u00e0 la transformation, M. Omar FASSI", "transformer la soci\u00e9t\u00e9 en soci\u00e9t\u00e9 anonyme");
        assertThat(sarl("designation_commissaire_transformation", ajout(res("designation_commissaire_transformation"),
                "commissaireTransformationNom", "M. Omar FASSI")))
                .contains("d\u00e9signe M. Omar FASSI en qualit\u00e9 de commissaire \u00e0 la transformation");
        assertThat(sarl("apport_partiel", ajout(res("operation_restructuration"),
                "restructurationNature", "apport partiel d'actif", "restructurationModalites", "d\u00e9p\u00f4t au greffe du trait\u00e9")))
                .contains("connaissance prise du projet de l'apport partiel d'actif").doesNotContain("projet de apport");
        assertThat(sarl("fusion", ajout(res("operation_restructuration"),
                "restructurationNature", "fusion", "restructurationModalites", "d\u00e9p\u00f4t au greffe du projet")))
                .contains("connaissance prise du projet de fusion");
    }

    @Test
    void sarl_plusieurs_resolutions_numerotees() throws Exception {
        corpusRequis();
        String t = rendre("PV_MODIFICATION_SARL", "SARL_multi", pvSarl(List.of(
                ajout(res("modification_denomination"), "nouvelleDenomination", "PARACOSME CONSULTING",
                        "dateEffet", "2026-06-01", "enseigneModifiee", "oui", "articlesModifies", "2"),
                ajout(res("transfert_siege"), "siegeMemePrefecture", "oui",
                        "nouveauSiege", "45 bd Zerktouni, Casablanca", "dateEffet", "2026-06-01", "articlesModifies", "4"),
                res("pouvoirs_formalites"))));
        assertThat(t).contains("PREMI\u00c8RE R\u00c9SOLUTION", "DEUXI\u00c8ME R\u00c9SOLUTION", "TROISI\u00c8ME R\u00c9SOLUTION",
                "mettre en harmonie l'enseigne");
    }

    // -- SARL AU ----------------------------------------------------------

    @Test
    void au_decisions_couvertes() throws Exception {
        corpusRequis();
        assertThat(au("approbation_comptes", ajout(res("approbation_comptes"),
                "exerciceClosDate", "2025-12-31", "resultatSens", "perte", "resultatMontant", 12_000)))
                .contains("r\u00e9sultat net d\u00e9ficitaire de 12\u202f000 dirhams (DOUZE MILLE)",
                        "greffe du tribunal de commerce de Casablanca", "soit au plus tard le 15/06/2026");
        assertThat(au("nomination_gerant", ajout(res("nomination_gerant"), "gerantSortant", "non",
                "dureeGerance", "trois ans", "gerants", List.of(gerantNomme()))))
                .contains("L'associ\u00e9 unique nomme en qualit\u00e9 de g\u00e9rant, pour une dur\u00e9e de trois ans");
        assertThat(au("revocation_gerant", ajout(res("revocation_gerant"), "gerantSortantNom", "M. Karim BENALI",
                "gerantSortantDateEffet", "2026-06-30")))
                .contains("r\u00e9voquer M. Karim BENALI de ses fonctions de g\u00e9rant de la soci\u00e9t\u00e9, \u00e0 effet du 30/06/2026.")
                .doesNotContain("fond\u00e9e sur les motifs");
        assertThat(au("conventions", ajout(res("conventions_reglementees"), "conventions",
                List.of(Map.of("date", "2026-02-01", "partieInteressee", "M. Karim BENALI", "objet", "un bail du d\u00e9p\u00f4t")))))
                .contains("convention du 01/02/2026 conclue avec M. Karim BENALI, ayant pour objet : un bail du d\u00e9p\u00f4t ;");
        assertThat(au("cac_nomination", ajout(res("commissaire_comptes"), "cacAction", "nomination",
                "commissaireComptesNom", "Cabinet AUDIT CONSEIL", "commissaireComptesAdresse", "10 rue Ibn Batouta, Casablanca",
                "dureeMandatCac", "trois", "cacFinMandatDate", "2028-12-31")))
                .contains("demeurant ou ayant son si\u00e8ge \u00e0 10 rue Ibn Batouta, Casablanca")
                .doesNotContain("honoraires du commissaire");
        assertThat(au("cac_cessation", ajout(res("commissaire_comptes"), "cacAction", "fin de mandat",
                "commissaireComptesNom", "Cabinet AUDIT CONSEIL", "cacCessationDateEffet", "2026-06-30",
                "cacCessationMotif", "la d\u00e9mission du commissaire")))
                .contains("\u00e0 effet du 30/06/2026, pour le motif suivant : la d\u00e9mission du commissaire.");
        assertThat(au("augmentation_numeraire", ajout(res("augmentation_capital_numeraire"),
                "augcapMontantChiffres", 50_000, "augcapMode", "cr\u00e9ation de parts nouvelles",
                "augcapNbPartsNouvelles", 500, "augcapLiberationMode", "num\u00e9raire", "primeEmission", "non",
                "souscriptionTiers", "non")))
                .contains("L'associ\u00e9 unique souscrira seul la totalit\u00e9 des parts nouvelles");
        assertThat(au("reduction", ajout(res("reduction_capital"), "redcapMotif", "pertes", "redcapMontant", 30_000,
                "redcapMode", "diminution du nominal", "reductionSuivieAugmentation", "non")))
                .contains("ramen\u00e9e de 100 dirhams \u00e0 70 dirhams").doesNotContain("indissociable");
        assertThat(au("refonte", ajout(res("mise_harmonie_statuts"),
                "refonteMotif", "la mise en harmonie des statuts avec la loi n\u00b0 5-96 telle que modifi\u00e9e")))
                .contains("refonte int\u00e9grale des statuts de la soci\u00e9t\u00e9 pour le motif suivant : la mise en harmonie");
        assertThat(au("clauses", ajout(res("modification_statuts_autre"), "articlesModifies", "15",
                "statutsNouveauLibelle", "\u00ab texte \u00bb")))
                .contains("modifier les articles 15 des statuts");
        assertThat(au("regularisation", ajout(res("capitaux_propres_art86"), "exerciceClosDate", "2025-12-31",
                "approbationComptesDate", "2026-04-20", "situationNetteMontant", 20_000)))
                .contains("s'\u00e9levant \u00e0 20\u202f000 dirhams, est devenue inf\u00e9rieure au quart du capital social");
        assertThat(au("passage_sarl", ajout(res("cession_parts_pluripersonnelle"), "cessionDate", "2026-06-15",
                "cessionNbParts", 400, "cessionPartsDe", 601, "cessionPartsA", 1000,
                "cessionnaireNom", "M. Youssef TAZI", "cessionPrix", 400_000)))
                .contains("QUATRE CENTS (400) parts sociales num\u00e9rot\u00e9es de 601 \u00e0 1\u202f000",
                        "au prix de MILLE (1\u202f000) dirhams par part",
                        "M. Karim BENALI : 600 parts (parts n\u00b0 1 \u00e0 600)",
                        "M. Youssef TAZI : 400 parts (parts n\u00b0 601 \u00e0 1\u202f000)");
        assertThat(au("pouvoirs", ajout(res("pouvoirs_formalites"), "pouvoirMandataireNom", "Mme Nadia KABBAJ")))
                // Le modele place chaque fragment conditionnel de la phrase dans son paragraphe.
                .contains("conf\u00e8re tous pouvoirs\n\u00e0 Mme Nadia KABBAJ, ainsi qu'au porteur");
    }

    @Test
    void au_decisions_non_couvertes_refusees_donnees_nommees() {
        // Le modele de l'associe unique ne prevoit pas d'acompte ("Aucun acompte sur dividendes
        // n'est organise par la reglementation marocaine") : refus nomme.
        assertRefus(pvAu(List.of(ajout(res("acompte_dividendes"), "acompteMontant", 20_000))), "RESOLUTION_TYPE");
        // Decisions du seul PV d'assemblee : refusees en SARL AU.
        assertThatThrownBy(() -> mapper.map("PV_MODIFICATION_SARL_AU", pvAu(List.of(ajout(res("agrement_cession"),
                "cessionnaireTiers", "oui")))))
                .isInstanceOf(GenerationRefuseeException.class);
    }

    // -- SARL AU : affectation du resultat et distributions (lot L5, E5) --

    private static Map<String, Object> affectationAu() {
        return ajout(res("affectation_resultat"), "exerciceClosDate", "2025-12-31", "resultatSens", "b\u00e9n\u00e9fice",
                "resultatMontant", 200_000, "reserveLegaleApplicable", "oui", "reserveLegaleDotationTaux", "5 %",
                "reserveLegalePlafond", "le cinqui\u00e8me du capital social", "reserveLegaleDotationMontant", 10_000,
                "affectations", List.of(Map.of("poste", "Dividendes", "montant", 150_000),
                        Map.of("poste", "Report \u00e0 nouveau", "montant", 40_000)));
    }

    private static Map<String, Object> dividendesAu() {
        return ajout(res("distribution_dividendes"), "exerciceClosDate", "2025-12-31",
                "approbationComptesDate", "2026-05-10", "beneficeDistribuable", 180_000, "dividendeTotal", 150_000,
                "dateMisePaiement", "2026-07-31", "prolongationOrdonnance", "non", "retenueSourceTaux", "10",
                "distributionExerciceOrigine", "2025", "retenueSourceExoneration", "non");
    }

    private static Map<String, Object> reservesAu() {
        return ajout(res("distribution_reserves"), "dateMisePaiement", "2026-07-31", "retenueSourceTaux", "10",
                "distributionExerciceOrigine", "2024", "retenueSourceExoneration", "non",
                "reservesPrelevees", List.of(
                        Map.of("libelle", "R\u00e9serves facultatives", "prelevement", 30_000, "soldeAvant", 80_000),
                        Map.of("libelle", "Autres r\u00e9serves", "prelevement", 20_000, "soldeAvant", 20_000)));
    }

    @Test
    void au_affectation_du_benefice_rendue() throws Exception {
        corpusRequis();
        assertThat(au("affectation_benefice", affectationAu())).contains(
                "affecter le b\u00e9n\u00e9fice net de l'exercice clos le 31/12/2025, s'\u00e9levant \u00e0 200\u202f000 dirhams (DEUX CENT MILLE)",
                "dotation \u00e0 la r\u00e9serve l\u00e9gale, \u00e0 hauteur de 5 % du b\u00e9n\u00e9fice net de l'exercice, le pr\u00e9l\u00e8vement cessant d'\u00eatre obligatoire lorsque la r\u00e9serve atteint le cinqui\u00e8me du capital social, soit 10\u202f000 dirhams ;",
                "Dividendes : 150\u202f000 dirhams (CENT CINQUANTE MILLE) ;",
                "Report \u00e0 nouveau : 40\u202f000 dirhams (QUARANTE MILLE) ;");
        String sansReserve = au("affectation_benefice_sans_reserve", ajout(affectationAu(),
                "reserveLegaleApplicable", "non", "reserveLegaleDotationMontant", "",
                "affectations", List.of(Map.of("poste", "Report \u00e0 nouveau", "montant", 200_000))));
        assertThat(sansReserve).contains("Report \u00e0 nouveau : 200\u202f000 dirhams").doesNotContain("r\u00e9serve l\u00e9gale");
    }

    @Test
    void au_affectation_de_la_perte_rendue() throws Exception {
        corpusRequis();
        assertThat(au("affectation_perte", ajout(res("affectation_resultat"), "exerciceClosDate", "2025-12-31",
                "resultatSens", "perte", "resultatMontant", 12_000, "capitauxPropresSousQuart", "oui")))
                .contains("affecter la perte nette de l'exercice clos le 31/12/2025, s'\u00e9levant \u00e0 12\u202f000 dirhams (DOUZE MILLE), au compte de report \u00e0 nouveau",
                        "la situation nette de la soci\u00e9t\u00e9 est devenue inf\u00e9rieure au quart du capital social");
    }

    @Test
    void au_distribution_de_dividendes_rendue() throws Exception {
        corpusRequis();
        String t = au("distribution_dividendes", dividendesAu());
        assertThat(t).contains(
                "approuv\u00e9s le 10/05/2026, s'\u00e9l\u00e8ve \u00e0 180\u202f000 dirhams (CENT QUATRE-VINGT MILLE)",
                "la somme de 150\u202f000 dirhams (CENT CINQUANTE MILLE), soit 150 dirhams par part sociale",
                "la totalit\u00e9 des 1\u202f000 parts sociales",
                "mis en paiement le 31/07/2026",
                "soit au plus tard le 30/09/2026",
                "au taux de 10 %, l'exercice d'origine des sommes distribu\u00e9es \u00e9tant l'exercice 2025, soit une retenue de 15\u202f000 dirhams")
                .doesNotContain("prolong\u00e9 par ordonnance").doesNotContain("exon\u00e9r\u00e9s de la retenue");
        // Prolongation par ordonnance, associe unique societe exoneree.
        Map<String, Object> pv = pvAu(List.of(ajout(dividendesAu(), "prolongationOrdonnance", "oui",
                "prolongationOrdonnanceDate", "2026-09-15", "prolongationDateNouvelle", "2026-12-31",
                "dateMisePaiement", "2026-11-30", "retenueSourceExoneration", "oui", "retenueSourceTaux", "11,25")));
        pv.put("associes", List.of(Map.of("typePersonne", "MORALE", "denomination", "HOLDING ATLAS SA",
                "nombreParts", 1_000, "presence", "pr\u00e9sent")));
        String p = rendreSansControle("PV_MODIFICATION_SARL_AU", "SARL_AU_distribution_dividendes_prolongation", pv);
        assertThat(p).contains("tribunal de commerce de Casablanca, statuant en r\u00e9f\u00e9r\u00e9, en date du 15/09/2026, jusqu'au 31/12/2026",
                "au taux de 11,25 %", "soit une retenue de 16\u202f875 dirhams", "exon\u00e9r\u00e9s de la retenue \u00e0 la source");
    }

    @Test
    void au_distribution_de_reserves_rendue() throws Exception {
        corpusRequis();
        assertThat(au("distribution_reserves", reservesAu())).contains(
                "une somme globale de 50\u202f000 dirhams, pr\u00e9lev\u00e9e sur les postes de r\u00e9serves disponibles suivants",
                "R\u00e9serves facultatives : pr\u00e9l\u00e8vement de 30\u202f000 dirhams, le poste \u00e9tant ramen\u00e9 de 80\u202f000 dirhams \u00e0 50\u202f000 dirhams ;",
                "Autres r\u00e9serves : pr\u00e9l\u00e8vement de 20\u202f000 dirhams, le poste \u00e9tant ramen\u00e9 de 20\u202f000 dirhams \u00e0 0 dirhams ;",
                "soit 50 dirhams par part sociale", "mise en paiement le 31/07/2026",
                "l'exercice d'origine des r\u00e9serves distribu\u00e9es \u00e9tant l'exercice 2024, soit une retenue de 5\u202f000 dirhams");
    }

    /** Les montants derives sont calcules (jamais saisis deux fois) ; mode CI, sans corpus. */
    @Test
    @SuppressWarnings("unchecked")
    void au_distributions_montants_derives() {
        Map<String, Object> d = ((List<Map<String, Object>>) mapper.map("PV_MODIFICATION_SARL_AU",
                pvAu(List.of(dividendesAu()))).get("RESOLUTIONS")).get(0);
        assertThat(d).containsEntry("RESOLUTION_TYPE", "distribution_dividendes")
                .containsEntry("DISTRIBUTION_MONTANT_PAR_PART", "150")
                .containsEntry("DIVIDENDES_DATE_LIMITE_PAIEMENT", "30/09/2026")
                .containsEntry("RETENUE_SOURCE_TAUX", "10 %")
                .containsEntry("RETENUE_SOURCE_MONTANT", "15\u202f000");
        Map<String, Object> vars = mapper.map("PV_MODIFICATION_SARL_AU", pvAu(List.of(reservesAu())));
        Map<String, Object> r = ((List<Map<String, Object>>) vars.get("RESOLUTIONS")).get(0);
        assertThat(r).containsEntry("RESERVES_PRELEVEMENT_TOTAL", "50\u202f000")
                .containsEntry("DISTRIBUTION_MONTANT_PAR_PART", "50");
        assertThat((List<Map<String, Object>>) vars.get("RESERVES_PRELEVEES")).extracting(m -> m.get("RESERVE_POSTE_SOLDE_APRES"))
                .containsExactly("50\u202f000", "0");
        // Retenue au centime : 33 000 x 11,25 % = 3 712,50.
        Map<String, Object> c = ((List<Map<String, Object>>) mapper.map("PV_MODIFICATION_SARL_AU",
                pvAu(List.of(ajout(dividendesAu(), "dividendeTotal", 33_000, "retenueSourceTaux", "11,25")))).get("RESOLUTIONS")).get(0);
        assertThat(c).containsEntry("RETENUE_SOURCE_MONTANT", "3\u202f712,50").containsEntry("DISTRIBUTION_MONTANT_PAR_PART", "33");
        Map<String, Object> a = ((List<Map<String, Object>>) mapper.map("PV_MODIFICATION_SARL_AU",
                pvAu(List.of(affectationAu()))).get("RESOLUTIONS")).get(0);
        assertThat(a).containsEntry("RESOLUTION_TYPE", "affectation_resultat").containsEntry("RESULTAT_NET_CHIFFRES", "200\u202f000");
    }

    /** Donnees qui pilotent une branche ou contredisent le calcul : refusees, nommees ; mode CI. */
    @Test
    void au_distributions_refus_nommes() {
        assertRefus(pvAu(List.of(ajout(affectationAu(), "resultatSens", ""))), "RESULTAT_NET_SENS");
        assertRefus(pvAu(List.of(ajout(affectationAu(), "reserveLegaleApplicable", ""))), "RESERVE_LEGALE_APPLICABLE");
        assertRefus(pvAu(List.of(ajout(affectationAu(), "resultatMontant", 210_000))), "AFFECTATION_RESULTAT");
        assertRefus(pvAu(List.of(ajout(affectationAu(), "affectations",
                List.of(Map.of("poste", "R\u00e9serve l\u00e9gale", "montant", 10_000),
                        Map.of("poste", "Dividendes", "montant", 180_000))))), "RESERVE_LEGALE_APPLICABLE");
        assertRefus(pvAu(List.of(ajout(affectationAu(), "reserveLegaleApplicable", "non",
                "reserveLegaleDotationMontant", "", "affectations", List.of()))), "AFFECTATION_RESULTAT");
        assertRefus(pvAu(List.of(ajout(res("affectation_resultat"), "exerciceClosDate", "2025-12-31",
                "resultatSens", "perte", "resultatMontant", 12_000))), "CAPITAUX_PROPRES_SOUS_QUART");

        assertRefus(pvAu(List.of(ajout(dividendesAu(), "prolongationOrdonnance", ""))), "DISTRIBUTION_PROLONGATION_ORDONNANCE");
        assertRefus(pvAu(List.of(ajout(dividendesAu(), "retenueSourceExoneration", ""))), "RETENUE_SOURCE_EXONERATION_APPLICABLE");
        assertRefus(pvAu(List.of(ajout(dividendesAu(), "dividendeTotal", 190_000))), "DISTRIBUTION_MONTANT_CHIFFRES");
        assertRefus(pvAu(List.of(ajout(dividendesAu(), "dateMisePaiement", "2026-10-15"))), "DIVIDENDE_DATE_MISE_EN_PAIEMENT");
        assertRefus(pvAu(List.of(ajout(dividendesAu(), "dividendeTotal", 100_001))), "DISTRIBUTION_MONTANT_PAR_PART");
        assertRefus(pvAu(List.of(ajout(dividendesAu(), "retenueSourceTaux", "abc"))), "RETENUE_SOURCE_TAUX");
        // L'exoneration ne vise qu'un associe unique societe : refusee pour une personne physique.
        assertRefus(pvAu(List.of(ajout(dividendesAu(), "retenueSourceExoneration", "oui"))),
                "RETENUE_SOURCE_EXONERATION_APPLICABLE");

        assertRefus(pvAu(List.of(ajout(reservesAu(), "reservesPrelevees", List.of()))), "RESERVES_PRELEVEES");
        assertRefus(pvAu(List.of(ajout(reservesAu(), "reservesPrelevees",
                List.of(Map.of("libelle", "R\u00e9serve l\u00e9gale", "prelevement", 10_000, "soldeAvant", 20_000))))),
                "RESERVE_POSTE_LIBELLE");
        assertRefus(pvAu(List.of(ajout(reservesAu(), "reservesPrelevees",
                List.of(Map.of("libelle", "R\u00e9serves facultatives", "prelevement", 90_000, "soldeAvant", 80_000))))),
                "RESERVE_POSTE_PRELEVEMENT");
        // Deux distributions de reserves : une seule liste RESERVES_PRELEVEES par proces-verbal.
        assertRefus(pvAu(List.of(reservesAu(), reservesAu())), "RESERVES_PRELEVEES");
    }

    // -- Refus : choix exiges, incoherences, boucles ----------------------

    @Test
    void choix_absents_ou_incoherents_refuses_et_nommes() {
        assertRefus(pvSarl(List.of(ajout(res("modification_denomination"), "nouvelleDenomination", "X",
                "dateEffet", "2026-08-01"))), "DENOMINATION_ENSEIGNE_MODIFIEE");
        assertRefus(pvSarl(List.of(ajout(res("augmentation_capital_numeraire"), "augcapMontantChiffres", 50_000,
                "augcapNouveauCapital", 160_000, "augcapMode", "cr\u00e9ation de parts nouvelles",
                "augcapLiberationMode", "num\u00e9raire"))), "CAPITAL_NOUVEAU_CHIFFRES");
        assertRefus(pvSarl(List.of(ajout(res("augmentation_capital_numeraire"), "augcapMontantChiffres", 50_000,
                "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapNbPartsNouvelles", 400,
                "augcapLiberationMode", "num\u00e9raire"))), "AUGMENTATION_PARTS_NOUVELLES");
        assertRefus(pvSarl(List.of(ajout(res("reduction_capital"), "redcapMotif", "non motiv\u00e9e par des pertes",
                "redcapMontant", 20_000, "redcapMode", "diminution du nominal"))), "REDUCTION_MODALITE");
        assertRefus(pvSarl(List.of(ajout(res("agrement_cession"), "cessionnaireTiers", "non"))), "CESSIONNAIRE_TIERS");
        assertRefus(pvSarl(List.of(ajout(res("augmentation_capital_numeraire"), "augcapMontantChiffres", 50_000,
                "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapNbPartsNouvelles", 500,
                "augcapLiberationMode", "num\u00e9raire", "primeEmission", "non", "dpsSuppression", "oui"))),
                "BENEFICIAIRES_DPS");
        assertRefus(pvSarl(List.of(
                ajout(res("augmentation_capital_numeraire"), "augcapMontantChiffres", 10_000,
                        "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapLiberationMode", "num\u00e9raire",
                        "primeEmission", "non", "dpsSuppression", "non"),
                ajout(res("augmentation_capital_nature"), "augcapMontantChiffres", 10_000))),
                "AUGMENTATION_MODALITES");
        assertRefus(pvSarl(List.of(ajout(res("nomination_gerant"), "gerants", List.of(gerantNomme())),
                ajout(res("nomination_gerant"), "gerants", List.of(gerantNomme())))), "GERANTS");
        assertRefus(pvAu(List.of(ajout(res("augmentation_capital_numeraire"), "augcapMontantChiffres", 50_000,
                "augcapMode", "cr\u00e9ation de parts nouvelles", "augcapNbPartsNouvelles", 500,
                "augcapLiberationMode", "num\u00e9raire", "primeEmission", "non"))),
                "ETAT_REPARTITION_EFFET_UNIPERSONNALITE");
        assertRefus(pvAu(List.of(res("conventions_reglementees"))), "CONVENTIONS_REGLEMENTEES");
    }

    /** Une donnee interne absente n'est pas inventee : le rendu la nomme (refus du controleur). */
    @Test
    void donnees_internes_absentes_nommees_au_rendu() throws Exception {
        corpusRequis();
        Map<String, Object> pv = pvAu(List.of(ajout(res("transfert_siege"), "siegeMemePrefecture", "non",
                "nouveauSiege", "45 avenue Mohammed V, Rabat", "dateEffet", "2026-08-01")));
        pv.remove("registre");
        pv.remove("nombreOriginaux");
        DocxTemplateEngine.DocumentResult r = engine.generate(mapper.gabarit("PV_MODIFICATION_SARL_AU", pv),
                RepriseFiche.completer(mapper.map("PV_MODIFICATION_SARL_AU", pv), pv));
        assertThat(r.manquantesBloquantes()).extracting(m -> m.nom())
                .contains("VILLE_GREFFE_ARRIVEE", "REGISTRE_PV_NUMERO", "REGISTRE_PV_FEUILLET", "NOMBRE_ORIGINAUX");
    }

    /**
     * "deposera au greffe du tribunal de commerce de $TRIBUNAL_VILLE" : la ville n'est deduite
     * que si le registre est tenu dans une ville de tribunal de commerce ; ailleurs, la donnee
     * est nommee (jamais "tribunal de commerce de Kenitra").
     */
    @Test
    void tribunal_de_commerce_non_deduit_hors_ville_de_tribunal_de_commerce() throws Exception {
        corpusRequis();
        Map<String, Object> pv = pvAu(List.of(ajout(res("approbation_comptes"),
                "exerciceClosDate", "2025-12-31", "resultatSens", "b\u00e9n\u00e9fice", "resultatMontant", 12_000)));
        @SuppressWarnings("unchecked")
        Map<String, Object> societe = (Map<String, Object>) pv.get("societe");
        societe.put("villeGreffe", "K\u00e9nitra");
        societe.put("rcVille", "K\u00e9nitra");
        DocxTemplateEngine.DocumentResult r = engine.generate(mapper.gabarit("PV_MODIFICATION_SARL_AU", pv),
                RepriseFiche.completer(mapper.map("PV_MODIFICATION_SARL_AU", pv), pv));
        assertThat(r.manquantesBloquantes()).extracting(m -> m.nom()).contains("TRIBUNAL_VILLE");
    }

    // -- Rendu ------------------------------------------------------------

    private void assertRefus(Map<String, Object> pv, String variable) {
        String code = Boolean.TRUE.equals(pv.get("associeUnique")) ? "PV_MODIFICATION_SARL_AU" : "PV_MODIFICATION_SARL";
        assertThatThrownBy(() -> mapper.map(code, pv))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable).contains(variable));
    }

    private String sarl(String nom, Map<String, Object> resolution) throws Exception {
        return rendre("PV_MODIFICATION_SARL", "SARL_" + nom, pvSarl(List.of(resolution)));
    }

    private String au(String nom, Map<String, Object> resolution) throws Exception {
        return rendre("PV_MODIFICATION_SARL_AU", "SARL_AU_" + nom, pvAu(List.of(resolution)));
    }

    private String rendre(String code, String nom, Map<String, Object> payload) throws Exception {
        Map<String, Object> vars = RepriseFiche.completer(mapper.map(code, payload), payload);
        String gabarit = mapper.gabarit(code, payload);
        DocxTemplateEngine.DocumentResult r = engine.generate(gabarit, vars);
        Files.write(OUT.resolve(nom + ".docx"), r.bytes());
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
        }
        String texte = sb.toString();
        Files.writeString(OUT.resolve(nom + ".txt"), texte);
        assertThat(r.missingVariables()).describedAs("%s : variables manquantes", nom).isEmpty();
        Matcher v = RESIDUAL_VAR.matcher(texte);
        assertThat(v.find()).describedAs("%s : variable residuelle %s", nom, v.reset().find() ? v.group() : "").isFalse();
        assertThat(RESIDUAL_MARKER.matcher(texte).find()).describedAs("%s : marqueur de bloc residuel", nom).isFalse();
        assertThat(texte).doesNotContain("VALEUR MANQUANTE").doesNotContain("TABLE DE CORRESPONDANCE");
        return texte;
    }

    /**
     * Rendu sans le controle des variables manquantes : l'associe unique personne morale n'a pas
     * encore d'ecran d'identite (forme, siege, RC : dette L4d), seule la decision est relue.
     */
    private String rendreSansControle(String code, String nom, Map<String, Object> payload) throws Exception {
        Map<String, Object> vars = RepriseFiche.completer(mapper.map(code, payload), payload);
        DocxTemplateEngine.DocumentResult r = engine.generate(mapper.gabarit(code, payload), vars);
        Files.write(OUT.resolve(nom + ".docx"), r.bytes());
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
        }
        String texte = sb.toString();
        Files.writeString(OUT.resolve(nom + ".txt"), texte);
        assertThat(RESIDUAL_MARKER.matcher(texte).find()).describedAs("%s : marqueur de bloc residuel", nom).isFalse();
        return texte;
    }

    // -- Charges ----------------------------------------------------------

    /** Reprise de la fiche societe, comme le controleur. */
    private static final class RepriseFiche {
        static Map<String, Object> completer(Map<String, Object> vars, Map<String, Object> payload) {
            return ma.jurika.ai.workflow.identity.RepriseFicheSociete.completer(vars, payload);
        }
    }

    private static Map<String, Object> societe(String forme, String denomination) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("denomination", denomination);
        s.put("formeJuridique", forme);
        s.put("capitalChiffres", 100_000);
        s.put("siegeSocial", "12 rue des Foules, Casablanca");
        s.put("nombreParts", 1_000);
        s.put("rcNumero", "123456");
        s.put("villeGreffe", "Casablanca");
        s.put("rcVille", "Casablanca");
        s.put("ice", "001122334000055");
        return s;
    }

    private static Map<String, Object> seance() {
        return Map.of("type", "extraordinaire", "date", "2026-05-16", "heure", "10 heures",
                "lieu", "au si\u00e8ge social", "heureCloture", "12 heures",
                "presidentNom", "M. Karim BENALI", "presidentQualite", "g\u00e9rant", "quorumAtteint", "oui");
    }

    private static Map<String, Object> pvSarl(List<Map<String, Object>> resolutions) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("formeJuridique", "SARL");
        p.put("societe", societe("SARL", "PARACOSME"));
        p.put("seance", seance());
        p.put("convocation", Map.of("auteur", "la g\u00e9rance", "date", "2026-04-30",
                "mode", "lettre recommand\u00e9e avec accus\u00e9 de r\u00e9ception"));
        p.put("vote", Map.of("resultat", "adopt\u00e9e", "majorite", "\u00e0 l'unanimit\u00e9"));
        p.put("quorumAtteint", "oui");
        p.put("associes", List.of(
                Map.of("civilite", "M.", "prenom", "Karim", "nom", "BENALI", "nombreParts", 600, "presence", "pr\u00e9sent"),
                Map.of("civilite", "Mme", "prenom", "Sofia", "nom", "ALAOUI", "nombreParts", 400, "presence", "pr\u00e9sent")));
        p.put("gerants", List.of(Map.of("civilite", "M.", "prenom", "Karim", "nom", "BENALI")));
        p.put("ordreDuJour", ordreDuJour(resolutions));
        p.put("resolutions", resolutions);
        p.put("nombreOriginaux", "quatre");
        return p;
    }

    private static Map<String, Object> pvAu(List<Map<String, Object>> resolutions) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("formeJuridique", "SARL_AU");
        p.put("associeUnique", true);
        p.put("societe", societe("SARL_AU", "PARACOSME AU"));
        p.put("seance", seance());
        p.put("associes", List.of(Map.of("typePersonne", "PHYSIQUE", "civilite", "M.", "prenom", "Karim",
                "nom", "BENALI", "nationalite", "marocaine", "adresse", "5 boulevard Zerktouni, Casablanca",
                "pieceType", "CIN", "pieceNumero", "BE123456", "nombreParts", 1_000, "presence", "pr\u00e9sent")));
        p.put("gerants", List.of(Map.of("civilite", "M.", "prenom", "Karim", "nom", "BENALI")));
        p.put("gerantEstAssocie", "oui");
        p.put("ordreDuJour", ordreDuJour(resolutions));
        p.put("resolutions", resolutions);
        p.put("nombreOriginaux", "quatre");
        p.put("registre", Map.of("numero", "12", "feuillet", "24"));
        return p;
    }

    private static List<String> ordreDuJour(List<Map<String, Object>> resolutions) {
        List<String> odj = new ArrayList<>();
        for (Map<String, Object> r : resolutions) odj.add(String.valueOf(r.get("type")).replace('_', ' '));
        return odj;
    }

    private static Map<String, Object> gerantNomme() {
        return Map.of("civilite", "M.", "prenom", "Youssef", "nom", "TAZI", "nationalite", "marocaine",
                "dateNaissance", "1985-03-12", "adresse", "Casablanca", "pieceType", "CIN", "pieceNumero", "BE998877");
    }

    private static Map<String, Object> res(String type) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", type);
        return r;
    }

    private static Map<String, Object> ajout(Map<String, Object> base, Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>(base);
        for (int i = 0; i + 1 < paires.length; i += 2) m.put(String.valueOf(paires[i]), paires[i + 1]);
        return m;
    }
}
