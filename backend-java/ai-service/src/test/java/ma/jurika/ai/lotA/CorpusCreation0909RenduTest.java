package ma.jurika.ai.lotA;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot A — GÉNÉRATION TÉMOIN DES 23 GABARITS DU 9 SEPTEMBRE 2026.
 *
 * <p>Ce test ne remplace pas la suite : il produit les documents et rapporte ce
 * qu'on y LIT. Une suite entièrement verte avait laissé passer quarante-trois
 * défauts au lot 5 ; on ouvre donc les fichiers.
 *
 * <p>Il travaille sur des copies inchangées des gabarits du cabinet
 * ({@code src/test/resources/lotA/gabarits}) et sur quatre dossiers témoins
 * ({@code src/test/resources/lotA/temoins}) produits par
 * {@code scripts/lotA/fixture_temoins.py}. Rien n'est écrit dans les ressources
 * de production : le corpus n'est pas encore intégré, et l'intégration attend
 * l'arbitrage de la phase 3.
 *
 * <p>Le moteur est appelé par {@code renderInternal}, seul point d'entrée qui
 * accepte un flux plutôt qu'un code du manifeste. La réflexion est assumée : le
 * corpus n'ayant pas d'entrée de manifeste, {@code generate(code, vars)} ne
 * saurait pas le résoudre.
 *
 * <p>Le relevé est écrit dans {@code output/lotA/temoins/} — c'est lui, pas le
 * vert du test, qui constitue le livrable de la phase 7.
 */
