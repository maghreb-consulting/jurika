package ma.jurika.ai.api;

import ma.jurika.ai.document.GabaritIntrouvableException;
import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.corpus.CorpusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lot L3 : reponses de la generation lisibles par l'employe.
 * <ul>
 *   <li>donnee interne manquante : 422, {@code code = GENERATION_REFUSEE}, et la liste
 *       {@code donneesManquantes} (variable, libelle, phrase) ;</li>
 *   <li>modele introuvable : 404, {@code MODELE_INTROUVABLE} (rendu 500 jusqu'ici :
 *       le gestionnaire commun ignore {@code @ResponseStatus}) ;</li>
 *   <li>modele du corpus refuse au rendu (modifie depuis le chargement, non rendable :
 *       backlog L2, D8) : 409, {@code MODELE_REFUSE}, au lieu d'une erreur 500.</li>
 * </ul>
 * Passe avant le gestionnaire commun de jurika-common.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GenerationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerationExceptionHandler.class);

    @ExceptionHandler(GenerationRefuseeException.class)
    public ResponseEntity<Map<String, Object>> refusee(GenerationRefuseeException ex) {
        log.warn("Generation refusee {} : {}", ex.templateCode(), ex.getMessage());
        Map<String, Object> body = corps("GENERATION_REFUSEE",
                "Le document ne peut pas être généré : des données manquent. Complétez-les, puis relancez la génération.");
        body.put("templateCode", ex.templateCode());
        body.put("donneesManquantes", ex.donnees());
        return reponse(HttpStatus.UNPROCESSABLE_ENTITY, body);
    }

    @ExceptionHandler(ma.jurika.ai.workflow.identity.IdentiteSocieteIndisponibleException.class)
    public ResponseEntity<Map<String, Object>> identite(ma.jurika.ai.workflow.identity.IdentiteSocieteIndisponibleException ex) {
        log.error("{}", ex.getMessage());
        return reponse(HttpStatus.SERVICE_UNAVAILABLE, corps("DONNEES_SOCIETE_INDISPONIBLES",
                "Les donn\u00e9es de la soci\u00e9t\u00e9 n\u2019ont pas pu \u00eatre lues : le document n\u2019a pas \u00e9t\u00e9 g\u00e9n\u00e9r\u00e9. "
                        + "R\u00e9essayez dans un instant ; si le probl\u00e8me persiste, signalez-le \u00e0 l\u2019administrateur."));
    }

    /** Lot L4d (E10) : clause libre impossible a placer sans risque ; la raison est donnee. */
    @ExceptionHandler(ma.jurika.ai.document.ClauseLibreImpossibleException.class)
    public ResponseEntity<Map<String, Object>> clause(ma.jurika.ai.document.ClauseLibreImpossibleException ex) {
        log.warn("Clause libre refusee {} : {}", ex.templateCode(), ex.getMessage());
        Map<String, Object> body = corps("CLAUSE_LIBRE_IMPOSSIBLE", ex.getMessage());
        body.put("templateCode", ex.templateCode());
        return reponse(HttpStatus.UNPROCESSABLE_ENTITY, body);
    }

    /** Decision du 2026-10-14 : apercu impossible (format ou document illisible), refus nomme. */
    @ExceptionHandler(ma.jurika.common.trial.RenduApercu.ApercuImpossibleException.class)
    public ResponseEntity<Map<String, Object>> apercu(ma.jurika.common.trial.RenduApercu.ApercuImpossibleException ex) {
        log.warn("Apercu impossible : {}", ex.getMessage(), ex.getCause());
        return reponse(HttpStatus.UNPROCESSABLE_ENTITY, corps("APERCU_IMPOSSIBLE", ex.getMessage()));
    }

    @ExceptionHandler(GabaritIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> introuvable(GabaritIntrouvableException ex) {
        log.error("{}", ex.getMessage());
        return reponse(HttpStatus.NOT_FOUND, corps("MODELE_INTROUVABLE",
                "Le mod\u00e8le de ce document est introuvable sur la plateforme. Signalez-le \u00e0 l\u2019administrateur : "
                        + "aucun document de remplacement n\u2019est produit."));
    }

    @ExceptionHandler(CorpusException.class)
    public ResponseEntity<Map<String, Object>> corpus(CorpusException ex) {
        log.error("Modele du corpus refuse : {}", ex.getMessage());
        return reponse(HttpStatus.CONFLICT, corps("MODELE_REFUSE",
                "Le mod\u00e8le de ce document ne peut pas \u00eatre utilis\u00e9 en l\u2019\u00e9tat (il a \u00e9t\u00e9 modifi\u00e9 ou n\u2019est pas "
                        + "rendable). Signalez-le \u00e0 l\u2019administrateur ; le d\u00e9tail figure au journal de la plateforme."));
    }

    private static Map<String, Object> corps(String code, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("code", code);
        b.put("message", message);
        b.put("timestamp", Instant.now().toString());
        return b;
    }

    private static ResponseEntity<Map<String, Object>> reponse(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
