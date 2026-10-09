package ma.jurika.auth.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0 : cause de l'echec intermittent d'OnboardingFlowE2ETest (verify-2fa en
 * 400). Le code TOTP est une chaine de 6 chiffres : un code commencant par 0
 * transmis sans son zero initial (5 chiffres) est refuse en validation, alors
 * que le meme code sur 6 chiffres est accepte. Le serveur est correct ; c'est le
 * test qui envoyait le code en nombre JSON.
 */
class Verify2faRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void un_code_a_zero_initial_sur_six_chiffres_est_accepte() {
        assertThat(validator.validate(requete("012345"))).isEmpty();
    }

    @Test
    void le_meme_code_ampute_de_son_zero_initial_est_refuse() {
        assertThat(validator.validate(requete("12345"))).isNotEmpty();
    }

    private static Verify2faRequest requete(String code) {
        return new Verify2faRequest(UUID.randomUUID(), UUID.randomUUID(), code);
    }
}
