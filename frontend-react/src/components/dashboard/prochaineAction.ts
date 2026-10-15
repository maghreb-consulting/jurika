import type { Ticket } from '../../types/ticket';
import type { EcheanceItem } from '../../types/dashboard';
import { aideStatutTicket, type AideStatut, type ProfilTicket } from '../../pages/tickets/textesAideStatut';

/**
 * Lot L6b (E9, point 8) : "Prochaine action" du tableau de bord de l'employe.
 *
 * Aucune regle nouvelle : pour chaque ticket en cours de l'employe,
 * - CE QU'IL FAUT FAIRE est le texte d'aide du statut du ticket (textesAideStatut, lot L6 E9),
 *   celui que la page du ticket affiche deja ; profil RESPONSABLE si le ticket lui est assigne,
 *   LECTURE sinon (consultation) ;
 * - L'ECHEANCE la plus proche est la plus proche de la date limite du ticket (`deadline`) et
 *   des echeances des demarches restant a accomplir sur son dossier (dashboard-service,
 *   `mesEcheancesAssignees` : delais du guide, 30 jours, dossiers dont il est responsable).
 *   Le serveur rend ces echeances par DOSSIER (sans le ticket) : elles sont rattachees aux
 *   tickets du dossier qui sont au statut "Deroulement de la demarche", seul statut ou des
 *   demarches se cochent.
 * Les tickets clos ou annules n'ont plus d'action : ils ne figurent pas dans la liste.
 * Tri : echeance la plus proche d'abord ; sans echeance, a la fin (plus ancien d'abord).
 */
export interface EcheanceAction {
  date: string;
  /** 'TICKET' : date limite du ticket ; 'DEMARCHE' : delai d'une demarche du dossier. */
  source: 'TICKET' | 'DEMARCHE';
  libelle: string;
  /** DEPASSE | CRITIQUE | APPROCHE (demarche seulement). */
  severite?: string;
}

export interface ProchaineAction {
  ticket: Ticket;
  profil: ProfilTicket;
  aide: AideStatut;
  echeance: EcheanceAction | null;
  /** Demarches du dossier restant a accomplir dans les 30 jours (au plus 3, les plus proches). */
  demarches: EcheanceItem[];
}

const SANS_ACTION = new Set(['CLOTURE_DOSSIER', 'ANNULE']);

function jour(iso: string): string {
  return iso.slice(0, 10);
}

export function prochainesActions(tickets: Ticket[], echeances: EcheanceItem[] | null, userId: string | undefined): ProchaineAction[] {
  const parDossier = new Map<string, EcheanceItem[]>();
  for (const e of echeances ?? []) {
    if (!e.dossierId) continue;
    const l = parDossier.get(e.dossierId) ?? [];
    l.push(e);
    parDossier.set(e.dossierId, l);
  }
  for (const l of parDossier.values()) {
    l.sort((a, b) => (a.dateEcheance ?? '9999').localeCompare(b.dateEcheance ?? '9999'));
  }

  const actions = tickets
    .filter((t) => !SANS_ACTION.has(t.statut))
    .map((t): ProchaineAction => {
      const profil: ProfilTicket = userId && t.assigneId === userId ? 'RESPONSABLE' : 'LECTURE';
      const demarches = t.statut === 'DEROULEMENT_DEMARCHE' && t.dossierId ? (parDossier.get(t.dossierId) ?? []) : [];
      const candidates: EcheanceAction[] = [];
      if (t.deadline) candidates.push({ date: jour(t.deadline), source: 'TICKET', libelle: 'Date limite du ticket' });
      const premiere = demarches.find((d) => d.dateEcheance);
      if (premiere?.dateEcheance) {
        candidates.push({ date: jour(premiere.dateEcheance), source: 'DEMARCHE', libelle: premiere.libelle, severite: premiere.severite });
      }
      candidates.sort((a, b) => a.date.localeCompare(b.date));
      return { ticket: t, profil, aide: aideStatutTicket(t.statut, profil), echeance: candidates[0] ?? null, demarches: demarches.slice(0, 3) };
    });

  return actions.sort((a, b) => {
    if (a.echeance && b.echeance) return a.echeance.date.localeCompare(b.echeance.date);
    if (a.echeance) return -1;
    if (b.echeance) return 1;
    return (a.ticket.createdAt ?? '').localeCompare(b.ticket.createdAt ?? '');
  });
}
