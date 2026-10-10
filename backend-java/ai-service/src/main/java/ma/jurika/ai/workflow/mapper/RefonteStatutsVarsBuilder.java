package ma.jurika.ai.workflow.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase E2 (2026-08-09) — Producteur des variables du <b>statut COMPLET refondu</b>
 * après une modification, par la voie <b>DIRECTEUR</b>
 * ({@code STATUTS_SARL_DIRECTEUR} / {@code STATUTS_SARL_AU_DIRECTEUR}).
 *
 * <p>Lot A (2026-09-10) : ces deux CODES ont ete retires du manifeste avec le
 * corpus de creation d'aout, mais le GABARIT, lui, est reste sur le disque —
 * c'est celui que {@code STATUTS_REFONDUS_SARL} / {@code _AU} rendent. Les codes
 * passes plus bas a {@link CreationDirecteurVarsBuilder} ne sont donc plus des
 * codes de manifeste : ce sont des arguments que le builder lit pour reconnaitre
 * la forme (« SARL_AU »). La refonte des statuts est le SEUL appelant restant.
 *
 * <p>Remplace l'ancienne voie LEGACY (refonte via {@code CreationSarlMapper} +
 * {@code STATUTS_CONSTITUTIFS_*}). L'assemblage est identique en esprit
 * (état structuré courant ⊕ nouvelles valeurs des modifications = « overlay ») mais
 * les variables produites sont celles du dictionnaire directeur : on délègue le
 * rendu final à {@link CreationDirecteurVarsBuilder} (source unique de vérité des
 * variables statuts directeur — <b>contenu directeur intouchable</b>).
 *
 * <p>Utilitaire statique, sans état (même style que {@link SeancePvVarsBuilder} /
 * {@code SuccursaleVarsBuilder} / {@code ModificationDirecteurMapper}).
 *
 * <p><b>Priorité « overlay gagne »</b> : les nouvelles valeurs de modification
 * écrasent l'état courant lu de la fiche, si bien que l'enrichissement identité BD
 * (BD-first, {@code SocieteIdentityEnricher}) ne peut pas ré-écraser la valeur
 * modifiée (ex. nouvelle dénomination).
 *
 * <p><b>Limite connue (répartition des parts sur cession)</b> : le formulaire de
 * cession ne capture pas l'identité du <i>cédant</i> (seulement le cessionnaire +
 * le nombre de parts). On applique donc en best-effort : cessionnaire {@code +N}
 * (créé ou mis à jour) et, en augmentation de capital, les parts nouvelles au
 * souscripteur. La soustraction {@code cédant −N} n'est pas dérivable sans évolution
 * du modèle de données.
 */
public final class RefonteStatutsVarsBuilder {

    static final String TPL_SARL = "STATUTS_REFONDUS_SARL";
    static final String TPL_SARL_AU = "STATUTS_REFONDUS_SARL_AU";

    private RefonteStatutsVarsBuilder() {}

    /**
     * Construit les variables du statut refondu directeur pour {@code templateCode}
     * ({@link #TPL_SARL} ou {@link #TPL_SARL_AU}) à partir du payload MODIFICATION.
     */
    public static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> creationPayload = assembleRefontePayload(safe);
        // CreationDirecteurVarsBuilder.resolveIsAu détecte la forme via le code
        // (« SARL_AU ») : STATUTS_REFONDUS_SARL_AU -> AU, STATUTS_REFONDUS_SARL -> SARL.
        return CreationDirecteurVarsBuilder.build(templateCode, creationPayload);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Assemblage : fiche structurée courante -> payload directeur, superposé aux
    // NOUVELLES valeurs des modifications cochées. JAMAIS depuis le scan.
    // ──────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assembleRefontePayload(Map<String, Object> payload) {
        Map<String, Object> fiche = asMap(payload.get("ficheStructuree"));
        if (fiche == null) fiche = Map.of();
        Map<String, Object> societeIn = asMap(payload.get("societe"));
        if (societeIn == null) societeIn = Map.of();

