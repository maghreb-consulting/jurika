import { useEffect } from 'react';
import { tokenStorage } from '../../lib/api';
import { useAuthStore } from '../../store/authStore';
import { EVENEMENT_SESSION_OUVERTE, purgerBrouillonsPerimes, renvoyerBrouillons } from './brouillonsLocaux';

/**
 * Lot L7 (E12) : renvoi des saisies gardees dans ce navigateur pour l'utilisateur
 * connecte, sans qu'il ait a rouvrir le parcours : a l'ouverture d'une session
 * (connexion apres expiration), au demarrage de l'application avec une session, et
 * au retour du reseau. Un parcours ouvert dans l'onglet renvoie lui-meme les siens.
 * Les brouillons d'un autre utilisateur ne sont jamais lus. Au demarrage, les brouillons
 * de plus de 7 jours sont effaces, connecte ou non (decision du 2026-10-14).
 */
export function useRenvoiBrouillons(): void {
  const isHydrating = useAuthStore((s) => s.isHydrating);

  useEffect(() => {
    purgerBrouillonsPerimes();
  }, []);

  useEffect(() => {
    const renvoyer = () => {
      const u = useAuthStore.getState().user;
      if (!u?.userId || !u.workspaceId || !tokenStorage.getAccessToken()) return;
      renvoyerBrouillons({ userId: u.userId, workspaceId: u.workspaceId }).catch(() => undefined);
    };
    if (!isHydrating) renvoyer();
    window.addEventListener(EVENEMENT_SESSION_OUVERTE, renvoyer);
    window.addEventListener('online', renvoyer);
    return () => {
      window.removeEventListener(EVENEMENT_SESSION_OUVERTE, renvoyer);
      window.removeEventListener('online', renvoyer);
    };
  }, [isHydrating]);
}
