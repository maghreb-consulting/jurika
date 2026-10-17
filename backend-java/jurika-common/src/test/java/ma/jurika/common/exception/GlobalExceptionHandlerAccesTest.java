package ma.jurika.common.exception;

import ma.jurika.common.dto.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E13) : un refus de ROLE a un utilisateur bien authentifie portait le code FORBIDDEN,
 * que le front ne distingue pas d'un jeton expire : il rafraichissait la session puis rejouait la
 * requete (deux 403 et une rotation de jeton inutile a chaque ecran refuse). Le refus d'un
 * utilisateur authentifie porte PERMISSION_DENIED (le front ne rafraichit pas) ; une requete
 * anonyme (jeton expire ou illisible) garde FORBIDDEN (le front rafraichit).
 */
class GlobalExceptionHandlerAccesTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Utilisateur authentifie sans le role : PERMISSION_DENIED, message francais")
    void roleRefuse() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u1", null, AuthorityUtils.createAuthorityList("ROLE_CLIENT")));
        ResponseEntity<ErrorResponse> r = handler.handleAccessDenied(new AccessDeniedException("Access Denied"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo("PERMISSION_DENIED");
        assertThat(r.getBody().message()).contains("droits");
    }

    @Test
    @DisplayName("Requete anonyme (jeton expire) : FORBIDDEN, le front rafraichit la session")
    void anonyme() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "cle", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().code()).isEqualTo("FORBIDDEN");
        SecurityContextHolder.clearContext();
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().code()).isEqualTo("FORBIDDEN");
    }
}
