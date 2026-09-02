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
 * Noyau de mapping <b>partagé</b> du workflow Modification (Phase E1) : le PV d'assemblée
 * générale (SARL) / les décisions de l'associé unique (SARL AU) contenant <b>une ou plusieurs
 * résolutions typées</b>.
 *
 * <p>Réutilise le <b>noyau séance d'AG</b> ({@link SeancePvVarsBuilder}) pour la séance
 * (convocation, présence / quorum, bureau, ordre du jour, identité société pré-remplie BD) puis
 * construit la <b>boucle {@code RESOLUTIONS}</b> : chaque résolution porte {@code $RESOLUTION_TYPE}
 * (sélecteur) qui active, à l'expansion par item, le bloc conditionnel correspondant du modèle
 * ({@code ◇ SI : $RESOLUTION_TYPE = « … »}). Les <b>32 types</b> du modèle SARL (28 pour le SARL
 * AU, + {@code cession_parts_pluripersonnelle}) sont couverts.
 *
 * <p><b>Portée des variables :</b> les variables propres à un type sont posées <b>par item</b> de
 * la boucle {@code RESOLUTIONS} (elles ont priorité sur le scope global — cf. moteur), de sorte
 * que deux résolutions hétérogènes ne se marchent pas dessus sur une variable partagée (ex.
 * {@code $ARTICLES_MODIFIES}). Les boucles imbriquées {@code AFFECTATIONS}, {@code BENEFICIAIRES_DPS}
 * et {@code GERANTS} n'apparaissent qu'à l'intérieur d'un type donné (affectation du résultat /
 * augmentation numéraire / nomination) et sont produites au <b>scope global</b> à partir de la
 * <b>première</b> résolution qui les porte (une occurrence par PV — le moteur expanse ces boucles
 * feuilles avant la boucle {@code RESOLUTIONS}).
 *
 * <p>Ce n'est pas un bean : c'est le cœur commun appelé par le mapper de workflow
 * {@link ModificationMapper} pour les codes {@code PV_MODIFICATION_SARL(/_AU)}. Aucune lecture DB :
 * tout provient du payload (identité société + associés + gérants pré-remplis par l'appelant).
 * Montants en lettres via {@link FrenchNumberToLetters}.
 */
public final class ModificationDirecteurMapper {

    static final String TPL_SARL = "PV_MODIFICATION_SARL";
    static final String TPL_SARL_AU = "PV_MODIFICATION_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private static final String[] ORDINALES_F = {"", "PREMIÈRE", "DEUXIÈME", "TROISIÈME",
            "QUATRIÈME", "CINQUIÈME", "SIXIÈME", "SEPTIÈME", "HUITIÈME", "NEUVIÈME", "DIXIÈME",
            "ONZIÈME", "DOUZIÈME", "TREIZIÈME", "QUATORZIÈME", "QUINZIÈME", "SEIZIÈME",
            "DIX-SEPTIÈME", "DIX-HUITIÈME", "DIX-NEUVIÈME", "VINGTIÈME"};

    private ModificationDirecteurMapper() {}

    // ==================================================================
    // Point d'entrée
    // ==================================================================

    static Map<String, Object> pvVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = templateCode != null && templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU");

        // 1) Noyau séance d'AG (identité société pré-remplie BD + présence/quorum + OJ + associés).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> seance = asMap(safe.get("seance"));
        Map<String, Object> cac = asMap(safe.get("commissaireComptes"));

        // 2) Drapeaux / scalaires globaux référencés HORS boucle RESOLUTIONS.
        //    Quorum : par défaut atteint (« oui ») — le défaut de quorum est traité par le PV
        //    d'incident dédié (PV_DEFAUT_QUORUM_*), pas par ce PV de modification.
        put(v, "QUORUM_ATTEINT",
                normOuiNon(first0(safe.get("quorumAtteint"), seance.get("quorumAtteint")), "oui"));
        // Commissaire aux comptes (existence / présence / nom) — pilote plusieurs blocs.
        String cacExiste = normOuiNon(first0(cac.get("existe"), safe.get("cacExiste")), "non");
        put(v, "CAC_EXISTE", cacExiste);
        put(v, "CAC_PRESENT", normOuiNon(first0(cac.get("present"), safe.get("cacPresent")),
                "oui".equals(cacExiste) ? "oui" : "non"));
        put(v, "COMMISSAIRE_COMPTES_NOM",
                first(str(cac.get("nom")), str(safe.get("commissaireComptesNom"))));
        // SARL AU : le gérant est-il l'associé unique ? (art. 75 — communication du rapport).
        put(v, "GERANT_EST_ASSOCIE",
                normOuiNon(first0(safe.get("gerantEstAssocie"), seance.get("gerantEstAssocie")),
                        isAu ? "oui" : "non"));

        // Valeur nominale de la part (fallback global : capital / nombre de parts). Utilisée
        // uniquement dans les blocs d'augmentation / réduction (surchargée par item si fournie).
        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        Long nbParts = toLong(societe.get("nombreParts"));
        String vnGlobal = (capital != null && nbParts != null && nbParts > 0)
                ? formatAmount(capital / nbParts) : "";
        put(v, "VALEUR_NOMINALE_PART", vnGlobal);

        // 3) Boucle RESOLUTIONS typée + boucles imbriquées globales (1 occurrence par PV).
        List<Map<String, Object>> resolutions = asListOfMaps(safe.get("resolutions"));
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> gerantsLoop = null;
        List<Map<String, Object>> affectationsLoop = null;
        List<Map<String, Object>> beneficiairesLoop = null;

        int n = 1;
        for (Map<String, Object> r : resolutions) {
            if (r == null || r.isEmpty()) continue;
            String type = normType(r.get("type"));
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("RESOLUTION_TYPE", type);
            it.put("RESOLUTION_ORDINAL", first(str(r.get("ordinal")), ordinalF(n)));
            it.put("RESOLUTION_OBJET", first(str(r.get("objet")), defaultObjet(type)));
            it.put("RESOLUTION_RESULTAT", first(str(r.get("resultat")), "à l'unanimité"));
            fillTypeVars(it, type, r, vnGlobal);
            out.add(it);
            n++;

            // Boucles imbriquées : capturées depuis la 1re résolution qui les porte.
            if ("affectation_resultat".equals(type) && affectationsLoop == null) {
                affectationsLoop = buildAffectations(r.get("affectations"));
            }
            if ("nomination_gerant".equals(type) && gerantsLoop == null) {
                gerantsLoop = buildGerants(first0(r.get("gerants"), r.get("gerantsNommes")));
            }
            if ("augmentation_capital_numeraire".equals(type) && beneficiairesLoop == null) {
                beneficiairesLoop = buildBeneficiaires(
                        first0(r.get("beneficiairesDps"), r.get("beneficiaires")));
            }
        }
        v.put("RESOLUTIONS", out);
        // Surcharge des boucles imbriquées (le noyau séance a pu poser GERANTS = gérants existants).
        v.put("GERANTS", gerantsLoop == null ? List.of() : gerantsLoop);
        v.put("AFFECTATIONS", affectationsLoop == null ? List.of() : affectationsLoop);
        v.put("BENEFICIAIRES_DPS", beneficiairesLoop == null ? List.of() : beneficiairesLoop);
        return v;
    }

    // ==================================================================
    // Variables propres à chaque type de résolution (32 + AU)
    // ==================================================================

    private static void fillTypeVars(Map<String, Object> it, String type, Map<String, Object> r,
                                     String vnGlobal) {
        switch (type) {
            case "approbation_comptes" -> {
                putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
                put(it, "RESULTAT_SENS", normResultatSens(r.get("resultatSens")));
                putAmount(it, "RESULTAT_MONTANT", r.get("resultatMontant"));
            }
            case "affectation_resultat" -> {
                putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
                putAmount(it, "RESULTAT_MONTANT", r.get("resultatMontant"));
            }
            case "distribution_dividendes" -> {
                putAmount(it, "DIVIDENDE_TOTAL", r.get("dividendeTotal"));
                putAmount(it, "DIVIDENDE_PAR_PART", r.get("dividendeParPart"));
                putDate(it, "DATE_MISE_PAIEMENT", r.get("dateMisePaiement"));
            }
            case "distribution_reserves" -> {
                putAmount(it, "PRELEVEMENT_MONTANT", r.get("prelevementMontant"));
                put(it, "PRELEVEMENT_POSTE", str(r.get("prelevementPoste")));
                putAmount(it, "DIVIDENDE_PAR_PART", r.get("dividendeParPart"));
                putDate(it, "DATE_MISE_PAIEMENT", r.get("dateMisePaiement"));
            }
            case "acompte_dividendes" -> {
                putAmount(it, "ACOMPTE_MONTANT", r.get("acompteMontant"));
                putAmount(it, "ACOMPTE_PAR_PART", r.get("acompteParPart"));
            }
            case "nomination_gerant" -> {
                boolean sortant = "oui".equals(normOuiNon(first0(r.get("gerantSortant"),
                        !str(r.get("gerantSortantNom")).isBlank()), "non"));
                put(it, "GERANT_SORTANT", sortant ? "oui" : "non");
                put(it, "GERANT_SORTANT_NOM", str(r.get("gerantSortantNom")));
                put(it, "GERANT_SORTANT_MOTIF",
                        first(str(r.get("gerantSortantMotif")), "démission"));
                putDate(it, "GERANT_SORTANT_DATE_EFFET", r.get("gerantSortantDateEffet"));
                put(it, "DUREE_GERANCE", first(str(r.get("dureeGerance")), "durée illimitée"));
            }
            case "renouvellement_gerant" -> {
                put(it, "GERANT_NOM", str(r.get("gerantNom")));
                put(it, "DUREE_GERANCE", first(str(r.get("dureeGerance")), "durée illimitée"));
            }
            case "revocation_gerant" ->
                put(it, "GERANT_SORTANT_NOM",
                        first(str(r.get("gerantSortantNom")), str(r.get("gerantNom"))));
            case "remuneration_gerant" -> {
                put(it, "GERANT_NOM", str(r.get("gerantNom")));
                put(it, "REMUNERATION_PERIODICITE",
                        first(str(r.get("remunerationPeriodicite")), "mensuelle"));
                putAmount(it, "REMUNERATION_MONTANT", r.get("remunerationMontant"));
                putDate(it, "REMUNERATION_DATE_EFFET", r.get("remunerationDateEffet"));
            }
            case "conventions_reglementees" -> { /* aucune variable propre (pilote CAC_EXISTE global) */ }
            case "commissaire_comptes" -> {
                put(it, "CAC_ACTION", normCacAction(r.get("cacAction")));
                put(it, "COMMISSAIRE_COMPTES_NOM", str(r.get("commissaireComptesNom")));
                put(it, "DUREE_MANDAT_CAC", first(str(r.get("dureeMandatCac")), "trois"));
                putDate(it, "CAC_FIN_MANDAT_DATE", r.get("cacFinMandatDate"));
            }
            case "ratification_actes_formation" ->
                put(it, "ENGAGEMENTS_MANDAT", str(r.get("engagementsMandat")));
            case "autorisation_gerance" ->
                put(it, "AUTORISATION_OBJET", str(r.get("autorisationObjet")));
            case "augmentation_capital_numeraire" -> {
                putAmount(it, "AUGCAP_MONTANT_CHIFFRES", r.get("augcapMontantChiffres"));
                put(it, "AUGCAP_MONTANT_LETTRES", lettres(r.get("augcapMontantChiffres")));
                putAmount(it, "AUGCAP_NOUVEAU_CAPITAL", r.get("augcapNouveauCapital"));
                put(it, "AUGCAP_MODE", normAugcapMode(r.get("augcapMode")));
                put(it, "AUGCAP_NB_PARTS_NOUVELLES", str(r.get("augcapNbPartsNouvelles")));
                put(it, "VALEUR_NOMINALE_PART", first(str(r.get("valeurNominalePart")), vnGlobal));
                put(it, "AUGCAP_PARTS_DE", str(r.get("augcapPartsDe")));
                put(it, "AUGCAP_PARTS_A", str(r.get("augcapPartsA")));
                putAmount(it, "NOUVELLE_VALEUR_NOMINALE", r.get("nouvelleValeurNominale"));
                put(it, "AUGCAP_LIBERATION_MODE", normLiberationMode(r.get("augcapLiberationMode")));
                String prime = normOuiNon(r.get("primeEmission"), "non");
                put(it, "PRIME_EMISSION", prime);
                putAmount(it, "PRIME_EMISSION_MONTANT", r.get("primeEmissionMontant"));
                putAmount(it, "PRIME_EMISSION_TOTAL", first0(r.get("primeEmissionTotal"),
                        product(r.get("primeEmissionMontant"), r.get("augcapNbPartsNouvelles"))));
                put(it, "DPS_SUPPRESSION", normOuiNon(r.get("dpsSuppression"), "non"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6 et 7"));
            }
            case "augmentation_capital_nature" -> {
                put(it, "COMMISSAIRE_APPORTS_NOM", str(r.get("commissaireApportsNom")));
                put(it, "APPORTEUR_NOM", str(r.get("apporteurNom")));
                put(it, "APPORT_NATURE_DESCRIPTION", str(r.get("apportNatureDescription")));
                putAmount(it, "APPORT_NATURE_VALEUR_CHIFFRES", r.get("apportNatureValeurChiffres"));
                put(it, "APPORT_NATURE_VALEUR_LETTRES", lettres(r.get("apportNatureValeurChiffres")));
                putAmount(it, "AUGCAP_MONTANT_CHIFFRES", r.get("augcapMontantChiffres"));
                putAmount(it, "AUGCAP_NOUVEAU_CAPITAL", r.get("augcapNouveauCapital"));
                put(it, "AUGCAP_NB_PARTS_NOUVELLES", str(r.get("augcapNbPartsNouvelles")));
                put(it, "VALEUR_NOMINALE_PART", first(str(r.get("valeurNominalePart")), vnGlobal));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6 et 7"));
                put(it, "FONDS_COMMERCE", normOuiNon(r.get("fondsCommerce"), "non"));
            }
            case "augmentation_capital_incorporation" -> {
                putAmount(it, "AUGCAP_MONTANT_CHIFFRES", r.get("augcapMontantChiffres"));
                putAmount(it, "AUGCAP_NOUVEAU_CAPITAL", r.get("augcapNouveauCapital"));
                put(it, "INCORPORATION_POSTE", first(str(r.get("incorporationPoste")),
                        "Report à nouveau"));
                put(it, "AUGCAP_MODE", normAugcapMode(r.get("augcapMode")));
                put(it, "AUGCAP_NB_PARTS_NOUVELLES", str(r.get("augcapNbPartsNouvelles")));
                put(it, "VALEUR_NOMINALE_PART", first(str(r.get("valeurNominalePart")), vnGlobal));
                putAmount(it, "NOUVELLE_VALEUR_NOMINALE", r.get("nouvelleValeurNominale"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6 et 7"));
            }
            case "reduction_capital" -> {
                put(it, "REDCAP_MOTIF", normRedcapMotif(r.get("redcapMotif")));
                putAmount(it, "REDCAP_MONTANT", r.get("redcapMontant"));
                putAmount(it, "REDCAP_NOUVEAU_CAPITAL", r.get("redcapNouveauCapital"));
                put(it, "REDCAP_MODE", normRedcapMode(r.get("redcapMode")));
                put(it, "VALEUR_NOMINALE_PART", first(str(r.get("valeurNominalePart")), vnGlobal));
                putAmount(it, "NOUVELLE_VALEUR_NOMINALE", r.get("nouvelleValeurNominale"));
                put(it, "REDCAP_NB_PARTS_NOUVELLES", str(r.get("redcapNbPartsNouvelles")));
                putAmount(it, "RACHAT_PRIX_PART", r.get("rachatPrixPart"));
                putDate(it, "RACHAT_DATE_LIMITE", r.get("rachatDateLimite"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6 et 7"));
            }
            case "modification_denomination" -> {
                put(it, "NOUVELLE_DENOMINATION", str(r.get("nouvelleDenomination")));
                putDate(it, "DATE_EFFET", r.get("dateEffet"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "2"));
            }
            case "modification_objet" -> {
                put(it, "OBJET_ACTION", normObjetAction(r.get("objetAction")));
                put(it, "OBJET_MODIFICATION", str(r.get("objetModification")));
                putDate(it, "DATE_EFFET", r.get("dateEffet"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "3"));
            }
            case "transfert_siege" -> {
                // Fix M1 (2026-08-16) — repli aligné sur le libellé AFFICHÉ par le
                // formulaire (« Non » en tête de liste). Le défaut « oui » faisait
                // écrire « située dans la MÊME préfecture » quand la clé n'arrivait
                // pas : c'est l'affirmation la plus lourde des deux (elle dispense
                // des formalités au greffe du nouveau ressort), donc jamais par défaut.
                put(it, "SIEGE_MEME_PREFECTURE", normOuiNon(r.get("siegeMemePrefecture"), "non"));
                put(it, "NOUVEAU_SIEGE", str(r.get("nouveauSiege")));
                putDate(it, "DATE_EFFET", r.get("dateEffet"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "4"));
            }
            case "prorogation_duree" -> {
                putDate(it, "DATE_EXPIRATION_INITIALE", r.get("dateExpirationInitiale"));
                put(it, "PROROGATION_DUREE", str(r.get("prorogationDuree")));
                putDate(it, "NOUVELLE_DUREE_ECHEANCE", r.get("nouvelleDureeEcheance"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "5"));
            }
            case "modification_exercice" -> {
                put(it, "NOUVELLE_DATE_CLOTURE", str(r.get("nouvelleDateCloture")));
                put(it, "EXERCICE_TRANSITOIRE_DUREE", str(r.get("exerciceTransitoireDuree")));
                putDate(it, "EXERCICE_TRANSITOIRE_DEBUT", r.get("exerciceTransitoireDebut"));
                putDate(it, "EXERCICE_TRANSITOIRE_FIN", r.get("exerciceTransitoireFin"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "24"));
            }
            case "mise_harmonie_statuts" -> { /* aucune variable propre */ }
            case "modification_statuts_autre" -> {
                put(it, "ARTICLES_MODIFIES", str(r.get("articlesModifies")));
                put(it, "STATUTS_NOUVEAU_LIBELLE", str(r.get("statutsNouveauLibelle")));
            }
            case "agrement_cession" -> {
                put(it, "CESSION_NB_PARTS", str(r.get("cessionNbParts")));
                put(it, "CESSION_PARTS_DE", str(r.get("cessionPartsDe")));
                put(it, "CESSION_PARTS_A", str(r.get("cessionPartsA")));
                put(it, "CEDANT_NOM", str(r.get("cedantNom")));
                put(it, "CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
                putAmount(it, "CESSION_PRIX", r.get("cessionPrix"));
                put(it, "CESSIONNAIRE_TIERS", normOuiNon(r.get("cessionnaireTiers"), "oui"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6 et 7"));
            }
            case "agrement_transmission" -> {
                put(it, "CESSION_NB_PARTS", str(r.get("cessionNbParts")));
                put(it, "TRANSMISSION_ORIGINE",
                        first(str(r.get("transmissionOrigine")), "une succession"));
                put(it, "CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6 et 7"));
            }
            case "nantissement_parts" -> {
                put(it, "NANTISSEMENT_NB_PARTS", str(r.get("nantissementNbParts")));
                put(it, "NANTISSEMENT_CONSTITUANT_NOM", str(r.get("nantissementConstituantNom")));
                put(it, "NANTISSEMENT_CREANCIER_NOM", str(r.get("nantissementCreancierNom")));
                put(it, "NANTISSEMENT_CREANCE_OBJET", str(r.get("nantissementCreanceObjet")));
            }
            case "transformation" -> {
                put(it, "COMMISSAIRE_TRANSFORMATION_EXISTE",
                        normOuiNon(first0(r.get("commissaireTransformationExiste"),
                                !str(r.get("commissaireTransformationNom")).isBlank()), "non"));
                put(it, "COMMISSAIRE_TRANSFORMATION_NOM", str(r.get("commissaireTransformationNom")));
                put(it, "TRANSFORMATION_FORME",
                        first(str(r.get("transformationForme")), "société anonyme"));
                put(it, "TRANSFORMATION_ORGANES", str(r.get("transformationOrganes")));
            }
            case "designation_commissaire_transformation" ->
                put(it, "COMMISSAIRE_TRANSFORMATION_NOM", str(r.get("commissaireTransformationNom")));
            case "capitaux_propres_art86" ->
                putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
            case "operation_restructuration" -> {
                put(it, "RESTRUCTURATION_NATURE",
                        first(str(r.get("restructurationNature")), "fusion"));
                put(it, "RESTRUCTURATION_MODALITES", str(r.get("restructurationModalites")));
            }
            case "cession_parts_pluripersonnelle" -> {
                put(it, "CESSION_NB_PARTS", str(r.get("cessionNbParts")));
                put(it, "CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
                putAmount(it, "CESSION_PRIX", r.get("cessionPrix"));
                put(it, "ARTICLES_MODIFIES", first(str(r.get("articlesModifies")), "6, 7 et 8"));
            }
            case "pouvoirs_formalites" -> { /* aucune variable propre */ }
            default -> { /* type inconnu : seuls ordinal / objet / résultat sont rendus */ }
        }
    }

    // ==================================================================
    // Boucles imbriquées
    // ==================================================================

    private static List<Map<String, Object>> buildAffectations(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : asListOfMaps(raw)) {
            if (a == null || a.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("AFFECTATION_POSTE", str(a.get("poste")));
            it.put("AFFECTATION_MONTANT", amount(first0(a.get("montant"), a.get("montantChiffres"))));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildGerants(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : asListOfMaps(raw)) {
            if (g == null || g.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("GERANT_CIVILITE", normCivilite(g.get("civilite")));
            it.put("GERANT_PRENOM", str(g.get("prenom")));
            it.put("GERANT_NOM", str(g.get("nom")));
            it.put("GERANT_NATIONALITE", first(str(g.get("nationalite")), "marocaine"));
            it.put("GERANT_DATE_NAISSANCE", dateStr(g.get("dateNaissance")));
            it.put("GERANT_ADRESSE", str(g.get("adresse")));
            it.put("GERANT_PIECE_TYPE", first(str(g.get("pieceType")), "CIN"));
            it.put("GERANT_PIECE_NUMERO", first(str(g.get("pieceNumero")), str(g.get("cin"))));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildBeneficiaires(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> b : asListOfMaps(raw)) {
            if (b == null || b.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("BENEFICIAIRE_NOM", str(b.get("nom")));
            it.put("BENEFICIAIRE_NB_PARTS", str(first0(b.get("nbParts"), b.get("nombreParts"))));
            out.add(it);
        }
        return out;
    }

    // ==================================================================
    // Objets par défaut (« Objet de la résolution : … »)
    // ==================================================================

    private static String defaultObjet(String type) {
        return switch (type) {
            case "approbation_comptes" -> "Approbation des comptes de l'exercice";
            case "affectation_resultat" -> "Affectation du résultat";
            case "distribution_dividendes" -> "Distribution de dividendes";
            case "distribution_reserves" -> "Distribution de réserves";
            case "acompte_dividendes" -> "Distribution d'un acompte sur dividendes";
            case "nomination_gerant" -> "Nomination d'un gérant";
            case "renouvellement_gerant" -> "Renouvellement du mandat de gérant";
            case "revocation_gerant" -> "Révocation d'un gérant";
            case "remuneration_gerant" -> "Rémunération de la gérance";
            case "conventions_reglementees" -> "Approbation des conventions réglementées";
            case "commissaire_comptes" -> "Commissaire aux comptes";
            case "ratification_actes_formation" -> "Ratification des actes de la période de formation";
            case "autorisation_gerance" -> "Autorisation donnée à la gérance";
            case "augmentation_capital_numeraire" -> "Augmentation de capital en numéraire";
            case "augmentation_capital_nature" -> "Augmentation de capital par apport en nature";
            case "augmentation_capital_incorporation" -> "Augmentation de capital par incorporation de réserves";
            case "reduction_capital" -> "Réduction de capital";
            case "modification_denomination" -> "Modification de la dénomination sociale";
            case "modification_objet" -> "Modification de l'objet social";
            case "transfert_siege" -> "Transfert du siège social";
            case "prorogation_duree" -> "Prorogation de la durée de la société";
            case "modification_exercice" -> "Modification de la date de clôture de l'exercice";
            case "mise_harmonie_statuts" -> "Mise en harmonie des statuts";
            case "modification_statuts_autre" -> "Modification statutaire";
            case "agrement_cession" -> "Agrément d'une cession de parts sociales";
            case "agrement_transmission" -> "Agrément d'une transmission de parts sociales";
            case "nantissement_parts" -> "Consentement à un nantissement de parts sociales";
            case "transformation" -> "Transformation de la société";
            case "designation_commissaire_transformation" -> "Désignation du commissaire à la transformation";
            case "capitaux_propres_art86" -> "Capitaux propres inférieurs au quart du capital (art. 86)";
            case "operation_restructuration" -> "Opération de restructuration";
            case "cession_parts_pluripersonnelle" -> "Cession de parts et passage en SARL pluripersonnelle";
            case "pouvoirs_formalites" -> "Pouvoirs pour les formalités";
            default -> "Résolution";
        };
    }

    // ==================================================================
    // Normalisations (libellés EXACTS comparés par le moteur)
    // ==================================================================

    /** Normalise le type de résolution en snake_case (tolère accents / espaces / casse). */
    static String normType(Object raw) {
        String s = norm(raw).replace(' ', '_').replace('-', '_').replaceAll("_+", "_");
        return s;
    }

    private static String normResultatSens(Object raw) {
        String s = norm(raw);
        if (s.startsWith("perte") || s.startsWith("defic") || s.startsWith("mali")) return "perte";
        return "bénéfice";
    }

    /** « nomination » / « renouvellement » / « fin de mandat ». */
    private static String normCacAction(Object raw) {
        String s = norm(raw);
        if (s.startsWith("renouv")) return "renouvellement";
        if (s.startsWith("fin") || s.contains("cessation")) return "fin de mandat";
        return "nomination";
    }

    /** « création de parts nouvelles » / « élévation du nominal ». */
    private static String normAugcapMode(Object raw) {
        String s = norm(raw);
        if (s.contains("elevation") || s.contains("nominal")) return "élévation du nominal";
        return "création de parts nouvelles";
    }

    /** « numéraire » / « compensation ». */
    private static String normLiberationMode(Object raw) {
        String s = norm(raw);
        if (s.contains("compensation") || s.contains("creance")) return "compensation";
        return "numéraire";
    }

    /** « pertes » / « non motivée par des pertes ». */
    private static String normRedcapMotif(Object raw) {
        String s = norm(raw);
        if (s.contains("non") || s.contains("rachat")) return "non motivée par des pertes";
        return "pertes";
    }

    /** « diminution du nominal » / « réduction du nombre de parts » / « annulation de parts rachetées ». */
    private static String normRedcapMode(Object raw) {
        String s = norm(raw);
        if (s.contains("annul") || s.contains("rachat")) return "annulation de parts rachetées";
        if (s.contains("nombre")) return "réduction du nombre de parts";
        return "diminution du nominal";
    }

    /** « extension » / « modification » de l'objet. */
    private static String normObjetAction(Object raw) {
        String s = norm(raw);
        if (s.startsWith("exten") || s.contains("ajout")) return "extension";
        return "modification";
    }

    private static String ordinalF(int n) {
        return (n >= 1 && n < ORDINALES_F.length) ? ORDINALES_F[n] : (n + "E");
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

    // ==================================================================
    // Helpers (coercion / format) — alignés sur SeancePvVarsBuilder
    // ==================================================================

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static void putAmount(Map<String, Object> v, String key, Object raw) {
        v.put(key, amount(raw));
    }

    private static void putDate(Map<String, Object> v, String key, Object raw) {
        v.put(key, dateStr(raw));
    }

    /** Montant en chiffres formaté (séparateur de milliers), "" si absent. */
    private static String amount(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : formatAmount(n);
    }

    /** Montant en toutes lettres, "" si absent. */
    private static String lettres(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : FrenchNumberToLetters.numberToLetters(n);
    }

    /** Produit de deux nombres (prime globale = prime/part × nb parts), null si l'un manque. */
    private static Long product(Object a, Object b) {
        Long x = toLong(a);
        Long y = toLong(b);
        return (x == null || y == null) ? null : x * y;
    }

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

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "");
            if (s.isEmpty()) return null;
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
