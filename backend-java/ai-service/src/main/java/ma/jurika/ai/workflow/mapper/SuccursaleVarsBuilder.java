package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.format.NationaliteFrancaise;
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
 * Noyau de mapping <b>partagé</b> des workflows Succursale (Phase D) :
 * <ul>
 *   <li>Création de succursale d'une société <b>marocaine</b> — {@code PV_CREATION_SUCCURSALE_MAROC_SARL(/_AU)} ;</li>
 *   <li>Création de succursale au Maroc d'une société <b>étrangère</b> — {@code PV_CREATION_SUCCURSALE_ETRANGERE_SARL(/_AU)} ;</li>
 *   <li><b>Fermeture</b> de succursale — {@code PV_FERMETURE_SUCCURSALE_SARL(/_AU)}.</li>
 * </ul>
 *
 * <p>Réutilise le <b>noyau séance d'AG</b> ({@link SeancePvVarsBuilder}) pour l'assemblée des
 * associés (société marocaine) ou pour la réunion de l'organe compétent (société étrangère), puis
 * ajoute les variables propres à la succursale ({@code $SUCCURSALE_*}), à la société mère étrangère
 * ({@code $SOCIETE_MERE_*}, {@code $ORGANE_*}, {@code $REPRESENTANT_SUCCURSALE_*}) et au mandataire
 * des formalités. La <b>boucle RESOLUTIONS</b> est construite ici à partir des champs structurés,
 * en reprenant la rédaction directeur des « résolutions/décisions types » (ouverture / désignation
 * du responsable ou représentant / pouvoirs pour formalités ; fermeture / cessation / radiation).
 *
 * <p>Ce n'est pas un bean : c'est le cœur commun appelé par les trois mappers minces
 * {@link SuccursaleMaMapper}, {@link SuccursaleEtrMapper} et {@link FermetureSuccursaleMapper}.
 * Pour la création MA et la fermeture, l'identité de la société marocaine est pré-remplie depuis la
 * BD (dossier) par l'enrichisseur en amont ; pour la société mère étrangère, tout est saisi.
 * Aucune lecture DB ici : tout provient du payload. Aucun marqueur {@code $…} résiduel : toutes les
 * variables des modèles sont systématiquement produites.
 */
public final class SuccursaleVarsBuilder {

    /**
     * Marqueur neutre des données attribuées par le greffe APRÈS le dépôt.
     * Même valeur que {@code CreationDirecteurVarsBuilder.POST_IMMAT} : un avis
     * dit ce qui reste à compléter plutôt que de laisser un blanc dans la phrase.
     */
    private static final String POST_IMMAT = "[à compléter après immatriculation]";

    static final String TPL_MA_SARL = "PV_CREATION_SUCCURSALE_MAROC_SARL";
    static final String TPL_MA_SARL_AU = "PV_CREATION_SUCCURSALE_MAROC_SARL_AU";
    static final String TPL_ETR_SARL = "PV_CREATION_SUCCURSALE_ETRANGERE_SARL";
    static final String TPL_ETR_SARL_AU = "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU";
    static final String TPL_FERM_SARL = "PV_FERMETURE_SUCCURSALE_SARL";
    static final String TPL_FERM_SARL_AU = "PV_FERMETURE_SUCCURSALE_SARL_AU";

    /** Lot DIVERS §B (2026-08-13) — avis d'OUVERTURE de succursale (annonce légale). */
    static final String TPL_ANNONCE_OUV_SARL = "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL";
    static final String TPL_ANNONCE_OUV_SARL_AU = "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU";

    /**
     * Lot DIVERS §C (2026-08-13) — variante ÉTRANGÈRE du même avis. ⚠ modèles
     * {@code origin = derive-jurika} (non directeur), à faire valider.
     */
    static final String TPL_ANNONCE_OUV_ETR_SARL =
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL";
    static final String TPL_ANNONCE_OUV_ETR_SARL_AU =
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU";

