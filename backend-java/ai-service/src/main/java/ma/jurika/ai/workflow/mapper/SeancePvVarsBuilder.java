package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.format.FrenchNumberToLetters;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Producteur des variables des 4 PV de SÉANCE (incident d'assemblée) du directeur :
 * {@code PV_DEFAUT_QUORUM_SARL(/_AU)} et {@code PV_IRREGULARITE_CONVOCATION_SARL(/_AU)}.
 *
 * <p>Ces PV sont <b>transverses</b> : ils décrivent une séance d'assemblée générale et
 * sont donc générables dans TOUT workflow qui tient une AG (modification, dissolution,
 * liquidation, etc.). Ce builder est <b>réutilisable</b> : il ne connaît que le contrat
 * de données de séance (§3 de la cartographie), pas le workflow appelant.
 *
 * <p>Produit exactement les variables {@code $NU} des modèles + les boucles
 * {@code ASSOCIES} (présence/quorum, SARL pluripersonnelle), {@code ORDRE_DU_JOUR} et
 * {@code RESOLUTIONS}. En SARL AU, l'associé unique est décrit hors boucle (variables
 * {@code $ASSOCIE_*} racine) avec la boucle {@code GERANTS}, sans quorum.
 */
public final class SeancePvVarsBuilder {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private SeancePvVarsBuilder() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = templateCode != null && templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU");
        boolean isIrreg = templateCode != null
                && templateCode.toUpperCase(Locale.ROOT).contains("IRREGULARITE");

        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> seance = asMap(safe.get("seance"));
        Map<String, Object> convoc = asMap(safe.get("convocation"));
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));
        List<Map<String, Object>> gerants = asListOfMaps(safe.get("gerants"));
        List<Map<String, Object>> resolutions = asListOfMaps(safe.get("resolutions"));
        List<Object> ordreDuJour = asList(safe.get("ordreDuJour"));

        Map<String, Object> v = new LinkedHashMap<>();

        // ---- Société (depuis le dossier) ----
        put(v, "DENOMINATION", str(societe.get("denomination")));
        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        put(v, "CAPITAL_CHIFFRES", capital == null ? "" : formatAmount(capital));
        put(v, "CAPITAL_LETTRES", capital == null ? "" : FrenchNumberToLetters.numberToLetters(capital));
        put(v, "SIEGE_SOCIAL", first(str(societe.get("siegeSocial")), str(societe.get("adresseSiege"))));
        Long nbParts = toLong(societe.get("nombreParts"));
        put(v, "NOMBRE_PARTS", nbParts == null ? "" : String.valueOf(nbParts));
        put(v, "RC_NUMERO", first(str(societe.get("rcNumero")), str(societe.get("rc"))));
        put(v, "VILLE_GREFFE", first(str(societe.get("villeGreffe")), str(societe.get("rcVille"))));

        // ---- Séance ----
        put(v, "ASSEMBLEE_TYPE", normAssembleeType(seance.get("type")));
        put(v, "ASSEMBLEE_DATE", dateStr(first0(seance.get("date"), seance.get("assembleeDate"))));
        put(v, "ASSEMBLEE_HEURE", str(seance.get("heure")));
        put(v, "ASSEMBLEE_LIEU", str(seance.get("lieu")));
        put(v, "HEURE_CLOTURE", str(seance.get("heureCloture")));
        put(v, "PRESIDENT_SEANCE_NOM", str(seance.get("presidentNom")));
        put(v, "PRESIDENT_SEANCE_QUALITE", first(str(seance.get("presidentQualite")), "gérant"));
        String secretairePresent = normOuiNon(seance.get("secretairePresent"), "non");
        put(v, "SECRETAIRE_PRESENT", secretairePresent);
        put(v, "SECRETAIRE_SEANCE_NOM", str(seance.get("secretaireNom")));

        // ---- Convocation ----
        put(v, "CONVOCATION_AUTEUR", first(str(convoc.get("auteur")), "la gérance"));
        put(v, "CONVOCATION_DATE", dateStr(convoc.get("date")));
        put(v, "CONVOCATION_MODE", first(str(convoc.get("mode")), "lettre recommandée"));
        if (isIrreg) {
            put(v, "IRREGULARITE_NATURE", str(convoc.get("irregulariteNature")));
        }

        // ---- 2e convocation / suite ----
        Map<String, Object> seconde = asMap(safe.get("secondeAssemblee"));
        put(v, "DEUXIEME_ASSEMBLEE_DATE", dateStr(seconde.get("date")));
        put(v, "DEUXIEME_ASSEMBLEE_HEURE", str(seconde.get("heure")));
        put(v, "DEUXIEME_ASSEMBLEE_LIEU", str(seconde.get("lieu")));
        put(v, "SUITE_ASSEMBLEE", normSuite(safe.get("suiteAssemblee")));

        // ---- Double nommage de séance (modèles Convocation / Feuille) + notions nouvelles ----
        // Les modèles directeur ne sont pas unifiés sur le nommage : on produit LES DEUX
        // variantes pour chaque notion afin que chaque modèle reçoive le nom qu'il attend.
        put(v, "AG_TYPE", normAssembleeType(seance.get("type")));
        put(v, "DATE_AG", dateStr(first0(seance.get("date"), seance.get("assembleeDate"))));
        put(v, "HEURE_AG", str(seance.get("heure")));
        put(v, "LIEU_AG", str(seance.get("lieu")));
        put(v, "PRESIDENT_NOM", str(seance.get("presidentNom")));
        put(v, "PRESIDENT_QUALITE", first(str(seance.get("presidentQualite")), "gérant"));
        Integer annee = yearOf(first0(seance.get("date"), seance.get("assembleeDate")));
        put(v, "ANNEE_LETTRES", annee == null ? "" : FrenchNumberToLetters.numberToLetters(annee));

        // Convocation : miroir de nommage + variables propres à la lettre.
        put(v, "DATE_CONVOCATION", dateStr(convoc.get("date")));
        put(v, "CONVOCATION_RANG", normRang(first0(convoc.get("rang"), safe.get("convocationRang"))));
        put(v, "DATE_AG_PREMIERE", dateStr(first0(convoc.get("dateAgPremiere"),
                safe.get("dateAgPremiere"), seance.get("datePremiere"))));
        put(v, "LIEU_SIGNATURE", first(str(convoc.get("lieuSignature")), str(safe.get("lieuSignature")),
                str(societe.get("villeGreffe")), str(societe.get("rcVille"))));

        // Associé unique (« oui »/« non ») — pilote SARL AU vs SARL dans les modèles unifiés.
        put(v, "ASSOCIE_UNIQUE", resolveAssocieUnique(safe, societe, isAu));

        // Gérant signataire (convocation / feuille) : 1er gérant ou champ direct.
        put(v, "GERANT_NOM", resolveGerantNom(safe, gerants, seance));

        // Documents joints à la convocation (boucle DOCUMENTS_JOINTS).
        v.put("DOCUMENTS_JOINTS", buildDocumentsJoints(asList(safe.get("documentsJoints"))));

        // ---- Ordre du jour + résolutions (boucles, double nommage) ----
        v.put("ORDRE_DU_JOUR", buildOrdreDuJour(ordreDuJour));
        v.put("POINTS_ODJ", buildPointsOdj(ordreDuJour));
        v.put("RESOLUTIONS", buildResolutions(resolutions));

        if (isAu) {
            // Associé unique hors boucle + gérants.
            Map<String, Object> unique = associes.isEmpty()
                    ? asMap(safe.get("associeUnique")) : associes.get(0);
            putAssocieScalaires(v, unique);
            v.put("GERANTS", buildGerantsLoop(gerants));
            // Totaux de présence (SARL AU : l'associé unique détient la totalité).
            Long up = toLong(first0(unique.get("nombreParts"), societe.get("nombreParts")));
            put(v, "NB_ASSOCIES_PRESENTS", "1");
            put(v, "NOMBRE_PARTS_PRESENTES", up == null ? "" : formatAmount(up));
            put(v, "NOMBRE_VOIX_PRESENTES", up == null ? "" : formatAmount(up));
            put(v, "PARTS_PRESENTES_CHIFFRES", up == null ? "" : formatAmount(up));
            put(v, "PARTS_PRESENTES_LETTRES",
                    up == null ? "" : FrenchNumberToLetters.numberToLetters(up));
        } else {
            // Présence / quorum : boucle ASSOCIES + parts / voix présentes.
            long partsPresentes = 0;
            long voixPresentes = 0;
            int nbPresents = 0;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> a : associes) {
                if (a == null || a.isEmpty()) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                boolean morale = isMorale(a);
                it.put("ASSOCIE_TYPE", morale ? "personne morale" : "personne physique");
                it.put("ASSOCIE_NOM", associeLabel(a));
                it.put("ASSOCIE_DENOMINATION", strOr(a.get("denomination"), ""));
                it.put("ASSOCIE_ADRESSE", strOr(first0(a.get("adresse"), a.get("siege")), ""));
                it.put("ASSOCIE_REPRESENTANT_NOM", strOr(first0(a.get("representantNom"),
                        a.get("representantLegal")), ""));
                String presence = normPresence(a.get("presence"));
                it.put("ASSOCIE_PRESENCE", presence);
                // Lot L3 : la colonne « mandataire » ne se lit que pour un associe represente.
                // Present ou absent, il n'y a pas de mandataire : « — » le dit (donnee derivee
                // de la presence) au lieu d'un blanc, desormais traite comme une donnee manquante.
                it.put("ASSOCIE_MANDATAIRE_NOM", "repr\u00e9sent\u00e9".equals(presence)
                        ? strOr(a.get("mandataireNom"), "") : "\u2014");
                Long parts = toLong(first0(a.get("nombreParts"), a.get("partsChiffres")));
                it.put("ASSOCIE_NOMBRE_PARTS", parts == null ? "" : String.valueOf(parts));
                // 1 part = 1 voix (art. 5-96) sauf override explicite.
                Long voix = toLong(first0(a.get("nombreVoix"), a.get("voix")));
                if (voix == null) voix = parts;
                it.put("ASSOCIE_NOMBRE_VOIX", voix == null ? "" : String.valueOf(voix));
                boolean present = !"absent".equals(presence);
                if (present) {
                    nbPresents++;
                    if (parts != null) partsPresentes += parts;
                    if (voix != null) voixPresentes += voix;
                }
                rows.add(it);
            }
            v.put("ASSOCIES", rows);
            Long override = toLong(safe.get("partsPresentes"));
            long pp = override != null ? override : partsPresentes;
            Long voixOverride = toLong(safe.get("voixPresentes"));
            long vp = voixOverride != null ? voixOverride : voixPresentes;
            put(v, "PARTS_PRESENTES_CHIFFRES", pp == 0 ? "" : formatAmount(pp));
            put(v, "PARTS_PRESENTES_LETTRES", pp == 0 ? "" : FrenchNumberToLetters.numberToLetters(pp));
            put(v, "NOMBRE_PARTS_PRESENTES", pp == 0 ? "" : formatAmount(pp));
            put(v, "NOMBRE_VOIX_PRESENTES", vp == 0 ? "" : formatAmount(vp));
            put(v, "NB_ASSOCIES_PRESENTS", String.valueOf(nbPresents));
        }
        return v;
    }

    // ------------------------------------------------------------------
    // Boucles
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> buildOrdreDuJour(List<Object> points) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (points != null) {
            for (Object p : points) {
                String s = p == null ? "" : (p instanceof Map<?, ?> m
                        ? strOr(((Map<String, Object>) castMap(m)).get("point"), "") : String.valueOf(p));
                if (s.isBlank()) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("POINT_ORDRE_DU_JOUR", s);
                out.add(it);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> buildResolutions(List<Map<String, Object>> resolutions) {
        List<Map<String, Object>> out = new ArrayList<>();
        long n = 1;
        for (Map<String, Object> r : resolutions) {
            if (r == null || r.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            String intitule = strOr(r.get("intitule"), "");
            // Schéma « PV incident » (numéro / intitulé / texte / voix).
            it.put("RESOLUTION_NUMERO", first(str(r.get("numero")), String.valueOf(n)));
            it.put("RESOLUTION_INTITULE", intitule);
            it.put("RESOLUTION_TEXTE", strOr(r.get("texte"), ""));
            it.put("RESOLUTION_VOIX_POUR", strOr(r.get("voixPour"), ""));
            it.put("RESOLUTION_VOIX_CONTRE", strOr(r.get("voixContre"), ""));
            it.put("RESOLUTION_ABSTENTIONS", strOr(r.get("abstentions"), ""));
            it.put("RESOLUTION_RESULTAT", first(str(r.get("resultat")), "adoptée"));
            // Schéma « PV AG » (objet / ordinal / type) — double nommage.
            it.put("RESOLUTION_OBJET", first(str(r.get("objet")), intitule));
            it.put("RESOLUTION_ORDINAL", first(str(r.get("ordinal")), ordinalFrFeminine(n)));
            it.put("RESOLUTION_TYPE", strOr(r.get("type"), ""));
            out.add(it);
            n++;
        }
        return out;
    }

    /** Boucle POINTS_ODJ (2e nommage de l'ordre du jour) : $POINT_ODJ_LIBELLE. */
    private static List<Map<String, Object>> buildPointsOdj(List<Object> points) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (points != null) {
            for (Object p : points) {
                String s = pointLibelle(p);
                if (s.isBlank()) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("POINT_ODJ_LIBELLE", s);
                out.add(it);
            }
        }
        return out;
    }

    /** Boucle DOCUMENTS_JOINTS (convocation) : $DOCUMENT_JOINT_LIBELLE. */
    private static List<Map<String, Object>> buildDocumentsJoints(List<Object> docs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (docs != null) {
            for (Object d : docs) {
                String s = d == null ? "" : (d instanceof Map<?, ?> m
                        ? strOr(castMap(m).get("libelle"), "") : String.valueOf(d));
                if (s.isBlank()) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("DOCUMENT_JOINT_LIBELLE", s);
                out.add(it);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String pointLibelle(Object p) {
        if (p == null) return "";
        if (p instanceof Map<?, ?> m) return strOr(((Map<String, Object>) m).get("point"), "");
        return String.valueOf(p);
    }

    private static List<Map<String, Object>> buildGerantsLoop(List<Map<String, Object>> gerants) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g == null || g.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("GERANT_CIVILITE", normCivilite(g.get("civilite")));
            it.put("GERANT_PRENOM", strOr(g.get("prenom"), ""));
            it.put("GERANT_NOM", strOr(g.get("nom"), ""));
            out.add(it);
        }
        return out;
    }

    /** Associé unique (SARL AU) décrit hors boucle : variables $ASSOCIE_* racine. */
    private static void putAssocieScalaires(Map<String, Object> v, Map<String, Object> a) {
        boolean morale = isMorale(a);
        put(v, "ASSOCIE_TYPE", morale ? "personne morale" : "personne physique");
        // Fix M5 (2026-08-16) — DEUX contrats coexistent pour $ASSOCIE_NOM :
        //  - en BOUCLE (PV SARL, feuille de présence) le modèle écrit « $ASSOCIE_NOM,
        //    propriétaire de … » : il attend le libellé COMPLET (civilité + prénom + nom) ;
        //  - en SCALAIRE (comparution SARL AU, statuts, acte) le modèle écrit
        //    « $ASSOCIE_CIVILITE $ASSOCIE_PRENOM $ASSOCIE_NOM » : il attend le SEUL NOM.
        // On posait ici le libellé complet dans les deux cas, d'où la comparution
        // « M. Salma M. Salma BENJELLOUN » (civilité + prénom rendus deux fois).
        put(v, "ASSOCIE_NOM", morale ? strOr(a.get("denomination"), "") : strOr(a.get("nom"), ""));
        if (morale) {
            put(v, "ASSOCIE_DENOMINATION", strOr(a.get("denomination"), ""));
            put(v, "ASSOCIE_FORME", strOr(first0(a.get("forme"), a.get("formeJuridique")), "SARL"));
            put(v, "ASSOCIE_CAPITAL", strOr(first0(a.get("capital"), a.get("capitalEntite")), ""));
            put(v, "ASSOCIE_SIEGE", strOr(a.get("siege"), ""));
            put(v, "ASSOCIE_RC_VILLE", strOr(first0(a.get("rcVille"), a.get("villeGreffe")), ""));
            put(v, "ASSOCIE_RC_NUMERO", strOr(first0(a.get("rcNumero"), a.get("rc")), ""));
            put(v, "ASSOCIE_REPRESENTANT_NOM", strOr(first0(a.get("representantNom"),
                    a.get("representantLegal")), ""));
            put(v, "ASSOCIE_REPRESENTANT_QUALITE", strOr(first0(a.get("representantQualite"),
                    a.get("repQualite")), "Gérant"));
        } else {
            put(v, "ASSOCIE_CIVILITE", normCivilite(a.get("civilite")));
            put(v, "ASSOCIE_PRENOM", strOr(a.get("prenom"), ""));
            put(v, "ASSOCIE_NATIONALITE", strOr(a.get("nationalite"), "marocaine"));
            put(v, "ASSOCIE_ADRESSE", strOr(a.get("adresse"), ""));
            put(v, "ASSOCIE_PIECE_TYPE", strOr(a.get("pieceType"), "CIN"));
            put(v, "ASSOCIE_PIECE_NUMERO", strOr(first0(a.get("pieceNumero"), a.get("cin"),
                    a.get("cinNumero")), ""));
        }
    }

    // ------------------------------------------------------------------
    // Normalisations (libellés EXACTS des modèles)
    // ------------------------------------------------------------------

    private static String normAssembleeType(Object raw) {
        String s = norm(raw);
        if (s.startsWith("extra")) return "extraordinaire";
        if (s.startsWith("mixt")) return "mixte";
        return "ordinaire";
    }

    private static String normPresence(Object raw) {
        String s = norm(raw);
        if (s.startsWith("repr")) return "représenté";
        if (s.startsWith("abs")) return "absent";
        return "présent";
    }

    private static String normSuite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("regul") || s.startsWith("régul")) return "régularisation";
        return "renvoi";
    }

    /** Rang de convocation : « première » / « deuxième » (libellés EXACTS des modèles). */
    private static String normRang(Object raw) {
        String s = norm(raw);
        if (s.startsWith("deux") || s.equals("2") || s.contains("second")) return "deuxième";
        return "première";
    }

    /**
     * Résout {@code ASSOCIE_UNIQUE} (« oui »/« non ») : flag explicite du payload, sinon
     * forme juridique de la société (SARL AU), sinon suffixe du templateCode.
     */
    private static String resolveAssocieUnique(Map<String, Object> safe,
                                               Map<String, Object> societe, boolean isAu) {
        Object flag = first0(safe.get("associeUnique"), societe.get("associeUnique"));
        if (flag != null && !(flag instanceof Map)) {
            return normOuiNon(flag, isAu ? "oui" : "non");
        }
        String forme = norm(first0(societe.get("formeJuridique"), societe.get("forme"),
                safe.get("formeJuridique")));
        if (forme.contains("au") || forme.contains("unique")) return "oui";
        return isAu ? "oui" : "non";
    }

    /** Nom du gérant signataire : champ direct, sinon 1er gérant de la liste, sinon président. */
    private static String resolveGerantNom(Map<String, Object> safe,
                                           List<Map<String, Object>> gerants,
                                           Map<String, Object> seance) {
        String direct = first(str(safe.get("gerantNom")), str(seance.get("gerantNom")));
        if (!direct.isBlank()) return direct;
        if (gerants != null) {
            for (Map<String, Object> g : gerants) {
                if (g == null || g.isEmpty()) continue;
                String label = gerantLabel(g);
                if (!label.isBlank()) return label;
            }
        }
        return str(seance.get("presidentNom")) == null ? "" : str(seance.get("presidentNom"));
    }

    private static String gerantLabel(Map<String, Object> g) {
        return join(" ", normCivilite(g.get("civilite")), strOr(g.get("prenom"), ""),
                strOr(g.get("nom"), ""));
    }

    private static Integer yearOf(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.getYear();
        java.util.regex.Matcher m = YEAR_PATTERN.matcher(o == null ? "" : String.valueOf(o));
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private static final java.util.regex.Pattern YEAR_PATTERN =
            java.util.regex.Pattern.compile("(?:19|20)\\d{2}");

    /** Ordinal français au féminin (résolutions) : PREMIÈRE, DEUXIÈME, … */
    private static String ordinalFrFeminine(long n) {
        String[] words = {"", "PREMIÈRE", "DEUXIÈME", "TROISIÈME", "QUATRIÈME", "CINQUIÈME",
                "SIXIÈME", "SEPTIÈME", "HUITIÈME", "NEUVIÈME", "DIXIÈME", "ONZIÈME", "DOUZIÈME",
                "TREIZIÈME", "QUATORZIÈME", "QUINZIÈME", "SEIZIÈME", "DIX-SEPTIÈME",
                "DIX-HUITIÈME", "DIX-NEUVIÈME", "VINGTIÈME"};
        return (n >= 1 && n < words.length) ? words[(int) n] : (n + "ᵉ");
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

    private static String normCivilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return raw == null ? "" : String.valueOf(raw);
    }

    private static boolean isMorale(Map<String, Object> a) {
        return "MORALE".equals(String.valueOf(first(str(a.get("typePersonne")), "PHYSIQUE"))
                .toUpperCase(Locale.ROOT));
    }

    private static String associeLabel(Map<String, Object> a) {
        if (isMorale(a)) return strOr(a.get("denomination"), "");
        String civ = normCivilite(a.get("civilite"));
        return join(" ", civ, strOr(a.get("prenom"), ""), strOr(a.get("nom"), ""));
    }

    // ------------------------------------------------------------------
    // Helpers (coercion / format)
    // ------------------------------------------------------------------

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static String formatAmount(long n) {
        return String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ');
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

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
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
        return o == null ? null : String.valueOf(o);
    }

    private static String strOr(Object o, String fallback) {
        if (o == null) return fallback;
        String s = String.valueOf(o);
        return s.isBlank() ? fallback : s;
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
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
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
