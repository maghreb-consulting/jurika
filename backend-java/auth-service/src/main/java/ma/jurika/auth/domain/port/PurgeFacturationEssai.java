package ma.jurika.auth.domain.port;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E14), RG-ESS-10 : donnees de facturation d'un essai supprime definitivement, confiees a
 * billing-service (base jurika_billing, hors de la fonction essai_supprimer_definitivement).
 * Decision du 2026-10-14 : paiements non aboutis supprimes ; paiements aboutis et factures
 * conserves 10 ans (obligation comptable) ; preuves du contrat conservees, duree A_DECIDER.
 *
 * <p>Appele AVANT les objets MinIO et les lignes de jurika_db. Leve une exception si billing refuse
 * ou ne repond pas : rien d'autre n'est alors supprime (nouvel essai au passage suivant).
 */
public interface PurgeFacturationEssai {

    Bilan traiter(UUID workspaceId);

    /** Ce que billing a supprime et conserve (lignes par table), echeance de la conservation. */
    record Bilan(UUID workspaceId, Map<String, Long> supprimees, Map<String, Long> conservees10Ans,
                 Map<String, Long> conserveesDureeADecider, String echeanceConservation,
                 String dureePreuvesContrat) {}
}
