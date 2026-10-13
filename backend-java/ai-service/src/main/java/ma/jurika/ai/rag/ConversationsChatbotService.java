package ma.jurika.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lot L5b E2 (RG-COM-12) : conversations du chatbot, persistees par utilisateur.
 *
 * <p>Regles tenues ici :
 * <ul>
 *   <li><b>Propriete</b> : toute lecture et toute ecriture filtrent sur le workspace ET sur
 *       l'utilisateur. Une conversation d'un autre utilisateur, meme du meme cabinet, est
 *       introuvable (404) : son existence n'est pas revelee. ai-service se connecte encore en
 *       proprietaire (hors RLS) : ce filtrage applicatif suffit a lui seul ; la RLS de V17
 *       (workspace) le double pour le role d'execution {@code jurika_app}.</li>
 *   <li><b>RG-COM-04 d'abord</b> : la question est passee au filtre des donnees personnelles
 *       AVANT toute recherche et toute ecriture ; une question bloquee n'est jamais
 *       enregistree (ni conversation, ni echange). Un titre saisi passe le meme filtre.</li>
 *   <li><b>Titre</b> : tire de la premiere question, tronque sans couper un mot ; modifiable.</li>
 *   <li><b>Archivage</b> : une conversation archivee reste consultable ; on n'y pose plus de
 *       question (409) tant qu'elle n'est pas desarchivee.</li>
 *   <li><b>Conservation</b> (decision de l'utilisateur, lot L6, 2026-10-13) : une conversation est
 *       conservee tant que le compte de son auteur existe (un compte n'est jamais supprime,
 *       seulement desactive : RG-CPT-03), puis elle suit le sort des donnees du cabinet :
 *       archivage a l'arret du forfait (RG-DP-04), ou suppression a la demande du cabinet.
 *       Aucune purge automatique ici ; les tables {@code chatbot_conversations} et
 *       {@code chatbot_echanges} portent {@code workspace_id} et doivent etre comprises dans
 *       l'archivage et la suppression des donnees d'un cabinet (lots L7 et L8).</li>
 * </ul>
 * Chaque ecriture passe dans une transaction qui pose {@code app.current_workspace_id}
 * (reglage local, il ne fuit pas dans le pool).
 */
@Service
public class ConversationsChatbotService {

    /** Longueur maximale du titre tire de la premiere question. */
    public static final int TITRE_INITIAL_MAX = 80;
    /** Longueur maximale d'un titre saisi (colonne VARCHAR(120)). */
    public static final int TITRE_MAX = 120;

    private static final String POINTS_DE_SUSPENSION = "\u2026";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LISTE_SOURCES = new TypeReference<>() {};

    /** Proprietaire d'une conversation : un utilisateur d'un cabinet. */
    public record Proprietaire(UUID workspaceId, UUID userId) {
        public Proprietaire {
            if (workspaceId == null || userId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Utilisateur ou cabinet introuvable : conversations indisponibles.");
            }
        }
    }

    private final JdbcTemplate jdbc;
    private final RagService rag;

    public ConversationsChatbotService(JdbcTemplate jdbc, RagService rag) {
        this.jdbc = jdbc;
        this.rag = rag;
    }

    // ------------------------------------------------------------------ lecture

    /** Mes conversations, actives ou archivees, de la plus recemment active a la plus ancienne. */
    public List<Map<String, Object>> lister(Proprietaire p, boolean archivees) {
        return dansLeCabinet(p, () -> jdbc.query(
                "SELECT c.id, c.titre, c.archivee, c.archived_at, c.created_at, c.updated_at, "
                        + "(SELECT count(*) FROM chatbot_echanges e WHERE e.conversation_id = c.id "
                        + "   AND e.workspace_id = c.workspace_id AND e.user_id = c.user_id) AS nombre_echanges "
                        + "FROM chatbot_conversations c "
                        + "WHERE c.workspace_id = ? AND c.user_id = ? AND c.archivee = ? "
                        + "ORDER BY c.updated_at DESC, c.created_at DESC",
                (rs, i) -> conversation(rs), p.workspaceId(), p.userId(), archivees));
    }

    /** Une de mes conversations, avec ses echanges dans l'ordre (404 si elle n'est pas a moi). */
    public Map<String, Object> lire(Proprietaire p, UUID id) {
        return dansLeCabinet(p, () -> {
            Map<String, Object> conv = trouver(p, id);
            conv.put("echanges", jdbc.query(
                    "SELECT id, question, reponse, sources, rag_mode, confidence, created_at "
                            + "FROM chatbot_echanges "
                            + "WHERE conversation_id = ? AND workspace_id = ? AND user_id = ? "
                            + "ORDER BY created_at, id",
                    (rs, i) -> echange(rs), id, p.workspaceId(), p.userId()));
            return conv;
        });
    }

    // ----------------------------------------------------------------- ecriture

    /**
     * Ouvre une conversation par sa premiere question : filtre RG-COM-04, reponse, puis
     * enregistrement de la conversation (titre tire de la question) et de l'echange.
     */
    public Map<String, Object> creer(Proprietaire p, String question) {
        String q = questionFiltree(question);
        Map<String, Object> reponse = rag.ask(q);
        return dansLeCabinet(p, () -> {
            UUID id = jdbc.queryForObject(
                    "INSERT INTO chatbot_conversations (workspace_id, user_id, titre) VALUES (?, ?, ?) RETURNING id",
                    UUID.class, p.workspaceId(), p.userId(), titreDe(q));
            Map<String, Object> echange = enregistrer(p, id, q, reponse);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("conversation", trouver(p, id));
            out.put("echange", echange);
            return out;
        });
    }

    /** Pose une question dans une de mes conversations actives. */
    public Map<String, Object> poser(Proprietaire p, UUID id, String question) {
        String q = questionFiltree(question);
        // Controle avant la recherche : pas de reponse calculee pour une conversation refusee.
        dansLeCabinet(p, () -> exigerActive(trouver(p, id)));
        Map<String, Object> reponse = rag.ask(q);
        return dansLeCabinet(p, () -> {
            // Verrou : un archivage concurrent ne laisse pas passer la question.
            jdbc.queryForList("SELECT id FROM chatbot_conversations WHERE id = ? AND workspace_id = ? AND user_id = ? FOR UPDATE",
                    id, p.workspaceId(), p.userId());
            exigerActive(trouver(p, id));
            Map<String, Object> echange = enregistrer(p, id, q, reponse);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("conversation", trouver(p, id));
            out.put("echange", echange);
            return out;
        });
    }

    /** Renomme une de mes conversations (active ou archivee). */
    public Map<String, Object> renommer(Proprietaire p, UUID id, String titre) {
        String t = titre == null ? "" : titre.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le titre ne peut pas être vide.");
        }
        if (t.length() > TITRE_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le titre ne peut pas dépasser " + TITRE_MAX + " caractères.");
        }
        List<FiltreDonneesPersonnelles.Nature> natures = FiltreDonneesPersonnelles.detecter(t);
        if (!natures.isEmpty()) {
            throw new DonneePersonnelleDetecteeException(natures);
        }
        return dansLeCabinet(p, () -> {
            int n = jdbc.update("UPDATE chatbot_conversations SET titre = ? "
                    + "WHERE id = ? AND workspace_id = ? AND user_id = ?", t, id, p.workspaceId(), p.userId());
            if (n == 0) throw introuvable();
            return trouver(p, id);
        });
    }

    /** Archive ou desarchive une de mes conversations ; elle reste consultable. */
    public Map<String, Object> archiver(Proprietaire p, UUID id, boolean archivee) {
        return dansLeCabinet(p, () -> {
            int n = jdbc.update("UPDATE chatbot_conversations "
                            + "SET archivee = ?, archived_at = CASE WHEN ? THEN now() ELSE NULL END "
                            + "WHERE id = ? AND workspace_id = ? AND user_id = ?",
                    archivee, archivee, id, p.workspaceId(), p.userId());
            if (n == 0) throw introuvable();
            return trouver(p, id);
        });
    }

    // ------------------------------------------------------------------ outils

    /**
     * Titre initial : la premiere question, espaces normalises, tronquee a
     * {@link #TITRE_INITIAL_MAX} caracteres sans couper un mot (points de suspension).
     */
    public static String titreDe(String question) {
        String t = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (t.length() <= TITRE_INITIAL_MAX) return t;
        int limite = TITRE_INITIAL_MAX - 1; // place des points de suspension
        // Dernier espace a la limite ou avant : le mot qui deborde est retire en entier.
        int coupe = t.lastIndexOf(' ', limite);
        String debut = coupe > 0 ? t.substring(0, coupe) : t.substring(0, limite);
        debut = debut.replaceAll("[\\s,;:.!?\\-]+$", "");
        return debut + POINTS_DE_SUSPENSION;
    }

    private static String questionFiltree(String question) {
        String q = question == null ? "" : question.strip();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La question ne peut pas être vide.");
        }
        List<FiltreDonneesPersonnelles.Nature> natures = FiltreDonneesPersonnelles.detecter(q);
        if (!natures.isEmpty()) {
            throw new DonneePersonnelleDetecteeException(natures);
        }
        return q;
    }

    private Map<String, Object> enregistrer(Proprietaire p, UUID conversationId, String question,
                                            Map<String, Object> reponse) {
        Object sources = reponse.getOrDefault("sources", List.of());
        Object confidence = reponse.get("confidence");
        UUID id = jdbc.queryForObject(
                "INSERT INTO chatbot_echanges (conversation_id, workspace_id, user_id, question, reponse, "
                        + "sources, rag_mode, confidence) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?) RETURNING id",
                UUID.class, conversationId, p.workspaceId(), p.userId(), question,
                String.valueOf(reponse.getOrDefault("reponse", "")), json(sources),
                reponse.get("ragMode") == null ? null : String.valueOf(reponse.get("ragMode")),
                confidence instanceof Number n ? n.doubleValue() : null);
        jdbc.update("UPDATE chatbot_conversations SET updated_at = now() WHERE id = ? AND workspace_id = ? AND user_id = ?",
                conversationId, p.workspaceId(), p.userId());
        return jdbc.queryForObject(
                "SELECT id, question, reponse, sources, rag_mode, confidence, created_at FROM chatbot_echanges WHERE id = ?",
                (rs, i) -> echange(rs), id);
    }

    private Map<String, Object> trouver(Proprietaire p, UUID id) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT c.id, c.titre, c.archivee, c.archived_at, c.created_at, c.updated_at, "
                        + "(SELECT count(*) FROM chatbot_echanges e WHERE e.conversation_id = c.id "
                        + "   AND e.workspace_id = c.workspace_id AND e.user_id = c.user_id) AS nombre_echanges "
                        + "FROM chatbot_conversations c WHERE c.id = ? AND c.workspace_id = ? AND c.user_id = ?",
                (rs, i) -> conversation(rs), id, p.workspaceId(), p.userId());
        if (rows.isEmpty()) throw introuvable();
        return rows.get(0);
    }

    private static Void exigerActive(Map<String, Object> conversation) {
        if (Boolean.TRUE.equals(conversation.get("archivee"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette conversation est archivée : vous pouvez la consulter, mais pour y poser une "
                            + "question, désarchivez-la d'abord.");
        }
        return null;
    }

    private static ResponseStatusException introuvable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation introuvable.");
    }

    private <T> T dansLeCabinet(Proprietaire p, Supplier<T> travail) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        return tx.execute(status -> {
            jdbc.queryForObject("SELECT set_config('app.current_workspace_id', ?, true)",
                    String.class, p.workspaceId().toString());
            return travail.get();
        });
    }

    private static Map<String, Object> conversation(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class));
        m.put("titre", rs.getString("titre"));
        m.put("archivee", rs.getBoolean("archivee"));
        m.put("archiveeLe", rs.getObject("archived_at", OffsetDateTime.class));
        m.put("creeeLe", rs.getObject("created_at", OffsetDateTime.class));
        m.put("derniereActivite", rs.getObject("updated_at", OffsetDateTime.class));
        m.put("nombreEchanges", rs.getLong("nombre_echanges"));
        return m;
    }

    private static Map<String, Object> echange(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class));
        m.put("question", rs.getString("question"));
        m.put("reponse", rs.getString("reponse"));
        m.put("sources", sources(rs.getString("sources")));
        m.put("ragMode", rs.getString("rag_mode"));
        double c = rs.getDouble("confidence");
        m.put("confidence", rs.wasNull() ? null : c);
        m.put("creeLe", rs.getObject("created_at", OffsetDateTime.class));
        return m;
    }

    private static List<Map<String, Object>> sources(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return JSON.readValue(json, LISTE_SOURCES);
        } catch (Exception ex) {
            throw new IllegalStateException("Sources d'un echange illisibles", ex);
        }
    }

    private static String json(Object valeur) {
        try {
            return JSON.writeValueAsString(valeur);
        } catch (Exception ex) {
            throw new IllegalStateException("Sources d'un echange non serialisables", ex);
        }
    }
}
