package ma.jurika.ticket.application.referentiel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L4b (E2, RG-DEM-01) : import du referentiel des demarches du corpus, versionne.
 *
 * <p>Regles :
 * <ul>
 *   <li>une version dont le rapport d'analyse porte une erreur, ou qui ne porte aucune
 *       ligne, est REFUSEE : rien ne change, le refus est journalise avec son motif ;</li>
 *   <li>une version deja appliquee n'est pas reimportee (refus journalise) ;</li>
 *   <li>une version appliquee desactive les lignes, decisions et renvois des versions
 *       precedentes sans les supprimer : les cochages des tickets restent rattaches ;</li>
 *   <li>les 51 lignes de la creation en service (sans version) ne sont jamais touchees.</li>
 * </ul>
 */
@Service
public class ImportReferentielDemarches {

    /** Statuts du ticket et leur phase d'affichage (meme codes que les lignes en service). */
    static final Map<String, String[]> PHASES = Map.of(
            "CREATION_TICKET", new String[]{"S1", "Cr\u00e9ation du ticket et collecte d'information"},
            "GENERATION_DOCUMENTS", new String[]{"S2", "G\u00e9n\u00e9ration des documents"},
            "DEROULEMENT_DEMARCHE", new String[]{"S3", "D\u00e9roulement de la d\u00e9marche"},
            "CLOTURE_DOSSIER", new String[]{"S4", "Cl\u00f4ture de dossier"},
            "ANNULE", new String[]{"S5", "Ticket annul\u00e9"});
    static final String TYPE_CORPUS = "CORPUS";
    private static final Set<String> UNITES = Set.of("JOURS", "MOIS");

    public record Resultat(UUID id, String version, String statut, int lignes, int decisions, int renvois,
                           String motif) {}

    private final SourceReferentiel source;
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public ImportReferentielDemarches(SourceReferentiel source, JdbcTemplate db, ObjectMapper json) {
        this.source = source;
        this.db = db;
        this.json = json;
    }

