import { useState } from 'react';
import { MessageCircle, Send, X } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';

interface Remark {
  id: string;
  employeeName: string;
  text: string;
  ticketRef?: string;
  createdAt: string;
}

// STUB DATA — API a brancher : GET/POST /api/v1/supervision/remarks
const SEED_REMARKS: Remark[] = [
  {
    id: 'rk-1',
    employeeName: 'Karim El Idrissi',
    text: 'Verifier le ticket TK-042 bloque depuis 48h — relancer le client pour les documents manquants.',
    ticketRef: 'TK-042',
    createdAt: '2026-05-13T14:20:00Z',
  },
  {
    id: 'rk-2',
    employeeName: 'Sara Bensouda',
    text: 'S. IDRISSI a 2 retards ce mois — planifier un point individuel.',
    createdAt: '2026-05-13T11:05:00Z',
  },
  {
    id: 'rk-3',
    employeeName: 'Younes Tahiri',
    text: "Lien d'acces Data Room expire pour comptable@benali.ma — a renouveler.",
    createdAt: '2026-05-12T16:45:00Z',
  },
];

/**
 * Floating annotations panel — visible only for SUPERVISEUR role.
 * Collapsible right-side bubble + panel. Stub data, ready to wire to API.
 * TODO: API a brancher : /api/v1/supervision/remarks
 */
export function SupervisorRemarks() {
  const user = useAuthStore((s) => s.user);
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [remarks, setRemarks] = useState<Remark[]>(SEED_REMARKS);

  if (!user || user.role !== 'SUPERVISEUR') return null;

  function addRemark() {
    const text = input.trim();
    if (!text) return;
    setRemarks((rs) => [
      {
        id: `rk-${Date.now()}`,
        employeeName: 'Equipe',
        text,
        createdAt: new Date().toISOString(),
      },
      ...rs,
    ]);
    setInput('');
  }

  return (
    <>
      {/* Floating button */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={`fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full shadow-2xl transition-all ${
          open ? 'bg-danger hover:bg-danger/85' : 'bg-accent hover:bg-accent-hover'
        }`}
        aria-label="Remarques superviseur"
      >
        {open ? (
          <X className="h-6 w-6 text-bg-raised" />
        ) : (
          <MessageCircle className="h-6 w-6 text-bg-raised" />
        )}
        {!open && remarks.length > 0 && (
          <span className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-danger text-[10px] font-bold text-bg-raised">
            {remarks.length}
          </span>
        )}
      </button>

      {/* Panel */}
      {open && (
        <div className="fixed bottom-24 right-6 z-50 flex max-h-[520px] w-[380px] flex-col overflow-hidden rounded-2xl border border-border bg-bg-raised shadow-2xl">
          {/* Header */}
          <div className="rounded-t-2xl bg-accent px-4 py-3 text-bg-raised">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MessageCircle className="h-4 w-4" />
                <span className="text-sm font-bold">Remarques superviseur</span>
              </div>
              <span className="rounded-full bg-bg-raised/20 px-2 py-0.5 text-xs">
                {remarks.length} notes
              </span>
            </div>
          </div>

          {/* Input */}
          <div className="border-b border-border p-3">
            <div className="flex gap-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') addRemark();
                }}
                placeholder="Ajouter une remarque..."
                className="h-9 flex-1 rounded-lg border border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
              />
              <button
                type="button"
                onClick={addRemark}
                disabled={!input.trim()}
                className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent text-bg-raised transition hover:bg-accent-hover disabled:opacity-40"
                aria-label="Envoyer"
              >
                <Send className="h-4 w-4" />
              </button>
            </div>
            <p className="mt-1 text-[10px] text-fg-subtle">
              API a brancher : /api/v1/supervision/remarks
            </p>
          </div>

          {/* List */}
          <div className="flex-1 overflow-y-auto p-3">
            {remarks.length === 0 ? (
              <div className="p-6 text-center">
                <MessageCircle className="mx-auto mb-2 h-8 w-8 text-fg-subtle" />
                <p className="text-xs text-fg-subtle">Aucune remarque.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {remarks.map((r) => (
                  <div
                    key={r.id}
                    className="rounded-lg border border-accent/10 bg-accent/10 p-3"
                  >
                    <div className="mb-1 flex items-center justify-between">
                      <span className="text-xs font-semibold text-accent">
                        {r.employeeName}
                      </span>
                      <span className="text-[10px] text-fg-subtle">
                        {formatTime(r.createdAt)}
                      </span>
                    </div>
                    <p className="text-xs leading-relaxed text-fg">{r.text}</p>
                    {r.ticketRef && (
                      <p className="mt-1 inline-block rounded bg-bg-raised px-2 py-0.5 font-mono text-[10px] text-accent">
                        {r.ticketRef}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}

function formatTime(iso: string): string {
  try {
    const date = new Date(iso);
    const today = new Date();
    const sameDay =
      date.getFullYear() === today.getFullYear() &&
      date.getMonth() === today.getMonth() &&
      date.getDate() === today.getDate();
    if (sameDay) {
      return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  } catch {
    return iso;
  }
}
