import { useEffect, useState } from 'react';
import { AlertCircle, Bell, BellOff, CheckCircle, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { notificationService } from '../../services/notification.service';
import {
  NOTIFICATION_TYPES,
  NOTIFICATION_TYPE_LABELS,
  filterNotificationTypesForRole,
} from '../../types/notification';
import { useCurrentUser } from '../../store/authStore';
import { extractError } from '../../lib/api';

/**
 * BUG 11 (2026-06-08) — Onglet Notifications dans /settings : opt-in/opt-out
 * par type de notif (ex DEADLINE_DUE, CHAT_MESSAGE, ...).
 *
 * Pas de save bouton : chaque toggle envoie un PATCH (debounce naturel par
 * l'attente reseau). Les preferences inconnues sont opt-in par defaut cote
 * serveur — voir realtime-service / notification_preferences.
 */
export function NotificationPreferencesTab() {
  const user = useCurrentUser();
  const [prefs, setPrefs] = useState<Record<string, boolean>>({});
  const [types, setTypes] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const r = await notificationService.getPreferences();
        setPrefs(r.preferences ?? {});
        // Repli : si le realtime-service ne renvoie pas (ou renvoie vide)
        // `knownTypes`, on utilise la liste par defaut alignee sur les types
        // reellement emis. Puis on adapte au role (CLIENT sans flux tickets).
        const base = r.knownTypes && r.knownTypes.length > 0 ? r.knownTypes : NOTIFICATION_TYPES;
        setTypes(filterNotificationTypesForRole(base, user?.role));
      } catch (err) {
        // Service injoignable : on affiche tout de meme les toggles par defaut
        // (l'utilisateur voit la liste), accompagnes d'un bandeau d'erreur.
        setTypes(filterNotificationTypesForRole(NOTIFICATION_TYPES, user?.role));
        setError(extractError(err).message);
      } finally {
        setLoading(false);
      }
    })();
  }, [user?.role]);

  async function toggle(type: string) {
    const next = !prefs[type];
    setBusy(type);
    setError(null);
    setFeedback(null);
    try {
      await notificationService.setPreferences({ [type]: next });
      setPrefs((p) => ({ ...p, [type]: next }));
      setFeedback('Preferences enregistrees.');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBusy(null);
    }
  }

  if (loading) {
    return (
      <div className="flex h-32 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-accent" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
          <Bell className="h-5 w-5 text-accent" /> Notifications
        </h2>
        <p className="mt-1 text-sm text-fg-muted">
          Choisissez les types de notifications que vous souhaitez recevoir
          en temps reel. Les changements sont enregistres automatiquement.
        </p>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          <AlertCircle className="h-4 w-4" /> {error}
        </div>
      )}
      {feedback && (
        <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          <CheckCircle className="h-4 w-4" /> {feedback}
        </div>
      )}

      {types.length === 0 ? (
        <div
          className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border bg-bg-raised px-4 py-10 text-center"
          data-testid="notif-pref-empty"
        >
          <BellOff className="h-6 w-6 text-fg-subtle" />
          <p className="text-sm font-medium text-fg">Aucune preference disponible</p>
          <p className="text-xs text-fg-subtle">
            Aucun type de notification n'est configurable pour le moment.
          </p>
        </div>
      ) : (
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-bg-raised">
          {types.map((t) => {
            const enabled = prefs[t] !== false;
            return (
              <li
                key={t}
                className="flex items-center justify-between gap-4 px-4 py-3"
                data-testid={`notif-pref-${t}`}
              >
                <div>
                  <p className="text-sm font-medium text-fg">
                    {NOTIFICATION_TYPE_LABELS[t] ?? t}
                  </p>
                  <p className="font-mono text-[10px] uppercase tracking-wide text-fg-subtle">
                    {t}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant={enabled ? 'primary' : 'secondary'}
                  onClick={() => toggle(t)}
                  loading={busy === t}
                  disabled={busy === t}
                >
                  {enabled ? 'Active' : 'Desactive'}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
