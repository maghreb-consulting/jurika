package ma.jurika.billing.application.cabinet;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L7 (E5) : ce que la facturation demande a auth-service, proprietaire de la table
 * {@code workspaces} (statut, forfait, identite du cabinet, usage du forfait).
 *
 * <p>Toute methode leve {@link CabinetsIndisponiblesException} quand auth-service ne repond pas :
 * l'appelant le sait et rejoue (activation propagee plus tard, facture emise plus tard). Avant ce
 * lot, un echec etait journalise puis oublie ("sera reconcilie", sans reconciliation).
 */
public interface CabinetsPort {

    /** Workspace actif (ou reactive dans l'etat exact, RG-TAR-08) au forfait donne. */
    void activer(UUID workspaceId, String forfait, LocalDate abonnementDebut, String rythme, LocalDate payeJusquAu);

    /** Workspace archive (RG-TAR-07) ; motifs : IMPAYE, FIN_ABONNEMENT, RESILIATION, DECISION. */
    void archiver(UUID workspaceId, String motif);

    /** Identifiants d'acces emis (inscription en ligne payee par virement, cheque ou especes). */
    void emettreIdentifiants(UUID workspaceId, String raison);

    /** Identite du cabinet pour la facture (valeurs telles qu'en base, vides comprises). */
    IdentiteCabinet identite(UUID workspaceId);

    /** Usage du forfait (dossiers du mois d'abonnement, acces employes, stockage). */
    UsageCabinet usage(UUID workspaceId);

    /** Super-admins actifs (canal du super-admin : paiement hors ligne a enregistrer). */
    java.util.List<SuperAdmin> superAdmins();

    record SuperAdmin(UUID id, UUID workspaceId) {}

    record IdentiteCabinet(UUID workspaceId, String denomination, String ice, String identifiantFiscal, String rc,
                           String adresse, String ville, String email, String statut, String forfait) {}

    record UsageCabinet(String planCode, int users, int maxUsers, boolean usersUnlimited,
                        int dossiers, int maxDossiers, boolean dossiersUnlimited,
                        long storageBytes, int maxStorageGb, boolean storageUnlimited,
                        LocalDate periodeDebut, LocalDate periodeFin, boolean storageMesure) {}

    class CabinetsIndisponiblesException extends RuntimeException {
        public CabinetsIndisponiblesException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
