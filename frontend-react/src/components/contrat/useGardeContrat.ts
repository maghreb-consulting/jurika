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
const CLE_SESSION_VERSION = 'jurika.contrat.nouvelle-version-vue';

export type DecisionGarde = 'CHARGEMENT' | 'LIBRE' | 'REDIRIGER' | 'RESTREINT' | 'ATTENTE_EMPLOYE';

export function useGardeContrat(): DecisionGarde {
  const role = useAuthStore((s) => s.user?.role);
  const userId = useAuthStore((s) => s.user?.userId);
  const { lu: luStore, utilisateur, bloque, objet, charger } = useContratStore();
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

  // RG-CTR-07 : nouvelle version a accepter "a la connexion suivante" : le superviseur est conduit
  // une fois par session a l'ecran du contrat (sans blocage).
  useEffect(() => {
    if (role !== 'SUPERVISEUR' || objet !== 'NOUVELLE_VERSION' || pathname === '/app/contrat') return;
    let dejaVue = false;
    try {
      dejaVue = sessionStorage.getItem(CLE_SESSION_VERSION) === '1';
      sessionStorage.setItem(CLE_SESSION_VERSION, '1');
    } catch {
      // stockage indisponible : la redirection a lieu, sans memoire de session
    }
    if (!dejaVue) navigate('/app/contrat');
  }, [role, objet, pathname, navigate]);

  if (!concerne) return 'LIBRE';
  if (!lu) return 'CHARGEMENT';
  if (!bloque) return 'LIBRE';
  if (role === 'EMPLOYE') return 'ATTENTE_EMPLOYE';
  return PAGES_PERMISES_CONTRAT.some((p) => pathname.startsWith(p)) ? 'RESTREINT' : 'REDIRIGER';
}
