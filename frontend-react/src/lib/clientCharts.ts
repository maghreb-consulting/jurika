/**
 * Regles du tableau de bord CLIENT.
 *
 * Lot L6b (E17) : les series des deux anneaux (requetes et demandes par statut) sont retirees
 * avec les anneaux ; reste la definition des requetes que le client doit traiter.
 */
import type { RequeteStatut } from '../types/dataroom';

/** Statuts de requete « actionnables » par le client (a traiter) : ouverte ou a completer. */
export const REQUETE_A_FAIRE: RequeteStatut[] = ['OUVERTE', 'A_COMPLETER'];
