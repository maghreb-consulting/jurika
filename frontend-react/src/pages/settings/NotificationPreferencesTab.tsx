import { useEffect, useState } from 'react';
import { BellOff } from 'lucide-react';
import { Badge } from '../../components/ui/Badge';
import { Interrupteur } from '../../components/ui/Choix';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { SectionOnglet } from './EcranOuOnglet';
import { notificationService } from '../../services/notification.service';
import {
  NOTIFICATION_TYPES,
  NOTIFICATION_TYPE_LABELS,
  filterNotificationTypesForRole,
} from '../../types/notification';
import { useCurrentUser } from '../../store/authStore';
import { extractError } from '../../lib/api';
import { TexteAide } from '../../components/ui/Aide';

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
    // Un type sans preference enregistree est active par defaut (prefs[type] !== false) :
    // le basculer le desactive.
    const next = prefs[type] === false;
    setBusy(type);
    setError(null);
    setFeedback(null);
    try {
      await notificationService.setPreferences({ [type]: next });
      setPrefs((p) => ({ ...p, [type]: next }));
      setFeedback('Préférences enregistrées.');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <SectionOnglet
      titre="Notifications"
      description="Choisissez les notifications que vous souhaitez recevoir en temps réel. Chaque changement est enregistré aussitôt."
      aide={
        <TexteAide cle="preferences-notifications" titre="Activer ou couper une notification">
          <p>
            Cliquez sur l’interrupteur d’une ligne pour la faire passer de « Activée » à « Désactivée », ou
            l’inverse. Une notification désactivée ne vous est plus signalée ; vous pouvez la réactiver à
            tout moment.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        {error && <Message ton="erreur">{error}</Message>}
        {feedback && <Message ton="succes">{feedback}</Message>}

        {loading ? (
          <SqueletteZone libelle="Chargement de vos préférences de notification">
            <div className="divide-y divide-border rounded-xl border border-border bg-bg-raised">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-center justify-between gap-4 px-4 py-3.5">
                  <Skeleton className="h-4 w-1/2" />
                  <Skeleton className="h-6 w-11 rounded-full" />
                </div>
              ))}
            </div>
          </SqueletteZone>
        ) : types.length === 0 ? (
          <div data-testid="notif-pref-empty">
            <EmptyState
              icon={<BellOff />}
              title="Aucune préférence disponible"
              description="Aucun type de notification n’est réglable pour le moment : vous recevez toutes les notifications prévues pour votre rôle."
              compact
            />
          </div>
        ) : (
          <ul className="divide-y divide-border rounded-xl border border-border bg-bg-raised" aria-label="Types de notification">
            {types.map((t) => {
              const enabled = prefs[t] !== false;
              return (
                <li key={t} className="px-4 py-3" data-testid={`notif-pref-${t}`}>
                  <Interrupteur
                    checked={enabled}
                    disabled={busy === t}
                    onChange={() => void toggle(t)}
                    label={
                      <span className="flex flex-wrap items-center gap-2 font-medium">
                        {NOTIFICATION_TYPE_LABELS[t] ?? t}
                        <Badge variant={enabled ? 'success' : 'neutral'}>{enabled ? 'Activée' : 'Désactivée'}</Badge>
                      </span>
                    }
                  />
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </SectionOnglet>
  );
}
