import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle, Clock, MessageSquare, Plus, Send } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Message } from '../../components/ui/Message';
import { Modal } from '../../components/ui/Modal';
import { Select } from '../../components/ui/Select';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { CardSkeleton, Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Page } from '../../components/layout/Page';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { requiredMsg } from '../../lib/formValidation';
import { formatDateTime } from '../../lib/date';
import type {
  DemandeStatut,
  DemandeSummary,
  DossierBrief,
} from '../../types/dataroom';

// Memes libelles que l'onglet "Mes demandes" de la Data Room (ClientDataroomView).
const STATUT_LABEL: Record<DemandeStatut, string> = {
  NON_TRAITEE: 'Non traitée',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitée',
};

/** Lot L6b (E7) : badges de la bibliotheque ; le libelle dit l'etat, l'icone le double. */
const STATUT_BADGE: Record<DemandeStatut, BadgeVariant> = {
  NON_TRAITEE: 'warning',
  EN_COURS: 'info',
  TRAITEE: 'success',
};

const COLUMNS: { statut: DemandeStatut; accent: string }[] = [
  { statut: 'NON_TRAITEE', accent: 'border-l-warning' },
  { statut: 'EN_COURS', accent: 'border-l-info' },
  { statut: 'TRAITEE', accent: 'border-l-success' },
];

const EMPTY_GROUPS: Record<DemandeStatut, DemandeSummary[]> = {
  NON_TRAITEE: [],
  EN_COURS: [],
  TRAITEE: [],
};

