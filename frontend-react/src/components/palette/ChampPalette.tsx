import { Search } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { useCommandesStore } from '../../store/commandesStore';
import { posteApple } from '../../lib/raccourcis';
import { Touches } from './Touches';

/**
 * Lot L6b (E11) : champ de recherche de l'en-tete (emplacement `data-emplacement="recherche"`).
 * C'est un bouton qui a l'allure d'un champ : il ouvre la palette de commandes, comme
 * Ctrl+K / Cmd+K. Sur petit ecran, seule la loupe reste.
 */
export function ChampPalette() {
  const role = useAuthStore((s) => s.user?.role);
  const ouvrir = useCommandesStore((s) => s.ouvrir);
  const apple = posteApple();
  if (!role) return null;
  const texte =
    role === 'SUPER_ADMIN'
      ? 'Aller à un écran, lancer une action…'
      : role === 'CLIENT'
        ? 'Rechercher un dossier…'
        : 'Rechercher un dossier, un ticket…';
  const touches = [apple ? 'Cmd' : 'Ctrl', 'K'];
  return (
    <button
      type="button"
      onClick={() => ouvrir('palette')}
      aria-haspopup="dialog"
      aria-keyshortcuts="Control+K Meta+K"
      aria-label={`${texte.replace('…', '')} : ouvrir la palette de commandes (${touches.join(' + ')})`}
      data-testid="palette-declencheur"
      className="flex h-9 w-9 items-center justify-center gap-2 rounded-lg border border-border-input bg-bg text-sm text-fg-subtle transition-colors duration-150 hover:border-border-hi hover:text-fg-muted sm:w-full sm:max-w-md sm:justify-start sm:px-3"
    >
      <Search className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span className="hidden min-w-0 flex-1 truncate text-left sm:block">{texte}</span>
      <Touches touches={touches} combinaison className="hidden md:inline-flex" />
    </button>
  );
}
