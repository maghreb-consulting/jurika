import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bot, ChevronDown, Compass, FileSignature, Info, LogOut, Menu as IconeMenu, Settings, Shield, User, Zap } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { useAidesStore } from '../../store/aidesStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import type { Role } from '../../types/auth';
import { BrandLogo } from '../ui/BrandLogo';
import { classesBouton } from '../ui/boutonClasses';
import { Menu, MenuElement, MenuSeparateur } from '../ui/Menu';
import { cleVisite } from '../ui/visiteEtapes';
import { DeadlineCountWidget } from './DeadlineCountWidget';
import { NotificationBell } from './NotificationBell';
import { ThemeToggle } from './ThemeToggle';
import { useDeconnexion } from './useDeconnexion';
import { ChampPalette } from '../palette/ChampPalette';
import { useEtatJuri, useToastSiDisponible } from '../assistant/useJuri';
import { NOM_PLATEFORME, libelleRole } from './identite';

/** Roles qui ont acces au calendrier des echeances (/calendar). */
const ROLES_ECHEANCES: Role[] = ['SUPERVISEUR', 'EMPLOYE'];

/**
 * Lot L6b (E3) : en-tete de la coquille (remplace la barre AppTopNav).
 *
 * Gauche : bouton du menu (petits ecrans), logo Moucharabieh (ivoire en sombre, BrandLogo),
 * code du cabinet. Centre : emplacement de la recherche / palette de commandes
 * (`data-emplacement="recherche"`, rempli par l'etape de la palette). Droite : changement de
 * forfait (cabinet), echeances, theme, notifications, compte.
 *
 * Reprises du lot L6 (E6) : super-admin presente sous le nom JURIKA (CDC 3.1) ; cibles de la
 * visite guidee (`data-visite` : echeances, cloche, compte) ; menu du compte avec "Mon
 * compte", "Revoir la visite guidee", "Reafficher les aides".
 */
