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
 * Producteur des variables des 4 MODÈLES DÉTERMINISTES DU DIRECTEUR (2026-08) pour
 * le workflow CRÉATION : STATUTS_SARL, STATUTS_SARL_AU, ACTE_NOMINATION_GERANT,
 * ANNONCE_LEGALE.
 *
 * <p>Produit EXACTEMENT l'ensemble des variables du dictionnaire officiel (+ les 2
 * drapeaux dérivés {@code HAS_APPORT_NATURE} / {@code GERANCE_UNIQUE} qui remplacent
 * les 2 conditions en langage naturel des modèles) et les 4 boucles
 * {@code ASSOCIES}, {@code APPORTS_PAR_ASSOCIE}, {@code GERANTS}, {@code SIGNATAIRES}.
 *
 * <p>Isolé de {@link CreationSarlMapper} (dont les méthodes {@code fill*} legacy
 * restent inchangées car {@code ModificationMapper} en dépend). Les valeurs des
 * conditions sont normalisées EXACTEMENT comme attendu par les modèles
 * (« personne physique », « numéraire », « intégrale », « séparée avec plafond »,
 * « statutaire », …). Les scalaires « mono » (associé unique, 1er gérant, 1er apport)
 * sont aussi émis au niveau racine pour le rendu SARL AU / ACTE.
 */
