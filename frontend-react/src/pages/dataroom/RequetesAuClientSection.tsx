import { useCallback, useEffect, useMemo, useState } from 'react';
import { Loader2, Plus, CheckCircle2, RotateCcw, X, Send } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { PromptDialog } from '../../components/ui/PromptDialog';
import { requiredMsg } from '../../lib/formValidation';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { formatDateTime } from '../../lib/date';
import type { RequeteSummary, TypeRequete } from '../../types/dataroom';
import { REQUETE_STATUT_LABELS, TYPE_REQUETE_LABELS } from '../../types/dataroom';
import type { Role } from '../../types/auth';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';

/**
 * Lot AG — Section EMPLOYE « Mes requêtes au client » (direction EMPLOYE_TO_CLIENT),
 * dans l'onglet Demandes du Data Room. L'employé responsable (ou superviseur) crée
 * une requête (PIECE/INFO/SIGNATURE), suit son état, puis VALIDE (REPONDUE→CLOTUREE)
 * ou demande un COMPLÉMENT (REPONDUE→A_COMPLETER). C'est le client qui « répond ».
 */
const STATUT_BADGE: Record<string, string> = {
  OUVERTE: 'bg-amber-100 text-amber-700',
  A_COMPLETER: 'bg-amber-100 text-amber-700',
  REPONDUE: 'bg-accent/15 text-accent',
  CLOTUREE: 'bg-emerald-100 text-emerald-700',
};

const TYPE_OPTIONS: TypeRequete[] = ['PIECE', 'INFO', 'SIGNATURE'];

