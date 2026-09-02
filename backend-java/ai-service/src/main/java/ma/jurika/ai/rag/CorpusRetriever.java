package ma.jurika.ai.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Recupere les chunks juridiques les plus pertinents pour une question.
 *
 * <p>Deux voies, combinables :
 * <ul>
 *   <li><b>FTS PostgreSQL</b> (tsvector + ts_rank) — toujours disponible, sans cout API.</li>
 *   <li><b>Vectorielle</b> (pgvector cosine) — active seulement si la voie embeddings est prete
 *       ({@code rag.enabled} + cle). {@link #searchHybrid} fusionne vecteur + FTS ; a defaut de
 *       vecteur, elle degrade proprement en FTS pur.</li>
 * </ul>
 * Toutes les requetes sont <b>scopees au workspace</b> (isolation multi-tenant) ; un
 * {@code workspaceId} null ne retourne jamais de source.
 */
@Component
public class CorpusRetriever {

    private final JdbcTemplate jdbc;
    /** Optionnel : null quand la voie vectorielle n'est pas câblée (tests FTS, contexte hors Spring). */
    private final RagEmbeddingClient embeddingClient;

    @Autowired
    public CorpusRetriever(JdbcTemplate jdbc, RagEmbeddingClient embeddingClient) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
    }

    /** Constructeur FTS-only (sans embeddings) — utilise par les tests et le repli sans cle. */
    public CorpusRetriever(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    /**
     * Mots interrogatifs et mots outils que la configuration {@code french} de
     * PostgreSQL ne considère PAS comme vides.
     *
     * <p>C'est le cœur du problème que cette classe corrige : {@code
     * websearch_to_tsquery} relie tous les lexèmes par un ET, donc « <b>Comment</b>
     * créer une SARL ? » exigeait qu'un passage contienne le lexème <i>comment</i>.
     * Aucune question posée en français naturel ne ressortait — pas même les quatre
     * suggestions affichées par la page ChatBot.
     */
    private static final Set<String> MOTS_OUTILS = Set.of(
            // interrogatifs
            "comment", "quel", "quelle", "quels", "quelles", "que", "qu", "qui", "quoi",
            "pourquoi", "quand", "ou", "où", "combien", "est-ce", "estce", "lequel",
            "laquelle", "lesquels", "lesquelles",
            // tournures modales fréquentes dans une question
            "dois", "doit", "doivent", "peut", "peuvent", "puis", "puis-je", "faut",
            "faut-il", "y", "a-t-il", "a-t-elle", "ont-ils", "peut-on", "existe-t-il",
            "prevoit", "prévoit", "prevoient", "prévoient", "prevu", "prévu",
            // mots outils que le dictionnaire français laisse parfois passer
            "est", "sont", "etre", "être", "cas", "sujet", "propos", "concernant",
            // determinants et prepositions de 3 lettres et plus : ils survivent au
            // filtre de longueur et diluent l'etage « ET » sans rien apporter au
            // classement.
            "les", "des", "une", "aux", "par", "pour", "sur", "dans", "avec", "sans",
            "son", "ses", "leur", "leurs", "cette", "cet", "ces", "tout", "tous",
            "toute", "toutes", "notre", "nos", "votre", "vos", "mon", "mes");

    /** Un terme plus court que cela n'apporte rien au classement. */
    private static final int LONGUEUR_MINIMALE_TERME = 3;

    /**
     * Part du meilleur score en dessous de laquelle un passage trouvé par la voie
     * « OU » est écarté. Relatif et non absolu : {@code ts_rank_cd} n'a pas
     * d'échelle stable d'un corpus à l'autre, seul le rapport au meilleur hit est
     * comparable.
     */
    private static final double SEUIL_RELATIF = 0.25;

    /**
     * Cherche les top-K chunks dans rag_corpus_chunks, <b>scopes au workspace
     * courant</b> (isolation multi-tenant). Un {@code workspaceId} null ne retourne
     * aucune source du corpus uploade. Retourne pour chaque chunk : source, article,
     * chunk_text, rank.
     *
     * <p>Trois étages, du plus précis au plus tolérant. On s'arrête au premier qui
     * rend quelque chose :
     * <ol>
     *   <li><b>ET strict</b> sur les termes significatifs (mots outils retirés) —
     *       c'est le comportement historique, mais débarrassé des interrogatifs qui
     *       le faisaient échouer systématiquement ;</li>
     *   <li><b>OU pondéré</b> classé par {@code ts_rank_cd}, avec un seuil relatif
     *       au meilleur score pour ne pas remonter n'importe quel passage contenant
     *       un mot commun ;</li>
     *   <li><b>repli</b> sur les 3 termes les plus longs — souvent les plus
     *       porteurs de sens — sans seuil.</li>
     * </ol>
     * L'objectif est de ne jamais rendre 0 passage quand des termes significatifs
     * de la question existent quelque part dans le corpus.
     */
    public List<Map<String, Object>> search(String question, int limit, UUID workspaceId) {
        if (question == null || question.isBlank()) return List.of();
        if (workspaceId == null) return List.of();

        List<String> termes = termesSignificatifs(question);
        if (termes.isEmpty()) {
            // Question ne contenant QUE des mots outils : on repart du texte brut
            // plutot que de ne rien chercher du tout.
            termes = tousLesTermes(question);
        }
        if (termes.isEmpty()) return List.of();

        List<Map<String, Object>> hits = interroger(String.join(" ", termes), limit, workspaceId, 0.0);
        if (!hits.isEmpty()) return hits;

        hits = interroger(String.join(" OR ", termes), limit, workspaceId, SEUIL_RELATIF);
        if (!hits.isEmpty()) return hits;

        List<String> porteurs = termes.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(3)
                .toList();
        if (porteurs.size() < termes.size()) {
            hits = interroger(String.join(" OR ", porteurs), limit, workspaceId, 0.0);
            if (!hits.isEmpty()) return hits;
        }

        return fallbackIlike(question, limit, workspaceId);
    }

    /**
     * Exécute une recherche plein texte et, si {@code seuilRelatif > 0}, écarte les
     * passages dont le score est trop loin derrière le meilleur.
     *
     * <p>La requête passe par {@code websearch_to_tsquery} (et non {@code to_tsquery})
     * dans les deux cas : elle accepte l'opérateur {@code OR}, avale la ponctuation
     * et ignore silencieusement les mots vides, sans exposer la moindre surface
     * d'injection puisque tout passe en paramètre lié.
     */
    private List<Map<String, Object>> interroger(String expression, int limit,
                                                 UUID workspaceId, double seuilRelatif) {
        if (expression == null || expression.isBlank()) return List.of();
        // En mode « OU » on ratisse plus large avant de filtrer sur le seuil.
        int aChercher = seuilRelatif > 0 ? Math.max(limit * 3, limit) : limit;
        String sql = """
                SELECT source, article, chunk_text, metadata,
                       ts_rank_cd(search_vector, q) AS rank
                FROM rag_corpus_chunks,
                     websearch_to_tsquery('french', ?) q
                WHERE workspace_id = ?
                  AND search_vector @@ q
                ORDER BY rank DESC
                LIMIT ?
                """;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, expression, workspaceId, aChercher);
        } catch (Exception ex) {
            // FTS indisponible (migration non jouee, Postgres trop ancien) : le
            // caller retombera sur le repli ILIKE.
            return List.of();
        }
        if (rows.isEmpty() || seuilRelatif <= 0) {
            return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
        }

        double meilleur = score(rows.get(0));
        if (meilleur <= 0) return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
        List<Map<String, Object>> retenus = new ArrayList<>(limit);
        for (Map<String, Object> row : rows) {
            if (retenus.size() >= limit) break;
            if (score(row) >= meilleur * seuilRelatif) retenus.add(row);
        }
        return retenus;
    }

    private static double score(Map<String, Object> row) {
        Object r = row.get("rank");
        return r instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** Termes de la question, mots outils et mots trop courts retirés. */
    static List<String> termesSignificatifs(String question) {
        List<String> out = new ArrayList<>();
        for (String terme : tousLesTermes(question)) {
            if (terme.length() < LONGUEUR_MINIMALE_TERME) continue;
            if (MOTS_OUTILS.contains(terme)) continue;
            out.add(terme);
        }
        return out;
    }

    /**
     * Découpe la question en termes alphanumériques. Le résultat ne contient que des
     * lettres et des chiffres : rien qui puisse être interprété comme un opérateur
     * par {@code websearch_to_tsquery}.
     */
    static List<String> tousLesTermes(String question) {
        List<String> out = new ArrayList<>();
        for (String brut : question.toLowerCase(Locale.FRENCH).split("[^\\p{L}\\p{N}]+")) {
            if (!brut.isBlank()) out.add(brut);
        }
        return out;
    }

    private List<Map<String, Object>> fallbackIlike(String question, int limit, UUID workspaceId) {
        try {
            String sql = """
                    SELECT source, article, chunk_text, metadata, 0.5 AS rank
                    FROM rag_corpus_chunks
                    WHERE workspace_id = ?
                      AND chunk_text ILIKE ?
                    LIMIT ?
                    """;
            return jdbc.queryForList(sql, workspaceId, "%" + question + "%", limit);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * Recherche <b>vectorielle</b> (cosine, pgvector) scopee au workspace. La question est
     * transformee en embedding puis comparee aux chunks ({@code embedding <=> ?::vector}).
     *
     * <p>Renvoie une liste vide (sans appel reseau ni SQL) si la voie vectorielle est
     * desactivee, si l'embedding echoue, ou si {@code workspaceId} est null — le caller
     * peut alors retomber sur le FTS. Le {@code rank} vaut {@code 1 - distance_cosine}.
     */
    public List<Map<String, Object>> searchByVector(String question, int limit, UUID workspaceId) {
        if (question == null || question.isBlank()) return List.of();
        if (workspaceId == null) return List.of();
        if (embeddingClient == null || !embeddingClient.isReady()) return List.of();

        Optional<float[]> embedded = embeddingClient.embed(question);
        if (embedded.isEmpty()) return List.of();
        String vectorLiteral = RagEmbeddingClient.toVectorLiteral(embedded.get());
        if (vectorLiteral == null) return List.of();

        String sql = """
                SELECT source, article, chunk_text, metadata,
                       1 - (embedding <=> ?::vector) AS rank
                FROM rag_corpus_chunks
                WHERE workspace_id = ?
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        try {
            return jdbc.queryForList(sql, vectorLiteral, workspaceId, vectorLiteral, limit);
        } catch (Exception ex) {
            // Colonne/index vectoriel absent ou incoherent -> pas de vecteur, on laisse le FTS jouer.
            return List.of();
        }
    }

    /**
     * Recherche <b>hybride</b> : privilegie la voie vectorielle quand elle est disponible,
     * complete/replie sur le FTS, puis fusionne en dedupliquant par {@code chunk_text}
     * (les hits vectoriels d'abord). A defaut de vecteur, comportement identique a
     * {@link #search} (FTS pur). {@code workspaceId} null -> liste vide.
     */
    public List<Map<String, Object>> searchHybrid(String question, int limit, UUID workspaceId) {
        if (workspaceId == null) return List.of();

        List<Map<String, Object>> vector = searchByVector(question, limit, workspaceId);
        List<Map<String, Object>> fts = search(question, limit, workspaceId);
        if (vector.isEmpty()) return fts; // FTS pur (comportement historique).

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : vector) {
            merged.putIfAbsent(dedupKey(row), row);
        }
        for (Map<String, Object> row : fts) {
            merged.putIfAbsent(dedupKey(row), row);
        }
        List<Map<String, Object>> out = new ArrayList<>(merged.values());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private String dedupKey(Map<String, Object> row) {
        return String.valueOf(row.get("chunk_text"));
    }

    /**
     * Formate les chunks en liste de citations digestible par le frontend
     * (reutilise le format {reference, article, topic} attendu par l'UI).
     */
    public List<Map<String, Object>> asCitations(List<Map<String, Object>> chunks) {
        List<Map<String, Object>> out = new ArrayList<>(chunks.size());
        for (Map<String, Object> row : chunks) {
            Map<String, Object> citation = new HashMap<>();
            citation.put("reference", row.get("source"));
            citation.put("article", row.get("article"));
            citation.put("extract", truncate(String.valueOf(row.get("chunk_text")), 200));
            citation.put("rank", row.get("rank"));
            out.add(citation);
        }
        return out;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
