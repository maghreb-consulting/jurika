package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fiche client (2026-07-14) — edition des identifiants de la societe
 * (immatriculation post-creation : RC, IF, patente, CNSS...).
 */
public final class DossierIdentifiantsDtos {

    private DossierIdentifiantsDtos() {}

    /**
     * Requete d'edition. Champs optionnels (formulaire de completion) ; un champ
     * vide efface la valeur correspondante. Tailles alignees sur les colonnes
     * de entreprise_dossiers.
     */
    public record UpdateIdentifiantsRequest(
            @Size(max = 20) String ice,
            @Size(max = 50) String rcNumero,
            @Size(max = 100) String rcTribunal,
            @Size(max = 50) String identifiantFiscal,
            @Size(max = 50) String taxeProfessionnelle,
            @Size(max = 50) String cnss,
            @Size(max = 4000) String adresseSiege,
            @Size(max = 100) String ville,
            BigDecimal capitalSocialMad,
            LocalDate dateConstitution,
            // Lot L1 (RG-FIC-02) : date de prise d'effet de la taxe professionnelle ;
            // absente, elle reste vide (jamais inventee) et peut etre completee plus tard.
            LocalDate taxeProfessionnelleDateEffet) {}

    /** Etat des identifiants apres mise a jour (renvoye au front). */
    public record DossierIdentifiantsView(
            UUID dossierId,
            String raisonSociale,
            String formeJuridique,
            String ice,
            String rcNumero,
            String rcTribunal,
            String identifiantFiscal,
            String taxeProfessionnelle,
            String cnss,
            String adresseSiege,
            String ville,
            BigDecimal capitalSocialMad,
            LocalDate dateConstitution,
            String statut) {}
}
