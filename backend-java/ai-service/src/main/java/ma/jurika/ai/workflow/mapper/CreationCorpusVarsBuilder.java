package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.format.FrenchNumberToLetters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot B (2026-09-11) — LES VARIABLES DU CORPUS DU 9 SEPTEMBRE QUE LA PLATEFORME
 * NE RÉSOLVAIT PAS.
 *
 * <p>Le lot A a établi l'écart : sur les 404 variables des 23 gabarits,
 * <b>149 sont déjà résolues</b> par {@link CreationDirecteurVarsBuilder} et
 * {@link CreationFormulairesVarsBuilder}, <b>37 sont dérivables</b>,
 * <b>160 sont à saisir</b> et <b>55 n'ont aucune source</b>.
 *
 * <p>Ce producteur couvre les deux catégories du milieu, et elles seulement :
 *
 * <ul>
 *   <li><b>Les saisies</b> — reprises telles quelles de {@code payload.creation},
 *       d'après le catalogue généré. Aucun nom n'est écrit ici : le catalogue dit
 *       quelle clé de payload alimente quelle variable, et pour quel document.</li>
 *   <li><b>Les dérivations</b> — numérotation des occurrences d'une boucle,
 *       totaux, montants en toutes lettres, échéance d'un bail, souscriptions
 *       déduites des associés. Ce sont des <b>calculs</b>, pas des valeurs par
 *       défaut : une donnée absente ne produit rien.</li>
 * </ul>
 *
 * <p><b>Ce qui n'est jamais fait ici.</b> Les 55 variables sans source ne
 * reçoivent aucune valeur — pas même une chaîne vide « pour que ça passe ». Une
 * variable non résolue disparaît du rendu ou remonte au contrôle de complétude,
 * et c'est exactement ce que le lot veut : que le cabinet voie ce qui manque
 * plutôt qu'un blanc silencieux.
 *
 * <p><b>Les trois arbitrages de nommage restés ouverts</b> ({@code $SIEGE_VILLE}
 * contre {@code $VILLE}, {@code $SIGNATAIRE_NOM_QUALITE} contre
 * {@code $FORMULAIRE_SIGNATAIRE}) ne sont pas tranchés : l'alignement se fait
 * dans la <b>résolution</b>, jamais dans le {@code .docx}, et sous les noms en
 * vigueur.
 */
public final class CreationCorpusVarsBuilder {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private CreationCorpusVarsBuilder() {}

    /**
     * @param templateCode le modèle demandé, qui décide des champs à lire
     * @param payload      le payload du workflow ; les saisies du parcours vivent
     *                     sous la clé {@code creation}
     * @param base         les variables déjà produites par les builders existants
     *                     — lues, jamais écrasées : ce qui est déjà résolu l'est
     * @return les seules variables ajoutées, prêtes à être fusionnées
     */
    public static Map<String, Object> build(String templateCode,
                                            Map<String, Object> payload,
                                            Map<String, Object> base) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> saisies = asMap(safe.get("creation"));
        CreationChampsCatalogue.Catalogue cat = CreationChampsCatalogue.get();

        Map<String, Object> v = new LinkedHashMap<>();

        // ---- 1. Les saisies simples -----------------------------------------
        //
        // Une clé absente ou vide n'écrit RIEN : la variable reste non résolue et
        // c'est le contrôle de complétude, sur le document rendu, qui dira si ce
        // blanc est recevable (une case d'imprimé) ou non (le milieu d'une phrase).
        for (CreationChampsCatalogue.Champ c : cat.champsDe(templateCode)) {
            if (c.boucle() != null) continue;
            String valeur = str(saisies.get(c.cle()));
            if (!valeur.isBlank()) v.put(c.variable(), valeur);
        }

