package ma.jurika.ai.document.corpus;

import ma.jurika.ai.document.corpus.ReferentielDemarches.Condition;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Delai;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Ligne;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4b (E1, RG-DEM-01) : le referentiel des demarches lu dans le corpus du cabinet.
 * Chiffres attendus comptes independamment (lecture brute des classeurs, 2026-10-11) :
 * 11 onglets, 7 006 lignes, 24 renvois, 301 decisions, 8 conditions en phrase, 161 delais
 * en texte seul, 73 delais chiffres, 8 echeances "au plus tard le $VAR".
 */
class LecteurDemarchesTest {

    /** Meme corpus que CorpusReelIT (variable JURIKA_CORPUS_REEL, sinon le corpus du serveur). */
    static final java.nio.file.Path RACINE = java.nio.file.Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-03"));

    static boolean corpusReelPresent() {
        return java.nio.file.Files.isRegularFile(RACINE.resolve("INDEX_CORPUS.xlsx"));
    }

    @Test
    void conditions_analysees_sans_invention() {
        assertThat(LecteurDemarches.condition(
                "$CAC_EXISTE = \u00ab oui \u00bb \u2014 (Si un commissaire aux comptes est en fonction)"))
                .isEqualTo(new Condition("CAC_EXISTE", "EGAL", "oui", "Si un commissaire aux comptes est en fonction"));
        assertThat(LecteurDemarches.condition("$DE_TVA_ASSUJETTISSEMENT existe"))
                .isEqualTo(new Condition("DE_TVA_ASSUJETTISSEMENT", "EXISTE", null, null));
        assertThat(LecteurDemarches.condition("Pi\u00e8ce produite au cours du ticket (r\u00e8gle RG-CLO-03)").operateur())
                .isEqualTo("PHRASE");
        assertThat(LecteurDemarches.condition("")).isNull();
        assertThat(LecteurDemarches.sousCondition("$REDUCTION_MOTIF = \u00ab retrait_associe \u00bb, cause \u00ab deces \u00bb"))
                .isEqualTo(new Condition("REDUCTION_MOTIF", "EGAL", "retrait_associe", "cause \u00ab deces \u00bb"));
    }

    @Test
    void delais_analyses_sans_invention() {
        assertThat(LecteurDemarches.delai("1 mois \u2014 texte de la matrice : Inscription RC dans le mois"))
                .extracting(Delai::valeur, Delai::unite, Delai::depuisVariable).containsExactly(1, "MOIS", null);
        assertThat(LecteurDemarches.delai("3 ans \u2014 texte de la matrice : x"))
                .extracting(Delai::valeur, Delai::unite).containsExactly(36, "MOIS");
        assertThat(LecteurDemarches.delai(
                "3 mois \u00e0 compter de $SUCCURSALE_DATE_OUVERTURE (art. 75 du code de commerce) \u2014 $SUCCURSALE_IMMATRICULATION_DATE_LIMITE"))
                .isEqualTo(new Delai("3 mois \u00e0 compter de $SUCCURSALE_DATE_OUVERTURE (art. 75 du code de commerce) \u2014 $SUCCURSALE_IMMATRICULATION_DATE_LIMITE",
                        3, "MOIS", "SUCCURSALE_DATE_OUVERTURE", "SUCCURSALE_IMMATRICULATION_DATE_LIMITE"));
        assertThat(LecteurDemarches.delai("au plus tard le $ANNONCE_LEGALE_DATE_LIMITE (d\u00e9lai non \u00e9tabli)").echeanceVariable())
                .isEqualTo("ANNONCE_LEGALE_DATE_LIMITE");
        assertThat(LecteurDemarches.delai("D\u00e9lai l\u00e9gal de la d\u00e9cision : \u2014")).isNull();
        Delai legal = LecteurDemarches.delai("D\u00e9lai l\u00e9gal de la d\u00e9cision : Inscription RC dans le mois");
        assertThat(legal.valeur()).as("un texte ne devient pas un chiffre").isNull();
        assertThat(LecteurDemarches.delai("1 mois au moins avant la fermeture (art. 66 du code du travail)").valeur())
                .as("delai avant un evenement : pas de calcul vers l'avant").isNull();
    }

