import type { ReaffectationVue } from '../../../types/dataroom';

/** Lot L1 : libelles et format de date de l'historique des responsables. */
export const LIBELLES_NATURE: Record<ReaffectationVue['nature'], string> = {
  ACCEPTEE: 'Transfert accepté',
  FORCEE: 'Réaffectation par le superviseur',
  RATTRAPAGE: 'Désignation automatique (mise à niveau des données)',
};

export function dateFr(iso: string): string {
  return new Date(iso).toLocaleString('fr-MA', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}
