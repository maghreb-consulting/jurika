package ma.jurika.ticket.domain.model;

/**
 * Unite d'un delai legal.
 *
 * <p>Elle n'est JAMAIS normalisee : un delai exprime en mois se calcule en mois
 * calendaires. Convertir « 3 mois » en 90 jours produit une echeance fausse des
 * qu'un mois de 31 jours est traverse, et l'erreur est toujours defavorable —
 * l'alerte se leve APRES l'expiration du delai.
 */
public enum DelaiUnite {
    JOURS,
    MOIS
}
