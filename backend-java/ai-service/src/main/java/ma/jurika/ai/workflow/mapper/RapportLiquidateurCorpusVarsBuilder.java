package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.GenerationRefuseeException.DonneeManquante;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static ma.jurika.ai.workflow.mapper.LiquidationCorpusCommun.*;

/**
 * Lot L5 (E4) : variables du rapport final du liquidateur du corpus
 * ({@code RAPPORT_LIQUIDATEUR_SARL} / {@code _SARL_AU}), qui remplace le rapport du classpath
 * {@code RAPPORT_LIQUIDATION_DIRECTEUR} (code logique de l'ecran conserve, decision D18 du lot
 * L4d).
 *
 * <p>Sources, sans double saisie : fiche de la societe et associes ; liquidateur et mandat
 * (enregistres a la dissolution) ; avis et PV de cloture (bloc {@code cloture} : date du compte
 * definitif, inscription au RC, journal d'annonces, retenue a la source, conservation des
 * documents) ; operations de liquidation saisies a l'ecran (bloc {@code rapportLiquidateur} :
 * onze tableaux, periode, rappel de la dissolution, diligences, signature).
 *
 * <p>Le modele n'additionne rien : tous les totaux, soldes, plus ou moins-values, le resultat
 * (boni ou mali) et la quote-part de chaque associe se CALCULENT ici a partir des lignes
 * saisies, avec l'arithmetique du compte de cloture (decision D27 du lot L4d) : actif realise -
 * passif regle - frais et honoraires (remuneration du liquidateur comprise) - impot ; remboursement
 * du nominal a concurrence du capital ; le solde est un boni ou un mali. Les totaux doivent
 * concorder avec ceux du PV de cloture (comptes finaux de l'etape 1, frais du PV) : un ecart est
 * refuse et nomme, jamais arrondi. Les donnees qui pilotent une branche (existence de chaque
 * tableau facultatif, liquidation prolongee, TVA, dix salaries ou plus, baux, ancien gerant,
 * retenue a la source) sont saisies ; absentes, la generation est refusee.
 */
final class RapportLiquidateurCorpusVarsBuilder {

    static final String CORPUS_SARL = "RAPPORT_LIQUIDATEUR_SARL";
    static final String CORPUS_SARL_AU = "RAPPORT_LIQUIDATEUR_SARL_AU";

    /** Le parcours de liquidation presente le rapport FINAL a l'assemblee (ou a la decision) de cloture. */
    static final String NATURE_FINALE = "final";

    private RapportLiquidateurCorpusVarsBuilder() {
    }

    /** Modele du corpus selon la forme de la societe (forme inconnue : SARL ; {@link #build} la nomme). */
    static String modele(Map<String, Object> payload) {
        return Boolean.TRUE.equals(RapportGestionVarsBuilder.associeUnique(payload)) ? CORPUS_SARL_AU : CORPUS_SARL;
    }

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Boolean associeUnique = RapportGestionVarsBuilder.associeUnique(safe);
        if (associeUnique == null) {
            throw SaisieExigee.refus(templateCode, "FORME_JURIDIQUE", "Forme juridique de la soci\u00e9t\u00e9 (SARL ou SARL AU)");
        }
        boolean au = associeUnique;
        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> c = asMap(safe.get("cloture"));
        Map<String, Object> r = asMap(safe.get("rapportLiquidateur"));
        Map<String, Object> mentions = asMap(safe.get("mentionsPv"));
        Map<String, Object> v = new LinkedHashMap<>();

        identite(v, safe, societe, mentions);
        v.put("RAPPORT_LIQUIDATEUR_NATURE", NATURE_FINALE);

        // Liquidateur (nomme a la dissolution), siege de la liquidation, mandat.
        DissolutionAnnonceVarsBuilder.liquidateurCorpus(v, liquidateur);
        if ("personne morale".equals(v.get("LIQUIDATEUR_FORME"))) {
            putSi(v, "LIQUIDATEUR_CAPITAL_CHIFFRES", montant(liquidateur.get("capital")));
        }
        DissolutionPvCorpusVarsBuilder.liquidateurPhysique(v, liquidateur);
        CloturePvCorpusVarsBuilder.siegeLiquidation(templateCode, v, liquidateur, societe, safe);
        putSi(v, "LIQUIDATEUR_MANDAT_DATE_EFFET", dateOuNull(liquidateur.get("mandatDateEffet")));
        putSi(v, "LIQUIDATEUR_DUREE_MANDAT", str(liquidateur.get("dureeMandat")));
        ancienGerant(templateCode, v, liquidateur);
        DissolutionPvCorpusVarsBuilder.remuneration(templateCode, v, liquidateur);

