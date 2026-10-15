import { useEffect, useState } from 'react';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';
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
    <div className="mx-auto max-w-7xl px-6 pt-3" data-testid="ice-reminder-banner">
      <Message ton="attention" onFermer={dismiss}>
        Votre ICE n’est pas encore renseigné.{' '}
        <Lien to="/settings/cabinet">Compléter maintenant</Lien> : il sera requis pour vos dossiers.
      </Message>
    </div>
  );
}
