package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sprint 14 bis / B4 — payload de {@code POST /api/v1/auth/verify-recovery-code}.
 * Format code attendu : 4 groupes de 4 caracteres alphanumeriques separes par
 * un tiret (ex. {@code A7K2-9XQM-3FBL-7TPN}). La validation est volontairement
 * permissive (trim + upper cote serveur) car les UI peuvent envoyer avec/sans
 * tirets.
 */
public record VerifyRecoveryCodeRequest(
        @NotBlank
        @Pattern(regexp = "^JUR-[A-Z0-9]{5}$", message = "Format attendu : JUR-XXXXX")
        String workspaceCode,
        @NotBlank @Email String email,
        @NotBlank
        @Size(min = 16, max = 23,
                message = "Code de recuperation attendu : 16 caracteres alphanumeriques (4 groupes de 4)")
        String code
) {}