        // 1) Etat structuré courant -> clés attendues par CreationDirecteurVarsBuilder.
        Map<String, Object> societe = new HashMap<>();
        putFirst(societe, "denomination", fiche.get("denomination"), societeIn.get("denomination"));
        putFirst(societe, "formeJuridique", fiche.get("formeJuridique"), societeIn.get("formeJuridique"));
        putFirst(societe, "adresseSiege", fiche.get("adresseSiege"), societeIn.get("adresseSiege"));
        putFirst(societe, "siegeSocial", fiche.get("adresseSiege"), societeIn.get("adresseSiege"));
        putFirst(societe, "iceNumero", fiche.get("ice"), societeIn.get("iceNumero"));
        putFirst(societe, "ifNumero", fiche.get("identifiantFiscal"), societeIn.get("ifNumero"));
        putFirst(societe, "rcNumero", fiche.get("rcNumero"), societeIn.get("rcNumero"));
        putFirst(societe, "rcVille", fiche.get("ville"), societeIn.get("rcVille"));
        putFirst(societe, "villeGreffe", fiche.get("ville"), societeIn.get("rcVille"));
        putFirst(societe, "objetSocial", fiche.get("objetSocial"), societeIn.get("objetSocial"));
        putFirst(societe, "capitalChiffres", fiche.get("capitalSocial"), societeIn.get("capitalChiffres"));
        putFirst(societe, "valeurPart", fiche.get("valeurNominale"), societeIn.get("valeurPart"));
        putFirst(societe, "nombreParts", fiche.get("nombreParts"), societeIn.get("nombreParts"));
        putFirst(societe, "dureeAnnees", fiche.get("dureeAnnees"), societeIn.get("dureeAnnees"));
        // Lot L3 : « Fait a ..., le ... » des statuts refondus. Ces deux variables n'etaient
        // fournies par personne : le blanc s'imprimait en silence. Les statuts mis a jour
        // portent la date de l'assemblee qui les adopte et le lieu de signature du PV ;
        // absents, ils sont nommes (regle des variables), jamais devines.
        Map<String, Object> seance = asMap(payload.get("seance"));
        Map<String, Object> assemblee = asMap(payload.get("assemblee"));
        Map<String, Object> convocation = asMap(payload.get("convocation"));
        putFirst(societe, "dateSignature", seance == null ? null : seance.get("date"),
                assemblee == null ? null : assemblee.get("dateAge"));
        putFirst(societe, "lieuSignature", convocation == null ? null : convocation.get("lieuSignature"),
                payload.get("lieuSignature"));

        List<Map<String, Object>> gerants =
                new ArrayList<>(asListOfMaps(firstNonNull(fiche.get("gerants"), fiche.get("dirigeants"))));
        List<Map<String, Object>> associes = new ArrayList<>(deepCopy(asListOfMaps(fiche.get("associes"))));

