import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CheckCircle,
  Clock,
  Loader2,
  MessageSquare,
  Plus,
  Send,
  X,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { requiredMsg } from '../../lib/formValidation';
import { formatDateTime } from '../../lib/date';
import type {
  DemandeStatut,
  DemandeSummary,
  DossierBrief,
} from '../../types/dataroom';

// Reprend STATUT_LABEL / STATUT_PILL de ClientDataroomView (non exportes la-bas)
// pour rester coherent avec l'onglet "Mes Demandes" de la Data Room.
const STATUT_LABEL: Record<DemandeStatut, string> = {
  NON_TRAITEE: 'Non traitee',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitee',
};

const STATUT_PILL: Record<DemandeStatut, { bg: string; text: string }> = {
  NON_TRAITEE: { bg: 'bg-danger', text: 'text-bg-raised' },
  EN_COURS: { bg: 'bg-accent', text: 'text-bg-raised' },
  TRAITEE: { bg: 'bg-success', text: 'text-bg-raised' },
};

// Layout des colonnes calque sur KanbanView.tsx (bandeau border-l-4 colore +
// pastille de compteur), SANS importer KanbanView (couple aux tickets).
const COLUMNS: { statut: DemandeStatut; accent: string }[] = [
  { statut: 'NON_TRAITEE', accent: 'border-danger' },
  { statut: 'EN_COURS', accent: 'border-accent' },
  { statut: 'TRAITEE', accent: 'border-success' },
];

const EMPTY_GROUPS: Record<DemandeStatut, DemandeSummary[]> = {
  NON_TRAITEE: [],
  EN_COURS: [],
  TRAITEE: [],
};

/**
 * Page CLIENT "Mes demandes" — vue Kanban LECTURE SEULE (3 colonnes : Non
 * traitee / En cours / Traitee). Le client SOUMET des demandes et SUIT leur
 * statut ; il ne change PAS le statut (c'est l'employe via PATCH).
 *
 * Donnees : listDossiers() (ouvert au CLIENT) -> listDemandesByDossier(d.id)
 * pour chaque dossier -> flatten -> groupe par statut (meme pattern que
 * ClientDashboard).
 */
