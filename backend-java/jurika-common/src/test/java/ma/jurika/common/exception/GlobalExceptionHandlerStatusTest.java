package ma.jurika.common.exception;

import ma.jurika.common.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-09-08 — UNE EXCEPTION QUI PORTE SON STATUT LE GARDE.
 *
 * <p>Le {@code @ExceptionHandler(Exception.class)} de
 * {@link GlobalExceptionHandler} prime sur la gestion native de Spring MVC : tout
 * ce que Spring aurait traduit en 4xx sortait en 500 « Erreur interne du serveur ».
 * Mesure sur la pile qui tourne, avant correction :
 *
 * <pre>
 *   attendu 404 -> recu 500   DELETE /api/v1/chatbot/sources/{inconnu}
 *   attendu 405 -> recu 500   mauvaise methode HTTP
 *   attendu 415 -> recu 500   mauvais Content-Type
 * </pre>
 *
 * <p>Le defaut touchait 12 {@code ResponseStatusException} de 5 controleurs dans
 * 3 services, ET toutes les exceptions de Spring MVC — qui n'ont pourtant aucun
 * {@code throw} dans le projet.
 */
class GlobalExceptionHandlerStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =================================================================
    //  Les ResponseStatusException levees par les controleurs du projet
    // =================================================================

    @Test
    @DisplayName("Un 404 leve par un controleur arrive en 404, avec son motif")
    void responseStatusException404() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Source introuvable."));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().message()).isEqualTo("Source introuvable.");
        assertThat(r.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("Un 422 garde le message qui NOMME la variable et cite la ligne")
    void responseStatusException422PreserveLeMotif() {
        // Le refus de generation du lot 5 : c'est ce message-la que l'employe doit
        // lire, pas « Erreur interne du serveur ».
        String motif = "Génération refusée : le document sortirait avec un blanc au milieu "
                + "d'une phrase. $GERANT_LIEU_NAISSANCE — « Date et lieu de naissance : "
                + "20/01/1980 à »";
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
                new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, motif));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().message()).isEqualTo(motif);
        assertThat(r.getBody().message()).contains("$GERANT_LIEU_NAISSANCE");
    }

    @Test
    @DisplayName("401, 400 et 503 traversent aussi")
    void lesAutresStatutsDesControleurs() {
        assertThat(handler.handleGeneric(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Workspace introuvable."))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleGeneric(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fournir un fichier ou du texte."))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleGeneric(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR indisponible."))
                .getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Sans motif, on rend la phrase du statut — jamais un message technique")
    void sansMotifOnRendLaPhraseDuStatut() {
        ResponseEntity<ErrorResponse> r =
                handler.handleGeneric(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThat(r.getBody().message()).isEqualTo("Not Found");
    }

    // =================================================================
    //  Les exceptions de Spring MVC — aucun `throw` dans le projet
    // =================================================================

    @Test
    @DisplayName("Mauvaise méthode HTTP : 405, pas 500")
    void methodeNonSupportee() {
        ResponseEntity<ErrorResponse> r =
                handler.handleGeneric(new HttpRequestMethodNotSupportedException("PATCH"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Mauvais Content-Type : 415, pas 500")
    void typeDeContenuNonSupporte() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
                new HttpMediaTypeNotSupportedException("text/plain non supporté"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    @DisplayName("Paramètre de requête manquant : 400, pas 500")
    void parametreManquant() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
                new MissingServletRequestParameterException("dossierId", "UUID"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // =================================================================
    //  Ce qui doit RESTER un 500
    // =================================================================

    @Test
    @DisplayName("Une vraie panne reste un 500, et ne fuit rien au client")
    void unePanneResteUn500() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
                new IllegalStateException("connexion JDBC fermée : jdbc:postgresql://.../jurika_db"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(r.getBody().message()).isEqualTo("Erreur interne du serveur");
        assertThat(r.getBody().message())
                .as("le detail technique ne doit jamais partir au client")
                .doesNotContain("jdbc");
    }

    @Test
    @DisplayName("Un 5xx PORTÉ par l'exception reste un 5xx, message compris")
    void un5xxPorteResteUn5xx() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Collabora injoignable."));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody().message()).isEqualTo("Collabora injoignable.");
    }
}
