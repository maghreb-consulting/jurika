package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Workflow IMPORT — Reprise d'un dossier existant (11 etapes, refonte 2026-06-25).
 *
 * <p>Objectif : produire une <b>fiche structuree COMPLETE</b> d'une societe deja
 * existante en reutilisant le MEME tronc de SAISIE que la {@link CreationSarlWorkflow}
 * (saisie FORCEE, meme les champs « inutiles » comme la denomination), MAIS :
 * <ul>
 *   <li>pas d'etape « Generation IA » (Creation step 7) ;</li>
 *   <li>l'etape « Pieces jointes » (Creation step 8) est remplacee par TROIS
 *       etapes d'UPLOAD typees deposees directement en Data Room
 *       (juridique / comptable / fiscal) — c'est l'EMPLOYE qui uploade ;</li>
 *   <li>les champs purement CREATION (certificat negatif, depot bancaire +
 *       liberation 25 %, dates de bail a la constitution) sont
 *       OPTIONNELS/masques cote front car non pertinents pour une societe
 *       existante. Ils ne sont donc pas exiges ici. En revanche RC + IF
 *       deviennent obligatoires (societe immatriculee).</li>
 * </ul>
 *
 * <p>Les etapes de saisie persistent leurs donnees sous les MEMES cles que la
 * Creation ({@code denomination}, {@code siege}, {@code capital}, {@code activite},
 * {@code dirigeants}/{@code gerance}, {@code associes}) afin que la consolidation
 * {@code fiche_structuree} cote {@code WorkflowUseCases} puisse reutiliser le meme
 * assembleur (objet/duree/gerance/associes/parts/capital/siege inclus) et rendre la
 * MODIFICATION robuste SANS preflight sur les societes importees.
 *
 * <p>Liste des etapes :
 * <ol>
 *   <li>Denomination (denomination, ICE 15 ch., RC, IF, forme)</li>
 *   <li>Siege</li>
 *   <li>Capital (capital total + parts ; pas de depot/liberation)</li>
 *   <li>Activite (objet + date debut exercice)</li>
 *   <li>Dirigeants (+ gerance)</li>
 *   <li>Associes (repartition des parts)</li>
 *   <li>Upload dossier JURIDIQUE (typed, Data Room)</li>
 *   <li>Depot libre des pieces comptables et fiscales (aucun traitement)</li>
 *   <li>Synthese (consolide la fiche juridique + marque l'import complete)</li>
 * </ol>
 */
@Component
public class ImportWorkflow extends AbstractWorkflow {

    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            "STATUTS", "BAIL", "DOMICILIATION", "CNIE", "CN", "RC", "IF", "AUTRE");

    @Override
    public WorkflowType type() {
        return WorkflowType.IMPORT;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> handleDenomination(ctx);
            case 2 -> handleSiege(ctx);
            case 3 -> handleCapital(ctx);
            case 4 -> handleActivite(ctx);
            case 5 -> handleDirigeants(ctx);
            case 6 -> handleAssocies(ctx);
            case 7 -> handleUploadJuridique(ctx);
            case 8 -> handleDepot(ctx);
            case 9 -> handleSynthese(ctx);
            default -> StepResult.blocked("Etape inconnue : " + ctx.step());
        };
    }

    // =========================================================================
    //  ETAPES DE SAISIE (1-6) — meme tronc que la Creation, adapte a l'IMPORT
    // =========================================================================

    /**
     * Etape 1 — Denomination + immatriculation. Saisie forcee : denomination,
     * ICE (15 chiffres), RC, IF, forme juridique. PAS de certificat negatif
     * (purement CREATION). On persiste sous la cle {@code denomination} (comme
     * la Creation) pour reutiliser l'assembleur de fiche structuree.
     */
    private StepResult handleDenomination(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        requireWithLabel(p, "denomination", "Denomination / Raison sociale");
        requireWithLabel(p, "rcNumero", "Numero de Registre de Commerce (RC)");
        requireWithLabel(p, "ifNumero", "Identifiant Fiscal (IF)");

        Object iceVal = p.get("ice");
        if (iceVal == null) iceVal = p.get("icenumero");
        if (iceVal == null || (iceVal instanceof String s && s.isBlank())) {
            return StepResult.blocked("Numero ICE requis (15 chiffres)");
        }
        // RG-C13 : ICE 15 chiffres exactement (espaces / tirets toleres).
        String ice = String.valueOf(iceVal).replaceAll("[\\s-]", "");
        if (!ice.matches("^\\d{15}$")) {
            return StepResult.blocked(
                    "ICE invalide : doit comporter exactement 15 chiffres (recu : "
                            + ice.length() + " caracteres). Reference RG-C13.");
        }

        String forme = p.get("formeJuridique") == null ? "SARL" : String.valueOf(p.get("formeJuridique"));
        if (!forme.equals("SARL") && !forme.equals("SARL_AU")) {
            return StepResult.blocked("Forme juridique invalide : utiliser SARL ou SARL_AU");
        }

        Map<String, Object> out = new HashMap<>(p);
        out.put("formeJuridique", forme);
        out.put("ice", ice);
        out.put("icenumero", ice);
        Map<String, Object> outAll = new HashMap<>(p);
        outAll.put("denomination", out);
        outAll.put("formeJuridique", forme);
        return StepResult.ok(outAll);
    }

    /**
     * Etape 2 — Siege social. Saisie forcee de la localisation. Les pieces
     * justificatives + dates de bail « a la constitution » ne sont PAS exigees
     * (la societe existe ; ses justificatifs seront deposes a l'etape d'upload
     * juridique). {@code justificatifType} reste optionnel mais valide si fourni.
     */
    private StepResult handleSiege(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        require(p, "adresse", "province", "commune", "codePostal");
        Object jt = p.get("justificatifType");
        if (jt != null && !String.valueOf(jt).isBlank()) {
            String type = String.valueOf(jt);
            if (!type.equals("BAIL") && !type.equals("DOMICILIATION")) {
                return StepResult.blocked("justificatifType doit etre BAIL ou DOMICILIATION");
            }
        }
        Map<String, Object> outSiege = new HashMap<>(p);
        outSiege.put("siege", p);
        return StepResult.ok(outSiege);
    }

    /**
     * Etape 3 — Capital social + repartition en parts. Pour une societe
     * existante on ne controle NI la liberation 25 % NI le depot bancaire
     * (purement CREATION). On exige un capital > 0 et un nombre de parts > 0,
     * et on calcule la valeur nominale pour la fiche structuree.
     */
    private StepResult handleCapital(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        BigDecimal numeraire = num(p, "apportNumeraire");
        BigDecimal nature = num(p, "apportNature");
        BigDecimal industrie = num(p, "apportIndustrie");
        BigDecimal capital = numeraire.add(nature).add(industrie);
        if (capital.compareTo(BigDecimal.ZERO) <= 0) {
            // Tolerance : capital saisi directement (societe existante sans detail
            // par type d'apport).
            capital = num(p, "capitalSocialMad");
            if (capital.signum() <= 0) capital = num(p, "capitalSocial");
        }
        if (capital.compareTo(BigDecimal.ZERO) <= 0) {
            return StepResult.blocked("Capital social requis et > 0");
        }
        Integer parts = asInt(p.get("nombreParts"));
        if (parts == null || parts <= 0) {
            return StepResult.blocked("Nombre de parts requis");
        }
        BigDecimal valeurNominale = capital.divide(BigDecimal.valueOf(parts), 4, BigDecimal.ROUND_HALF_UP);
        Map<String, Object> out = new HashMap<>(p);
        out.put("capitalSocialMad", capital);
        out.put("nombreParts", parts);
        out.put("valeurNominaleMad", valeurNominale);
        Map<String, Object> outCap = new HashMap<>(p);
        outCap.put("capital", out);
        return StepResult.ok(outCap);
    }

    /**
     * Etape 4 — Activite / objet social + date de debut d'exercice (alimente le
     * Suivi des exercices a l'etape 10).
     */
    private StepResult handleActivite(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        require(p, "description", "dateDebutExercice");
        Map<String, Object> outAct = new HashMap<>(p);
        outAct.put("activite", p);
        return StepResult.ok(outAct);
    }

    /**
     * Etape 5 — Dirigeants (PHYSIQUE / MORALE) + gouvernance de la gerance.
     * Validation identique a la Creation : la fiche doit etre complete pour
     * permettre une future Modification sans preflight. Les uploads de pieces
     * (CIN, etc.) ne sont PAS exiges ici — ils passent par l'etape d'upload
     * juridique dediee.
     */
    private StepResult handleDirigeants(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Object raw = p.get("dirigeants");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return StepResult.blocked("Au moins un dirigeant requis");
        }
        int idx = 0;
        for (Object o : list) {
            idx++;
            if (!(o instanceof Map<?, ?> m)) {
                return StepResult.blocked("Dirigeant " + idx + " : format invalide");
            }
            String type = m.get("typePersonne") == null ? "PHYSIQUE" : String.valueOf(m.get("typePersonne"));
            if ("MORALE".equals(type)) {
                requireStr(m, "denomination", "Dirigeant " + idx + " (MORALE) : denomination");
                requireStr(m, "rc", "Dirigeant " + idx + " (MORALE) : RC");
                requireStr(m, "ice", "Dirigeant " + idx + " (MORALE) : ICE");
                requireStr(m, "ifFiscal", "Dirigeant " + idx + " (MORALE) : IF");
                requireStr(m, "siege", "Dirigeant " + idx + " (MORALE) : siege");
                requireStr(m, "repNom", "Dirigeant " + idx + " (MORALE) : nom du representant legal");
                requireStr(m, "repPrenom", "Dirigeant " + idx + " (MORALE) : prenom du representant legal");
                requireStr(m, "repCin", "Dirigeant " + idx + " (MORALE) : CIN du representant legal");
                requireStr(m, "repQualite", "Dirigeant " + idx + " (MORALE) : qualite du representant legal");
            } else {
                requireStr(m, "nom", "Dirigeant " + idx + " : nom");
                requireStr(m, "prenom", "Dirigeant " + idx + " : prenom");
                requireStr(m, "cinNumero", "Dirigeant " + idx + " : CIN");
            }
        }
        Object geranceRaw = p.get("gerance");
        if (!(geranceRaw instanceof Map<?, ?> ger)) {
            return StepResult.blocked(
                    "Gouvernance de la gerance requise (duree du mandat + remuneration).");
        }
        Object dureeMandatVal = ger.get("dureeMandat");
        if (dureeMandatVal == null || String.valueOf(dureeMandatVal).isBlank()) {
            return StepResult.blocked("Duree du mandat des gerants requise.");
        }
        Object remuModeVal = ger.get("remunerationMode");
        if (remuModeVal == null || String.valueOf(remuModeVal).isBlank()) {
            return StepResult.blocked("Mode de remuneration de la gerance requis.");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("dirigeants", list);
        out.put("gerance", geranceRaw);
        if (p.containsKey("cacNomme")) out.put("cacNomme", p.get("cacNomme"));
        if (p.containsKey("cacNom"))   out.put("cacNom",   p.get("cacNom"));
        return StepResult.ok(out);
    }

    /**
     * Etape 6 — Associes (repartition des parts). Validation identique a la
     * Creation : SARL_AU = 1 associe a 100 %, SARL = somme des parts == total.
     */
    @SuppressWarnings("unchecked")
    private StepResult handleAssocies(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Object associes = p.get("associes");
        if (!(associes instanceof List<?> list) || list.isEmpty()) {
            return StepResult.blocked("Au moins un associe requis");
        }
        int idx = 0;
        for (Object o : list) {
            idx++;
            if (!(o instanceof Map<?, ?> m)) {
                return StepResult.blocked("Associe " + idx + " : format invalide");
            }
            String type = m.get("typePersonne") == null ? "PHYSIQUE" : String.valueOf(m.get("typePersonne"));
            if ("MORALE".equals(type)) {
                requireStr(m, "denomination", "Associe " + idx + " (MORALE) : denomination");
                requireStr(m, "rc", "Associe " + idx + " (MORALE) : RC");
                requireStr(m, "ice", "Associe " + idx + " (MORALE) : ICE");
                requireStr(m, "ifFiscal", "Associe " + idx + " (MORALE) : IF");
                requireStr(m, "siege", "Associe " + idx + " (MORALE) : siege");
                requireStr(m, "repNom", "Associe " + idx + " (MORALE) : nom du representant legal");
                requireStr(m, "repPrenom", "Associe " + idx + " (MORALE) : prenom du representant legal");
                requireStr(m, "repCin", "Associe " + idx + " (MORALE) : CIN du representant legal");
                requireStr(m, "repQualite", "Associe " + idx + " (MORALE) : qualite du representant legal");
            } else {
                requireStr(m, "nom", "Associe " + idx + " : nom");
                requireStr(m, "prenom", "Associe " + idx + " : prenom");
                requireStr(m, "cin", "Associe " + idx + " : CIN");
            }
        }
        String forme = p.get("formeJuridique") == null
                ? String.valueOf(ctx.existingData().getOrDefault("formeJuridique", "SARL"))
                : String.valueOf(p.get("formeJuridique"));
        if ("SARL_AU".equals(forme) && list.size() != 1) {
            return StepResult.blocked(
                    "SARL_AU = un seul associe (100% des parts). Trouve : " + list.size());
        }
        if ("SARL_AU".equals(forme)) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                Map<String, Object> single = new HashMap<>((Map<String, Object>) m);
                single.put("pourcentageDetention", 100);
                Map<String, Object> outAu = new HashMap<>(p);
                outAu.put("associes", List.of(single));
                outAu.put("formeJuridique", forme);
                return StepResult.ok(outAu);
            }
        }
        // SARL : somme des parts == nombreParts total (lu dans step3.capital).
        Object totalPartsObj = null;
        Object step3Bag = ctx.existingData().get("step3");
        if (step3Bag instanceof Map<?, ?> step3Map) {
            Object capitalBag = step3Map.get("capital");
            if (capitalBag instanceof Map<?, ?> capMap) {
                totalPartsObj = capMap.get("nombreParts");
            }
            if (totalPartsObj == null) {
                totalPartsObj = step3Map.get("nombreParts");
            }
        }
        if (totalPartsObj instanceof Number nb && nb.intValue() > 0) {
            int sum = 0;
            for (Object o : list) {
                if (o instanceof Map<?, ?> mp) {
                    Object np = mp.get("nombreParts");
                    if (np instanceof Number n) sum += n.intValue();
                }
            }
            if (sum != nb.intValue()) {
                return StepResult.blocked("Somme parts distribuees (" + sum
                        + ") differe du total (" + nb.intValue() + ")");
            }
        }
        Map<String, Object> outSarl = new HashMap<>(p);
        outSarl.put("associes", list);
        outSarl.put("formeJuridique", forme);
        return StepResult.ok(outSarl);
    }

    // =========================================================================
    //  ETAPES D'UPLOAD (7-9) — depot direct Data Room (l'employe uploade)
    // =========================================================================

    /**
     * Etape 7 — Upload du dossier JURIDIQUE. Au moins un document typé (statuts,
     * RC, bail, CNIE...). Les blobs sont deposes en Data Room cote frontend ; ici
     * on valide/persiste la liste typee (exploitee par la synthese + tracabilite).
     */
    private StepResult handleUploadJuridique(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Object raw = p.get("documents");
        if (raw == null) raw = p.get("documentsJuridiques");
        List<?> docs = (raw instanceof List<?> l) ? l : List.of();
        if (docs.isEmpty()) {
            return StepResult.blocked(
                    "Au moins un document juridique requis (statuts + bail/domiciliation).");
        }
        for (Object d : docs) {
            if (!(d instanceof Map<?, ?> dm) || dm.get("type") == null || dm.get("filename") == null) {
                return StepResult.blocked("Chaque document doit avoir un type et un filename.");
            }
            String type = String.valueOf(dm.get("type"));
            if (!ALLOWED_DOC_TYPES.contains(type)) {
                return StepResult.blocked(
                        "Type document invalide : " + type
                                + " (attendu : STATUTS, BAIL, DOMICILIATION, CNIE, CN, RC, IF, AUTRE).");
            }
        }
        return StepResult.ok(Map.of("juridique", Map.of(
                "documents", docs,
                "piecesUploaded", docs)));
    }

    /**
     * Etape 8 — Depot des pieces comptables et fiscales. Etape OPTIONNELLE et
     * SANS TRAITEMENT : les fichiers sont deposes tels quels dans l'espace
     * « Depot » de la Data Room (cote front), on ne fait que conserver les
     * metadonnees. Les dossiers comptable et fiscal, avec leur classement par
     * categorie et leurs exercices, sont sortis du perimetre produit.
     */
    private StepResult handleDepot(StepContext ctx) {
        return StepResult.ok(Map.of("depot", ctx.payload()));
    }

    // =========================================================================
    //  ETAPE 9 — Synthese
    // =========================================================================

    /**
     * Etape 9 (Synthese) — Consolide la fiche juridique a partir des etapes de
     * SAISIE (1-6) et marque l'import comme complete. La
     * persistance effective dans {@code entreprise_dossiers.fiche_structuree} est
     * faite par {@code WorkflowUseCases.applyImportConsolidation} a la finalisation.
     */
    private StepResult handleSynthese(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Map<String, Object> existing = ctx.existingData();

        Map<String, Object> denomination = unwrapStep(existing, "step1", "denomination");
        if (denomination == null || denomination.isEmpty()) {
            return StepResult.blocked(
                    "Etape 1 non validee : denomination / immatriculation manquante. Reprendre l'etape 1.");
        }
        Map<String, Object> juridique = unwrapStep(existing, "step7", "juridique");
        if (juridique == null || juridique.isEmpty()) {
            return StepResult.blocked(
                    "Etape 7 non validee : dossier juridique non importe. Reprendre l'etape 7.");
        }
        Map<String, Object> siege = unwrapStep(existing, "step2", "siege");
        Map<String, Object> capital = unwrapStep(existing, "step3", "capital");
        Map<String, Object> activite = unwrapStep(existing, "step4", "activite");
        Map<String, Object> dirigeantsBag = unwrapStep(existing, "step5", "dirigeants");
        Map<String, Object> associesBag = unwrapStep(existing, "step6", "associes");

        Map<String, Object> ficheJuridique = new HashMap<>();
        putIfPresent(ficheJuridique, "raisonSociale", denomination.get("denomination"));
        putIfPresent(ficheJuridique, "denomination", denomination.get("denomination"));
        putIfPresent(ficheJuridique, "ice", denomination.get("ice"));
        putIfPresent(ficheJuridique, "rcNumero", denomination.get("rcNumero"));
        putIfPresent(ficheJuridique, "ifNumero", denomination.get("ifNumero"));
        putIfPresent(ficheJuridique, "formeJuridique", denomination.get("formeJuridique"));
        putIfPresent(ficheJuridique, "capitalSocial", capital.get("capitalSocialMad"));
        putIfPresent(ficheJuridique, "valeurNominale", capital.get("valeurNominaleMad"));
        putIfPresent(ficheJuridique, "nombreParts", capital.get("nombreParts"));
        putIfPresent(ficheJuridique, "siegeAdresse", siege.get("adresse"));
        putIfPresent(ficheJuridique, "siegeVille",
                firstNonNull(siege.get("commune"), siege.get("ville")));
        putIfPresent(ficheJuridique, "objetSocial",
                firstNonNull(activite.get("description"), activite.get("objet")));
        putIfPresent(ficheJuridique, "dateDebutExercice", activite.get("dateDebutExercice"));
        Object dirigeants = dirigeantsBag.get("dirigeants");
        if (dirigeants != null) {
            ficheJuridique.put("dirigeants", dirigeants);
            // alias historique « gerants » pour les consommateurs existants.
            ficheJuridique.put("gerants", dirigeants);
        }
        putIfPresent(ficheJuridique, "gerance", dirigeantsBag.get("gerance"));
        putIfPresent(ficheJuridique, "associes", associesBag.get("associes"));

        Map<String, Object> dataRoom = unwrapStep(existing, "step7", "juridique");
        // dossierId est porte par le ticket (auto-create IMPORT) — recopie s'il a
        // ete propage dans une etape (best-effort, non bloquant).
        Object dossierId = firstNonNull(
                denomination.get("dossierId"), dataRoom.get("dossierId"));
        if (dossierId != null) {
            ficheJuridique.put("dossierId", dossierId);
        }


        if (p.containsKey("validated") && !Boolean.TRUE.equals(p.get("validated"))) {
            return StepResult.blocked("Validation finale requise (validated=true).");
        }

        Map<String, Object> synthese = new HashMap<>();
        synthese.put("ficheJuridique", ficheJuridique);
        synthese.put("importComplete", true);
        synthese.put("finalisedAt", java.time.Instant.now().toString());
        return StepResult.ok(Map.of("synthese", synthese));
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    /**
     * Dezippe le contenu metier d'une step : {@code data.stepN.<inner>}, en
     * tolerant un acces direct (legacy / pas de wrapping).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapStep(Map<String, Object> data, String stepKey, String innerKey) {
        if (data == null) return Map.of();
        Object stepRaw = data.get(stepKey);
        if (stepRaw instanceof Map<?, ?> stepMap) {
            Object innerRaw = ((Map<String, Object>) stepMap).get(innerKey);
            if (innerRaw instanceof Map<?, ?> innerMap) return (Map<String, Object>) innerMap;
            return (Map<String, Object>) stepMap;
        }
        Object direct = data.get(innerKey);
        if (direct instanceof Map<?, ?> dm) return (Map<String, Object>) dm;
        return Map.of();
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            m.put(key, value);
        }
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void require(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new ValidationException("Champ requis : " + k);
            }
        }
    }

    private static void requireWithLabel(Map<String, Object> m, String key, String label) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new ValidationException("Champ requis manquant : " + label);
        }
    }

    private static void requireStr(Map<?, ?> m, String key, String label) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new ValidationException("Champ requis manquant : " + label);
        }
    }

    private static BigDecimal num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
