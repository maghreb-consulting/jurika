package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CreationSarlWorkflow extends AbstractWorkflow {

    @Override
    public WorkflowType type() {
        return WorkflowType.CREATION;
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
            case 7 -> handleGenerationStatuts(ctx);
            case 8 -> handlePiecesJointes(ctx);
            case 9 -> handleSynthese(ctx);
            default -> StepResult.blocked("Etape inconnue : " + ctx.step());
        };
    }

    private StepResult handleDenomination(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        // Fix 2026-06-04 : front Step1Denomination envoie la cle `ice` (cf
        // frontend-react/.../steps/Step1Denomination.tsx). On accepte aussi
        // `icenumero` (ancien contrat) pour compat ascendante. Cle canonique :
        // `ice` (alignee table entreprise_dossiers.ice et signup wizard Sprint 11).
        // Phase 4 (contrat §2.10) — l'ICE de la société N'EST PLUS demandé à la
        // constitution : il est attribué APRÈS immatriculation (comme RC_NUMERO). Il
        // n'alimente aucun des 4 modèles directeur. Le champ reste TOLÉRÉ (compat /
        // saisie ultérieure) : s'il est fourni, on valide le format 15 chiffres.
        Object iceVal = p.get("ice");
        if (iceVal == null) iceVal = p.get("icenumero");
        // Autres champs obligatoires : message explicite par champ.
        requireWithLabel(p, "denomination", "Denomination / Raison sociale");
        requireWithLabel(p, "cnNumero", "N° Certificat Negatif");
        requireWithLabel(p, "cnDate", "Date de delivrance du CN");
        requireWithLabel(p, "activiteCn", "Activite (telle qu'inscrite au CN)");
        requireWithLabel(p, "beneficiaire", "Beneficiaire du CN");
        // 2026-06-22 — sigle FACULTATIF : si vide, vaut « néant » (rempli côté
        // payload front). Plus de blocage serveur sur ce champ.
        // RG-S01 vs RG-C01 : forme juridique requise pour differencier SARL / SARL_AU
        // Defaut : SARL (multi-associes). SARL_AU = associe unique.
        String forme = p.get("formeJuridique") == null ? "SARL" : String.valueOf(p.get("formeJuridique"));
        if (!forme.equals("SARL") && !forme.equals("SARL_AU")) {
            return StepResult.blocked("Forme juridique invalide : utiliser SARL ou SARL_AU");
        }
        java.util.Map<String, Object> out = new java.util.HashMap<>(p);
        out.put("formeJuridique", forme);
        // RG-C13 : si un ICE est fourni, il doit comporter 15 chiffres exactement
        // (tolérés : espaces / tirets). Absent -> laissé vide (attribué post-immat).
        if (iceVal != null && !(iceVal instanceof String s0 && s0.isBlank())) {
            String ice = String.valueOf(iceVal).replaceAll("[\\s-]", "");
            if (!ice.matches("^\\d{15}$")) {
                return StepResult.blocked(
                        "ICE invalide : doit comporter exactement 15 chiffres (recu : "
                                + ice.length() + " caracteres). Reference RG-C13.");
            }
            // Persisté sous les deux clés pour les consommateurs existants.
            out.put("ice", ice);
            out.put("icenumero", ice);
        }
        // P1 2026-06-21 (Cowork) — preserver tout le payload (sigle, dureeModalite,
        // dureePreavisMois, cnArchivedDocumentId, ...) ; Map.of figeait a 2 cles.
        java.util.Map<String, Object> outAll = new java.util.HashMap<>(p);
        outAll.put("denomination", out);
        outAll.put("formeJuridique", forme);
        return StepResult.ok(outAll);
    }

    private StepResult handleSiege(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        require(p, "adresse", "province", "commune", "codePostal", "justificatifType");
        String type = String.valueOf(p.get("justificatifType"));
        if (!type.equals("BAIL") && !type.equals("DOMICILIATION")) {
            return StepResult.blocked("justificatifType doit etre BAIL ou DOMICILIATION");
        }
        // P1 2026-06-21 (Cowork) — preserve egalement certificatProprieteDocName,
        // attestationDocName etc. presents a plat ; le wrap historique etait deja
        // sous "siege", on enrichit avec une copie a plat des cles utiles.
        java.util.Map<String, Object> outSiege = new java.util.HashMap<>(p);
        outSiege.put("siege", p);
        return StepResult.ok(outSiege);
    }

    private StepResult handleCapital(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        BigDecimal numeraire = num(p, "apportNumeraire");
        BigDecimal nature = num(p, "apportNature");
        BigDecimal industrie = num(p, "apportIndustrie");
        BigDecimal capital = numeraire.add(nature).add(industrie);
        if (capital.compareTo(BigDecimal.ZERO) <= 0) {
            return StepResult.blocked("Au moins un apport doit etre superieur a 0");
        }
        // EX2 2026-06-09 — Refonte regle 25% :
        //   Apport nature + industrie = libere a 100% par definition (decision interne JURIKA,
        //   front verrouille ces deux champs avec badge "100% libere").
        //   Apport numeraire = partiellement liberable (front : champ numeraireLibere).
        //   capitalLibere total = nature + industrie + numeraireLibere
        //   Seuil legal 25% se calcule sur le CAPITAL TOTAL (numeraire + nature + industrie).
        // Compat ascendante : si le front envoie encore `capitalLibere` direct on l'utilise,
        // sinon on calcule depuis numeraireLibere (nouvelle cle).
        BigDecimal numeraireLibere = num(p, "apportNumeraireLibere");
        // Clamp : numeraireLibere ne peut pas depasser numeraire effectif (defense en profondeur).
        if (numeraireLibere.compareTo(numeraire) > 0) {
            numeraireLibere = numeraire;
        }
        BigDecimal libereFromComponents = nature.add(industrie).add(numeraireLibere);
        BigDecimal libereLegacy = num(p, "capitalLibere");
        // Si le front fournit numeraireLibere on prend le calcul componentise (autorite).
        // Sinon (workflows anciens) on retombe sur capitalLibere brut.
        BigDecimal libere = p.containsKey("apportNumeraireLibere")
                ? libereFromComponents
                : (libereLegacy.signum() > 0 ? libereLegacy : libereFromComponents);
        BigDecimal ratio25 = capital.multiply(BigDecimal.valueOf(25))
                .divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP);
        if (libere.compareTo(ratio25) < 0) {
            return StepResult.blocked(
                    "Liberation insuffisante — Le minimum legal de 25% du capital social total doit etre libere ("
                            + ratio25 + " MAD min, actuellement " + libere + " MAD). "
                            + "Nature et industrie sont liberes a 100% par defaut, augmentez la part liberee du numeraire.");
        }
        Integer parts = (Integer) p.get("nombreParts");
        if (parts == null || parts <= 0) {
            return StepResult.blocked("Nombre de parts requis");
        }
        // Phase 4 (contrat §2.2) — le dépôt de fonds bloqué est CONDITIONNEL :
        // banque + numéro d'attestation ne sont requis que si DEPOT_FONDS_BLOQUE = oui
        // (obligatoire si apports numéraire > 100 000 DH). Sinon, champs optionnels.
        Object depotFlag = p.get("depotFondsBloque");
        boolean depotBloque = Boolean.TRUE.equals(depotFlag)
                || "oui".equals(normLabel(depotFlag))
                || "true".equals(normLabel(depotFlag));
        if (depotBloque) {
            Object depotBanque = p.get("depotBanqueNom");
            if (depotBanque == null || String.valueOf(depotBanque).isBlank()) {
                return StepResult.blocked("Banque du depot de fonds requise (depot en compte bloque).");
            }
            Object depotNum = p.get("depotNumero");
            if (depotNum == null || String.valueOf(depotNum).isBlank()) {
                return StepResult.blocked("Numero de l'attestation de depot requise (depot en compte bloque).");
            }
        }
        // 2026-08 (Phase 3) — Options de constitution (voie directeur). Non bloquant
        // si le champ est absent (le mapper applique son defaut legal) ; valide les
        // libelles EXACTS attendus par les conditions des modeles quand ils sont fournis.
        String modeLib = normLabel(p.get("modeLiberation"));
        if (!modeLib.isEmpty() && !java.util.Set.of("integrale", "partielle").contains(modeLib)) {
            return StepResult.blocked("Mode de liberation invalide : « integrale » ou « partielle ».");
        }
        String modeSig = normLabel(p.get("modeSignature"));
        if (!modeSig.isEmpty()
                && !java.util.Set.of("separee", "separee avec plafond", "conjointe").contains(modeSig)) {
            return StepResult.blocked(
                    "Mode de signature invalide : « separee », « separee avec plafond » ou « conjointe ».");
        }
        if (modeSig.equals("separee avec plafond") && num(p, "signaturePlafond").signum() <= 0) {
            return StepResult.blocked("Plafond de signature requis (> 0) pour « separee avec plafond ».");
        }
        // Phase 4 — signature des documents administratifs (art. 15) : « identique » /
        // « signature seule ». Non bloquant si absent (le mapper applique « identique »).
        String modeSigAdmin = normLabel(p.get("modeSignatureAdmin"));
        if (!modeSigAdmin.isEmpty()
                && !java.util.Set.of("identique", "signature seule").contains(modeSigAdmin)) {
            return StepResult.blocked(
                    "Mode de signature administrative invalide : « identique » ou « signature seule ».");
        }
        BigDecimal valeurNominale = capital.divide(BigDecimal.valueOf(parts), 4, BigDecimal.ROUND_HALF_UP);
        Map<String, Object> out = new HashMap<>(p);
        out.put("capitalSocialMad", capital);
        out.put("apportNumeraireLibere", numeraireLibere);
        out.put("capitalLibere", libere);
        out.put("valeurNominaleMad", valeurNominale);
        out.put("pourcentageLibere", libere.divide(capital, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100)));
        // Detail par type d'apport pour les statuts/declaration.
        out.put("apportNumeraireLiberePct", numeraire.signum() > 0
                ? numeraireLibere.multiply(BigDecimal.valueOf(100))
                        .divide(numeraire, 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO);
        // P1 2026-06-21 (Cowork) — preserve toutes les cles du payload (depot,
        // fonds_commerce, commissaire, duree modalite, ...). Map.of("capital", out)
        // historique etait suffisant pour le mapper qui plonge dans .capital,
        // mais on enrichit pour permettre une re-hydratation directe a plat.
        java.util.Map<String, Object> outCap = new java.util.HashMap<>(p);
        outCap.put("capital", out);
        return StepResult.ok(outCap);
    }

    private StepResult handleActivite(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        // Dé-dup 2026-08 — « date de commencement de l'exercice » n'est plus saisie ici
        // (source unique = Step3 capital.dateCommencement). Seule la description est requise.
        require(p, "description");
        // P1 2026-06-21 (Cowork) — preserve aussi les cles a plat (compat).
        java.util.Map<String, Object> outAct = new java.util.HashMap<>(p);
        outAct.put("activite", p);
        return StepResult.ok(outAct);
    }

    private StepResult handleDirigeants(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!p.containsKey("dirigeants")) {
            return StepResult.blocked("Au moins un dirigeant requis");
        }
        // EX5 2026-06-09 — Un dirigeant peut etre PHYSIQUE ou MORALE. Validation
        // par type : PHYSIQUE => CIN + nom + prenom ; MORALE => denomination + RC + ICE + IF
        // + siege + representant legal physique complet. Le front valide deja en
        // detail (regex CIN/ICE/RC/IF) ; ici on enforce le minimum non-vide pour
        // proteger contre un payload manuel/incomplet.
        Object raw = p.get("dirigeants");
        if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
            return StepResult.blocked("Au moins un dirigeant requis");
        }
        // 2026-08 (Phase 3) — mode de designation de la gerance (voie directeur).
        // Non bloquant si absent (defaut « statutaire ») ; libelles exacts sinon.
        String modeDesig = normLabel(p.get("gerantModeDesignation"));
        if (!modeDesig.isEmpty()
                && !java.util.Set.of("statutaire", "non statutaire").contains(modeDesig)) {
            return StepResult.blocked(
                    "Mode de designation du gerant invalide : « statutaire » ou « non statutaire ».");
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
        // C1 2026-06-21 — Gouvernance de la gerance OBLIGATOIRE (politique stricte
        // « valeur reelle ou saisie forcee »). Le front Step5 bloque deja, ceci est
        // la defense en profondeur cote serveur.
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
        // Persistance « bulletproof » 2026-08 — on preserve TOUT le payload Step5
        // (signataires, gerantModeDesignation, dureeGerance, limitationPouvoirs,
        // modeSignature/AdminCAC, mandataire…) au lieu de reconstruire une sortie
        // partielle qui droppait des cles au save+reload. Bug confirme : la sortie
        // historique construite depuis un HashMap VIDE oubliait `signataires` (que
        // Step5Dirigeants envoie dans son getter) -> la liste des signataires
        // disparaissait au retour sur l'etape. On copie donc l'integralite du
        // payload (comme handleSiege/handleCapital/handleActivite) puis on re-pose
        // par-dessus les cles validees/normalisees.
        java.util.Map<String, Object> out = new java.util.HashMap<>(p);
        out.put("dirigeants", list);
        out.put("gerance", geranceRaw);
        return StepResult.ok(out);
    }

    private StepResult handleAssocies(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Object associes = p.get("associes");
        if (!(associes instanceof java.util.List<?> list) || list.isEmpty()) {
            return StepResult.blocked("Au moins un associe requis");
        }
        // EX5 2026-06-09 — Validation par type d'associe (PHYSIQUE / MORALE).
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
        // RG-C16/S04 : SARL_AU = 1 associe a 100% / SARL = somme parts == nombreParts total
        String forme = p.get("formeJuridique") == null
                ? String.valueOf(ctx.existingData().getOrDefault("formeJuridique", "SARL"))
                : String.valueOf(p.get("formeJuridique"));
        if ("SARL_AU".equals(forme) && list.size() != 1) {
            return StepResult.blocked(
                    "SARL_AU = un seul associe (100% des parts). Trouve : " + list.size());
        }
        // SARL (pluralite) = au moins 2 associes. Un seul associe => SARL a associe unique.
        if (!"SARL_AU".equals(forme) && list.size() < 2) {
            return StepResult.blocked(
                    "SARL : au moins 2 associes requis (1 seul associe = SARL a associe unique). Trouve : "
                            + list.size());
        }
        if ("SARL_AU".equals(forme)) {
            // Force 100% au seul associe
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                java.util.Map<String, Object> single = new java.util.HashMap<>((java.util.Map<String, Object>) m);
                single.put("pourcentageDetention", 100);
                // P1 2026-06-21 (Cowork) — preserver les clauses Step6 (Map.of figeait
                // a 2 cles -> clauseAgrementMajorite/delaiConvocationJours/etc.
                // disparaissaient apres save+reload).
                java.util.Map<String, Object> outAu = new java.util.HashMap<>(p);
                outAu.put("associes", java.util.List.of(single));
                outAu.put("formeJuridique", forme);
                return StepResult.ok(outAu);
            }
        }
        // SARL : verifier somme parts == nombreParts total
        // EX3 2026-06-09 — existingData est structure par etape :
        //   existingData = {step3: {capital: {nombreParts: ...}}, step4: {...}, ...}
        // On cherche capital.nombreParts en plongeant dans step3 ; fallback step3.nombreParts
        // direct (au cas ou un autre flow legacy le mette a la racine du step), puis fallback
        // racine pour compat ascendante extreme.
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
        if (totalPartsObj == null) {
            Object capitalBag = ctx.existingData().get("capital");
            if (capitalBag instanceof Map<?, ?> capMap) {
                totalPartsObj = capMap.get("nombreParts");
            }
        }
        if (totalPartsObj == null) {
            totalPartsObj = ctx.existingData().get("nombreParts");
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
        // P1 2026-06-21 (Cowork) — preserver les clauses Step6 (agrement, delai
        // convocation, inalienabilite, preemption) qui sortaient muettes du
        // Map.of("associes", "formeJuridique") historique.
        java.util.Map<String, Object> outSarl = new java.util.HashMap<>(p);
        outSarl.put("associes", list);
        outSarl.put("formeJuridique", forme);
        return StepResult.ok(outSarl);
    }

    private StepResult handleGenerationStatuts(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!Boolean.TRUE.equals(p.get("statutsValides"))) {
            return StepResult.blocked("Les statuts doivent etre valides avant de continuer");
        }
        // Fix 2026-06-11 — Map.of() refuse les valeurs null : si le front
        // envoie statutsDocumentId=null ou actesNominationIds=null (ex: le
        // user valide les statuts sans avoir genere/uploade le document
        // documentId), NPE -> 500. Le frontend Step7Generation passe explicitement
        // null via `statutsState?.documentId ?? null`. On utilise donc un
        // HashMap qui tolere null + une Map racine equivalente.
        Map<String, Object> generation = new HashMap<>();
        generation.put("statutsDocumentId", p.get("statutsDocumentId"));
        generation.put("actesNominationIds", p.get("actesNominationIds"));
        generation.put("statutsValides", Boolean.TRUE);
        // Persistance « bulletproof » 2026-08 — on preserve tout le payload Step7
        // (notamment `documents`, la map par-code d'etat genere/valide que
        // Step7Generation relit via `existing.documents`). Sans cela, l'etat des
        // documents generes/valides disparaissait au retour sur l'etape apres
        // validation. Le wrapper `generation` reste emis pour compat ascendante.
        Map<String, Object> out = new HashMap<>(p);
        out.put("generation", generation);
        return StepResult.ok(out);
    }

    private StepResult handlePiecesJointes(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        @SuppressWarnings("unchecked")
        List<String> pieces = (List<String>) p.getOrDefault("piecesUploaded", List.of());

        // Politique produit 2026-08 : AUCUNE pièce n'est obligatoire à l'étape 8.
        // L'employé dépose les justificatifs quand il les a ; l'étape ne bloque
        // JAMAIS la progression (aligné sur le front Step8PiecesJointes où
        // `requiredOk = true` et toutes les pièces sont marquées optionnelles).
        // Les anciennes validations « pièce de base manquante » + « CIN par
        // personne » sont retirées côté back (elles rejetaient une soumission
        // pourtant valide côté front → executeStep(8) en erreur).
        //
        // Persistance « bulletproof » — on préserve tout le payload Step8
        // (piecesUploaded, cinPersonnes…) en plus du wrap historique `pieces`.
        java.util.Map<String, Object> out = new java.util.HashMap<>(p);
        out.put("pieces", Map.of("uploaded", pieces));
        return StepResult.ok(out);
    }

    private StepResult handleSynthese(StepContext ctx) {
        return StepResult.ok(Map.of("synthese", Map.of(
                "annonceLegalGenere", true,
                "finalise", true)));
    }

    private static void require(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new ValidationException("Champ requis manquant : " + k);
            }
        }
    }

    /**
     * Variante de {@link #require(Map, String...)} avec un libelle lisible pour le user.
     * Permet d'afficher "Champ requis manquant : Denomination / Raison sociale" plutot
     * que la cle technique "denomination".
     */
    private static void requireWithLabel(Map<String, Object> m, String key, String label) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new ValidationException("Champ requis manquant : " + label);
        }
    }

    /** EX5 — Variante pour Map non-typee (sous-objets dirigeant/associe). */
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

    /**
     * 2026-08 (Phase 3) — normalise un libelle pour comparaison aux valeurs
     * attendues par les conditions des modeles directeur : sans accents,
     * minuscules, espaces compactes. Chaine vide si absent.
     */
    private static String normLabel(Object o) {
        if (o == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(o), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
