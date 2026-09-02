package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EntrepriseDossier(
        UUID id,
        UUID workspaceId,
        String raisonSociale,
        FormeJuridique formeJuridique,
        String ice,
        String rcNumero,
        String rcTribunal,
        String identifiantFiscal,
        String taxeProfessionnelle,
        String cnss,
        String adresseSiege,
        String ville,
        Double capitalSocialMad,
        LocalDate dateConstitution,
        DossierStatut statut,
        UUID clientId,
        /** Owner durable du dossier (employe qui "gere la societe"). Cf. V9. */
        UUID responsableId,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Factory : dossier "en construction" pour un workflow CREATION qui demarre.
     * {@code responsableId} = employe createur : le dossier appartient a son
     * employe des sa naissance (scoping "un employe ne voit que ses dossiers").
     */
    public static EntrepriseDossier creationStub(UUID workspaceId, String raisonSociale,
                                                 FormeJuridique forme, UUID responsableId) {
        Instant now = Instant.now();
        return new EntrepriseDossier(
                null, workspaceId, raisonSociale, forme,
                null, null, null, null, null, null, null, null, null, null,
                DossierStatut.EN_CONSTITUTION, null, responsableId, now, now);
    }

    /** Factory : dossier importe (deja immatricule). {@code responsableId} = employe createur. */
    public static EntrepriseDossier importStub(UUID workspaceId, String raisonSociale,
                                               FormeJuridique forme, UUID responsableId) {
        Instant now = Instant.now();
        return new EntrepriseDossier(
                null, workspaceId, raisonSociale, forme,
                null, null, null, null, null, null, null, null, null, null,
                DossierStatut.ACTIVE, null, responsableId, now, now);
    }
}
