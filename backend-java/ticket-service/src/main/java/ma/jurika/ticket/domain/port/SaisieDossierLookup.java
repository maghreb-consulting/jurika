package ma.jurika.ticket.domain.port;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot B — LIRE UNE DATE SAISIE AU PARCOURS, POUR FAIRE COURIR UN DÉLAI LÉGAL.
 *
 * <p>Huit des délais du parcours partent du cochage d'une autre ligne : la date
 * est alors dans `ticket_demarches`, chez nous. Deux n'en partent pas — la taxe
 * professionnelle et l'affiliation CNSS courent depuis le <b>début d'activité</b>,
 * qui est une date <i>déclarée</i> et non une étape qu'on coche. Elle vit dans la
 * saisie du workflow.
 *
 * <p>Ce port existe pour que le domaine n'ait pas à savoir cela. Il pose la
 * question — « quelle est la valeur de cette donnée pour ce ticket ? » — et
 * accepte qu'elle n'ait pas de réponse.
 *
 * <p><b>Un {@link Optional} vide n'est pas une erreur.</b> C'est l'état normal
 * d'un dossier où la date n'est pas encore renseignée, et la règle acquise au
 * lot 1 s'y applique sans changement : aucune échéance n'est alors calculée, et
 * <b>aucune date n'est fabriquée</b>. Une alerte fausse coûte plus cher qu'une
 * alerte absente : la première se croit, la seconde se voit.
 */
public interface SaisieDossierLookup {

    /**
     * La valeur d'une donnée <b>de type date</b> saisie au parcours de ce ticket.
     *
     * @param nomDonnee nom de la variable du corpus, sans le {@code $}
     *                  (ex. {@code DATE_DEBUT_ACTIVITE})
     * @return la date, ou vide si elle n'est pas saisie, illisible, ou si le
     *         ticket n'a pas de workflow
     */
    Optional<LocalDate> dateSaisie(UUID workspaceId, UUID ticketId, String nomDonnee);
}