export function DemandesKanban() {
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<DemandeSummary | null>(null);
  const [showForm, setShowForm] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await dataroomService.listDossiers();
      setDossiers(list);
      const perDossier = await Promise.all(
        list.map((d) =>
          dataroomService
            .listDemandesByDossier(d.id)
            .catch(() => [] as DemandeSummary[]),
        ),
      );
      setDemandes(perDossier.flat());
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const grouped = useMemo(() => {
    const acc: Record<DemandeStatut, DemandeSummary[]> = {
      NON_TRAITEE: [],
      EN_COURS: [],
      TRAITEE: [],
    };
    for (const d of demandes) {
      (acc[d.statut] ?? acc.NON_TRAITEE).push(d);
    }
    // tri decroissant par date dans chaque colonne (plus recentes en haut)
    for (const k of Object.keys(acc) as DemandeStatut[]) {
      acc[k].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
    }
    return acc;
  }, [demandes]);

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-xs text-fg-subtle">Espace client securise</p>
          <h1 className="font-heading text-2xl font-semibold text-fg">
            Mes demandes
          </h1>
          <p className="text-sm text-fg-subtle">
            Soumettez vos demandes au cabinet et suivez leur avancement.
          </p>
        </div>
        <Button
          variant="primary"
          onClick={() => setShowForm(true)}
          disabled={dossiers.length === 0}
          className="bg-accent hover:bg-accent-hover"
        >
          <Plus className="mr-2 h-4 w-4" /> Nouvelle demande
        </Button>
      </header>

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
          {error}
        </div>
      )}

      {dossiers.length === 0 ? (
        <div className="rounded-2xl border border-border bg-bg-raised p-12 text-center">
          <MessageSquare className="mx-auto mb-3 h-10 w-10 text-fg-subtle" />
          <p className="text-sm text-fg-subtle">
            Aucun Data Room n'est associe a votre compte.
          </p>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-3">
          {COLUMNS.map((col) => {
            const items = grouped[col.statut] ?? EMPTY_GROUPS[col.statut];
            return (
              <div key={col.statut} className="flex flex-col gap-3">
                <div
                  className={`flex items-center justify-between rounded-r-lg border-l-4 ${col.accent} bg-bg-overlay px-3 py-2`}
                >
                  <span className="font-heading text-sm font-semibold text-fg">
                    {STATUT_LABEL[col.statut]}
                  </span>
                  <span className="rounded-full bg-bg-raised px-2 py-0.5 text-xs font-bold text-fg-muted">
                    {items.length}
                  </span>
                </div>
                <div className="flex min-h-[120px] flex-col gap-2 rounded-lg border border-dashed border-border-hi/40 p-2">
                  {items.map((dem) => (
                    <DemandeCard
                      key={dem.id}
                      demande={dem}
                      onClick={() => setSelected(dem)}
                    />
                  ))}
                  {items.length === 0 && (
                    <div className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-fg-subtle">
                      Aucune demande
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {selected && (
        <DemandeDetailModal
          demande={selected}
          onClose={() => setSelected(null)}
        />
      )}

      {showForm && dossiers.length > 0 && (
        <NouvelleDemandeModal
          dossiers={dossiers}
          onClose={() => setShowForm(false)}
          onCreated={async () => {
            setShowForm(false);
            await load();
          }}
        />
      )}
    </div>
  );
}

function DemandeCard({
  demande,
  onClick,
}: {
  demande: DemandeSummary;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full rounded-lg border border-border bg-bg-raised p-3 text-left transition hover:border-accent hover:shadow-sm"
    >
      <div className="mb-1 flex items-center gap-2">
        {demande.statut === 'NON_TRAITEE' && (
          <Clock className="h-4 w-4 flex-shrink-0 text-danger" />
        )}
        {demande.statut === 'EN_COURS' && (
          <MessageSquare className="h-4 w-4 flex-shrink-0 text-accent" />
        )}
        {demande.statut === 'TRAITEE' && (
          <CheckCircle className="h-4 w-4 flex-shrink-0 text-success" />
        )}
        <h4 className="truncate text-sm font-bold text-fg">{demande.sujet}</h4>
      </div>
      {demande.description && (
        <p className="line-clamp-2 text-xs text-fg-subtle">
          {demande.description}
        </p>
      )}
      <div className="mt-2 space-y-0.5 text-[10px] text-fg-subtle">
        <p>Envoyée le {formatDateTime(demande.createdAt)}</p>
        {demande.traiteAt && (
          <p className="text-success">Traitée le {formatDateTime(demande.traiteAt)}</p>
        )}
      </div>
    </button>
  );
}

function DemandeDetailModal({
  demande,
  onClose,
}: {
  demande: DemandeSummary;
  onClose: () => void;
}) {
  const st = STATUT_PILL[demande.statut];
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-2xl border border-border bg-bg-raised p-6 shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="flex items-center gap-2">
            <span
              className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${st.bg} ${st.text}`}
            >
              {STATUT_LABEL[demande.statut]}
            </span>
            <span className="font-mono text-[10px] text-fg-subtle">
              {demande.id.slice(0, 8)}
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-fg-subtle transition hover:bg-bg-overlay hover:text-fg"
            aria-label="Fermer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <h3 className="mb-2 font-heading text-lg font-semibold text-fg">
          {demande.sujet}
        </h3>

        {demande.description ? (
          <p className="whitespace-pre-wrap text-sm text-fg-muted">
            {demande.description}
          </p>
        ) : (
          <p className="text-sm italic text-fg-subtle">
            Aucune description fournie.
          </p>
        )}

        {demande.noteInterne && (
          <div className="mt-4 rounded-lg border border-accent/30 bg-accent/10 p-3">
            <p className="text-xs font-semibold text-accent">
              Note du cabinet
            </p>
            <p className="mt-1 whitespace-pre-wrap text-sm text-fg-muted">
              {demande.noteInterne}
            </p>
          </div>
        )}

        <div className="mt-5 border-t border-border pt-4 text-xs text-fg-subtle">
          <p>
            Envoyee le{' '}
            {new Date(demande.createdAt).toLocaleDateString('fr-FR')}
          </p>
          {demande.traiteAt && (
            <p className="mt-0.5">
              Traitee le{' '}
              {new Date(demande.traiteAt).toLocaleDateString('fr-FR')}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

function NouvelleDemandeModal({
  dossiers,
  onClose,
  onCreated,
}: {
  dossiers: DossierBrief[];
  onClose: () => void;
  onCreated: () => Promise<void> | void;
}) {
  const [dossierId, setDossierId] = useState(dossiers[0]?.id ?? '');
  const [sujet, setSujet] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reutilise la logique de creation existante (ClientDemandes) :
  // POST /demandes via dataroomService.createDemande.
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const sujetErr = requiredMsg(sujet, 'Le sujet est requis.');
    if (sujetErr) {
      setError(sujetErr);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await dataroomService.createDemande({
        dossierId,
        sujet: sujet.trim(),
        description: description.trim() || undefined,
      });
      await onCreated();
    } catch (err) {
      setError(extractError(err).message);
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-2xl border border-border bg-bg-raised p-6 shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="flex items-center gap-2 font-heading text-lg font-semibold text-fg">
            <Plus className="h-4 w-4 text-accent" /> Nouvelle demande
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-fg-subtle transition hover:bg-bg-overlay hover:text-fg"
            aria-label="Fermer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={submit} noValidate className="space-y-4">
          {dossiers.length > 1 && (
            <div>
              <label className="mb-1.5 block text-xs font-medium text-fg">
                Dossier
              </label>
              <select
                value={dossierId}
                onChange={(e) => setDossierId(e.target.value)}
                className="h-10 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-[#7C3AED]"
              >
                {dossiers.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.raisonSociale}
                  </option>
                ))}
              </select>
            </div>
          )}
          <div>
            <label className="mb-1.5 block text-xs font-medium text-fg">
              Sujet
            </label>
            <input
              type="text"
              value={sujet}
              onChange={(e) => setSujet(e.target.value)}
              placeholder="Ex : Modification de l'objet social"
              className="h-10 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-[#7C3AED]"
              required
            />
          </div>
          <div>
            <label className="mb-1.5 block text-xs font-medium text-fg">
              Description
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              placeholder="Decrivez votre demande en detail..."
              className="w-full resize-none rounded-lg border border-border bg-bg-raised px-3 py-2.5 text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-[#7C3AED]"
            />
          </div>
          {error && (
            <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
              {error}
            </div>
          )}
          <div className="flex items-center gap-3">
            <button
              type="submit"
              disabled={!sujet.trim() || !dossierId || submitting}
              className={`flex items-center gap-2 rounded-lg px-6 h-10 text-sm font-medium transition ${
                sujet.trim() && dossierId && !submitting
                  ? 'bg-accent text-bg-raised hover:bg-accent-hover'
                  : 'cursor-not-allowed bg-border text-fg-subtle'
              }`}
            >
              <Send className="h-3.5 w-3.5" /> Envoyer
            </button>
            <button
              type="button"
              onClick={onClose}
              className="px-4 h-10 text-sm text-fg-subtle transition hover:text-fg"
            >
              Annuler
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
