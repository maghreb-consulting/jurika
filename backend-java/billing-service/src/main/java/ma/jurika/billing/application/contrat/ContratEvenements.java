package ma.jurika.billing.application.contrat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L7 (E6) : evenements d'application publies par le contrat, apres validation de la
 * transaction, a l'intention de la partie "abonnement" (activation du forfait apres
 * acceptation, RG-CTR-01 ; arret du forfait a la date d'effet d'une resiliation, RG-CTR-08).
 * Ils ne suppriment rien : la resiliation archive (RG-TAR-07).
 */
public final class ContratEvenements {

    private ContratEvenements() {}

    /** Conditions particulieres (ou nouvelle version des conditions generales) acceptees. */
    public record ContratAccepte(UUID workspaceId, UUID conditionsId, UUID acceptationId, String objet,
                                 String forfait, String rythmePaiement, BigDecimal prixHt,
                                 LocalDate dateDebut, LocalDate dateFin) {}

    /** Resiliation enregistree : le forfait s'arrete a {@code dateEffet}, le workspace est archive. */
    public record ResiliationEnregistree(UUID workspaceId, UUID conditionsId, LocalDate dateEffet,
                                         String motif, String demandeurRole) {}
}
