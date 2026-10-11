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

        List<Object[]> lignes = new ArrayList<>();
        for (ReferentielCorpus.Ligne l : r.lignes()) {
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
                    l.numeroDecision(), l.aCompleter(), l.completePlateforme()});
        }
        db.batchUpdate("""
                INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle, libelle,
                    statut_ticket, organisme, obligatoire, condition_application, justificatifs_texte, modele_jurika,
                    delai, delai_valeur, delai_unite, delai_reference_donnee, delai_echeance_donnee,
                    corpus_version, matrice, forme, decision_code, sous_condition_variable, sous_condition_valeur,
                    sous_condition_texte, condition_variable, condition_operateur, condition_valeur,
                    numero_decision, a_completer, complete_plateforme, actif)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE)
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
