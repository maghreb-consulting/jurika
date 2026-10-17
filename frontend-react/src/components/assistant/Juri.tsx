import { useEffect, useRef, useState } from 'react';
import { Bot, Compass, EyeOff, Info, Keyboard, Search } from 'lucide-react';
import { cn } from '../../lib/utils';
import { useMouvementReduit } from '../../lib/mouvement';
import { posteApple } from '../../lib/raccourcis';
import { useAuthStore } from '../../store/authStore';
import { useAidesStore } from '../../store/aidesStore';
import { useAssistantStore } from '../../store/assistantStore';
import { useCommandesStore } from '../../store/commandesStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { Button } from '../ui/Button';
import { Drawer } from '../ui/Drawer';
import { EmptyState } from '../ui/EmptyState';
import { cleVisite } from '../ui/visiteEtapes';
import { peutUtiliserChatbot } from '../chatbot/rolesChatbot';
import { lireAidesEcran, montrerAide, titreEcran, type AideEcran } from './aidesEcran';
import { JuriRobot } from './JuriRobot';
import { useEtatJuri, useToastSiDisponible } from './useJuri';

/**
 * Lot L6b (E11, point 8 de la demande) : JURI, l'assistant de l'interface.
 *
 * - Au repos, a moitie cache sur le bord droit de l'ecran ; au survol ou au focus clavier,
 *   il glisse vers l'interieur et dit "Besoin d'aide ?" ; au clic, il ouvre son panneau :
 *   l'aide de l'ecran en cours (encadres et infobulles de la page), la visite guidee, les
 *   raccourcis clavier, la palette de commandes et le chatbot juridique (roles du cabinet
 *   et super-admin ; la logique du chatbot reste dans AssistantJuridique).
 * - Il ne recouvre rien : la coquille lui reserve une bande a droite de la zone de contenu
 *   (`pr-16`, 4 rem) et JURI tient dans cette bande, deploye compris (3,75 rem).
 * - Masquable : choix enregistre sur le compte (preferences, `juriMasque`) ; le menu du
 *   compte et la palette le reaffichent.
 * - "Reduire les animations" : JURI est immobile (entierement visible dans sa bande, aucun
 *   glissement ; seule la bulle "Besoin d'aide ?" apparait, sans transition).
 * - Accessibilite : bouton nomme (le nom commence par le texte visible), panneau en
 *   <dialog> natif (Echap ferme, focus rendu au bouton) ; a la fermeture du chatbot ouvert
 *   depuis JURI, le focus revient aussi au bouton.
 */
export function Juri() {
  const role = useAuthStore((s) => s.user?.role);
  const { affiche } = useEtatJuri();
  const ouvert = useCommandesStore((s) => s.ouverte === 'juri');
  const ouvrir = useCommandesStore((s) => s.ouvrir);
  const fermer = useCommandesStore((s) => s.fermer);
  const reduit = useMouvementReduit();
  const [survol, setSurvol] = useState(false);
  const [focus, setFocus] = useState(false);
  const bouton = useRef<HTMLButtonElement | null>(null);

  // Le chatbot ouvert depuis JURI se ferme : le focus revient a JURI.
  const chatbotOuvert = useAssistantStore((s) => s.ouvert);
  const chatbotAvant = useRef(chatbotOuvert);
  useEffect(() => {
    if (chatbotAvant.current && !chatbotOuvert) bouton.current?.focus();
    chatbotAvant.current = chatbotOuvert;
  }, [chatbotOuvert]);

  if (!affiche || !role) return null;
  const deploye = survol || focus || ouvert;

  return (
    <>
      <div className="pointer-events-none fixed bottom-6 right-0 z-30" data-testid="juri" data-visite="chatbot">
        <button
          ref={bouton}
          type="button"
          data-juri-bouton=""
          data-deploye={deploye ? 'oui' : 'non'}
          aria-label="Besoin d’aide ? Ouvrir JURI, l’assistant de cet écran"
          aria-haspopup="dialog"
          aria-expanded={ouvert}
          title="JURI : aide de cet écran, raccourcis et chatbot"
          onMouseEnter={() => setSurvol(true)}
          onMouseLeave={() => setSurvol(false)}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          onClick={() => ouvrir('juri')}
          className={cn(
            'pointer-events-auto relative mr-1 flex w-14 flex-col items-center rounded-2xl outline-none',
            'focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-bg',
            !reduit && 'transition-transform duration-200 ease-out',
            !reduit && !deploye && 'translate-x-[55%]',
          )}
        >
          <span
            aria-hidden="true"
            data-testid="juri-bulle"
            className={cn(
              'absolute bottom-full right-0 mb-1.5 w-14 rounded-lg border border-nav-active bg-marine px-1 py-1 text-center text-[11px] font-semibold leading-tight text-nav-fg-hi shadow-card-lifted',
              !reduit && 'transition-opacity duration-150',
              deploye ? 'opacity-100' : 'invisible opacity-0',
            )}
          >
            Besoin d’aide&nbsp;?
          </span>
          <JuriRobot taille={56} className="drop-shadow-sm" />
        </button>
      </div>
      <PanneauJuri open={ouvert} onClose={() => fermer('juri')} />
    </>
  );
}

