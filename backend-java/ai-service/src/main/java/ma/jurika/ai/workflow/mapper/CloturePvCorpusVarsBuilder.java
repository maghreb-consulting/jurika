package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static ma.jurika.ai.workflow.mapper.LiquidationCorpusCommun.*;

/**
 * Lot L4d (E11d) : variables du proces-verbal de cloture de la liquidation du corpus
 * ({@code PV_CLOTURE_LIQUIDATION_SARL} / {@code _SARL_AU}), qui remplace le PV unifie du
 * classpath a l'etape "cloture" (L2_correspondance_gabarits.md).
 *
 * <p>Le compte definitif se CALCULE, a partir du rapport final du liquidateur et par la regle
 * unique {@link ResultatLiquidation} (L6b E19) : actif net disponible = actif realise - passif
 * regle - frais (remuneration du liquidateur comprise) - impot ; remboursement du nominal a concurrence du
 * capital ; le solde est un boni, un mali ou nul. Le sens n'est jamais suppose : sans les
 * donnees du calcul, la generation est refusee et elles sont nommees. La repartition du boni
 * entre associes se calcule au prorata des parts ; une quote-part qui ne tombe pas juste au
 * centime est refusee (a arreter par l'etat de repartition), jamais arrondie en silence.
 *
 * <p>Les donnees qui pilotent une branche (quorum, auteur de la convocation, approbation du
 * compte, quitus, comptes bancaires, retenue a la source, passif eventuel, commissaire aux
 * comptes, siege de la liquidation) sont saisies ; absentes, la generation est refusee.
 */
final class CloturePvCorpusVarsBuilder {

    static final String CORPUS_SARL = "PV_CLOTURE_LIQUIDATION_SARL";
    static final String CORPUS_SARL_AU = "PV_CLOTURE_LIQUIDATION_SARL_AU";

    private static final Set<String> QUITUS = Set.of("accorde", "sous_reserve", "sans_quitus");

