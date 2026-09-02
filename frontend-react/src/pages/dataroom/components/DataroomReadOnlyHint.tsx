import { Lock } from 'lucide-react';
import {
  archivedStatutLabel,
  dataroomReadOnlyReason,
} from '../../../lib/dossierArchive';

/**
 * Lot DIVERS §A (2026-08-13) — encart affiche A LA PLACE des actions d'ecriture
 * (upload, depot, nouvelle demande) quand la societe est dissoute / liquidee /
 * radiee. Le bandeau global de la page dit la regle ; cet encart la rappelle la
 * ou l'utilisateur cherche le bouton disparu.
 */
export function DataroomReadOnlyHint({
  statut,
  testId,
  className = '',
}: {
  statut?: string | null;
  testId?: string;
  className?: string;
}) {
  return (
    <div
      className={`flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 px-3 py-2 text-xs text-fg-subtle ${className}`}
      title={dataroomReadOnlyReason(statut)}
      data-testid={testId ?? 'dataroom-readonly-hint'}
    >
      <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" />
      <span>
        <span className="font-semibold text-fg">
          {archivedStatutLabel(statut)}
        </span>{' '}
        — archive legale : consultation et telechargement uniquement.
      </span>
    </div>
  );
}
