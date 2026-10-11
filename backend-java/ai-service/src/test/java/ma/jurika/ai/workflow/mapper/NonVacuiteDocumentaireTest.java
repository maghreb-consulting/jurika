package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GROUPE 7 — <b>assertions de NON-VACUITÉ</b> (correction globale 2026-08-16).
 *
 * <p><b>La cause profonde du lot.</b> ~1 300 tests étaient verts pendant que des PV
 * sortaient avec « <i>M., propriétaire de ␣ parts sociales</i> » et « <i>présidée par ␣</i> ».
 * Les tests existants vérifient l'ABSENCE DE MARQUEURS : plus de {@code $VARIABLE}
 * résiduelle, plus de {@code ◇/◆}, plus de « VALEUR MANQUANTE ». Or ici les variables
 * étaient bel et bien <b>résolues</b> — en <b>chaîne vide</b>. Le document passait donc
 * tous les contrôles tout en étant juridiquement inutilisable.
 *
 * <p><b>Ce que ce test ajoute.</b> Un contrôle générique de « libellé suivi d'un vide » :
 * on ne demande plus seulement que la variable ait disparu, on exige qu'elle ait laissé
 * quelque chose derrière elle. Le contrôle est <i>réutilisable</i> — il ne connaît aucun
 * workflow, seulement les formes typiques d'un trou dans une phrase — et il est appliqué
 * aux documents de séance des workflows touchés, en <b>SARL ET SARL AU</b>.
 *
 * <p><b>Limite assumée</b> : ces assertions portent sur le CONTENU des documents. Elles ne
 * couvrent NI C1 (parcours IMPORT infranchissable) NI D2 (post-completion sauté) — deux
 * défauts d'état métier qui laissaient les documents parfaits. Ceux-là sont gardés par
 * {@code ImportWorkflowTest} et {@code PostCompletionSansDossierLieIT} (GROUPE 0).
 */
class NonVacuiteDocumentaireTest {

    private DocxTemplateEngine engine;
    private final DissolutionMapper dissolutionMapper = new DissolutionMapper();
    private final LiquidationMapper liquidationMapper = new LiquidationMapper();

