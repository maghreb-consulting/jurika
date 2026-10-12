package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.GenerationRefuseeException.DonneeManquante;
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
 * Lot L4d (E11b) : documents de seance partages (convocation, feuille de presence) rendus
 * par les modeles du corpus du cabinet, au lieu des gabarits du classpath.
 *
 * <p>Choix du modele, selon la donnee du parcours (L2_correspondance_gabarits.md) :
 * <ul>
 *   <li>convocation : seconde convocation (rang "deuxieme") -> SECONDE_CONVOCATION_ASSEMBLEE_SARL,
 *       quel que soit le type ; sinon assemblee ordinaire -> CONVOCATION_AGO_SARL,
 *       extraordinaire ou mixte -> CONVOCATION_AGE_SARL ;</li>
 *   <li>feuille de presence : ordinaire -> FEUILLE_PRESENCE_AGO_SARL, extraordinaire ou
 *       mixte -> FEUILLE_PRESENCE_AGE_SARL.</li>
 * </ul>
 * Assemblee mixte : le corpus n'a ni convocation ni feuille "mixte" ; le modele de
 * l'assemblee extraordinaire est retenu (decision a revoir, rapport du lot).
 *
 * <p>SARL a associe unique : le corpus n'a ni convocation ni feuille de presence (article 76
 * de la loi n 5-96 : l'associe unique n'est pas convoque) ; la generation est refusee avec
 * ce motif.
 *
 * <p>Regle des variables : ce qui se calcule est calcule (valeur nominale, quotites, totaux
 * de presence, date limite du delai de six mois, majorites atteintes), jamais suppose ; ce
 * qui se saisit et pilote une condition du modele (oui / non, type d'auteur...) est exige :
 * absent, la generation est refusee et la donnee nommee (une condition sans valeur
 * choisirait sans bruit la branche "sinon").
 */
public final class SeanceCorpusVarsBuilder {

    public static final String CONVOCATION_AGO = "CONVOCATION_AGO_SARL";
    public static final String CONVOCATION_AGE = "CONVOCATION_AGE_SARL";
    public static final String SECONDE_CONVOCATION = "SECONDE_CONVOCATION_ASSEMBLEE_SARL";
    public static final String FEUILLE_AGO = "FEUILLE_PRESENCE_AGO_SARL";
    public static final String FEUILLE_AGE = "FEUILLE_PRESENCE_AGE_SARL";

    static final String CONVOCATION_AG = "CONVOCATION_AG";
    static final String FEUILLE_PRESENCE_AG = "FEUILLE_PRESENCE_AG";

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    static final String PRESENT = "pr\u00e9sent";
    static final String REPRESENTE = "repr\u00e9sent\u00e9";
    static final String ABSENT = "absent";

    private SeanceCorpusVarsBuilder() {}

    // ------------------------------------------------------------------
    // Choix du modele
    // ------------------------------------------------------------------

    /**
     * Modele du corpus a rendre pour un document de seance, ou {@code null} si la donnee
     * ne permet pas de le choisir (type d'assemblee non indique, SARL AU).
     */
    public static String modele(String templateCode, Map<String, Object> payload) {
        Map<String, Object> p = payload == null ? Map.of() : payload;
        if (associeUnique(p)) return null;
        String type = typeAssemblee(p);
        if (CONVOCATION_AG.equals(templateCode)) {
            if (secondeConvocation(p)) return SECONDE_CONVOCATION;
            if (type == null) return null;
            return "ordinaire".equals(type) ? CONVOCATION_AGO : CONVOCATION_AGE;
        }
        if (FEUILLE_PRESENCE_AG.equals(templateCode)) {
            if (type == null) return null;
            return "ordinaire".equals(type) ? FEUILLE_AGO : FEUILLE_AGE;
        }
        return null;
    }

    /** SARL a associe unique : forme de la societe (base d'abord), sinon drapeau du formulaire. */
    static boolean associeUnique(Map<String, Object> p) {
        Map<String, Object> societe = asMap(p.get("societe"));
        Object forme = first0(societe.get("formeJuridique"), societe.get("forme"), p.get("formeJuridique"));
        if (forme != null) {
            String f = norm(forme).replace('_', ' ');
            return f.contains(" au") || f.endsWith("au") || f.contains("unique");
        }
        Object flag = p.get("associeUnique");
        return flag instanceof Boolean b ? b : "oui".equals(norm(flag)) || "true".equals(norm(flag));
    }

    /** Type d'assemblee saisi ("ordinaire", "extraordinaire", "mixte"), ou null : jamais suppose. */
    static String typeAssemblee(Map<String, Object> p) {
        String s = norm(asMap(p.get("seance")).get("type"));
        if (s.startsWith("extra")) return "extraordinaire";
        if (s.startsWith("mixt")) return "mixte";
        if (s.startsWith("ordin")) return "ordinaire";
        return null;
    }

    static boolean secondeConvocation(Map<String, Object> p) {
        String s = norm(first0(asMap(p.get("convocation")).get("rang"), p.get("convocationRang")));
        return s.startsWith("deux") || s.equals("2") || s.contains("second");
    }

    // ------------------------------------------------------------------
    // Variables
    // ------------------------------------------------------------------

    /**
     * Variables du modele du corpus choisi pour {@code templateCode} (CONVOCATION_AG ou
     * FEUILLE_PRESENCE_AG). Refuse la SARL AU et toute donnee de condition absente.
     */
    public static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> p = payload == null ? Map.of() : payload;
        if (associeUnique(p)) {
            throw new IllegalArgumentException(CONVOCATION_AG.equals(templateCode)
                    ? "En SARL \u00e0 associ\u00e9 unique, il n'y a pas de convocation : l'associ\u00e9 unique "
                            + "d\u00e9cide seul (article 76 de la loi n\u00b0 5-96). Aucun mod\u00e8le du cabinet n'existe pour ce cas."
                    : "En SARL \u00e0 associ\u00e9 unique, il n'y a pas de feuille de pr\u00e9sence : l'associ\u00e9 unique "
                            + "d\u00e9cide seul et consigne sa d\u00e9cision au registre (article 76 de la loi n\u00b0 5-96).");
        }
        List<DonneeManquante> manquantes = new ArrayList<>();
        String type = typeAssemblee(p);
        if (type == null) {
            throw new GenerationRefuseeException(templateCode,
                    "Type d'assembl\u00e9e non indiqu\u00e9 (ordinaire, extraordinaire ou mixte).",
                    List.of(new DonneeManquante("ASSEMBLEE_TYPE", "Type d'assembl\u00e9e (ordinaire, extraordinaire ou mixte)",
                            "Choix du mod\u00e8le de " + (CONVOCATION_AG.equals(templateCode) ? "convocation" : "feuille de pr\u00e9sence"))));
        }
        String modele = modele(templateCode, p);
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(modele, p));

        Map<String, Object> societe = asMap(p.get("societe"));
        Map<String, Object> seance = asMap(p.get("seance"));
        Map<String, Object> convoc = asMap(p.get("convocation"));
        Map<String, Object> comptes = asMap(p.get("comptes"));
        Map<String, Object> exercice = asMap(p.get("exercice"));
        boolean feuille = FEUILLE_AGO.equals(modele) || FEUILLE_AGE.equals(modele);
        boolean seconde = SECONDE_CONVOCATION.equals(modele);

        // ---- Societe (fiche) ----
        put(v, "FORME_JURIDIQUE", formeImprimee(societe));
        BigDecimal capital = toBig(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        BigDecimal nbParts = toBig(societe.get("nombreParts"));
        put(v, "VALEUR_NOMINALE_PART", montant(valeurNominale(societe, capital, nbParts)));

        // ---- Seance et convocation ----
        put(v, "ASSEMBLEE_TYPE", type);
        put(v, "CONVOCATION_AUTEUR_QUALITE", str(convoc.get("auteurQualite")));
        String secondeOuiNon = secondeConvocation(p) ? "oui" : "non";
        put(v, "CONVOCATION_SECONDE", secondeOuiNon);
        String datePremiere = dateStr(first0(convoc.get("dateAgPremiere"), p.get("dateAgPremiere")));
        put(v, "ASSEMBLEE_PREMIERE_DATE", datePremiere);
        put(v, "ASSEMBLEE_DATE_PREMIERE", datePremiere);
        // Seule une lettre AGO porte la mention de seconde convocation en interne ; une seconde
        // convocation est rendue par son propre modele.
        if (CONVOCATION_AGO.equals(modele)) put(v, "CONVOCATION_SECONDE", "non");

        // ---- Associes (une lettre, ou une ligne de la feuille, par associe) ----
        List<Map<String, Object>> associes = asListOfMaps(p.get("associes"));
        boolean voieElectronique = norm(convoc.get("mode")).contains("electronique");
        List<Map<String, Object>> rows = new ArrayList<>();
        long partsPresentes = 0;
        int presents = 0;
        int representes = 0;
        List<String> presencesInconnues = new ArrayList<>();
        for (Map<String, Object> a : associes) {
            if (a == null || a.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            boolean morale = "MORALE".equalsIgnoreCase(String.valueOf(a.get("typePersonne")));
            String nom = associeLabel(a, morale);
            it.put("ASSOCIE_TYPE", morale ? "personne morale" : "personne physique");
            it.put("ASSOCIE_NOM", nom);
            it.put("ASSOCIE_ADRESSE", strOr(first0(a.get("adresse"), a.get("siege")), ""));
            BigDecimal parts = toBig(first0(a.get("nombreParts"), a.get("partsChiffres")));
            it.put("ASSOCIE_NOMBRE_PARTS", parts == null ? "" : parts.toPlainString());
            it.put("ASSOCIE_PARTS_QUOTITE", quotite(parts, nbParts));
            if (morale) {
                it.put("ASSOCIE_DENOMINATION", strOr(a.get("denomination"), ""));
                it.put("ASSOCIE_FORME", strOr(first0(a.get("forme"), a.get("formeJuridique")), ""));
                it.put("ASSOCIE_SIEGE", strOr(first0(a.get("siege"), a.get("adresse")), ""));
                it.put("ASSOCIE_RC_VILLE", strOr(first0(a.get("rcVille"), a.get("villeGreffe")), ""));
                it.put("ASSOCIE_RC_NUMERO", strOr(first0(a.get("rcNumero"), a.get("rc")), ""));
                it.put("ASSOCIE_REPRESENTANT_NOM", strOr(first0(a.get("representantNom"), a.get("representantLegal")), ""));
                it.put("ASSOCIE_REPRESENTANT_QUALITE", strOr(first0(a.get("representantQualite"), a.get("repQualite")), ""));
            } else {
                it.put("ASSOCIE_PIECE_TYPE", strOr(a.get("pieceType"), ""));
                it.put("ASSOCIE_PIECE_NUMERO", strOr(first0(a.get("pieceNumero"), a.get("cin"), a.get("cinNumero")), ""));
            }
            if (voieElectronique) {
                it.put("ASSOCIE_ADRESSE_ELECTRONIQUE", strOr(first0(a.get("adresseElectronique"), a.get("email")), ""));
            }
            String presence = presence(a.get("presence"));
            it.put("ASSOCIE_PRESENCE", presence);
            it.put("ASSOCIE_MANDATAIRE_NOM", REPRESENTE.equals(presence) ? strOr(a.get("mandataireNom"), "") : "\u2014");
            it.put("ASSOCIE_POUVOIR_DATE", REPRESENTE.equals(presence) ? dateStr(a.get("pouvoirDate")) : "");
            if (presence.isEmpty()) presencesInconnues.add(nom);
            if (PRESENT.equals(presence)) presents++;
            if (REPRESENTE.equals(presence)) representes++;
            if ((PRESENT.equals(presence) || REPRESENTE.equals(presence)) && parts != null) {
                partsPresentes += parts.longValue();
            }
            rows.add(it);
        }
        v.put("ASSOCIES", rows);
        if ((feuille || seconde) && !presencesInconnues.isEmpty()) {
            // Feuille : presence du jour ; seconde convocation : presence a la premiere assemblee.
            for (String n : presencesInconnues) {
                manquantes.add(new DonneeManquante("ASSOCIE_PRESENCE",
                        "Pr\u00e9sence de " + n + (seconde ? " \u00e0 la premi\u00e8re assembl\u00e9e" : "")
                                + " (pr\u00e9sent, repr\u00e9sent\u00e9 ou absent)",
                        seconde ? "Parts pr\u00e9sentes \u00e0 la premi\u00e8re assembl\u00e9e" : "Feuille de pr\u00e9sence"));
            }
        }
        if (feuille || seconde) {
            // Meme ecriture que $NOMBRE_PARTS ("600 parts sur 1000"), sans separateur de milliers.
            put(v, "PARTS_PRESENTES_CHIFFRES", String.valueOf(partsPresentes));
            put(v, "PARTS_PRESENTES_LETTRES", FrenchNumberToLetters.numberToLetters(partsPresentes));
            put(v, "PARTS_PRESENTES_QUOTITE", quotite(BigDecimal.valueOf(partsPresentes), nbParts));
            put(v, "ASSOCIES_TOTAL_NOMBRE", String.valueOf(rows.size()));
            put(v, "ASSOCIES_PRESENTS_NOMBRE", String.valueOf(presents));
            put(v, "ASSOCIES_REPRESENTES_NOMBRE", String.valueOf(representes));
            if (nbParts != null && nbParts.signum() > 0) {
                BigDecimal pp = BigDecimal.valueOf(partsPresentes);
                // Article 74, alinea 1 : plus de la moitie des parts sociales.
                put(v, "MAJORITE_MOITIE_ATTEINTE", pp.multiply(BigDecimal.valueOf(2)).compareTo(nbParts) > 0 ? "oui" : "non");
                // Article 75 : au moins les trois quarts des parts sociales.
                put(v, "QUORUM_ATTEINT", pp.multiply(BigDecimal.valueOf(4))
                        .compareTo(nbParts.multiply(BigDecimal.valueOf(3))) >= 0 ? "oui" : "non");
            } else {
                manquantes.add(new DonneeManquante("NOMBRE_PARTS", "Nombre de parts sociales de la soci\u00e9t\u00e9",
                        "Quorum et majorit\u00e9"));
            }
        }

        // ---- Documents communiques avec la convocation ----
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Object d : asList(p.get("documentsJoints"))) {
            String s = d instanceof Map<?, ?> m ? strOr(asMap(m).get("libelle"), "") : (d == null ? "" : String.valueOf(d).trim());
            if (s.isBlank()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("DOCUMENT_COMMUNIQUE_LIBELLE", s);
            docs.add(it);
        }
        v.put("DOCUMENTS_COMMUNIQUES", docs);

        // ---- Resolutions : "PREMIERE RESOLUTION -- intitule" ----
        Object res = v.get("RESOLUTIONS");
        if (res instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> r = asMap(m);
                    r.put("RESOLUTION_NUMERO", String.valueOf(r.get("RESOLUTION_ORDINAL")) + " R\u00c9SOLUTION");
                }
            }
        }

        // ---- Exigences propres a chaque modele ----
        boolean convocation = !feuille;
        if (convocation) {
            exigerOuiNon(v, manquantes, convoc.get("pouvoirJoint"), "CONVOCATION_POUVOIR_JOINT",
                    "Formule de pouvoir jointe \u00e0 la convocation (oui ou non)", "Repr\u00e9sentation");
        }
        if (CONVOCATION_AGE.equals(modele) || seconde) {
            if (docs.isEmpty()) {
                manquantes.add(new DonneeManquante("DOCUMENT_COMMUNIQUE_LIBELLE",
                        "Documents communiqu\u00e9s avec la convocation (au moins un)",
                        "Vous trouverez ci-joint les documents suivants"));
            }
        }
        if (CONVOCATION_AGO.equals(modele)) {
            exigerType(v, manquantes, convoc.get("auteurType"));
            if ("mandataire de justice".equals(v.get("CONVOCATION_AUTEUR_TYPE"))) {
                put(v, "CONVOCATION_ORDONNANCE_DATE", dateStr(convoc.get("ordonnanceDate")));
            }
            LocalDate clos = toDate(exercice.get("dateCloture"));
            put(v, "EXERCICE_CLOS_DATE", dateStr(exercice.get("dateCloture")));
            put(v, "EXERCICE_DEBUT", dateStr(exercice.get("dateDebut")));
            // Article 70, alinea 1 : six mois a compter de la cloture de l'exercice.
            put(v, "ASSEMBLEE_DELAI_SIX_MOIS_DATE_LIMITE", clos == null ? "" : clos.plusMonths(6).format(DATE_FR));
            put(v, "COMMUNICATION_DATE_ENVOI", dateStr(convoc.get("communicationDateEnvoi")));
            comptes(v, manquantes, comptes, true);
            Object r = v.get("RESOLUTIONS");
            if (!(r instanceof List<?> rl) || rl.isEmpty()) {
                manquantes.add(new DonneeManquante("RESOLUTION_TEXTE",
                        "Texte des r\u00e9solutions propos\u00e9es (au moins une r\u00e9solution)",
                        "Annexe : texte des r\u00e9solutions propos\u00e9es"));
            }
        }
        if (seconde) {
            put(v, "CONVOCATION_MODE_PREMIERE", str(convoc.get("modePremiere")));
            put(v, "CONVOCATION_DATE_PREMIERE", dateStr(convoc.get("datePremiere")));
            put(v, "ASSEMBLEE_HEURE_PREMIERE", str(convoc.get("heureAgPremiere")));
            put(v, "ASSEMBLEE_LIEU_PREMIERE", str(convoc.get("lieuAgPremiere")));
            // Article 75 : l'ordre du jour d'une assemblee extraordinaire (ou mixte) porte des
            // decisions emportant modification des statuts.
            put(v, "DECISION_RELEVE_ARTICLE_75", "ordinaire".equals(type) ? "non" : "oui");
            String clause = exigerOuiNon(v, manquantes, convoc.get("clauseContraireStatuts"),
                    "STATUTS_CLAUSE_CONTRAIRE_SECONDE_CONVOCATION",
                    "Clause des statuts contraire \u00e0 la r\u00e8gle de la seconde convocation (oui ou non)",
                    "Fondement de la seconde convocation");
            if ("oui".equals(clause)) put(v, "STATUTS_CLAUSE_CONTRAIRE_TEXTE", str(convoc.get("clauseContraireTexte")));
            exigerOuiNon(v, manquantes, convoc.get("associeInteressePresent"), "ASSOCIE_INTERESSE_PRESENT",
                    "Associ\u00e9 ou g\u00e9rant int\u00e9ress\u00e9 \u00e0 une convention soumise \u00e0 l'assembl\u00e9e (oui ou non)",
                    "Rappel de l'article 64");
            exigerOuiNon(v, manquantes, convoc.get("documentsJointsSeconde"), "DOCUMENTS_JOINTS_SECONDE_CONVOCATION",
                    "Documents joints de nouveau \u00e0 la lettre (oui ou non)", "Documents communiqu\u00e9s");
            exigerOuiNon(v, manquantes, convoc.get("pouvoirsPremiereValables"), "POUVOIRS_PREMIERE_ASSEMBLEE_VALABLES",
                    "Pouvoirs donn\u00e9s pour la premi\u00e8re assembl\u00e9e valables (oui ou non)", "Repr\u00e9sentation");
            Object r = v.get("RESOLUTIONS");
            if (!(r instanceof List<?> rl) || rl.isEmpty()) {
                manquantes.add(new DonneeManquante("RESOLUTION_RESULTAT",
                        "R\u00e9sultat des votes de la premi\u00e8re assembl\u00e9e (au moins une r\u00e9solution)",
                        "Le r\u00e9sultat des votes intervenus sur premi\u00e8re convocation"));
            }
        }
        if (FEUILLE_AGO.equals(modele)) {
            put(v, "EXERCICE_CLOS_DATE", dateStr(exercice.get("dateCloture")));
            comptes(v, manquantes, comptes, false);
        }

        if (!manquantes.isEmpty()) {
            StringBuilder sb = new StringBuilder("G\u00e9n\u00e9ration refus\u00e9e : des donn\u00e9es manquent. ");
            for (DonneeManquante m : manquantes) sb.append(m.libelle()).append(" ; ");
            sb.setLength(sb.length() - 3);
            throw new GenerationRefuseeException(templateCode, sb.append('.').toString(), manquantes);
        }
        return v;
    }

    /** Donnees du dossier des comptes (assemblee ordinaire annuelle). */
    private static void comptes(Map<String, Object> v, List<DonneeManquante> manquantes,
                                Map<String, Object> c, boolean convocation) {
        String cac = exigerOuiNon(v, manquantes, c.get("cacDesigne"), "CAC_DESIGNE",
                "Commissaire aux comptes en fonction (oui ou non)", "Ordre du jour et documents");
        if ("oui".equals(cac)) {
            put(v, "CAC_NOM", str(c.get("cacNom")));
            if (!convocation) put(v, "CAC_PRESENCE", str(c.get("cacPresence")));
        }
        exigerOuiNon(v, manquantes, c.get("conventionsReglementees"), "CONVENTIONS_REGLEMENTEES_EXISTE",
                "Conventions de l'article 64 conclues au titre de l'exercice (oui ou non)", "Ordre du jour");
        if (!convocation) return;
        String reserve = exigerOuiNon(v, manquantes, c.get("reserveLegale"), "RESERVE_LEGALE_APPLICABLE",
                "Dotation \u00e0 la r\u00e9serve l\u00e9gale due (oui ou non)", "Ordre du jour");
        if ("oui".equals(reserve)) {
            put(v, "RESERVE_LEGALE_DOTATION_TAUX", str(c.get("reserveLegaleTaux")));
            put(v, "RESERVE_LEGALE_PLAFOND", str(c.get("reserveLegalePlafond")));
        }
        exigerOuiNon(v, manquantes, c.get("dividendes"), "DIVIDENDES_DISTRIBUTION_PROPOSEE",
                "Distribution de dividendes propos\u00e9e (oui ou non)", "Ordre du jour");
        exigerOuiNon(v, manquantes, c.get("reserves"), "RESERVES_DISTRIBUTION_PROPOSEE",
                "Distribution de r\u00e9serves propos\u00e9e (oui ou non)", "Ordre du jour");
        exigerOuiNon(v, manquantes, c.get("quitusGerantVote"), "QUITUS_GERANT_PREND_PART_VOTE",
                "La g\u00e9rance associ\u00e9e prend part au vote du quitus (oui ou non)", "Majorit\u00e9");
    }

    private static void exigerType(Map<String, Object> v, List<DonneeManquante> manquantes, Object raw) {
        String s = norm(raw);
        String type = s.startsWith("gerant") ? "g\u00e9rant"
                : s.startsWith("commissaire") ? "commissaire aux comptes"
                : s.startsWith("mandataire") ? "mandataire de justice" : "";
        put(v, "CONVOCATION_AUTEUR_TYPE", type);
        if (type.isEmpty()) {
            manquantes.add(new DonneeManquante("CONVOCATION_AUTEUR_TYPE",
                    "Auteur de la convocation : g\u00e9rant, commissaire aux comptes ou mandataire de justice",
                    "En ma qualit\u00e9 de ..., j'ai l'honneur de vous convoquer"));
        }
    }

    /** Pose "oui" / "non" saisi, ou nomme la donnee : une condition sans valeur ne se suppose pas. */
    private static String exigerOuiNon(Map<String, Object> v, List<DonneeManquante> manquantes, Object raw,
                                       String variable, String libelle, String endroit) {
        String s = ouiNon(raw);
        put(v, variable, s);
        if (s.isEmpty()) manquantes.add(new DonneeManquante(variable, libelle, endroit));
        return s;
    }

    static String ouiNon(Object raw) {
        if (raw instanceof Boolean b) return b ? "oui" : "non";
        String s = norm(raw);
        if (s.equals("oui") || s.equals("true")) return "oui";
        if (s.equals("non") || s.equals("false")) return "non";
        return "";
    }

    // ------------------------------------------------------------------
    // Calculs
    // ------------------------------------------------------------------

    /** Forme imprimee dans l'en-tete ("PARACOSME, SARL au capital de ..."), depuis la fiche. */
    private static String formeImprimee(Map<String, Object> societe) {
        String f = norm(first0(societe.get("formeJuridique"), societe.get("forme")));
        return f.equals("sarl") ? "SARL" : "";
    }

    /** Valeur nominale : fiche, sinon capital / nombre de parts (division exacte seulement). */
    private static BigDecimal valeurNominale(Map<String, Object> societe, BigDecimal capital, BigDecimal nbParts) {
        BigDecimal fiche = toBig(first0(societe.get("valeurNominalePart"), societe.get("valeurNominale")));
        if (fiche != null && fiche.signum() > 0) return fiche;
        if (capital == null || nbParts == null || nbParts.signum() <= 0) return null;
        try {
            return capital.divide(nbParts);
        } catch (ArithmeticException inexacte) {
            return null;
        }
    }

    /** Quotite du capital, en pourcentage ("60 %", "33,33 %"), ou "" si une donnee manque. */
    static String quotite(BigDecimal parts, BigDecimal total) {
        if (parts == null || total == null || total.signum() <= 0) return "";
        BigDecimal pct = parts.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return pct.toPlainString().replace('.', ',') + "\u00a0%";
    }

    private static String montant(BigDecimal b) {
        if (b == null) return "";
        BigDecimal s = b.stripTrailingZeros();
        if (s.scale() <= 0) return formatAmount(s.longValueExact());
        return s.toPlainString().replace('.', ',');
    }

    private static String formatAmount(long n) {
        return String.format(Locale.FRANCE, "%,d", n).replace('\u202f', '\u00a0');
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String presence(Object raw) {
        String s = norm(raw);
        if (s.startsWith("repr")) return REPRESENTE;
        if (s.startsWith("abs")) return ABSENT;
        if (s.startsWith("pr")) return PRESENT;
        return "";
    }

    private static String associeLabel(Map<String, Object> a, boolean morale) {
        if (morale) return strOr(a.get("denomination"), "");
        StringBuilder sb = new StringBuilder();
        for (Object o : new Object[]{civilite(a.get("civilite")), a.get("prenom"), a.get("nom")}) {
            String s = o == null ? "" : String.valueOf(o).trim();
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(s);
        }
        return sb.toString();
    }

    private static String civilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return raw == null ? "" : String.valueOf(raw);
    }

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
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

    static BigDecimal toBig(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = String.valueOf(o).trim().replace("\u00a0", "").replace("\u202f", "").replace(" ", "").replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String norm(Object raw) {
        if (raw == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(raw), java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String strOr(Object o, String fallback) {
        if (o == null) return fallback;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? fallback : s;
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

    private static List<Object> asList(Object o) {
        List<Object> out = new ArrayList<>();
        if (o instanceof List<?> l) out.addAll(l);
        return out;
    }
}
