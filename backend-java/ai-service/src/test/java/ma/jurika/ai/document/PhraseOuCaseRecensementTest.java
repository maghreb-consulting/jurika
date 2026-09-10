package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot A (2026-09-10) — LE RECENSEMENT DU CHANGEMENT DE CLASSEMENT PHRASE / CASE.
 *
 * <p>La règle d'origine ne regardait que la ligne entière. Le corpus livré le
 * 9 septembre groupe plusieurs champs par ligne, si bien qu'un dossier sans numéro
 * de télécopie voyait sa génération refusée — alors que la case peut légitimement
 * rester vide sur un imprimé.
 *
 * <p>Ce test ne se contente pas de vérifier la nouvelle règle : il MESURE ce
 * qu'elle déplace. Il rejoue l'ancienne et la nouvelle sur chaque occurrence de
 * variable des 23 gabarits, et écrit la liste des variables qui changent de
 * classement dans {@code output/lotA/RECENSEMENT_PHRASE_CASE.md}. Un assouplissement
 * qu'on ne sait pas chiffrer n'est pas un assouplissement, c'est un pari.
 */
class PhraseOuCaseRecensementTest {

    private static final char OUVRE = MissingVariableMarker.SENTINEL_OPEN;
    private static final char FERME = MissingVariableMarker.SENTINEL_CLOSE;

    private static final Pattern VARIABLE = Pattern.compile("\\$([A-Z][A-Z0-9_]*)");
    private static final Pattern SENTINEL = Pattern.compile(OUVRE + "([^" + FERME + "]+)" + FERME);

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

