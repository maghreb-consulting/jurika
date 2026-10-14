package ma.jurika.billing.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E5) : contrats d'echange de la facturation (abonnement, changement de forfait, paiement,
 * factures, ecrans du super-admin). Montants en centimes de dirham ; prix HT du catalogue ; TVA au
 * taux des parametres de l'editeur.
 */
public final class BillingDtos {

    private BillingDtos() {}

    /** Montants d'un paiement ou d'une echeance. */
    public record MontantsVue(long htCentimes, BigDecimal tauxTva, long tvaCentimes, long ttcCentimes) {}

    /** Fonction distinctive d'un forfait (CDC 4.5) et sa presence. */
    public record FonctionVue(String code, String libelle, boolean incluse, String disponibleDes) {}

    /** Prochaine echeance (montant absent : {@code motif} dit pourquoi). */
    public record EcheanceVue(LocalDate debut, LocalDate fin, String forfait, String forfaitLibelle, String rythme,
                              MontantsVue montants, String motif) {}

    /** Changement programme a la prochaine echeance (RG-TAR-05, RG-TAR-06). */
    public record ChangementAVenirVue(String forfait, String forfaitLibelle, String rythme, LocalDate effectifLe) {}

    /**
     * Etat de l'abonnement du cabinet. {@code statut} : AUCUN (jamais souscrit), ACTIF, ARCHIVE (forfait
     * arrete, RG-TAR-07 : rien n'est supprime).
     */
    public record AbonnementVue(
            UUID workspaceId,
            String statut,
            String forfait,
            String forfaitLibelle,
            String rythme,
            LocalDate engagementDebut,
            LocalDate engagementFin,
            LocalDate periodeDebut,
            LocalDate periodeFin,
            EcheanceVue prochaineEcheance,
            ChangementAVenirVue changementAVenir,
            String archiveMotif,
            List<FonctionVue> fonctions,
            boolean carteDisponible,
            String carteIndisponibleMotif,
            int paiementsEnAttente
    ) {}

    public record ChangementForfaitRequest(String forfait, String rythme, String mode) {}

    /**
     * Apercu d'un changement de forfait ou de rythme. {@code nature} : HAUSSE, BAISSE, RYTHME, IDENTIQUE.
     * {@code effet} : APRES_PAIEMENT (hausse en annuel, au prorata), IMMEDIAT (hausse en mensuel),
     * PROCHAINE_ECHEANCE (baisse, rythme). {@code depassements} : ce qui depasse le forfait vise
     * (baisse refusee, RG-TAR-05).
     */
    public record ApercuChangementVue(
            String depuis, String vers, String versLibelle, String rythmeActuel, String rythmeVise,
            String nature, String effet, LocalDate effectifLe, Integer moisRestants, MontantsVue montants,
            boolean accepte, List<String> depassements, String message) {}

    /** Resultat d'une demande de changement : applique, programme, ou paiement a regler. */
    public record ChangementForfaitVue(String resultat, String message, ApercuChangementVue apercu,
                                       PreparationPaiementVue paiement) {}

    public record PreparationPaiementRequest(String objet, String forfait, String rythme, String mode,
                                             String contactEmail, Boolean inscription) {}

    /** Formulaire POST auto-soumis vers la page de paiement hebergee du CMI (RG-PAY-04). */
    public record CmiFormulaireVue(String url, Map<String, String> champs) {}

    public record InstructionVue(String libelle, String valeur) {}

    public record PreparationPaiementVue(PaiementVue paiement, CmiFormulaireVue cmi,
                                         List<InstructionVue> instructions, String message) {}

    public record PaiementVue(
            Long id,
            UUID workspaceId,
            String objet,
            String forfait,
            String forfaitLibelle,
            String rythme,
            String mode,
            String statut,
            MontantsVue montants,
            LocalDate periodeDebut,
            LocalDate periodeFin,
            String reference,
            LocalDate recuLe,
            Instant creeLe,
            Instant acquisLe,
            Long factureId,
            String factureNumero,
            String factureRefus,
            boolean activationPropagee,
            String notes
    ) {}

    public record FactureVue(Long id, String numero, UUID workspaceId, String cabinet, LocalDate dateEmission,
                             String designation, LocalDate periodeDebut, LocalDate periodeFin, String mode,
                             MontantsVue montants) {}

    public record ContactSalesRequest(String workspaceName, String contactEmail, String contactName,
                                      String phone, String message) {}

    public record ContactSalesResponse(boolean accepted, String message) {}

    // ------------------------------------------------------------------ super-admin

    /** Enregistrement d'un paiement recu (virement, cheque, especes) par le super-admin (RG-PAY-02). */
    public record EnregistrementPaiementRequest(UUID workspaceId, String objet, String forfait, String rythme,
                                                String mode, LocalDate recuLe, String reference,
                                                Long montantHtCentimes, String notes) {}

    /** Validation d'un paiement declare par le cabinet : reception constatee. */
    public record ValidationPaiementRequest(LocalDate recuLe, String reference, String notes) {}

    public record RejetPaiementRequest(String motif) {}

    /** Estimation Sur Devis (RG-PLN-05). */
    public record SurDevisVue(long dossiersDuMois, LocalDate moisDebut, LocalDate moisFin,
                              BigDecimal prixParDossierHt, BigDecimal montantHt, String motif,
                              BigDecimal repereMaxParDossierMensuel, BigDecimal repereMaxParDossierAnnuel) {}

    public record AbonnementAdminVue(
            UUID workspaceId, Long abonnementId, String statut, String forfait, String forfaitLibelle, String rythme,
            LocalDate engagementDebut, LocalDate engagementFin, LocalDate periodeDebut, LocalDate periodeFin,
            EcheanceVue prochaineEcheance, ChangementAVenirVue changementAVenir, String archiveMotif,
            SurDevisVue surDevis) {}

    public record BilanTacheVue(int traites, int reussis, int echecs, List<String> details) {}
}