    @Test
    void le_corpus_reel_est_lu_sans_perte() {
        Assumptions.assumeTrue(corpusReelPresent(), "corpus reel absent");
        ReferentielDemarches r = LecteurDemarches.lire(RACINE);
        assertThat(r.rapport().erreurs()).isEmpty();
        assertThat(r.rapport().lignesParOnglet()).hasSize(11);
        assertThat(r.rapport().total()).isEqualTo(7006);
        assertThat(r.rapport().lignesParOnglet())
                .containsEntry("MATRICE_DECISIONS_MODIFICATIONS_SARL", 2175)
                .containsEntry("MATRICE_SUCCURSALE_SOCIETE_ETRANGERE", 44)
                .containsEntry("MATRICE_IMPORT_DOSSIER", 12);
        assertThat(r.renvois()).hasSize(24);
        assertThat(r.decisions()).hasSize(301);
        assertThat(r.rapport().conditionsPhrases()).hasSize(8);
        assertThat(r.rapport().delaisTextuels()).hasSize(161);
        assertThat(r.lignes().stream().filter(l -> l.delai() != null && l.delai().valeur() != null)).hasSize(73);
        assertThat(r.lignes().stream().filter(l -> l.delai() != null && l.delai().echeanceVariable() != null
                && l.delai().valeur() == null)).hasSize(8);

        Map<String, Long> statuts = r.lignes().stream().collect(Collectors.groupingBy(Ligne::statut, Collectors.counting()));
        assertThat(statuts.keySet()).containsExactlyInAnyOrder(
                "CREATION_TICKET", "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "CLOTURE_DOSSIER", "ANNULE");
        assertThat(r.lignes()).filteredOn(l -> l.matrice().endsWith("_SARL_AU")).allMatch(l -> "SARL_AU".equals(l.forme()));
        assertThat(r.lignes()).filteredOn(l -> l.matrice().equals("MATRICE_INCIDENTS_ASSEMBLEE"))
                .allMatch(l -> l.forme() == null);
        // Decision a sous-condition : la variable est lue, le reste garde en texte.
        assertThat(r.lignes()).filteredOn(l -> "reduction_capital".equals(l.decision()) && l.sousCondition() != null
                && l.sousCondition().texte() != null).isNotEmpty()
                .allMatch(l -> "REDUCTION_MOTIF".equals(l.sousCondition().variable()));
        // Renvoi : la matrice de la famille fait foi.
        assertThat(r.renvois()).anySatisfy(rv -> {
            assertThat(rv.code()).isEqualTo("approbation_comptes");
            assertThat(rv.matriceRenvoi()).isEqualTo("MATRICE_COMPTES_RESULTAT_SARL");
            assertThat(rv.numeroRenvoi()).isEqualTo(1);
        });
        // Libelle d'une decision : celui de l'onglet d'origine.
        assertThat(r.decisions()).anySatisfy(d -> {
            assertThat(d.matrice()).isEqualTo("MATRICE_DISSOLUTION_LIQUIDATION_SARL");
            assertThat(d.code()).isEqualTo("dissolution_anticipee");
            assertThat(d.libelle()).startsWith("Dissolution anticip\u00e9e de la so");
        });
    }

    @Test
    void le_parcours_de_creation_du_corpus_a_51_lignes() {
        Assumptions.assumeTrue(corpusReelPresent(), "corpus reel absent");
        List<Ligne> creation = LecteurDemarches.lireParcoursCreation(RACINE);
        assertThat(creation).hasSize(51);
        // Valeurs brutes (comparaison avec les lignes en service) : condition non analysee.
        Ligne un = creation.get(0);
        assertThat(un.ordre()).isEqualTo(1);
        assertThat(un.statut()).isEqualTo("CREATION_TICKET");
        assertThat(un.libelle()).isEqualTo("Ouverture du ticket");
        assertThat(un.condition().texte()).isEqualTo("Tous dossiers");
        assertThat(un.delai().texte()).isEqualTo("J0");
        assertThat(LecteurDemarches.lire(RACINE).parcoursCreation()).hasSize(51);
    }
}
