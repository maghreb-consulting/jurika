import { useEffect, useState } from 'react';
import { dataroomService } from '../../services/dataroom.service';
import { ticketService } from '../../services/ticket.service';
import type { DossierBrief } from '../../types/dataroom';
import type { Ticket } from '../../types/ticket';

export interface EntityNames {
  dossiers: DossierBrief[];
  tickets: Ticket[];
  loading: boolean;
  /**
   * Resout (entityType, entityId) -> libelle lisible (raison sociale du dossier
   * ou reference/titre du ticket). Fallback : UUID court si non resolu (ex.
   * entityType "document", non liste cote front).
   */
  entityLabelOf: (type: string | null | undefined, id: string | null | undefined) => string;
}

/**
 * Charge une fois les dossiers (raisonSociale) et tickets (reference/titre) du
 * workspace — listes deja scopees RLS par le JWT — pour : (1) alimenter les
 * <Select> par nom dans le filtre Tracabilite ; (2) afficher des noms (pas des
 * UUID) dans les lignes du tableau. Best-effort : un echec laisse la liste vide.
 */
export function useEntityNames(): EntityNames {
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([
      dataroomService.listDossiers(),
      ticketService.list({ limit: 200, sortBy: 'createdAt', desc: true }),
    ])
      .then(([d, t]) => {
        if (!alive) return;
        if (d.status === 'fulfilled') setDossiers(d.value);
        if (t.status === 'fulfilled') setTickets(t.value.items);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const entityLabelOf: EntityNames['entityLabelOf'] = (type, id) => {
    if (!id) return '—';
    if (type === 'dossier') {
      const d = dossiers.find((x) => x.id === id);
      if (d) return d.raisonSociale;
    } else if (type === 'ticket') {
      const t = tickets.find((x) => x.id === id);
      if (t) return t.titre || t.reference;
    }
    return `${id.slice(0, 8)}…`;
  };

  return { dossiers, tickets, loading, entityLabelOf };
}
