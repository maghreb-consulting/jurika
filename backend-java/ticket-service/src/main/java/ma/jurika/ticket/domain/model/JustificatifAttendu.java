package ma.jurika.ticket.domain.model;

/**
 * Un justificatif attendu au cochage d'une demarche.
 *
 * <p>Le {@code documentType} vient du REFERENTIEL : il n'est jamais saisi par
 * l'employe. Deux justificatifs partageant le meme {@code alternativeGroupe}
 * sont des ALTERNATIVES -- l'un d'eux suffit (le guide ecrit « bail commercial,
 * contrat de domiciliation ou local en propriete »). Deux groupes distincts sont
 * CUMULATIFS (etape 7 : le contrat enregistre ET l'attestation d'enregistrement).
 */
public record JustificatifAttendu(int alternativeGroupe, String documentType, String libelle) {
}
