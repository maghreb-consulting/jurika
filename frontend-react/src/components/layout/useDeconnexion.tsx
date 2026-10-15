import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { envoyerAvantDeconnexion } from '../../pages/workflow/brouillonsLocaux';
import { ConfirmDialog } from '../ui/ConfirmDialog';

/**
 * Lot L7 (E12) -- deconnexion VOLONTAIRE protegee.
 *
 * Les saisies d'un parcours gardees dans ce navigateur (session expiree, coupure)
 * contiennent des donnees de dossier : la deconnexion les efface. Elles sont donc
 * d'abord envoyees ; si une saisie n'a pas pu partir (reseau, conflit), l'employe est
 * averti qu'elle serait perdue et doit confirmer.
 *
 * `avant` : nettoyage propre a l'ecran appelant, fait seulement si la deconnexion a lieu.
 */
export function useDeconnexion(avant?: () => void): {
  deconnecter: () => Promise<void>;
  dialogue: ReactNode;
  enCours: boolean;
} {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [restants, setRestants] = useState(0);
  const [ouvert, setOuvert] = useState(false);
  const [enCours, setEnCours] = useState(false);

  async function terminer() {
    avant?.();
    await logout();
    navigate('/login', { replace: true });
  }

  async function deconnecter() {
    const u = useAuthStore.getState().user;
    if (u?.userId && u.workspaceId) {
      setEnCours(true);
      let n = 0;
      try {
        n = await envoyerAvantDeconnexion({ userId: u.userId, workspaceId: u.workspaceId });
      } finally {
        setEnCours(false);
      }
      if (n > 0) {
        setRestants(n);
        setOuvert(true);
        return;
      }
    }
    await terminer();
  }

  const dialogue = (
    <ConfirmDialog
      open={ouvert}
      onOpenChange={setOuvert}
      title="Une saisie n’a pas pu être envoyée"
      description={
        restants > 1
          ? `${restants} saisies de parcours sont gardées dans ce navigateur et n’ont pas pu être envoyées (connexion coupée, ou étape modifiée ailleurs). Si vous vous déconnectez maintenant, elles seront effacées et perdues.`
          : 'Une saisie de parcours est gardée dans ce navigateur et n’a pas pu être envoyée (connexion coupée, ou étape modifiée ailleurs). Si vous vous déconnectez maintenant, elle sera effacée et perdue.'
      }
      cancelLabel="Rester connecté"
      confirmLabel="Me déconnecter et l’effacer"
      variant="danger"
      onConfirm={() => {
        setOuvert(false);
        void terminer();
      }}
    >
      <p className="text-sm text-fg-muted">
        Pour la conserver, restez connecté : rouvrez le parcours concerné une fois la connexion
        revenue, puis choisissez la version à garder si on vous le demande.
      </p>
    </ConfirmDialog>
  );

  return { deconnecter, dialogue, enCours };
}