public final class CreationDirecteurVarsBuilder {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);


    private CreationDirecteurVarsBuilder() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> societe = unwrap(asMap(safe.get("societe")));
        Map<String, Object> depot = asMap(safe.get("depot"));
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));
        if (associes.isEmpty()) {
            Map<String, Object> unique = asMap(safe.get("associeUnique"));
            if (!unique.isEmpty()) associes = List.of(unique);
        }
        List<Map<String, Object>> gerants = asListOfMaps(safe.get("gerants"));
        if (gerants.isEmpty()) {
            Map<String, Object> single = asMap(safe.get("gerant"));
            if (!single.isEmpty()) gerants = List.of(single);
        }
        List<Map<String, Object>> signataires = asListOfMaps(safe.get("signataires"));

        Map<String, Object> v = new LinkedHashMap<>();

        boolean isAu = resolveIsAu(templateCode, societe, associes);
        v.put("ASSOCIE_UNIQUE", isAu ? "oui" : "non");

        // ---- Société ----
        put(v, "DENOMINATION", str(societe.get("denomination")));
        put(v, "OBJET_SOCIAL", first(str(societe.get("objetSocial")), str(societe.get("activiteSociete"))));
        put(v, "SIEGE_SOCIAL", first(str(societe.get("siegeSocial")), str(societe.get("adresseSiege"))));
        put(v, "DUREE_SOCIETE", first(str(societe.get("dureeSociete")), str(societe.get("dureeAnnees")), "99"));
        put(v, "VILLE_GREFFE", first(str(societe.get("villeGreffe")), str(societe.get("tribunalCompetent")),
                str(societe.get("rcVille"))));

        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        put(v, "CAPITAL_CHIFFRES", capital == null ? "" : formatAmount(capital));
        put(v, "CAPITAL_LETTRES", capital == null ? "" : FrenchNumberToLetters.numberToLetters(capital));
        Long nbParts = toLong(societe.get("nombreParts"));
        put(v, "NOMBRE_PARTS", nbParts == null ? "" : String.valueOf(nbParts));
        Long valPart = toLong(first0(societe.get("valeurNominalePart"), societe.get("valeurPart")));
        put(v, "VALEUR_NOMINALE_PART", valPart == null ? "" : formatAmount(valPart));

        // ---- Libération / dépôt ----
        put(v, "MODE_LIBERATION", normLiberation(societe.get("modeLiberation")));
        String depotBloque = normOuiNon(first0(societe.get("depotFondsBloque"),
                depot.get("fondsBloque"), depot.get("depotFondsBloque")), "non");
        put(v, "DEPOT_FONDS_BLOQUE", depotBloque);
        put(v, "BANQUE_DEPOSITAIRE", first(str(societe.get("banqueDepositaire")), str(depot.get("banque")),
                str(depot.get("depositaireFonds"))));
        put(v, "COMPTE_BANCAIRE_NUMERO", first(str(societe.get("compteBancaireNumero")),
                str(depot.get("compteBancaireNumero")), str(depot.get("numero"))));

        // ---- Signature sociale (art. 15) ----
        put(v, "MODE_SIGNATURE", normSignature(societe.get("modeSignature")));
        Long plafond = toLong(first0(societe.get("signaturePlafondChiffres"), societe.get("signaturePlafond")));
        put(v, "SIGNATURE_PLAFOND_CHIFFRES", plafond == null ? "" : formatAmount(plafond));
        put(v, "SIGNATURE_PLAFOND_LETTRES", plafond == null ? "" : FrenchNumberToLetters.numberToLetters(plafond));
        String mandataire = normOuiNon(societe.get("signatureMandataire"), "non");
        put(v, "SIGNATURE_MANDATAIRE", mandataire);
        put(v, "MANDATAIRE_NOM", str(societe.get("mandataireNom")));
        put(v, "MANDATAIRE_ACTE_DELEGATION", str(societe.get("mandataireActeDelegation")));
        // Signature des documents administratifs (art. 15) — « identique » / « signature seule ».
        put(v, "MODE_SIGNATURE_ADMIN", normSignatureAdmin(societe.get("modeSignatureAdmin")));
        // Accord grammatical des signataires (« un masculin »/« un féminin »/« plusieurs »).
        List<Map<String, Object>> effSignataires =
                (signataires != null && !signataires.isEmpty()) ? signataires : gerants;
        put(v, "SIGNATAIRE_ACCORD", accord(effSignataires));

        // ---- Gérance ----
        put(v, "GERANT_MODE_DESIGNATION", resolveGerantMode(societe.get("gerantModeDesignation"), gerants));
        // Accord grammatical de la gérance (« un masculin »/« un féminin »/« plusieurs »).
        put(v, "GERANT_ACCORD", accord(gerants));
        // B.4 (Phase 2) — le modèle écrit « pour une durée de $DUREE_GERANCE » (texte
        // directeur INTOUCHABLE) ; on garantit donc une valeur grammaticale après « de ».
        // Défaut : durée concrète en années (au lieu de « illimitée » qui donnait
        // « pour une durée de illimitée »).
        // 2026-08-18 — DURÉE SCOPÉE PAR DOCUMENT.
        //
        // La boucle GERANTS est déjà filtrée par modèle (l'acte de nomination ne
        // liste que les gérants NON statutaires, cf. fix A10) mais la durée, elle,
        // restait globale. L'acte pouvait donc annoncer « pour une durée de 3 année(s)
        // pour M. ALAOUI et illimitée pour Mme BENJELLOUN : » puis ne lister que
        // Mme BENJELLOUN — une durée attribuée à quelqu'un qui n'y figure pas.
        //
        // On agrège donc sur LES SEULS gérants que ce document nomme. Effet de bord
        // heureux : les mandats ne divergent plus qu'à l'intérieur d'un même groupe,
        // cas rare, où l'énumération devient légitimement informative.
        put(v, "DUREE_GERANCE", dureeGeranceScopee(
                isActeNomination(templateCode) ? nonStatutaires(gerants) : gerants,
                societe.get("dureeGerance")));
        put(v, "LIMITATION_POUVOIRS", str(societe.get("limitationPouvoirs")));

        // ---- Contrôle / comptes ----
        put(v, "COMMISSAIRE_COMPTES_NOM", str(societe.get("commissaireComptesNom")));
        put(v, "DUREE_MANDAT_CAC", first(str(societe.get("dureeMandatCac")), "3"));
        put(v, "EXERCICE_DEBUT", first(str(societe.get("exerciceDebut")), "1er janvier"));
        put(v, "EXERCICE_FIN", first(str(societe.get("exerciceFin")), "31 décembre"));
        LocalDate dateConstit = toDate(first0(societe.get("dateSignature"), societe.get("dateConstitution")));
        put(v, "PREMIER_EXERCICE_CLOTURE", first(str(societe.get("premierExerciceCloture")),
                dateConstit != null ? ("31 décembre " + dateConstit.getYear()) : "31 décembre"));

        // ---- Apports (agrégats) ----
        boolean hasNature = false;
        for (Map<String, Object> a : associes) {
            if ("nature".equals(apportType(a))) { hasNature = true; break; }
        }
        put(v, "HAS_APPORT_NATURE", hasNature ? "oui" : "non");
        put(v, "COMMISSAIRE_APPORTS_NOM", str(societe.get("commissaireApportsNom")));

        // ---- Constitution / signature ----
        put(v, "ENGAGEMENTS_MANDAT", str(societe.get("engagementsMandat")));
        put(v, "LIEU_SIGNATURE", first(str(societe.get("lieuSignature")), str(societe.get("villeSignature"))));
        put(v, "DATE_SIGNATURE", dateConstit != null ? dateConstit.format(DATE_FR)
                : first(str(societe.get("dateSignature")), ""));
        put(v, "NOMBRE_ORIGINAUX", first(str(societe.get("nombreOriginaux")), str(societe.get("nbExemplaires")), "6"));

        // ---- Acte de nomination du gérant ----
        put(v, "ARTICLE_DESIGNATION_STATUTS", first(str(societe.get("articleDesignationStatuts")),
                isAu ? "32" : "36"));
        put(v, "HEURE_ACTE", first(str(societe.get("heureActe")), "10 heures"));

        // ---- Publication (post-immatriculation) ----
        LocalDate dateActe = toDate(societe.get("dateActe"));
        put(v, "DATE_ACTE", dateActe != null ? dateActe.format(DATE_FR)
                : first(str(societe.get("dateActe")), dateConstit != null ? dateConstit.format(DATE_FR) : ""));
        put(v, "RC_NUMERO", str(societe.get("rcNumero")));
        LocalDate dateDepot = toDate(societe.get("dateDepotLegal"));
        put(v, "DATE_DEPOT_LEGAL", dateDepot != null ? dateDepot.format(DATE_FR)
                : str(societe.get("dateDepotLegal")));

        // ---- Boucles ----
        List<Map<String, Object>> associesLoop = buildAssociesLoop(associes, gerants, isAnnonce(templateCode));
        v.put("ASSOCIES", associesLoop);
        v.put("APPORTS_PAR_ASSOCIE", buildApportsLoop(associes));
        // Fix A10 (2026-08-16) — l'ACTE de nomination est l'acte SÉPARÉ qui nomme les
        // gérants NON désignés dans les statuts. Il itérait la gérance entière : un
        // gérant déjà nommé statutairement s'y retrouvait « nommé » une seconde fois,
        // par un acte qui n'a pas vocation à le viser. On restreint donc la boucle
        // aux non statutaires pour ce seul modèle (statuts / annonce inchangés).
        List<Map<String, Object>> gerantsLoop =
                buildGerantsLoop(isActeNomination(templateCode) ? nonStatutaires(gerants) : gerants);
        v.put("GERANTS", gerantsLoop);
        v.put("SIGNATAIRES", buildSignatairesLoop(signataires, gerants));

        // ---- Scalaires « mono » racine (SARL AU + ACTE : associé/gérant/apport uniques) ----
        if (!associesLoop.isEmpty()) copyItemToRoot(v, associesLoop.get(0));
        if (!gerantsLoop.isEmpty()) copyItemToRoot(v, gerantsLoop.get(0));
        List<Map<String, Object>> apportsLoop = (List<Map<String, Object>>) v.get("APPORTS_PAR_ASSOCIE");
        if (!apportsLoop.isEmpty()) copyItemToRoot(v, apportsLoop.get(0));

        return v;
    }

    // ------------------------------------------------------------------
    // Boucles
    // ------------------------------------------------------------------

    /**
     * Fix A11 (2026-08-16) — {@code $ASSOCIE_NOM} porte DEUX contrats selon le modèle :
     * <ul>
     *   <li>statuts / acte : « $ASSOCIE_CIVILITE $ASSOCIE_PRENOM $ASSOCIE_NOM » et
     *       « $ASSOCIE_NOM $ASSOCIE_PRENOM … parts » → le SEUL nom de famille ;</li>
     *   <li>annonce légale : « $ASSOCIE_NOM : $ASSOCIE_NOMBRE_PARTS parts » → seule
     *       occurrence du nom dans la phrase, elle doit donc porter l'identité
     *       COMPLÈTE. On lisait « EL AMRANI : 700 parts » au lieu de
     *       « Youssef EL AMRANI : 700 parts ».</li>
     * </ul>
     */
    private static List<Map<String, Object>> buildAssociesLoop(List<Map<String, Object>> associes,
                                                               List<Map<String, Object>> gerants,
                                                               boolean nomComplet) {
        List<Map<String, Object>> out = new ArrayList<>();
        long cursor = 1;
        for (Map<String, Object> a : associes) {
            if (a == null || a.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            boolean morale = isMorale(a);
            it.put("ASSOCIE_TYPE", morale ? "personne morale" : "personne physique");
            // Accord grammatical de l'associé (PM -> « féminin » ; PP -> selon civilité/genre).
            it.put("ASSOCIE_GENRE", morale ? "féminin" : personGenre(a));
            if (morale) {
                it.put("ASSOCIE_NOM", str(a.get("denomination")));
                it.put("ASSOCIE_DENOMINATION", strOr(a.get("denomination"), ""));
                it.put("ASSOCIE_FORME", strOr(first0(a.get("forme"), a.get("formeJuridique")), "SARL"));
                it.put("ASSOCIE_CAPITAL", capitalDH(first0(a.get("capital"), a.get("capitalEntite"))));
                it.put("ASSOCIE_SIEGE", strOr(a.get("siege"), ""));
                it.put("ASSOCIE_RC_VILLE", strOr(first0(a.get("rcVille"), a.get("villeGreffe")), ""));
                it.put("ASSOCIE_RC_NUMERO", strOr(first0(a.get("rcNumero"), a.get("rc")), ""));
                it.put("ASSOCIE_REPRESENTANT_NOM", strOr(first0(a.get("representantNom"),
                        a.get("representantLegal")), ""));
                it.put("ASSOCIE_REPRESENTANT_QUALITE", strOr(first0(a.get("representantQualite"),
                        a.get("repQualite")), "Gérant"));
                // Champs PP émis vides pour couverture homogène.
                putEmptyPpFields(it);
            } else {
                it.put("ASSOCIE_CIVILITE", normCivilite(a.get("civilite")));
                it.put("ASSOCIE_PRENOM", strOr(a.get("prenom"), ""));
                it.put("ASSOCIE_NOM", nomComplet
                        ? join(" ", strOr(a.get("prenom"), ""), strOr(a.get("nom"), ""))
                        : strOr(a.get("nom"), ""));
                it.put("ASSOCIE_NATIONALITE", strOr(a.get("nationalite"), "marocaine"));
                it.put("ASSOCIE_DATE_NAISSANCE", dateStr(first0(a.get("dateNaissance"), a.get("naissance"))));
                it.put("ASSOCIE_LIEU_NAISSANCE", strOr(a.get("lieuNaissance"), ""));
                it.put("ASSOCIE_ADRESSE", strOr(a.get("adresse"), ""));
                it.put("ASSOCIE_PIECE_TYPE", strOr(a.get("pieceType"), "CIN"));
                it.put("ASSOCIE_PIECE_NUMERO", strOr(first0(a.get("pieceNumero"), a.get("cin"), a.get("cinNumero")), ""));
                putEmptyPmFields(it);
            }
            Long parts = toLong(first0(a.get("nombreParts"), a.get("partsChiffres")));
            long n = parts == null ? 0 : parts;
            it.put("ASSOCIE_NOMBRE_PARTS", parts == null ? "" : String.valueOf(parts));
            it.put("ASSOCIE_PARTS_DE", n > 0 ? String.valueOf(cursor) : "");
            it.put("ASSOCIE_PARTS_A", n > 0 ? String.valueOf(cursor + n - 1) : "");
            if (n > 0) cursor += n;
            it.put("ASSOCIE_EST_GERANT", estGerant(a, gerants) ? "oui" : "non");
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildApportsLoop(List<Map<String, Object>> associes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : associes) {
            if (a == null || a.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            String type = apportType(a);
            it.put("APPORT_ASSOCIE_LIBELLE", associeLabel(a));
            it.put("APPORT_TYPE", type);
            Long num = toLong(first0(a.get("apportNumeraire"), a.get("apportNumeraireChiffres"), a.get("montantApport")));
            it.put("APPORT_NUMERAIRE_CHIFFRES", num == null ? "" : formatAmount(num));
            it.put("APPORT_NUMERAIRE_LETTRES", num == null ? "" : FrenchNumberToLetters.numberToLetters(num));
            it.put("APPORT_NATURE_DESCRIPTION", strOr(a.get("apportNatureDescription"), ""));
            // Lot L3 : la charge utile du serveur (ConstructeurChargeUtileCreation) porte la
            // valeur sous `apportNature` ; ce nom n'etait pas lu : blanc silencieux aux statuts.
            Long natVal = toLong(first0(a.get("apportNatureValeur"), a.get("apportNatureValeurChiffres"), a.get("apportNature")));
            it.put("APPORT_NATURE_VALEUR_CHIFFRES", natVal == null ? "" : formatAmount(natVal));
            it.put("APPORT_NATURE_VALEUR_LETTRES", natVal == null ? "" : FrenchNumberToLetters.numberToLetters(natVal));
            it.put("APPORT_INDUSTRIE_DESCRIPTION", strOr(a.get("apportIndustrieDescription"), ""));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildGerantsLoop(List<Map<String, Object>> gerants) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g == null || g.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("GERANT_CIVILITE", normCivilite(g.get("civilite")));
            it.put("GERANT_PRENOM", strOr(g.get("prenom"), ""));
            it.put("GERANT_NOM", strOr(g.get("nom"), ""));
            it.put("GERANT_NATIONALITE", strOr(g.get("nationalite"), "marocaine"));
            it.put("GERANT_ADRESSE", strOr(g.get("adresse"), ""));
            it.put("GERANT_PIECE_TYPE", strOr(g.get("pieceType"), "CIN"));
            it.put("GERANT_PIECE_NUMERO", strOr(first0(g.get("pieceNumero"), g.get("cin"), g.get("cinNumero")), ""));
            it.put("GERANT_DATE_NAISSANCE", dateStr(first0(g.get("dateNaissance"), g.get("naissance"))));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildSignatairesLoop(List<Map<String, Object>> signataires,
                                                                  List<Map<String, Object>> gerants) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (signataires != null && !signataires.isEmpty()) {
            for (Map<String, Object> s : signataires) {
                if (s == null || s.isEmpty()) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("SIGNATAIRE_NOM", first(str(s.get("nom")), personLabel(s)));
                it.put("SIGNATAIRE_QUALITE", strOr(s.get("qualite"), "gérant"));
                out.add(it);
            }
            if (!out.isEmpty()) return out;
        }
        // Défaut : chaque gérant est signataire, qualité « gérant ».
        for (Map<String, Object> g : gerants) {
            if (g == null || g.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("SIGNATAIRE_NOM", personLabel(g));
            it.put("SIGNATAIRE_QUALITE", "gérant");
            out.add(it);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Normalisation des valeurs de condition (libellés EXACTS des modèles)
    // ------------------------------------------------------------------

    private static String normLiberation(Object raw) {
        String s = norm(raw);
        if (s.startsWith("part")) return "partielle";
        return "intégrale";
    }

    private static String normSignature(Object raw) {
        String s = norm(raw);
        if (s.contains("plafond")) return "séparée avec plafond";
        if (s.startsWith("conjoint")) return "conjointe";
        return "séparée";
    }

    private static String resolveGerantMode(Object raw, List<Map<String, Object>> gerants) {
        String s = norm(raw);
        if (s.contains("non")) return "non statutaire";
        if (s.startsWith("statut")) return "statutaire";
        // Dérivation : si un gérant au moins n'est PAS statutaire -> non statutaire.
        for (Map<String, Object> g : gerants) {
            Object st = g == null ? null : first0(g.get("isStatutaire"), g.get("statutaire"));
            if (st != null && (Boolean.FALSE.equals(st) || "false".equalsIgnoreCase(String.valueOf(st)))) {
                return "non statutaire";
            }
        }
        return "statutaire";
    }

    private static String apportType(Map<String, Object> a) {
        if (a == null) return "numéraire";
        String s = norm(first0(a.get("apportType"), a.get("typeApport")));
        if (s.startsWith("natur")) return "nature";
        if (s.startsWith("indus")) return "industrie";
        if (s.startsWith("numer") || s.startsWith("numér")) return "numéraire";
        // Dérivation par présence de champs.
        if (toLong(first0(a.get("apportNatureValeur"), a.get("apportNatureValeurChiffres"), a.get("apportNature"))) != null
                || strOr(a.get("apportNatureDescription"), "").length() > 0) return "nature";
        if (strOr(a.get("apportIndustrieDescription"), "").length() > 0) return "industrie";
        return "numéraire";
    }

    private static String normOuiNon(Object raw, String def) {
        if (raw == null) return def;
        if (raw instanceof Boolean b) return b ? "oui" : "non";
        String s = norm(raw);
        if (s.isEmpty()) return def;
        if (s.startsWith("o") || s.equals("true") || s.equals("1") || s.equals("yes")) return "oui";
        if (s.startsWith("n") || s.equals("false") || s.equals("0")) return "non";
        return def;
    }

    /** Signature des documents administratifs (art. 15) : « identique » (défaut) ou « signature seule ». */
    private static String normSignatureAdmin(Object raw) {
        String s = norm(raw);
        if (s.contains("seul")) return "signature seule";
        return "identique";
    }

    /** Genre grammatical d'une personne : « féminin » / « masculin » (défaut masculin). */
    private static String personGenre(Map<String, Object> p) {
        if (p == null) return "masculin";
        String g = norm(first0(p.get("genre"), p.get("sexe")));
        if (g.startsWith("f")) return "féminin";
        if (g.startsWith("m") && !g.startsWith("mme") && !g.startsWith("mlle")) {
            // « masculin » explicite (éviter de confondre avec la civilité "M." déjà gérée).
            if (g.equals("masculin") || g.equals("homme") || g.equals("h")) return "masculin";
        }
        String civ = normCivilite(p.get("civilite"));
        return ("Mme".equals(civ) || "Mlle".equals(civ)) ? "féminin" : "masculin";
    }

    /**
     * Accord grammatical d'une liste de personnes (gérants, signataires) :
     * « un masculin » / « un féminin » / « plusieurs ». Liste vide -> « un masculin ».
     */
    private static String accord(List<Map<String, Object>> people) {
        List<Map<String, Object>> nonEmpty = new ArrayList<>();
        if (people != null) {
            for (Map<String, Object> p : people) if (p != null && !p.isEmpty()) nonEmpty.add(p);
        }
        if (nonEmpty.size() > 1) return "plusieurs";
        if (nonEmpty.isEmpty()) return "un masculin";
        return "féminin".equals(personGenre(nonEmpty.get(0))) ? "un féminin" : "un masculin";
    }

    private static String normCivilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return raw == null ? "" : String.valueOf(raw);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean resolveIsAu(String templateCode, Map<String, Object> societe,
                                       List<Map<String, Object>> associes) {
        if (templateCode != null && templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU")) return true;
        String forme = norm(societe.get("formeJuridique"));
        if (forme.contains("au")) return true;
        return associes.size() == 1;
    }

    private static boolean isMorale(Map<String, Object> a) {
        return "MORALE".equals(String.valueOf(first(str(a.get("typePersonne")), "PHYSIQUE"))
                .toUpperCase(Locale.ROOT));
    }

    private static boolean estGerant(Map<String, Object> a, List<Map<String, Object>> gerants) {
        Object flag = first0(a.get("estGerant"), a.get("isGerant"));
        if (flag != null) return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag))
                || "oui".equalsIgnoreCase(String.valueOf(flag));
        String label = personLabel(a);
        if (label.isBlank()) return false;
        for (Map<String, Object> g : gerants) {
            if (personLabel(g).equalsIgnoreCase(label)) return true;
        }
        return false;
    }

    private static String associeLabel(Map<String, Object> a) {
        if (isMorale(a)) return strOr(a.get("denomination"), "");
        return personLabel(a);
    }

    private static String personLabel(Map<String, Object> p) {
        if (p == null) return "";
        String civ = normCivilite(p.get("civilite"));
        String prenom = strOr(p.get("prenom"), "");
        String nom = strOr(p.get("nom"), "");
        return join(" ", civ, prenom, nom);
    }

    private static void copyItemToRoot(Map<String, Object> v, Map<String, Object> item) {
        for (Map.Entry<String, Object> e : item.entrySet()) {
            v.putIfAbsent(e.getKey(), e.getValue());
            // Les scalaires racine doivent refléter le 1er élément (associé/gérant/apport
            // unique) même si une clé homonyme existe déjà via un autre item.
            v.put(e.getKey(), e.getValue());
        }
    }

    private static void putEmptyPmFields(Map<String, Object> it) {
        for (String k : new String[]{"ASSOCIE_DENOMINATION", "ASSOCIE_FORME", "ASSOCIE_CAPITAL",
                "ASSOCIE_SIEGE", "ASSOCIE_RC_VILLE", "ASSOCIE_RC_NUMERO", "ASSOCIE_REPRESENTANT_NOM",
                "ASSOCIE_REPRESENTANT_QUALITE"}) {
            it.putIfAbsent(k, "");
        }
    }

    private static void putEmptyPpFields(Map<String, Object> it) {
        for (String k : new String[]{"ASSOCIE_CIVILITE", "ASSOCIE_PRENOM", "ASSOCIE_NATIONALITE",
                "ASSOCIE_DATE_NAISSANCE", "ASSOCIE_LIEU_NAISSANCE", "ASSOCIE_ADRESSE",
                "ASSOCIE_PIECE_TYPE", "ASSOCIE_PIECE_NUMERO"}) {
            it.putIfAbsent(k, "");
        }
    }

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static String amountOrEmpty(Object o) {
        Long l = toLong(o);
        return l == null ? strOr(o, "") : formatAmount(l);
    }

    /** B.4 — capital d'un associé PM avec unité normalisée (« 500 000 DH »). */
    private static String capitalDH(Object o) {
        Long l = toLong(o);
        if (l != null) return formatAmount(l) + " DH";
        String s = strOr(o, "");
        // n'ajoute « DH » que si une unité n'est pas déjà présente.
        if (s.isBlank() || s.toLowerCase(Locale.ROOT).matches(".*(dh|dirham|mad).*")) return s;
        return s + " DH";
    }

    /**
     * B.4 — valeur de {@code DUREE_GERANCE} grammaticale après « pour une durée de »
     * (texte modèle intouchable). Valeur explicite conservée ; défaut = durée concrète.
     */
    /**
     * Durée de la gérance pour LES GÉRANTS D'UN DOCUMENT donné (fix 2026-08-18).
     *
     * <p>Chaque gérant porte désormais son propre mandat ({@code dureeMandat}), la
     * gérance étant saisie par dirigeant. On agrège sur le seul groupe que le modèle
     * appelant nomme :
     * <ul>
     *   <li>mandats IDENTIQUES → la valeur commune (rendu inchangé, cas courant) ;</li>
     *   <li>mandats DIFFÉRENTS → énumération nominative, qui se lit correctement
     *       après « pour une durée de » et reste vraie.</li>
     * </ul>
     *
     * @param repli valeur agrégée par le front, seule source des payloads antérieurs
     *              à la gérance par dirigeant
     */
    private static String dureeGeranceScopee(List<Map<String, Object>> gerants, Object repli) {
        List<String> durees = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g == null || g.isEmpty()) continue;
            String d = strOr(g.get("dureeMandat"), "").trim();
            if (d.isEmpty()) continue;
            durees.add(d);
            labels.add(gerantLabel(g));
        }
        // Aucun mandat porté par les gérants : payload antérieur -> on garde le repli.
        if (durees.isEmpty()) return dureeGerance(repli);

        boolean uniforme = durees.stream().allMatch(d -> d.equals(durees.get(0)));
        if (uniforme) return dureeGerance(durees.get(0));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < durees.size(); i++) {
            if (i > 0) sb.append(i == durees.size() - 1 ? " et " : ", ");
            sb.append(dureeGerance(durees.get(i))).append(" pour ").append(labels.get(i));
        }
        return sb.toString();
    }

    /** Nom lisible d'un gérant, pour lui attribuer un mandat dans un acte. */
    private static String gerantLabel(Map<String, Object> g) {
        if (isMorale(g)) return strOr(g.get("denomination"), "");
        return join(" ", normCivilite(g.get("civilite")), strOr(g.get("prenom"), ""),
                strOr(g.get("nom"), ""));
    }

    /**
     * Fix A10 (2026-08-16) — durée du mandat de gérance.
     *
     * <p>Le modèle écrit « nommé(s) … <b>pour une durée de</b> $DUREE_GERANCE » : la
     * valeur doit donc se lire APRÈS « de ». On renvoyait la saisie telle quelle, si
     * bien que le choix « Illimitée (jusqu'à révocation) » ne trouvait pas de repli et
     * retombait sur « 99 années » — l'acte annonçait un mandat de 99 ans là où
     * l'utilisateur avait choisi un mandat sans terme. On traduit donc les libellés
     * d'illimité en une formule grammaticale et juridiquement exacte.
     */
    private static String dureeGerance(Object raw) {
        String s = strOr(raw, "").trim();
        if (s.isBlank()) return "99 années";
        String n = norm(s);
        if (n.startsWith("illimit") || n.contains("indetermin") || n.contains("revocation")
                || n.contains("sans terme") || n.contains("sans limitation")) {
            // Se lit « pour une durée de la société, soit jusqu'à révocation ».
            return "la société, soit jusqu'à révocation";
        }
        return s;
    }

    /** {@code true} si le modèle est l'ACTE DE NOMINATION séparé (cf. fix A10). */
    private static boolean isActeNomination(String templateCode) {
        return templateCode != null
                && templateCode.toUpperCase(Locale.ROOT).contains("ACTE_NOMINATION");
    }

    /** {@code true} si le modèle est l'ANNONCE LÉGALE de création (cf. fix A11). */
    private static boolean isAnnonce(String templateCode) {
        return templateCode != null
                && templateCode.toUpperCase(Locale.ROOT).contains("ANNONCE");
    }

    /**
     * Gérants NON désignés dans les statuts — seuls visés par l'acte de nomination
     * séparé (fix A10). Un gérant sans information de statut est traité comme non
     * statutaire : l'acte séparé est justement le cas par défaut quand la gérance
     * n'a pas été arrêtée dans les statuts. Si le filtre ne laisse personne (toute la
     * gérance est statutaire), on retourne la liste entière plutôt qu'un acte vide.
     */
    private static List<Map<String, Object>> nonStatutaires(List<Map<String, Object>> gerants) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g == null || g.isEmpty()) continue;
            Object st = first0(g.get("isStatutaire"), g.get("statutaire"), g.get("designationMode"));
            String s = norm(st);
            boolean statutaire = "true".equals(s) || s.startsWith("statut");
            if (!statutaire) out.add(g);
        }
        return out.isEmpty() ? gerants : out;
    }

    private static String formatAmount(long n) {
        // Séparateur de milliers par espace insécable fine (typographie FR).
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
    private static List<Map<String, Object>> asListOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<String, Object> societe) {
        Object d = societe.get("denomination");
        if (d instanceof Map<?, ?> nested && nested.containsKey("denomination")) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : nested.entrySet()) merged.put(String.valueOf(e.getKey()), e.getValue());
            merged.putAll(societe);
            merged.put("denomination", nested.get("denomination"));
            return merged;
        }
        return societe;
    }
}