class CorpusCreation0909RenduTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marqueurs de balisage : aucun ne doit survivre au rendu. */
    private static final Map<String, Character> MARQUEURS = new LinkedHashMap<>() {{
        put("variable $", '$');
        put("boucle ouvrante", '▼');
        put("boucle fermante", '▲');
        put("condition ouvrante", '◇');
        put("condition fermante", '◆');
        put("case a cocher", '◈');
        put("annotation", '↳');
        put("bloc legacy ouvrant", '▶');
        put("bloc legacy fermant", '◀');
    }};

    private static final String[] CAS = {
            "sarl_pluripersonnelle", "sarl_au", "associe_personne_morale", "apport_en_nature"
    };

    private static final String[] GABARITS = {
            "ACTE_NOMINATION_GERANT", "ANNONCE_LEGALE_CONSTITUTION",
            "ATTESTATION_SOUSCRIPTION_LIBERATION", "BORDEREAU_REMISE_DOSSIER", "CONTRAT_BAIL",
            "CONTRAT_DOMICILIATION", "DECLARATION_BENEFICIAIRES_EFFECTIFS", "DECLARATION_CNDP",
            "DECLARATION_EXISTENCE", "DECLARATION_IMMATRICULATION_RC", "DEMANDE_ADHESION_SIMPL",
            "DEMANDE_AFFILIATION_CNSS", "DEMANDE_DEBLOCAGE_CAPITAL", "DEMANDE_TAXE_PROFESSIONNELLE",
            "ETAT_ACTES_SOCIETE_EN_FORMATION", "FICHE_RENSEIGNEMENTS_CREATION",
            "LETTRE_RETRAIT_DEPOT", "NOTE_ANNULATION_DOSSIER", "NOTE_CONFORMITE_MENTIONS_LEGALES",
            "POUVOIR_FORMALITES_CREATION", "RAPPORT_COMMISSAIRE_APPORTS", "STATUTS_SARL",
            "STATUTS_SARL_AU"
    };

    @Test
    void les_23_gabarits_rendus_sur_quatre_dossiers_temoins() throws Exception {
        Path sortie = Path.of(System.getProperty("lotA.sortie",
                "../../output/lotA/temoins")).toAbsolutePath().normalize();
        Files.createDirectories(sortie);

        DocxTemplateEngine engine = new DocxTemplateEngine();
        Method render = DocxTemplateEngine.class.getDeclaredMethod(
                "renderInternal", InputStream.class, Map.class, boolean.class, boolean.class);
        render.setAccessible(true);

        StringBuilder releve = new StringBuilder();
        releve.append("# Lot A — ce qui a été lu dans les 23 documents produits\n\n");

        for (String cas : CAS) {
            Map<String, Object> variables = fixture(cas);
            Path dossier = sortie.resolve(cas);
            Files.createDirectories(dossier);
            releve.append("\n## Cas témoin : ").append(cas).append("\n\n");
            releve.append("| Document | par. | marqueurs résiduels | blancs en phrase |")
                  .append(" cases cochées | cases non cochées |\n");
            releve.append("|---|---|---|---|---|---|\n");

            for (String code : GABARITS) {
                byte[] rendu;
                try (InputStream in = new ClassPathResource(
                        "lotA/gabarits/" + code + ".docx").getInputStream()) {
                    Object outcome = render.invoke(engine, in, variables,
                            code.startsWith("STATUTS_"), estImprime(code));
                    rendu = (byte[]) accesseur(outcome, "bytes").invoke(outcome);
                    Files.write(dossier.resolve(code + ".docx"), rendu);
                    releve.append(ligneReleve(code, rendu, outcome));
                }
            }
        }

        Path rapport = sortie.resolve("RELEVE_LECTURE.md");
        Files.writeString(rapport, releve.toString(), StandardCharsets.UTF_8);
        System.out.println("Relevé écrit : " + rapport);
        System.out.println(releve);
    }

    /**
     * Lot A — REPRISE DE LA GARANTIE DU LOT 5, sur le corpus qui l'a remplacé.
     *
     * <p>{@code CreationFormulairesRenderTest} tenait cette garantie sur les trois
     * imprimés du 4 septembre. Ces gabarits ont été remplacés par ceux du
     * 9 septembre : le test ne pouvait pas survivre à son sujet. Ce qu'il
     * protégeait, en revanche, doit survivre — c'est le contrôle de complétude
     * corrigé au lot 5, celui qui distingue le blanc <b>d'une case</b> (un imprimé
     * administratif reste recevable) du blanc <b>d'une phrase</b> (« né le  à ,
     * demeurant à  » : on refuse).
     *
     * <p>Le prompt du lot demandait explicitement de le vérifier sur les nouveaux
     * modèles plutôt que de le supposer : la structure du texte a changé. Elle a
     * bien changé, et pas dans le sens attendu — voir la remarque en fin de méthode.
     */
    @Test
    void le_blanc_dune_case_passe_celui_dune_phrase_bloque() throws Exception {
        DocxTemplateEngine engine = new DocxTemplateEngine();
        Method render = DocxTemplateEngine.class.getDeclaredMethod(
                "renderInternal", InputStream.class, Map.class, boolean.class, boolean.class);
        render.setAccessible(true);

        // ── Une CASE : « Domicile fiscal : $DOMICILE_FISCAL » finit la ligne ──
        Map<String, Object> imprime = fixture("sarl_pluripersonnelle");
        imprime.remove("DOMICILE_FISCAL");
        Object sortie;
        try (InputStream in = new ClassPathResource(
                "lotA/gabarits/DECLARATION_EXISTENCE.docx").getInputStream()) {
            sortie = render.invoke(engine, in, imprime, false, true);
        }
        byte[] rendu = (byte[]) accesseur(sortie, "bytes").invoke(sortie);
        assertEquals(0, bloquantes(sortie),
                "une case d'imprimé laissée blanche ne doit pas bloquer la génération");
        assertFalse(texte(rendu).contains("VALEUR MANQUANTE"),
                "aucune alarme rouge ne doit s'imprimer sur un imprimé administratif");

        // ── Une PHRASE : « Pièce d'identité : $TYPE n° $NUMERO » ─────────────
        // Deux champs sur la ligne, mais AUCUN séparateur : le second n'a pas de
        // libellé propre, il prolonge le premier. Le blanc se lit entre les deux.
        //
        // ⚠ L'exemple d'origine était « Téléphone : … — Télécopie : … », qui a
        // cessé d'être une phrase avec l'affinage du classement : les segments
        // séparés par « — » et libellés sont désormais des cases (cf.
        // PhraseOuCaseRecensementTest, 17 variables déplacées).
        Map<String, Object> troue = fixture("sarl_pluripersonnelle");
        troue.remove("DECLARANT_PIECE_TYPE");
        Object sortieTrouee;
        try (InputStream in = new ClassPathResource(
                "lotA/gabarits/DECLARATION_EXISTENCE.docx").getInputStream()) {
            sortieTrouee = render.invoke(engine, in, troue, false, true);
        }
        assertTrue(bloquantes(sortieTrouee) > 0,
                "un blanc au milieu d'un segment non libellé doit faire refuser");

        // ── Un acte : « de nationalité $GERANT_NATIONALITE, demeurant à … » ──
        Map<String, Object> acte = fixture("sarl_pluripersonnelle");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gerants = (List<Map<String, Object>>) acte.get("GERANTS");
        gerants.forEach(g -> g.remove("GERANT_NATIONALITE"));
        // Retirer la clé de la ligne ne suffit pas : le scope d'item retombe sur
        // le scope global, où le jeu témoin porte aussi la variable.
        acte.remove("GERANT_NATIONALITE");
        Object sortieActe;
        try (InputStream in = new ClassPathResource(
                "lotA/gabarits/ACTE_NOMINATION_GERANT.docx").getInputStream()) {
            sortieActe = render.invoke(engine, in, acte, false, false);
        }
        assertTrue(bloquantes(sortieActe) > 0,
                "un blanc au milieu d'une phrase doit faire refuser la génération");

        // ⚠ CE QUE CE TEST A APPRIS. Le corpus du 9 septembre GROUPE plusieurs
        // champs par ligne. La règle d'origine, qui ne regardait que la ligne
        // entière, en faisait des PHRASES : un dossier sans numéro de télécopie
        // était refusé. Le classement raisonne désormais par SEGMENT — 17
        // variables sont revenues au statut de case, aucune n'a durci. Restent
        // bloquants les segments SANS libellé propre, comme « n° $NUMERO » qui
        // prolonge « Pièce d'identité : $TYPE » : là, le blanc se lit vraiment.
    }

    private long bloquantes(Object outcome) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> detail = (List<Object>) accesseur(outcome, "detail").invoke(outcome);
        long n = 0;
        for (Object m : detail) {
            if (Boolean.TRUE.equals(accesseur(m, "bloquante").invoke(m))) n++;
        }
        return n;
    }

    /**
     * Liste du moteur (IMPRIMES_ADMINISTRATIFS) : trois entrées, figées au lot 5.
     * Le corpus du 9 septembre en compte bien davantage — c'est l'un des écarts
     * rapportés en phase 6.
     */
    private boolean estImprime(String code) {
        return code.equals("DEMANDE_TAXE_PROFESSIONNELLE")
                || code.equals("DECLARATION_EXISTENCE")
                || code.equals("DECLARATION_IMMATRICULATION_RC");
    }

    private String ligneReleve(String code, byte[] rendu, Object outcome) throws Exception {
        String texte = texte(rendu);
        List<String> residus = new ArrayList<>();
        for (Map.Entry<String, Character> e : MARQUEURS.entrySet()) {
            long n = texte.chars().filter(c -> c == e.getValue()).count();
            if (n > 0) residus.add(e.getKey() + " ×" + n);
        }
        @SuppressWarnings("unchecked")
        List<Object> detail = (List<Object>) accesseur(outcome, "detail").invoke(outcome);
        long bloquantes = 0;
        for (Object m : detail) {
            Boolean b = (Boolean) accesseur(m, "bloquante").invoke(m);
            if (Boolean.TRUE.equals(b)) bloquantes++;
        }
        long cochees = texte.chars().filter(c -> c == '☒').count();
        long vides = texte.chars().filter(c -> c == '☐').count();
        return String.format(Locale.ROOT, "| %s | %d | %s | %d | %d | %d |%n",
                code, texte.split("\n", -1).length,
                residus.isEmpty() ? "aucun" : String.join(", ", residus),
                bloquantes, cochees, vides);
    }

    /** {@code RenderOutcome} est un record prive : ses accesseurs se debrident. */
    private Method accesseur(Object cible, String nom) throws Exception {
        Method m = cible.getClass().getDeclaredMethod(nom);
        m.setAccessible(true);
        return m;
    }

    /** Texte du document rendu, corps et tableaux, un paragraphe par ligne. */
    private String texte(byte[] docx) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText() == null ? "" : p.getText()).append('\n');
            }
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow r : t.getRows()) {
                    for (XWPFTableCell c : r.getTableCells()) {
                        sb.append(c.getText() == null ? "" : c.getText()).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    private Map<String, Object> fixture(String cas) throws Exception {
        try (InputStream in = new ClassPathResource(
                "lotA/temoins/" + cas + ".json").getInputStream()) {
            Map<String, Object> brut = JSON.readValue(in, new TypeReference<>() {});
            return new TreeMap<>(brut);
        }
    }
}
