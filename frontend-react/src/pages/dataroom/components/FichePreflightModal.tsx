import { AlertTriangle } from 'lucide-react';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import type { IdentityField } from './ficheClientPreflight';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  missing: IdentityField[];
  generating: boolean;
  onComplete: () => void;
  onGenerateAnyway: () => void;
}

/**
 * Fiche client (2026-07-14) — fenêtre de contrôle avant génération. Liste les
 * identifiants manquants pertinents au vu du statut, avec deux actions :
 * « Compléter maintenant » (ouvre le formulaire) et « Générer quand même ».
 * Ne bloque jamais la génération.
 */
export function FichePreflightModal({
  open,
  onOpenChange,
  missing,
  generating,
  onComplete,
  onGenerateAnyway,
}: Props) {
  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      size="md"
      title="Identifiants incomplets"
      description="Certains identifiants de la société ne sont pas encore renseignés."
    >
      <div className="mb-4 flex items-start gap-3 rounded-lg border border-warning/40 bg-warning/10 px-3 py-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning" />
        <div>
          <p className="text-sm font-medium text-fg">
            {missing.length} champ{missing.length > 1 ? 's' : ''} manquant
            {missing.length > 1 ? 's' : ''} :
          </p>
          <ul className="mt-1 list-disc pl-5 text-sm text-fg-muted">
            {missing.map((f) => (
              <li key={String(f.key)}>{f.label}</li>
            ))}
          </ul>
        </div>
      </div>
      <p className="mb-5 text-sm text-fg-subtle">
        Vous pouvez compléter ces informations maintenant pour une fiche complète,
        ou générer la Fiche client en l'état.
      </p>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button
          variant="secondary"
          size="sm"
          onClick={onGenerateAnyway}
          loading={generating}
        >
          Générer quand même
        </Button>
        <Button size="sm" onClick={onComplete} disabled={generating}>
          Compléter maintenant
        </Button>
      </div>
    </Modal>
  );
}
