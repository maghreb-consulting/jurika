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
        // Lot L5 E8 : seul le corpus commun (sources du super-admin) est lu par le chatbot.
        long passages = compter("SELECT count(*) FROM rag_corpus_chunks WHERE portee = 'COMMUNE'");
        long vecteurs = compter("SELECT count(*) FROM rag_corpus_chunks WHERE portee = 'COMMUNE' AND embedding IS NOT NULL");
        long sources = compter("SELECT count(DISTINCT doc_id) FROM rag_corpus_chunks WHERE portee = 'COMMUNE'");
        long archives = compter("SELECT count(*) FROM rag_corpus_chunks WHERE portee <> 'COMMUNE'");

        boolean chatPret = chatClient != null && chatClient.isReady();
        boolean embedPret = embeddingClient != null && embeddingClient.isReady();

        log.info("═══ Etat du RAG ═══");
        log.info("  drapeau jurika.rag.enabled : {}", props.enabled());
        log.info("  generation (chat)          : {} — provider={} model={}",
                chatPret ? "PRETE" : "INACTIVE (restitution des passages retrouves)",
                props.chat().provider(), props.chat().model());
        log.info("  embeddings (vectoriel)     : {} — provider={} model={} dims={}",
                embedPret ? "PRETS" : "INACTIFS (recherche plein texte seule)",
                props.embed().provider(), props.embed().model(), props.embed().dimensions());
        log.info("  corpus commun              : {} source(s), {} passage(s), {} avec vecteur ; {} passage(s) archive(s), jamais lus",
                sources, passages, vecteurs, archives);

        // Les trois pannes silencieuses reellement rencontrees, chacune avec son
        // symptome cote utilisateur.
        if (!props.enabled()) {
            log.warn("  ⚠ RAG_ENABLED absent du processus : le chatbot restituera les passages "
                    + "retrouves, sans redaction par le modele.");
        }
        if (props.enabled() && !chatPret) {
            log.warn("  ⚠ Generation indisponible (cle absente ou modele retire) : les reponses "
                    + "seront les passages retrouves, sans redaction.");
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
            log.warn("  ⚠ Corpus commun vide : aucune source fiable n'a ete deposee par le super-admin. "
                    + "Le chatbot repondra qu'il n'a aucune source.");
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
