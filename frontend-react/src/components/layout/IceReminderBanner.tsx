import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, X } from 'lucide-react';
import { useCurrentUser } from '../../store/authStore';
import { workspaceService } from '../../services/workspace.service';

/**
 * Simplification inscription (2026-07-13) — Rappel discret "ICE manquant".
 *
 * L'inscription simplifiee laisse l'ICE optionnel. Tant qu'il est vide, on
 * affiche ce bandeau (SUPERVISEUR uniquement — seul role habilite a le
 * completer) avec un lien direct vers l'onglet Cabinet. Best-effort : toute
 * erreur reseau masque simplement le bandeau. Masquable pour la session.
 */
const DISMISS_KEY = 'jurika.ice-reminder.dismissed';

export function IceReminderBanner() {
  const user = useCurrentUser();
  const [missing, setMissing] = useState(false);
  const [dismissed, setDismissed] = useState<boolean>(() => {
    try { return sessionStorage.getItem(DISMISS_KEY) === '1'; } catch { return false; }
  });

  useEffect(() => {
    // Seul le SUPERVISEUR peut completer l'ICE — on ne sollicite pas les autres.
    if (!user || user.role !== 'SUPERVISEUR') return;
    let cancelled = false;
    workspaceService
      .getProfile()
      .then((p) => { if (!cancelled) setMissing(p.iceMissing); })
      .catch(() => { /* best-effort : on masque le bandeau en cas d'erreur */ });
    return () => { cancelled = true; };
  }, [user]);

  if (!user || user.role !== 'SUPERVISEUR' || !missing || dismissed) return null;

  function dismiss() {
    setDismissed(true);
    try { sessionStorage.setItem(DISMISS_KEY, '1'); } catch { /* noop */ }
  }

  return (
    <div
      className="border-b border-warning/40 bg-warning/10"
      data-testid="ice-reminder-banner"
      role="status"
    >
      <div className="mx-auto flex max-w-7xl items-center gap-3 px-6 py-2 text-sm text-fg">
        <AlertTriangle className="h-4 w-4 flex-shrink-0 text-warning" />
        <span className="flex-1">
          Votre ICE n'est pas encore renseigne.{' '}
          <Link to="/settings/cabinet" className="font-semibold text-accent hover:underline">
            Completer maintenant
          </Link>{' '}
          — il sera requis pour vos dossiers.
        </span>
        <button
          type="button"
          onClick={dismiss}
          aria-label="Masquer ce rappel"
          className="rounded p-1 text-fg-subtle transition-colors hover:text-fg"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
