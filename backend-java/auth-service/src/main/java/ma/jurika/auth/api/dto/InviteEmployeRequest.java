package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HIGH-13 (audit 2026-06-02) : payload invitation collaborateur interne.
 * Role doit etre EMPLOYE ou SUPERVISEUR.
 */
public record InviteEmployeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        // Telephone international optionnel (2026-07-28) : le front stocke le
        // numero en E.164 via le selecteur d'indicatif pays (Maroc par defaut).
        // null tolere (champ optionnel) ; sinon E.164 valide (7 a 15 chiffres).
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "T\u00e9l\u00e9phone : format international attendu (par exemple +212612345678).") String phone,
        @NotNull @Pattern(regexp = "^(EMPLOYE|SUPERVISEUR)$", message = "R\u00f4le inconnu : choisissez employ\u00e9 ou superviseur.") String role
) {}