    /**
     * La règle telle qu'elle était avant le lot A : la LIGNE entière, sans égard
     * aux séparateurs de champs. Reproduite ici — et nulle part ailleurs — pour
     * pouvoir chiffrer l'écart.
     */
    private static boolean ancienneRegle(String full, int debut, int fin) {
        String apres = SENTINEL.matcher(full.substring(fin)).replaceAll("");
        if (!apres.isBlank()) return true;
        String avant = SENTINEL.matcher(full.substring(0, debut)).replaceAll("");
        avant = avant.replaceAll("[\\s\\u00A0\\u202F]+$", "");
        if (avant.isBlank()) return false;
        return !(avant.endsWith(":") || avant.endsWith("："));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Les cas qui ont motivé le changement, fixés un par un
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("champs groupés : chaque segment libellé redevient une case")
    void champs_groupes_sur_une_ligne() {
        String ligne = "Téléphone : $TELEPHONE — Télécopie : $FAX — Courriel : $EMAIL";
        assertFalse(estUnePhrase(ligne, "TELEPHONE"), "« Téléphone : … » porte son libellé");
        assertFalse(estUnePhrase(ligne, "FAX"), "« Télécopie : … » aussi");
        assertFalse(estUnePhrase(ligne, "EMAIL"), "« Courriel : … » aussi");

        assertTrue(ancienne(ligne, "TELEPHONE"), "l'ancienne règle bloquait le premier champ");
        assertTrue(ancienne(ligne, "FAX"), "et le deuxième");
    }

    @Test
    @DisplayName("un segment sans libellé propre laisse un séparateur en suspens : phrase")
    void segment_sans_libelle_reste_une_phrase() {
        assertTrue(estUnePhrase("Téléphone : $TELEPHONE — $FAX", "FAX"),
                "« Téléphone : 05… —  » se lit cassé");
    }

    @Test
    @DisplayName("du texte qui suit DANS le segment compte toujours")
    void texte_suivant_dans_le_segment() {
        String ligne = "Durée de la société : $DUREE_SOCIETE années — date d'expiration : $DATE_FIN";
        assertTrue(estUnePhrase(ligne, "DUREE_SOCIETE"), "« années » resterait suspendu");
        assertFalse(estUnePhrase(ligne, "DATE_FIN"), "le second champ finit sa ligne");
    }

    @Test
    @DisplayName("sans séparateur, le comportement d'avant est conservé à l'identique")
    void sans_separateur_rien_ne_change() {
        assertFalse(estUnePhrase("Ville : $SIEGE_VILLE", "SIEGE_VILLE"));
        assertTrue(estUnePhrase("né(e) le $DATE_NAISSANCE à $LIEU, demeurant à $ADRESSE", "LIEU"));
        assertTrue(estUnePhrase("Pièce d'identité : $TYPE n° $NUMERO", "TYPE"));
        assertFalse(estUnePhrase("$DENOMINATION", "DENOMINATION"), "une variable seule sur sa ligne");
    }

    @Test
    @DisplayName("le tiret collé à un mot ne sépare rien")
    void tiret_colle_nest_pas_un_separateur() {
        assertTrue(estUnePhrase("Objet : $OBJET—suite du texte", "OBJET"));
    }

    @Test
    @DisplayName("point médian et barre verticale séparent aussi")
    void autres_separateurs() {
        assertFalse(estUnePhrase("Téléphone : $TEL · Courriel : $MAIL", "TEL"));
        assertFalse(estUnePhrase("Téléphone : $TEL | Courriel : $MAIL", "TEL"));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Le recensement sur le corpus réel
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recensement : quelles variables des 23 gabarits changent de classement")
    void recensement_sur_les_23_gabarits() throws Exception {
        // variable -> (gabarit, ligne) de la première occurrence qui bascule
        Map<String, String> basculeVersCase = new TreeMap<>();
        Set<String> resteBloquanteAilleurs = new LinkedHashSet<>();
        Map<String, Integer> parGabarit = new LinkedHashMap<>();
        int occurrences = 0;
        int bascules = 0;

        for (String code : GABARITS) {
            int n = 0;
            for (String ligne : lignes(code)) {
                Matcher m = VARIABLE.matcher(ligne);
                while (m.find()) {
                    occurrences++;
                    String nom = m.group(1);
                    String avecSentinel = ligne.substring(0, m.start()) + OUVRE + nom + FERME
                            + ligne.substring(m.end());
                    int debut = m.start();
                    int fin = debut + nom.length() + 2;

                    boolean ancien = ancienneRegle(avecSentinel, debut, fin);
                    boolean nouveau = MissingVariableMarker.dansUnePhrase(avecSentinel, debut, fin);

                    assertFalse(!ancien && nouveau,
                            "la règle ne doit jamais durcir : $" + nom + " dans « " + ligne + " »");

                    if (ancien && !nouveau) {
                        bascules++;
                        n++;
                        basculeVersCase.putIfAbsent(nom, code + " — « " + ligne.trim() + " »");
                    } else if (ancien) {
                        resteBloquanteAilleurs.add(nom);
                    }
                }
            }
            if (n > 0) parGabarit.put(code, n);
        }

        StringBuilder md = new StringBuilder();
        md.append("# Lot A — recensement du changement de classement phrase / case\n\n");
        md.append("Règle affinée : un segment délimité par un séparateur de champs (`—`, `·`, `|`)\n");
        md.append("et portant son propre libellé reste une **case**, même s'il partage sa ligne.\n\n");
        md.append("| Mesure | Valeur |\n|---|---:|\n");
        md.append("| Occurrences de variable examinées | ").append(occurrences).append(" |\n");
        md.append("| Occurrences qui basculent en case | ").append(bascules).append(" |\n");
        md.append("| **Variables distinctes qui basculent** | **")
          .append(basculeVersCase.size()).append("** |\n");
        md.append("| Variables durcies par la nouvelle règle | 0 |\n\n");

        md.append("## Par gabarit\n\n| Gabarit | occurrences basculées |\n|---|---:|\n");
        parGabarit.forEach((g, v) -> md.append("| ").append(g).append(" | ").append(v).append(" |\n"));

        md.append("\n## Les variables qui passent de « phrase » à « case »\n\n");
        md.append("| Variable | Où, et pourquoi c'est une case |\n|---|---|\n");
        basculeVersCase.forEach((nom, ou) ->
                md.append("| `$").append(nom).append("` | ").append(ou.replace('|', '/')).append(" |\n"));

        List<String> mixtes = basculeVersCase.keySet().stream()
                .filter(resteBloquanteAilleurs::contains).sorted().toList();
        md.append("\n## Variables qui basculent ici et restent bloquantes ailleurs (")
          .append(mixtes.size()).append(")\n\n");
        md.append("Le classement est **par occurrence**, jamais par nom : la même variable peut être\n");
        md.append("une case sur un imprimé et une phrase dans un acte. C'est voulu.\n\n");
        for (String nom : mixtes) md.append("- `$").append(nom).append("`\n");

        md.append(releveDesMarqueurs());

        Path sortie = Path.of(System.getProperty("lotA.sortie", "../../output/lotA"))
                .toAbsolutePath().normalize();
        Files.createDirectories(sortie);
        Path fichier = sortie.resolve("RECENSEMENT_PHRASE_CASE.md");
        Files.writeString(fichier, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Recensement écrit : " + fichier);
        System.out.println(md);

        assertTrue(bascules > 0, "l'affinage doit déplacer quelque chose, sinon il ne sert à rien");
        assertEquals(0, basculeVersCase.keySet().stream()
                        .filter(n -> n.isBlank()).count(),
                "aucun nom de variable vide");
    }

    // ─────────────────────────────────────────────────────────────────
    //  La règle du lot 2 sur les imprimés administratifs, tenue de bout en bout
    // ─────────────────────────────────────────────────────────────────

    /** Les trois imprimés que {@code DocxTemplateEngine.IMPRIMES_ADMINISTRATIFS} déclare. */
    private static final String[] IMPRIMES = {
            "DECLARATION_EXISTENCE", "DEMANDE_TAXE_PROFESSIONNELLE", "DECLARATION_IMMATRICULATION_RC"
    };

    /**
     * L'invariant du lot 2 : sur un imprimé administratif, une CASE non renseignée
     * sort <b>blanche</b>. Crier « VALEUR MANQUANTE » en rouge sur un formulaire
     * remis à la DGI contredirait la règle qui l'a laissé passer.
     *
     * <p>Le test rend les trois imprimés avec un dossier VIDE — donc toutes les
     * variables manquantes — et vérifie que chaque marqueur rouge imprimé
     * correspond bien à une occurrence de type <b>phrase</b>. Une seule case qui
     * s'imprimerait en rouge fait échouer, en la nommant.
     *
     * <p>Le sens inverse n'est pas asserté ici : avec un dossier vide, les branches
     * conditionnelles tombent et les boucles sont vides, si bien que beaucoup de
     * variables disparaissent du rendu sans être manquantes. La direction testée
     * est celle qui protège le document.
     */
    @Test
    @DisplayName("imprimés DGI : aucun marqueur rouge sur une case, seulement sur des phrases")
    void aucune_case_ne_simprime_en_rouge_sur_un_imprime() throws Exception {
        Map<String, Set<String>> fautives = new LinkedHashMap<>();

        for (String code : IMPRIMES) {
            byte[] rendu = rendre(code, Map.of());
            Set<String> enRouge = variablesEnRouge(rendu);

            Set<String> cases = new LinkedHashSet<>();
            for (String nom : enRouge) {
                if (!auMoinsUneOccurrencePhrase(code, nom)) cases.add(nom);
            }
            if (!cases.isEmpty()) fautives.put(code, cases);

            assertFalse(enRouge.isEmpty(),
                    code + " : un dossier vide doit bien produire des manquantes de type phrase");
        }

        assertTrue(fautives.isEmpty(),
                "des CASES s'impriment en rouge sur un imprimé administratif — "
                        + "la règle du lot 2 a régressé : " + fautives);
    }

    /**
     * Le relevé nominatif : quel marqueur rouge s'imprime sur quel imprimé, sur
     * quelle ligne, et pourquoi cette occurrence est une phrase. C'est ce qu'on
     * relit avant de commiter, plutôt que de faire confiance à un booléen.
     */
    private String releveDesMarqueurs() throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("\n\n## Les marqueurs rouges des trois imprimés DGI, un par un\n\n");
        md.append("Rendu avec un dossier **vide** — donc toutes les variables manquantes. ");
        md.append("Chaque marqueur imprimé est confronté au classement du moteur.\n\n");
        md.append("| Imprimé | Variable | Ligne du gabarit | Classement | Marqueur |\n");
        md.append("|---|---|---|---|---|\n");

        for (String code : IMPRIMES) {
            for (String nom : variablesEnRouge(rendre(code, Map.of()))) {
                for (String ligne : lignes(code)) {
                    Matcher m = VARIABLE.matcher(ligne);
                    boolean trouve = false;
                    while (m.find()) {
                        if (!m.group(1).equals(nom)) continue;
                        String avec = ligne.substring(0, m.start()) + OUVRE + nom + FERME
                                + ligne.substring(m.end());
                        boolean phrase = MissingVariableMarker.dansUnePhrase(
                                avec, m.start(), m.start() + nom.length() + 2);
                        md.append("| ").append(code).append(" | `$").append(nom)
                          .append("` | ").append(ligne.trim().replace('|', '/'))
                          .append(" | **").append(phrase ? "phrase" : "CASE")
                          .append("** | ").append(phrase ? "légitime" : "**RÉGRESSION**")
                          .append(" |\n");
                        trouve = true;
                        break;
                    }
                    if (trouve) break;
                }
            }
        }

        return md.toString();
    }

    /**
     * Les six variables qui s'impriment en rouge sur les échantillons versionnés
     * de {@code docs/v2/sample_outputs/}, nommément.
     *
     * <p>Ce sont elles qu'on relit avant de commiter : chacune doit être une
     * PHRASE, sans quoi le marqueur rouge n'a rien à faire sur un imprimé
     * administratif. Le classement est demandé au moteur, pas déduit à la lecture.
     */
    @Test
    @DisplayName("les six marqueurs des échantillons DGI sont tous de type phrase")
    void les_six_marqueurs_des_echantillons_sont_des_phrases() throws Exception {
        Map<String, String> attendus = new LinkedHashMap<>();
        attendus.put("CERTIFICAT_NEGATIF_NUMERO",
                "Certificat négatif n° $CERTIFICAT_NEGATIF_NUMERO délivré le $CERTIFICAT_NEGATIF_DATE");
        attendus.put("CERTIFICAT_NEGATIF_DATE",
                "Certificat négatif n° $CERTIFICAT_NEGATIF_NUMERO délivré le $CERTIFICAT_NEGATIF_DATE");
        attendus.put("GERANT_LIEU_NAISSANCE",
                "Né(e) le $GERANT_DATE_NAISSANCE à $GERANT_LIEU_NAISSANCE");
        attendus.put("DECLARANT_PIECE_TYPE",
                "Pièce d'identité : $DECLARANT_PIECE_TYPE n° $DECLARANT_PIECE_NUMERO");
        attendus.put("DECLARANT_PIECE_NUMERO",
                "Pièce d'identité : $DECLARANT_PIECE_TYPE n° $DECLARANT_PIECE_NUMERO");
        attendus.put("TRIBUNAL_VILLE",
                "Greffe du registre du commerce de $TRIBUNAL_VILLE");

        List<String> corpus = new ArrayList<>();
        for (String code : IMPRIMES) corpus.addAll(lignes(code));

        for (Map.Entry<String, String> e : attendus.entrySet()) {
            assertTrue(corpus.stream().anyMatch(l -> l.trim().equals(e.getValue())),
                    "la ligne attendue a bougé dans le gabarit — à relire : " + e.getValue());
            assertTrue(estUnePhrase(e.getValue(), e.getKey()),
                    "$" + e.getKey() + " devrait être une PHRASE dans « " + e.getValue()
                            + " » ; classée CASE, son marqueur rouge est une régression");
        }
    }

    /**
     * L'autre moitié de la règle : la case sort blanche, mais la variable reste
     * <b>remontée</b> — c'est elle qui alimente l'en-tête {@code X-Missing-Variables}.
     * Une case silencieuse ET invisible serait pire que le marqueur rouge.
     */
    @Test
    @DisplayName("une case laissée blanche reste remontée dans les variables manquantes")
    void la_case_blanche_reste_remontee() throws Exception {
        // « Domicile fiscal : $DOMICILE_FISCAL » finit sa ligne : c'est une case.
        Map<String, Object> dossier = new LinkedHashMap<>();
        dossier.put("DENOMINATION", "ATLAS NEGOCE");

        DocxTemplateEngine engine = new DocxTemplateEngine();
        Object outcome = renderInternal(engine, "DECLARATION_EXISTENCE", dossier);
        byte[] rendu = (byte[]) accesseur(outcome, "bytes").invoke(outcome);

        @SuppressWarnings("unchecked")
        List<String> manquantes = (List<String>) accesseur(outcome, "missingVariables").invoke(outcome);

        assertTrue(manquantes.contains("DOMICILE_FISCAL"),
                "la case blanche doit rester dans missingVariables (X-Missing-Variables) : "
                        + manquantes);
        assertFalse(variablesEnRouge(rendu).contains("DOMICILE_FISCAL"),
                "et ne doit PAS s'imprimer en rouge sur un imprimé administratif");
    }

    /** Vrai si AU MOINS une occurrence de la variable dans ce gabarit est une phrase. */
    private boolean auMoinsUneOccurrencePhrase(String code, String variable) throws Exception {
        for (String ligne : lignes(code)) {
            Matcher m = VARIABLE.matcher(ligne);
            while (m.find()) {
                if (!m.group(1).equals(variable)) continue;
                String avecSentinel = ligne.substring(0, m.start()) + OUVRE + variable + FERME
                        + ligne.substring(m.end());
                if (MissingVariableMarker.dansUnePhrase(
                        avecSentinel, m.start(), m.start() + variable.length() + 2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Noms lus dans les « ‹ VALEUR MANQUANTE : NOM › » réellement imprimés. */
    private Set<String> variablesEnRouge(byte[] docx) throws Exception {
        Set<String> noms = new LinkedHashSet<>();
        Matcher m = Pattern.compile("VALEUR MANQUANTE : ([A-Z][A-Z0-9_]*)").matcher(texte(docx));
        while (m.find()) noms.add(m.group(1));
        return noms;
    }

    private byte[] rendre(String code, Map<String, Object> vars) throws Exception {
        Object outcome = renderInternal(new DocxTemplateEngine(), code, vars);
        return (byte[]) accesseur(outcome, "bytes").invoke(outcome);
    }

    /** Rend le gabarit EN IMPRIMÉ ADMINISTRATIF — c'est tout l'objet du test. */
    private Object renderInternal(DocxTemplateEngine engine, String code, Map<String, Object> vars)
            throws Exception {
        java.lang.reflect.Method render = DocxTemplateEngine.class.getDeclaredMethod(
                "renderInternal", java.io.InputStream.class, Map.class, boolean.class, boolean.class);
        render.setAccessible(true);
        try (InputStream in = new ClassPathResource("lotA/gabarits/" + code + ".docx").getInputStream()) {
            return render.invoke(engine, in, vars, false, true);
        }
    }

    private java.lang.reflect.Method accesseur(Object cible, String nom) throws Exception {
        java.lang.reflect.Method m = cible.getClass().getDeclaredMethod(nom);
        m.setAccessible(true);
        return m;
    }

    private String texte(byte[] docx) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(docx))) {
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

    // ─────────────────────────────────────────────────────────────────

    private boolean estUnePhrase(String ligne, String variable) {
        int[] bornes = poser(ligne, variable);
        return MissingVariableMarker.dansUnePhrase(sentinelle(ligne, variable), bornes[0], bornes[1]);
    }

    private boolean ancienne(String ligne, String variable) {
        int[] bornes = poser(ligne, variable);
        return ancienneRegle(sentinelle(ligne, variable), bornes[0], bornes[1]);
    }

    private String sentinelle(String ligne, String variable) {
        return ligne.replace("$" + variable, OUVRE + variable + FERME);
    }

    private int[] poser(String ligne, String variable) {
        int debut = ligne.indexOf("$" + variable);
        if (debut < 0) throw new IllegalArgumentException("$" + variable + " absent de « " + ligne + " »");
        return new int[]{debut, debut + variable.length() + 2};
    }

    /** Corps + cellules de tableau, un paragraphe par entrée. */
    private List<String> lignes(String code) throws Exception {
        List<String> out = new ArrayList<>();
        try (InputStream in = new ClassPathResource("lotA/gabarits/" + code + ".docx").getInputStream();
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (p.getText() != null && !p.getText().isBlank()) out.add(p.getText());
            }
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow r : t.getRows()) {
                    for (XWPFTableCell c : r.getTableCells()) {
                        for (XWPFParagraph p : c.getParagraphs()) {
                            if (p.getText() != null && !p.getText().isBlank()) out.add(p.getText());
                        }
                    }
                }
            }
        }
        return out;
    }
}