    private CloturePvCorpusVarsBuilder() {
    }

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean au = estAu(templateCode);
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));
        Map<String, Object> c = asMap(safe.get("cloture"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> mentions = asMap(safe.get("mentionsPv"));
        Map<String, Object> societe = asMap(safe.get("societe"));

        seanceCorpus(templateCode, v, safe, au);
        identiteAssocieUnique(v, safe, au);
        mentionsActe(v, safe, mentions);
        DissolutionPvCorpusVarsBuilder.commissaireAuxComptes(templateCode, v, mentions, false);
        piecesAssocies(v, safe, au);

        // Rappel de la dissolution et de sa publicite.
        Map<String, Object> dissolution = asMap(safe.get("dissolution"));
        putSi(v, "DISSOLUTION_DATE_DECISION", dateOuNull(first0(c.get("dissolutionDateDecision"),
                dissolution.get("date"), dissolution.get("dateDissolution"), safe.get("dissolutionDate"))));
        putSi(v, "DISSOLUTION_DATE_EFFET", dateOuNull(c.get("dissolutionDateEffet")));
        LocalDate inscription = toDate(c.get("dissolutionRcInscriptionDate"));
        putSi(v, "DISSOLUTION_RC_INSCRIPTION_DATE", dateFr(inscription));
        // Art. 372 de la loi 17-95 (rappele par le modele) : cinq ans a compter de l'inscription.
        if (inscription != null) v.put("PRESCRIPTION_QUINQUENNALE_DATE_TERME", dateFr(inscription.plusYears(5)));
        putSi(v, "ANNONCE_LEGALE_JOURNAL", str(c.get("annonceJournal")));
        putSi(v, "ANNONCE_LEGALE_DATE_PUBLICATION", dateOuNull(c.get("annoncePublicationDate")));
        if (!au) convocation(templateCode, v, c);

        // Liquidateur (nomme a la dissolution) et siege de la liquidation.
        DissolutionAnnonceVarsBuilder.liquidateurCorpus(v, liquidateur);
        DissolutionPvCorpusVarsBuilder.liquidateurPhysique(v, liquidateur);
        siegeLiquidation(templateCode, v, liquidateur, societe, safe);
        DissolutionAnnonceVarsBuilder.tribunal(v, safe);

        // Compte definitif et resultat.
        DissolutionPvCorpusVarsBuilder.remuneration(templateCode, v, liquidateur);
        LocalDate dateCompte = toDate(first0(c.get("compteDefinitifDate")));
        putSi(v, "LIQUIDATION_COMPTE_DEFINITIF_DATE", dateFr(dateCompte));
        putSi(v, "LIQUIDATION_OPERATIONS_RESUME", str(c.get("operationsResume")));
        putSi(v, "LIQUIDATION_CONTESTATIONS", str(c.get("contestations")));
        v.put("LIQUIDATION_COMPTES_BANCAIRES_CLOTURES", SaisieExigee.ouiNon(templateCode,
                c.get("comptesBancairesClotures"), "LIQUIDATION_COMPTES_BANCAIRES_CLOTURES",
                "Les comptes bancaires de la soci\u00e9t\u00e9 sont-ils cl\u00f4tur\u00e9s (oui ou non)"));
        String type = compteDefinitif(templateCode, v, c, safe);

        decisions(templateCode, v, c);
        LocalDate dateCloture = toDate(first0(c.get("dateClotureLiquidation"), c.get("dateCloture")));
        putSi(v, "LIQUIDATION_CLOTURE_DATE", dateFr(dateCloture));
        // Art. 150-II du CGI (rappele par le modele) : 45 jours suivant la cloture.
        if (dateCloture != null) v.put("LIQUIDATION_DECLARATION_FINALE_DATE_LIMITE", dateFr(dateCloture.plusDays(45)));
        repartition(templateCode, v, c, safe, societe, type, au);
        conservation(v, c);
        passifEventuel(templateCode, v, c);

        List<Map<String, Object>> pieces = new ArrayList<>();
        for (Object o : asList(c.get("piecesRadiation"))) {
            String p = str(o).trim();
            if (!p.isEmpty()) pieces.add(new LinkedHashMap<>(Map.of("PIECE_RADIATION_LIBELLE", p)));
        }
        v.put("PIECES_RADIATION", pieces);
        v.put("PIECES_RADIATION_EXISTENT", pieces.isEmpty() ? "non" : "oui");
        v.put("ORDRE_DU_JOUR_COMPLEMENTAIRE", List.of());
        v.put("ORDRE_DU_JOUR_COMPLEMENTAIRE_EXISTE", "non");
        return v;
    }

    /** Convocation de l'assemblee de cloture : par le liquidateur, ou par un mandataire de justice. */
    private static void convocation(String templateCode, Map<String, Object> v, Map<String, Object> c) {
        String auteur = str(c.get("convocationAuteur")).trim();
        if (!auteur.equals("liquidateur") && !auteur.equals("mandataire")) {
            throw SaisieExigee.refus(templateCode, "CLOTURE_CONVOCATION_AUTEUR",
                    "Auteur de la convocation de l'assembl\u00e9e de cl\u00f4ture (le liquidateur ou un mandataire "
                            + "d\u00e9sign\u00e9 en r\u00e9f\u00e9r\u00e9)");
        }
        v.put("CLOTURE_CONVOCATION_AUTEUR", auteur);
        if (auteur.equals("mandataire")) {
            Map<String, Object> o = asMap(c.get("ordonnance"));
            putSi(v, "ORDONNANCE_REFERE_ASSOCIE_DEMANDEUR", str(o.get("associeDemandeur")));
            putSi(v, "ORDONNANCE_REFERE_TRIBUNAL", str(o.get("tribunal")));
            putSi(v, "ORDONNANCE_REFERE_NUMERO", str(o.get("numero")));
            putSi(v, "ORDONNANCE_REFERE_DATE", dateOuNull(o.get("date")));
            putSi(v, "MANDATAIRE_REFERE_NOM", str(o.get("mandataireNom")));
        }
    }

    /** Siege de la liquidation : choix enregistre a la dissolution, ou adresse connue. */
    static void siegeLiquidation(String templateCode, Map<String, Object> v, Map<String, Object> l,
                                         Map<String, Object> societe, Map<String, Object> safe) {
        String identique = norm(first0(l.get("siegeIdentique"), safe.get("siegeIdentique")));
        String adresse = str(first0(l.get("siege"), safe.get("liquidationSiege"), safe.get("siegeLiquidation"))).trim();
        if (!identique.equals("oui") && !identique.equals("non")) {
            if (adresse.isEmpty()) {
                throw SaisieExigee.refus(templateCode, "LIQUIDATION_SIEGE_IDENTIQUE",
                        "Si\u00e8ge de la liquidation (au si\u00e8ge social ou \u00e0 une autre adresse)");
            }
            identique = norm(adresse).equals(norm(first0(societe.get("siegeSocial"), societe.get("adresseSiege"))))
                    ? "oui" : "non";
        }
        v.put("LIQUIDATION_SIEGE_IDENTIQUE", identique);
        if (identique.equals("non")) putSi(v, "LIQUIDATION_SIEGE_ADRESSE", adresse);
    }

    /**
     * Compte definitif : totaux, actif net disponible, nominal rembourse, boni / mali / nul.
     * L6b (E19) : le compte vient du rapport final du liquidateur par la regle unique
     * {@link ResultatLiquidation} (meme calcul que le rapport) ; les totaux de l'etape 1 et les
     * frais du PV n'y sont que controles.
     * @return le sens du solde ("boni", "mali" ou "nul")
     */
    private static String compteDefinitif(String templateCode, Map<String, Object> v, Map<String, Object> c,
                                          Map<String, Object> safe) {
        // Liquidateur non remunere : aucune remuneration a deduire (donnee saisie, pas supposee).
        Long remuneration = "oui".equals(v.get("LIQUIDATEUR_REMUNERATION_APPLICABLE"))
                ? toLong(str(v.get("LIQUIDATEUR_REMUNERATION_MONTANT_CHIFFRES"))) : Long.valueOf(0);
        if (remuneration == null) {
            throw new GenerationRefuseeException(templateCode, "Compte d\u00e9finitif incomplet : R\u00e9mun\u00e9ration du liquidateur",
                    List.of(manque("LIQUIDATEUR_REMUNERATION_MONTANT_CHIFFRES", "R\u00e9mun\u00e9ration du liquidateur")));
        }
        ResultatLiquidation.Compte k = ResultatLiquidation.depuisRapport(templateCode, safe, BigDecimal.valueOf(remuneration));
        long actif = entier(templateCode, k.actif(), "LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES", "Produit total de la r\u00e9alisation de l'actif");
        long passif = entier(templateCode, k.passif(), "LIQUIDATION_PASSIF_REGLE_TOTAL_CHIFFRES", "Montant total du passif acquitt\u00e9");
        long frais = entier(templateCode, k.frais(), "LIQUIDATION_FRAIS_CHIFFRES", "Frais et honoraires de liquidation") - remuneration;
        long impot = entier(templateCode, k.impot(), "LIQUIDATION_IS_MONTANT_CHIFFRES", "Imp\u00f4t sur les soci\u00e9t\u00e9s de la liquidation");
        long capital = entier(templateCode, k.capital(), "CAPITAL_CHIFFRES", "Capital social");
        paire(v, "LIQUIDATION_ACTIF_REALISE_TOTAL", actif);
        paire(v, "LIQUIDATION_PASSIF_REGLE_TOTAL", passif);
        v.put("LIQUIDATION_FRAIS_CHIFFRES", formatAmount(frais));
        if (!str(c.get("isMontant")).isBlank()) v.put("LIQUIDATION_IS_MONTANT_CHIFFRES", formatAmount(impot));

        long disponible = entier(templateCode, k.disponible(), "LIQUIDATION_DISPONIBLE_CHIFFRES", "Actif net disponible");
        if (disponible < 0) {
            throw new GenerationRefuseeException(templateCode,
                    "Compte d\u00e9finitif incoh\u00e9rent : l'actif r\u00e9alis\u00e9 ne couvre pas le passif, les frais et "
                            + "les imp\u00f4ts, alors que le proc\u00e8s-verbal d\u00e9clare tout le passif acquitt\u00e9",
                    List.of(manque("LIQUIDATION_DISPONIBLE_CHIFFRES", "Actif net disponible (n\u00e9gatif)")));
        }
        paire(v, "LIQUIDATION_DISPONIBLE", disponible);
        paire(v, "LIQUIDATION_CAPITAL_REMBOURSE", Math.min(disponible, capital));
        String type = k.sens();
        long montant = entier(templateCode, k.montant(), "LIQUIDATION_DISPONIBLE_CHIFFRES", "R\u00e9sultat de la liquidation");
        if (ResultatLiquidation.BONI.equals(type)) paire(v, "LIQUIDATION_BONI", montant);
        if (ResultatLiquidation.MALI.equals(type)) paire(v, "LIQUIDATION_MALI", montant);
        v.put("LIQUIDATION_RESULTAT_TYPE", type);
        return type;
    }

    /** Le PV de cloture s'etablit en dirhams entiers : un montant en centimes est refuse et nomme. */
    private static long entier(String templateCode, BigDecimal b, String variable, String libelle) {
        try {
            return b.longValueExact();
        } catch (ArithmeticException e) {
            throw new GenerationRefuseeException(templateCode, "Compte d\u00e9finitif : " + libelle
                    + " en centimes, le proc\u00e8s-verbal de cl\u00f4ture s'\u00e9tablit en dirhams entiers",
                    List.of(manque(variable, libelle + " (dirhams entiers)")));
        }
    }

    /** Approbation du compte definitif et quitus du liquidateur (saisis). */
    private static void decisions(String templateCode, Map<String, Object> v, Map<String, Object> c) {
        String approbation = str(c.get("compteDefinitifApprobation")).trim();
        if (!approbation.equals("approuve") && !approbation.equals("refuse")) {
            throw SaisieExigee.refus(templateCode, "COMPTE_DEFINITIF_APPROBATION",
                    "Le compte d\u00e9finitif est-il approuv\u00e9 (approuv\u00e9 ou refus\u00e9)");
        }
        v.put("COMPTE_DEFINITIF_APPROBATION", approbation);
        if (approbation.equals("refuse")) {
            putSi(v, "COMPTE_DEFINITIF_REFUS_MOTIFS", str(c.get("refusMotifs")));
            return;
        }
        String quitus = str(c.get("quitus")).trim();
        if (!QUITUS.contains(quitus)) {
            throw SaisieExigee.refus(templateCode, "LIQUIDATION_QUITUS_SENS",
                    "Quitus au liquidateur (accord\u00e9, sous r\u00e9serve ou refus\u00e9)");
        }
        v.put("LIQUIDATION_QUITUS_SENS", quitus);
        if (quitus.equals("sous_reserve")) putSi(v, "LIQUIDATEUR_QUITUS_RESERVE_TEXTE", str(c.get("quitusReserveTexte")));
        if (quitus.equals("sans_quitus")) {
            putSi(v, "LIQUIDATEUR_QUITUS_REFUS_MOTIFS", str(c.get("quitusRefusMotifs")));
            putSi(v, "QUITUS_REFUS_MANDATAIRE_NOM", str(c.get("quitusRefusMandataireNom")));
        }
    }

    /**
     * Repartition du solde : boni reparti au prorata des parts (SARL) ou attribue a l'associe
     * unique ; retenue a la source saisie (applicable ou motif) ; mali (repartition facultative).
     */
    private static void repartition(String templateCode, Map<String, Object> v, Map<String, Object> c,
                                    Map<String, Object> safe, Map<String, Object> societe, String type, boolean au) {
        if ("mali".equals(type)) {
            putSi(v, "LIQUIDATION_MALI_REPARTITION", str(c.get("maliRepartition")));
            return;
        }
        if (!"boni".equals(type) || "refuse".equals(v.get("COMPTE_DEFINITIF_APPROBATION"))) return;
        putSi(v, "LIQUIDATION_REPARTITION_MODALITES", str(c.get("repartitionModalites")));
        putSi(v, "LIQUIDATION_REPARTITION_DATE_LIMITE", dateOuNull(c.get("repartitionDateLimite")));
        String retenue = SaisieExigee.ouiNon(templateCode, c.get("retenueApplicable"), "RETENUE_SOURCE_APPLICABLE",
                "Retenue \u00e0 la source sur le boni de liquidation (oui ou non)");
        v.put("RETENUE_SOURCE_APPLICABLE", retenue);
        Long montantRetenue = 0L;
        boolean exoneration = false;
        if ("oui".equals(retenue)) {
            putSi(v, "RETENUE_SOURCE_ASSIETTE_CHIFFRES", montant(c.get("retenueAssiette")));
            putSi(v, "RETENUE_SOURCE_TAUX", str(c.get("retenueTaux")));
            montantRetenue = toLong(c.get("retenueMontant"));
            putSi(v, "RETENUE_SOURCE_MONTANT", montantRetenue == null ? null : formatAmount(montantRetenue));
            String exo = SaisieExigee.ouiNon(templateCode, c.get("retenueExonerationInvoquee"),
                    "RETENUE_SOURCE_EXONERATION_INVOQUEE",
                    "Un associ\u00e9 se pr\u00e9vaut-il d'une exon\u00e9ration de retenue \u00e0 la source (oui ou non)");
            v.put("RETENUE_SOURCE_EXONERATION_INVOQUEE", exo);
            exoneration = "oui".equals(exo);
            if (exoneration) putSi(v, "RETENUE_SOURCE_EXONERATION_BENEFICIAIRE", str(c.get("retenueExonerationBeneficiaire")));
        } else {
            putSi(v, "RETENUE_SOURCE_NON_APPLICABLE_MOTIF", str(c.get("retenueNonApplicableMotif")));
            // Aucune retenue operee : son montant est nul (imprime par l'attribution de l'associe unique).
            v.put("RETENUE_SOURCE_MONTANT", "0");
        }
        long boni = toLong(str(v.get("LIQUIDATION_BONI_CHIFFRES")));
        long rembourse = toLong(str(v.get("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES")));
        if (au) {
            if (montantRetenue != null) v.put("ASSOCIE_SOLDE_NET_CHIFFRES", formatAmount(rembourse + boni - montantRetenue));
            return;
        }
        if (montantRetenue == null) return; // nomme par le controle de completude
        if (exoneration) {
            throw SaisieExigee.refus(templateCode, "ASSOCIE_RETENUE_SOURCE_CHIFFRES",
                    "Retenue \u00e0 la source par associ\u00e9 : une exon\u00e9ration est invoqu\u00e9e, la retenue de chacun "
                            + "se fixe par l'\u00e9tat de r\u00e9partition et ne se r\u00e9partit pas au prorata");
        }
        lignesRepartition(templateCode, v, safe, societe, boni, montantRetenue);
    }

    /** Une ligne par associe : nominal, quote-part du boni, retenue et net, au prorata des parts. */
    private static void lignesRepartition(String templateCode, Map<String, Object> v, Map<String, Object> safe,
                                          Map<String, Object> societe, long boni, long retenue) {
        Long vn = toLong(str(v.get("VALEUR_NOMINALE_PART")));
        Long total = toLong(societe.get("nombreParts"));
        List<Map<String, Object>> lignes = new ArrayList<>();
        List<GenerationRefuseeException.DonneeManquante> manquantes = new ArrayList<>();
        for (Map<String, Object> a : asListOfMaps(safe.get("associes"))) {
            if (a.isEmpty()) continue;
            String nom = "MORALE".equalsIgnoreCase(str(a.get("typePersonne"))) ? str(a.get("denomination")) : labelPhysique(a);
            Long parts = toLong(first0(a.get("nombreParts"), a.get("partsChiffres")));
            if (parts == null || vn == null || total == null || total <= 0) {
                manquantes.add(manque("ASSOCIE_NOMBRE_PARTS", "Nombre de parts de " + nom + " et valeur nominale"));
                continue;
            }
            BigDecimal brut = prorata(boni, parts, total);
            BigDecimal ret = prorata(retenue, parts, total);
            if (brut == null || ret == null) {
                manquantes.add(manque("ASSOCIE_SOLDE_BRUT_CHIFFRES", "Quote-part du boni de " + nom
                        + " (le prorata ne tombe pas juste au centime : \u00e0 arr\u00eater par l'\u00e9tat de r\u00e9partition)"));
                continue;
            }
            BigDecimal nominal = BigDecimal.valueOf(parts * vn);
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("ASSOCIE_NOM", nom);
            l.put("ASSOCIE_NOMBRE_PARTS", String.valueOf(parts));
            l.put("ASSOCIE_NOMINAL_REMBOURSE_CHIFFRES", decimal(nominal));
            l.put("ASSOCIE_SOLDE_BRUT_CHIFFRES", decimal(brut));
            l.put("ASSOCIE_RETENUE_SOURCE_CHIFFRES", decimal(ret));
            l.put("ASSOCIE_SOLDE_NET_CHIFFRES", decimal(nominal.add(brut).subtract(ret)));
            lignes.add(l);
        }
        if (!manquantes.isEmpty()) {
            throw new GenerationRefuseeException(templateCode, "R\u00e9partition du boni : "
                    + String.join(", ", manquantes.stream().map(GenerationRefuseeException.DonneeManquante::libelle).toList()),
                    manquantes);
        }
        v.put("REPARTITION_SOLDE", lignes);
    }

    /** montant * parts / total, exact au centime ; sinon null. */
    private static BigDecimal prorata(long montant, long parts, long total) {
        BigDecimal r = BigDecimal.valueOf(montant).multiply(BigDecimal.valueOf(parts));
        try {
            return r.divide(BigDecimal.valueOf(total)).setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** "12 500" ou "12 500,50". */
    private static String decimal(BigDecimal b) {
        BigDecimal s = b.stripTrailingZeros();
        if (s.scale() <= 0) return formatAmount(b.longValueExact());
        return String.format(Locale.FRANCE, "%,.2f", b);
    }

    /** Conservation des documents : depositaire, qualite, lieu, duree (saisis). */
    private static void conservation(Map<String, Object> v, Map<String, Object> c) {
        putSi(v, "DOCUMENTS_CONSERVATION_DEPOSITAIRE_NOM", str(c.get("conservationDepositaireNom")));
        putSi(v, "DOCUMENTS_CONSERVATION_DEPOSITAIRE_QUALITE", str(c.get("conservationDepositaireQualite")));
        putSi(v, "DOCUMENTS_CONSERVATION_LIEU", str(c.get("conservationLieu")));
        putSi(v, "DOCUMENTS_CONSERVATION_DUREE", str(c.get("conservationDuree")));
    }

    /** Passifs eventuels : existence saisie ; nature, montant, modalites, sequestre, charge. */
    private static void passifEventuel(String templateCode, Map<String, Object> v, Map<String, Object> c) {
        String applicable = SaisieExigee.ouiNon(templateCode, c.get("passifEventuelApplicable"), "PASSIF_EVENTUEL_APPLICABLE",
                "Subsiste-t-il des passifs \u00e9ventuels \u00e0 la cl\u00f4ture (oui ou non)");
        v.put("PASSIF_EVENTUEL_APPLICABLE", applicable);
        if (!"oui".equals(applicable)) return;
        putSi(v, "PASSIF_EVENTUEL_NATURE", str(c.get("passifEventuelNature")));
        Long m = toLong(c.get("passifEventuelMontant"));
        if (m != null) paire(v, "PASSIF_EVENTUEL_MONTANT", m);
        putSi(v, "PASSIF_EVENTUEL_MODALITES", str(c.get("passifEventuelModalites")));
        String sequestre = str(c.get("passifEventuelSequestreNom"));
        putSi(v, "PASSIF_EVENTUEL_SEQUESTRE_NOM", sequestre);
        if (!sequestre.isBlank()) putSi(v, "PASSIF_EVENTUEL_DUREE", str(c.get("passifEventuelDuree")));
        putSi(v, "PASSIF_EVENTUEL_CHARGE_PERSONNE", str(c.get("passifEventuelChargePersonne")));
    }

    /** SARL : piece d'identite de chaque associe dans la liste des presents. */
    private static void piecesAssocies(Map<String, Object> v, Map<String, Object> safe, boolean au) {
        if (au) return;
        List<Map<String, Object>> lignes = asListOfMaps(v.get("ASSOCIES"));
        List<Map<String, Object>> associes = new ArrayList<>();
        for (Map<String, Object> a : asListOfMaps(safe.get("associes"))) if (!a.isEmpty()) associes.add(a);
        if (lignes.size() != associes.size()) return;
        for (int i = 0; i < lignes.size(); i++) {
            Map<String, Object> a = associes.get(i);
            putSi(lignes.get(i), "ASSOCIE_PIECE_TYPE", str(a.get("pieceType")));
            putSi(lignes.get(i), "ASSOCIE_PIECE_NUMERO", str(first0(a.get("pieceNumero"), a.get("cin"), a.get("cinNumero"))));
        }
    }

    private static void paire(Map<String, Object> v, String base, Long n) {
        if (n == null) return;
        v.put(base + "_CHIFFRES", formatAmount(n));
        v.put(base + "_LETTRES", lettres(n));
    }

    private static GenerationRefuseeException.DonneeManquante manque(String variable, String libelle) {
        return new GenerationRefuseeException.DonneeManquante(variable, libelle, null);
    }
}