        // Rappel de la dissolution et de sa publicite.
        if (!au) putSi(v, "DISSOLUTION_ORGANE", str(r.get("dissolutionOrgane")));
        putSi(v, "DISSOLUTION_DATE_DECISION", dateOuNull(r.get("dissolutionDateDecision")));
        putSi(v, "DISSOLUTION_DATE_EFFET", dateOuNull(c.get("dissolutionDateEffet")));
        putSi(v, "DISSOLUTION_MOTIF_LIBELLE", str(r.get("dissolutionMotifLibelle")));
        putSi(v, "DISSOLUTION_DEPOT_GREFFE_DATE", dateOuNull(r.get("dissolutionDepotGreffeDate")));
        putSi(v, "DISSOLUTION_RC_INSCRIPTION_DATE", dateOuNull(c.get("dissolutionRcInscriptionDate")));
        putSi(v, "DISSOLUTION_RC_INSCRIPTION_NUMERO", str(r.get("dissolutionRcInscriptionNumero")));
        putSi(v, "ANNONCE_LEGALE_JOURNAL", str(c.get("annonceJournal")));
        putSi(v, "ANNONCE_LEGALE_DATE_PUBLICATION", dateOuNull(c.get("annoncePublicationDate")));
        putSi(v, "DISSOLUTION_BO_DATE_PARUTION", dateOuNull(r.get("dissolutionBoDateParution")));
        DissolutionAnnonceVarsBuilder.tribunal(v, safe);
        putSi(v, "INVENTAIRE_DATE_ARRETE", dateOuNull(r.get("inventaireDateArrete")));

        // Periode couverte ; les comptes presentes sont le compte definitif (date de l'avis de cloture).
        putSi(v, "RAPPORT_LIQUIDATEUR_PERIODE_DEBUT", dateOuNull(r.get("periodeDebut")));
        putSi(v, "RAPPORT_LIQUIDATEUR_PERIODE_FIN", dateOuNull(r.get("periodeFin")));
        putSi(v, "RAPPORT_LIQUIDATEUR_DATE_ARRETE_COMPTES", dateOuNull(c.get("compteDefinitifDate")));
        String prolongee = SaisieExigee.ouiNon(templateCode, r.get("prolongee"), "LIQUIDATION_PROLONGEE",
                "Les op\u00e9rations de liquidation se sont-elles prolong\u00e9es au-del\u00e0 de la p\u00e9riode pr\u00e9vue (oui ou non)");
        v.put("LIQUIDATION_PROLONGEE", prolongee);
        if ("oui".equals(prolongee)) {
            putSi(v, "LIQUIDATION_PROLONGATION_MOTIFS", str(r.get("prolongationMotifs")));
            putSi(v, "DECLARATION_RESULTAT_PROVISOIRE_DATE", dateOuNull(r.get("declarationResultatProvisoireDate")));
        }

        Calcul calcul = new Calcul(templateCode);
        realisations(v, r, calcul);
        apurements(v, r, calcul);
        creancesIrrecouvrables(templateCode, v, r, calcul);
        passifsContestes(templateCode, v, r, calcul);
        cessionsRestreintes(templateCode, v, r, calcul);
        frais(v, r, calcul);
        personnel(templateCode, v, r, calcul);
        contrats(templateCode, v, r, calcul);
        diligences(v, r, calcul);
        calcul.refuserSiManquantes("Op\u00e9rations de liquidation incompl\u00e8tes");

        fiscal(templateCode, v, r);
        resultat(templateCode, v, r, c, safe, societe, calcul, au);

        // Conclusions : cloture, conservation des documents (saisies au PV de cloture).
        putSi(v, "LIQUIDATION_CLOTURE_DATE", dateOuNull(first0(c.get("dateClotureLiquidation"), c.get("dateCloture"))));
        putSi(v, "DOCUMENTS_CONSERVATION_LIEU", str(c.get("conservationLieu")));
        putSi(v, "DOCUMENTS_CONSERVATION_DEPOSITAIRE_NOM", str(c.get("conservationDepositaireNom")));
        putSi(v, "RAPPORT_LIQUIDATEUR_OBSERVATIONS_GENERALES", str(r.get("observationsGenerales")));
        putSi(v, "RESOLUTIONS_COMPLEMENTAIRES", str(r.get("resolutionsComplementaires")));

        // Annexes : liste facultative ; l'existence se lit sur la liste (aucune declaration).
        List<Map<String, Object>> annexes = new ArrayList<>();
        for (Object o : asList(r.get("annexes"))) {
            String p = str(o instanceof Map<?, ?> m ? m.get("designation") : o).trim();
            if (!p.isEmpty()) annexes.add(new LinkedHashMap<>(Map.of("PIECE_JOINTE_DESIGNATION", p)));
        }
        v.put("RAPPORT_ANNEXES", annexes);
        v.put("RAPPORT_ANNEXES_EXISTENT", annexes.isEmpty() ? "non" : "oui");

