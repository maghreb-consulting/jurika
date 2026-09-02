import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, Inbox, CheckCircle2, Clock, Send, FileUp, X } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import { formatDateTime } from '../../lib/date';
import type { DossierBrief, RequeteSummary, RequeteStatut } from '../../types/dataroom';
import { REQUETE_STATUT_LABELS, TYPE_REQUETE_LABELS } from '../../types/dataroom';

/**
 * Lot AG — Ecran CLIENT « Demandes de mon conseiller » (requetes EMPLOYE_TO_CLIENT).
 *
 * <p>3 colonnes : « A faire » (OUVERTE + A_COMPLETER) / « En attente de validation »
 * (REPONDUE) / « Termine » (CLOTUREE). Le client repond/fournit -> REPONDUE. C'est
 * l'employe qui valide (CLOTUREE) ou demande un complement (A_COMPLETER).
 * Ecran SEPARE de « Mes demandes » (client -> employe), qui reste inchange.
 */
type Column = { key: 'A_FAIRE' | 'ATTENTE' | 'TERMINE'; label: string; match: RequeteStatut[]; accent: string };

const COLUMNS: Column[] = [
  { key: 'A_FAIRE', label: 'À faire', match: ['OUVERTE', 'A_COMPLETER'], accent: 'border-amber-500' },
  { key: 'ATTENTE', label: 'En attente de validation', match: ['REPONDUE'], accent: 'border-accent' },
  { key: 'TERMINE', label: 'Terminé', match: ['CLOTUREE'], accent: 'border-emerald-500' },
];