function PanneauJuri({ open, onClose }: { open: boolean; onClose: () => void }) {
  const role = useAuthStore((s) => s.user?.role);
  const masquees = useAidesStore((s) => s.masquees);
  const reafficher = useAidesStore((s) => s.reafficher);
  const ouvrirFenetre = useCommandesStore((s) => s.ouvrir);
  const ouvrirChatbot = useAssistantStore((s) => s.ouvrir);
  const definirJuriMasque = usePreferencesStore((s) => s.definirJuriMasque);
  const toast = useToastSiDisponible();
  const [aides, setAides] = useState<AideEcran[]>([]);
  const [titre, setTitre] = useState<string | null>(null);
  const apple = posteApple();

  // Les aides sont lues dans la page a l'ouverture, puis apres chaque reaffichage.
  useEffect(() => {
    if (!open) return;
    const image = window.requestAnimationFrame(() => {
      setAides(lireAidesEcran());
      setTitre(titreEcran());
    });
    return () => window.cancelAnimationFrame(image);
  }, [open, masquees]);

  if (!role) return null;
  const chatbot = peutUtiliserChatbot(role);
  const textes = aides.filter((a) => a.type === 'texte');
  const bulles = aides.filter((a) => a.type === 'infobulle');

  function voir(cle: string, masquee: boolean) {
    onClose();
    if (masquee) reafficher(cle);
    // Apres la fermeture du panneau (focus rendu), l'encadre recoit le focus.
    window.setTimeout(() => montrerAide(cle), 0);
  }

  function masquerJuri() {
    onClose();
    void definirJuriMasque(true).then((ok) => {
      if (ok) toast?.enregistre('JURI est masqué ; le menu de votre compte le réaffiche');
    });
    window.setTimeout(() => document.getElementById('contenu')?.focus(), 0);
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width="md"
      title="JURI, votre assistant"
      subtitle={titre ? `Aide de l’écran « ${titre} »` : 'Aide de l’écran en cours'}
      footer={
        <div className="flex w-full flex-wrap items-center justify-between gap-2">
          <p className="text-xs text-fg-muted">Vous pourrez réafficher JURI depuis le menu de votre compte.</p>
          <Button type="button" variant="ghost" size="sm" icon={<EyeOff className="h-4 w-4" />} onClick={masquerJuri}>
            Masquer JURI
          </Button>
        </div>
      }
    >
      <div className="space-y-6" data-testid="juri-panneau">
        <section aria-labelledby="juri-ecran">
          <h3 id="juri-ecran" className="text-sm font-semibold text-heading">
            Sur cet écran
          </h3>
          {aides.length === 0 ? (
            <EmptyState
              compact
              icon={<Info />}
              title="Pas d’aide particulière sur cet écran"
              description="Les boutons ci-dessous restent à votre disposition : la visite guidée présente l’essentiel, les raccourcis clavier font gagner du temps et la palette de commandes retrouve un dossier ou un ticket."
            />
          ) : (
            <ul className="mt-2 space-y-3">
              {textes.map((a) => (
                <li key={`t-${a.cle}`} className="rounded-lg border border-border bg-bg p-3">
                  <p className="text-sm font-semibold text-fg">{a.titre}</p>
                  {a.masquee ? (
                    <p className="mt-1 text-sm text-fg-muted">Vous avez masqué cette aide sur la page.</p>
                  ) : (
                    <p className="mt-1 text-sm leading-relaxed text-fg-muted">{a.texte}</p>
                  )}
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    className="mt-2"
                    onClick={() => voir(a.cle, a.masquee)}
                  >
                    {a.masquee ? 'Réafficher sur la page' : 'Voir sur la page'}
                  </Button>
                </li>
              ))}
              {bulles.map((a) => (
                <li key={`b-${a.texte}`} className="rounded-lg border border-border bg-bg p-3">
                  <p className="flex items-center gap-1.5 text-sm font-semibold text-fg">
                    <Info className="h-4 w-4 text-info" aria-hidden="true" />
                    {libelleBulle(a.libelle)}
                  </p>
                  <p className="mt-1 text-sm leading-relaxed text-fg-muted">{a.texte}</p>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="juri-plus">
          <h3 id="juri-plus" className="text-sm font-semibold text-heading">
            Pour aller plus loin
          </h3>
          <div className="mt-2 grid gap-2">
            {chatbot && (
              <Button
                type="button"
                variant="secondary"
                fullWidth
                className="justify-start"
                icon={<Bot className="h-4 w-4" />}
                onClick={() => {
                  onClose();
                  ouvrirChatbot();
                }}
              >
                Poser une question au chatbot juridique
              </Button>
            )}
            <Button
              type="button"
              variant="secondary"
              fullWidth
              className="justify-start"
              icon={<Compass className="h-4 w-4" />}
              onClick={() => {
                onClose();
                reafficher(cleVisite(role));
              }}
            >
              Revoir la visite guidée
            </Button>
            <Button
              type="button"
              variant="secondary"
              fullWidth
              className="justify-start"
              icon={<Keyboard className="h-4 w-4" />}
              onClick={() => ouvrirFenetre('raccourcis')}
            >
              Raccourcis clavier (touche ?)
            </Button>
            <Button
              type="button"
              variant="secondary"
              fullWidth
              className="justify-start"
              icon={<Search className="h-4 w-4" />}
              onClick={() => ouvrirFenetre('palette')}
            >
              {`Palette de commandes (${apple ? 'Cmd' : 'Ctrl'} + K)`}
            </Button>
          </div>
          {chatbot && (
            <p className="mt-2 text-xs leading-relaxed text-fg-muted">
              Le chatbot répond à partir des seules sources déposées par JURIKA et les cite ; il n’a accès à
              aucun dossier du cabinet.
            </p>
          )}
        </section>
      </div>
    </Drawer>
  );
}

function libelleBulle(libelle: string): string {
  const net = libelle.replace(/^Aide\s*:\s*/i, '').trim();
  if (!net || net.toLowerCase() === 'aide') return 'Aide';
  return net.charAt(0).toUpperCase() + net.slice(1);
}
