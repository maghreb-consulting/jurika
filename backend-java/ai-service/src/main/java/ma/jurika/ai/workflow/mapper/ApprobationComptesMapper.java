package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.format.FrenchNumberToLetters;
import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code PV_AGO} : PV de l'assemblée générale ordinaire annuelle
 * d'<b>approbation des comptes</b>, décliné en SARL pluripersonnelle et SARL AU.
 *
 * <p>Ce PV directeur <b>remplace</b> l'ancienne voie AGO « affectation du résultat »
 * (retirée en Phase B). Il compose deux jeux de variables :
 * <ol>
 *   <li>le <b>noyau séance d'AG</b> (identité société, tenue de séance, feuille de
 *       présence / quorum, ordre du jour, résolutions, gérants…) produit par le builder
 *       réutilisable {@link SeancePvVarsBuilder} — exactement comme les PV d'incident et
 *       les documents de séance partagés ;</li>
 *   <li>les variables <b>propres à l'approbation des comptes</b> (exercice clos, présence
 *       du commissaire aux comptes, résultat net, boucle d'affectation du résultat,
 *       dividendes) produites ici à partir du bloc {@code approbation} du payload.</li>
 * </ol>
 *
 * <p>Le mapper couvre aussi {@code RAPPORT_GESTION} (rapport de la gérance — document
 * distinct, optionnel, listé en pièce jointe de la convocation) en déléguant à
 * {@link AnnualReportMapper}, de sorte que le rapport reste générable depuis le même
 * workflow {@code PV_AGO}.
 *
 * <p>Sélection SARL vs SARL AU : par le suffixe du {@code templateCode}
 * ({@code _SARL_AU} → associé unique). Aucune lecture DB : tout est dans le payload.
 */
