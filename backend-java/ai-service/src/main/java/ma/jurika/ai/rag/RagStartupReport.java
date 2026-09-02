package ma.jurika.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Journalise l'état réel du RAG au démarrage d'ai-service.
 *
 * <p><b>Pourquoi cette classe existe.</b> Le service démarrait « vert » — health UP,
 * aucun WARN — alors que le chatbot tournait en repli complet : les variables
 * {@code RAG_*} n'atteignaient jamais le processus (le filtre des scripts
 * {@code restart-*.ps1} ne les laissait pas passer), le modèle de chat configuré
 * avait été retiré par le fournisseur, et celui d'embeddings aussi — donc pas un
 * seul vecteur en base. Rien dans les journaux ne le disait. Un démarrage réussi ne
 * doit plus pouvoir masquer un RAG inopérant.
 *
 * <p>Le rapport est <b>best-effort</b> : toute erreur de lecture est avalée, ce
 * diagnostic ne doit jamais empêcher le service de démarrer.
 */
@Component
public class RagStartupReport {

    private static final Logger log = LoggerFactory.getLogger(RagStartupReport.class);

    private final RagProperties props;
    private final RagChatClient chatClient;
    private final RagEmbeddingClient embeddingClient;
    private final JdbcTemplate jdbc;

    public RagStartupReport(RagProperties props,
                            RagChatClient chatClient,
                            RagEmbeddingClient embeddingClient,
                            JdbcTemplate jdbc) {
        this.props = props;
        this.chatClient = chatClient;
        this.embeddingClient = embeddingClient;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rapporter() {
        long passages = compter("SELECT count(*) FROM rag_corpus_chunks");
        long vecteurs = compter("SELECT count(*) FROM rag_corpus_chunks WHERE embedding IS NOT NULL");
        long workspaces = compter("SELECT count(DISTINCT workspace_id) FROM rag_corpus_chunks");

        boolean chatPret = chatClient != null && chatClient.isReady();
        boolean embedPret = embeddingClient != null && embeddingClient.isReady();

        log.info("═══ Etat du RAG ═══");
        log.info("  drapeau jurika.rag.enabled : {}", props.enabled());
        log.info("  generation (chat)          : {} — provider={} model={}",
                chatPret ? "PRETE" : "INACTIVE (repli base de connaissances + FTS)",
                props.chat().provider(), props.chat().model());
        log.info("  embeddings (vectoriel)     : {} — provider={} model={} dims={}",
                embedPret ? "PRETS" : "INACTIFS (recherche plein texte seule)",
                props.embed().provider(), props.embed().model(), props.embed().dimensions());
        log.info("  corpus                     : {} passage(s) sur {} workspace(s), {} avec vecteur",
                passages, workspaces, vecteurs);

        // Les trois pannes silencieuses reellement rencontrees, chacune avec son
        // symptome cote utilisateur.
        if (!props.enabled()) {
            log.warn("  ⚠ RAG_ENABLED absent du processus : le chatbot repondra depuis sa base de "
                    + "connaissances interne, jamais depuis les documents du cabinet.");
        }
        if (props.enabled() && !chatPret) {
            log.warn("  ⚠ Generation indisponible (cle absente ou modele retire) : les reponses "
                    + "seront des fiches generiques, non ancrees sur les passages retrouves.");
        }
        if (props.enabled() && embedPret && passages > 0 && vecteurs == 0) {
            log.warn("  ⚠ Embeddings actifs mais AUCUN passage vectorise : le modele d'embedding "
                    + "refuse probablement les appels (modele retire, dimension incompatible). "
                    + "Re-indexer le corpus apres correction.");
        }
        if (props.enabled() && !embedPret && passages > 0) {
            log.warn("  ⚠ Voie vectorielle inactive : recherche plein texte uniquement.");
        }
        if (passages == 0) {
            log.warn("  ⚠ Corpus vide : aucune source fiable n'a ete ingeree. Le chatbot ne pourra "
                    + "citer aucun document.");
        }
        log.info("═══════════════════");
    }

    private long compter(String sql) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class);
            return n == null ? 0L : n;
        } catch (Exception ex) {
            // Table absente (migration non jouee) ou base injoignable : on le dit une
            // fois, sans casser le demarrage.
            log.debug("Rapport RAG : lecture impossible ({})", ex.getMessage());
            return -1L;
        }
    }
}
