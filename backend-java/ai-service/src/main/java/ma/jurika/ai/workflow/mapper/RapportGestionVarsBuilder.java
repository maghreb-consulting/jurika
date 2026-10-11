package ma.jurika.ai.workflow.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot L4d (E11e) : variables du rapport de gestion de la gerance, rendu par les modeles du
 * corpus RAPPORT_GESTION_EXERCICE_SARL et RAPPORT_GESTION_EXERCICE_SARL_AU (choisis par la
 * forme de la societe). SEUL endroit qui construit ces variables : le parcours PV_AGO
 * (ApprobationComptesMapper) et le parcours ANNUAL_REPORT (AnnualReportMapper) l'appellent
 * tous deux (fin du doublon de mappers releve au backlog).
 *
 * <p>Sources, sans double saisie :
 * <ul>
 *   <li>societe (identite lue en base, SocieteIdentityEnricher) : denomination, capital,
 *       siege, greffe, RC, ICE, nombre de parts, objet social ; valeur nominale = capital /
 *       nombre de parts ;</li>
 *   <li>seance : date de l'assemblee (ou de la decision de l'associe unique) ;</li>
 *   <li>gerants : boucle GERANTS (signature de la gerance) ;</li>
 *   <li>approbation (etape 2 du PV) : exercice clos, resultat, affectation, dividendes,
 *       commissaire aux comptes, conventions reglementees -- memes variables que le PV
 *       (ApprobationComptesMapper#approbationVars) : le rapport propose, le PV vote ;</li>
 *   <li>rapportGestion (saisi a l'ecran) : ouverture de l'exercice, exposes, faits
 *       marquants, indicateurs, capitaux propres, evenements posterieurs, recherche et
 *       developpement, reserve legale, pertes cumulees, lieu et date du rapport.</li>
 * </ul>
 *
 * <p>Rien n'est suppose : une donnee absente reste vide et la generation la nomme ; une
 * donnee qui pilote une branche du modele (oui / non) est exigee (SaisieExigee). Une donnee
 * n'est exigee que si la branche qui l'imprime est prise (pertes cumulees en cas de perte,
 * reserve legale et dividendes en cas de benefice, liste des evenements si "oui"...).
 */
public final class RapportGestionVarsBuilder {

    public static final String TPL = "RAPPORT_GESTION";
    public static final String MODELE_SARL = "RAPPORT_GESTION_EXERCICE_SARL";
    public static final String MODELE_SARL_AU = "RAPPORT_GESTION_EXERCICE_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRANCE);

    private RapportGestionVarsBuilder() {
    }

    /**
     * Modele du corpus selon la forme de la societe. Forme inconnue : modele SARL (le choix
     * n'est alors qu'une lecture du modele, ex. emplacement de clause) ; {@link #build}
     * refuse la generation en nommant la forme.
     */
    public static String modele(Map<String, Object> payload) {
        return Boolean.TRUE.equals(associeUnique(payload)) ? MODELE_SARL_AU : MODELE_SARL;
    }

    /** true : SARL AU ; false : SARL ; null : forme inconnue. */
    static Boolean associeUnique(Map<String, Object> payload) {
        if (payload == null) return null;
        Map<String, Object> societe = asMap(payload.get("societe"));
        // La forme lue en base (societe, enrichie) prime sur celle de l'ecran.
        for (Object raw : new Object[]{societe.get("formeJuridique"), payload.get("formeJuridique")}) {
            String f = raw == null ? "" : String.valueOf(raw).toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
            if (f.equals("SARLAU")) return true;
            if (f.equals("SARL")) return false;
        }
        Object au = payload.get("associeUnique");
        if (au instanceof Boolean b) return b;
        return null;
    }

    public static Map<String, Object> build(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        if (associeUnique(safe) == null) {
            throw SaisieExigee.refus(TPL, "FORME_JURIDIQUE", "Forme juridique de la soci\u00e9t\u00e9 (SARL ou SARL AU)");
        }
        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> seance = asMap(safe.get("seance"));
        Map<String, Object> a = new LinkedHashMap<>(asMap(safe.get("approbation")));
        Map<String, Object> rg = asMap(safe.get("rapportGestion"));

        Map<String, Object> v = new LinkedHashMap<>();

        // ---- Societe (fiche) ----
        put(v, "DENOMINATION", str(societe.get("denomination")));
        BigDecimal capital = decimal(first(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        put(v, "CAPITAL_CHIFFRES", capital == null ? "" : montant(capital));
        put(v, "SIEGE_SOCIAL", firstStr(societe.get("siegeSocial"), societe.get("adresseSiege")));
        put(v, "VILLE_GREFFE", firstStr(societe.get("villeGreffe"), societe.get("rcVille")));
        put(v, "RC_NUMERO", str(societe.get("rcNumero")));
        put(v, "ICE", firstStr(societe.get("ice"), societe.get("iceNumero")));
        put(v, "OBJET_SOCIAL", str(societe.get("objetSocial")));
        BigDecimal parts = decimal(societe.get("nombreParts"));
        put(v, "NOMBRE_PARTS", parts == null ? "" : montant(parts));
        put(v, "VALEUR_NOMINALE_PART", valeurNominale(capital, parts, societe.get("valeurNominalePart")));

        // ---- Assemblee (ou decision de l'associe unique) ----
        put(v, "ASSEMBLEE_DATE", dateStr(first(seance.get("date"), seance.get("assembleeDate"))));

        // ---- Exercice et comptes : memes variables que le PV d'approbation ----
        Object resultatType = a.get("resultatType");
        if (blank(resultatType) && blank(a.get("resultatNet"))) {
            throw SaisieExigee.refus(TPL, "RESULTAT_TYPE", "Sens du r\u00e9sultat de l'exercice (b\u00e9n\u00e9fice ou perte)");
        }
        a.put("commissairePresent", SaisieExigee.ouiNon(TPL, a.get("commissairePresent"),
                "COMMISSAIRE_COMPTES_PRESENT", "Commissaire aux comptes en fonction (oui ou non)"));
        Map<String, Object> comptes = ApprobationComptesMapper.approbationVars(a);
        boolean benefice = "b\u00e9n\u00e9fice".equals(comptes.get("RESULTAT_TYPE"));
        if (benefice) {
            // Dividende et reserve legale : branches imprimees seulement en cas de benefice.
            a.put("dividendeDistribue", SaisieExigee.ouiNon(TPL, a.get("dividendeDistribue"),
                    "DIVIDENDE_DISTRIBUE", "Distribution de dividendes (oui ou non)"));
            comptes = ApprobationComptesMapper.approbationVars(a);
            put(v, "RESERVE_LEGALE_DOTEE_INTEGRALEMENT", SaisieExigee.ouiNon(TPL,
                    rg.get("reserveLegaleDoteeIntegralement"), "RESERVE_LEGALE_DOTEE_INTEGRALEMENT",
                    "R\u00e9serve l\u00e9gale ayant atteint le cinqui\u00e8me du capital (oui ou non)"));
        } else {
            put(v, "PERTES_CUMULEES_MONTANT", montantOuVide(rg.get("pertesCumulees")));
        }
        v.putAll(comptes);
        if (((List<?>) comptes.get("AFFECTATION_RESULTAT")).isEmpty()) {
            // Boucle vide : la ligne s'effacerait en silence ; absente, ses donnees sont nommees.
            v.remove("AFFECTATION_RESULTAT");
        }
        put(v, "CONVENTIONS_REGLEMENTEES_EXISTE", SaisieExigee.ouiNon(TPL, a.get("conventionsReglementees"),
                "CONVENTIONS_REGLEMENTEES_EXISTE",
                "Conventions r\u00e9glement\u00e9es intervenues au cours de l'exercice (oui ou non)"));

        // ---- Saisies propres au rapport ----
        LocalDate debut = toDate(rg.get("exerciceDebut"));
        put(v, "EXERCICE_DEBUT", dateStr(rg.get("exerciceDebut")));
        // L'exercice precedent se clot la veille de l'ouverture de celui-ci (exercices
        // consecutifs) ; une date saisie prime.
        Object precedent = rg.get("exercicePrecedentClosDate");
        put(v, "EXERCICE_PRECEDENT_CLOS_DATE", !blank(precedent) ? dateStr(precedent)
                : debut == null ? "" : debut.minusDays(1).format(DATE_FR));
        put(v, "ACTIVITE_EXERCICE_EXPOSE", str(rg.get("activiteExpose")));
        putBoucle(v, "FAITS_MARQUANTS_EXERCICE", faitsMarquants(asListOfMaps(rg.get("faitsMarquants"))));
        putBoucle(v, "INDICATEURS_GESTION", indicateurs(asListOfMaps(rg.get("indicateurs"))));
        put(v, "ACTIVITE_EVOLUTION_COMMENTAIRE", str(rg.get("evolutionCommentaire")));
        BigDecimal capitauxPropres = decimal(rg.get("capitauxPropres"));
        put(v, "CAPITAUX_PROPRES_MONTANT", capitauxPropres == null ? "" : montant(capitauxPropres));
        // Article 86 de la loi 5-96 : capitaux propres inferieurs au quart du capital. Calcule
        // (le moteur ne compare pas de nombres) ; sans l'un des deux montants, rien n'est suppose.
        if (capitauxPropres != null && capital != null) {
            boolean sousQuart = capitauxPropres.multiply(BigDecimal.valueOf(4)).compareTo(capital) < 0;
            put(v, "CAPITAUX_PROPRES_SOUS_QUART", sousQuart ? "oui" : "non");
        } else {
            put(v, "CAPITAUX_PROPRES_SOUS_QUART", "");
        }
        String evenements = SaisieExigee.ouiNon(TPL, rg.get("evenementsPostCloture"), "EVENEMENTS_POST_CLOTURE",
                "\u00c9v\u00e9nements importants survenus depuis la cl\u00f4ture (oui ou non)");
        put(v, "EVENEMENTS_POST_CLOTURE", evenements);
        if ("oui".equals(evenements)) {
            List<Map<String, Object>> liste = evenementsPostCloture(asListOfMaps(rg.get("evenementsPostClotureListe")));
            // Ecart mecanique du corpus (rapporte au cabinet) : la condition et la boucle
            // portent le meme nom ; la liste sert les deux (le moteur lit une liste non vide
            // comme "oui"). Annonces sans aucun evenement : le moteur effacerait la boucle en
            // silence, la generation est refusee et la donnee nommee.
            if (liste.isEmpty()) {
                throw SaisieExigee.refus(TPL, "EVENEMENT_POST_CLOTURE_LIBELLE",
                        "\u00c9v\u00e9nements survenus depuis la cl\u00f4ture (au moins un, avec sa date et son incidence)");
            }
            v.put("EVENEMENTS_POST_CLOTURE", liste);
        }
        put(v, "PERSPECTIVES_EXPOSE", str(rg.get("perspectives")));
        String rd = SaisieExigee.ouiNon(TPL, rg.get("rechercheDeveloppement"), "RECHERCHE_DEVELOPPEMENT",
                "Travaux de recherche et de d\u00e9veloppement engag\u00e9s (oui ou non)");
        put(v, "RECHERCHE_DEVELOPPEMENT", rd);
        if ("oui".equals(rd)) put(v, "RECHERCHE_DEVELOPPEMENT_EXPOSE", str(rg.get("rechercheDeveloppementExpose")));
        put(v, "LIEU_SIGNATURE", str(rg.get("lieuSignature")));
        put(v, "RAPPORT_GESTION_DATE", dateStr(rg.get("date")));

        // ---- Signature de la gerance ----
        List<Map<String, Object>> gerants = SeancePvVarsBuilder.buildGerantsLoop(asListOfMaps(safe.get("gerants")));
        putBoucle(v, "GERANTS", gerants);
        return v;
    }

    // ------------------------------------------------------------------
    // Boucles
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> faitsMarquants(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (blank(r.get("date")) && blank(r.get("libelle"))) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("FAIT_MARQUANT_DATE", dateStr(r.get("date")));
            it.put("FAIT_MARQUANT_LIBELLE", strOr(r.get("libelle")));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> indicateurs(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (blank(r.get("libelle")) && blank(r.get("montantExercice"))
                    && blank(r.get("montantExercicePrecedent"))) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("INDICATEUR_LIBELLE", strOr(r.get("libelle")));
            it.put("INDICATEUR_MONTANT_EXERCICE", montantOuTexte(r.get("montantExercice")));
            it.put("INDICATEUR_MONTANT_EXERCICE_PRECEDENT", montantOuTexte(r.get("montantExercicePrecedent")));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> evenementsPostCloture(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (blank(r.get("date")) && blank(r.get("libelle")) && blank(r.get("incidence"))) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("EVENEMENT_POST_CLOTURE_DATE", dateStr(r.get("date")));
            it.put("EVENEMENT_POST_CLOTURE_LIBELLE", strOr(r.get("libelle")));
            it.put("EVENEMENT_POST_CLOTURE_INCIDENCE", strOr(r.get("incidence")));
            out.add(it);
        }
        return out;
    }

    /**
     * Boucle vide : le modele en effacerait la ligne en silence (une liste annoncee "suivante"
     * resterait sans element). Absente, ses variables sont nommees a la generation.
     */
    private static void putBoucle(Map<String, Object> v, String nom, List<Map<String, Object>> rows) {
        if (!rows.isEmpty()) v.put(nom, rows);
    }

    // ------------------------------------------------------------------
    // Montants
    // ------------------------------------------------------------------

    /** Capital / nombre de parts, exact ; sinon la valeur de la fiche ; sinon vide (nommee). */
    private static String valeurNominale(BigDecimal capital, BigDecimal parts, Object fiche) {
        if (capital != null && parts != null && parts.signum() > 0) {
            try {
                return montant(capital.divide(parts));
            } catch (ArithmeticException nonExacte) {
                // division sans quotient decimal exact : la fiche tranche
            }
        }
        BigDecimal f = decimal(fiche);
        return f == null ? "" : montant(f);
    }

    /** Montant a la francaise : separateur de milliers, virgule decimale si besoin. */
    static String montant(BigDecimal n) {
        BigDecimal s = n.stripTrailingZeros();
        if (s.scale() <= 0) return String.format(Locale.FRANCE, "%,d", s.toBigIntegerExact());
        // Centimes : deux decimales au moins (12 500,50).
        return String.format(Locale.FRANCE, "%,." + Math.max(2, s.scale()) + "f", s);
    }

    private static String montantOuVide(Object raw) {
        BigDecimal d = decimal(raw);
        return d == null ? "" : montant(d);
    }

    /** Indicateur : un nombre se met en forme ; un texte saisi (ex. effectif) reste tel quel. */
    private static String montantOuTexte(Object raw) {
        BigDecimal d = decimal(raw);
        if (d != null) return montant(d);
        return strOr(raw);
    }

    static BigDecimal decimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "").replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void put(Map<String, Object> v, String k, String val) {
        v.put(k, val == null ? "" : val);
    }

    private static String dateStr(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.format(DATE_FR);
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        try {
            return LocalDate.parse(String.valueOf(o).trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean blank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private static String str(Object o) {
        return blank(o) ? "" : String.valueOf(o).trim();
    }

    private static String strOr(Object o) {
        return str(o);
    }

    private static String firstStr(Object... vals) {
        for (Object o : vals) if (!blank(o)) return String.valueOf(o).trim();
        return "";
    }

    private static Object first(Object... vals) {
        for (Object o : vals) if (!blank(o)) return o;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }
}
