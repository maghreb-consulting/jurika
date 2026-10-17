import { useEffect, useMemo, useState } from 'react';
import { Drawer } from '../ui/Drawer';
import { Button } from '../ui/Button';
import { Select } from '../ui/Select';
import { Message } from '../ui/Message';
import { ZoneTexte } from '../ui/TextField';
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
          <Button type="button" variant="secondary" onClick={onClose}>
            {success ? 'Fermer' : 'Annuler'}
          </Button>
          {!success && (
            <Button type="button" onClick={submit} loading={loading}>
              Envoyer la demande
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4">
        {error && <Message ton="erreur">{error}</Message>}
        {success ? (
          <Message ton="succes">{success}</Message>
        ) : (
          <>
            <p className="text-sm text-fg-muted">
              Choisissez l’employé qui reprendra ce dossier. Une demande lui est envoyée : le transfert ne
              s’applique qu’après son acceptation, et vous restez responsable du dossier d’ici là.
            </p>
            <Select
              label="Nouveau responsable"
              value={toUserId}
              onChange={(e) => setToUserId(e.target.value)}
              options={options}
            />
            <ZoneTexte
              id="transfer-motif"
              label="Motif (facultatif)"
              value={motif}
              onChange={(e) => setMotif(e.target.value)}
              rows={3}
              maxLength={1000}
              placeholder="Exemple : congé prolongé, répartition de la charge…"
            />
          </>
        )}
      </div>
    </Drawer>
  );
}
