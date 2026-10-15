package ma.jurika.billing.application.contrat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L7 (socle commun) : point de rencontre entre l'abonnement (forfaits, paiements) et le
 * contrat d'abonnement (paragraphe 4.8 du CDC).
 *
 * <p>Implemente par la partie "contrat" ; appele par la partie "abonnement" chaque fois que de
 * nouvelles conditions particulieres naissent (activation par le super-admin RG-CTR-03,
 * souscription en ligne, changement de forfait ou de rythme RG-CTR-05). L'appelant l'obtient par
 * {@code ObjectProvider<ContratAbonnementPort>} : tant qu'aucune implementation n'existe, rien
 * n'est appele (aucun contrat invente).
 */
public interface ContratAbonnementPort {

    /** Conditions particulieres d'un abonnement (RG-CTR-03, RG-CTR-05). Prix hors taxes. */
    record ConditionsParticulieres(
            UUID workspaceId,
            String forfait,            // ESSENTIEL, PROFESSIONNEL, MAX, SUR_DEVIS
            String rythmePaiement,     // MENSUEL, ANNUEL
            BigDecimal prixHt,         // montant de l'echeance (mensualite ou annuite), HT
            LocalDate dateDebut,
            LocalDate dateFin,
            UUID creePar) {}

    /**
     * Enregistre des conditions particulieres EN ATTENTE d'acceptation par le superviseur du cabinet.
     * Tant qu'elles ne sont pas acceptees, seul l'ecran du contrat est accessible au cabinet.
     *
     * @return identifiant des conditions particulieres creees.
     */
    UUID ouvrirConditionsParticulieres(ConditionsParticulieres conditions);

    /** Vrai si le cabinet a un contrat en attente d'acceptation (porte d'entree de RG-CTR-03). */
    boolean acceptationEnAttente(UUID workspaceId);
}