        // Signature du rapport : date et lieu propres au rapport (presente avant l'assemblee).
        putSi(v, "DATE_SIGNATURE", dateOuNull(r.get("dateSignature")));
        putSi(v, "LIEU_SIGNATURE", str(first0(r.get("lieuSignature"), asMap(safe.get("signature")).get("lieu"))));
        putSi(v, "NOMBRE_ORIGINAUX", str(first0(mentions.get("nombreOriginaux"),
                asMap(safe.get("signature")).get("nombreOriginaux"))));
        return v;
    }

    // ------------------------------------------------------------------ identite

    private static void identite(Map<String, Object> v, Map<String, Object> safe, Map<String, Object> societe,
                                 Map<String, Object> mentions) {
        putSi(v, "DENOMINATION", str(societe.get("denomination")));
        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        if (capital != null) {
            v.put("CAPITAL_CHIFFRES", formatAmount(capital));
            v.put("CAPITAL_LETTRES", lettres(capital));
        }
        Long parts = toLong(societe.get("nombreParts"));
        if (parts != null) v.put("NOMBRE_PARTS", String.valueOf(parts));
        putSi(v, "VALEUR_NOMINALE_PART", valeurNominale(societe));
        putSi(v, "SIEGE_SOCIAL", str(first0(societe.get("siegeSocial"), societe.get("adresseSiege"))));
        putSi(v, "DOSSIER_REFERENCE", str(first0(mentions.get("dossierReference"), safe.get("dossierReference"))));
    }

    /** Le liquidateur a-t-il ete gerant : saisi pour une personne physique ; sans objet pour une personne morale. */
    private static void ancienGerant(String templateCode, Map<String, Object> v, Map<String, Object> l) {
        if (!"personne physique".equals(v.get("LIQUIDATEUR_FORME"))) {
            v.put("LIQUIDATEUR_EST_ANCIEN_GERANT", "non");
            return;
        }
        v.put("LIQUIDATEUR_EST_ANCIEN_GERANT", SaisieExigee.ouiNon(templateCode, l.get("ancienGerant"),
                "LIQUIDATEUR_EST_ANCIEN_GERANT", "Le liquidateur a-t-il \u00e9t\u00e9 g\u00e9rant de la soci\u00e9t\u00e9 (oui ou non)"));
    }

    // ------------------------------------------------------------------ tableaux

    /** IV : realisation de l'actif ; resultat de chaque realisation et totaux calcules. */
    private static void realisations(Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        BigDecimal plus = BigDecimal.ZERO;
        BigDecimal moins = BigDecimal.ZERO;
        int n = 0;
        for (Map<String, Object> e : lignesSaisies(r.get("realisations"))) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "REALISATION_BIEN_DESIGNATION", str(e.get("designation")));
            putSi(l, "REALISATION_MODE", str(e.get("mode")));
            putSi(l, "REALISATION_ACQUEREUR", str(e.get("acquereur")));
            putSi(l, "REALISATION_DATE", dateOuNull(e.get("date")));
            putSi(l, "REALISATION_OBSERVATIONS", str(e.get("observations")));
            BigDecimal prix = k.montant(e.get("prix"), "REALISATION_PRIX", "Prix ou montant encaiss\u00e9 de la r\u00e9alisation n\u00b0 " + n);
            BigDecimal inventaire = k.montant(e.get("valeurInventaire"), "REALISATION_VALEUR_INVENTAIRE",
                    "Valeur retenue \u00e0 l'inventaire d'ouverture de la r\u00e9alisation n\u00b0 " + n);
            if (prix != null) {
                l.put("REALISATION_PRIX", decimal(prix));
                k.actif = k.actif.add(prix);
            }
            if (inventaire != null) l.put("REALISATION_VALEUR_INVENTAIRE", decimal(inventaire));
            if (prix != null && inventaire != null) {
                BigDecimal ecart = prix.subtract(inventaire);
                int s = ecart.signum();
                // Le modele imprime "$SENS d'un montant de $MONTANT dirhams" : il n'a pas de branche
                // pour un resultat nul (ecart mecanique du corpus, rapporte).
                l.put("REALISATION_RESULTAT_SENS", s > 0 ? "plus-value" : s < 0 ? "moins-value" : "ni plus-value ni moins-value");
                l.put("REALISATION_RESULTAT_MONTANT", decimal(ecart.abs()));
                if (s > 0) plus = plus.add(ecart);
                if (s < 0) moins = moins.add(ecart.abs());
            }
            lignes.add(l);
        }
        v.put("RAPPORT_REALISATION_ACTIF", lignes);
        k.paire(v, "LIQUIDATION_ACTIF_REALISE_TOTAL", k.actif);
        v.put("LIQUIDATION_PLUS_VALUES_TOTAL_CHIFFRES", decimal(plus));
        v.put("LIQUIDATION_MOINS_VALUES_TOTAL_CHIFFRES", decimal(moins));
        // Rapport final : le liquidateur declare tout l'actif realise (texte du modele).
        v.put("LIQUIDATION_ACTIF_RESTANT_CHIFFRES", "0");
    }

    /** V : apurement du passif ; solde de chaque creancier et totaux calcules. */
    private static void apurements(Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        BigDecimal admis = BigDecimal.ZERO;
        BigDecimal restant = BigDecimal.ZERO;
        int n = 0;
        for (Map<String, Object> e : lignesSaisies(r.get("apurements"))) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "APUREMENT_CREANCIER", str(e.get("creancier")));
            putSi(l, "APUREMENT_NATURE", str(e.get("nature")));
            putSi(l, "APUREMENT_DATE_REGLEMENT", dateOuNull(e.get("dateReglement")));
            putSi(l, "APUREMENT_MODE_REGLEMENT", str(e.get("modeReglement")));
            putSi(l, "APUREMENT_OBSERVATIONS", str(e.get("observations")));
            BigDecimal a = k.montant(e.get("montantAdmis"), "APUREMENT_MONTANT_ADMIS", "Montant admis du cr\u00e9ancier n\u00b0 " + n);
            BigDecimal reg = k.montant(e.get("montantRegle"), "APUREMENT_MONTANT_REGLE", "Montant r\u00e9gl\u00e9 au cr\u00e9ancier n\u00b0 " + n);
            if (a != null) {
                l.put("APUREMENT_MONTANT_ADMIS", decimal(a));
                admis = admis.add(a);
            }
            if (reg != null) {
                l.put("APUREMENT_MONTANT_REGLE", decimal(reg));
                k.passif = k.passif.add(reg);
            }
            if (a != null && reg != null) {
                BigDecimal solde = a.subtract(reg);
                if (solde.signum() < 0) {
                    k.manque("APUREMENT_SOLDE", "Cr\u00e9ancier n\u00b0 " + n + " : le montant r\u00e9gl\u00e9 d\u00e9passe le montant admis");
                } else {
                    l.put("APUREMENT_SOLDE", decimal(solde));
                    restant = restant.add(solde);
                }
            }
            lignes.add(l);
        }
        v.put("RAPPORT_APUREMENT_PASSIF", lignes);
        v.put("LIQUIDATION_PASSIF_ADMIS_TOTAL_CHIFFRES", decimal(admis));
        k.paire(v, "LIQUIDATION_PASSIF_REGLE_TOTAL", k.passif);
        v.put("LIQUIDATION_PASSIF_RESTANT_CHIFFRES", decimal(restant));
        if (restant.signum() > 0) {
            // Rapport final : le modele declare qu'aucun passif connu ne subsiste.
            k.manque("LIQUIDATION_PASSIF_RESTANT_CHIFFRES", "Passif demeurant \u00e0 acquitter (" + decimal(restant)
                    + " dirhams) : le rapport final d\u00e9clare tout le passif connu acquitt\u00e9 ; les pr\u00e9tentions "
                    + "contest\u00e9es se d\u00e9clarent au tableau des passifs contest\u00e9s");
        }
    }

    /** VI.1 : creances irrecouvrables (existence saisie). */
    private static void creancesIrrecouvrables(String templateCode, Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> saisies = existence(templateCode, v, r, "creancesIrrecouvrablesExistent",
                "creancesIrrecouvrables", "RAPPORT_CREANCES_IRRECOUVRABLES_EXISTENT",
                "Des cr\u00e9ances sont-elles irr\u00e9couvrables (oui ou non)", "cr\u00e9ances irr\u00e9couvrables", k);
        List<Map<String, Object>> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (Map<String, Object> e : saisies) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "CREANCE_IRRECOUVRABLE_DEBITEUR", str(e.get("debiteur")));
            putSi(l, "CREANCE_IRRECOUVRABLE_DILIGENCES", str(e.get("diligences")));
            putSi(l, "CREANCE_IRRECOUVRABLE_MOTIF", str(e.get("motif")));
            putSi(l, "CREANCE_IRRECOUVRABLE_SUITE", str(e.get("suite")));
            BigDecimal m = k.montant(e.get("montant"), "CREANCE_IRRECOUVRABLE_MONTANT", "Montant de la cr\u00e9ance irr\u00e9couvrable n\u00b0 " + n);
            if (m != null) {
                l.put("CREANCE_IRRECOUVRABLE_MONTANT", decimal(m));
                total = total.add(m);
            }
            lignes.add(l);
        }
        v.put("RAPPORT_CREANCES_IRRECOUVRABLES", lignes);
        if (!lignes.isEmpty()) v.put("LIQUIDATION_CREANCES_IRRECOUVRABLES_TOTAL_CHIFFRES", decimal(total));
    }

    /** VI.2 : passifs contestes (existence saisie), totaux des reclamations et des provisions. */
    private static void passifsContestes(String templateCode, Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> saisies = existence(templateCode, v, r, "passifsContestesExistent", "passifsContestes",
                "RAPPORT_PASSIFS_CONTESTES_EXISTENT", "Des pr\u00e9tentions contre la soci\u00e9t\u00e9 sont-elles contest\u00e9es (oui ou non)",
                "passifs contest\u00e9s", k);
        List<Map<String, Object>> lignes = new ArrayList<>();
        BigDecimal reclame = BigDecimal.ZERO;
        BigDecimal provisions = BigDecimal.ZERO;
        int n = 0;
        for (Map<String, Object> e : saisies) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "PASSIF_CONTESTE_CREANCIER", str(e.get("creancier")));
            putSi(l, "PASSIF_CONTESTE_MOTIF", str(e.get("motif")));
            putSi(l, "PASSIF_CONTESTE_ETAT", str(e.get("etat")));
            BigDecimal mr = k.montant(e.get("montantReclame"), "PASSIF_CONTESTE_MONTANT_RECLAME", "Montant r\u00e9clam\u00e9 du passif contest\u00e9 n\u00b0 " + n);
            BigDecimal ma = k.montant(e.get("montantAdmis"), "PASSIF_CONTESTE_MONTANT_ADMIS",
                    "Montant admis par le liquidateur du passif contest\u00e9 n\u00b0 " + n);
            BigDecimal mp = k.montant(e.get("provision"), "PASSIF_CONTESTE_PROVISION", "Provision constitu\u00e9e pour le passif contest\u00e9 n\u00b0 " + n);
            if (mr != null) {
                l.put("PASSIF_CONTESTE_MONTANT_RECLAME", decimal(mr));
                reclame = reclame.add(mr);
            }
            if (ma != null) l.put("PASSIF_CONTESTE_MONTANT_ADMIS", decimal(ma));
            if (mp != null) {
                l.put("PASSIF_CONTESTE_PROVISION", decimal(mp));
                provisions = provisions.add(mp);
            }
            lignes.add(l);
        }
        v.put("RAPPORT_PASSIFS_CONTESTES", lignes);
        if (!lignes.isEmpty()) {
            v.put("LIQUIDATION_PASSIFS_CONTESTES_TOTAL_CHIFFRES", decimal(reclame));
            v.put("LIQUIDATION_PROVISIONS_PASSIFS_CONTESTES_CHIFFRES", decimal(provisions));
            putSi(v, "LIQUIDATION_PASSIFS_CONTESTES_MESURES", str(r.get("passifsContestesMesures")));
        }
    }

    /** VII : cessions soumises aux articles 365 et 366 de la loi 17-95 (existence saisie). */
    private static void cessionsRestreintes(String templateCode, Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> saisies = existence(templateCode, v, r, "cessionsRestreintesExistent", "cessionsRestreintes",
                "RAPPORT_CESSIONS_RESTREINTES_EXISTENT",
                "Des cessions entrent-elles dans le champ des articles 365 et 366 de la loi n\u00b0 17-95 (oui ou non)",
                "cessions soumises \u00e0 restriction", k);
        List<Map<String, Object>> lignes = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> e : saisies) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "CESSION_RESTREINTE_BIEN", str(e.get("bien")));
            putSi(l, "CESSION_RESTREINTE_CESSIONNAIRE", str(e.get("cessionnaire")));
            putSi(l, "CESSION_RESTREINTE_LIEN", str(e.get("lien")));
            putSi(l, "CESSION_RESTREINTE_ARTICLE", str(e.get("article")));
            putSi(l, "CESSION_RESTREINTE_DATE", dateOuNull(e.get("date")));
            putSi(l, "CESSION_RESTREINTE_AUTORISATION_NATURE", str(e.get("autorisationNature")));
            putSi(l, "CESSION_RESTREINTE_AUTORISATION_DATE", dateOuNull(e.get("autorisationDate")));
            putSi(l, "CESSION_RESTREINTE_AUTORISATION_REFERENCE", str(e.get("autorisationReference")));
            BigDecimal p = k.montant(e.get("prix"), "CESSION_RESTREINTE_PRIX", "Prix de la cession n\u00b0 " + n);
            if (p != null) l.put("CESSION_RESTREINTE_PRIX", decimal(p));
            lignes.add(l);
        }
        v.put("RAPPORT_CESSIONS_RESTREINTES", lignes);
    }

    /** VIII : frais et honoraires de liquidation (remuneration du liquidateur comprise). */
    private static void frais(Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> e : lignesSaisies(r.get("frais"))) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "FRAIS_LIQUIDATION_NATURE", str(e.get("nature")));
            putSi(l, "FRAIS_LIQUIDATION_BENEFICIAIRE", str(e.get("beneficiaire")));
            putSi(l, "FRAIS_LIQUIDATION_DATE", dateOuNull(e.get("date")));
            putSi(l, "FRAIS_LIQUIDATION_STATUT", str(e.get("statut")));
            BigDecimal m = k.montant(e.get("montant"), "FRAIS_LIQUIDATION_MONTANT", "Montant du frais n\u00b0 " + n);
            if (m != null) {
                l.put("FRAIS_LIQUIDATION_MONTANT", decimal(m));
                k.frais = k.frais.add(m);
            }
            lignes.add(l);
        }
        v.put("RAPPORT_FRAIS_LIQUIDATION", lignes);
        k.paire(v, "LIQUIDATION_FRAIS_TOTAL", k.frais);
        BigDecimal rembourses = k.montant(r.get("fraisRembourses"), "LIQUIDATEUR_FRAIS_REMBOURSES_MONTANT",
                "Frais rembours\u00e9s au liquidateur sur justificatifs (0 s'il n'y en a pas)");
        if (rembourses != null) v.put("LIQUIDATEUR_FRAIS_REMBOURSES_MONTANT", decimal(rembourses));
    }

    /** IX.1 : sort du personnel (existence saisie), dix salaries ou plus, charges acquittees. */
    private static void personnel(String templateCode, Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> saisies = existence(templateCode, v, r, "personnelExiste", "personnel",
                "RAPPORT_PERSONNEL_SORT_EXISTE", "La soci\u00e9t\u00e9 occupait-elle des salari\u00e9s pendant la p\u00e9riode (oui ou non)",
                "salari\u00e9s", k);
        List<Map<String, Object>> lignes = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> e : saisies) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "SALARIE_NOM", str(e.get("nom")));
            putSi(l, "SALARIE_FONCTION", str(e.get("fonction")));
            putSi(l, "SALARIE_SORT_CONTRAT", str(e.get("sortContrat")));
            putSi(l, "SALARIE_SORT_DATE_EFFET", dateOuNull(e.get("dateEffet")));
            putSi(l, "SALARIE_SOLDE_TOUT_COMPTE_DATE", dateOuNull(e.get("soldeToutCompteDate")));
            BigDecimal m = k.montant(e.get("indemnites"), "SALARIE_INDEMNITES_MONTANT", "Indemnit\u00e9s vers\u00e9es au salari\u00e9 n\u00b0 " + n);
            if (m != null) l.put("SALARIE_INDEMNITES_MONTANT", decimal(m));
            lignes.add(l);
        }
        v.put("RAPPORT_PERSONNEL_SORT", lignes);
        if (lignes.isEmpty()) return;
        BigDecimal charges = k.montant(r.get("chargesPersonnelTotal"), "LIQUIDATION_CHARGES_PERSONNEL_TOTAL_CHIFFRES",
                "Total des salaires, indemnit\u00e9s et charges sociales acquitt\u00e9s pendant la p\u00e9riode");
        if (charges != null) v.put("LIQUIDATION_CHARGES_PERSONNEL_TOTAL_CHIFFRES", decimal(charges));
        String dix = SaisieExigee.ouiNon(templateCode, r.get("salariesDixOuPlus"), "SALARIES_DIX_OU_PLUS",
                "La soci\u00e9t\u00e9 occupait-elle habituellement dix salari\u00e9s ou plus (oui ou non)");
        v.put("SALARIES_DIX_OU_PLUS", dix);
        if ("oui".equals(dix)) {
            putSi(v, "SALARIES_AUTORISATION_DEMANDE_DATE", dateOuNull(r.get("autorisationDemandeDate")));
            putSi(v, "SALARIES_AUTORISATION_SUITE", str(r.get("autorisationSuite")));
        }
    }

    /** IX.2 : contrats en cours (existence saisie) et baux (article 364 de la loi 17-95). */
    private static void contrats(String templateCode, Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> saisies = existence(templateCode, v, r, "contratsExistent", "contrats",
                "RAPPORT_CONTRATS_EN_COURS_EXISTENT", "Des contrats \u00e9taient-ils en cours d'ex\u00e9cution (oui ou non)",
                "contrats en cours", k);
        List<Map<String, Object>> lignes = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> e : saisies) {
            n++;
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "CONTRAT_EN_COURS_DESIGNATION", str(e.get("designation")));
            putSi(l, "CONTRAT_EN_COURS_COCONTRACTANT", str(e.get("cocontractant")));
            putSi(l, "CONTRAT_EN_COURS_SORT", str(e.get("sort")));
            putSi(l, "CONTRAT_EN_COURS_DATE_EFFET", dateOuNull(e.get("dateEffet")));
            BigDecimal m = k.montant(e.get("indemnite"), "CONTRAT_EN_COURS_INDEMNITE", "Indemnit\u00e9 ou solde du contrat n\u00b0 " + n);
            if (m != null) l.put("CONTRAT_EN_COURS_INDEMNITE", decimal(m));
            lignes.add(l);
        }
        v.put("RAPPORT_CONTRATS_EN_COURS", lignes);
        if (lignes.isEmpty()) return;
        v.put("INVENTAIRE_BAUX_EXISTENT", SaisieExigee.ouiNon(templateCode, r.get("bauxExistent"), "INVENTAIRE_BAUX_EXISTENT",
                "Des baux d'immeubles affect\u00e9s \u00e0 l'activit\u00e9 figurent-ils parmi ces contrats (oui ou non)"));
    }

    /** X : diligences fiscales et sociales accomplies (au moins une). */
    private static void diligences(Map<String, Object> v, Map<String, Object> r, Calcul k) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Map<String, Object> e : lignesSaisies(r.get("diligences"))) {
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "DILIGENCE_NATURE", str(e.get("nature")));
            putSi(l, "DILIGENCE_ORGANISME", str(e.get("organisme")));
            putSi(l, "DILIGENCE_REFERENCE_TEXTE", str(e.get("referenceTexte")));
            putSi(l, "DILIGENCE_DATE_LIMITE", dateOuNull(e.get("dateLimite")));
            putSi(l, "DILIGENCE_DATE_ACCOMPLISSEMENT", dateOuNull(e.get("dateAccomplissement")));
            putSi(l, "DILIGENCE_ETAT", str(e.get("etat")));
            putSi(l, "DILIGENCE_OBSERVATIONS", str(e.get("observations")));
            lignes.add(l);
        }
        if (lignes.isEmpty()) {
            k.manque("RAPPORT_DILIGENCES_FISCALES_SOCIALES",
                    "Diligences fiscales et sociales accomplies (au moins une : le rapport en rend compte)");
        }
        v.put("RAPPORT_DILIGENCES_FISCALES_SOCIALES", lignes);
    }

    /** X (suite) : TVA, taxe professionnelle, CNSS, attestations (dates limites saisies, jamais calculees). */
    private static void fiscal(String templateCode, Map<String, Object> v, Map<String, Object> r) {
        String tva = SaisieExigee.ouiNon(templateCode, r.get("tvaAssujettie"), "TVA_ASSUJETTIE",
                "La soci\u00e9t\u00e9 est-elle assujettie \u00e0 la TVA (oui ou non)");
        v.put("TVA_ASSUJETTIE", tva);
        if ("oui".equals(tva)) putSi(v, "TVA_CESSATION_DATE_LIMITE", dateOuNull(r.get("tvaCessationDateLimite")));
        putSi(v, "TP_CESSATION_DATE_LIMITE", dateOuNull(r.get("tpCessationDateLimite")));
        putSi(v, "CNSS_RADIATION_DATE_LIMITE", dateOuNull(r.get("cnssRadiationDateLimite")));
        putSi(v, "LIQUIDATION_ATTESTATIONS_ETAT", str(r.get("attestationsEtat")));
    }

    // ------------------------------------------------------------------ resultat

    /**
     * XI : resultat de la liquidation, calcule par la regle unique {@link ResultatLiquidation}
     * (decision D27 du lot L4d, L6b E19), concordance avec le PV de cloture comprise.
     */
    private static void resultat(String templateCode, Map<String, Object> v, Map<String, Object> r, Map<String, Object> c,
                                 Map<String, Object> safe, Map<String, Object> societe, Calcul k, boolean au) {
        BigDecimal remuneration = BigDecimal.ZERO;
        if ("oui".equals(v.get("LIQUIDATEUR_REMUNERATION_APPLICABLE"))) {
            remuneration = k.montant(asMap(safe.get("liquidateur")).get("remunerationMontant"),
                    "LIQUIDATEUR_REMUNERATION_MONTANT_CHIFFRES", "R\u00e9mun\u00e9ration du liquidateur");
        }
        k.refuserSiManquantes("Compte de la liquidation incomplet");

        // L6b (E19) : regle unique du resultat, partagee avec le PV de cloture (concordance
        // avec l'etape 1 et les frais du PV comprise).
        ResultatLiquidation.Compte compte = ResultatLiquidation.depuisRapport(templateCode, safe, remuneration);
        BigDecimal disponible = compte.disponible();
        if (disponible.signum() < 0) {
            throw new GenerationRefuseeException(templateCode,
                    "Compte de la liquidation incoh\u00e9rent : l'actif r\u00e9alis\u00e9 ne couvre pas le passif, les frais et "
                            + "les imp\u00f4ts, alors que le rapport final d\u00e9clare tout le passif acquitt\u00e9",
                    List.of(new DonneeManquante("LIQUIDATION_TRESORERIE_DISPONIBLE_CHIFFRES", "Tr\u00e9sorerie disponible (n\u00e9gative)", null)));
        }
        v.put("LIQUIDATION_TRESORERIE_DISPONIBLE_CHIFFRES", decimal(disponible));
        v.put("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES", decimal(compte.rembourse()));
        String sensCompte = compte.sens();
        if (ResultatLiquidation.NUL.equals(sensCompte)) {
            // Le modele n'a que deux branches (boni, sinon mali) : un solde nul y imprimerait
            // "un mali de 0 dirhams" (ecart du corpus, rapporte) ; jamais suppose.
            throw new GenerationRefuseeException(templateCode,
                    "R\u00e9sultat de la liquidation nul : le mod\u00e8le du rapport ne pr\u00e9voit qu'un boni ou un mali "
                            + "(\u00e9cart du mod\u00e8le du cabinet, \u00e0 d\u00e9cider)",
                    List.of(new DonneeManquante("LIQUIDATION_RESULTAT_TYPE", "Sens du r\u00e9sultat de la liquidation (solde nul)", null)));
        }
        if (ResultatLiquidation.BONI.equals(sensCompte)) {
            v.put("LIQUIDATION_RESULTAT_TYPE", "boni");
            BigDecimal boni = compte.montant();
            k.paire(v, "LIQUIDATION_BONI", boni);
            repartitionBoni(templateCode, v, safe, societe, boni, au);
            retenue(templateCode, v, r, c);
        } else {
            v.put("LIQUIDATION_RESULTAT_TYPE", "mali");
            k.paire(v, "LIQUIDATION_MALI", compte.montant());
            putSi(v, "LIQUIDATION_MALI_OBSERVATIONS", str(r.get("maliObservations")));
            associes(v, safe, au, null, null);
        }
    }

    /** Boni : reparti au prorata des parts (exact au centime, sinon refuse) ou attribue a l'associe unique. */
    private static void repartitionBoni(String templateCode, Map<String, Object> v, Map<String, Object> safe,
                                        Map<String, Object> societe, BigDecimal boni, boolean au) {
        if (au) {
            associes(v, safe, true, null, null);
            return;
        }
        Long total = toLong(societe.get("nombreParts"));
        associes(v, safe, false, boni, total);
        List<DonneeManquante> manquantes = new ArrayList<>();
        for (Map<String, Object> l : asListOfMaps(v.get("ASSOCIES"))) {
            if (!l.containsKey("ASSOCIE_BONI_CHIFFRES")) {
                manquantes.add(new DonneeManquante("ASSOCIE_BONI_CHIFFRES", "Quote-part du boni de " + l.get("ASSOCIE_NOM")
                        + " (nombre de parts inconnu, ou prorata qui ne tombe pas juste au centime : \u00e0 arr\u00eater par "
                        + "l'\u00e9tat de r\u00e9partition)", null));
            }
        }
        if (!manquantes.isEmpty()) {
            throw new GenerationRefuseeException(templateCode, "R\u00e9partition du boni : "
                    + String.join(", ", manquantes.stream().map(DonneeManquante::libelle).toList()), manquantes);
        }
    }

    /** Associes : liste (SARL, reception du rapport et repartition du boni) ou associe unique. */
    private static void associes(Map<String, Object> v, Map<String, Object> safe, boolean au, BigDecimal boni, Long totalParts) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Map<String, Object> a : asListOfMaps(safe.get("associes"))) {
            if (a.isEmpty()) continue;
            String nom = "MORALE".equalsIgnoreCase(str(a.get("typePersonne"))) ? str(a.get("denomination")) : labelPhysique(a);
            if (au) {
                putSi(v, "ASSOCIE_NOM", nom);
                return;
            }
            Map<String, Object> l = new LinkedHashMap<>();
            putSi(l, "ASSOCIE_NOM", nom);
            Long parts = toLong(first0(a.get("nombreParts"), a.get("partsChiffres")));
            if (parts != null) l.put("ASSOCIE_NOMBRE_PARTS", String.valueOf(parts));
            if (boni != null && parts != null && totalParts != null && totalParts > 0) {
                try {
                    BigDecimal q = boni.multiply(BigDecimal.valueOf(parts))
                            .divide(BigDecimal.valueOf(totalParts)).setScale(2, RoundingMode.UNNECESSARY);
                    l.put("ASSOCIE_BONI_CHIFFRES", decimal(q));
                } catch (ArithmeticException inexact) {
                    // nomme par repartitionBoni
                }
            }
            lignes.add(l);
        }
        if (!au) v.put("ASSOCIES", lignes);
    }

    /** Retenue a la source sur le boni : saisie au PV de cloture ; non applicable, observations saisies. */
    private static void retenue(String templateCode, Map<String, Object> v, Map<String, Object> r, Map<String, Object> c) {
        String applicable = SaisieExigee.ouiNon(templateCode, c.get("retenueApplicable"), "BONI_RETENUE_SOURCE_APPLICABLE",
                "Retenue \u00e0 la source sur le boni de liquidation (oui ou non)");
        v.put("BONI_RETENUE_SOURCE_APPLICABLE", applicable);
        if ("oui".equals(applicable)) {
            putSi(v, "RETENUE_SOURCE_TAUX", str(c.get("retenueTaux")));
            BigDecimal m = montantOuNull(c.get("retenueMontant"));
            if (m != null) v.put("RETENUE_SOURCE_MONTANT", decimal(m));
        } else {
            putSi(v, "BONI_RETENUE_SOURCE_OBSERVATIONS", str(r.get("retenueObservations")));
        }
    }

    // ------------------------------------------------------------------ outils

    /**
     * Tableau facultatif : existence saisie (oui / non, jamais deduite d'une liste vide, car le
     * modele imprime alors une declaration du liquidateur) ; "oui" exige au moins une ligne.
     */
    private static List<Map<String, Object>> existence(String templateCode, Map<String, Object> v, Map<String, Object> r,
                                                       String cleExistence, String cleListe, String variable,
                                                       String libelle, String nomTableau, Calcul k) {
        String existe = SaisieExigee.ouiNon(templateCode, r.get(cleExistence), variable, libelle);
        v.put(variable, existe);
        if (!"oui".equals(existe)) return List.of();
        List<Map<String, Object>> lignes = lignesSaisies(r.get(cleListe));
        if (lignes.isEmpty()) k.manque(variable, "Tableau des " + nomTableau + " : annonc\u00e9 (oui) mais vide");
        return lignes;
    }

    /** Lignes d'un tableau, lignes entierement vides ignorees. */
    static List<Map<String, Object>> lignesSaisies(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : asListOfMaps(o)) {
            if (m.values().stream().anyMatch(x -> x != null && !String.valueOf(x).isBlank())) out.add(m);
        }
        return out;
    }

    /** Montant en dirhams (centimes admis), positif ou nul ; illisible : null. */
    static BigDecimal montantOuNull(Object o) {
        if (o == null) return null;
        BigDecimal b;
        if (o instanceof BigDecimal d) b = d;
        else if (o instanceof Number n) b = new BigDecimal(String.valueOf(n));
        else {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "").replace(',', '.');
            if (s.isEmpty()) return null;
            try {
                b = new BigDecimal(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (b.signum() < 0 || b.stripTrailingZeros().scale() > 2) return null;
        return b;
    }

    /** "12 500" ou "12 500,50". */
    static String decimal(BigDecimal b) {
        BigDecimal s = b.stripTrailingZeros();
        if (s.scale() <= 0) return formatAmount(b.longValueExact());
        return String.format(Locale.FRANCE, "%,.2f", b);
    }

    /** En lettres (capitales du convertisseur) : "TROIS CENT MILLE" ; avec centimes : "... DIRHAMS ET CINQUANTE CENTIMES". */
    static String lettresMontant(BigDecimal b) {
        BigDecimal entier = b.setScale(0, RoundingMode.DOWN);
        int centimes = b.subtract(entier).movePointRight(2).intValueExact();
        String base = lettres(entier.longValueExact());
        return centimes == 0 ? base : base + " DIRHAMS ET " + lettres(centimes) + " CENTIMES";
    }

    /** Accumulateur des totaux et des donnees manquantes a nommer ensemble. */
    private static final class Calcul {
        private final String templateCode;
        private final List<DonneeManquante> manquantes = new ArrayList<>();
        BigDecimal actif = BigDecimal.ZERO;
        BigDecimal passif = BigDecimal.ZERO;
        BigDecimal frais = BigDecimal.ZERO;

        Calcul(String templateCode) {
            this.templateCode = templateCode;
        }

        BigDecimal montant(Object raw, String variable, String libelle) {
            BigDecimal b = montantOuNull(raw);
            if (b == null) {
                boolean saisi = raw != null && !String.valueOf(raw).isBlank();
                manque(variable, libelle + (saisi ? " (montant illisible : dirhams, au plus deux d\u00e9cimales, positif)" : ""));
            }
            return b;
        }

        void manque(String variable, String libelle) {
            manquantes.add(new DonneeManquante(variable, libelle, null));
        }

        void paire(Map<String, Object> v, String base, BigDecimal n) {
            v.put(base + "_CHIFFRES", decimal(n));
            v.put(base + "_LETTRES", lettresMontant(n));
        }

        void refuserSiManquantes(String titre) {
            if (manquantes.isEmpty()) return;
            List<DonneeManquante> d = List.copyOf(manquantes);
            manquantes.clear();
            Function<DonneeManquante, String> l = DonneeManquante::libelle;
            throw new GenerationRefuseeException(templateCode, titre + " : "
                    + String.join(" ; ", d.stream().map(l).toList()), d);
        }
    }
}