@Component
public class ApprobationComptesMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "PV_AGO";
    static final String TPL_SARL = "PV_APPROBATION_COMPTES_SARL";
    static final String TPL_SARL_AU = "PV_APPROBATION_COMPTES_SARL_AU";
    static final String TPL_RAPPORT = AnnualReportMapper.TPL; // RAPPORT_GESTION

    private static final Set<String> TEMPLATES = Set.of(TPL_SARL, TPL_SARL_AU, TPL_RAPPORT);

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRANCE);

    /** Réutilisé pour le rapport de gestion (document optionnel du workflow PV_AGO). */
    private final AnnualReportMapper annualReportMapper;

    public ApprobationComptesMapper(AnnualReportMapper annualReportMapper) {
        this.annualReportMapper = annualReportMapper;
    }

    @Override
    public String workflowCode() {
        return WORKFLOW;
    }

    @Override
    public Set<String> supportedTemplates() {
        return TEMPLATES;
    }

    @Override
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        if (templateCode == null || !TEMPLATES.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par ApprobationComptesMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // Rapport de gestion : document distinct, délégué au mapper dédié.
        if (TPL_RAPPORT.equals(templateCode)) {
            return annualReportMapper.map(templateCode, safe);
        }

        // 1) Noyau séance d'AG (réutilisable, double nommage).
        Map<String, Object> vars = new LinkedHashMap<>(
                SeancePvVarsBuilder.build(templateCode, safe));

        // 2) Variables propres à l'approbation des comptes.
        Map<String, Object> approbation = asMap(safe.get("approbation"));
        vars.putAll(approbationVars(approbation));

        // 3) Lot DIVERS §E (2026-08-13) — ORDRE DU JOUR et RÉSOLUTIONS DÉRIVÉS.
        //
        // Ils étaient jusqu'ici saisis en texte libre dans le sous-formulaire de séance,
        // alors que leur contenu est ENTIÈREMENT déterminé par les données structurées de
        // l'étape 2 (résultat, affectation, dividendes, quitus, conventions). C'était donc
        // une seconde saisie de la même information, libre de diverger du corps du PV.
        // On les construit désormais ici, comme le fait déjà SuccursaleVarsBuilder pour
        // les PV de succursale. Un ordre du jour explicitement fourni reste respecté
        // (rétro-compatibilité des payloads existants).
        boolean isAu = templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU");
        vars.put("RESOLUTIONS", buildResolutions(approbation, vars, isAu));
        if (isEmptyList(vars.get("ORDRE_DU_JOUR"))) {
            vars.put("ORDRE_DU_JOUR", buildOrdreDuJour(approbation));
        }
        return vars;
    }

    // ------------------------------------------------------------------
    // Ordre du jour + résolutions dérivés (lot DIVERS §E)
    // ------------------------------------------------------------------

    /** Points de l'ordre du jour d'une AGO d'approbation, dans l'ordre légal. */
    static List<Map<String, Object>> buildOrdreDuJour(Map<String, Object> a) {
        List<Map<String, Object>> odj = new ArrayList<>();
        odj.add(point("Lecture du rapport de gestion de la gérance"));
        if ("oui".equals(normOuiNon(a.get("commissairePresent"), "non"))) {
            odj.add(point("Lecture du rapport du commissaire aux comptes"));
        }
        odj.add(point("Approbation des comptes de l'exercice clos"));
        odj.add(point("Affectation du résultat"));
        if ("oui".equals(normOuiNon(a.get("dividendeDistribue"), "non"))) {
            odj.add(point("Distribution de dividendes"));
        }
        if ("oui".equals(normOuiNon(a.get("conventionsReglementees"), "non"))) {
            odj.add(point("Conventions réglementées"));
        }
        if ("oui".equals(normOuiNon(a.get("quitusGerance"), "oui"))) {
            odj.add(point("Quitus à la gérance"));
        }
        odj.add(point("Pouvoirs à l'effet des formalités"));
        return odj;
    }

    /**
     * Résolutions de l'AGO d'approbation, dérivées des données structurées.
     *
     * <p>Reprend la rédaction directeur des résolutions types : approbation des comptes,
     * affectation du résultat, distribution de dividendes le cas échéant, conventions
     * réglementées, quitus à la gérance, pouvoirs pour formalités.
     */
    static List<Map<String, Object>> buildResolutions(Map<String, Object> a,
                                                      Map<String, Object> v, boolean isAu) {
        String exercice = str(v.get("EXERCICE_CLOS_DATE"));
        String resultatType = str(v.get("RESULTAT_TYPE"));
        String resultatChiffres = str(v.get("RESULTAT_NET_CHIFFRES"));
        String resultatLettres = str(v.get("RESULTAT_NET_LETTRES"));
        String voixPour = defaultVoixPour(v);

        List<Map<String, Object>> res = new ArrayList<>();

        StringBuilder approbationTexte = new StringBuilder(
                "L'assemblée, après avoir entendu la lecture du rapport de gestion de la gérance");
        if ("oui".equals(normOuiNon(a.get("commissairePresent"), "non"))) {
            approbationTexte.append(" et du rapport du commissaire aux comptes");
        }
        approbationTexte.append(", approuve les comptes de l'exercice clos le ")
                .append(exercice)
                .append(" tels qu'ils lui ont été présentés, se soldant par un ")
                .append(resultatType)
                .append(" de ").append(resultatChiffres).append(" DH (")
                .append(resultatLettres).append(").");
        res.add(resolution("Approbation des comptes de l'exercice", approbationTexte.toString(),
                isAu, voixPour));

        String affectationTexte = describeAffectations(v);
        res.add(resolution("Affectation du résultat",
                "L'assemblée décide d'affecter le " + resultatType + " de l'exercice comme suit : "
                        + affectationTexte + ".",
                isAu, voixPour));

        if ("oui".equals(normOuiNon(a.get("dividendeDistribue"), "non"))) {
            Long total = toLong(first0(a.get("dividendeMontantTotal"), a.get("dividendeTotal")));
            StringBuilder d = new StringBuilder("L'assemblée décide de distribuer un dividende");
            if (total != null) d.append(" d'un montant total de ").append(formatAmount(total)).append(" DH");
            String parPart = str(v.get("DIVIDENDE_PAR_PART_CHIFFRES"));
            if (parPart != null && !parPart.isBlank()) {
                d.append(", soit ").append(parPart).append(" DH par part sociale");
            }
            String miseEnPaiement = str(v.get("DIVIDENDE_MISE_EN_PAIEMENT_DATE"));
            if (miseEnPaiement != null && !miseEnPaiement.isBlank()) {
                d.append(". La mise en paiement interviendra le ").append(miseEnPaiement);
            }
            d.append('.');
            res.add(resolution("Distribution de dividendes", d.toString(), isAu, voixPour));
        }

        String conventions = normOuiNon(a.get("conventionsReglementees"), "non");
        if ("oui".equals(conventions)) {
            String detail = str(a.get("conventionsDetail"));
            res.add(resolution("Conventions réglementées",
                    "L'assemblée approuve les conventions réglementées visées par la loi 5-96 : "
                            + (detail == null || detail.isBlank()
                                    ? "telles que présentées dans le rapport spécial" : detail)
                            + ".",
                    isAu, voixPour));
        } else {
            res.add(resolution("Conventions réglementées",
                    "L'assemblée constate qu'aucune convention visée par la loi 5-96 n'est "
                            + "intervenue au cours de l'exercice.",
                    isAu, voixPour));
        }

        if ("oui".equals(normOuiNon(a.get("quitusGerance"), "oui"))) {
            res.add(resolution("Quitus à la gérance",
                    "L'assemblée donne à la gérance quitus entier et sans réserve de sa gestion "
                            + "pour l'exercice clos le " + exercice + ".",
                    isAu, voixPour));
        }

        res.add(resolution("Pouvoirs à l'effet des formalités",
                "Tous pouvoirs sont donnés au porteur d'un original, d'une copie ou d'un extrait "
                        + "du présent procès-verbal à l'effet d'accomplir les formalités de dépôt "
                        + "et de publicité prévues par la loi.",
                isAu, voixPour));

        String suffix = isAu ? " DÉCISION" : " RÉSOLUTION";
        for (int i = 0; i < res.size(); i++) {
            res.get(i).put("RESOLUTION_NUMERO", ordinal(i + 1) + suffix);
        }
        return res;
    }

    /** « réserve légale : 5 000 DH, report à nouveau : 12 000 DH » à partir de la boucle. */
    @SuppressWarnings("unchecked")
    private static String describeAffectations(Map<String, Object> v) {
        Object raw = v.get("AFFECTATION_RESULTAT");
        if (!(raw instanceof List<?> rows) || rows.isEmpty()) {
            return "en totalité au report à nouveau";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> row = (Map<String, Object>) m;
            if (sb.length() > 0) sb.append(" ; ");
            sb.append(str(row.get("AFFECTATION_LIBELLE")))
              .append(" : ").append(str(row.get("AFFECTATION_MONTANT_CHIFFRES"))).append(" DH");
        }
        return sb.toString();
    }

    private static Map<String, Object> point(String libelle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("POINT_ORDRE_DU_JOUR", libelle);
        m.put("POINT_ODJ_LIBELLE", libelle);
        return m;
    }

    private static Map<String, Object> resolution(String intitule, String texte, boolean isAu,
                                                  String voixPour) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("RESOLUTION_INTITULE", intitule);
        r.put("RESOLUTION_TEXTE", texte);
        r.put("RESOLUTION_RESULTAT", "adoptée");
        r.put("RESOLUTION_VOIX_POUR", isAu ? "" : voixPour);
        r.put("RESOLUTION_VOIX_CONTRE", isAu ? "" : "0");
        r.put("RESOLUTION_ABSTENTIONS", isAu ? "" : "0");
        return r;
    }

    private static final String[] ORDINALES = {"", "PREMIÈRE", "DEUXIÈME", "TROISIÈME",
            "QUATRIÈME", "CINQUIÈME", "SIXIÈME", "SEPTIÈME", "HUITIÈME"};

    private static String ordinal(int n) {
        return (n >= 1 && n < ORDINALES.length) ? ORDINALES[n] : (n + "E");
    }

    /** Voix « pour » : parts présentes calculées par le noyau séance, sinon « l'unanimité ». */
    private static String defaultVoixPour(Map<String, Object> v) {
        String pp = str(v.get("PARTS_PRESENTES_CHIFFRES"));
        return pp == null || pp.isBlank() ? "l'unanimité" : pp;
    }

    private static boolean isEmptyList(Object o) {
        return !(o instanceof List<?> l) || l.isEmpty();
    }

    // ------------------------------------------------------------------
    // Variables d'approbation des comptes
    // ------------------------------------------------------------------

    /**
     * Produit les variables propres au PV d'approbation à partir du bloc
     * {@code approbation} du payload. Toutes les variables sont produites (les
     * conditionnelles inutilisées seront simplement retirées par le moteur).
     */
    static Map<String, Object> approbationVars(Map<String, Object> a) {
        Map<String, Object> v = new LinkedHashMap<>();

        put(v, "EXERCICE_CLOS_DATE", dateStr(a.get("exerciceClosDate")));

        String cacPresent = normOuiNon(a.get("commissairePresent"), "non");
        put(v, "COMMISSAIRE_COMPTES_PRESENT", cacPresent);
        put(v, "COMMISSAIRE_COMPTES_NOM", str(a.get("commissaireNom")));

        // Résultat net : type (bénéfice / perte) + montant chiffres / lettres.
        Long resultat = toLong(a.get("resultatNet"));
        put(v, "RESULTAT_TYPE", normResultatType(a.get("resultatType"), resultat));
        put(v, "RESULTAT_NET_CHIFFRES", resultat == null ? "" : formatAmount(Math.abs(resultat)));
        put(v, "RESULTAT_NET_LETTRES",
                resultat == null ? "" : FrenchNumberToLetters.numberToLetters(Math.abs(resultat)));

        // Boucle AFFECTATION_RESULTAT : libellé + montant (chiffres / lettres).
        v.put("AFFECTATION_RESULTAT", buildAffectations(asListOfMaps(a.get("affectations"))));

        // Dividendes (conditionnels).
        String divide = normOuiNon(a.get("dividendeDistribue"), "non");
        put(v, "DIVIDENDE_DISTRIBUE", divide);
        Long divPart = toLong(a.get("dividendeParPart"));
        put(v, "DIVIDENDE_PAR_PART_CHIFFRES", divPart == null ? "" : formatAmount(divPart));
        put(v, "DIVIDENDE_MISE_EN_PAIEMENT_DATE", dateStr(a.get("dividendeMiseEnPaiementDate")));

        return v;
    }

    // NOTE (lot DIVERS §E) — le montant TOTAL des dividendes n'a PAS de variable dans le
    // modèle directeur : il y figure naturellement comme ligne « Dividendes » de la boucle
    // AFFECTATION_RESULTAT. On n'invente donc aucune variable ; le total n'est repris que
    // dans le texte de la résolution de distribution (cf. buildResolutions).

    private static List<Map<String, Object>> buildAffectations(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r == null || r.isEmpty()) continue;
            String libelle = str(r.get("libelle"));
            Long montant = toLong(first0(r.get("montant"), r.get("montantChiffres")));
            if ((libelle == null || libelle.isBlank()) && montant == null) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("AFFECTATION_LIBELLE", libelle == null ? "" : libelle);
            it.put("AFFECTATION_MONTANT_CHIFFRES", montant == null ? "" : formatAmount(montant));
            it.put("AFFECTATION_MONTANT_LETTRES",
                    montant == null ? "" : FrenchNumberToLetters.numberToLetters(montant));
            out.add(it);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Normalisations / helpers
    // ------------------------------------------------------------------

    /** Type de résultat : libellés EXACTS du modèle (« bénéfice » / « perte »). */
    private static String normResultatType(Object raw, Long montant) {
        String s = norm(raw);
        if (s.startsWith("benef") || s.startsWith("profit")) return "bénéfice";
        if (s.startsWith("pert") || s.startsWith("defic")) return "perte";
        // Déduction depuis le signe du montant si le type n'est pas fourni.
        if (montant != null) return montant < 0 ? "perte" : "bénéfice";
        return "bénéfice";
    }

    private static String normOuiNon(Object raw, String def) {
        if (raw == null) return def;
        if (raw instanceof Boolean b) return b ? "oui" : "non";
        String s = norm(raw);
        if (s.isEmpty()) return def;
        if (s.startsWith("o") || s.equals("true") || s.equals("1")) return "oui";
        if (s.startsWith("n") || s.equals("false") || s.equals("0")) return "non";
        return def;
    }

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    /** Formatage montant à la française (séparateur de milliers), aligné sur la séance. */
    private static String formatAmount(long n) {
        return String.format(Locale.FRANCE, "%,d", n);
    }

    private static String dateStr(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.format(DATE_FR);
        return o == null ? "" : String.valueOf(o);
    }

    private static String norm(Object raw) {
        if (raw == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(raw), java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "");
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
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
