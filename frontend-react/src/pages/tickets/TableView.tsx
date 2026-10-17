import { SearchX } from 'lucide-react';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { PRIORITE_LABELS, STATUT_LABELS, TICKET_TYPE_LABELS, responsableLabel } from '../../types/ticket';
import type { Ticket, TicketStatut } from '../../types/ticket';

interface Props {
  tickets: Ticket[];
  onSelect: (id: string) => void;
  /** Premier chargement : lignes squelettes aux dimensions du tableau. */
  chargement?: boolean;
}

/**
 * Vue Tableau des tickets (lot L6b : Tableau de la bibliotheque). La ligne est cliquable ;
 * pour le clavier, la reference est un bouton qui ouvre la meme fiche.
 */
export function TableView({ tickets, onSelect, chargement = false }: Props) {
  const colonnes: ColonneTableau<Ticket>[] = [
    {
      cle: 'reference',
      titre: 'Référence',
      rendu: (t) => (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onSelect(t.id);
          }}
          className="rounded font-mono text-xs text-fg-muted underline-offset-2 hover:text-fg hover:underline"
          aria-label={`Ouvrir le ticket ${t.reference}`}
        >
          {t.reference}
        </button>
      ),
    },
    { cle: 'titre', titre: 'Titre', rendu: (t) => <span className="text-fg">{t.titre}</span> },
    { cle: 'type', titre: 'Type', rendu: (t) => <span className="text-fg-muted">{TICKET_TYPE_LABELS[t.type]}</span>, masquerSous: 'md' },
    { cle: 'responsable', titre: 'Responsable', rendu: (t) => <ResponsableCell t={t} /> },
    { cle: 'statut', titre: 'Statut', rendu: (t) => <StatutBadge s={t.statut} /> },
    { cle: 'priorite', titre: 'Priorité', rendu: (t) => <span className="text-fg-muted">{PRIORITE_LABELS[t.priorite]}</span>, masquerSous: 'lg' },
    {
      cle: 'echeance',
      titre: 'Échéance',
      rendu: (t) => (
        <span className="whitespace-nowrap text-fg-muted">
          {t.deadline ? new Date(t.deadline).toLocaleDateString('fr-FR') : '—'}
        </span>
      ),
    },
  ];

  return (
    <Tableau
      colonnes={colonnes}
      lignes={tickets}
      cleLigne={(t) => t.id}
      legende="Tickets"
      chargement={chargement}
      onLigne={(t) => onSelect(t.id)}
      vide={
        <EmptyState
          compact
          icon={<SearchX />}
          title="Aucun ticket à afficher"
          description="Aucun ticket ne correspond à la recherche ou aux filtres."
        />
      }
    />
  );
}

function ResponsableCell({ t }: { t: Ticket }) {
  const label = responsableLabel(t);
  if (label === 'Non assigné') {
    return <span className="italic text-fg-subtle">Non assigné</span>;
  }
  return <span className="text-fg">{label}</span>;
}

const VARIANTE_STATUT: Record<TicketStatut, 'info' | 'warning' | 'success' | 'danger' | 'neutral'> = {
  CREATION_TICKET: 'info',
  GENERATION_DOCUMENTS: 'warning',
  DEROULEMENT_DEMARCHE: 'warning',
  CLOTURE_DOSSIER: 'success',
  ANNULE: 'danger',
};

export function StatutBadge({ s }: { s: TicketStatut }) {
  return <Badge variant={VARIANTE_STATUT[s]}>{STATUT_LABELS[s]}</Badge>;
}
