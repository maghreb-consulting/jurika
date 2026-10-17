import type { TicketsPerEmploye } from '../../services/supervision.service';
import type { Ticket } from '../../types/ticket';
import { STATUT_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';
import { BarresEmpilees, type LigneEmpilee, type SerieEmpilee } from './charts/BarresEmpilees';
import { STATUTS_OUVERTS, estOuvert } from './series';

const SERIES: SerieEmpilee[] = [
  { cle: 'CREATION_TICKET', nom: labelOf(STATUT_TICKET_LABELS, 'CREATION_TICKET'), ton: 'neutre' },
  { cle: 'GENERATION_DOCUMENTS', nom: labelOf(STATUT_TICKET_LABELS, 'GENERATION_DOCUMENTS'), ton: 'info' },
  { cle: 'DEROULEMENT_DEMARCHE', nom: labelOf(STATUT_TICKET_LABELS, 'DEROULEMENT_DEMARCHE'), ton: 'accent' },
];

const NON_ASSIGNE = 'non-assigne';

function pluriel(n: number, s: string, p: string): string {
  return `${n} ${n > 1 ? p : s}`;
}

/**
 * Lot L6b (E18) : charge de l'equipe, une barre par employe, empilee par etape du parcours
 * (tickets ouverts qui lui sont confies). Repond a "qui porte quoi, et ou en sont ses tickets ?"
 * pour decider d'une reaffectation. Sous chaque nom : ses clotures et ses annulations depuis le
 * debut (supervision). Les tickets sans responsable ont leur ligne. Les employes sans ticket
 * ouvert restent listes (barre vide), jamais omis.
 */
export function ChargeEquipe({ tickets, equipe }: { tickets: Ticket[]; equipe: TicketsPerEmploye[] | null }) {
  const lignes = new Map<string, LigneEmpilee>();
  const vide = (): Record<string, number> => Object.fromEntries(STATUTS_OUVERTS.map((s) => [s, 0]));

  for (const e of equipe ?? []) {
    lignes.set(e.userId, {
      cle: e.userId,
      libelle: `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim() || 'Nom non renseigné',
      detail: `${pluriel(e.clotures, 'clôturé', 'clôturés')} · ${pluriel(e.annules, 'annulé', 'annulés')}`,
      valeurs: vide(),
    });
  }
  for (const t of tickets.filter(estOuvert)) {
    const cle = t.assigneId ?? NON_ASSIGNE;
    if (!lignes.has(cle)) {
      const nom = `${t.assignePrenom ?? ''} ${t.assigneNom ?? ''}`.trim();
      lignes.set(cle, { cle, libelle: t.assigneId ? nom || 'Responsable inconnu' : 'Sans responsable', valeurs: vide() });
    }
    const l = lignes.get(cle)!;
    l.valeurs[t.statut] = (l.valeurs[t.statut] ?? 0) + 1;
  }
  const total = (l: LigneEmpilee) => Object.values(l.valeurs).reduce((n, v) => n + v, 0);
  const triees = [...lignes.values()].sort((a, b) => {
    if (a.cle === NON_ASSIGNE) return 1;
    if (b.cle === NON_ASSIGNE) return -1;
    return total(b) - total(a) || a.libelle.localeCompare(b.libelle);
  });
  return <BarresEmpilees lignes={triees} series={SERIES} legende="Tickets ouverts de chaque employé, par étape" />;
}
