import { useEffect, useMemo, useState } from 'react';
import { Drawer } from '../ui/Drawer';
import { Button } from '../ui/Button';
import { Select } from '../ui/Select';
import { authService } from '../../services/auth.service';
import { transferService } from '../../services/transfer.service';
import { extractError } from '../../lib/api';
import type { WorkspaceUser } from '../../types/auth';

interface Props {
  open: boolean;
  dossierId: string;
  raisonSociale: string;
  onClose: () => void;
  onDone?: () => void;
}

/**
 * Drawer "Transferer a..." (V9). Selecteur de collegue + motif. Reserve aux
 * EMPLOYES : cree une demande EN_ATTENTE (la cible devra accepter). Le transfert
 * direct superviseur (ex-VOIE B) a ete retire.
 */
export function TransferDossierDrawer({ open, dossierId, raisonSociale, onClose, onDone }: Props) {
  const [contacts, setContacts] = useState<WorkspaceUser[]>([]);
  const [toUserId, setToUserId] = useState('');
  const [motif, setMotif] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Remise a zero a l'ouverture, ajustee pendant le rendu (pas d'etat pose dans un effet).
  const [ouvertVu, setOuvertVu] = useState<boolean | null>(null);
  if (ouvertVu !== open) {
    setOuvertVu(open);
    if (open) {
      setError(null);
      setSuccess(null);
    }
  }

  useEffect(() => {
    if (!open) return;
    authService
      .listChatContacts()
      // 2026-06-30 — Un dossier ne peut être transféré qu'à un EMPLOYE : le
      // SUPERVISEUR est oversight-only (ne traite pas les workflows) et n'a
      // donc aucune voie de transfert (G1). On exclut aussi tout rôle non
      // EMPLOYE par construction (SUPER_ADMIN/CLIENT ne sont déjà pas dans la
      // liste contacts, mais ce filtre rend la garde explicite côté UI).
      .then((list) => setContacts(list.filter((u) => u.status === 'ACTIVE' && u.role === 'EMPLOYE')))
      .catch((err) => setError(extractError(err).message));
  }, [open]);

  const options = useMemo(
    () => [
      { value: '', label: 'Choisir un collègue…' },
      // Tous les contacts ici sont des EMPLOYES (cf. filtre ci-dessus) — plus
      // de branche libellé "(Superviseur)" : elle était devenue morte.
      ...contacts.map((u) => ({
        value: u.userId,
        label: `${u.firstName} ${u.lastName} (employé)`,
      })),
    ],
    [contacts],
  );

  async function submit() {
    if (!toUserId) {
      setError('Sélectionnez le collègue qui reprendra le dossier.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await transferService.requestTransfer(dossierId, toUserId, motif);
      setSuccess('Demande de transfert envoyée. Le dossier vous reste confié jusqu’à ce que votre collègue l’accepte.');
      onDone?.();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Transférer le dossier"
      subtitle={raisonSociale}
      width="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            {success ? 'Fermer' : 'Annuler'}
          </Button>
          {!success && (
            <Button onClick={submit} loading={loading}>
              Envoyer la demande
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4">
        {error && (
          <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}
        {success ? (
          <div className="rounded-lg border border-success/40 bg-success/10 px-3 py-2 text-sm text-success">
            {success}
          </div>
        ) : (
          <>
            <p className="text-sm text-fg-subtle">
              Choisissez l’employé qui reprendra ce dossier. Une demande lui est envoyée : le transfert ne
              s’applique qu’après son acceptation, et vous restez responsable du dossier d’ici là.
            </p>
            <Select
              label="Nouveau responsable"
              value={toUserId}
              onChange={(e) => setToUserId(e.target.value)}
              options={options}
            />
            <div className="flex flex-col gap-1">
              <label htmlFor="transfer-motif" className="text-sm font-medium text-fg-muted">
                Motif (facultatif)
              </label>
              <textarea
                id="transfer-motif"
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
                rows={3}
                maxLength={1000}
                placeholder="Exemple : congé prolongé, répartition de la charge…"
                className="w-full rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
            </div>
          </>
        )}
      </div>
    </Drawer>
  );
}
