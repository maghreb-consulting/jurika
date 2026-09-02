package ma.jurika.auth.api.dto;

import java.util.UUID;

/**
 * HIGH-4 (audit 2026-06-02) : ajout {@code emailDelivered} pour que le frontend
 * affiche un encart "Email non envoye" si l'envoi a echoue (sans casser la
 * creation du workspace).
 *
 * <p>BUG 7 (2026-06-08) : ajout {@code loginEmail} (identifiant @jurika.ma
 * genere) + {@code contactEmail} (email perso fourni) pour que l'ecran de
 * succes affiche clairement "Voici votre identifiant de connexion".
 */
public record RegisterResponse(UUID workspaceId, String workspaceCode, UUID userId, boolean emailDelivered,
                                String loginEmail, String contactEmail) {}