        // ---- 2. Les boucles --------------------------------------------------
        Map<String, List<CreationChampsCatalogue.Champ>> parBoucle = cat.champsParBoucle();
        for (CreationChampsCatalogue.Boucle b : cat.bouclesDe(templateCode)) {
            List<Map<String, Object>> saisie = asListOfMaps(saisies.get(cleDeBoucle(b.nom())));
            if (saisie.isEmpty()) {
                // UNE LISTE VIDE, PAS RIEN.
                //
                // Le moteur traite les deux cas differemment : une liste VIDE fait
                // disparaitre le corps de la boucle ; une liste ABSENTE ne retire
                // que les delimiteurs et CONSERVE le corps — une occurrence
                // fantome, avec ses variables non resolues et ses cases inertes.
                //
                // Constate sur le document produit : la declaration des
                // beneficiaires effectifs d'un dossier qui n'en declare aucun
                // sortait avec « Beneficiaire effectif n° ‹ VALEUR MANQUANTE :
                // BE_NUMERO › » et un bloc « ☐ Masculin ☐ Feminin » vide.
                v.put(b.nom(), List.of());
                continue;
            }
            List<Map<String, Object>> items = new ArrayList<>(saisie.size());
            List<CreationChampsCatalogue.Champ> champs =
                    parBoucle.getOrDefault(b.nom(), List.of());
            int index = 0;
            for (Map<String, Object> brut : saisie) {
                index++;
                Map<String, Object> item = new LinkedHashMap<>();
                for (CreationChampsCatalogue.Champ c : champs) {
                    String valeur = str(brut.get(c.cle()));
                    if (!valeur.isBlank()) item.put(c.variable(), valeur);
                }
                // DÉRIVATION — la numérotation d'une occurrence ne se saisit pas :
                // « bénéficiaire n° 2 » est le rang dans la liste, rien d'autre.
                // Demander ce numéro à l'employé serait lui demander de compter.
                String numero = numeroDeBoucle(b.nom());
                if (numero != null) item.put(numero, String.valueOf(index));
                items.add(item);
            }
            v.put(b.nom(), items);
            // Le compte d'occurrences, quand le corpus le réclame.
            String compte = compteDeBoucle(b.nom());
            if (compte != null) v.put(compte, String.valueOf(items.size()));
        }

        // ---- 3. Les dérivations ---------------------------------------------
        deriverDossier(v, safe);
        deriverSouscriptions(v, safe, base);
        deriverApportsNature(v, safe, base);
        deriverBail(v, saisies);
        deriverDomiciliation(v, saisies);
        deriverActesEnFormation(v);
        deriverPiecesRemises(v, safe);
        deriverDemarchesInterrompues(v, safe);
        deriverBeneficiaires(v, base);
        alignerFormeJuridiqueSurLImprime(templateCode, v, base);