export function MesRequetes() {
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState<RequeteSummary | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const dossiers: DossierBrief[] = await dataroomService.listDossiers().catch(() => []);
      const lists = await Promise.all(
        dossiers.map((d) => dataroomService.listRequetesByDossier(d.id).catch(() => [] as RequeteSummary[])),
      );
      setRequetes(lists.flat());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const byColumn = useMemo(() => {
    const map: Record<string, RequeteSummary[]> = { A_FAIRE: [], ATTENTE: [], TERMINE: [] };
    for (const r of requetes) {
      const col = COLUMNS.find((c) => c.match.includes(r.statut));
      if (col) map[col.key].push(r);
    }
    for (const k of Object.keys(map)) {
      map[k].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    }
    return map;
  }, [requetes]);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-fg-subtle" />
      </div>
    );
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h2 className="font-heading text-2xl font-semibold text-fg">Demandes de mon conseiller</h2>
        <p className="text-sm text-fg-subtle">
          Les requêtes de votre conseiller. Répondez ou fournissez ce qui est demandé ; il valide ensuite.
        </p>
      </div>

      {requetes.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border bg-bg-raised py-16 text-center">
          <Inbox className="mb-3 h-10 w-10 text-fg-subtle" />
          <p className="text-sm text-fg-muted">Aucune requête pour le moment.</p>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-3">
          {COLUMNS.map((col) => (
            <div key={col.key}>
              <div className={`mb-3 rounded-t-lg border-t-4 bg-bg-overlay px-3 py-2 ${col.accent}`}>
                <h3 className="flex items-center justify-between text-xs font-semibold uppercase tracking-wide text-fg-subtle">
                  {col.label}
                  <span className="rounded-full bg-bg-raised px-2 py-0.5 text-[10px] font-bold text-fg-muted">
                    {byColumn[col.key].length}
                  </span>
                </h3>
              </div>
              <div className="space-y-3">
                {byColumn[col.key].length === 0 && (
                  <div className="rounded-lg border border-dashed border-border bg-bg-raised p-4 text-center text-xs text-fg-subtle">
                    Rien ici
                  </div>
                )}
                {byColumn[col.key].map((r) => (
                  <RequeteCard key={r.id} r={r} onOpen={() => setActive(r)} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {active && (
        <RepondreModal
          requete={active}
          onClose={() => setActive(null)}
          onDone={() => {
            setActive(null);
            void load();
          }}
        />
      )}
    </div>
  );
}

function RequeteCard({ r, onOpen }: { r: RequeteSummary; onOpen: () => void }) {
  const actionnable = r.statut === 'OUVERTE' || r.statut === 'A_COMPLETER';
  return (
    <div className="rounded-lg border border-border bg-bg-raised p-4 shadow-sm">
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="rounded bg-accent/10 px-2 py-0.5 text-[10px] font-semibold text-accent">
          {r.typeRequete ? TYPE_REQUETE_LABELS[r.typeRequete] : 'Requête'}
        </span>
        <span className="text-[10px] text-fg-subtle">
          Envoyée {formatDateTime(r.createdAt)}
        </span>
      </div>
      <p className="text-sm font-semibold text-fg">{r.sujet}</p>
      {r.description && <p className="mt-1 line-clamp-3 text-xs text-fg-subtle">{r.description}</p>}
      {r.statut === 'A_COMPLETER' && r.noteInterne && (
        <p className="mt-2 rounded bg-amber-50 p-2 text-xs text-amber-800">
          Complément demandé : {r.noteInterne}
        </p>
      )}
      {r.statut === 'REPONDUE' && (
        <p className="mt-2 flex flex-wrap items-center gap-1 text-xs text-fg-subtle">
          <Clock className="h-3.5 w-3.5" /> En attente de validation
          {r.reponduAt && <span>· répondue le {formatDateTime(r.reponduAt)}</span>}
        </p>
      )}
      {r.statut === 'CLOTUREE' && (
        <p className="mt-2 flex flex-wrap items-center gap-1 text-xs text-emerald-600">
          <CheckCircle2 className="h-3.5 w-3.5" /> Terminé
          {r.clotureAt && <span>le {formatDateTime(r.clotureAt)}</span>}
        </p>
      )}
      {actionnable && (
        <button
          type="button"
          onClick={onOpen}
          className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-1.5 text-xs font-semibold text-bg transition hover:bg-accent-hover"
        >
          <Send className="h-3.5 w-3.5" /> Répondre / Fournir
        </button>
      )}
    </div>
  );
}

function RepondreModal({
  requete,
  onClose,
  onDone,
}: {
  requete: RequeteSummary;
  onClose: () => void;
  onDone: () => void;
}) {
  const navigate = useNavigate();
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const estPiece = requete.typeRequete === 'PIECE';

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await dataroomService.repondreRequete(requete.id, { noteClient: note.trim() || undefined });
      onDone();
    } catch {
      setError("L'envoi de votre réponse a échoué. Réessayez.");
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div
        className="w-full max-w-md rounded-xl bg-bg-raised p-5 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-start justify-between">
          <div>
            <h3 className="text-base font-semibold text-fg">Répondre à la requête</h3>
            <p className="text-xs text-fg-subtle">{requete.sujet}</p>
          </div>
          <button type="button" onClick={onClose} className="text-fg-subtle hover:text-fg">
            <X className="h-5 w-5" />
          </button>
        </div>

        {estPiece && (
          <div className="mb-3 rounded-lg bg-accent/5 p-3 text-xs text-fg-muted">
            <p className="flex items-start gap-2">
              <FileUp className="mt-0.5 h-4 w-4 flex-shrink-0 text-accent" />
              Pièce à fournir : déposez d'abord le document dans votre espace « Dépôts », puis
              confirmez ci-dessous. Votre conseiller validera ensuite.
            </p>
            <button
              type="button"
              onClick={() => navigate('/data-rooms?tab=depots')}
              className="mt-2 inline-flex items-center gap-1.5 rounded-lg border border-accent/40 bg-accent/10 px-2.5 py-1 text-xs font-semibold text-accent transition hover:bg-accent/20"
            >
              <FileUp className="h-3.5 w-3.5" /> Aller à mes Dépôts
            </button>
          </div>
        )}

        <label className="mb-1 block text-xs font-medium text-fg-subtle">
          {estPiece ? 'Message (optionnel)' : 'Votre réponse'}
        </label>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={4}
          placeholder={estPiece ? 'Ex. : document déposé dans mes Dépôts.' : 'Votre réponse…'}
          className="w-full resize-none rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
        />

        {error && <p className="mt-2 text-xs text-danger">{error}</p>}

        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-border px-3 py-1.5 text-sm text-fg-subtle transition hover:bg-bg-overlay"
          >
            Annuler
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-bg transition hover:bg-accent-hover disabled:opacity-60"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            Envoyer
          </button>
        </div>
      </div>
    </div>
  );
}

/** Libelle statut (reutilise le mapping partage). */
export function requeteStatutLabel(s: RequeteStatut): string {
  return REQUETE_STATUT_LABELS[s];
}
