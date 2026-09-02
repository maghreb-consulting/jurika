package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BUG 7 (2026-06-08, chore finitions) — Body de PATCH /api/v1/auth/me/contact-email.
 *
 * <p>L'utilisateur connecte modifie son email de notifications (contact_email).
 * Le login_email reste immuable cote API : pour le changer, il faut passer par
 * un flux administrateur (non expose V1).
 */
public record UpdateContactEmailRequest(
        @NotBlank @Email @Size(max = 150)
        String contactEmail
) {}
