package ma.jurika.ai.workflow.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ma.jurika.ai.workflow.mapper.LiquidationCorpusCommun.*;

/**
 * Lot L4d (E11d) : variables du proces-verbal de dissolution anticipee du corpus
 * ({@code PV_DISSOLUTION_ANTICIPEE_SARL} / {@code _SARL_AU}), qui remplace le PV unifie du
 * classpath ({@code PV_DISSOLUTION_LIQUIDATION_SARL(_AU)}, L2_correspondance_gabarits.md).
 *
 * <p>Sources : noyau de seance ({@link SeancePvVarsBuilder}), variables du corpus deja
 * produites pour l'avis de dissolution ({@link DissolutionAnnonceVarsBuilder#corpus}),
 * bloc {@code liquidateur} (etape 1 du parcours) et bloc {@code mentionsPv} (etape 2).
 *
 * <p>Regles : une donnee qui pilote une branche (quorum, commissaire aux comptes, quitus de
 * la gerance, effectif, remuneration, ancien gerant) est SAISIE, jamais supposee : absente,
 * la generation est refusee et la donnee nommee. Les donnees imprimees absentes restent
 * absentes : le controle de completude les nomme. Les donnees calculables le sont (date du
 * proces-verbal = date de la seance, valeur nominale, montants en lettres, heures).
 */
final class DissolutionPvCorpusVarsBuilder {

    static final String CORPUS_SARL = "PV_DISSOLUTION_ANTICIPEE_SARL";
    static final String CORPUS_SARL_AU = "PV_DISSOLUTION_ANTICIPEE_SARL_AU";

