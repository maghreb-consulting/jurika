import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck, Inbox, Loader2 } from 'lucide-react';
import { notificationService } from '../../services/notification.service';
import { getSocket } from '../../lib/realtimeSocket';
import type { Notification } from '../../types/notification';
import { NOTIFICATION_TYPE_LABELS } from '../../types/notification';

/**
 * BUG 10 (2026-06-08) — Cloche topbar avec badge dynamique (unread-count)
 * + drawer listant les notifs. Combine :
 *  - polling 60s (fallback),
 *  - Socket.io `notification:received` pour le temps reel.
 *
 * Clic sur une notification : marque comme lue + navigue vers
 * {@code actionUrl} si present (ex `/chat` pour CHAT_MESSAGE).
 */
function formatRelative(iso: string): string {
  try {
    const d = new Date(iso);
    const diffSec = Math.floor((Date.now() - d.getTime()) / 1000);
    if (diffSec < 60) return 'a l\'instant';
    if (diffSec < 3600) return `il y a ${Math.floor(diffSec / 60)} min`;
    if (diffSec < 86_400) return `il y a ${Math.floor(diffSec / 3600)} h`;
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
  } catch {
    return '';
  }
}

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  // 2026-06-22 — coupe-circuit : si le backend temps réel (port 3000) est down,
  // on arrête de poller après quelques échecs pour ne pas inonder la console.
  const failuresRef = useRef(0);
  const refreshCount = useCallback(async () => {
    try {
      setUnread(await notificationService.unreadCount());
      failuresRef.current = 0;
    } catch {
      failuresRef.current += 1;
    }
  }, []);

  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const r = await notificationService.list({ limit: 30 });
      setItems(r.items);
      setUnread(r.items.filter((n) => !n.readAt).length);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial + polling 60s (s'arrête après 3 échecs consécutifs).
  useEffect(() => {
    void refreshCount();
    const t = setInterval(() => {
      if (failuresRef.current >= 3) {
        clearInterval(t);
        return;
      }
      void refreshCount();
    }, 60_000);
    return () => clearInterval(t);
  }, [refreshCount]);

  // Realtime push
  useEffect(() => {
    const s = getSocket();
    if (!s) return;
    const onPush = (n: Notification) => {
      setItems((prev) => [n, ...prev.filter((x) => x.id !== n.id)].slice(0, 30));
      setUnread((c) => c + 1);
    };
    s.on('notification:received', onPush);
    return () => {
      s.off('notification:received', onPush);
    };
  }, []);

  // Click-outside pour fermer le panel
  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  // Charge la liste a l'ouverture
  useEffect(() => {
    if (open) void loadList();
  }, [open, loadList]);

  async function handleItemClick(n: Notification) {
    if (!n.readAt) {
      try {
        await notificationService.markRead(n.id);
      } catch {
        /* ignore */
      }
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, readAt: new Date().toISOString() } : x)));
      setUnread((c) => Math.max(0, c - 1));
    }
    if (n.actionUrl) {
      setOpen(false);
      navigate(n.actionUrl);
    }
  }

  async function handleMarkAll() {
    try {
      await notificationService.markAllRead();
      const now = new Date().toISOString();
      setItems((prev) => prev.map((x) => (x.readAt ? x : { ...x, readAt: now })));
      setUnread(0);
    } catch {
      /* ignore */
    }
  }

  const headerLabel = useMemo(
    () => (unread > 0 ? `${unread} non lue${unread > 1 ? 's' : ''}` : 'Tout est lu'),
    [unread],
  );

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        className="relative p-2 text-fg-subtle transition hover:text-fg"
        aria-label={`Notifications${unread > 0 ? ` (${unread} non lues)` : ''}`}
        title="Notifications"
        onClick={() => setOpen((v) => !v)}
      >
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span
            data-testid="notification-badge"
            className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[9px] font-bold text-fg"
          >
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-96 overflow-hidden rounded-lg border border-border-hi bg-bg-overlay shadow-card-lifted">
          <div className="flex items-center justify-between border-b border-border-hi px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-fg">Notifications</p>
              <p className="text-[10px] uppercase tracking-wide text-fg-subtle">
                {headerLabel}
              </p>
            </div>
            {unread > 0 && (
              <button
                type="button"
                onClick={handleMarkAll}
                className="flex items-center gap-1 rounded px-2 py-1 text-xs text-fg-muted transition hover:bg-bg-raised hover:text-fg"
                title="Tout marquer comme lu"
              >
                <CheckCheck className="h-3.5 w-3.5" /> Tout lu
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {loading && items.length === 0 ? (
              <div className="flex h-32 items-center justify-center">
                <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
              </div>
            ) : items.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-10 text-center text-fg-subtle">
                <Inbox className="h-8 w-8" />
                <p className="text-sm">Aucune notification.</p>
              </div>
            ) : (
              <ul className="divide-y divide-border-hi">
                {items.map((n) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      onClick={() => void handleItemClick(n)}
                      className={`flex w-full flex-col items-start gap-1 px-4 py-3 text-left transition hover:bg-bg-raised ${
                        n.readAt ? '' : 'bg-accent/5'
                      }`}
                      data-testid="notification-item"
                    >
                      <div className="flex w-full items-start justify-between gap-2">
                        <span className="text-sm font-medium text-fg">{n.title}</span>
                        {!n.readAt && (
                          <span className="mt-1 h-2 w-2 flex-shrink-0 rounded-full bg-accent" />
                        )}
                      </div>
                      <span className="line-clamp-2 text-xs text-fg-muted">{n.message}</span>
                      <span className="text-[10px] uppercase tracking-wide text-fg-subtle">
                        {NOTIFICATION_TYPE_LABELS[n.type] ?? n.type} — {formatRelative(n.createdAt)}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
