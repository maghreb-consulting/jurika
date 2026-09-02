package ma.jurika.ticket.api.dto;

import ma.jurika.ticket.infrastructure.persistence.SuccursaleEntity;

import java.util.UUID;

/**
 * Vue succursale exposee au front (auto-remplissage a la fermeture).
 * L'{@code id} est le VRAI UUID DB, qui remplace l'ancien id logique
 * "RC{rc}@{ville}" saisi a la main.
 */
public record SuccursaleDto(
        UUID id,
        String type,
        String denomination,
        String activite,
        String adresse,
        String ville,
        String rcSecondaire,
        String directeurNom,
        String directeurPrenom,
        String directeurCin,
        String paysOrigine,
        String statut
) {
    public static SuccursaleDto from(SuccursaleEntity e) {
        return new SuccursaleDto(
                e.getId(),
                e.getType(),
                e.getDenomination(),
                e.getActivite(),
                e.getAdresse(),
                e.getVille(),
                e.getRcSecondaire(),
                e.getDirecteurNom(),
                e.getDirecteurPrenom(),
                e.getDirecteurCin(),
                e.getPaysOrigine(),
                e.getStatut());
    }
}