        // 2) Superpose les NOUVELLES valeurs (overlay).
        //    Voie directeur (Phase 3) : la source privilégiée est `resolutions` (résolutions
        //    typées aplaties — snake_case `type` + champs directement au niveau, sous-listes,
        //    `nouvelAssocie`). À défaut, on retombe sur `modifications` (voie LEGACY, typeId
        //    UPPERCASE + `details`). Les deux vocabulaires sont gérés par overlayModification.
        List<Map<String, Object>> resolutions = asListOfMaps(payload.get("resolutions"));
        if (!resolutions.isEmpty()) {
            for (Map<String, Object> res : resolutions) {
                if (res == null) continue;
                String typeId = asString(res.get("type"));
                Map<String, Object> na = asMap(res.get("nouvelAssocie"));
                overlayModification(typeId, res, na, societe, gerants, associes);
            }
        } else {
            for (Map<String, Object> mod : asListOfMaps(payload.get("modifications"))) {
                if (mod == null) continue;
                String typeId = asString(mod.get("typeId"));
                Map<String, Object> d = asMap(mod.get("details"));
                Map<String, Object> na = asMap(mod.get("nouvelAssocie"));
                overlayModification(typeId, d == null ? Map.of() : d, na, societe, gerants, associes);
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("societe", societe);
        out.put("gerants", gerants);
        out.put("associes", associes);
        return out;
    }

    /**
     * Applique la nouvelle valeur d'UNE modification/résolution sur l'état structuré
     * (clés directeur). Gère les DEUX vocabulaires :
     *  - voie directeur (snake_case `resolutionType` + clés {@code RES_SPECS}) ;
     *  - voie LEGACY (typeId UPPERCASE + clés du wizard historique).
     * {@code nouvelAssocie} (facultatif) porte l'identité OCR d'un associé entrant.
     */
    private static void overlayModification(String typeId, Map<String, Object> d,
                                            Map<String, Object> nouvelAssocie,
                                            Map<String, Object> societe,
                                            List<Map<String, Object>> gerants,
                                            List<Map<String, Object>> associes) {
        if (typeId == null) return;
        switch (typeId) {
            // ── Voie directeur (snake_case) ───────────────────────────────────
            case "modification_denomination" -> putStr(societe, "denomination", d.get("nouvelleDenomination"));
            case "modification_objet" -> {
                Object txt = d.get("objetModification");
                if (txt != null && !txt.toString().isBlank()) {
                    if ("extension".equalsIgnoreCase(asString(d.get("objetAction")))) {
                        Object cur = societe.get("objetSocial");
                        societe.put("objetSocial",
                                (cur == null || cur.toString().isBlank() ? "" : cur + " ; ") + txt);
                    } else {
                        societe.put("objetSocial", txt.toString());
                    }
                }
            }
            case "transfert_siege" -> {
                Object s = d.get("nouveauSiege");
                if (s != null && !s.toString().isBlank()) {
                    societe.put("adresseSiege", s.toString());
                    societe.put("siegeSocial", s.toString());
                }
            }
            case "prorogation_duree" -> {
                Long add = asLong(d.get("prorogationDuree"));
                if (add != null) {
                    Long cur = asLong(societe.get("dureeAnnees"));
                    societe.put("dureeAnnees", (cur == null ? 0 : cur) + add);
                }
            }
            case "augmentation_capital_numeraire", "augmentation_capital_incorporation",
                 "augmentation_capital_nature" -> {
                Long nc = asLong(d.get("augcapNouveauCapital"));
                if (nc != null) societe.put("capitalChiffres", nc);
                Long vn = asLong(d.get("valeurNominalePart"));
                if (vn != null) societe.put("valeurPart", vn);
                Long np = asLong(d.get("augcapNbPartsNouvelles"));
                if (np != null) {
                    Long cur = asLong(societe.get("nombreParts"));
                    societe.put("nombreParts", (cur == null ? 0 : cur) + np);
                }
                if (hasIdentity(nouvelAssocie)) {
                    appendNouvelAssocie(associes, nouvelAssocie, np, societe);
                } else {
                    attribuerPartsNouvelles(associes, asString(d.get("apporteurNom")), np);
                }
            }
            case "reduction_capital" -> {
                Long nc = asLong(d.get("redcapNouveauCapital"));
                if (nc != null) societe.put("capitalChiffres", nc);
                Long vn = asLong(d.get("nouvelleValeurNominale"));
                if (vn != null) societe.put("valeurPart", vn);
                Long np = asLong(d.get("redcapNbPartsNouvelles"));
                if (np != null) societe.put("nombreParts", np);
            }
            case "agrement_cession", "agrement_transmission", "cession_parts_pluripersonnelle" -> {
                Long parts = asLong(d.get("cessionNbParts"));
                // Débit du cédant si identifiable : `cedantNom` (agrément) ou, pour un
                // passage pluripersonnel, l'unique associé courant. Préserve la cohérence
                // du total des parts avec le capital (aucune part créée sur une cession).
                String cedant = asString(d.get("cedantNom"));
                if ((cedant == null || cedant.isBlank())
                        && "cession_parts_pluripersonnelle".equals(typeId) && associes.size() == 1) {
                    cedant = asString(firstNonNull(associes.get(0).get("nom"), associes.get(0).get("denomination")));
                }
                debiterCedant(associes, cedant, parts, societe);
                // Crédit du nouvel entrant : identité OCR complète si dispo, sinon nom seul.
                if (hasIdentity(nouvelAssocie)) {
                    appendNouvelAssocie(associes, nouvelAssocie, parts, societe);
                } else {
                    crediterCessionnaire(associes, asString(d.get("cessionnaireNom")), parts);
                }
            }
            case "nomination_gerant" -> {
                if ("oui".equalsIgnoreCase(asString(d.get("gerantSortant")))) {
                    removeGerantByName(gerants, asString(d.get("gerantSortantNom")));
                }
                for (Map<String, Object> g : asListOfMaps(d.get("gerants"))) {
                    Map<String, Object> ng = new HashMap<>(g);
                    ng.putIfAbsent("isStatutaire", Boolean.FALSE);
                    gerants.add(ng);
                }
            }
            case "revocation_gerant" -> removeGerantByName(gerants, asString(d.get("gerantSortantNom")));

            // ── Voie LEGACY (typeId UPPERCASE) ────────────────────────────────
            case "CHANGEMENT_DENOMINATION" -> {
                putStr(societe, "denomination", d.get("nouvelleDenomination"));
                putStr(societe, "sigle", d.get("sigle"));
            }
            case "CHANGEMENT_OBJET" -> putStr(societe, "objetSocial", d.get("nouvelObjet"));
            case "TRANSFERT_SIEGE" -> {
                Object adr = d.get("nouvelleAdresse");
                Object ville = d.get("nouvelleVille");
                if (adr != null && ville != null) {
                    societe.put("adresseSiege", adr + ", " + ville);
                    societe.put("siegeSocial", adr + ", " + ville);
                } else if (adr != null) {
                    societe.put("adresseSiege", adr.toString());
                    societe.put("siegeSocial", adr.toString());
                }
                if (ville != null) {
                    societe.put("rcVille", ville.toString());
                    societe.put("villeGreffe", ville.toString());
                }
            }
            case "PROROGATION_DUREE" -> {
                Long ans = asLong(d.get("annees"));
                if (ans != null) societe.put("dureeAnnees", ans);
            }
            case "AUGMENTATION_CAPITAL" -> {
                Long nc = asLong(d.get("nouveauCapital"));
                if (nc != null) societe.put("capitalChiffres", nc);
                Long np = asLong(d.get("nouvellesParts"));
                if (np != null) {
                    Long cur = asLong(societe.get("nombreParts"));
                    societe.put("nombreParts", (cur == null ? 0 : cur) + np);
                    // Parts nouvelles attribuées au souscripteur (best-effort) : soit le
                    // souscripteur nommé, soit réparties si un seul associé.
                    attribuerPartsNouvelles(associes, asString(d.get("souscripteur")), np);
                }
            }
            case "AUGMENTATION_CAPITAL_RESERVES" -> {
                Long inc = asLong(d.get("montantIncorporation"));
                Long cur = asLong(societe.get("capitalChiffres"));
                if (inc != null && cur != null) societe.put("capitalChiffres", cur + inc);
            }
            case "REDUCTION_CAPITAL" -> {
                Long red = asLong(d.get("montantReduction"));
                Long cur = asLong(societe.get("capitalChiffres"));
                if (red != null && cur != null) societe.put("capitalChiffres", Math.max(0, cur - red));
            }
            case "MODIF_VALEUR_NOMINALE" -> {
                Long v = asLong(d.get("nouvelleValeurNominale"));
                if (v != null) societe.put("valeurPart", v);
            }
            case "TRANSFORMATION" -> putStr(societe, "formeJuridique", d.get("nouvelleForme"));
            case "DESIGNATION_GERANT" -> {
                Map<String, Object> g = new HashMap<>();
                putStr(g, "nom", d.get("nom"));
                putStr(g, "prenom", d.get("prenom"));
                putStr(g, "cinNumero", d.get("cin"));
                putStr(g, "nationalite", d.get("nationalite"));
                g.put("isStatutaire", Boolean.FALSE);
                if (g.size() > 1) gerants.add(g);
            }
            case "REVOCATION_GERANT" -> {
                String ident = asString(d.get("identite"));
                if (ident != null && !ident.isBlank()) {
                    String low = ident.toLowerCase(Locale.ROOT);
                    gerants.removeIf(gg -> {
                        String nom = asString(gg.get("nom"));
                        return nom != null && !nom.isBlank() && low.contains(nom.toLowerCase(Locale.ROOT));
                    });
                }
            }
            case "CESSION_PARTIELLE", "CESSION_TOTALE", "TRANSMISSION_PARTS" ->
                    // Best-effort : le cessionnaire acquiert N parts. L'identité du cédant
                    // n'étant pas capturée par le formulaire, la soustraction cédant −N
                    // n'est pas dérivable ici (voir javadoc classe).
                    crediterCessionnaire(associes, asString(d.get("cessionnaire")), asLong(d.get("nombreParts")));
            default -> { /* clauses / pacte / formalités : pas d'impact direct sur le statut refondu */ }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Répartition des parts (best-effort)
    // ──────────────────────────────────────────────────────────────────────

    /** Crédite {@code parts} au cessionnaire (associé existant apparié par nom, sinon ajouté). */
    private static void crediterCessionnaire(List<Map<String, Object>> associes, String cessionnaire, Long parts) {
        if (cessionnaire == null || cessionnaire.isBlank() || parts == null || parts <= 0) return;
        Map<String, Object> cible = findByLabel(associes, cessionnaire);
        if (cible == null) {
            cible = new HashMap<>();
            cible.put("nom", cessionnaire);
            associes.add(cible);
        }
        Long cur = asLong(firstNonNull(cible.get("nombreParts"), cible.get("partsChiffres")));
        cible.put("nombreParts", (cur == null ? 0 : cur) + parts);
    }

    /** Attribue les parts nouvelles au souscripteur nommé, ou au seul associé si unique. */
    private static void attribuerPartsNouvelles(List<Map<String, Object>> associes, String souscripteur, Long parts) {
        if (parts == null || parts <= 0) return;
        Map<String, Object> cible = null;
        if (souscripteur != null && !souscripteur.isBlank()) {
            cible = findByLabel(associes, souscripteur);
            if (cible == null) {
                cible = new HashMap<>();
                cible.put("nom", souscripteur);
                associes.add(cible);
            }
        } else if (associes.size() == 1) {
            cible = associes.get(0);
        }
        if (cible == null) return;
        Long cur = asLong(firstNonNull(cible.get("nombreParts"), cible.get("partsChiffres")));
        cible.put("nombreParts", (cur == null ? 0 : cur) + parts);
    }

    /** Vrai si l'identité OCR d'un nouvel associé porte au moins un nom/prénom/CIN. */
    private static boolean hasIdentity(Map<String, Object> na) {
        if (na == null) return false;
        for (String k : new String[]{"nom", "prenom", "cin", "cinNumero"}) {
            String v = asString(na.get(k));
            if (v != null && !v.isBlank()) return true;
        }
        return false;
    }

    /**
     * Ajoute un associé PHYSIQUE complet (identité OCR + apport en numéraire) à la
     * liste, avec {@code parts} parts. Alimente l'article « associés » + « apports »
     * du statut refondu directeur (clés lues par {@code CreationDirecteurVarsBuilder}).
     */
    private static void appendNouvelAssocie(List<Map<String, Object>> associes,
                                            Map<String, Object> na, Long parts,
                                            Map<String, Object> societe) {
        if (!hasIdentity(na)) return;
        Map<String, Object> a = new HashMap<>();
        a.put("typePersonne", "PHYSIQUE");
        // Lot L3 : la civilite de l'associe entrant etait perdue (blanc silencieux).
        putStr(a, "civilite", na.get("civilite"));
        putStr(a, "prenom", na.get("prenom"));
        putStr(a, "nom", na.get("nom"));
        putStr(a, "cinNumero", firstNonNull(na.get("cin"), na.get("cinNumero")));
        putStr(a, "nationalite", na.get("nationalite"));
        putStr(a, "adresse", na.get("adresse"));
        putStr(a, "dateNaissance", na.get("dateNaissance"));
        putStr(a, "lieuNaissance", na.get("lieuNaissance"));
        if (parts != null && parts > 0) {
            a.put("nombreParts", parts);
            a.put("apportType", "numéraire");
            Long vn = asLong(societe.get("valeurPart"));
            if (vn != null) a.put("apportNumeraire", parts * vn);
        }
        associes.add(a);
    }

    /** Débite {@code parts} au cédant (apparié par nom) ; le retire si son solde tombe à 0. */
    private static void debiterCedant(List<Map<String, Object>> associes, String cedant, Long parts,
                                      Map<String, Object> societe) {
        if (cedant == null || cedant.isBlank() || parts == null || parts <= 0) return;
        Map<String, Object> src = findByLabel(associes, cedant);
        if (src == null) return;
        Long cur = asLong(firstNonNull(src.get("nombreParts"), src.get("partsChiffres")));
        if (cur == null) return;
        long remaining = Math.max(0, cur - parts);
        Long vn = asLong(societe.get("valeurPart"));
        if (remaining == 0) {
            associes.remove(src);
        } else {
            src.put("nombreParts", remaining);
            if (vn != null) src.put("apportNumeraire", remaining * vn);
        }
    }

    /** Retire de la liste tout gérant dont le nom apparaît dans {@code label}. */
    private static void removeGerantByName(List<Map<String, Object>> gerants, String label) {
        if (label == null || label.isBlank()) return;
        String low = label.toLowerCase(Locale.ROOT);
        gerants.removeIf(gg -> {
            String nom = asString(gg.get("nom"));
            return nom != null && !nom.isBlank() && low.contains(nom.toLowerCase(Locale.ROOT));
        });
    }

    private static Map<String, Object> findByLabel(List<Map<String, Object>> associes, String label) {
        String low = label.toLowerCase(Locale.ROOT).trim();
        for (Map<String, Object> a : associes) {
            if (a == null) continue;
            String nom = asString(a.get("nom"));
            String denom = asString(a.get("denomination"));
            String prenom = asString(a.get("prenom"));
            String full = ((prenom == null ? "" : prenom + " ") + (nom == null ? "" : nom)).trim();
            for (String cand : new String[]{nom, denom, full}) {
                if (cand != null && !cand.isBlank()) {
                    String c = cand.toLowerCase(Locale.ROOT).trim();
                    if (c.equals(low) || low.contains(c) || c.contains(low)) return a;
                }
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers (self-contained)
    // ──────────────────────────────────────────────────────────────────────

    private static void putFirst(Map<String, Object> m, String key, Object... vals) {
        for (Object v : vals) {
            if (v != null && !(v instanceof String s && s.isBlank())) {
                m.put(key, v);
                return;
            }
        }
    }

    private static void putStr(Map<String, Object> m, String key, Object v) {
        if (v == null) return;
        String s = v.toString();
        if (s.isBlank()) return;
        m.put(key, s);
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static List<Map<String, Object>> deepCopy(List<Map<String, Object>> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : src) {
            out.add(m == null ? new HashMap<>() : new HashMap<>(m));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s.replace(" ", "").replace(" ", "").replace("_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
