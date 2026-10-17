package ma.jurika.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.jurika.common.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E13) : une erreur 400 dit en francais QUEL champ corriger.
 *
 * <p>Mesure sur la pile du Z440 (2026-10-16), POST /auth/login avec un corps vide :
 * {@code {"message":"Donn\u00e9es invalides","errors":[{"field":"password","message":"must not be blank"}...]}}.
 * Le message ne nomme aucun champ, et le detail est en anglais (JVM non francaise, messages par
 * defaut de Bean Validation). Le front n'affichait que "Donn\u00e9es invalides".
 */
class GlobalExceptionHandlerMessagesTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    record Demande(@NotBlank String workspaceCode,
                   @NotBlank @Email String email,
                   @NotNull @Size(max = 5) String reference,
                   @NotBlank(message = "Indiquez la d\u00e9nomination de la soci\u00e9t\u00e9.") String denomination) {}

    record Corps(UUID dossierId, LocalDate dateEffet, Integer nombre) {}

    @SuppressWarnings("unused")
    void cible(Demande d) { }

    private MethodArgumentNotValidException invalide(Demande d) throws Exception {
        Locale avant = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH); // comme la JVM du conteneur
        try {
            Validator v = Validation.buildDefaultValidatorFactory().getValidator();
            BeanPropertyBindingResult br = new BeanPropertyBindingResult(d, "demande");
            new SpringValidatorAdapter(v).validate(d, br);
            MethodParameter p = new MethodParameter(getClass().getDeclaredMethod("cible", Demande.class), 0);
            return new MethodArgumentNotValidException(p, br);
        } finally {
            Locale.setDefault(avant);
        }
    }

    @Test
    @DisplayName("Champ obligatoire vide : le message nomme le champ, en francais, quelle que soit la langue de la JVM")
    void champObligatoireNomme() throws Exception {
        ResponseEntity<ErrorResponse> r = handler.handleMethodArgumentNotValid(
                invalide(new Demande("", "a@b.ma", "x", "SARL Demo")));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse b = r.getBody();
        assertThat(b).isNotNull();
        assertThat(b.message()).contains("code du cabinet").contains("obligatoire");
        assertThat(b.errors()).singleElement().satisfies(fe -> {
            assertThat(fe.field()).isEqualTo("workspaceCode");
            assertThat(fe.message()).isEqualTo("Ce champ est obligatoire.");
        });
        assertThat(b.message()).doesNotContain("must not");
    }

    @Test
    @DisplayName("Plusieurs champs : chacun est nomme ; adresse et longueur en francais")
    void plusieursChamps() throws Exception {
        ErrorResponse b = handler.handleMethodArgumentNotValid(
                invalide(new Demande("JUR-X", "pas-une-adresse", "trop-long", "SARL Demo"))).getBody();
        assertThat(b).isNotNull();
        assertThat(b.message()).contains("adresse \u00e9lectronique").contains("reference");
        assertThat(b.errors()).extracting(ErrorResponse.FieldError::message)
                .contains("Adresse \u00e9lectronique invalide.", "5 caract\u00e8res au plus.");
    }

    @Test
    @DisplayName("Un message propre au DTO est garde tel quel")
    void messageDuDtoGarde() throws Exception {
        ErrorResponse b = handler.handleMethodArgumentNotValid(
                invalide(new Demande("JUR-X", "a@b.ma", "x", " "))).getBody();
        assertThat(b).isNotNull();
        assertThat(b.errors()).singleElement().satisfies(fe ->
                assertThat(fe.message()).isEqualTo("Indiquez la d\u00e9nomination de la soci\u00e9t\u00e9."));
        assertThat(b.message()).containsIgnoringCase("Indiquez la d\u00e9nomination de la soci\u00e9t\u00e9.");
    }

    private HttpMessageNotReadableException illisible(String json) {
        HttpInputMessage in = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(json.getBytes()); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        try {
            new ObjectMapper().findAndRegisterModules().readValue(json, Corps.class);
            throw new AssertionError("lecture attendue en echec : " + json);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return new HttpMessageNotReadableException("JSON parse error: " + e.getOriginalMessage(), e, in);
        }
    }

    @Test
    @DisplayName("Date au mauvais format : le champ est nomme et le format attendu dit en francais")
    void dateMalFormee() {
        ErrorResponse b = handler.handleHttpMessageNotReadable(illisible("{\"dateEffet\":\"16/10/2026\"}")).getBody();
        assertThat(b).isNotNull();
        assertThat(b.message()).contains("dateEffet").contains("AAAA-MM-JJ").doesNotContain("LocalDate");
        assertThat(b.errors()).singleElement().satisfies(fe -> assertThat(fe.field()).isEqualTo("dateEffet"));
    }

    @Test
    @DisplayName("Identifiant vide : le champ est nomme, aucun message technique de Jackson")
    void uuidVide() {
        ErrorResponse b = handler.handleHttpMessageNotReadable(illisible("{\"dossierId\":\"pas-un-uuid\"}")).getBody();
        assertThat(b).isNotNull();
        assertThat(b.message()).contains("dossier").contains("UUID").doesNotContain("Cannot deserialize");
        assertThat(b.errors()).singleElement().satisfies(fe -> assertThat(fe.field()).isEqualTo("dossierId"));
    }

    @Test
    @DisplayName("Mauvais type (tableau au lieu d'un nombre) : le champ est nomme")
    void mauvaisType() {
        ErrorResponse b = handler.handleHttpMessageNotReadable(illisible("{\"nombre\":[1,2]}")).getBody();
        assertThat(b).isNotNull();
        assertThat(b.message()).contains("nombre").doesNotContain("Cannot deserialize");
    }

    @Test
    @DisplayName("JSON mal forme : message francais, sans le texte de l'analyseur")
    void jsonMalForme() {
        ErrorResponse b = handler.handleHttpMessageNotReadable(illisible("{\"nombre\": ")).getBody();
        assertThat(b).isNotNull();
        assertThat(b.message()).startsWith("La requ\u00eate est illisible").doesNotContain("Unexpected end-of-input");
    }

    @Test
    @DisplayName("Parametre de requete manquant : nomme, en francais")
    void parametreManquant() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(new MissingServletRequestParameterException("dossierId", "UUID"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().message()).contains("dossierId").contains("manquant").doesNotContain("Required");
    }

    @Test
    @DisplayName("Violation de contrainte sur un parametre : le parametre est nomme, en francais")
    void violationDeContrainte() {
        Locale avant = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            Validator v = Validation.buildDefaultValidatorFactory().getValidator();
            var violations = v.validate(new Demande("", "a@b.ma", "x", "SARL"));
            ErrorResponse b = handler.handleConstraintViolation(new ConstraintViolationException(violations)).getBody();
            assertThat(b).isNotNull();
            assertThat(b.message()).contains("code du cabinet").contains("obligatoire").doesNotContain("must not");
        } finally {
            Locale.setDefault(avant);
        }
    }
}
