import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Clock, FileUp, Inbox, Send } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { Modal } from '../../components/ui/Modal';
import { ZoneTexte } from '../../components/ui/TextField';
import { CardSkeleton, Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Page } from '../../components/layout/Page';
import { dataroomService } from '../../services/dataroom.service';
import { formatDateTime } from '../../lib/date';
import type { DossierBrief, RequeteSummary, RequeteStatut } from '../../types/dataroom';
import { TYPE_REQUETE_LABELS } from '../../types/dataroom';

/**
 * Lot AG : ecran CLIENT "Requetes du cabinet" (requetes EMPLOYE_TO_CLIENT).
 *
 * <p>3 colonnes : "A faire" (OUVERTE + A_COMPLETER) / "En attente de validation"
 * (REPONDUE) / "Termine" (CLOTUREE). Le client repond/fournit -> REPONDUE. C'est
 * l'employe qui valide (CLOTUREE) ou demande un complement (A_COMPLETER).
 * Ecran SEPARE de "Mes demandes" (client -> employe).
 *
 * Lot L6b (E7) : structure commune <Page>, composants de la bibliotheque, <dialog>.
 */
type Column = { key: 'A_FAIRE' | 'ATTENTE' | 'TERMINE'; label: string; match: RequeteStatut[]; accent: string };

const COLUMNS: Column[] = [
  { key: 'A_FAIRE', label: 'À faire', match: ['OUVERTE', 'A_COMPLETER'], accent: 'border-l-warning' },
  { key: 'ATTENTE', label: 'En attente de validation', match: ['REPONDUE'], accent: 'border-l-info' },
  { key: 'TERMINE', label: 'Terminé', match: ['CLOTUREE'], accent: 'border-l-success' },
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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
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

  return (
    <Page
      titre="Requêtes du cabinet"
      description="Ce que votre conseiller attend de vous : une pièce à fournir ou une réponse. Répondez à chaque requête « À faire » ; votre conseiller valide ensuite votre réponse."
    >
      {loading ? (
        <SqueletteZone libelle="Chargement des requêtes…">
          <div className="grid gap-4 md:grid-cols-3">
            {COLUMNS.map((c) => (
              <div key={c.key} className="space-y-2">
                <Skeleton className="h-10 rounded-lg" />
                <CardSkeleton />
              </div>
            ))}
          </div>
        </SqueletteZone>
      ) : requetes.length === 0 ? (
        <Card padding={false}>
          <EmptyState
            icon={<Inbox />}
            title="Rien à faire pour l’instant"
            description="Aucune requête pour le moment : votre conseiller n’attend rien de vous."
          />
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-3">
          {COLUMNS.map((col) => (
            <section key={col.key} aria-labelledby={`requetes-${col.key}`} className="flex min-w-0 flex-col gap-2">
              <div className={`flex items-center justify-between rounded-r-lg border border-border border-l-4 bg-bg-raised px-3 py-2 shadow-card ${col.accent}`}>
                <h2 id={`requetes-${col.key}`} className="text-sm font-semibold text-heading">
                  {col.label}
                </h2>
                <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-xs font-semibold tabular-nums text-fg-muted">
                  {byColumn[col.key].length}
                </span>
              </div>
              <div className="flex flex-col gap-2">
                {byColumn[col.key].length === 0 && (
                  <p className="rounded-lg border border-dashed border-border-hi px-2 py-6 text-center text-xs text-fg-muted">
                    Aucune requête
                  </p>
                )}
                {byColumn[col.key].map((r) => (
                  <RequeteCard key={r.id} r={r} onOpen={() => setActive(r)} />
                ))}
              </div>
            </section>
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
    </Page>
  );
}

function RequeteCard({ r, onOpen }: { r: RequeteSummary; onOpen: () => void }) {
  const actionnable = r.statut === 'OUVERTE' || r.statut === 'A_COMPLETER';
  return (
    <div className="min-w-0 rounded-lg border border-border bg-bg-raised p-4 shadow-card">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <Badge variant="neutral">{(r.typeRequete && TYPE_REQUETE_LABELS[r.typeRequete]) || 'Requête'}</Badge>
        <span className="text-[11px] text-fg-muted">Envoyée {formatDateTime(r.createdAt)}</span>
      </div>
      <p className="break-words text-sm font-semibold text-fg">{r.sujet}</p>
      {r.description && <p className="mt-1 line-clamp-3 break-words text-xs text-fg-muted">{r.description}</p>}
      {r.statut === 'A_COMPLETER' && r.noteInterne && (
        <Message ton="attention" className="mt-2">
          Complément demandé : {r.noteInterne}
        </Message>
      )}
      {r.statut === 'REPONDUE' && (
        <p className="mt-2 flex flex-wrap items-center gap-1 text-xs text-fg-muted">
          <Clock className="h-3.5 w-3.5" aria-hidden="true" /> En attente de validation
          {r.reponduAt && <span>· répondue le {formatDateTime(r.reponduAt)}</span>}
        </p>
      )}
      {r.statut === 'CLOTUREE' && (
        <p className="mt-2 flex flex-wrap items-center gap-1 text-xs text-success">
          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" /> Terminé
          {r.clotureAt && <span>le {formatDateTime(r.clotureAt)}</span>}
        </p>
      )}
      {actionnable && (
        <Button type="button" size="sm" className="mt-3" icon={<Send />} onClick={onOpen}>
          Répondre / Fournir
        </Button>
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
      setError('L’envoi de votre réponse a échoué. Réessayez.');
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Répondre à la requête"
      description={requete.sujet}
      size="md"
      closeOnBackdrop={false}
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="button" icon={<Send />} loading={submitting} onClick={submit}>
            Envoyer
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {estPiece && (
          <Message
            ton="info"
            titre="Pièce à fournir"
            action={
              <Lien to="/data-rooms?tab=depots" commeBouton={{ variant: 'secondary', size: 'sm' }}>
                <FileUp className="h-4 w-4" aria-hidden="true" /> Aller à mes dépôts
              </Lien>
            }
          >
            Déposez d’abord le document dans votre Data Room, onglet « Dépôts du client », puis confirmez
            ci-dessous. Votre conseiller validera ensuite.
          </Message>
        )}
        <ZoneTexte
          label={estPiece ? 'Message (facultatif)' : 'Votre réponse'}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={4}
          autoFocus
          placeholder={estPiece ? 'Exemple : document déposé dans mes dépôts.' : 'Votre réponse…'}
        />
        {error && <Message ton="erreur">{error}</Message>}
      </div>
    </Modal>
  );
}