export function AppEnTete({ onOuvrirMenuMobile }: { onOuvrirMenuMobile?: () => void }) {
  const user = useAuthStore((s) => s.user);
  const reafficherAide = useAidesStore((s) => s.reafficher);
  const reafficherTout = useAidesStore((s) => s.reafficherTout);
  const viderAides = useAidesStore((s) => s.vider);
  const viderPreferences = usePreferencesStore((s) => s.vider);
  const definirJuriMasque = usePreferencesStore((s) => s.definirJuriMasque);
  const { masque: juriMasque } = useEtatJuri();
  const toast = useToastSiDisponible();
  const [annonce, setAnnonce] = useState('');
  // Lot L7 (E12) : les saisies gardees dans ce navigateur partent avant la deconnexion.
  const { deconnecter, dialogue: dialogueDeconnexion } = useDeconnexion(() => {
    viderAides();
    viderPreferences();
  });

  if (!user) return null;

  const plateforme = user.role === 'SUPER_ADMIN';
  const nomAffiche = plateforme ? NOM_PLATEFORME : user.email;
  const initiales = (nomAffiche[0] ?? 'U').toUpperCase();
  // Code du cabinet saisi a la connexion ; repli sur l'ancien derive de l'UUID (anciennes sessions).
  const wsCode = user.workspaceCode ?? `JUR-${user.workspaceId.slice(0, 5).toUpperCase()}`;
  const sousTitre = plateforme ? 'Administration de la plateforme' : wsCode;
  const superviseur = user.role === 'SUPERVISEUR';

  function revoirVisite() {
    if (user) reafficherAide(cleVisite(user.role));
  }

  function reafficherAides() {
    reafficherTout().then(
      () => setAnnonce('Les aides masquées sont de nouveau affichées.'),
      () => setAnnonce('Les aides sont réaffichées sur cet écran, mais le choix n’a pas pu être enregistré.'),
    );
  }

  // Lot L6b (E11) : JURI masque ou reaffiche depuis le menu du compte (choix du compte).
  function basculerJuri() {
    const masquer = !juriMasque;
    void definirJuriMasque(masquer).then((ok) => {
      if (ok) toast?.enregistre(masquer ? 'JURI est masqué' : 'JURI est de retour');
    });
  }

  return (
    <header className="sticky top-0 z-40 flex h-[var(--hauteur-entete)] items-center gap-3 border-b border-border bg-bg-raised px-3 md:px-4">
      {onOuvrirMenuMobile && (
        <button
          type="button"
          onClick={onOuvrirMenuMobile}
          aria-label="Ouvrir le menu"
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted hover:bg-bg-overlay hover:text-fg md:hidden"
        >
          <IconeMenu className="h-5 w-5" aria-hidden="true" />
        </button>
      )}
      <Link
        to="/dashboard"
        className="flex shrink-0 items-center gap-3 rounded-md"
        aria-label="JURIKA, retour au tableau de bord"
      >
        <BrandLogo variant="full" size={34} />
        <span className="hidden border-l border-border pl-3 lg:block">
          <span className="block font-mono text-xs text-fg-subtle" data-testid="topnav-sous-titre">
            {sousTitre}
          </span>
        </span>
      </Link>

      <div data-emplacement="recherche" className="flex min-w-0 flex-1 justify-center">
        {/* Lot L6b (E11) : champ qui ouvre la palette de commandes (Ctrl+K). */}
        <ChampPalette />
      </div>

      <div className="flex shrink-0 items-center gap-1">
        {superviseur && (
          <span className="mr-1 hidden rounded-full border border-border px-2 py-0.5 text-xs font-medium text-fg-muted xl:inline">
            Superviseur
          </span>
        )}

        {/* Changement de forfait : cabinet seulement (ni super-admin, ni client : lot M). */}
        {user.role !== 'SUPER_ADMIN' && user.role !== 'CLIENT' && (
          <Link
            to="/app/billing/change-plan"
            className={classesBouton('secondary', 'sm', 'mr-1 hidden sm:inline-flex')}
            data-testid="topnav-upgrade-cta"
            title="Voir les forfaits et changer de forfait"
          >
            <Zap className="h-4 w-4" aria-hidden="true" />
            Changer de forfait
          </Link>
        )}

        {ROLES_ECHEANCES.includes(user.role) && (
          <span data-visite="echeances">
            <DeadlineCountWidget />
          </span>
        )}

        <ThemeToggle />

        {/* Lot L5 (E9, RG-COM-09) : le client est averti par courriel, pas dans l'application. */}
        {user.role !== 'CLIENT' && (
          <span data-visite="cloche">
            <NotificationBell />
          </span>
        )}

        <div className="mx-1 h-6 w-px bg-border" aria-hidden="true" />

        <div data-visite="compte">
          <Menu
            libelle={`Menu du compte : ${nomAffiche}`}
            alignement="fin"
            classeBouton="h-10 px-1.5"
            bouton={
              <>
                <span
                  aria-hidden="true"
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-accent/30 bg-accent-soft text-xs font-bold text-accent"
                >
                  {initiales}
                </span>
                <span className="hidden max-w-[12rem] text-left sm:block">
                  <span className="block truncate text-sm font-medium leading-tight text-fg" data-testid="topnav-nom">
                    {nomAffiche}
                  </span>
                  <span className="block text-xs leading-tight text-fg-subtle">{libelleRole(user.role)}</span>
                </span>
                <ChevronDown className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
              </>
            }
            entete={
              plateforme ? (
                <>
                  <p className="text-xs uppercase tracking-wide text-fg-subtle">Compte</p>
                  <p className="mt-0.5 text-sm font-semibold text-fg">{NOM_PLATEFORME}</p>
                  <p className="text-xs text-fg-subtle">Administration de la plateforme</p>
                </>
              ) : (
                <>
                  <p className="text-xs uppercase tracking-wide text-fg-subtle">Cabinet</p>
                  <p className="mt-0.5 font-mono text-sm text-fg">{wsCode}</p>
                </>
              )
            }
          >
            <MenuElement to="/profile" icone={<User />}>
              Mon compte
            </MenuElement>
            <MenuElement to="/settings/security" icone={<Shield />}>
              Sécurité de connexion
            </MenuElement>
            {/* Lot L7 (E6) : contrat d'abonnement du cabinet (superviseur). */}
            {superviseur && (
              <MenuElement to="/app/contrat" icone={<FileSignature />}>
                Contrat d’abonnement
              </MenuElement>
            )}
            <MenuElement to="/settings" icone={<Settings />}>
              Paramètres
            </MenuElement>
            <MenuSeparateur />
            <MenuElement onSelect={revoirVisite} icone={<Compass />}>
              Revoir la visite guidée
            </MenuElement>
            <MenuElement onSelect={reafficherAides} icone={<Info />}>
              Réafficher les aides
            </MenuElement>
            <MenuElement onSelect={basculerJuri} icone={<Bot />}>
              {juriMasque ? 'Afficher JURI' : 'Masquer JURI'}
            </MenuElement>
            <MenuSeparateur />
            <MenuElement onSelect={() => void deconnecter()} icone={<LogOut />} danger>
              Déconnexion
            </MenuElement>
          </Menu>
        </div>
      </div>
      <p role="status" aria-live="polite" className="sr-only">
        {annonce}
      </p>
      {dialogueDeconnexion}
    </header>
  );
}
