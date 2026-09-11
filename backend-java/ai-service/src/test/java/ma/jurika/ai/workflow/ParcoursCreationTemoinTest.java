package ma.jurika.ai.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.ControleCompletude;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.mapper.CreationChampsCatalogue;
import ma.jurika.ai.workflow.mapper.CreationSarlMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot B — LE PARCOURS TÉMOIN : on GÉNÈRE, puis on LIT ce qui sort.
 *
 * <p>C'est la leçon du lot A, et elle a coûté quatre défauts moteur : ils ont
 * tous été trouvés par lecture du document produit, aucun par un compteur. Une
 * génération qui ne lève pas d'exception ne dit rien du document.
 *
 * <p>Ce test mène donc un parcours complet — <b>SARL puis SARL AU</b> — sur les
 * 23 modèles du corpus, et vérifie sur le TEXTE RENDU :
 *
 * <ul>
 *   <li>qu'aucun marqueur de variable ne survit — ni {@code $NOM}, ni
 *       {@code ${NOM}}, ni {@code ‹ VALEUR MANQUANTE : … ›} ;</li>
 *   <li>qu'aucun marqueur de structure ne survit — les {@code ◇ SI},
 *       {@code ◆ FIN SI}, {@code ▼}, {@code ▲}, {@code ◈} du corpus ;</li>
 *   <li>que les valeurs saisies s'y trouvent réellement ;</li>
 *   <li>que les cases à cocher suivent la donnée.</li>
 * </ul>
 *
 * <p>Les documents produits sont écrits dans {@code output/lotB/temoin/} avec
 * leur texte extrait, pour être relus à la main — c'est ce que le lot demande de
 * rapporter, et un test ne remplace pas cette lecture.
 */
class ParcoursCreationTemoinTest {

    private static final Path SORTIE = Path.of("../../output/lotB/temoin");

    private final CreationSarlMapper mapper = new CreationSarlMapper();

    /**
     * Le moteur, câblé comme en PRODUCTION : manifeste + applicateur de
     * valeurs par défaut. Sans eux, toute variable non résolue sortirait en
     * rouge, y compris celles que le manifeste déclare et que le dossier ne
     * porte pas encore — et le témoin crierait au loup.
     *
     * <p>Avec eux, le rouge « VALEUR MANQUANTE » est réservé aux variables
     * HORS manifeste : un vrai défaut de gabarit, et rien d'autre.
     */
    private static DocxTemplateEngine engine;

    private static synchronized DocxTemplateEngine engine() {
        if (engine == null) {
            TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
            loader.load();
            engine = new DocxTemplateEngine(loader, new TemplateDefaultsApplier(loader));
        }
        return engine;
    }

    /** Marqueurs de variable qui ne doivent JAMAIS survivre au rendu. */
    private static final Pattern VARIABLE_RESIDUELLE =
            Pattern.compile("\\$\\{?[A-Z][A-Z0-9_]{3,}\\}?");
    /** Marqueurs de structure du corpus du 9 septembre. */
    private static final Pattern STRUCTURE_RESIDUELLE =
            Pattern.compile("[◇◆▼▲◈↳]");

    // =====================================================================
    //  Le dossier témoin
    // =====================================================================

