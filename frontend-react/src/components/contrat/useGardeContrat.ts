import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useContratStore } from '../../store/contratStore';
import { EVENEMENT_CONTRAT_EN_ATTENTE } from '../../services/contrat.service';

/**
 * Lot L7 (E6, RG-CTR-03) : garde de l'ecran pour un cabinet dont le contrat d'abonnement attend
 * l'acceptation du superviseur. Le serveur refuse deja les autres routes (passerelle, 423) ;
 * l'ecran redirige le superviseur vers le contrat et dit a l'employe d'attendre.
 *
 * Pages permises au superviseur pendant l'attente : le contrat et l'identite du cabinet
 * (Parametres > Cabinet), a completer avant d'accepter.
 */
export const PAGES_PERMISES_CONTRAT = ['/app/contrat', '/settings/cabinet'];

export type DecisionGarde = 'CHARGEMENT' | 'LIBRE' | 'REDIRIGER' | 'RESTREINT' | 'ATTENTE_EMPLOYE';

export function useGardeContrat(): DecisionGarde {
  const role = useAuthStore((s) => s.user?.role);
  const userId = useAuthStore((s) => s.user?.userId);
  const { lu: luStore, utilisateur, bloque, objet, charger, versionPresentee, marquerVersionPresentee } =
    useContratStore();
  const lu = luStore && utilisateur === userId;
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const concerne = role === 'SUPERVISEUR' || role === 'EMPLOYE';

  useEffect(() => {
    if (!concerne || !userId) return;
    void charger(userId);
    const relire = () => void charger(userId);
    window.addEventListener(EVENEMENT_CONTRAT_EN_ATTENTE, relire);
    return () => window.removeEventListener(EVENEMENT_CONTRAT_EN_ATTENTE, relire);
  }, [concerne, userId, charger]);

  // RG-CTR-07, article 16 : tant qu'il n'a pas repondu (Accepter ou Refuser) a la nouvelle version,
  // le superviseur est conduit a l'ecran du contrat a chaque connexion, une fois, sans blocage :
  // il peut ensuite travailler librement. La reponse donnee, le serveur ne la redemande plus.
  useEffect(() => {
    if (role !== 'SUPERVISEUR' || objet !== 'NOUVELLE_VERSION' || !userId) return;
    if (versionPresentee === userId) return;
    marquerVersionPresentee(userId);
    if (pathname !== '/app/contrat') navigate('/app/contrat');
  }, [role, objet, userId, versionPresentee, marquerVersionPresentee, pathname, navigate]);

  // Sortie de l'application (deconnexion) : la demande sera presentee a la connexion suivante.
  useEffect(() => () => marquerVersionPresentee(null), [marquerVersionPresentee]);

  if (!concerne) return 'LIBRE';
  if (!lu) return 'CHARGEMENT';
  if (!bloque) return 'LIBRE';
  if (role === 'EMPLOYE') return 'ATTENTE_EMPLOYE';
  return PAGES_PERMISES_CONTRAT.some((p) => pathname.startsWith(p)) ? 'RESTREINT' : 'REDIRIGER';
}
