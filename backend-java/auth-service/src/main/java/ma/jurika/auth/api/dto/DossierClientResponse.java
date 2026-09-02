package ma.jurika.auth.api.dto;

import ma.jurika.auth.application.GetDossierClientUseCase.ClientInfo;

import java.util.UUID;

/**
 * 2026-07-01 — Reponse de GET /api/v1/auth/dossiers/{id}/client.
 *
 * <p>{@code client} est {@code null} si aucun client n'est lie au dossier
 * (le dossier existe mais {@code entreprise_dossiers.client_id IS NULL}).
 * On enveloppe dans un objet pour toujours renvoyer un JSON valide (200 avec
 * {@code {"client": null}}) plutot qu'un corps vide.
 */
public record DossierClientResponse(DossierClientDto client) {

    /** Identite du CLIENT lie : {@code email} = email de contact. */
    public record DossierClientDto(UUID userId, String firstName, String lastName,
                                    String email, String status) {}

    public static DossierClientResponse of(ClientInfo info) {
        if (info == null) {
            return new DossierClientResponse(null);
        }
        return new DossierClientResponse(new DossierClientDto(
                info.userId(), info.firstName(), info.lastName(),
                info.email(), info.status()));
    }
}
