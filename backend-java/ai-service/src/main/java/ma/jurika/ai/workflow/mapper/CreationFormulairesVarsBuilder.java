package ma.jurika.ai.workflow.mapper;

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
 * Lot 5 (2026-09-07) — variables des TROIS FORMULAIRES administratifs du parcours
 * de création : demande d'inscription à la taxe professionnelle (étape 19),
 * déclaration d'existence (étape 20) et déclaration d'immatriculation au registre
 * du commerce — modèle 2 (étape 21).
 *
 * <p>Ces trois modèles ne sont pas des actes : ce sont des <b>imprimés</b> de
 * l'administration, faits de cases. La quasi-totalité de ce qu'ils demandent est
 * déjà saisie ailleurs dans le workflow — c'est la règle permanente du projet : ne
 * jamais redemander une donnée qui est en base. Ce producteur part donc du jeu
 * complet des modèles du directeur ({@link CreationDirecteurVarsBuilder}) et n'y
 * ajoute que ce que les formulaires demandent en propre :
 *
 * <ul>
 *   <li><b>des dérivations</b> — l'activité principale se lit dans l'objet social,
 *       le domicile fiscal est le siège, le type de tribunal se déduit de la ville,
 *       la date de fin de société se calcule ;</li>
 *   <li><b>cinq drapeaux</b> pour les conditions du modèle 2, rédigées en français
 *       dans le fichier du directeur (qui reste intouché) et résolues côté moteur ;</li>
 *   <li><b>deux boucles</b> — les établissements secondaires et les dirigeants
 *       personnes morales ;</li>
 *   <li><b>les saisies propres aux formulaires</b> (direction régionale DGI, régime
 *       de TVA…), reprises telles quelles du payload : elles n'ont aucune source
 *       ailleurs dans le workflow.</li>
 * </ul>
 *
 * <p>Aucune valeur n'est inventée : ce qui n'a pas de source reste vide, remonte
 * comme variable non renseignée, et — s'agissant de cases administratives — ne
 * bloque pas la génération (cf. la classification du lot 5).
 */
public final class CreationFormulairesVarsBuilder {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    /** Codes des trois formulaires. */
    public static final String TPL_DEMANDE_TP = "DEMANDE_TAXE_PROFESSIONNELLE";
    public static final String TPL_DECLARATION_EXISTENCE = "DECLARATION_EXISTENCE";
    public static final String TPL_DECLARATION_RC = "DECLARATION_IMMATRICULATION_RC";

    public static final Set<String> TEMPLATES =
            Set.of(TPL_DEMANDE_TP, TPL_DECLARATION_EXISTENCE, TPL_DECLARATION_RC);

    /**
     * Villes dotées d'un tribunal de commerce (Dahir 53-95). Partout ailleurs c'est
     * le tribunal de première instance qui tient le registre — la note du modèle du
     * directeur porte exactement cette liste. Comparaison sans accents ni casse.
     */
    private static final Set<String> VILLES_TRIBUNAL_COMMERCE = Set.of(
            "beni mellal", "rabat", "casablanca", "fes", "meknes", "oujda",
            "tanger", "marrakech", "agadir");

