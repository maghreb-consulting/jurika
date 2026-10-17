import { useState } from 'react';
import { ArrowRightLeft } from 'lucide-react';
import { Button } from '../ui/Button';
import { useCurrentUser } from '../../store/authStore';
import { TransferDossierDrawer } from './TransferDossierDrawer';

interface Props {
  dossierId: string;
  raisonSociale: string;
  /** Style optionnel pour s'integrer aux barres d'actions existantes. */
  className?: string;
  onDone?: () => void;
}

/**
 * Bouton "Transferer a..." (V9). Reserve aux EMPLOYES (cree une demande de
 * transfert). Le SUPERVISEUR (oversight only) ne transfere plus : le bouton est
 * masque pour lui. Le backend verifie que l'employe est bien le responsable
 * courant du dossier.
 */
export function TransferDossierButton({ dossierId, raisonSociale, className, onDone }: Props) {
  const user = useCurrentUser();
  const [open, setOpen] = useState(false);

  if (!user || user.role !== 'EMPLOYE') {
    return null;
  }

  return (
    <>
      <Button
        type="button"
        variant="secondary"
        icon={<ArrowRightLeft />}
        onClick={() => setOpen(true)}
        className={className}
        title="Transférer ce dossier à un collègue"
      >
        Transférer à un collègue
      </Button>
      <TransferDossierDrawer
        open={open}
        dossierId={dossierId}
        raisonSociale={raisonSociale}
        onClose={() => setOpen(false)}
        onDone={onDone}
      />
    </>
  );
}
