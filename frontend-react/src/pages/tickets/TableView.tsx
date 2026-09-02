import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { PRIORITE_LABELS, STATUT_LABELS, TICKET_TYPE_LABELS, responsableLabel } from '../../types/ticket';
import type { Ticket, TicketStatut } from '../../types/ticket';

interface Props {
  tickets: Ticket[];
  onSelect: (id: string) => void;
}

export function TableView({ tickets, onSelect }: Props) {
  // Sprint 12.5 T7 -- zebra navy alterne + headers font-heading + hover bg-overlay
  return (
    <Card className="overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-bg-overlay font-heading text-xs uppercase tracking-wider text-fg-muted">
          <tr>
            <th className="px-4 py-3 text-left">Reference</th>
            <th className="px-4 py-3 text-left">Titre</th>
            <th className="px-4 py-3 text-left">Type</th>
            <th className="px-4 py-3 text-left">Responsable</th>
            <th className="px-4 py-3 text-left">Statut</th>
            <th className="px-4 py-3 text-left">Priorite</th>
            <th className="px-4 py-3 text-left">Echeance</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {tickets.map((t) => (
            <tr
              key={t.id}
              className="cursor-pointer transition-colors odd:bg-bg-raised even:bg-bg-overlay/30 hover:bg-bg-overlay"
              onClick={() => onSelect(t.id)}
            >
              <td className="px-4 py-3 font-mono text-xs text-fg-muted">{t.reference}</td>
              <td className="px-4 py-3 text-fg">{t.titre}</td>
              <td className="px-4 py-3 text-fg-muted">{TICKET_TYPE_LABELS[t.type]}</td>
              <td className="px-4 py-3">
                <ResponsableCell t={t} />
              </td>
              <td className="px-4 py-3">
                <StatutBadge s={t.statut} />
              </td>
              <td className="px-4 py-3 text-fg-muted">{PRIORITE_LABELS[t.priorite]}</td>
              <td className="px-4 py-3 text-fg-subtle">
                {t.deadline ? new Date(t.deadline).toLocaleDateString('fr-FR') : '—'}
              </td>
            </tr>
          ))}
          {tickets.length === 0 && (
            <tr>
              <td colSpan={7} className="px-4 py-12 text-center text-sm text-fg-subtle">
                Aucun ticket
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </Card>
  );
}

function ResponsableCell({ t }: { t: Ticket }) {
  const label = responsableLabel(t);
  if (label === 'Non assigne') {
    return <span className="text-fg-subtle italic">Non assigne</span>;
  }
  return <span className="text-fg">{label}</span>;
}

function StatutBadge({ s }: { s: TicketStatut }) {
  const variant = {
    NOUVEAU: 'info',
    EN_COURS: 'warning',
    CLOTURE: 'success',
    ANNULE: 'danger',
  }[s] as 'info' | 'warning' | 'success' | 'danger';
  return <Badge variant={variant}>{STATUT_LABELS[s]}</Badge>;
}
