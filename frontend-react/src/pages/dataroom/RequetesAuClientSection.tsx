import { useCallback, useEffect, useMemo, useState } from 'react';
import { Plus, CheckCircle2, RotateCcw, Send } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { Modal } from '../../components/ui/Modal';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
import { GroupeRadio } from '../../components/ui/Choix';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { PromptDialog } from '../../components/ui/PromptDialog';
import { requiredMsg } from '../../lib/formValidation';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { formatDateTime } from '../../lib/date';
import type { RequeteSummary, TypeRequete } from '../../types/dataroom';
import { ETAT_SUIVI_LABELS, REQUETE_STATUT_LABELS, TYPE_REQUETE_LABELS } from '../../types/dataroom';
import type { Role } from '../../types/auth';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';
import { InfoBulle } from '../../components/ui/Aide';

/**
 * Lot AG — Section EMPLOYE « Mes requêtes au client » (direction EMPLOYE_TO_CLIENT),
 * dans l'onglet Demandes du Data Room. L'employé responsable (ou superviseur) crée
 * une requête (PIECE/INFO/SIGNATURE), suit son état, puis VALIDE (REPONDUE→CLOTUREE)
 * ou demande un COMPLÉMENT (REPONDUE→A_COMPLETER). C'est le client qui « répond ».
 */
const STATUT_VARIANTE: Record<string, BadgeVariant> = {
  OUVERTE: 'warning',
  A_COMPLETER: 'warning',
  REPONDUE: 'info',
  CLOTUREE: 'success',
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

  // Lecture : employe et superviseur. Creer, valider, demander un complement : POST /requetes,
  // /demandes/{id}/valider et /complement sont reserves a l'employe (DemandesController) ;
  // le superviseur suit sans bouton refuse (backlog L0).
  const canVoir = role === 'EMPLOYE' || role === 'SUPERVISEUR';
  const canAgir = role === 'EMPLOYE';

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
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

  if (!canVoir) return null;

  return (
    <section aria-labelledby="requetes-cabinet-titre" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 id="requetes-cabinet-titre" className="flex items-center gap-1 text-base font-semibold text-heading">
          Requêtes du cabinet au client
          <InfoBulle
            libelle="Aide sur les requêtes au client"
            texte="Une requête demande au client une pièce, une information ou une signature. Elle est « Non traitée » tant que le client n’a pas répondu, « En cours » quand sa réponse est à vérifier ou qu’un complément lui est demandé, « Traitée » une fois que vous avez validé sa réponse. Le client est averti par courriel de chaque nouvelle requête et de chaque complément demandé ; vous êtes notifié dans la cloche quand il répond."
          />
        </h3>
        {readOnly ? (
          <DataroomReadOnlyHint
            statut={readOnlyStatut}
            testId="requetes-readonly-hint"
          />
        ) : canAgir ? (
          <Button type="button" size="sm" icon={<Plus />} onClick={() => setNewOpen(true)}>
            Nouvelle requête
          </Button>
        ) : null}
      </div>

      {!canAgir && (
        <p className="text-sm text-fg-muted">
          Seul l’employé responsable du dossier crée les requêtes et valide les réponses du client ; vous
          les suivez ici.
        </p>
      )}

      {error && <Message ton="erreur">{error}</Message>}

      {loading ? (
        <div className="space-y-2" aria-busy="true" aria-label="Chargement des requêtes">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : sorted.length === 0 ? (
        <Card padding={false}>
          <EmptyState
            compact
            icon={<Send />}
            title="Aucune requête envoyée à ce client"
            description={
              canAgir
                ? 'Quand une pièce ou une réponse vous manque, cliquez sur « Nouvelle requête » : le client la verra dans son espace.'
                : 'Les requêtes de l’employé responsable au client apparaîtront ici.'
            }
          />
        </Card>
      ) : (
        <ul className="space-y-2">
          {sorted.map((r) => (
            <li key={r.id} className="rounded-xl border border-border bg-bg-raised p-4 shadow-card">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="mb-1 flex flex-wrap items-center gap-2">
                    <Badge variant="neutral">
                      {r.typeRequete ? TYPE_REQUETE_LABELS[r.typeRequete] : 'Requête'}
                    </Badge>
                    <span data-testid="etat-suivi-requete">
                      <Badge variant={STATUT_VARIANTE[r.statut] ?? 'neutral'} dot>
                        {r.etatSuivi ? ETAT_SUIVI_LABELS[r.etatSuivi] : 'État inconnu'}
                      </Badge>
                    </span>
                    <span className="text-xs text-fg-muted">{REQUETE_STATUT_LABELS[r.statut]}</span>
                  </div>
                  <p className="text-sm font-medium text-fg">{r.sujet}</p>
                  {r.noteClient && r.statut !== 'CLOTUREE' && (
                    <p className="mt-2 rounded-lg border border-border border-l-4 border-l-success bg-success-soft px-3 py-2 text-sm text-fg">
                      Réponse du client : {r.noteClient}
                    </p>
                  )}
                  <p className="mt-1 text-xs text-fg-muted">
                    Envoyée le {formatDateTime(r.createdAt)}
                    {r.reponduAt ? ` · répondue le ${formatDateTime(r.reponduAt)}` : ''}
                    {r.clotureAt ? ` · clôturée le ${formatDateTime(r.clotureAt)}` : ''}
                  </p>
                </div>
                {canAgir && r.statut === 'REPONDUE' && (
                  <div className="flex shrink-0 flex-wrap gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      icon={<RotateCcw />}
                      disabled={busyId === r.id}
                      onClick={() => setComplementId(r.id)}
                    >
                      Complément
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      icon={<CheckCircle2 className="text-success" />}
                      disabled={busyId === r.id}
                      onClick={() => valider(r.id)}
                    >
                      Valider
                    </Button>
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
    <Modal
      open
      onOpenChange={(o) => {
        if (!o) onClose();
      }}
      title="Nouvelle requête au client"
      description="Le client la voit dans son espace et reçoit un courriel ; vous êtes notifié quand il répond."
      closeOnBackdrop={false}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="button" icon={<Send />} loading={submitting} onClick={() => void submit()}>
            Envoyer la requête
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <GroupeRadio
          legende="Type"
          name="type-requete"
          horizontal
          value={typeRequete}
          onChange={(v) => setTypeRequete(v as TypeRequete)}
          options={TYPE_OPTIONS.map((t) => ({ value: t, label: TYPE_REQUETE_LABELS[t] }))}
        />
        <TextField
          label="Sujet"
          value={sujet}
          onChange={(e) => setSujet(e.target.value)}
          maxLength={200}
          placeholder="Exemple : fournir le contrat de bail signé"
          error={error === 'Le sujet est obligatoire.' ? error : undefined}
          required
        />
        <ZoneTexte
          label="Description (facultatif)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          placeholder="Précisez ce que le client doit fournir ou répondre…"
        />
        {error && error !== 'Le sujet est obligatoire.' && <Message ton="erreur">{error}</Message>}
      </div>
    </Modal>
  );
}
