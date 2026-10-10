import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
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
    <section aria-labelledby="reaffecter-titre" className="space-y-3 rounded-lg border border-border bg-bg-raised p-4">
      <h2 id="reaffecter-titre" className="text-sm font-semibold text-fg">
        Réaffecter « {raisonSociale} »
      </h2>
      <p className="text-xs text-fg-subtle">
        Le dossier et tous ses tickets passent au nouvel employé, qui est prévenu, ainsi que l’ancien.
      </p>
      {choix.length === 0 && (
        <p className="text-xs text-fg-muted">
          Aucun autre employé actif dans le cabinet : invitez ou réactivez un employé depuis la page « Équipe ».
        </p>
      )}
      <label className="block text-sm text-fg-muted">
        Nouvel employé responsable
        <select
          className="mt-1 block w-full rounded-md border border-border bg-bg-raised p-2 text-sm text-fg"
          value={employe}
          onChange={(e) => setEmploye(e.target.value)}
        >
          <option value="">Choisir un employé…</option>
          {choix.map((e) => (
            <option key={e.userId} value={e.userId}>
              {e.firstName} {e.lastName}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-sm text-fg-muted">
        Motif (obligatoire)
        <input
          className="mt-1 block w-full rounded-md border border-border bg-bg-raised p-2 text-sm text-fg"
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          placeholder="Par exemple : absence prolongée de l’employé responsable"
        />
      </label>
      <div className="flex justify-end gap-2">
        <Button size="sm" variant="ghost" onClick={onAnnuler}>
          Annuler
        </Button>
        <Button
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