    private static Map<String, Object> associe(String prenom, String nom, String parts,
                                                String apportNature) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("civilite", "M");
        a.put("prenom", prenom);
        a.put("nom", nom);
        a.put("type", "personne physique");
        a.put("nationalite", "marocaine");
        a.put("adresse", "14 rue Ibn Batouta, Casablanca");
        a.put("dateNaissance", "12/04/1988");
        a.put("lieuNaissance", "Fès");
        a.put("pieceType", "CIN");
        a.put("pieceNumero", "BK" + nom.hashCode());
        a.put("nombreParts", parts);
        a.put("apportNumeraire", apportNature == null ? parts + "00" : "0");
        if (apportNature != null) a.put("apportNature", apportNature);
        if (apportNature != null) a.put("apportNatureDescription", "Matériel informatique");
        return a;
    }

    private static Map<String, Object> payload(boolean associeUnique) {
        Map<String, Object> societe = new LinkedHashMap<>();
        societe.put("denomination", "PARACOSME");
        societe.put("sigle", "PCM");
        societe.put("formeJuridique", associeUnique ? "SARL_AU" : "SARL");
        societe.put("siegeSocial", "101 boulevard Zerktouni, Casablanca");
        societe.put("adresse", "101 boulevard Zerktouni");
        societe.put("ville", "Casablanca");
        societe.put("villeGreffe", "Casablanca");
        societe.put("capitalChiffres", "100000");
        societe.put("nombreParts", "1000");
        societe.put("valeurNominalePart", "100");
        societe.put("objetSocial", "le conseil en systèmes d'information");
        societe.put("dureeSociete", "99");
        societe.put("certificatNegatifNumero", "CN2026/114532");
        societe.put("certificatNegatifDate", "02/09/2026");
        // Le front envoie l'ICE sous `iceNumero` (Step7 : `iceNumero: den.ice`).
        // Le témoin doit parler exactement le même payload que la production,
        // sans quoi il teste un contrat qui n'existe pas.
        societe.put("iceNumero", "001234567000089");
        societe.put("identifiantFiscal", "40221188");
        societe.put("rcNumero", "445221");
        societe.put("telephone", "0522 99 11 22");
        societe.put("email", "contact@paracosme.ma");

        Map<String, Object> gerant = new LinkedHashMap<>();
        gerant.put("civilite", "M");
        gerant.put("prenom", "Yassine");
        gerant.put("nom", "BENANI");
        gerant.put("nationalite", "marocaine");
        gerant.put("adresse", "14 rue Ibn Batouta, Casablanca");
        gerant.put("dateNaissance", "12/04/1988");
        gerant.put("lieuNaissance", "Fès");
        gerant.put("pieceType", "CIN");
        gerant.put("pieceNumero", "BK188421");
        gerant.put("qualite", "Gérant");
        gerant.put("dureeMandat", "3 année(s)");

        Map<String, Object> dossier = new LinkedHashMap<>();
        dossier.put("numero", "D-2026-00841");
        dossier.put("dateOuverture", "2026-09-01");
        dossier.put("charge", "Salma IDRISSI");

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe);
        p.put("associes", associeUnique
                ? List.of(associe("Yassine", "BENANI", "1000", null))
                : List.of(associe("Yassine", "BENANI", "600", null),
                          associe("Nour", "ALAMI", "400", "40000")));
        p.put("gerants", List.of(gerant));
        p.put("dossier", dossier);
        p.put("valeurNominalePart", "100");
        p.put("creation", saisies());
        // Les valeurs des cases des imprimés DGI. Elles transitent par
        // `formulaires`, contrat établi au lot 5 — le moteur coche la case dont le
        // LIBELLÉ est exactement égal à la valeur, d'où des chaînes reprises mot
        // pour mot du modèle. C'est aussi la réserve du cabinet : ces libellés
        // doivent être confrontés aux imprimés officiels avant mise en production.
        p.put("formulaires", Map.of(
                "regimeResultat", "Impôt sur les sociétés — régime du résultat net réel",
                "activiteNature", "Prestation de services",
                "tvaAssujettissement", "Assujetti à titre obligatoire",
                "tvaFaitGenerateur", "Régime de l'encaissement",
                "tvaPeriodicite", "Déclaration mensuelle",
                "tpObjet", "Personne morale",
                "directionRegionale", "Direction régionale de Casablanca",
                "subdivision", "Subdivision Anfa",
                "telephone", "0522 99 11 22",
                "email", "contact@paracosme.ma"));
        return p;
    }

    /**
     * Les saisies du parcours — les 160 champs, renseignés comme un employé les
     * renseignerait. On ne remplit pas TOUT : c'est justement l'intérêt du témoin
     * de montrer ce qu'un dossier incomplet produit.
     */
    private static Map<String, Object> saisies() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("tpCommune", "Casablanca-Anfa");
        s.put("effectifPrevisionnel", "4");
        s.put("declarantPieceType", "CIN");
        s.put("declarantPieceNumero", "BK188421");
        s.put("siegeTitreOccupation", "Bail commercial");
        s.put("bailDateEffet", "2026-10-01");
        s.put("bailDuree", "3");
        s.put("bailLoyerChiffres", "8500");
        s.put("bailLoyerPeriodicite", "mensuelle");
        s.put("activiteReglementee", "non");
        s.put("cnssDatePremierSalarie", "2026-11-02");
        s.put("simplContactNom", "Salma IDRISSI");
        s.put("pouvoirMandataireNom", "Salma IDRISSI");
        s.put("clientSignataireNom", "Yassine BENANI");
        s.put("bordereauObjet", "remise");
        s.put("beneficiairesEffectifs", List.of(
                Map.of("beNom", "BENANI", "bePrenom", "Yassine", "beGenre", "Masculin",
                       "beNationalite", "marocaine", "beModeDetention", "Directe")));
        s.put("traitementsDonnees", List.of(
                Map.of("traitementDenomination", "Fichier clients",
                       "traitementFinalite", "gestion de la relation commerciale")));
        s.put("piecesRemises", List.of(
                Map.of("pieceDesignation", "Statuts enregistrés", "pieceForme", "original",
                       "pieceNombre", "3")));
        return s;
    }

    // =====================================================================

    @Test
    @DisplayName("Parcours complet SARL : les 23 modèles sortent, et on lit ce qui sort")
    void parcoursSarl() throws Exception {
        parcours("SARL", payload(false));
    }

    @Test
    @DisplayName("Parcours complet SARL AU : les 23 modèles sortent, et on lit ce qui sort")
    void parcoursSarlAu() throws Exception {
        parcours("SARL_AU", payload(true));
    }

    private void parcours(String forme, Map<String, Object> payload) throws Exception {
        Path dossier = SORTIE.resolve(forme);
        Files.createDirectories(dossier);

        List<String> anomalies = new ArrayList<>();
        Map<String, String> refuses = new LinkedHashMap<>();
        StringBuilder rapport = new StringBuilder();
        rapport.append("# Parcours témoin — ").append(forme).append("\n\n")
                .append("Généré par `ParcoursCreationTemoinTest`. Chaque section porte le TEXTE\n")
                .append("réellement extrait du `.docx` produit — pas ce que la génération renvoie.\n\n");

        int rendus = 0;
        for (String code : CreationChampsCatalogue.get().codes()) {
            // Chaque modèle n'existe que dans sa variante : générer les statuts de
            // SARL pour un associé unique n'aurait pas de sens.
            if (code.equals("STATUTS_SARL") && forme.equals("SARL_AU")) continue;
            if (code.equals("STATUTS_SARL_AU") && forme.equals("SARL")) continue;

            Map<String, Object> variables = mapper.map(code, payload);
            DocxTemplateEngine.DocumentResult resultat = engine().generate(code, variables);
            assertThat(resultat.templateFound())
                    .as("gabarit introuvable au classpath : %s", code)
                    .isTrue();
            rendus++;

            // On ÉCRIT d'abord, refus ou pas. Un document refusé se lit aussi —
            // c'est même le plus instructif : on y voit la phrase trouée, et c'est
            // par cette lecture que le lot A avait trouvé ses quatre défauts.
            Files.write(dossier.resolve(code + ".docx"), resultat.bytes());
            String texte = texteDe(resultat.bytes());
            Files.writeString(dossier.resolve(code + ".txt"), texte, StandardCharsets.UTF_8);

            // LE REFUS FAIT PARTIE DU RÉSULTAT ATTENDU. Un document dont une
            // variable manque AU MILIEU D'UNE PHRASE ne doit pas sortir : le
            // contrôleur le refuse, et nomme la ligne fautive. C'est le cas des
            // documents que les 55 variables sans source alimentent — le contrat
            // de bail attend son bailleur, et aucun écran ne le produit.
            String refus = ControleCompletude.motifDeRefus(resultat);
            if (refus != null) {
                refuses.put(code, refus);
                rapport.append("## ").append(code).append(" — **génération refusée**\n\n")
                        .append("La plateforme refuse de produire ce document en l'état :\n\n")
                        .append("> ").append(refus.replace("\n", " ")).append("\n\n");
                continue;
            }

            rapport.append("## ").append(code).append("\n\n");
            rapport.append("- taille : ").append(resultat.bytes().length).append(" octets, ")
                    .append(texte.length()).append(" caractères de texte\n");

            // --- Ce qui ne doit JAMAIS survivre ---------------------------
            for (String residu : residus(texte, VARIABLE_RESIDUELLE)) {
                anomalies.add(code + " : marqueur de variable résiduel « " + residu + " »");
            }
            for (String residu : residus(texte, STRUCTURE_RESIDUELLE)) {
                anomalies.add(code + " : marqueur de structure résiduel « " + residu + " »");
            }
            if (texte.contains("VALEUR MANQUANTE")) {
                anomalies.add(code + " : « VALEUR MANQUANTE » imprimé sur le document");
            }

            List<String> manquantes = resultat.missingVariables();
            rapport.append("- variables non résolues : ").append(manquantes.size());
            if (!manquantes.isEmpty()) {
                rapport.append(" — ").append(String.join(", ", manquantes.stream().limit(12)
                        .toList()));
                if (manquantes.size() > 12) rapport.append(", …");
            }
            rapport.append("\n\n");
            rapport.append("```\n").append(extrait(texte, 1200)).append("\n```\n\n");
        }

        Files.writeString(SORTIE.resolve("rapport-" + forme + ".md"), rapport.toString(),
                StandardCharsets.UTF_8);

        Files.writeString(SORTIE.resolve("refus-" + forme + ".md"),
                rapportDesRefus(forme, refuses), StandardCharsets.UTF_8);

        assertThat(rendus)
                .as("le corpus compte 23 modèles ; une variante de statuts par forme")
                .isEqualTo(22);
        assertThat(anomalies)
                .as("ce que le document produit porte encore : %s", anomalies)
                .isEmpty();
        // UN CORPUS OÙ TOUT SERAIT REFUSÉ passerait le test précédent sans rien
        // prouver : on exige donc que le dossier témoin en produise réellement.
        assertThat(rendus - refuses.size())
                .as("documents effectivement produits ; refusés : %s", refuses.keySet())
                .isGreaterThanOrEqualTo(8);

        // ET CHAQUE REFUS DOIT NOMMER CE QUI MANQUE. Un refus muet renverrait
        // l'employé chercher au hasard — c'est le défaut que le lot A avait
        // trouvé sur la déclaration d'existence.
        refuses.forEach((code, motif) -> {
            assertThat(motif).as("refus de %s", code)
                    .contains("$")
                    .contains("«");
        });
    }

    private static String rapportDesRefus(String forme, Map<String, String> refuses) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Générations refusées — ").append(forme).append("\n\n");
        if (refuses.isEmpty()) {
            sb.append("Aucune. Les 22 documents sortent.\n");
            return sb.toString();
        }
        sb.append("La plateforme REFUSE de produire ces documents en l'état, et nomme à\n")
                .append("chaque fois la variable et la ligne fautives. Ce n'est pas une panne :\n")
                .append("c'est le contrôle de complétude, qui distingue le blanc d'une case\n")
                .append("administrative — recevable — du blanc au milieu d'une phrase d'acte.\n\n")
                .append("La cause est presque toujours la même : une **variable sans source**.\n")
                .append("Le bailleur, le domiciliataire, le commissaire aux apports sont des\n")
                .append("TIERS ; les récépissés et références de dépôt viennent d'une\n")
                .append("ADMINISTRATION. Aucun écran ne les produit — voir\n")
                .append("`output/lotB/variables-sans-source.md`.\n\n");
        for (Map.Entry<String, String> e : refuses.entrySet()) {
            sb.append("## ").append(e.getKey()).append("\n\n> ")
                    .append(e.getValue().replace("\n", " ")).append("\n\n");
        }
        return sb.toString();
    }

    // =====================================================================

    @Test
    @DisplayName("Une valeur saisie au parcours se LIT dans le document produit")
    void laValeurSaisieSeLitDansLeDocument() throws Exception {
        Map<String, Object> variables =
                mapper.map("DEMANDE_TAXE_PROFESSIONNELLE", payload(false));
        String texte = texteDe(engine().generate("DEMANDE_TAXE_PROFESSIONNELLE", variables).bytes());

        assertThat(texte)
                .as("la commune saisie à l'étape 7 doit figurer sur l'imprimé")
                .contains("Casablanca-Anfa");
        assertThat(texte).contains("PARACOSME");
    }

    @Test
    @DisplayName("Une case à cocher suit la donnée : le genre du bénéficiaire effectif")
    void laCaseSuitLaDonnee() throws Exception {
        Map<String, Object> variables =
                mapper.map("DECLARATION_BENEFICIAIRES_EFFECTIFS", payload(false));
        String texte = texteDe(
                engine().generate("DECLARATION_BENEFICIAIRES_EFFECTIFS", variables).bytes());

        // Le moteur coche la case dont le libellé est EXACTEMENT égal à la valeur.
        // Une case inerte est exactement le défaut que le lot A avait trouvé — 23
        // blocs sur 27 ne cochaient plus.
        assertThat(texte)
                .as("la case cochée doit porter le marqueur ☒, pas ☐")
                .contains("☒");
        assertThat(texte).contains("BENANI");
    }

    @Test
    @DisplayName("Une boucle vide ne laisse ni section fantôme ni marqueur")
    void boucleVideNeLaissePasDeTrace() throws Exception {
        Map<String, Object> p = payload(false);
        // Aucun bénéficiaire effectif saisi : la boucle doit disparaître, pas
        // sortir une occurrence vide.
        Map<String, Object> saisies = new LinkedHashMap<>(saisies());
        saisies.remove("beneficiairesEffectifs");
        p.put("creation", saisies);

        String texte = texteDe(engine().generate("DECLARATION_BENEFICIAIRES_EFFECTIFS",
                mapper.map("DECLARATION_BENEFICIAIRES_EFFECTIFS", p)).bytes());

        assertThat(residus(texte, STRUCTURE_RESIDUELLE))
                .as("aucun marqueur de structure ne survit à une boucle vide")
                .isEmpty();
        // La SECTION de la boucle a disparu : plus d'occurrence fantôme.
        assertThat(texte)
                .as("une occurrence vide sortait avec « Bénéficiaire effectif n° ‹ VALEUR "
                        + "MANQUANTE : BE_NUMERO › » et un bloc de cases inerte")
                .doesNotContain("Bénéficiaire effectif n°")
                .doesNotContain("BE_NUMERO")
                .doesNotContain("Masculin");

        // Le reste du formulaire, lui, sort normalement — et ce qui manque
        // ailleurs (la pièce d'identité du déclarant) est signalé, comme il doit
        // l'être : c'est une phrase, pas une case.
        assertThat(texte).contains("PARACOSME");
    }

    // =====================================================================
    //  Lecture du document produit
    // =====================================================================

    /** Le texte du document : corps ET tableaux. Les formulaires sont des tableaux. */
    private static String texteDe(byte[] docx) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            sb.append(p.getText()).append('\t');
                        }
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static List<String> residus(String texte, Pattern motif) {
        List<String> out = new ArrayList<>();
        Matcher m = motif.matcher(texte);
        while (m.find()) {
            String trouve = m.group();
            // Les montants en dirhams s'écrivent parfois « 100 000 DH » : le motif
            // de variable ne peut pas les confondre (il exige un $), mais on garde
            // la garde explicite pour les sigles isolés du corpus.
            if (!out.contains(trouve)) out.add(trouve);
            if (out.size() >= 10) break;
        }
        return out;
    }

    private static String extrait(String texte, int taille) {
        String propre = texte.replaceAll("\n{3,}", "\n\n").trim();
        return propre.length() <= taille ? propre : propre.substring(0, taille) + "\n[…]";
    }
}