    @Transactional
    public Resultat importer(UUID acteur) {
        ReferentielCorpus r = source.lire();
        String motif = motifDeRefus(r);
        if (motif == null && r.version().equals(versionActive())) {
            motif = "La version " + r.version() + " est d\u00e9j\u00e0 appliqu\u00e9e.";
        }
        if (motif != null) return journaliser(r, "REFUSE", motif, acteur);

        db.update("UPDATE demarches_referentiel SET actif = FALSE WHERE corpus_version IS NOT NULL AND actif");
        db.update("UPDATE referentiel_decisions SET actif = FALSE WHERE actif");
        db.update("UPDATE referentiel_renvois SET actif = FALSE WHERE actif");

        List<String[]> formalites = formalites(r.lignes());
        List<Object[]> lignes = new ArrayList<>();
        for (int i = 0; i < r.lignes().size(); i++) {
            ReferentielCorpus.Ligne l = r.lignes().get(i);
            String[] formalite = formalites.get(i);
            String[] phase = PHASES.get(l.statut());
            ReferentielCorpus.Condition c = l.condition();
            ReferentielCorpus.Condition s = l.sousCondition();
            ReferentielCorpus.Delai d = l.delai();
            boolean chiffre = d != null && d.valeur() != null && UNITES.contains(d.unite());
            lignes.add(new Object[]{TYPE_CORPUS, l.ordre(), phase[0], phase[1], l.libelle(), l.statut(), l.organisme(),
                    l.obligatoire() ? "O" : "C", c == null ? null : (c.texte() != null && c.variable() == null ? c.texte() : brut(c)),
                    l.justificatif(), l.modele(), d == null ? null : d.texte(),
                    chiffre ? d.valeur() : null, chiffre ? d.unite() : null,
                    d == null ? null : d.depuisVariable(), d == null ? null : d.echeanceVariable(),
                    r.version(), l.matrice(), l.forme(), l.decision(),
                    s == null ? null : s.variable(), s == null ? null : s.valeur(), s == null ? null : s.texte(),
                    c == null ? null : c.variable(), c == null ? null : c.operateur(), c == null ? null : c.valeur(),
                    l.numeroDecision(), l.aCompleter(), l.completePlateforme(),
                    formalite == null ? null : formalite[0], formalite == null ? null : formalite[1]});
        }
        db.batchUpdate("""
                INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle, libelle,
                    statut_ticket, organisme, obligatoire, condition_application, justificatifs_texte, modele_jurika,
                    delai, delai_valeur, delai_unite, delai_reference_donnee, delai_echeance_donnee,
                    corpus_version, matrice, forme, decision_code, sous_condition_variable, sous_condition_valeur,
                    sous_condition_texte, condition_variable, condition_operateur, condition_valeur,
                    numero_decision, a_completer, complete_plateforme, formalite_code, formalite_volet, actif)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE)
                """, lignes);

        List<Object[]> decisions = new ArrayList<>();
        for (ReferentielCorpus.Decision d : r.decisions()) {
            decisions.add(new Object[]{r.version(), d.matrice(), d.numero(), d.section(), d.code(), d.libelle(),
                    d.typeDecision()});
        }
        db.batchUpdate("""
                INSERT INTO referentiel_decisions (corpus_version, matrice, numero, section, code, libelle, type_decision)
                VALUES (?,?,?,?,?,?,?)
                """, decisions);

        List<Object[]> renvois = new ArrayList<>();
        for (ReferentielCorpus.Renvoi v : r.renvois()) {
            renvois.add(new Object[]{r.version(), v.matrice(), v.numero(), v.section(), v.decision(), v.code(),
                    v.familleRenvoi(), v.matriceRenvoi(), v.numeroRenvoi(), v.regle()});
        }
        db.batchUpdate("""
                INSERT INTO referentiel_renvois (corpus_version, matrice, numero, section, decision, code,
                    famille_renvoi, matrice_renvoi, numero_renvoi, regle)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, renvois);
        return journaliser(r, "APPLIQUE", null, acteur);
    }

    /**
     * Lot L4b (E3) : le parcours de creation du corpus compare, numero par numero, aux lignes
     * de la creation en service (V24). Chaque ecart est nomme ; aucun n'est aligne en silence :
     * la creation reste servie par ses lignes en service, et les ecarts vont au rapport.
     */
    List<String> ecartsCreation(List<ReferentielCorpus.Ligne> corpus) {
        if (corpus == null || corpus.isEmpty()) return List.of("Parcours de cr\u00e9ation absent du corpus.");
        Map<Integer, Map<String, Object>> enService = new LinkedHashMap<>();
        for (Map<String, Object> l : db.queryForList("""
                SELECT ordre, statut_ticket, libelle, condition_application, delai, modele_jurika, justificatifs_texte
                FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND corpus_version IS NULL AND actif
                ORDER BY ordre
                """)) {
            enService.put(((Number) l.get("ordre")).intValue(), l);
        }
        List<String> ecarts = new ArrayList<>();
        Set<Integer> vus = new java.util.HashSet<>();
        for (ReferentielCorpus.Ligne c : corpus) {
            int n = c.ordre();
            vus.add(n);
            Map<String, Object> s = enService.get(n);
            if (s == null) {
                ecarts.add("n\u00b0 " + n + " : ligne du corpus absente des lignes en service (\u00ab " + c.libelle() + " \u00bb)");
                continue;
            }
            comparer(ecarts, n, "statut", s.get("statut_ticket"), c.statut());
            comparer(ecarts, n, "d\u00e9marche", s.get("libelle"), c.libelle());
            comparer(ecarts, n, "condition", s.get("condition_application"), c.condition() == null ? null : c.condition().texte());
            comparer(ecarts, n, "d\u00e9lai", s.get("delai"), c.delai() == null ? null : c.delai().texte());
            comparer(ecarts, n, "mod\u00e8le", s.get("modele_jurika"), c.modele());
            comparer(ecarts, n, "justificatif", s.get("justificatifs_texte"), c.justificatif());
        }
        for (Integer n : enService.keySet()) {
            if (!vus.contains(n)) ecarts.add("n\u00b0 " + n + " : ligne en service absente du corpus (\u00ab "
                    + enService.get(n).get("libelle") + " \u00bb)");
        }
        return ecarts;
    }

    private static void comparer(List<String> ecarts, int n, String champ, Object enService, String corpus) {
        String a = normaliser(enService);
        String b = normaliser(corpus);
        if (!a.equals(b)) {
            ecarts.add("n\u00b0 " + n + " : " + champ + " : en service \u00ab " + a + " \u00bb, corpus \u00ab " + b + " \u00bb");
        }
    }

    /**
     * Presentation seule : espaces, case "&#9744;" en tete d'une etape, tiret seul pour une
     * cellule vide. Tout autre ecart de texte est rapporte.
     */
    static String normaliser(Object o) {
        if (o == null) return "";
        String t = String.valueOf(o).replaceAll("\\s+", " ").trim();
        if (t.startsWith("\u2610")) t = t.substring(1).trim();
        return t.equals("\u2014") ? "" : t;
    }

    private static final java.util.regex.Pattern DEPOT = java.util.regex.Pattern.compile("^(.*) \u2014 d\u00e9p\u00f4t\\b.*$");
    private static final java.util.regex.Pattern RETRAIT = java.util.regex.Pattern.compile("^(.*) \u2014 retrait\\b.*$");

    /**
     * RG-DEM-02 : une formalite a depot puis retrait figure sur deux lignes liees. Au corpus,
     * la ligne "X -- depot" est suivie, pour la meme decision, de "X -- retrait ..." : les deux
     * recoivent le meme code (rang de la matrice et ordre du depot). Une ligne sans pendant
     * reste une ligne simple.
     */
    static List<String[]> formalites(List<ReferentielCorpus.Ligne> lignes) {
        List<String[]> out = new ArrayList<>();
        Map<String, String> ouverts = new LinkedHashMap<>();
        for (ReferentielCorpus.Ligne l : lignes) {
            String libelle = l.libelle() == null ? "" : l.libelle().trim();
            java.util.regex.Matcher d = DEPOT.matcher(libelle);
            java.util.regex.Matcher r = RETRAIT.matcher(libelle);
            if (d.matches()) {
                String code = "R" + MatricesDuTicket.rang(l.matrice()) + "-" + l.ordre();
                ouverts.put(l.matrice() + "|" + l.decision() + "|" + d.group(1), code);
                out.add(new String[]{code, "DEPOT"});
            } else if (r.matches() && ouverts.containsKey(l.matrice() + "|" + l.decision() + "|" + r.group(1))) {
                out.add(new String[]{ouverts.remove(l.matrice() + "|" + l.decision() + "|" + r.group(1)), "RETRAIT"});
            } else {
                out.add(null);
            }
        }
        return out;
    }

    /** Journal des imports, du plus recent au plus ancien. */
    public List<Map<String, Object>> journal() {
        return db.queryForList("""
                SELECT id, corpus_version, statut, lignes, decisions, renvois, motif, importe_par, importe_le,
                       rapport::text AS rapport
                FROM referentiel_imports ORDER BY importe_le DESC LIMIT 50
                """);
    }

    /** Version du corpus actuellement appliquee, ou null. */
    public String versionActive() {
        List<String> v = db.queryForList("""
                SELECT corpus_version FROM referentiel_imports WHERE statut = 'APPLIQUE'
                ORDER BY importe_le DESC LIMIT 1
                """, String.class);
        return v.isEmpty() ? null : v.get(0);
    }

    static String motifDeRefus(ReferentielCorpus r) {
        if (r == null || r.version() == null || r.version().isBlank()) return "Version du corpus absente.";
        if (r.rapport() != null && r.rapport().erreurs() != null && !r.rapport().erreurs().isEmpty()) {
            return "Le rapport d'analyse comporte " + r.rapport().erreurs().size() + " erreur(s) : "
                    + String.join(" ; ", r.rapport().erreurs().subList(0, Math.min(5, r.rapport().erreurs().size())));
        }
        if (r.lignes() == null || r.lignes().isEmpty()) return "Aucune d\u00e9marche dans le corpus.";
        for (ReferentielCorpus.Ligne l : r.lignes()) {
            if (MatricesDuTicket.rang(l.matrice()) == 0) {
                return "Matrice inconnue de la plateforme : \u00ab " + l.matrice() + " \u00bb (rang \u00e0 d\u00e9finir).";
            }
            if (!PHASES.containsKey(l.statut())) {
                return l.matrice() + " l. " + l.ordre() + " : statut inconnu \u00ab " + l.statut() + " \u00bb.";
            }
        }
        return null;
    }

    private Resultat journaliser(ReferentielCorpus r, String statut, String motif, UUID acteur) {
        String version = r == null || r.version() == null ? "?" : r.version();
        Map<String, Object> rapport = new LinkedHashMap<>();
        if (r != null && r.rapport() != null) {
            rapport.put("lignesParOnglet", r.rapport().lignesParOnglet());
            rapport.put("total", r.rapport().total());
            rapport.put("conditionsPhrases", r.rapport().conditionsPhrases());
            rapport.put("delaisTextuels", r.rapport().delaisTextuels());
            rapport.put("erreurs", r.rapport().erreurs());
        }
        if (r != null && "APPLIQUE".equals(statut)) rapport.put("ecartsCreation", ecartsCreation(r.parcoursCreation()));
        int lignes = "APPLIQUE".equals(statut) ? r.lignes().size() : 0;
        int decisions = "APPLIQUE".equals(statut) ? r.decisions().size() : 0;
        int renvois = "APPLIQUE".equals(statut) ? r.renvois().size() : 0;
        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO referentiel_imports (id, corpus_version, statut, lignes, decisions, renvois, motif, rapport, importe_par)
                VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?)
                """, id, version, statut, lignes, decisions, renvois, motif, ecrire(rapport), acteur);
        return new Resultat(id, version, statut, lignes, decisions, renvois, motif);
    }

    private String ecrire(Map<String, Object> m) {
        try {
            return json.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String brut(ReferentielCorpus.Condition c) {
        if ("EGAL".equals(c.operateur())) return "$" + c.variable() + " = \u00ab " + c.valeur() + " \u00bb"
                + (c.texte() == null ? "" : " \u2014 (" + c.texte() + ")");
        if ("EXISTE".equals(c.operateur())) return "$" + c.variable() + " existe"
                + (c.texte() == null ? "" : " \u2014 (" + c.texte() + ")");
        return c.texte();
    }
}