/**
 * Page CLIENT "Mes demandes" : vue par statut EN LECTURE SEULE (Non traitee / En cours /
 * Traitee). Le client SOUMET des demandes et SUIT leur statut ; il ne change PAS le statut
 * (c'est l'employe, par PATCH).
 *
 * Donnees : listDossiers() (ouvert au CLIENT) puis listDemandesByDossier(d.id) pour chaque
 * dossier, regroupees par statut.
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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
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

  return (
    <Page
      titre="Mes demandes"
      description="Adressez vos demandes au cabinet et suivez leur avancement : « Non traitée » tant que votre conseiller ne l’a pas prise en charge, puis « En cours », puis « Traitée »."
      action={
        !loading && dossiers.length > 0 ? (
          <Button type="button" icon={<Plus />} onClick={() => setShowForm(true)}>
            Nouvelle demande
          </Button>
        ) : undefined
      }
    >
      <div className="space-y-5">
        {error && <Message ton="erreur">{error}</Message>}

        {loading ? (
          <SqueletteZone libelle="Chargement de vos demandes…">
            <div className="grid gap-4 md:grid-cols-3">
              {COLUMNS.map((c) => (
                <div key={c.statut} className="space-y-2">
                  <Skeleton className="h-10 rounded-lg" />
                  <CardSkeleton />
                </div>
              ))}
            </div>
          </SqueletteZone>
        ) : dossiers.length === 0 ? (
          <Card padding={false}>
            <EmptyState
              icon={<MessageSquare />}
              title="Aucune Data Room associée"
              description="Aucune Data Room n’est associée à votre compte. Si votre cabinet vous a invité, contactez votre conseiller."
            />
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            {COLUMNS.map((col) => {
              const items = grouped[col.statut] ?? EMPTY_GROUPS[col.statut];
              return (
                <section key={col.statut} aria-labelledby={`demandes-${col.statut}`} className="flex min-w-0 flex-col gap-2">
                  <div className={`flex items-center justify-between rounded-r-lg border border-border border-l-4 bg-bg-raised px-3 py-2 shadow-card ${col.accent}`}>
                    <h2 id={`demandes-${col.statut}`} className="text-sm font-semibold text-heading">
                      {STATUT_LABEL[col.statut]}
                    </h2>
                    <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-xs font-semibold tabular-nums text-fg-muted">
                      {items.length}
                    </span>
                  </div>
                  <div className="flex min-h-[120px] flex-col gap-2 rounded-lg border border-dashed border-border-hi p-1.5">
                    {items.map((dem) => (
                      <DemandeCard key={dem.id} demande={dem} onClick={() => setSelected(dem)} />
                    ))}
                    {items.length === 0 && (
                      <p className="px-2 py-6 text-center text-xs text-fg-muted">Aucune demande</p>
                    )}
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </div>

      {selected && <DemandeDetailModal demande={selected} onClose={() => setSelected(null)} />}

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
    </Page>
  );
}

function IconeStatut({ statut }: { statut: DemandeStatut }) {
  if (statut === 'NON_TRAITEE') return <Clock className="h-4 w-4 shrink-0 text-warning" aria-hidden="true" />;
  if (statut === 'EN_COURS') return <MessageSquare className="h-4 w-4 shrink-0 text-info" aria-hidden="true" />;
  return <CheckCircle className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />;
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
      className="w-full min-w-0 rounded-lg border border-border bg-bg-raised p-3 text-left shadow-card transition-shadow duration-150 hover:shadow-card-lifted"
    >
      <div className="mb-1 flex min-w-0 items-center gap-2">
        <IconeStatut statut={demande.statut} />
        <h3 className="truncate text-sm font-semibold text-fg">{demande.sujet}</h3>
      </div>
      {demande.description && (
        <p className="line-clamp-2 break-words text-xs text-fg-muted">{demande.description}</p>
      )}
      <div className="mt-2 space-y-0.5 text-[11px] text-fg-muted">
        <p>Envoyée le {formatDateTime(demande.createdAt)}</p>
        {demande.traiteAt && <p className="text-success">Traitée le {formatDateTime(demande.traiteAt)}</p>}
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
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={demande.sujet}
      description={
        <Badge variant={STATUT_BADGE[demande.statut]}>
          {STATUT_LABEL[demande.statut]}
        </Badge>
      }
      size="md"
      footer={
        <div className="flex justify-end">
          <Button type="button" variant="secondary" onClick={onClose}>
            Fermer
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {demande.description ? (
          <p className="whitespace-pre-wrap text-sm text-fg-muted">{demande.description}</p>
        ) : (
          <p className="text-sm italic text-fg-muted">Aucune description fournie.</p>
        )}

        {demande.noteInterne && (
          <Message ton="info" titre="Note du cabinet">
            <p className="whitespace-pre-wrap">{demande.noteInterne}</p>
          </Message>
        )}

        <div className="border-t border-border pt-3 text-xs text-fg-muted">
          <p>Envoyée le {new Date(demande.createdAt).toLocaleDateString('fr-FR')}</p>
          {demande.traiteAt && (
            <p className="mt-0.5">Traitée le {new Date(demande.traiteAt).toLocaleDateString('fr-FR')}</p>
          )}
        </div>
      </div>
    </Modal>
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

  // Logique de creation existante (ClientDemandes) : POST /demandes via dataroomService.createDemande.
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
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Nouvelle demande"
      description="Votre conseiller la reçoit aussitôt et la prend en charge."
      size="md"
      closeOnBackdrop={false}
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button
            type="submit"
            form="formulaire-nouvelle-demande"
            icon={<Send />}
            loading={submitting}
            disabled={!sujet.trim() || !dossierId || submitting}
          >
            Envoyer
          </Button>
        </div>
      }
    >
      <form id="formulaire-nouvelle-demande" onSubmit={submit} noValidate className="space-y-4">
        {dossiers.length > 1 && (
          <Select
            label="Dossier"
            value={dossierId}
            onChange={(e) => setDossierId(e.target.value)}
            options={dossiers.map((d) => ({ value: d.id, label: d.raisonSociale }))}
          />
        )}
        <TextField
          label="Sujet"
          value={sujet}
          onChange={(e) => setSujet(e.target.value)}
          placeholder="Exemple : modification de l’objet social"
          required
          autoFocus
        />
        <ZoneTexte
          label="Description (facultatif)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={4}
          placeholder="Décrivez votre demande en détail…"
        />
        {error && <Message ton="erreur">{error}</Message>}
      </form>
    </Modal>
  );
}
