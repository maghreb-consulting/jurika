import { useCallback, useEffect, useState } from 'react';
import { ArrowRightLeft, Check, X } from 'lucide-react';
import { InfoBulle } from '../ui/Aide';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { Message } from '../ui/Message';
import { transferService } from '../../services/transfer.service';
import { extractError } from '../../lib/api';
import { getSocket } from '../../lib/realtimeSocket';
import { emitTicketsChanged } from '../../lib/ticketsRefresh';
import type { DossierTransfer } from '../../types/transfer';

/**
 * Panneau "Transferts en attente" (V9). Affiche les demandes recues (a
 * accepter/refuser) et envoyees (a annuler). Se rafraichit sur les events
 * socket transfer:received / transfer:accepted / transfer:rejected.
 */
export function PendingTransfersPanel() {
  const [inbox, setInbox] = useState<DossierTransfer[]>([]);
  const [outbox, setOutbox] = useState<DossierTransfer[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [inb, outb] = await Promise.all([
        transferService.listInbox(),
        transferService.listOutbox(),
      ]);
      setInbox(inb);
      setOutbox(outb);
    } catch (err) {
      setError(extractError(err).message);
    }
  }, []);

  useEffect(() => {
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
    // Rafraichissement temps reel best-effort (le socket est optionnel).
    const socket = getSocket();
    const refresh = () => load();
    socket?.on('transfer:received', refresh);
    socket?.on('transfer:accepted', refresh);
    socket?.on('transfer:rejected', refresh);
    return () => {
      socket?.off('transfer:received', refresh);
      socket?.off('transfer:accepted', refresh);
      socket?.off('transfer:rejected', refresh);
    };
  }, [load]);

  async function act(fn: () => Promise<unknown>, id: string, onSuccess?: () => void) {
    setBusyId(id);
    setError(null);
    try {
      await fn();
      onSuccess?.();
      await load();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBusyId(null);
    }
  }

  if (inbox.length === 0 && outbox.length === 0) {
    return null;
  }

  return (
    <Card className="mb-6 p-4">
      <div className="mb-3 flex items-center gap-2">
        <ArrowRightLeft className="h-4 w-4 text-info" aria-hidden="true" />
        <h3 className="text-base font-semibold text-heading">Transferts en attente</h3>
        <InfoBulle
          libelle="Que faire d’un transfert reçu ?"
          texte="Un collègue vous propose de reprendre un de ses dossiers. Acceptez : le dossier et ses tickets en cours passent sous votre responsabilité. Refusez : il reste à votre collègue. Les demandes que vous avez envoyées attendent la réponse de leur destinataire."
        />
      </div>

      {error && (
        <Message ton="erreur" className="mb-3" onFermer={() => setError(null)}>
          {error}
        </Message>
      )}

      {inbox.length > 0 && (
        <div className="mb-3">
          <h4 className="mb-1 text-xs font-semibold text-fg-muted">Reçus</h4>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {inbox.map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium text-fg">{t.raisonSociale ?? 'Dossier sans raison sociale'}</p>
                  {t.motif && <p className="truncate text-xs text-fg-muted">{t.motif}</p>}
                </div>
                <div className="flex flex-shrink-0 items-center gap-2">
                  <Button
                    type="button"
                    size="sm"
                    icon={<Check />}
                    loading={busyId === t.id}
                    onClick={() =>
                      // Le dossier vient de m'etre reassigne : signaler a la page
                      // Tickets (si montee) de recharger pour voir mes tickets.
                      act(() => transferService.accept(t.id), t.id, emitTicketsChanged)
                    }
                  >
                    Accepter
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    icon={<X />}
                    disabled={busyId === t.id}
                    onClick={() => act(() => transferService.reject(t.id), t.id)}
                  >
                    Refuser
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {outbox.length > 0 && (
        <div>
          <h4 className="mb-1 text-xs font-semibold text-fg-muted">Envoyés</h4>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {outbox.map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium text-fg">{t.raisonSociale ?? 'Dossier sans raison sociale'}</p>
                  <p className="text-xs text-fg-muted">En attente d’acceptation</p>
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  disabled={busyId === t.id}
                  onClick={() => act(() => transferService.cancel(t.id), t.id)}
                >
                  Annuler
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
