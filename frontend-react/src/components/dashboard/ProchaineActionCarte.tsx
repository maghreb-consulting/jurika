import { Link } from 'react-router-dom';
import { CalendarClock, ListChecks } from 'lucide-react';
import type { Ticket } from '../../types/ticket';
import type { EcheanceItem } from '../../types/dashboard';
import { STATUT_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';
import { Card } from '../ui/Card';
import { Badge, type BadgeVariant } from '../ui/Badge';
import { EmptyState } from '../ui/EmptyState';
import { InfoBulle } from '../ui/Aide';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';
import { Skeleton, SqueletteZone } from '../ui/LoadingSkeleton';
import { prochainesActions, type EcheanceAction } from './prochaineAction';

const MAX_LIGNES = 5;

/** Ecart en jours entre une date ISO (AAAA-MM-JJ) et aujourd'hui (heure locale). */
function joursRestants(date: string, aujourdHui: Date): number {
  const [a, m, j] = date.split('-').map(Number);
  const cible = Date.UTC(a, m - 1, j);
  const ref = Date.UTC(aujourdHui.getFullYear(), aujourdHui.getMonth(), aujourdHui.getDate());
  return Math.round((cible - ref) / 86_400_000);
}

function delai(n: number): { texte: string; variante: BadgeVariant } {
  if (n < 0) return { texte: `Dépassée de ${-n} jour${n < -1 ? 's' : ''}`, variante: 'danger' };
  if (n === 0) return { texte: 'Aujourd’hui', variante: 'danger' };
  if (n <= 3) return { texte: `Dans ${n} jour${n > 1 ? 's' : ''}`, variante: 'warning' };
  return { texte: `Dans ${n} jours`, variante: 'neutral' };
}

function dateFr(date: string): string {
  const [a, m, j] = date.split('-').map(Number);
  return new Date(a, m - 1, j).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

function Echeance({ e, aujourdHui }: { e: EcheanceAction | null; aujourdHui: Date }) {
  if (!e) {
    return <p className="text-sm text-fg-subtle">Aucune échéance connue</p>;
  }
  const d = delai(joursRestants(e.date, aujourdHui));
  return (
    <div className="flex flex-col items-start gap-1 @lg:items-end">
      <Badge variant={d.variante}>{d.texte}</Badge>
      <p className="text-sm font-medium tabular-nums text-fg">{dateFr(e.date)}</p>
      <p className="max-w-[16rem] text-xs text-fg-muted @lg:text-right">
        {e.source === 'TICKET' ? 'Date limite du ticket' : `Démarche : ${e.libelle}`}
      </p>
    </div>
  );
}

/**
 * Lot L6b (E9, point 8) : carte "Prochaine action" du tableau de bord de l'employe. Pour chaque
 * ticket en cours : ce qu'il faut faire maintenant (aide du statut du ticket) et l'echeance la
 * plus proche, triee par echeance ; lien direct vers le ticket. Regles : `prochaineAction.ts`.
 *
 * `tickets` null : la liste des tickets n'a pas pu etre chargee (dit, jamais "aucun ticket").
 * `echeances` null : les echeances des demarches n'ont pas pu etre chargees (dit ; les dates
 * limites des tickets restent prises en compte).
 */
export function ProchaineActionCarte({
  tickets,
  echeances,
  userId,
  loading,
  aujourdHui = new Date(),
}: {
  tickets: Ticket[] | null;
  echeances: EcheanceItem[] | null;
  userId: string | undefined;
  loading: boolean;
  aujourdHui?: Date;
}) {
  const actions = tickets ? prochainesActions(tickets, echeances, userId) : [];
  const titre = (
    <span className="inline-flex items-center gap-1.5">
      Prochaine action
      <InfoBulle
        libelle="Aide : prochaine action"
        texte="Pour chacun de vos tickets en cours : ce qu’il faut faire à son statut et l’échéance la plus proche (date limite du ticket ou délai d’une démarche restant à accomplir sur le dossier, dans les 30 prochains jours). Ce qui manque pour passer à l’étape suivante est nommé sur la page du ticket."
      />
    </span>
  );

  return (
    <Card accent title={titre} description="Ce qu’il faut faire maintenant, ticket par ticket, l’échéance la plus proche en premier." className="mb-6">
      {loading ? (
        <SqueletteZone libelle="Chargement de vos prochaines actions…">
          <ul className="divide-y divide-border">
            {Array.from({ length: 3 }).map((_, i) => (
              <li key={i} className="flex items-start justify-between gap-4 py-3">
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-4 w-1/2" />
                  <Skeleton className="h-3 w-5/6" />
                </div>
                <Skeleton className="h-10 w-28" />
              </li>
            ))}
          </ul>
        </SqueletteZone>
      ) : tickets === null ? (
        <Message ton="attention" titre="Vos tickets n’ont pas pu être chargés">
          La prochaine action de chaque ticket ne peut pas être calculée. Rechargez la page dans quelques instants,
          ou ouvrez la liste des tickets.
        </Message>
      ) : actions.length === 0 ? (
        <EmptyState
          compact
          icon={<ListChecks />}
          title="Aucune action en attente"
          description="Vous n’avez aucun ticket en cours. Les tickets qui vous sont confiés apparaîtront ici avec ce qu’il faut faire et leur échéance. Une nouvelle opération commence par un ticket, depuis la page Tickets."
          cta={
            <Lien to="/tickets" commeBouton={{ variant: 'secondary', size: 'sm' }}>
              Ouvrir les tickets
            </Lien>
          }
        />
      ) : (
        <>
          {echeances === null && (
            <Message ton="info" className="mb-3">
              Les échéances des démarches n’ont pas pu être chargées : seules les dates limites des tickets sont
              prises en compte dans le tri.
            </Message>
          )}
          <ol className="divide-y divide-border" aria-label="Prochaines actions, échéance la plus proche en premier">
            {actions.slice(0, MAX_LIGNES).map((a) => (
              <li key={a.ticket.id} className="grid gap-3 py-3 @lg:grid-cols-[1fr_auto]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link
                      to={`/tickets/${a.ticket.id}`}
                      className="rounded-sm font-semibold text-heading underline decoration-border-hi underline-offset-2 hover:decoration-2"
                    >
                      {a.ticket.reference ? `${a.ticket.reference} · ` : ''}
                      {a.ticket.titre}
                    </Link>
                    <Badge variant="neutral">{labelOf(STATUT_TICKET_LABELS, a.ticket.statut)}</Badge>
                    {a.profil === 'LECTURE' && <Badge variant="info">Consultation</Badge>}
                  </div>
                  <p className="mt-1 text-sm leading-relaxed text-fg-muted">{a.aide.faire}</p>
                  {a.demarches.length > 1 && (
                    <p className="mt-1 flex items-center gap-1 text-xs text-fg-subtle">
                      <CalendarClock className="h-3.5 w-3.5" aria-hidden="true" />
                      {a.demarches.length} démarches du dossier à échéance dans les 30 jours
                    </p>
                  )}
                </div>
                <Echeance e={a.echeance} aujourdHui={aujourdHui} />
              </li>
            ))}
          </ol>
          {actions.length > MAX_LIGNES && (
            <p className="mt-2 text-sm text-fg-muted">
              {actions.length - MAX_LIGNES} autre{actions.length - MAX_LIGNES > 1 ? 's' : ''} ticket
              {actions.length - MAX_LIGNES > 1 ? 's' : ''} en cours : <Lien to="/tickets">voir tous mes tickets</Lien>.
            </p>
          )}
        </>
      )}
    </Card>
  );
}