    private CreationFormulairesVarsBuilder() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> v = new LinkedHashMap<>(
                CreationDirecteurVarsBuilder.build(templateCode, safe));

        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> formulaires = asMap(safe.get("formulaires"));
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));
        List<Map<String, Object>> gerants = asListOfMaps(safe.get("gerants"));

        // ---- Identification de la société : tout vient des étapes 1, 2 et 4 -------
        put(v, "FORME_JURIDIQUE", libelleForme(v.get("ASSOCIE_UNIQUE"), societe.get("formeJuridique")));
        put(v, "SOCIETE_NATIONALITE", first(str(societe.get("nationalite")), "marocaine"));
        put(v, "ICE", str(societe.get("iceNumero")));
        put(v, "IDENTIFIANT_FISCAL", str(societe.get("ifNumero")));
        put(v, "SIGLE", str(societe.get("sigle")));
        put(v, "ENSEIGNE", first(str(formulaires.get("enseigne")), str(societe.get("enseigne"))));
        put(v, "CERTIFICAT_NEGATIF_NUMERO", str(societe.get("certificatNegatifNumero")));
        put(v, "CERTIFICAT_NEGATIF_DATE", dateFr(societe.get("certificatNegatifDate")));

        // Le siège vaut domicile fiscal ET lieu d'activité tant qu'aucun établissement
        // secondaire n'est déclaré : c'est le cas de toute société qui se constitue.
        String siege = str(v.get("SIEGE_SOCIAL"));
        put(v, "DOMICILE_FISCAL", first(str(formulaires.get("domicileFiscal")), siege));
        put(v, "LIEU_ACTIVITE", first(str(formulaires.get("lieuActivite")), siege));
        put(v, "VILLE", first(str(societe.get("ville")), str(societe.get("rcVille")),
                str(v.get("VILLE_GREFFE"))));

        // ---- Activité : l'objet social porte déjà la liste ------------------------
        List<String> activites = activites(societe);
        put(v, "ACTIVITE_PRINCIPALE", activites.isEmpty() ? "" : activites.get(0));
        put(v, "ACTIVITES_AUTRES", activites.size() > 1
                ? String.join(" ; ", activites.subList(1, activites.size())) : "");
        put(v, "ACTIVITE_EXERCEE", activites.isEmpty() ? "" : activites.get(0));

        // ---- Tribunal : le type se DÉDUIT de la ville, il ne se saisit pas ---------
        String villeTribunal = first(str(v.get("VILLE_GREFFE")), str(societe.get("rcVille")));
        put(v, "TRIBUNAL_VILLE", villeTribunal);
        put(v, "TRIBUNAL_TYPE", VILLES_TRIBUNAL_COMMERCE.contains(norm(villeTribunal))
                ? "Tribunal de commerce" : "Tribunal de première instance");

        // ---- Durée : la date de fin se calcule, elle ne se redemande pas -----------
        LocalDate constitution = toDate(first0(societe.get("dateConstitution"),
                societe.get("dateSignature")));
        Long duree = toLong(first0(societe.get("dureeSociete"), societe.get("dureeAnnees")));
        put(v, "DATE_FIN_SOCIETE", constitution != null && duree != null
                ? constitution.plusYears(duree).format(DATE_FR) : "");
        put(v, "DATE_COMMENCEMENT_EXPLOITATION",
                dateFr(first0(formulaires.get("dateDebutActivite"),
                        formulaires.get("dateCommencementExploitation"),
                        societe.get("dateConstitution"))));
        put(v, "DATE_DEBUT_ACTIVITE", str(v.get("DATE_COMMENCEMENT_EXPLOITATION")));

        // ---- Signataire / déclarant ------------------------------------------------
        // Art. 38 al. 2 du Code de commerce : l'immatriculation ne peut être requise
        // que par un gérant. Le déclarant EST donc le premier gérant — le redemander
        // serait une saisie en double.
        Map<String, Object> signataire = gerants.isEmpty() ? Map.of() : gerants.get(0);
        String nomSignataire = nomComplet(signataire);
        put(v, "DECLARANT_NOM", nomSignataire);
        put(v, "DECLARANT_ADRESSE", str(signataire.get("adresse")));
        put(v, "DECLARANT_QUALITE", "gérant");
        put(v, "SIGNATAIRE_QUALITE", "gérant");
        put(v, "SIGNATAIRE_NOM_QUALITE",
                nomSignataire.isBlank() ? "" : nomSignataire + ", gérant");
        put(v, "CNI_CS_NUMERO", str(first0(signataire.get("cinNumero"), signataire.get("cin"),
                signataire.get("pieceNumero"))));

        // ---- Associé principal (art. 26 CGI) : le plus grand nombre de parts -------
        Map<String, Object> principal = associePrincipal(associes);
        put(v, "ASSOCIE_PRINCIPAL_NOM", nomComplet(principal));
        put(v, "ASSOCIE_PRINCIPAL_CNI", str(first0(principal.get("cin"), principal.get("cinNumero"),
                principal.get("pieceNumero"))));
        put(v, "ASSOCIE_PRINCIPAL_ADRESSE", str(principal.get("adresse")));
        put(v, "ASSOCIE_PRINCIPAL_IF", str(first0(principal.get("ifFiscal"),
                formulaires.get("associePrincipalIf"))));
        put(v, "ASSOCIE_PRINCIPAL_VILLE", str(formulaires.get("associePrincipalVille")));
        put(v, "ASSOCIE_PRINCIPAL_TEL", str(formulaires.get("associePrincipalTel")));
        put(v, "ASSOCIE_PRINCIPAL_FAX", str(formulaires.get("associePrincipalFax")));
        put(v, "ASSOCIE_PRINCIPAL_EMAIL", str(formulaires.get("associePrincipalEmail")));

        // ---- Saisies propres aux formulaires (aucune source ailleurs) --------------
        put(v, "DIRECTION_REGIONALE", str(formulaires.get("directionRegionale")));
        put(v, "SUBDIVISION", str(formulaires.get("subdivision")));
        put(v, "TELEPHONE", str(formulaires.get("telephone")));
        put(v, "FAX", str(formulaires.get("fax")));
        put(v, "EMAIL", str(formulaires.get("email")));
        put(v, "PIECES_PRODUITES", str(formulaires.get("piecesProduites")));
        // L'objet de la demande TP est FIXE dans ce workflow : on constitue une société.
        put(v, "TP_OBJET", first(str(formulaires.get("tpObjet")), "Création d'une personne morale"));
        put(v, "TP_OBJET_AUTRE_PRECISION", str(formulaires.get("tpObjetAutrePrecision")));
        put(v, "DE_REGIME_RESULTAT", str(formulaires.get("regimeResultat")));
        put(v, "DE_TVA_ASSUJETTISSEMENT", str(formulaires.get("tvaAssujettissement")));
        put(v, "DE_TVA_FAIT_GENERATEUR", str(formulaires.get("tvaFaitGenerateur")));
        put(v, "DE_TVA_PERIODICITE", str(formulaires.get("tvaPeriodicite")));
        put(v, "DE_ACTIVITE_NATURE", str(formulaires.get("activiteNature")));

        // ---- Post-immatriculation : connus seulement après les étapes 19 à 22 ------
        put(v, "IDENTIFIANT_TP", str(societe.get("taxeProfessionnelle")));
        put(v, "CNSS_NUMERO", str(societe.get("cnss")));
        put(v, "TAXE_SERVICES_COMMUNAUX_NUMERO", str(societe.get("taxeServicesCommunaux")));
        put(v, "DEPOT_ACTES_REFERENCE", str(societe.get("depotActesReference")));
        put(v, "RC_VILLE", first(str(societe.get("rcVille")), villeTribunal));

        // ---- Boucle GERANTS : deux colonnes que seul le modèle 2 réclame -----------
        enrichirGerants((List<Map<String, Object>>) v.get("GERANTS"), gerants);

        // ---- Boucles propres aux formulaires ---------------------------------------
        List<Map<String, Object>> etablissements =
                buildEtablissementsLoop(asListOfMaps(safe.get("etablissements")));
        v.put("ETABLISSEMENTS", etablissements);
        List<Map<String, Object>> dirigeantsPm = buildDirigeantsPmLoop(gerants);
        v.put("DIRIGEANTS_PM", dirigeantsPm);

        // ---- Drapeaux des cinq conditions du modèle 2 -------------------------------
        // Rédigées en français dans le fichier du directeur (intouchable) ; le moteur
        // les résout via NL_CONDITION_FLAGS. Toutes fausses à la constitution, sauf
        // « un dirigeant est une personne morale », qui se lit dans l'étape 5.
        put(v, "HAS_DIRIGEANT_PM", dirigeantsPm.isEmpty() ? "non" : "oui");
        put(v, "HAS_SIEGE_PRECEDENT",
                str(formulaires.get("siegePrecedentExploitant")).isBlank() ? "non" : "oui");
        put(v, "HAS_SUCCURSALES", str(formulaires.get("succursalesMaroc")).isBlank()
                && str(formulaires.get("succursalesEtranger")).isBlank() ? "non" : "oui");
        put(v, "HAS_CAPITAL_VARIABLE",
                str(formulaires.get("capitalVariableMinimum")).isBlank() ? "non" : "oui");
        put(v, "HAS_BREVETS_MARQUES",
                str(formulaires.get("brevetsMarquesReference")).isBlank() ? "non" : "oui");
        put(v, "SIEGE_PRECEDENT_EXPLOITANT", str(formulaires.get("siegePrecedentExploitant")));
        put(v, "SIEGE_PRECEDENT_RC", str(formulaires.get("siegePrecedentRc")));
        put(v, "SUCCURSALES_MAROC", str(formulaires.get("succursalesMaroc")));
        put(v, "SUCCURSALES_ETRANGER", str(formulaires.get("succursalesEtranger")));
        put(v, "SUCCURSALES_PATENTE", str(formulaires.get("succursalesPatente")));
        put(v, "CAPITAL_VARIABLE_MINIMUM", str(formulaires.get("capitalVariableMinimum")));
        put(v, "BREVETS_MARQUES_REFERENCE", str(formulaires.get("brevetsMarquesReference")));

        return v;
    }

    // ------------------------------------------------------------------
    //  Boucles
    // ------------------------------------------------------------------

    /**
     * Ajoute à la boucle GERANTS les deux colonnes que la rubrique 11 du modèle 2
     * réclame et que les modèles du directeur n'utilisent pas.
     *
     * <p>L'enrichissement se fait ICI, et non dans {@link CreationDirecteurVarsBuilder},
     * pour que les statuts, l'acte et l'annonce continuent de produire EXACTEMENT
     * leur jeu de variables : une clé de plus dans leur scope serait une clé produite
     * hors modèle.
     *
     * <p>Les deux données existent déjà à l'étape 5 : le lieu de naissance est saisi,
     * la qualité se lit sur la case « Gérant statutaire ». Aucun champ n'est ajouté.
     * Le lieu de naissance n'est écrit QUE s'il est renseigné — une clé vide se
     * substituerait en silence et laisserait sortir « né le 20/01/1980 à  ».
     */
    private static void enrichirGerants(List<Map<String, Object>> loop,
                                         List<Map<String, Object>> gerants) {
        if (loop == null || loop.isEmpty()) return;
        List<Map<String, Object>> source = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g != null && !g.isEmpty()) source.add(g);
        }
        for (int i = 0; i < loop.size() && i < source.size(); i++) {
            Map<String, Object> g = source.get(i);
            String lieu = strOr(g.get("lieuNaissance"), "");
            if (!lieu.isBlank()) loop.get(i).put("GERANT_LIEU_NAISSANCE", lieu);
            String st = norm(first0(g.get("isStatutaire"), g.get("statutaire"),
                    g.get("designationMode")));
            loop.get(i).put("GERANT_QUALITE",
                    ("true".equals(st) || st.startsWith("statut")) ? "gérant statutaire" : "gérant");
        }
    }

    private static List<Map<String, Object>> buildEtablissementsLoop(List<Map<String, Object>> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : src) {
            if (e == null || e.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("ETAB_ADRESSE", strOr(e.get("adresse"), ""));
            it.put("ETAB_VILLE", strOr(e.get("ville"), ""));
            it.put("ETAB_ACTIVITE", strOr(e.get("activite"), ""));
            it.put("ETAB_IDENTIFIANT_TP", strOr(e.get("identifiantTp"), ""));
            out.add(it);
        }
        return out;
    }

    /**
     * Dirigeants personnes morales — la rubrique 11 du modèle 2 les sépare des
     * personnes physiques. Ils sont déjà saisis à l'étape 5 ({@code typePersonne =
     * MORALE}) : dénomination, siège, RC et représentant en viennent directement.
     * Seuls la forme juridique et l'objet de l'entité n'y figurent pas.
     */
    private static List<Map<String, Object>> buildDirigeantsPmLoop(List<Map<String, Object>> gerants) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g == null || g.isEmpty()) continue;
            if (!"morale".equals(norm(g.get("typePersonne")))
                    && !norm(g.get("typePersonne")).contains("moral")) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("DIRIGEANT_PM_DENOMINATION", strOr(g.get("denomination"), ""));
            it.put("DIRIGEANT_PM_FORME", strOr(first0(g.get("forme"), g.get("formeJuridique")), ""));
            it.put("DIRIGEANT_PM_SIEGE", strOr(g.get("siege"), ""));
            it.put("DIRIGEANT_PM_OBJET", strOr(g.get("objetSocial"), ""));
            it.put("DIRIGEANT_PM_RC", strOr(first0(g.get("rc"), g.get("rcNumero")), ""));
            it.put("DIRIGEANT_PM_REPRESENTANT", strOr(g.get("representantLegal"), ""));
            out.add(it);
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Dérivations
    // ------------------------------------------------------------------

    /** « SARL » / « SARL à associé unique », tel que l'imprimé l'attend. */
    private static String libelleForme(Object associeUnique, Object formeJuridique) {
        String f = norm(formeJuridique);
        boolean au = "oui".equals(norm(associeUnique)) || f.contains("au") || f.contains("unique");
        return au ? "SARL à associé unique" : "SARL";
    }

    /**
     * Activités de la société, une par ligne de l'objet social. La liste explicite
     * du payload prime ; à défaut on découpe la description saisie à l'étape 4.
     */
    private static List<String> activites(Map<String, Object> societe) {
        List<String> out = new ArrayList<>();
        Object liste = societe.get("activites");
        if (liste instanceof List<?> l) {
            for (Object o : l) {
                String s = o == null ? "" : String.valueOf(o).trim();
                if (!s.isBlank()) out.add(s);
            }
        }
        if (!out.isEmpty()) return out;
        String texte = first(str(societe.get("activiteSociete")), str(societe.get("objetSocial")));
        for (String ligne : texte.split("[\\r\\n;]+")) {
            String s = ligne.replaceFirst("^[-–—•*\\s]+", "").trim();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    /** Associé principal au sens de l'art. 26 CGI : le plus grand nombre de parts. */
    private static Map<String, Object> associePrincipal(List<Map<String, Object>> associes) {
        Map<String, Object> best = null;
        long bestParts = Long.MIN_VALUE;
        for (Map<String, Object> a : associes) {
            if (a == null || a.isEmpty()) continue;
            Long parts = toLong(a.get("nombreParts"));
            long p = parts == null ? 0L : parts;
            if (best == null || p > bestParts) { best = a; bestParts = p; }
        }
        return best == null ? Map.of() : best;
    }

    private static String nomComplet(Map<String, Object> p) {
        if (p == null || p.isEmpty()) return "";
        String denom = strOr(p.get("denomination"), "");
        if (!denom.isBlank() && norm(p.get("typePersonne")).contains("moral")) return denom;
        String prenom = strOr(p.get("prenom"), "");
        String nom = strOr(p.get("nom"), "");
        return (prenom + " " + nom).trim();
    }

    // ------------------------------------------------------------------
    //  Utilitaires (mêmes contrats que CreationDirecteurVarsBuilder)
    // ------------------------------------------------------------------

    /**
     * Écrit la variable, ou la RETIRE si sa valeur est vide.
     *
     * <p>Le retrait est essentiel au contrôle de complétude du lot 5 : une clé
     * présente mais vide se substitue en silence par du blanc, et le moteur ne la
     * compte pas comme non résolue — c'est exactement ainsi que « né le  à  » est
     * sorti sans que rien ne le signale. Une clé ABSENTE, elle, produit un sentinel,
     * donc une variable nommée, située, et jugée « phrase » ou « case ».
     */
    private static void put(Map<String, Object> v, String key, String value) {
        if (value == null || value.isBlank()) {
            v.remove(key);
        } else {
            v.put(key, value);
        }
    }

    private static String dateFr(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.format(DATE_FR);
        return strOr(o, "");
    }

    private static String first(String... vals) {
        for (String s : vals) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String strOr(Object o, String fallback) {
        if (o == null) return fallback;
        String s = String.valueOf(o);
        return s.isBlank() ? fallback : s;
    }

    private static String norm(Object o) {
        if (o == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(o), java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o).trim().replace(" ", "").replace(" ", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        String s = String.valueOf(o).trim();
        if (s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(s, DATE_FR);
            } catch (DateTimeParseException ex2) {
                return null;
            }
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
