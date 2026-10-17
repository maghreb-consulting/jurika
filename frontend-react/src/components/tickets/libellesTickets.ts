import type { Role } from '../../types/auth';

/** Lot L6b (E7) : titre de la liste des tickets selon le role. */
export function titreTickets(role: Role | null): string {
  return role === 'SUPERVISEUR' || role === 'SUPER_ADMIN' ? 'Tous les tickets' : 'Mes tickets';
}

/** "12 tickets, dont 4 en cours et 2 nouveaux" : totaux en base, jamais le nombre affiche. */
export function resumeTickets(total: number, enCours: number, nouveaux: number): string {
  const t = `${total} ticket${total > 1 ? 's' : ''}`;
  return `${t}, dont ${enCours} en cours et ${nouveaux} ${nouveaux > 1 ? 'nouveaux' : 'nouveau'}`;
}
