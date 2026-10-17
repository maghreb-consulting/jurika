import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Message } from '../../../components/ui/Message';
import { Select } from '../../../components/ui/Select';
import { TextField } from '../../../components/ui/TextField';
import type { WorkspaceUser } from '../../../types/auth';

/**
 * Lot L1 (RG-DOS-03) : formulaire de reaffectation d'office d'un dossier par le
 * superviseur : un employe actif (hors responsable actuel) et un motif obligatoire.
 * Utilise par l'historique des responsables et par "Dossiers a verifier".
 */
export function ReaffectationForm({
  raisonSociale,
  responsableActuelId,
  employes,
  enCours,
  onValider,
  onAnnuler,
}: {
  raisonSociale: string;
  responsableActuelId: string | null;
  /** Employes actifs du cabinet. */
  employes: WorkspaceUser[];
  enCours: boolean;
  onValider: (employeId: string, motif: string) => void;
  onAnnuler: () => void;
}) {
  const [employe, setEmploye] = useState('');
  const [motif, setMotif] = useState('');
  const choix = employes.filter((e) => e.userId !== responsableActuelId);

  return (
    <section aria-labelledby="reaffecter-titre" className="space-y-3 rounded-lg border border-border bg-bg-overlay p-4">
      <h2 id="reaffecter-titre" className="text-sm font-semibold text-heading">
        Réaffecter « {raisonSociale} »
      </h2>
      <p className="text-sm text-fg-muted">
        Le dossier et tous ses tickets passent au nouvel employé, qui est prévenu, ainsi que l’ancien.
      </p>
      {choix.length === 0 && (
        <Message ton="attention">
          Aucun autre employé actif dans le cabinet : invitez ou réactivez un employé depuis la page « Équipe ».
        </Message>
      )}
      <Select
        label="Nouvel employé responsable"
        value={employe}
        onChange={(e) => setEmploye(e.target.value)}
        options={[
          { value: '', label: 'Choisir un employé…' },
          ...choix.map((e) => ({ value: e.userId, label: `${e.firstName} ${e.lastName}` })),
        ]}
      />
      <TextField
        label="Motif (obligatoire)"
        value={motif}
        onChange={(e) => setMotif(e.target.value)}
        placeholder="Par exemple : absence prolongée de l’employé responsable"
      />
      <div className="flex justify-end gap-2">
        <Button type="button" size="sm" variant="ghost" onClick={onAnnuler}>
          Annuler
        </Button>
        <Button
          type="button"
          size="sm"
          onClick={() => onValider(employe, motif.trim())}
          disabled={!employe || !motif.trim()}
          loading={enCours}
        >
          Réaffecter le dossier
        </Button>
      </div>
    </section>
  );
}
