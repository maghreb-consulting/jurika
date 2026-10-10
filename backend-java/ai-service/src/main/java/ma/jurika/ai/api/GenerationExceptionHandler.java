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

    @ExceptionHandler(GabaritIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> introuvable(GabaritIntrouvableException ex) {
        log.error("{}", ex.getMessage());
        return reponse(HttpStatus.NOT_FOUND, corps("MODELE_INTROUVABLE",
                "Le modèle de ce document est introuvable sur la plateforme. Signalez-le à l'administrateur : "
                        + "aucun document de remplacement n'est produit."));
    }

    @ExceptionHandler(CorpusException.class)
    public ResponseEntity<Map<String, Object>> corpus(CorpusException ex) {
        log.error("Modele du corpus refuse : {}", ex.getMessage());
        return reponse(HttpStatus.CONFLICT, corps("MODELE_REFUSE",
                "Le modèle de ce document ne peut pas être utilisé en l'état (il a été modifié ou n'est pas "
                        + "rendable). Signalez-le à l'administrateur ; le détail figure au journal de la plateforme."));
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