    private DissolutionPvCorpusVarsBuilder() {
    }

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean au = estAu(templateCode);
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> seance = asMap(safe.get("seance"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> dissolution = new LinkedHashMap<>(asMap(safe.get("dissolution")));
        Map<String, Object> mentions = asMap(safe.get("mentionsPv"));
        // Le choix du siege de la liquidation voyage dans le bloc liquidateur (etape 1).
        dissolution.putIfAbsent("siegeLiquidationIdentique", first0(liquidateur.get("siegeIdentique"),
                safe.get("siegeIdentique")));

        // Variables du corpus communes a l'avis : date de decision et d'effet, motif, terme,
        // siege de la liquidation, identite du liquidateur (physique ou morale), tribunal.
        DissolutionAnnonceVarsBuilder.corpus(v, safe, dissolution, liquidateur, seance);
        if ("personne morale".equals(v.get("LIQUIDATEUR_FORME"))) {
            putSi(v, "LIQUIDATEUR_CAPITAL_CHIFFRES", montant(liquidateur.get("capital")));
        }
        exigerMotif(templateCode, v);
        motifDetail(v, dissolution);

        seanceCorpus(templateCode, v, safe, au);
        identiteAssocieUnique(v, safe, au);
        mentionsActe(v, safe, mentions);

        liquidateurPhysique(v, liquidateur);
        mandatLiquidateur(templateCode, v, liquidateur);
        gerance(templateCode, v, safe, liquidateur, mentions);
        commissaireAuxComptes(templateCode, v, mentions, au);

        putSi(v, "ANNONCE_LEGALE_JOURNAL", str(mentions.get("annonceJournal")));
        String salaries = SaisieExigee.ouiNon(templateCode, mentions.get("salariesDixOuPlus"), "SALARIES_DIX_OU_PLUS",
                "La soci\u00e9t\u00e9 occupe-t-elle habituellement dix salari\u00e9s ou plus (oui ou non)");
        v.put("SALARIES_DIX_OU_PLUS", salaries);
        if ("oui".equals(salaries)) putSi(v, "SALARIES_NOMBRE", str(mentions.get("salariesNombre")));

        // Aucun point complementaire n'est saisi a l'ecran : la liste est vide.
        v.put("ORDRE_DU_JOUR_COMPLEMENTAIRE", List.of());
        v.put("ORDRE_DU_JOUR_COMPLEMENTAIRE_EXISTE", "non");
        return v;
    }

    /** Le motif pilote le rapport de la gerance et la premiere resolution : saisi, mot pour mot. */
    private static void exigerMotif(String templateCode, Map<String, Object> v) {
        if (v.get("DISSOLUTION_MOTIF") == null) {
            throw SaisieExigee.refus(templateCode, "DISSOLUTION_MOTIF",
                    "Nature de la dissolution (volont\u00e9 des associ\u00e9s, terme statutaire, r\u00e9alisation "
                            + "ou extinction de l'objet, article 86, cause statutaire ou autre motif)");
        }
    }

    /** Precisions propres a chaque motif (une seule branche du rapport de la gerance s'imprime). */
    private static void motifDetail(Map<String, Object> v, Map<String, Object> d) {
        String motif = str(v.get("DISSOLUTION_MOTIF"));
        switch (motif) {
            case "realisation_objet", "extinction_objet" ->
                    putSi(v, "DISSOLUTION_OBJET_EVENEMENT", str(d.get("objetEvenement")));
            case "situation_nette_art86" -> {
                putSi(v, "SITUATION_NETTE_MONTANT", montant(d.get("situationNetteMontant")));
                putSi(v, "ASSEMBLEE_APPROBATION_COMPTES_DATE", dateOuNull(d.get("approbationComptesDate")));
                putSi(v, "DISSOLUTION_ART86_MOTIFS_ECARTEMENT", str(d.get("art86MotifsEcartement")));
            }
            case "cause_statutaire" -> putSi(v, "DISSOLUTION_CLAUSE_STATUTAIRE_TEXTE", str(d.get("clauseTexte")));
            default -> {
                // volonte_associes, terme_statutaire : rien de plus ; autre : texte deja pose.
            }
        }
    }

    /**
     * Liquidateur personne physique : nationalite et date de naissance (troisieme resolution).
     * La personne morale est servie par {@link DissolutionAnnonceVarsBuilder#liquidateurCorpus}.
     */
    static void liquidateurPhysique(Map<String, Object> v, Map<String, Object> l) {
        if (!"personne physique".equals(v.get("LIQUIDATEUR_FORME"))) return;
        putSi(v, "LIQUIDATEUR_CIVILITE", civilite(l.get("civilite")));
        putSi(v, "LIQUIDATEUR_PRENOM", str(l.get("prenom")));
        putSi(v, "LIQUIDATEUR_NOM", str(l.get("nom")));
        putSi(v, "LIQUIDATEUR_ADRESSE", str(l.get("adresse")));
        putSi(v, "LIQUIDATEUR_NATIONALITE", str(l.get("nationalite")));
        putSi(v, "LIQUIDATEUR_DATE_NAISSANCE", dateOuNull(l.get("dateNaissance")));
    }

    /** Mandat du liquidateur : duree, remuneration, reddition des comptes, pouvoirs. */
    private static void mandatLiquidateur(String templateCode, Map<String, Object> v, Map<String, Object> l) {
        remuneration(templateCode, v, l);
        putSi(v, "LIQUIDATEUR_REDDITION_PERIODICITE", str(l.get("redditionPeriodicite")));
        List<Map<String, Object>> pouvoirs = new ArrayList<>();
        for (Object o : asList(l.get("pouvoirsComplementaires"))) {
            String p = str(o).trim();
            if (!p.isEmpty()) pouvoirs.add(new LinkedHashMap<>(Map.of("LIQUIDATEUR_POUVOIR_COMPLEMENTAIRE", p)));
        }
        v.put("LIQUIDATEUR_POUVOIRS_COMPLEMENTAIRES", pouvoirs);
        v.put("LIQUIDATEUR_POUVOIRS_COMPLEMENTAIRES_EXISTENT", pouvoirs.isEmpty() ? "non" : "oui");
        putSi(v, "LIQUIDATEUR_POUVOIRS_LIMITATION", str(l.get("pouvoirsLimitation")));
    }

    /**
     * Remuneration du liquidateur : applicable (oui / non), montant et modalites. Saisie ;
     * la valeur proposee avant le lot L4d ("exercees a titre gratuit") vaut "non".
     */
    static void remuneration(String templateCode, Map<String, Object> v, Map<String, Object> l) {
        Object applicable = l.get("remunerationApplicable");
        if (applicable == null && norm(l.get("remuneration")).contains("gratuit")) applicable = "non";
        String a = SaisieExigee.ouiNon(templateCode, applicable, "LIQUIDATEUR_REMUNERATION_APPLICABLE",
                "Le liquidateur est-il r\u00e9mun\u00e9r\u00e9 (oui ou non)");
        v.put("LIQUIDATEUR_REMUNERATION_APPLICABLE", a);
        if ("oui".equals(a)) {
            Long m = toLong(l.get("remunerationMontant"));
            if (m != null) {
                v.put("LIQUIDATEUR_REMUNERATION_MONTANT_CHIFFRES", formatAmount(m));
                v.put("LIQUIDATEUR_REMUNERATION_MONTANT_LETTRES", lettres(m));
            }
            putSi(v, "LIQUIDATEUR_REMUNERATION_MODALITES", str(l.get("remunerationModalites")));
        }
    }

    /**
     * Gerance : le gerant en fonction devient-il liquidateur (deduit de la designation),
     * le liquidateur a-t-il ete gerant (deduit, sinon saisi), quitus de la gerance (saisi).
     */
    private static void gerance(String templateCode, Map<String, Object> v, Map<String, Object> safe,
                                Map<String, Object> l, Map<String, Object> mentions) {
        List<Map<String, Object>> gerants = asListOfMaps(safe.get("gerants"));
        boolean physique = "personne physique".equals(v.get("LIQUIDATEUR_FORME"));
        boolean gerantDevient = physique && estGerantEnFonction(l, gerants);
        v.put("GERANT_DEVIENT_LIQUIDATEUR", gerantDevient ? "oui" : "non");
        String ancien;
        if (gerantDevient) ancien = "oui";
        // Une personne morale ne peut etre gerant d'une SARL (art. 62 de la loi 5-96).
        else if (!physique) ancien = "non";
        else ancien = SaisieExigee.ouiNon(templateCode, l.get("ancienGerant"), "LIQUIDATEUR_EST_ANCIEN_GERANT",
                "Le liquidateur a-t-il eu la qualit\u00e9 de g\u00e9rant de la soci\u00e9t\u00e9 (oui ou non)");
        v.put("LIQUIDATEUR_EST_ANCIEN_GERANT", ancien);

        String quitus = SaisieExigee.ouiNon(templateCode, mentions.get("quitusGerance"), "GERANT_QUITUS_DONNE",
                "Quitus donn\u00e9 \u00e0 la g\u00e9rance (oui ou non)");
        v.put("GERANT_QUITUS_DONNE", quitus);

        // $GERANT_NOM / $GERANT_QUALITE : le gerant appele aux fonctions de liquidateur, ou la
        // gerance qui recoit quitus. Les deux phrases visent la meme variable : avec plusieurs
        // gerants dont l'un devient liquidateur, l'une des deux serait fausse.
        if (gerantDevient && "oui".equals(quitus) && gerants.size() > 1) {
            throw SaisieExigee.refus(templateCode, "GERANT_NOM",
                    "Le mod\u00e8le ne nomme qu'un g\u00e9rant pour le quitus et pour la nomination comme liquidateur : "
                            + "avec plusieurs g\u00e9rants, r\u00e9servez le quitus de la g\u00e9rance");
        }
        if (gerantDevient) {
            v.put("GERANT_NOM", labelPhysique(l));
            putSi(v, "GERANT_QUALITE", qualiteGerant(List.of(l)));
        } else if (!gerants.isEmpty()) {
            List<String> noms = new ArrayList<>();
            for (Map<String, Object> g : gerants) {
                String n = labelPhysique(g);
                if (!n.isBlank()) noms.add(n);
            }
            putSi(v, "GERANT_NOM", joinEt(noms));
            putSi(v, "GERANT_QUALITE", qualiteGerant(gerants));
        }
    }

    /** Commissaire aux comptes : en fonction (saisi), present (SARL), sort du mandat. */
    static void commissaireAuxComptes(String templateCode, Map<String, Object> v, Map<String, Object> m, boolean au) {
        String existe = SaisieExigee.ouiNon(templateCode, m.get("cacExiste"), "CAC_EXISTE",
                "Un commissaire aux comptes est-il en fonction (oui ou non)");
        v.put("CAC_EXISTE", existe);
        if (!"oui".equals(existe)) return;
        putSi(v, "COMMISSAIRE_COMPTES_NOM", str(m.get("cacNom")));
        if (!au) {
            v.put("CAC_PRESENT", SaisieExigee.ouiNon(templateCode, m.get("cacPresent"), "CAC_PRESENT",
                    "Le commissaire aux comptes assiste-t-il \u00e0 la r\u00e9union (oui ou non)"));
        }
        String sort = str(m.get("cacMandatLiquidation")).trim();
        if (!sort.equals("poursuite") && !sort.equals("fin_de_mandat") && !sort.equals("reserve")) {
            throw SaisieExigee.refus(templateCode, "CAC_MANDAT_LIQUIDATION",
                    "Sort du mandat du commissaire aux comptes (poursuite, fin de mandat ou d\u00e9cision r\u00e9serv\u00e9e)");
        }
        v.put("CAC_MANDAT_LIQUIDATION", sort);
        if (sort.equals("fin_de_mandat")) putSi(v, "CAC_FIN_MANDAT_DATE_EFFET", dateOuNull(m.get("cacFinMandatDateEffet")));
    }

    /** Le gerant en fonction choisi comme liquidateur (option "gerant" ou meme nom). */
    private static boolean estGerantEnFonction(Map<String, Object> l, List<Map<String, Object>> gerants) {
        if (str(l.get("partieKey")).startsWith("gerant:")) return true;
        String cle = cleNom(l);
        if (cle.isEmpty()) return false;
        for (Map<String, Object> g : gerants) {
            if (cle.equals(cleNom(g))) return true;
        }
        return false;
    }

    private static String cleNom(Map<String, Object> p) {
        return (norm(p.get("prenom")) + " " + norm(p.get("nom"))).trim().replaceAll("\\s+", " ");
    }

    /** "gerant" / "gerante" (un gerant, selon la civilite), "cogerants" (plusieurs). */
    private static String qualiteGerant(List<Map<String, Object>> gerants) {
        if (gerants.size() > 1) return "cog\u00e9rants";
        String c = civilite(gerants.get(0).get("civilite"));
        if (c.equals("Mme") || c.equals("Mlle")) return "g\u00e9rante";
        if (c.equals("M.")) return "g\u00e9rant";
        return null;
    }

    /** Date du proces-verbal : la date de la seance (le PV est dresse et signe a la seance). */
    static LocalDate dateSeance(Map<String, Object> safe) {
        Map<String, Object> seance = asMap(safe.get("seance"));
        return toDate(first0(seance.get("date"), seance.get("assembleeDate")));
    }
}
