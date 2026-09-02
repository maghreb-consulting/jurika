package ma.jurika.auth.api.dto;

import java.util.UUID;

/**
 * BUG 7 (2026-06-08) — Reponse de POST /auth/invite-client enrichie : on
 * expose {@code loginEmail} (identifiant @jurika.ma genere) + {@code contactEmail}
 * (email perso fourni). Le front affiche les deux dans le bandeau de succes
 * de l'invitation pour que l'employe puisse les transmettre par WhatsApp.
 */
public record InviteClientResponse(
        UUID userId,
        boolean userCreated,
        String temporaryPassword,
        String loginEmail,
        String contactEmail,
        String message
) {}