export function RequetesAuClientSection({
  dossierId,
  role,
  readOnly = false,
  readOnlyStatut = null,
}: {
  dossierId: string;
  role: Role | null;
  /**
   * Lot DIVERS §A (2026-08-13) — societe archivee : plus de NOUVELLE requete au
   * client. Le suivi des requetes deja ouvertes (valider / demander un
   * complement) reste possible : on ne fige que la creation.
   */
  readOnly?: boolean;
  readOnlyStatut?: string | null;
}) {
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newOpen, setNewOpen] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  // Requête ciblée par le dialog « Complément » (remplace window.prompt).
  const [complementId, setComplementId] = useState<string | null>(null);

  const canManage = role === 'EMPLOYE' || role === 'SUPERVISEUR';

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRequetes(await dataroomService.listRequetesByDossier(dossierId));
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId]);

  useEffect(() => {
    void load();
  }, [load]);

  const sorted = useMemo(
    () => [...requetes].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [requetes],
  );

  const valider = async (id: string) => {
    setBusyId(id);
    try {
      await dataroomService.validerRequete(id);
      await load();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  // La saisie du complément passe désormais par un PromptDialog stylé : la note
  // saisie est validée (non vide) puis transmise à l'action « à compléter ».
  const submitComplement = async (note: string) => {
    const id = complementId;
    if (!id) return;
    setComplementId(null);
    setBusyId(id);
    try {
      await dataroomService.complementRequete(id, { note: note.trim() });
      await load();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  if (!canManage) return null;

  return (
    <section className="mt-8">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-fg">Mes requêtes au client</h3>
        {readOnly ? (
          <DataroomReadOnlyHint
            statut={readOnlyStatut}
            testId="requetes-readonly-hint"
          />
        ) : (
          <Button size="sm" onClick={() => setNewOpen(true)}>
            <Plus className="mr-1 h-4 w-4" /> Nouvelle requête
          </Button>
        )}
      </div>

      {error && <p className="mb-2 text-xs text-danger">{error}</p>}

      {loading ? (
        <div className="flex h-16 items-center justify-center">
          <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
        </div>
      ) : sorted.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border bg-bg-raised p-4 text-center text-xs text-fg-subtle">
          Aucune requête envoyée à ce client.
        </p>
      ) : (
        <ul className="space-y-2">
          {sorted.map((r) => (
            <li key={r.id} className="rounded-lg border border-border bg-bg-raised p-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="mb-1 flex items-center gap-2">
                    <span className="rounded bg-bg-overlay px-2 py-0.5 text-[10px] font-semibold text-fg-muted">
                      {r.typeRequete ? TYPE_REQUETE_LABELS[r.typeRequete] : 'Requête'}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${STATUT_BADGE[r.statut] ?? ''}`}>
                      {REQUETE_STATUT_LABELS[r.statut]}
                    </span>
                  </div>
                  <p className="truncate text-sm font-medium text-fg">{r.sujet}</p>
                  {r.noteClient && r.statut !== 'CLOTUREE' && (
                    <p className="mt-1 rounded bg-emerald-50 p-2 text-xs text-emerald-800">
                      Réponse du client : {r.noteClient}
                    </p>
                  )}
                  <p className="mt-1 text-[10px] text-fg-subtle">
                    Envoyée le {formatDateTime(r.createdAt)}
                    {r.reponduAt ? ` · répondue le ${formatDateTime(r.reponduAt)}` : ''}
                    {r.clotureAt ? ` · clôturée le ${formatDateTime(r.clotureAt)}` : ''}
                  </p>
                </div>
                {r.statut === 'REPONDUE' && (
                  <div className="flex flex-shrink-0 flex-col gap-1.5">
                    <button
                      type="button"
                      disabled={busyId === r.id}
                      onClick={() => valider(r.id)}
                      className="inline-flex items-center gap-1 rounded-lg border border-emerald-300 bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 transition hover:bg-emerald-100 disabled:opacity-60"
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" /> Valider
                    </button>
                    <button
                      type="button"
                      disabled={busyId === r.id}
                      onClick={() => setComplementId(r.id)}
                      className="inline-flex items-center gap-1 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 transition hover:bg-amber-100 disabled:opacity-60"
                    >
                      <RotateCcw className="h-3.5 w-3.5" /> Complément
                    </button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {newOpen && (
        <NouvelleRequeteModal
          dossierId={dossierId}
          onClose={() => setNewOpen(false)}
          onDone={() => {
            setNewOpen(false);
            void load();
          }}
        />
      )}

      <PromptDialog
        open={complementId !== null}
        onOpenChange={(o) => {
          if (!o) setComplementId(null);
        }}
        title="Demander un complément"
        label="Que manque-t-il ? (précisez le complément demandé au client)"
        multiline
        confirmLabel="Demander le complément"
        loading={busyId != null && busyId === complementId}
        validate={(v) => requiredMsg(v)}
        onConfirm={(note) => void submitComplement(note)}
      />
    </section>
  );
}

function NouvelleRequeteModal({
  dossierId,
  onClose,
  onDone,
}: {
  dossierId: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const [typeRequete, setTypeRequete] = useState<TypeRequete>('PIECE');
  const [sujet, setSujet] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!sujet.trim()) {
      setError('Le sujet est obligatoire.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await dataroomService.createRequete({
        dossierId,
        typeRequete,
        sujet: sujet.trim(),
        description: description.trim() || undefined,
      });
      onDone();
    } catch (err) {
      setError(extractError(err).message);
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="w-full max-w-md rounded-xl bg-bg-raised p-5 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-base font-semibold text-fg">Nouvelle requête au client</h3>
          <button type="button" onClick={onClose} className="text-fg-subtle hover:text-fg">
            <X className="h-5 w-5" />
          </button>
        </div>

        <label className="mb-1 block text-xs font-medium text-fg-subtle">Type</label>
        <div className="mb-3 flex gap-2">
          {TYPE_OPTIONS.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTypeRequete(t)}
              className={`flex-1 rounded-lg border px-2 py-1.5 text-xs font-medium transition ${
                typeRequete === t
                  ? 'border-accent bg-accent/10 text-accent'
                  : 'border-border text-fg-subtle hover:bg-bg-overlay'
              }`}
            >
              {TYPE_REQUETE_LABELS[t]}
            </button>
          ))}
        </div>

        <label className="mb-1 block text-xs font-medium text-fg-subtle">Sujet</label>
        <input
          value={sujet}
          onChange={(e) => setSujet(e.target.value)}
          maxLength={200}
          className="mb-3 w-full rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
          placeholder="Ex. : Fournir le contrat de bail signé"
        />

        <label className="mb-1 block text-xs font-medium text-fg-subtle">Description (optionnel)</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          className="w-full resize-none rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
          placeholder="Précisez ce que le client doit fournir / répondre…"
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
            Envoyer la requête
          </button>
        </div>
      </div>
    </div>
  );
}
