package ma.jurika.ticket.application.referentiel;

import ma.jurika.common.exception.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L4d (E7), decision du directeur du 11 octobre 2026 : referentiel des delais de depot
 * au greffe par nature d'acte, avec l'article cite, "a confirmer" s'il n'est pas verifie,
 * ajustable par le cabinet.
 *
 * <p>Rien n'est saisi ni invente : le referentiel se lit dans la version active du corpus
 * (lignes de depot au greffe de {@code demarches_referentiel}, une nature d'acte = une
 * decision d'une matrice). L'article est celui que le texte de la matrice cite ; le delai est
 * "cite par le corpus" quand un article l'accompagne sans reserve, "a confirmer" sinon (aucun
 * article, ou texte marque PROBABLE, non etabli, non fixe). Le cabinet peut ajuster un delai
 * ({@code delais_greffe_cabinet}, ticket V39) : l'ajustement s'applique aux lignes de depot
 * des tickets du cabinet, et le referentiel montre les deux.
 */
@Component
public class DelaisGreffe {

    public enum Statut { CITE_PAR_LE_CORPUS, A_CONFIRMER }

    public record Ajustement(int delaiValeur, String delaiUnite, String article, String note,
                             UUID modifiePar, String modifieLe) {}

    public record Nature(String matrice, String decisionCode, String decisionLibelle, String delaiTexte,
                         Integer delaiValeur, String delaiUnite, String article, Statut statut,
                         Ajustement ajustement) {}

    public record Saisie(Integer delaiValeur, String delaiUnite, String article, String note) {}