    /**
     * Lot L4d (E11d) : les PV de dissolution et de cloture sont rendus par les modeles du
     * CORPUS (plus de copie au classpath) : ce controle les rend sur le corpus reel, et il est
     * saute sans lui.
     */
    private static final java.nio.file.Path CORPUS =
            java.nio.file.Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));

    @BeforeEach
    void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = java.nio.file.Files.isDirectory(CORPUS)
                ? new DocxTemplateEngine(loader, null, ma.jurika.ai.document.corpus.ChargeurCorpus.charger(CORPUS))
                : new DocxTemplateEngine(loader);
    }

    /** Rendu par le modele que le mapper choisit, avec la reprise de la fiche (chaine de production). */
    private List<String> rendre(ma.jurika.ai.workflow.WorkflowDocumentMapper mapper, String code,
                                Map<String, Object> p) throws Exception {
        return rendre(mapper.gabarit(code, p),
                ma.jurika.ai.workflow.identity.RepriseFicheSociete.completer(mapper.map(code, p), p));
    }

    // =====================================================================
    //  LE CONTRÔLE GÉNÉRIQUE — réutilisable par n'importe quel document
    // =====================================================================

    /**
     * Motifs de « libellé suivi d'un vide » relevés sur les documents réellement produits
     * pendant la simulation. Chacun correspond à une variable résolue en chaîne vide.
     */
    private static final Map<String, Pattern> TROUS = new LinkedHashMap<>() {{
        // « M., propriétaire de  parts sociales » — civilité seule, nom absent.
        put("civilité orpheline (« M., » / « Mme, »)",
                Pattern.compile("\\b(M\\.|Mme|Mlle)\\s*(,|;|\\.|$)", Pattern.MULTILINE));
        // « M.  M. Salma » — libellé répété : symptôme du nom composé deux fois (M5).
        // Pas de `\b` final : après « M. » vient un point, donc deux caractères non-mots
        // se suivent et la limite de mot ne peut JAMAIS matcher (le motif ne mordait pas).
        put("civilité répétée (« M.  M. »)",
                Pattern.compile("\\b(M\\.|Mme|Mlle)\\s+(M\\.|Mme|Mlle)(?![\\p{L}])"));
        // « propriétaire de  parts », « détenteur de  parts ».
        put("quantité manquante (« de  parts »)",
                Pattern.compile("\\bde\\s{2,}parts\\b"));
        // « présidée par , » / « présidée par . »
        put("président manquant (« présidée par ␣ »)",
                Pattern.compile("pr[ée]sid[ée]e?\\s+par\\s*(,|;|\\.|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE));
        // « CIN n° , » — le cas M6 exactement.
        put("n° de pièce manquant (« n° ␣ »)",
                Pattern.compile("n[°o]\\s*(,|;|\\.|$)", Pattern.MULTILINE));
        // « en qualité de , » / « demeurant à , »
        put("qualité ou adresse manquante",
                Pattern.compile("(en qualit[ée] de|demeurant [àa])\\s*(,|;|\\.|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE));
        // « : ; » ou « :  ; » — un poste annoncé sans valeur.
        put("valeur manquante après deux-points",
                Pattern.compile(":\\s*(;|\\.\\s*$)", Pattern.MULTILINE));
        // « «  » » — une citation vide.
        put("citation vide (« ⟨vide⟩ »)",
                Pattern.compile("[«\"]\\s*[»\"]"));
    }};

    /**
     * Échoue en nommant le motif ET la ligne fautive : un test qui dit seulement
     * « le document est vide quelque part » ne sert à rien à qui doit le corriger.
     */
    private static void assertAucunTrou(String contexte, List<String> lignes) {
        List<String> defauts = new ArrayList<>();
        for (String ligne : lignes) {
            if (ligne == null || ligne.isBlank()) continue;
            for (Map.Entry<String, Pattern> e : TROUS.entrySet()) {
                Matcher m = e.getValue().matcher(ligne);
                if (m.find()) {
                    defauts.add("  • " + e.getKey() + "  →  « " + ligne.trim() + " »");
                }
            }
        }
        assertThat(defauts)
                .describedAs("%s : le document rend des libellés SUIVIS D'UN VIDE.%n%s",
                        contexte, String.join("\n", defauts))
                .isEmpty();
    }

    /** Exige qu'une valeur attendue apparaisse réellement dans le document. */
    private static void assertPresent(String contexte, List<String> lignes, String... attendus) {
        String texte = String.join("\n", lignes);
        for (String attendu : attendus) {
            assertThat(texte)
                    .describedAs("%s : « %s » devrait figurer dans le document", contexte, attendu)
                    .contains(attendu);
        }
    }

    private List<String> rendre(String templateCode, Map<String, Object> vars) throws Exception {
        DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, vars);
        assertThat(result.templateFound())
                .describedAs("modèle introuvable : %s", templateCode).isTrue();
        assertThat(result.bytes()).describedAs("0 octet : %s", templateCode).isNotEmpty();
        List<String> lignes = new ArrayList<>();
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(result.bytes()))) {
            for (XWPFParagraph p : d.getParagraphs()) lignes.add(p.getText());
        }
        return lignes;
    }

    // =====================================================================
    //  Jeux de données — l'état NOMINAL, tel qu'il vient de la base
    // =====================================================================

    private static Map<String, Object> societe(String forme) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("denomination", "PARACOSME");
        m.put("capitalChiffres", 100_000);
        m.put("siegeSocial", "12 RUE DES FOULES, CASABLANCA");
        m.put("nombreParts", 1_000);
        m.put("rcNumero", "123456");
        m.put("villeGreffe", "CASABLANCA");
        m.put("formeJuridique", forme);
        m.put("ville", "Casablanca");
        m.put("rcVille", "Casablanca");
        m.put("ice", "001122334000055");
        m.put("ifNumero", "40998877");
        return m;
    }

    private static Map<String, Object> associe(String civ, String prenom, String nom,
                                               int parts, String cin) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("typePersonne", "PHYSIQUE");
        a.put("civilite", civ);
        a.put("prenom", prenom);
        a.put("nom", nom);
        a.put("adresse", "45 BD ZERKTOUNI, CASABLANCA");
        a.put("nombreParts", parts);
        a.put("nombreVoix", parts);
        a.put("presence", "présent");
        a.put("pieceType", "CIN");
        a.put("pieceNumero", cin);
        return a;
    }

    private static Map<String, Object> seance() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "extraordinaire");
        s.put("date", "2026-05-15");
        s.put("heure", "10H00");
        s.put("lieu", "SIEGE SOCIAL");
        s.put("presidentNom", "M. Ahmed ALAOUI");
        s.put("presidentQualite", "gérant");
        s.put("heureCloture", "12H00");
        return s;
    }

    private static Map<String, Object> liquidateur() {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "5 RUE D'ALGER, CASABLANCA");
        l.put("pieceType", "CIN");
        l.put("pieceNumero", "BE123456");
        l.put("genre", "masculin");
        l.put("siege", "12 RUE DES FOULES, CASABLANCA");
        l.put("siegeIdentique", "oui");
        l.put("nationalite", "marocaine");
        l.put("dateNaissance", "1975-03-12");
        l.put("mandatDateEffet", "2026-05-15");
        l.put("dureeMandat", "trois ans");
        l.put("remunerationApplicable", "non");
        l.put("redditionPeriodicite", "chaque semestre");
        return l;
    }

    private static Map<String, Object> payload(String forme, List<Map<String, Object>> associes) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe(forme));
        p.put("seance", seance());
        p.put("convocation", Map.of("auteur", "la gérance", "date", "2026-04-25",
                "mode", "lettre recommandée"));
        p.put("associes", associes);
        p.put("gerants", List.of(Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "ALAOUI")));
        p.put("liquidateur", liquidateur());
        p.put("dissolution", Map.of("motif", "volontaire", "nature", "volonte_associes", "dateEffet", "2026-05-15",
                "date", "2026-05-15"));
        // Lot L4d (E11d) : donnees saisies des modeles du corpus (dissolution et cloture).
        p.put("formeJuridique", forme);
        p.put("associeUnique", "SARL_AU".equals(forme));
        p.put("quorumAtteint", "oui");
        p.put("vote", Map.of("resultat", "adopt\u00e9e", "majorite", "\u00e0 l'unanimit\u00e9"));
        p.put("mentionsPv", Map.of("dossierReference", "DOS-1", "registreFolio", "3", "enregistrementLieu", "Casablanca",
                "annonceJournal", "LE JOURNAL", "quitusGerance", "oui", "cacExiste", "non", "salariesDixOuPlus", "non",
                "nombreOriginaux", "quatre"));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("dateClotureLiquidation", "2026-09-30");
        c.put("actifRealise", 300_000);
        c.put("passifRegle", 150_000);
        c.put("frais", 10_000);
        c.put("dissolutionDateEffet", "2026-05-15");
        c.put("compteDefinitifDate", "2026-09-15");
        c.put("dissolutionRcInscriptionDate", "2026-05-25");
        c.put("annonceJournal", "LE JOURNAL");
        c.put("annoncePublicationDate", "2026-05-28");
        c.put("convocationAuteur", "liquidateur");
        c.put("operationsResume", "l'actif a \u00e9t\u00e9 r\u00e9alis\u00e9 et le passif r\u00e9gl\u00e9");
        c.put("comptesBancairesClotures", "oui");
        c.put("compteDefinitifApprobation", "approuve");
        c.put("quitus", "accorde");
        c.put("retenueApplicable", "non");
        c.put("retenueNonApplicableMotif", "le boni est inf\u00e9rieur aux r\u00e9serves");
        c.put("repartitionModalites", "par virement bancaire");
        c.put("repartitionDateLimite", "2026-10-30");
        c.put("conservationDepositaireNom", "M. Ahmed ALAOUI");
        c.put("conservationDepositaireQualite", "liquidateur");
        c.put("conservationLieu", "5 RUE D'ALGER, CASABLANCA");
        c.put("conservationDuree", "dix ans");
        c.put("passifEventuelApplicable", "non");
        p.put("cloture", c);
        return p;
    }

    // =====================================================================
    //  D1 — présence / comparution : le défaut phare du GROUPE 1
    // =====================================================================

    @Nested
    @DisplayName("D1 — la section présence / comparution n'est jamais vide")
    class PresenceEtComparution {

        @BeforeEach
        void corpusReel() {
            org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(CORPUS), "corpus reel absent");
        }

        @Test
        @DisplayName("PV de dissolution SARL : chaque associé porte un nom ET un nombre de parts")
        void pvDissolutionSarl() throws Exception {
            Map<String, Object> p = payload("SARL", List.of(
                    associe("M.", "Ahmed", "ALAOUI", 600, "BE111111"),
                    associe("Mme", "Fatima", "BENNANI", 400, "BE222222")));
            List<String> lignes = rendre(dissolutionMapper, "PV_DISSOLUTION_LIQUIDATION_SARL", p);

            assertAucunTrou("PV dissolution SARL", lignes);
            // Les deux associés, leurs parts et le président de séance sont bien rendus.
            assertPresent("PV dissolution SARL", lignes,
                    "ALAOUI", "BENNANI", "600", "400", "Ahmed ALAOUI");
        }

        @Test
        @DisplayName("PV de dissolution SARL AU : la comparution de l'associé unique est complète")
        void pvDissolutionSarlAu() throws Exception {
            Map<String, Object> p = payload("SARL_AU", List.of(
                    associe("Mme", "Salma", "BENJELLOUN", 1_000, "BK987654")));
            List<String> lignes = rendre(dissolutionMapper, "PV_DISSOLUTION_LIQUIDATION_SARL_AU", p);

            assertAucunTrou("PV dissolution SARL AU", lignes);
            // M5 : le nom ne doit apparaître qu'UNE fois précédé de sa civilité.
            // Le modele du corpus (decisions de l'associe unique) n'imprime ni sa piece ni son
            // adresse : seule son identite, une fois, precedee de sa civilite (M5).
            assertPresent("PV dissolution SARL AU", lignes, "Mme Salma BENJELLOUN, personne physique");
        }

        @Test
        @DisplayName("PV de clôture de liquidation SARL AU : comparution + résultat complets")
        void pvClotureSarlAu() throws Exception {
            Map<String, Object> p = payload("SARL_AU", List.of(
                    associe("Mme", "Salma", "BENJELLOUN", 1_000, "BK987654")));
            List<String> lignes = rendre(liquidationMapper, "PV_DISSOLUTION_LIQUIDATION_SARL_AU", p);

            assertAucunTrou("PV clôture SARL AU", lignes);
            assertPresent("PV clôture SARL AU", lignes, "Mme Salma BENJELLOUN", "BK987654");
        }
    }

    // =====================================================================
    //  Le contrôle générique lui-même doit MORDRE — sinon il ne garde rien
    // =====================================================================

    @Nested
    @DisplayName("Le contrôle de non-vacuité détecte bien les défauts constatés")
    class LeControleMord {

        @Test
        @DisplayName("Il attrape les rendus exacts relevés pendant la simulation")
        void detecteLesCasReels() {
            // Chacune de ces lignes est un extrait RÉEL des documents produits le 2026-08-15.
            List<String> fautifs = List.of(
                    "Sont présents ou représentés : M., propriétaire de  parts sociales",
                    "La séance est présidée par .",
                    "M.  M. Salma BENJELLOUN, de nationalité marocaine",
                    "titulaire de la CIN n° ,",
                    "demeurant à ,");
            for (String ligne : fautifs) {
                boolean detecte = TROUS.values().stream()
                        .anyMatch(p -> p.matcher(ligne).find());
                assertThat(detecte)
                        .describedAs("Le contrôle DOIT détecter : « %s »", ligne)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("Il laisse passer une phrase correctement remplie (aucun faux positif)")
        void neCriePasSurDuTexteCorrect() {
            assertAucunTrou("phrases nominales", List.of(
                    "Mme Salma BENJELLOUN, de nationalité marocaine, demeurant à 45 BD ZERKTOUNI,"
                            + " titulaire de la CIN n° BK987654,",
                    "M. Ahmed ALAOUI, propriétaire de 600 parts sociales, présent ;",
                    "La séance est présidée par M. Ahmed ALAOUI, en qualité de gérant.",
                    "Le capital social est fixé à 100 000 dirhams (cent mille dirhams).",
                    "Réserve légale : 5 000 dirhams ;"));
        }
    }
}
