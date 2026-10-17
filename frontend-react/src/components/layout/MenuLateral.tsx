import { useId, type MouseEvent } from 'react';
import { flushSync } from 'react-dom';
import { NavLink, useNavigate } from 'react-router-dom';
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { cn } from '../../lib/utils';
import { avecTransitionDeVue } from '../../lib/mouvement';
import { prechargerEcran } from '../../routes/pages';
import { useChatUnreadStore, useChatUnreadSync } from '../../store/chatUnreadStore';
import type { Role } from '../../types/auth';
import { menuDuRole, type EntreeMenu } from './navigation';

/**
 * Lot L6b (E3) : menu lateral de la coquille, par role (layout/navigation.ts).
 *
 * - Structure marine ; entree active en or (aria-current="page").
 * - Repliable (icones seules, libelle en infobulle et pour les lecteurs d'ecran).
 * - Prechargement : survol ou focus d'une entree => le code de l'ecran est telecharge.
 * - Transition de vue entre ecrans quand le navigateur la connait (sans dependance) ; un
 *   clic avec Ctrl, Maj ou la molette garde le comportement natif (nouvel onglet).
 * - Cible "menu" de la visite guidee.
 */
export function MenuLateral({
  role,
  replie,
  onBasculer,
  mobile = false,
  onNaviguer,
}: {
  role: Role;
  replie: boolean;
  onBasculer: () => void;
  /** Rendu dans le panneau mobile (pleine largeur, jamais replie). */
  mobile?: boolean;
  /** Appele apres un choix (ferme le panneau mobile). */
  onNaviguer?: () => void;
}) {
  useChatUnreadSync();
  const chatNonLus = useChatUnreadStore((s) => s.total);
  const navigate = useNavigate();
  const idListe = useId();
  const groupes = menuDuRole(role);
  const compact = replie && !mobile;

  function surClic(e: MouseEvent<HTMLAnchorElement>, to: string) {
    onNaviguer?.();
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    e.preventDefault();
    avecTransitionDeVue(() => flushSync(() => navigate(to)));
  }

  function compteur(e: EntreeMenu): number {
    return e.compteur === 'chat' ? chatNonLus : 0;
  }

  return (
    <nav
      aria-label="Menu principal"
      data-visite="menu"
      className={cn(
        'zone-marine flex h-full flex-col bg-nav-bg text-nav-fg',
        compact ? 'w-[var(--largeur-menu-replie)]' : 'w-[var(--largeur-menu)]',
      )}
    >
      <div id={idListe} className="min-h-0 flex-1 overflow-y-auto overscroll-auto px-2 py-4">
        {groupes.map((g) => (
          <div key={g.titre} className="mb-4 last:mb-0">
            <p className={cn('mb-1 px-3 text-[0.6875rem] font-semibold uppercase tracking-wider text-nav-fg', compact && 'sr-only')}>
              {g.titre}
            </p>
            <ul className="space-y-0.5">
              {g.entrees.map((e) => {
                const Icone = e.icone;
                const n = compteur(e);
                return (
                  <li key={`${g.titre}-${e.to}`}>
                    <NavLink
                      to={e.to}
                      end={e.to === '/dashboard'}
                      title={compact ? e.libelle : undefined}
                      onMouseEnter={() => prechargerEcran(e.to)}
                      onFocus={() => prechargerEcran(e.to)}
                      onClick={(ev) => surClic(ev, e.to)}
                      className={({ isActive }) =>
                        cn(
                          'relative flex h-9 items-center gap-3 rounded-lg px-3 text-sm transition-colors duration-150',
                          compact && 'justify-center px-0',
                          isActive
                            ? 'bg-nav-bg-active font-semibold text-nav-active before:absolute before:inset-y-1.5 before:left-0 before:w-[3px] before:rounded-full before:bg-nav-active'
                            : 'text-nav-fg hover:bg-nav-bg-hover hover:text-nav-fg-hi',
                        )
                      }
                    >
                      <Icone className="h-[1.125rem] w-[1.125rem] shrink-0" aria-hidden="true" />
                      <span className={cn('min-w-0 flex-1 truncate', compact && 'sr-only')}>{e.libelle}</span>
                      {n > 0 && (
                        <span
                          className={cn(
                            'min-w-[1.25rem] rounded-full bg-danger px-1.5 py-0.5 text-center text-[0.6875rem] font-bold leading-none text-danger-fg',
                            compact && 'absolute right-1 top-1 min-w-[1rem] px-1',
                          )}
                          aria-label={`${n} message${n > 1 ? 's' : ''} non lu${n > 1 ? 's' : ''}`}
                        >
                          {n}
                        </span>
                      )}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
      {!mobile && (
        <div className="border-t border-nav-border p-2">
          <button
            type="button"
            onClick={onBasculer}
            aria-expanded={!replie}
            aria-controls={idListe}
            title={replie ? 'Déplier le menu' : 'Réduire le menu'}
            className={cn(
              'flex h-9 w-full items-center gap-3 rounded-lg px-3 text-sm text-nav-fg transition-colors duration-150 hover:bg-nav-bg-hover hover:text-nav-fg-hi',
              compact && 'justify-center px-0',
            )}
          >
            {replie ? (
              <PanelLeftOpen className="h-[1.125rem] w-[1.125rem]" aria-hidden="true" />
            ) : (
              <PanelLeftClose className="h-[1.125rem] w-[1.125rem]" aria-hidden="true" />
            )}
            <span className={cn(compact && 'sr-only')}>{replie ? 'Déplier le menu' : 'Réduire le menu'}</span>
          </button>
        </div>
      )}
    </nav>
  );
}