    /** Reference d'article dans un texte de matrice : "art. 75 du code de commerce", "art. 95 de la loi n\u00b0 5-96"... */
    private static final Pattern ARTICLE = Pattern.compile(
            "(art(?:icle)?s?\\.?\\s[^;()]*?(?:code de commerce|loi\\s+n\\s*\u00b0\\s*\\d+-\\d+))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MEME_ARTICLE = Pattern.compile("m\u00eame article", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESERVE = Pattern.compile("PROBABLE|non \u00e9tabli|non fix\u00e9", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate db;

    public DelaisGreffe(JdbcTemplate db) {
        this.db = db;
    }

    /** Une ligne du referentiel est-elle un depot au greffe ? */
    static boolean estDepotGreffe(String organisme, String libelle) {
        String o = organisme == null ? "" : organisme.toLowerCase(Locale.ROOT);
        String l = libelle == null ? "" : libelle.toLowerCase(Locale.ROOT);
        return o.contains("greffe") && l.contains("d\u00e9p\u00f4t");
    }

    /** Article cite par le texte du delai ("code de commerce" complete par la loi n\u00b0 15-95), ou null. */
    static String article(String texte) {
        if (texte == null) return null;
        Matcher m = ARTICLE.matcher(texte);
        if (!m.find()) return null;
        String a = m.group(1).trim();
        if (a.toLowerCase(Locale.ROOT).contains("code de commerce")) a = a + " (loi n\u00b0 15-95)";
        return a;
    }

    static Statut statut(String texte, String article) {
        if (article == null) return Statut.A_CONFIRMER;
        if (texte != null && (RESERVE.matcher(texte).find() || MEME_ARTICLE.matcher(texte).find())) {
            return Statut.A_CONFIRMER;
        }
        return Statut.CITE_PAR_LE_CORPUS;
    }

    /** Le referentiel du cabinet : natures d'acte de la version active, avec ses ajustements. */
    @Transactional(readOnly = true)
    public List<Nature> referentiel(UUID workspaceId) {
        Map<String, Ajustement> ajustements = ajustements(db, workspaceId);
        Map<String, Nature> parNature = new LinkedHashMap<>();
        for (Map<String, Object> l : db.queryForList("""
                SELECT d.matrice, d.decision_code, d.organisme, d.libelle, d.delai, d.delai_valeur, d.delai_unite,
                       (SELECT r.libelle FROM referentiel_decisions r
                        WHERE r.actif AND r.matrice = d.matrice AND r.code = d.decision_code LIMIT 1) AS decision_libelle
                FROM demarches_referentiel d
                WHERE d.corpus_version IS NOT NULL AND d.actif AND d.decision_code IS NOT NULL
                ORDER BY d.matrice, d.ordre
                """)) {
            if (!estDepotGreffe((String) l.get("organisme"), (String) l.get("libelle"))) continue;
            String cle = cle((String) l.get("matrice"), (String) l.get("decision_code"));
            String texte = (String) l.get("delai");
            Nature deja = parNature.get(cle);
            // Une nature d'acte peut porter plusieurs lignes de depot : la premiere qui dit un delai.
            if (deja != null && (deja.delaiTexte() != null || texte == null)) continue;
            String article = article(texte);
            Number valeur = (Number) l.get("delai_valeur");
            parNature.put(cle, new Nature((String) l.get("matrice"), (String) l.get("decision_code"),
                    (String) l.get("decision_libelle"), texte, valeur == null ? null : valeur.intValue(),
                    (String) l.get("delai_unite"), article, statut(texte, article), ajustements.get(cle)));
        }
        return new ArrayList<>(parNature.values());
    }

    /** Ajuste le delai d'une nature d'acte pour le cabinet (superviseur). */
    @Transactional
    public void ajuster(UUID workspaceId, String matrice, String decisionCode, Saisie s, UUID acteur) {
        if (s == null || s.delaiValeur() == null || s.delaiValeur() <= 0) {
            throw new ValidationException("Indiquez le d\u00e9lai ajust\u00e9 (nombre de jours ou de mois, sup\u00e9rieur \u00e0 z\u00e9ro).");
        }
        if (!"JOURS".equals(s.delaiUnite()) && !"MOIS".equals(s.delaiUnite())) {
            throw new ValidationException("Indiquez l\u2019unit\u00e9 du d\u00e9lai : jours ou mois.");
        }
        boolean connue = referentiel(workspaceId).stream()
                .anyMatch(n -> n.matrice().equals(matrice) && n.decisionCode().equals(decisionCode));
        if (!connue) {
            throw new ValidationException("Nature d\u2019acte inconnue du r\u00e9f\u00e9rentiel : " + matrice + " / " + decisionCode + ".");
        }
        db.update("""
                INSERT INTO delais_greffe_cabinet (workspace_id, matrice, decision_code, delai_valeur, delai_unite,
                                                   article, note, modifie_par, modifie_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (workspace_id, matrice, decision_code) DO UPDATE SET
                    delai_valeur = EXCLUDED.delai_valeur, delai_unite = EXCLUDED.delai_unite,
                    article = EXCLUDED.article, note = EXCLUDED.note,
                    modifie_par = EXCLUDED.modifie_par, modifie_le = now()
                """, workspaceId, matrice, decisionCode, s.delaiValeur(), s.delaiUnite(),
                vide(s.article()), vide(s.note()), acteur);
    }

    /** Retire l'ajustement : le delai du corpus s'applique de nouveau. */
    @Transactional
    public void retirer(UUID workspaceId, String matrice, String decisionCode) {
        db.update("DELETE FROM delais_greffe_cabinet WHERE workspace_id = ? AND matrice = ? AND decision_code = ?",
                workspaceId, matrice, decisionCode);
    }

    /** Ajustements du cabinet, par cle matrice|decision. */
    static Map<String, Ajustement> ajustements(JdbcTemplate db, UUID workspaceId) {
        Map<String, Ajustement> out = new HashMap<>();
        if (workspaceId == null) return out;
        for (Map<String, Object> r : db.queryForList("""
                SELECT matrice, decision_code, delai_valeur, delai_unite, article, note, modifie_par,
                       to_char(modifie_le AT TIME ZONE 'Africa/Casablanca', 'YYYY-MM-DD') AS modifie_le
                FROM delais_greffe_cabinet WHERE workspace_id = ?
                """, workspaceId)) {
            out.put(cle((String) r.get("matrice"), (String) r.get("decision_code")),
                    new Ajustement(((Number) r.get("delai_valeur")).intValue(), (String) r.get("delai_unite"),
                            (String) r.get("article"), (String) r.get("note"), (UUID) r.get("modifie_par"),
                            (String) r.get("modifie_le")));
        }
        return out;
    }

    /**
     * Applique l'ajustement du cabinet a une ligne de depot au greffe (copie de la ligne), ou
     * rend la ligne telle quelle.
     */
    static Map<String, Object> appliquer(Map<String, Object> ligne, Map<String, Ajustement> ajustements) {
        if (ajustements.isEmpty() || !estDepotGreffe((String) ligne.get("organisme"), (String) ligne.get("libelle"))) {
            return ligne;
        }
        Ajustement a = ajustements.get(cle((String) ligne.get("matrice"), (String) ligne.get("decision_code")));
        if (a == null) return ligne;
        Map<String, Object> l = new HashMap<>(ligne);
        l.put("delai_valeur", a.delaiValeur());
        l.put("delai_unite", a.delaiUnite());
        String unite = "JOURS".equals(a.delaiUnite()) ? "jours" : "mois";
        l.put("delai", a.delaiValeur() + " " + unite + " \u2014 d\u00e9lai ajust\u00e9 par le cabinet"
                + (a.article() == null ? "" : " (" + a.article() + ")")
                + (ligne.get("delai") == null ? "" : " ; texte de la matrice : " + ligne.get("delai")));
        return l;
    }

    static String cle(String matrice, String decision) {
        return matrice + "|" + decision;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