    /** Lot DIVERS §D (2026-08-13) — avis de FERMETURE de succursale (annonce légale). */
    static final String TPL_ANNONCE_FERM_SARL = "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL";
    static final String TPL_ANNONCE_FERM_SARL_AU = "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private static final String[] ORDINALES = {"", "PREMIÈRE", "DEUXIÈME", "TROISIÈME",
            "QUATRIÈME", "CINQUIÈME", "SIXIÈME", "SEPTIÈME", "HUITIÈME"};

    private SuccursaleVarsBuilder() {}

    // ==================================================================
    // 1) Création de succursale — société MAROCAINE (AG des associés)
    // ==================================================================

    static Map<String, Object> ouvertureMaVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = isAu(templateCode);

        // Noyau séance d'AG (identité société marocaine pré-remplie BD + présence/quorum + OJ).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> succ = asMap(safe.get("succursale"));
        putSuccursaleCommun(v, succ);
        putDotation(v, succ);
        String respPresent = putResponsable(v, succ, false);
        String mandataire = mandataireNom(safe);

        // Boucle RESOLUTIONS : ouverture + (désignation responsable) + pouvoirs formalités.
        List<Map<String, Object>> res = new ArrayList<>();
        String villeGreffe = str(v.get("SUCCURSALE_VILLE_GREFFE"));
        String voixPour = defaultVoixPour(v);
        res.add(resolution("Ouverture de la succursale",
                "Décision est prise d'ouvrir une succursale de la société à "
                        + str(v.get("SUCCURSALE_ADRESSE")) + " (" + str(v.get("SUCCURSALE_VILLE"))
                        + "), sous l'enseigne " + str(v.get("SUCCURSALE_ENSEIGNE"))
                        + ", pour l'activité de " + str(v.get("SUCCURSALE_ACTIVITE"))
                        + ", à compter du " + str(v.get("SUCCURSALE_DATE_OUVERTURE")) + ".",
                isAu, voixPour));
        if ("oui".equals(respPresent)) {
            res.add(resolution("Désignation du responsable de la succursale",
                    "Est nommé(e) responsable de la succursale " + responsableLabel(v)
                            + ", de nationalité " + str(v.get("SUCCURSALE_RESPONSABLE_NATIONALITE"))
                            + ", demeurant à " + str(v.get("SUCCURSALE_RESPONSABLE_ADRESSE"))
                            + ", titulaire de la " + str(v.get("SUCCURSALE_RESPONSABLE_PIECE_TYPE"))
                            + " n° " + str(v.get("SUCCURSALE_RESPONSABLE_PIECE_NUMERO"))
                            + ", avec les pouvoirs suivants : "
                            + str(v.get("SUCCURSALE_RESPONSABLE_POUVOIRS")) + ".",
                    isAu, voixPour));
        }
        res.add(resolution("Pouvoirs à l'effet des formalités",
                "Tous pouvoirs sont donnés à " + mandataire
                        + " à l'effet d'accomplir l'inscription modificative au registre du commerce"
                        + " du siège et la déclaration d'immatriculation de la succursale au registre"
                        + " du commerce de " + villeGreffe + ".",
                isAu, voixPour));
        finalizeResolutions(v, res, isAu);
        return v;
    }

    // ==================================================================
    // 2) Création de succursale au Maroc — société ÉTRANGÈRE (organe compétent)
    // ==================================================================

    static Map<String, Object> ouvertureEtrVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = isAu(templateCode);

        // Noyau séance : bureau (président / secrétaire), ordre du jour, clôture. La société
        // marocaine n'est pas décrite ici (variables $DENOMINATION… non référencées par le modèle).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        // Société mère étrangère (saisie — pas d'identité BD) + organe décisionnaire.
        // Mêmes helpers que l'annonce étrangère : PV et avis ne peuvent pas diverger.
        Map<String, Object> organe = asMap(safe.get("organe"));
        // PV étranger : « de droit $SOCIETE_MERE_PAYS » → adjectif.
        putSocieteMere(v, asMap(safe.get("societeMere")), true);
        putOrgane(v, organe, asMap(safe.get("seance")), isAu);

        // Succursale au Maroc.
        Map<String, Object> succ = asMap(safe.get("succursale"));
        putSuccursaleCommun(v, succ);
        putDotation(v, succ);

        // Représentant résident au Maroc.
        Map<String, Object> rep = asMap(safe.get("representant"));
        put(v, "REPRESENTANT_SUCCURSALE_CIVILITE", normCivilite(rep.get("civilite")));
        put(v, "REPRESENTANT_SUCCURSALE_PRENOM", str(rep.get("prenom")));
        put(v, "REPRESENTANT_SUCCURSALE_NOM", str(rep.get("nom")));
        put(v, "REPRESENTANT_SUCCURSALE_NATIONALITE", str(rep.get("nationalite")));
        put(v, "REPRESENTANT_SUCCURSALE_ADRESSE", str(rep.get("adresse")));
        put(v, "REPRESENTANT_SUCCURSALE_PIECE_TYPE", first(str(rep.get("pieceType")), "carte de séjour"));
        put(v, "REPRESENTANT_SUCCURSALE_PIECE_NUMERO", first(str(rep.get("pieceNumero")),
                str(rep.get("cin"))));
        put(v, "REPRESENTANT_SUCCURSALE_POUVOIRS", first(str(rep.get("pouvoirs")),
                "diriger la succursale et représenter la société auprès des tiers et des"
                        + " administrations marocaines"));

        String mandataire = mandataireNom(safe);
        String villeGreffe = str(v.get("SUCCURSALE_VILLE_GREFFE"));
        String voixPour = first(str(organe.get("voixPour")), "l'unanimité des membres présents");

        List<Map<String, Object>> res = new ArrayList<>();
        res.add(resolution("Ouverture d'une succursale au Maroc",
                "Décision est prise d'ouvrir une succursale au Maroc à " + str(v.get("SUCCURSALE_ADRESSE"))
                        + " (" + str(v.get("SUCCURSALE_VILLE")) + "), sous l'enseigne "
                        + str(v.get("SUCCURSALE_ENSEIGNE")) + ", pour l'activité de "
                        + str(v.get("SUCCURSALE_ACTIVITE")) + ", à compter du "
                        + str(v.get("SUCCURSALE_DATE_OUVERTURE")) + ".",
                isAu, voixPour));
        res.add(resolution("Désignation du représentant résident au Maroc",
                "Est nommé(e) représentant de la succursale au Maroc " + representantLabel(v)
                        + ", de nationalité " + str(v.get("REPRESENTANT_SUCCURSALE_NATIONALITE"))
                        + ", demeurant à " + str(v.get("REPRESENTANT_SUCCURSALE_ADRESSE"))
                        + ", titulaire de la " + str(v.get("REPRESENTANT_SUCCURSALE_PIECE_TYPE"))
                        + " n° " + str(v.get("REPRESENTANT_SUCCURSALE_PIECE_NUMERO"))
                        + ", avec les pouvoirs suivants : "
                        + str(v.get("REPRESENTANT_SUCCURSALE_POUVOIRS")) + ".",
                isAu, voixPour));
        res.add(resolution("Pouvoirs à l'effet des formalités",
                "Tous pouvoirs sont donnés à " + mandataire
                        + " à l'effet d'accomplir l'immatriculation de la succursale au registre du"
                        + " commerce de " + villeGreffe
                        + " et l'ensemble des formalités légales et administratives au Maroc.",
                isAu, voixPour));
        finalizeResolutions(v, res, isAu);
        return v;
    }

    // ==================================================================
    // 3) Fermeture de succursale — société MAROCAINE (AG des associés)
    // ==================================================================

    static Map<String, Object> fermetureVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = isAu(templateCode);

        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> succ = asMap(safe.get("succursale"));
        put(v, "SUCCURSALE_ENSEIGNE", str(succ.get("enseigne")));
        put(v, "SUCCURSALE_ADRESSE", str(succ.get("adresse")));
        put(v, "SUCCURSALE_VILLE", str(succ.get("ville")));
        put(v, "SUCCURSALE_VILLE_GREFFE", first(str(succ.get("villeGreffe")), str(succ.get("ville"))));
        put(v, "SUCCURSALE_ACTIVITE", str(succ.get("activite")));
        put(v, "SUCCURSALE_RC_NUMERO", first(str(succ.get("rcNumero")), str(succ.get("rc"))));
        put(v, "SUCCURSALE_DATE_FERMETURE", dateStr(first0(succ.get("dateFermeture"),
                succ.get("dateEffet"))));
        put(v, "SUCCURSALE_MOTIF", first(str(succ.get("motif")),
                "cessation de l'activité exercée dans la succursale"));
        // Responsable dont les fonctions cessent (identité réduite : civilité / prénom / nom).
        String respPresent = putResponsable(v, succ, true);
        String mandataire = mandataireNom(safe);
        String villeGreffe = str(v.get("SUCCURSALE_VILLE_GREFFE"));
        String voixPour = defaultVoixPour(v);

        List<Map<String, Object>> res = new ArrayList<>();
        res.add(resolution("Fermeture de la succursale",
                "Décision est prise de fermer la succursale sise à " + str(v.get("SUCCURSALE_ADRESSE"))
                        + " (" + str(v.get("SUCCURSALE_VILLE")) + "), immatriculée sous le numéro "
                        + str(v.get("SUCCURSALE_RC_NUMERO")) + ", avec effet au "
                        + str(v.get("SUCCURSALE_DATE_FERMETURE")) + ".",
                isAu, voixPour));
        if ("oui".equals(respPresent)) {
            res.add(resolution("Cessation des fonctions du responsable",
                    "Il est constaté la cessation des fonctions de " + responsableLabel(v)
                            + ", responsable de la succursale, à la date d'effet de la fermeture.",
                    isAu, voixPour));
        }
        res.add(resolution("Pouvoirs à l'effet des formalités",
                "Tous pouvoirs sont donnés à " + mandataire
                        + " à l'effet de procéder à la radiation de la succursale au registre du"
                        + " commerce de " + villeGreffe + " et à l'inscription modificative au"
                        + " registre du commerce du siège.",
                isAu, voixPour));
        finalizeResolutions(v, res, isAu);
        return v;
    }

    // ==================================================================
    // 4) Annonce légale d'OUVERTURE de succursale (lot DIVERS §B, 2026-08-13)
    // ==================================================================

    /**
     * Variables de l'<b>avis d'ouverture de succursale</b>
     * ({@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL} / {@code _SARL_AU}).
     *
     * <p>Avis <b>linéaire à deux blocs conditionnels</b> (dotation, responsable) : seul le
     * chapeau diffère entre les deux formes (« les associés … ont décidé » vs « l'associé
     * unique … a décidé »), porté par le modèle lui-même — les variables sont donc identiques.
     *
     * <p>Le corps réutilise EXACTEMENT les mêmes variables que le PV d'ouverture
     * ({@link #ouvertureMaVars}) : l'enseigne, l'adresse, l'activité, la date d'ouverture, la
     * dotation et le responsable de l'avis ne peuvent pas diverger du PV, puisqu'ils sont
     * produits par les mêmes {@code putSuccursaleCommun / putDotation / putResponsable}.
     * L'identité de la société mère vient du dossier (BD) via le noyau séance — jamais saisie.
     *
     * <p><b>{@code $DATE_DEPOT_LEGAL} / {@code $DEPOT_LEGAL_NUMERO}</b> : attribués par le
     * greffe <b>APRÈS</b> le dépôt, donc inconnus à la génération. Rendus <b>vides</b> si non
     * fournis (aucun marqueur résiduel) — même contrat que les annonces de modification,
     * de dissolution et de clôture de liquidation.
     *
     * <p>L'avis ne porte AUCUNE résolution : la boucle {@code RESOLUTIONS} n'est pas produite.
     */
    static Map<String, Object> ouvertureAnnonceVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // Identité société mère (BD) + $ASSEMBLEE_DATE via le noyau séance.
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> succ = asMap(safe.get("succursale"));
        putSuccursaleCommun(v, succ);
        putDotation(v, succ);
        putResponsable(v, succ, false);

        putDepotLegal(v, safe);
        return v;
    }

    // ==================================================================
    // 6) Annonce légale de FERMETURE de succursale (lot DIVERS §D)
    // ==================================================================

    /**
     * Variables de l'<b>avis de fermeture de succursale</b>
     * ({@code ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL} / {@code _SARL_AU}).
     *
     * <p>Avis <b>linéaire</b> (aucun bloc conditionnel) : la fermeture publie l'enseigne,
     * l'adresse, le RC de la succursale, la date d'effet et le <b>motif</b> — ce dernier
     * étant obligatoire dans l'avis (« Cette fermeture est motivée par … »), contrairement
     * à la dissolution où le motif reste au PV.
     *
     * <p>Le corps réutilise EXACTEMENT les mêmes variables que le PV de fermeture
     * ({@link #fermetureVars}) : mêmes helpers, donc aucune divergence possible entre
     * l'acte et l'avis. L'identité de la société mère vient du dossier (BD).
     *
     * <p><b>{@code $DATE_DEPOT_LEGAL} / {@code $DEPOT_LEGAL_NUMERO}</b> : attribués par le
     * greffe APRÈS le dépôt → rendus vides si non fournis.
     */
    static Map<String, Object> fermetureAnnonceVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> succ = asMap(safe.get("succursale"));
        put(v, "SUCCURSALE_ENSEIGNE", str(succ.get("enseigne")));
        put(v, "SUCCURSALE_ADRESSE", str(succ.get("adresse")));
        put(v, "SUCCURSALE_VILLE", str(succ.get("ville")));
        put(v, "SUCCURSALE_VILLE_GREFFE", first(str(succ.get("villeGreffe")), str(succ.get("ville"))));
        put(v, "SUCCURSALE_ACTIVITE", str(succ.get("activite")));
        put(v, "SUCCURSALE_RC_NUMERO", first(str(succ.get("rcNumero")), str(succ.get("rc"))));
        put(v, "SUCCURSALE_DATE_FERMETURE", dateStr(first0(succ.get("dateFermeture"),
                succ.get("dateEffet"))));
        put(v, "SUCCURSALE_MOTIF", str(succ.get("motif")));
        // Le responsable dont les fonctions cessent figure au PV, pas dans l'avis :
        // on le produit tout de même (identité réduite) pour un payload homogène.
        putResponsable(v, succ, true);

        putDepotLegal(v, safe);
        return v;
    }

    // ==================================================================
    // 5) Annonce légale d'OUVERTURE — société mère ÉTRANGÈRE (lot DIVERS §C)
    // ==================================================================

    /**
     * Variables de l'<b>avis d'ouverture de succursale d'une société mère ÉTRANGÈRE</b>
     * ({@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL} / {@code _SARL_AU}).
     *
     * <p>⚠ Ces deux modèles sont <b>dérivés</b> ({@code origin = derive-jurika}), pas livrés
     * par le directeur : sa variante 05_ suppose une mère marocaine (capital en DH, RC
     * marocain) et ne porte aucune variable {@code $SOCIETE_MERE_*}.
     *
     * <p>Le <b>corps</b> est strictement le même que la variante marocaine : les blocs
     * succursale / dotation / responsable sont produits par les MÊMES helpers, donc l'avis
     * ne peut pas diverger du PV étranger ({@link #ouvertureEtrVars}). Seul le chapeau
     * change : identité de la mère ({@code $SOCIETE_MERE_*}) et organe décisionnaire
     * ({@code $ORGANE_COMPETENT}) au lieu de « les associés » / « l'associé unique ».
     *
     * <p>Le dépôt légal se fait au greffe de la SUCCURSALE (la mère n'a pas de greffe
     * marocain) : le modèle dérivé référence {@code $SUCCURSALE_VILLE_GREFFE}.
     */
    static Map<String, Object> ouvertureEtrAnnonceVars(String templateCode,
                                                       Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = isAu(templateCode);

        // Noyau séance : fournit $ASSEMBLEE_DATE (date de la décision de l'organe).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        // Annonce légale : « Siège social : …, $SOCIETE_MERE_PAYS » → nom du pays.
        putSocieteMere(v, asMap(safe.get("societeMere")), false);
        putOrgane(v, asMap(safe.get("organe")), asMap(safe.get("seance")), isAu);

        Map<String, Object> succ = asMap(safe.get("succursale"));
        putSuccursaleCommun(v, succ);
        putDotation(v, succ);
        // Le responsable de l'avis étranger est le représentant résident au Maroc :
        // on accepte les deux clés (`responsable` du bloc succursale, `representant`
        // du PV étranger) pour ne jamais forcer une double saisie.
        Map<String, Object> resp = asMap(succ.get("responsable"));
        if (resp.isEmpty()) {
            Map<String, Object> rep = asMap(safe.get("representant"));
            if (!rep.isEmpty()) {
                Map<String, Object> merged = new LinkedHashMap<>(succ);
                merged.put("responsable", rep);
                if (succ.get("responsablePresent") == null) merged.put("responsablePresent", true);
                succ = merged;
            }
        }
        putResponsable(v, succ, false);

        putDepotLegal(v, safe);
        return v;
    }

    // ------------------------------------------------------------------
    // Blocs communs succursale
    // ------------------------------------------------------------------

    /**
     * Identité de la société mère étrangère (saisie — aucune identité en base).
     *
     * @param adjectifDeDroit rendre {@code $SOCIETE_MERE_PAYS} sous forme d'ADJECTIF de
     *   nationalité au lieu du nom de pays. Grammaire d'assemblage (2026-08-17) : les
     *   PV écrivent « $SOCIETE_MERE_FORME de droit $SOCIETE_MERE_PAYS », locution qui
     *   appelle un adjectif — on lisait « GmbH DE DROIT Allemagne » au lieu de « GmbH de
     *   droit allemand ». L'ANNONCE, elle, publie « Siège social : …, $SOCIETE_MERE_PAYS »
     *   et veut le NOM du pays. La même donnée occupe deux rôles grammaticaux : c'est
     *   donc le modèle appelant qui tranche, jamais la saisie.
     */
    private static void putSocieteMere(Map<String, Object> v, Map<String, Object> mere,
                                       boolean adjectifDeDroit) {
        put(v, "SOCIETE_MERE_DENOMINATION", str(mere.get("denomination")));
        put(v, "SOCIETE_MERE_FORME", str(mere.get("forme")));
        String pays = str(mere.get("pays"));
        put(v, "SOCIETE_MERE_PAYS",
                adjectifDeDroit ? NationaliteFrancaise.adjectifDeDroit(pays) : pays);
        put(v, "SOCIETE_MERE_CAPITAL", str(mere.get("capital")));
        put(v, "SOCIETE_MERE_SIEGE", str(mere.get("siege")));
        put(v, "SOCIETE_MERE_REGISTRE", str(mere.get("registre")));
        put(v, "SOCIETE_MERE_REGISTRE_NUMERO", first(str(mere.get("registreNumero")),
                str(mere.get("registreNum"))));
        put(v, "SOCIETE_MERE_LOI_APPLICABLE", str(mere.get("loiApplicable")));
    }

    /** Organe décisionnaire de la mère étrangère (accord du texte publié). */
    private static void putOrgane(Map<String, Object> v, Map<String, Object> organe,
                                  Map<String, Object> seance, boolean isAu) {
        put(v, "ORGANE_COMPETENT", first(str(organe.get("competent")),
                isAu ? "l'associé unique" : "l'assemblée générale des associés"));
        put(v, "ORGANE_DECISION_DATE", dateStr(first0(organe.get("date"), seance.get("date"))));
        put(v, "ORGANE_DECISION_HEURE", first(str(organe.get("heure")), str(seance.get("heure"))));
        put(v, "ORGANE_DECISION_LIEU", first(str(organe.get("lieu")), str(seance.get("lieu"))));
        // L'avis publie $ASSEMBLEE_DATE : à défaut de date de séance, on retient
        // celle de la décision de l'organe (c'est le même événement).
        if (str(v.get("ASSEMBLEE_DATE")).isBlank()) {
            put(v, "ASSEMBLEE_DATE", str(v.get("ORGANE_DECISION_DATE")));
        }
    }

    /**
     * Dépôt légal : {@code $DATE_DEPOT_LEGAL} et {@code $DEPOT_LEGAL_NUMERO} sont attribués par
     * le greffe APRÈS le dépôt de l'avis — donc inconnus au moment de la génération. Ils sont
     * volontairement rendus <b>vides</b> (et non « VALEUR MANQUANTE ») : l'avis part à la
     * publication avec ces deux emplacements en blanc, complétés à la main après dépôt.
     */
    private static void putDepotLegal(Map<String, Object> v, Map<String, Object> safe) {
        Map<String, Object> depot = asMap(safe.get("depotLegal"));
        // Grammaire d'assemblage (2026-08-17) — le greffe n'attribue ces deux valeurs
        // qu'APRÈS le dépôt : au moment de rédiger l'avis, elles sont normalement
        // inconnues. On les rendait vides, d'où « … le  sous le numéro  RC N° 123456 »
        // — une phrase trouée que rien ne signalait. On reprend le marqueur déjà
        // employé par la CRÉATION (CreationDirecteurVarsBuilder.POST_IMMAT) : l'avis
        // dit explicitement ce qui reste à compléter au lieu de laisser un blanc.
        String dateDepotLegal = dateStr(first0(depot.get("date"), safe.get("dateDepotLegal")));
        String numeroDepotLegal = str(first0(depot.get("numero"), safe.get("depotLegalNumero")));
        put(v, "DATE_DEPOT_LEGAL",
                dateDepotLegal == null || dateDepotLegal.isBlank() ? POST_IMMAT : dateDepotLegal);
        put(v, "DEPOT_LEGAL_NUMERO",
                numeroDepotLegal == null || numeroDepotLegal.isBlank() ? POST_IMMAT : numeroDepotLegal);
    }

    private static void putSuccursaleCommun(Map<String, Object> v, Map<String, Object> succ) {
        put(v, "SUCCURSALE_ENSEIGNE", str(succ.get("enseigne")));
        put(v, "SUCCURSALE_ADRESSE", str(succ.get("adresse")));
        put(v, "SUCCURSALE_VILLE", str(succ.get("ville")));
        put(v, "SUCCURSALE_VILLE_GREFFE", first(str(succ.get("villeGreffe")), str(succ.get("ville"))));
        put(v, "SUCCURSALE_ACTIVITE", str(succ.get("activite")));
        put(v, "SUCCURSALE_DATE_OUVERTURE", dateStr(succ.get("dateOuverture")));
    }

    private static void putDotation(Map<String, Object> v, Map<String, Object> succ) {
        Long montant = toLong(first0(succ.get("dotationMontant"), succ.get("dotationChiffres")));
        String present = normOuiNon(first0(succ.get("dotationPresente"),
                montant != null && montant > 0), "non");
        put(v, "SUCCURSALE_DOTATION_PRESENTE", present);
        put(v, "SUCCURSALE_DOTATION_CHIFFRES", montant == null ? "" : formatAmount(montant));
        put(v, "SUCCURSALE_DOTATION_LETTRES",
                montant == null ? "" : FrenchNumberToLetters.numberToLetters(montant));
    }

    /**
     * Responsable de la succursale. {@code reduced} = true pour la fermeture (identité réduite :
     * civilité / prénom / nom uniquement, référencée par le modèle de fermeture).
     */
    private static String putResponsable(Map<String, Object> v, Map<String, Object> succ,
                                         boolean reduced) {
        Map<String, Object> r = asMap(succ.get("responsable"));
        boolean hasName = !str(r.get("nom")).isBlank();
        String present = normOuiNon(first0(succ.get("responsablePresent"), hasName), "non");
        put(v, "SUCCURSALE_RESPONSABLE_PRESENT", present);
        put(v, "SUCCURSALE_RESPONSABLE_CIVILITE", normCivilite(r.get("civilite")));
        put(v, "SUCCURSALE_RESPONSABLE_PRENOM", str(r.get("prenom")));
        put(v, "SUCCURSALE_RESPONSABLE_NOM", str(r.get("nom")));
        if (!reduced) {
            put(v, "SUCCURSALE_RESPONSABLE_NATIONALITE", first(str(r.get("nationalite")), "marocaine"));
            put(v, "SUCCURSALE_RESPONSABLE_ADRESSE", str(r.get("adresse")));
            put(v, "SUCCURSALE_RESPONSABLE_PIECE_TYPE", first(str(r.get("pieceType")), "CIN"));
            put(v, "SUCCURSALE_RESPONSABLE_PIECE_NUMERO", first(str(r.get("pieceNumero")),
                    str(r.get("cin"))));
            put(v, "SUCCURSALE_RESPONSABLE_POUVOIRS", first(str(r.get("pouvoirs")),
                    "la gestion courante de la succursale et la représentation de la société auprès"
                            + " des tiers et des administrations"));
        }
        return present;
    }

    private static String responsableLabel(Map<String, Object> v) {
        return join(" ", str(v.get("SUCCURSALE_RESPONSABLE_CIVILITE")),
                str(v.get("SUCCURSALE_RESPONSABLE_PRENOM")), str(v.get("SUCCURSALE_RESPONSABLE_NOM")));
    }

    private static String representantLabel(Map<String, Object> v) {
        return join(" ", str(v.get("REPRESENTANT_SUCCURSALE_CIVILITE")),
                str(v.get("REPRESENTANT_SUCCURSALE_PRENOM")), str(v.get("REPRESENTANT_SUCCURSALE_NOM")));
    }

    private static String mandataireNom(Map<String, Object> safe) {
        return first(str(safe.get("formalitesMandataireNom")),
                str(asMap(safe.get("formalites")).get("mandataireNom")),
                str(asMap(safe.get("seance")).get("presidentNom")), "le président de séance");
    }

    // ------------------------------------------------------------------
    // Résolutions (rédaction directeur reproduite ; boucle RESOLUTIONS)
    // ------------------------------------------------------------------

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

    private static void finalizeResolutions(Map<String, Object> v, List<Map<String, Object>> res,
                                            boolean isAu) {
        String suffix = isAu ? " DÉCISION" : " RÉSOLUTION";
        for (int i = 0; i < res.size(); i++) {
            res.get(i).put("RESOLUTION_NUMERO", ordinal(i + 1) + suffix);
        }
        v.put("RESOLUTIONS", res);
    }

    private static String ordinal(int n) {
        return (n >= 1 && n < ORDINALES.length) ? ORDINALES[n] : (n + "E");
    }

    /** Voix « pour » par défaut : parts présentes calculées par le noyau séance, sinon « l'unanimité ». */
    private static String defaultVoixPour(Map<String, Object> v) {
        String pp = str(v.get("PARTS_PRESENTES_CHIFFRES"));
        return pp.isBlank() ? "l'unanimité" : pp;
    }

    // ------------------------------------------------------------------
    // Normalisations / helpers (alignés sur SeancePvVarsBuilder)
    // ------------------------------------------------------------------

    private static boolean isAu(String templateCode) {
        return templateCode != null && templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU");
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

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
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
}
