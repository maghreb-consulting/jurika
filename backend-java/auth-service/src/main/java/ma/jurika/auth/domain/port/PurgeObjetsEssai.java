package ma.jurika.auth.domain.port;

import java.util.UUID;

/**
 * Lot L7 (E4), RG-ESS-10 : suppression des objets MinIO d'un essai expire
 * (documents, depots, apercus), confiee a dataroom-service qui detient le
 * stockage. Leve une exception si la suppression n'a pas abouti : les lignes
 * de la base ne sont alors PAS supprimees (nouvel essai au passage suivant).
 *
 * @return nombre d'objets supprimes
 */
public interface PurgeObjetsEssai {

    int purger(UUID workspaceId);
}
