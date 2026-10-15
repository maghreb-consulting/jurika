import { Suspense, lazy, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCommandesStore } from '../../store/commandesStore';
import { CIBLES, creerLecteurRaccourcis, type ActionRaccourci } from '../../lib/raccourcis';
import { emitTicketsChanged } from '../../lib/ticketsRefresh';
import { prechargerEcran } from '../../routes/pages';
import { useToastSiDisponible } from '../assistant/useJuri';
import { PaletteCommandes } from './PaletteCommandes';
import { FenetreRaccourcis } from './FenetreRaccourcis';

/**
 * Lot L6b (E11) : raccourcis clavier et fenetres globales de la coquille.
 *
 * - Lecteur de touches unique (lib/raccourcis) : Ctrl+K / Cmd+K, ?, n, g puis a / t / d / c ;
 *   jamais pendant la saisie ni quand une fenetre modale est ouverte.
 * - Fenetres : palette de commandes, raccourcis clavier, nouveau ticket (le tiroir de la
 *   page des tickets, sans modification : creation, quota du forfait, puis ouverture du
 *   parcours ; les listes de tickets montees sont rafraichies par le bus ticketsRefresh).
 */
// Charge a la demande : le tiroir (et ses dependances) n'alourdit pas la coquille.
const NewTicketDrawer = lazy(() => import('../../pages/tickets/NewTicketDrawer').then((m) => ({ default: m.NewTicketDrawer })));

export function CommandesGlobales() {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const ouverte = useCommandesStore((s) => s.ouverte);
  const fermer = useCommandesStore((s) => s.fermer);
  const toast = useToastSiDisponible();

  useEffect(() => {
    const lire = creerLecteurRaccourcis({ role: () => useAuthStore.getState().user?.role });
    const executer = (action: ActionRaccourci) => {
      const commandes = useCommandesStore.getState();
      if (action === 'palette') commandes.basculer('palette');
      else if (action === 'raccourcis') commandes.ouvrir('raccourcis');
      else if (action === 'nouveau-ticket') commandes.ouvrir('nouveauTicket');
      else {
        const to = CIBLES[action];
        if (to) {
          prechargerEcran(to);
          navigate(to);
        }
      }
    };
    const surTouche = (e: KeyboardEvent) => {
      const action = lire(e);
      if (!action) return;
      e.preventDefault();
      executer(action);
    };
    window.addEventListener('keydown', surTouche);
    return () => window.removeEventListener('keydown', surTouche);
  }, [navigate]);

  if (!role) return null;
  const cabinet = role === 'EMPLOYE' || role === 'SUPERVISEUR';

  return (
    <>
      {ouverte === 'palette' && <PaletteCommandes />}
      <FenetreRaccourcis open={ouverte === 'raccourcis'} onClose={() => fermer('raccourcis')} />
      {cabinet && ouverte === 'nouveauTicket' && (
        <Suspense fallback={null}>
          <NewTicketDrawer
            open
            onClose={() => fermer('nouveauTicket')}
            onCreated={async () => {
              // Le tiroir ouvre ensuite le parcours du ticket cree : il se ferme apres.
              window.setTimeout(() => fermer('nouveauTicket'), 0);
              emitTicketsChanged();
              toast?.enregistre('Ticket créé');
            }}
          />
        </Suspense>
      )}
    </>
  );
}
