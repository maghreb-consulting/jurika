package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * CRIT-3 (audit 2026-06-02) : {@code code} en String (etait int 0-999999) pour
 * supporter TOTP (6 chiffres) ET SMS OTP (6 chiffres mais idiomatique en String,
 * permet leading zeros et tolere espacement utilisateur). Le pattern est strict
 * cote serveur ; le frontend peut continuer a envoyer un int formatte mais
 * doit utiliser un champ string serialise tel quel.
 */
public record Verify2faRequest(
        @NotNull UUID userId,
        @NotNull UUID workspaceId,
        @NotBlank @Size(min = 6, max = 8) @Pattern(regexp = "^[0-9 ]{6,8}$", message = "Code 2FA invalide") String code
) {}
