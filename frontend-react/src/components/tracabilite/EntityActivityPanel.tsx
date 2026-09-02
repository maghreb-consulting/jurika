import { useCallback, useEffect, useState } from 'react';
import { History, Loader2 } from 'lucide-react';
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
export function EntityActivityPanel({ entityType, entityId, title = 'Activite' }: Props) {
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
    load();
  }, [load]);

  return (
    <section>
      <div className="mb-2 flex items-center gap-2">
        <History className="h-4 w-4 text-accent" />
        <h3 className="text-sm font-semibold text-fg">{title}</h3>
      </div>

      {error && (
        <div className="mb-2 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-6">
          <Loader2 className="h-5 w-5 animate-spin text-accent" />
        </div>
      ) : items.length === 0 ? (
        <p className="py-4 text-center text-xs text-fg-subtle">Aucune activite enregistree.</p>
      ) : (
        <ul className="space-y-2">
          {items.map((e) => (
            <li key={e.id} className="flex items-start gap-3 border-l-2 border-border pl-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm text-fg">
                  <span className="font-medium">{nameOf(e.userId)}</span>{' '}
                  <span className="text-fg-muted">— {e.actionLabel}</span>
                </p>
                <p className="text-xs text-fg-subtle">
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
