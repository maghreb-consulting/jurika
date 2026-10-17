package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Envoi d'un code SMS par un utilisateur connecte.
 *
 * <p>Recette R2 (2026-10-17) : {@code 2FA_SETUP} (activation, sur le numero saisi) est admis ;
 * avant, la validation le refusait alors que l'ecran de choix l'envoyait (activation par SMS
 * impossible). {@code telephone} : numero a verifier (E.164), facultatif ; {@code codeActuel} :
 * code de la methode active, exige pour changer de methode.
 */
public record SmsOtpSendRequest(
        @NotBlank @Pattern(regexp = "PHONE_VERIFICATION|2FA_SETUP|2FA_LOGIN") String purpose,
        @Size(max = 25) String telephone,
        @Size(max = 12) String codeActuel
) {}
