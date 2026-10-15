import { useCallback, useEffect, useState } from 'react';
import { History } from 'lucide-react';
import { Message } from '../ui/Message';
import { Skeleton, SqueletteZone } from '../ui/LoadingSkeleton';
import { tracabiliteService } from '../../services/tracabilite.service';
import { extractError } from '../../lib/api';
import { useUserNames } from './useUserNames';
import type { TracabiliteEntry } from '../../types/tracabilite';

interface Props {
  /** Type d'entite audite : 'dossier' ou 'ticket'. */
  entityType: string;
  entityId: string;
  title?: string;
}

/**
 * Panneau "Activite" contextuel (E2) — historique audit_log d'une entite precise
 * (dossier / ticket). Ouvert a EMPLOYE + SUPERVISEUR.
 */
export function EntityActivityPanel({ entityType, entityId, title = 'Activité' }: Props) {
  const [items, setItems] = useState<TracabiliteEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const nameOf = useUserNames();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await tracabiliteService.entityHistory(entityType, entityId, 50);
      setItems(page.items);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [entityType, entityId]);

  useEffect(() => {
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
  }, [load]);

  return (
    <section>
      <div className="mb-2 flex items-center gap-2">
        <History className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
        <h3 className="text-sm font-semibold text-heading">{title}</h3>
      </div>

      {error && (
        <Message ton="erreur" className="mb-2">
          {error}
        </Message>
      )}

      {loading ? (
        <SqueletteZone libelle="Chargement de l’activité…" className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="space-y-1.5 border-l-2 border-border pl-3">
              <Skeleton className="h-3.5 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          ))}
        </SqueletteZone>
      ) : items.length === 0 ? (
        error ? null : <p className="py-4 text-center text-sm text-fg-muted">Aucune activité enregistrée.</p>
      ) : (
        <ul className="space-y-2">
          {items.map((e) => (
            <li key={e.id} className="flex items-start gap-3 border-l-2 border-border pl-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm text-fg">
                  <span className="font-medium">{nameOf(e.userId)}</span>{' '}
                  <span className="text-fg-muted">— {e.actionLabel}</span>
                </p>
                <p className="text-xs tabular-nums text-fg-subtle">
                  {new Date(e.createdAt).toLocaleString('fr-FR')}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