        return v;
    }

    // =====================================================================
    //  Dérivations — des CALCULS, jamais des valeurs par défaut
    // =====================================================================

    /**
     * Identification du dossier. Ces quatre variables ne sont pas saisies au
     * parcours : le ticket les porte déjà. Les redemander contreviendrait à la
     * règle « ne jamais demander une donnée déjà en base ».
     */
    private static void deriverDossier(Map<String, Object> v, Map<String, Object> payload) {
        Map<String, Object> dossier = asMap(payload.get("dossier"));
        Map<String, Object> ticket = asMap(payload.get("ticket"));
        put(v, "DOSSIER_NUMERO", first(str(dossier.get("numero")), str(ticket.get("reference"))));
        put(v, "DOSSIER_DATE_OUVERTURE",
                dateFr(first(str(dossier.get("dateOuverture")), str(ticket.get("createdAt")))));
        put(v, "DOSSIER_CHARGE",
                first(str(dossier.get("charge")), str(ticket.get("responsableNom"))));
        put(v, "DOSSIER_DATE_ANNULATION", dateFr(str(ticket.get("dateAnnulation"))));
        // Le statut atteint au moment de l'annulation : la note d'annulation doit
        // dire jusqu'où le dossier était allé, et le ticket le sait.
        put(v, "ANNULATION_STATUT_ATTEINT", str(ticket.get("statutAvantAnnulation")));
    }

    /**
     * La boucle {@code SOUSCRIPTIONS} de la déclaration de souscription et de
     * versement. Elle ne se saisit pas : un souscripteur EST un associé, et son
     * montant souscrit est déjà le produit de ses parts par la valeur nominale.
     * Demander à nouveau ces chiffres, c'est demander à l'employé de recopier une
     * addition que la plateforme sait faire — et d'y introduire une erreur.
     */
    private static void deriverSouscriptions(Map<String, Object> v,
                                             Map<String, Object> payload,
                                             Map<String, Object> base) {
        List<Map<String, Object>> associes = associes(payload);
        if (associes.isEmpty()) return;

        BigDecimal valeurNominale = decimal(base.get("VALEUR_NOMINALE_PART"));
        if (valeurNominale.signum() <= 0) valeurNominale = decimal(payload.get("valeurNominalePart"));

        List<Map<String, Object>> items = new ArrayList<>(associes.size());
        BigDecimal totalSouscrit = BigDecimal.ZERO;
        BigDecimal totalVerse = BigDecimal.ZERO;

        for (Map<String, Object> a : associes) {
            BigDecimal parts = decimal(first(str(a.get("nombreParts")), str(a.get("parts"))));
            BigDecimal souscrit = decimal(str(a.get("montantSouscrit")));
            if (souscrit.signum() <= 0 && parts.signum() > 0 && valeurNominale.signum() > 0) {
                souscrit = parts.multiply(valeurNominale);
            }
            BigDecimal verse = decimal(first(str(a.get("montantVerse")), str(a.get("montantLibere"))));
            if (verse.signum() <= 0) verse = souscrit;   // libération intégrale, cas nominal
            BigDecimal solde = souscrit.subtract(verse).max(BigDecimal.ZERO);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("SOUSCRIPTEUR_LIBELLE", libelleAssocie(a));
            if (parts.signum() > 0) item.put("SOUSCRIPTEUR_PARTS_SOUSCRITES", entier(parts));
            if (souscrit.signum() > 0) item.put("SOUSCRIPTEUR_MONTANT_SOUSCRIT", entier(souscrit));
            if (verse.signum() > 0) item.put("SOUSCRIPTEUR_MONTANT_VERSE", entier(verse));
            item.put("SOUSCRIPTEUR_SOLDE_A_LIBERER", entier(solde));
            items.add(item);

            totalSouscrit = totalSouscrit.add(souscrit);
            totalVerse = totalVerse.add(verse);
        }

        v.put("SOUSCRIPTIONS", items);
        if (totalSouscrit.signum() > 0) {
            put(v, "SOUSCRIPTIONS_TOTAL_SOUSCRIT", entier(totalSouscrit));
        }
        if (totalVerse.signum() > 0) {
            put(v, "SOUSCRIPTIONS_TOTAL_VERSE", entier(totalVerse));
            put(v, "SOUSCRIPTIONS_TOTAL_VERSE_LETTRES", enLettres(totalVerse));
        }
        put(v, "SOUSCRIPTIONS_SOLDE_TOTAL",
                entier(totalSouscrit.subtract(totalVerse).max(BigDecimal.ZERO)));
    }

    /**
     * La boucle {@code APPORTS_NATURE} du rapport du commissaire aux apports.
     * L'apporteur et les parts attribuées se lisent dans les apports déjà saisis à
     * l'étape 3 ; la méthode d'évaluation, l'origine de propriété et les charges,
     * elles, viennent du commissaire — elles sont « sans source » et restent vides.
     */
    private static void deriverApportsNature(Map<String, Object> v,
                                             Map<String, Object> payload,
                                             Map<String, Object> base) {
        List<Map<String, Object>> associes = associes(payload);
        BigDecimal valeurNominale = decimal(base.get("VALEUR_NOMINALE_PART"));

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal partsTotal = BigDecimal.ZERO;

        for (Map<String, Object> a : associes) {
            BigDecimal valeur = decimal(first(str(a.get("apportNature")),
                                              str(a.get("apportNatureValeur"))));
            if (valeur.signum() <= 0) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("APPORT_NATURE_APPORTEUR", libelleAssocie(a));
            String description = first(str(a.get("apportNatureDescription")));
            if (!description.isBlank()) item.put("APPORT_NATURE_DESCRIPTION", description);
            item.put("APPORT_NATURE_VALEUR_CHIFFRES", entier(valeur));
            item.put("APPORT_NATURE_VALEUR_LETTRES", enLettres(valeur));
            if (valeurNominale.signum() > 0) {
                BigDecimal parts = valeur.divide(valeurNominale, 0, RoundingMode.DOWN);
                item.put("APPORT_NATURE_PARTS_ATTRIBUEES", entier(parts));
                partsTotal = partsTotal.add(parts);
            }
            items.add(item);
            total = total.add(valeur);
        }
        if (items.isEmpty()) return;

        v.put("APPORTS_NATURE", items);
        put(v, "APPORTS_NATURE_TOTAL_CHIFFRES", entier(total));
        put(v, "APPORTS_NATURE_TOTAL_LETTRES", enLettres(total));
        if (partsTotal.signum() > 0) put(v, "APPORTS_NATURE_PARTS_TOTAL", entier(partsTotal));
    }

    /**
     * Le bail : l'échéance se calcule, elle ne se saisit pas. La durée est en
     * ANNÉES et l'addition se fait en années calendaires — la même règle qu'au
     * lot 1 pour les mois : 29/02 + 1 an = 28/02, jamais 01/03.
     */
    private static void deriverBail(Map<String, Object> v, Map<String, Object> saisies) {
        LocalDate effet = date(str(saisies.get("bailDateEffet")));
        BigDecimal duree = decimal(str(saisies.get("bailDuree")));
        if (effet != null && duree.signum() > 0) {
            put(v, "BAIL_DATE_FIN", DATE_FR.format(effet.plusYears(duree.longValue())));
        }
        BigDecimal loyer = decimal(str(saisies.get("bailLoyerChiffres")));
        if (loyer.signum() > 0) put(v, "BAIL_LOYER_LETTRES", enLettres(loyer));
    }

    private static void deriverDomiciliation(Map<String, Object> v, Map<String, Object> saisies) {
        BigDecimal redevance = decimal(str(saisies.get("domiciliationRedevanceChiffres")));
        if (redevance.signum() > 0) {
            put(v, "DOMICILIATION_REDEVANCE_LETTRES", enLettres(redevance));
        }
    }

    /** Le total des engagements repris est la somme de la boucle, pas une saisie. */
    private static void deriverActesEnFormation(Map<String, Object> v) {
        List<Map<String, Object>> items = asListOfMaps(v.get("ACTES_EN_FORMATION"));
        if (items.isEmpty()) return;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            total = total.add(decimal(str(item.get("ACTE_FORMATION_MONTANT"))));
        }
        if (total.signum() > 0) put(v, "ACTES_EN_FORMATION_TOTAL", entier(total));
    }

    /**
     * Le bordereau. Le nombre total de pièces est la somme des quantités de la
     * boucle ; les pièces manquantes viennent du récapitulatif de clôture, que le
     * ticket-service calcule — elles ne se saisissent pas ici.
     */
    private static void deriverPiecesRemises(Map<String, Object> v, Map<String, Object> payload) {
        List<Map<String, Object>> items = asListOfMaps(v.get("PIECES_REMISES"));
        if (!items.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> item : items) {
                BigDecimal n = decimal(str(item.get("PIECE_NOMBRE")));
                total = total.add(n.signum() > 0 ? n : BigDecimal.ONE);
            }
            put(v, "PIECES_NOMBRE_TOTAL", entier(total));
        }
        List<String> manquantes = asListOfStrings(payload.get("piecesManquantes"));
        if (!manquantes.isEmpty()) {
            put(v, "PIECES_MANQUANTES", String.join(" ; ", manquantes));
        }
    }

    /**
     * La note d'annulation énumère les démarches interrompues. La plateforme les
     * connaît : ce sont les démarches cochées du ticket, avec leur date de
     * cochage. Les redemander à l'employé serait lui faire recopier son propre
     * journal.
     */
    private static void deriverDemarchesInterrompues(Map<String, Object> v,
                                                     Map<String, Object> payload) {
        List<Map<String, Object>> demarches = asListOfMaps(payload.get("demarchesInterrompues"));
        if (demarches.isEmpty()) return;
        List<Map<String, Object>> items = new ArrayList<>(demarches.size());
        for (Map<String, Object> d : demarches) {
            Map<String, Object> item = new LinkedHashMap<>();
            put(item, "DEMARCHE_DESIGNATION", str(d.get("libelle")));
            put(item, "DEMARCHE_ADMINISTRATION", str(d.get("organisme")));
            put(item, "DEMARCHE_DATE_DEPOT", dateFr(str(d.get("cocheAt"))));
            put(item, "DEMARCHE_REFERENCE", str(d.get("reference")));
            items.add(item);
        }
        v.put("DEMARCHES_INTERROMPUES", items);
    }

    /**
     * Les bénéficiaires effectifs : l'étendue du contrôle se lit dans la
     * répartition du capital quand le bénéficiaire est aussi un associé. Le
     * rapprochement se fait sur le nom, et <b>uniquement</b> sur une
     * correspondance exacte : approcher un nom serait attribuer des parts à
     * quelqu'un.
     */
    private static void deriverBeneficiaires(Map<String, Object> v, Map<String, Object> base) {
        List<Map<String, Object>> items = asListOfMaps(v.get("BENEFICIAIRES_EFFECTIFS"));
        if (items.isEmpty()) return;
        List<Map<String, Object>> associes = asListOfMaps(base.get("ASSOCIES"));
        if (associes.isEmpty()) return;

        BigDecimal totalParts = BigDecimal.ZERO;
        for (Map<String, Object> a : associes) {
            totalParts = totalParts.add(decimal(str(a.get("ASSOCIE_NOMBRE_PARTS"))));
        }

        for (Map<String, Object> be : items) {
            if (be.containsKey("BE_NOMBRE_PARTS")) continue;
            String nom = normaliser(str(be.get("BE_NOM")) + " " + str(be.get("BE_PRENOM")));
            if (nom.isBlank()) continue;
            for (Map<String, Object> a : associes) {
                String candidat = normaliser(str(a.get("ASSOCIE_NOM")) + " "
                        + str(a.get("ASSOCIE_PRENOM")));
                if (!candidat.equals(nom)) continue;
                BigDecimal parts = decimal(str(a.get("ASSOCIE_NOMBRE_PARTS")));
                if (parts.signum() <= 0) break;
                be.put("BE_NOMBRE_PARTS", entier(parts));
                if (totalParts.signum() > 0 && !be.containsKey("BE_POURCENTAGE")) {
                    be.put("BE_POURCENTAGE", parts.multiply(BigDecimal.valueOf(100))
                            .divide(totalParts, 2, RoundingMode.HALF_UP)
                            .stripTrailingZeros().toPlainString());
                }
                break;
            }
        }
    }

    /**
     * LA CASE « FORME JURIDIQUE » DE LA DÉCLARATION D'EXISTENCE NE SE COCHAIT PAS.
     *
     * <p>Constaté en lisant le document produit : le formulaire DGI sortait avec
     * ses six cases vides. Le moteur coche la case dont le libellé est
     * <b>exactement</b> égal à la valeur ; or {@code $FORME_JURIDIQUE} vaut
     * « SARL » — c'est ce qui s'imprime dans les actes (« la société PARACOSME,
     * SARL au capital de… ») — tandis que l'imprimé DGI écrit « Société à
     * responsabilité limitée ».
     *
     * <p>On ne touche pas au gabarit du cabinet, et on ne change pas la valeur
     * partout : un acte qui dirait « la société X, Société à responsabilité
     * limitée au capital de… » serait fautif. L'alignement se fait donc <b>dans
     * la résolution, et pour ce document seul</b>.
     *
     * <p>La déclaration d'immatriculation au RC, elle, ne porte pas cette case :
     * elle n'est pas concernée.
     */
    private static void alignerFormeJuridiqueSurLImprime(String templateCode,
                                                          Map<String, Object> v,
                                                          Map<String, Object> base) {
        if (!"DECLARATION_EXISTENCE".equals(templateCode)) return;
        boolean associeUnique = "oui".equalsIgnoreCase(str(base.get("ASSOCIE_UNIQUE")));
        v.put("FORME_JURIDIQUE", associeUnique
                ? "Société à responsabilité limitée à associé unique"
                : "Société à responsabilité limitée");
    }

    // =====================================================================
    //  Conventions de nommage entre le catalogue et le payload
    // =====================================================================

    /** `BENEFICIAIRES_EFFECTIFS` -> `beneficiairesEffectifs`. */
    static String cleDeBoucle(String nom) {
        String[] mots = nom.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(mots[0]);
        for (int i = 1; i < mots.length; i++) {
            sb.append(Character.toUpperCase(mots[i].charAt(0))).append(mots[i].substring(1));
        }
        return sb.toString();
    }

    /**
     * La variable qui porte le RANG d'une occurrence, quand la boucle en a une.
     * Elle est dérivée et jamais saisie — cf. {@code deriverDossier} pour le
     * principe.
     */
    private static String numeroDeBoucle(String boucle) {
        return switch (boucle) {
            case "BENEFICIAIRES_EFFECTIFS" -> "BE_NUMERO";
            case "ACTES_EN_FORMATION" -> "ACTE_FORMATION_NUMERO";
            case "PIECES_REMISES" -> "PIECE_NUMERO";
            case "TRAITEMENTS_DONNEES" -> "TRAITEMENT_NUMERO";
            default -> null;
        };
    }

    /** La variable qui porte le NOMBRE d'occurrences, quand le corpus la réclame. */
    private static String compteDeBoucle(String boucle) {
        return "BENEFICIAIRES_EFFECTIFS".equals(boucle) ? "RBE_NOMBRE_BENEFICIAIRES" : null;
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    private static void put(Map<String, Object> v, String cle, String valeur) {
        if (valeur != null && !valeur.isBlank()) v.put(cle, valeur);
    }

    private static String libelleAssocie(Map<String, Object> a) {
        String denomination = str(a.get("denomination"));
        if (!denomination.isBlank()) return denomination;
        return (str(a.get("prenom")) + " " + str(a.get("nom"))).trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> associes(Map<String, Object> payload) {
        List<Map<String, Object>> associes = asListOfMaps(payload.get("associes"));
        if (!associes.isEmpty()) return associes;
        Map<String, Object> unique = asMap(payload.get("associeUnique"));
        return unique.isEmpty() ? List.of() : List.of(unique);
    }

    private static String enLettres(BigDecimal montant) {
        return FrenchNumberToLetters.madToLetters(montant.setScale(0, RoundingMode.HALF_UP)
                .longValueExact());
    }

    private static String entier(BigDecimal b) {
        return b.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal decimal(Object o) {
        String s = str(o).replace(" ", "").replace(" ", "").replace(",", ".");
        if (s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DATE_FR)) {
            try {
                return LocalDate.parse(s.trim(), f);
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return null;
    }

    private static String dateFr(String s) {
        LocalDate d = date(s);
        if (d != null) return DATE_FR.format(d);
        // Un instant ISO (« 2026-09-11T14:03:22Z ») : on n'en garde que le jour.
        if (s != null && s.length() >= 10) {
            LocalDate jour = date(s.substring(0, 10));
            if (jour != null) return DATE_FR.format(jour);
        }
        return "";
    }

    private static String normaliser(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String first(String... vals) {
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return "";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static List<String> asListOfStrings(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            String s = str(item);
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }
}
