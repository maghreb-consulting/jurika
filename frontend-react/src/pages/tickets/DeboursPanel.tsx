import { useCallback, useEffect, useState } from 'react';
import { Download, Paperclip, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { ticketService } from '../../services/ticket.service';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { CATEGORIE_LABELS } from '../../types/ticket';
import type { Debours, DeboursCategorie, TicketStatut } from '../../types/ticket';

/**
 * Lot L5 (E6) : rubrique « Débours » du ticket (cahier des charges 10.1).
 *
 * - RG-DEB-01 : une ligne par frais (libellé, montant en DH, type parmi les sept du cahier
 *   des charges, pièce justificative déposée en Data Room du dossier) ; ajout, modification,
 *   suppression par l'employé en charge seulement (le serveur le recontrôle) ;
 * - RG-DEB-02 : état des débours au format PDF à la charte JURIKA (lot L5b E3), régénéré à
 *   chaque téléchargement ;
 * - RG-TKT-11 : ticket clos ou annulé, lecture seule (garde serveur aussi).
 */

const mad = (n: number) =>
  `${new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)} DH`;

const TYPES = Object.entries(CATEGORIE_LABELS).map(([value, label]) => ({ value, label }));

/** Anciennes catégories reprises par la migration (V40), pour les nommer à l’écran. */
const ANCIENNES: Record<string, string> = {
  FRAIS_TRIBUNAL: 'Frais tribunal',
  FRAIS_NOTARIE: 'Frais notarié',
  FRAIS_ENREGISTREMENT: 'Frais d’enregistrement',
  PUBLICATION_JAL: 'Publication JAL',
  PUBLICATION_BO: 'Publication BO',
  HONORAIRES: 'Honoraires',
  TRANSPORT: 'Transport',
};

interface Props {
  ticketId: string;
  reference: string;
  dossierId: string | null;
  statut: TicketStatut;
  /** Employé en charge du ticket (RG-DOS-01). */
  canAct: boolean;
}

export function DeboursPanel({ ticketId, reference, dossierId, statut, canAct }: Props) {
  const ferme = statut === 'CLOTURE_DOSSIER' || statut === 'ANNULE';
  const modifiable = canAct && !ferme;
  const [items, setItems] = useState<Debours[]>([]);
  const [total, setTotal] = useState(0);
  const [charge, setCharge] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [edition, setEdition] = useState<Debours | 'nouveau' | null>(null);
  const [aSupprimer, setASupprimer] = useState<Debours | null>(null);
  const [suppression, setSuppression] = useState(false);
  const [telechargement, setTelechargement] = useState(false);

  const charger = useCallback(async () => {
    try {
      const r = await ticketService.listDebours(ticketId);
      setItems(r.items);
      setTotal(r.total);
      setErreur(null);
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setCharge(true);
    }
  }, [ticketId]);

  useEffect(() => {
    Promise.resolve().then(charger).catch(() => undefined);
  }, [charger]);

  async function supprimer() {
    if (!aSupprimer) return;
    setSuppression(true);
    try {
      await ticketService.deleteDebours(ticketId, aSupprimer.id);
      setASupprimer(null);
      await charger();
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setSuppression(false);
    }
  }

  async function telechargerEtat() {
    setTelechargement(true);
    try {
      await ticketService.downloadEtatDebours(ticketId, reference);
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setTelechargement(false);
    }
  }

  const sansPiece = items.filter((d) => !d.pieceJointeFilename).length;

  return (
    <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="debours-ticket">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-1 text-sm font-semibold text-fg">
          Débours
          <InfoBulle
            libelle="Que saisir dans les débours ?"
            texte="Les frais avancés par le cabinet pour le compte du client : une ligne par frais, avec son libellé, son montant en dirhams, son type et la pièce qui le justifie (reçu, quittance, facture). La pièce est rangée dans la Data Room du dossier."
          />
        </h2>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="secondary"
            onClick={telechargerEtat}
            disabled={items.length === 0 || telechargement}
            title={items.length === 0 ? 'Aucun débours : l’état ne peut pas être établi.' : undefined}
          >
            <Download className="mr-1 h-3.5 w-3.5" /> État des débours (PDF)
          </Button>
          <InfoBulle
            libelle="Que contient l’état des débours ?"
            texte="Un document PDF à la charte JURIKA, régénéré à chaque téléchargement à partir des lignes ci-dessous : en-tête du cabinet, société et ticket, tableau des frais, total en chiffres et en lettres, liste des pièces justificatives. Il n’est pas modifiable : corrigez une ligne ci-dessous, puis téléchargez-le de nouveau. Le client peut le télécharger depuis son espace si ses permissions le prévoient."
          />
          {modifiable && edition === null && (
            <Button size="sm" onClick={() => setEdition('nouveau')}>
              <Plus className="mr-1 h-3.5 w-3.5" /> Ajouter un frais
            </Button>
          )}
        </div>
      </div>

      <TexteAide cle="debours-ticket" titre="Comment tenir les débours ?">
        {modifiable ? (
          <p>
            Ajoutez une ligne pour chaque frais payé pour le client et joignez sa pièce justificative.
            Une ligne sans pièce est signalée ici et dans l’état des débours. Téléchargez ensuite
            l’état des débours (PDF) pour le remettre au client ; il se régénère à tout moment.
          </p>
        ) : ferme ? (
          <p>
            Le ticket est {statut === 'ANNULE' ? 'annulé' : 'clos'} : les débours sont en lecture seule.
            L’état des débours reste téléchargeable.
          </p>
        ) : (
          <p>
            Consultation seule : seul l’employé responsable du dossier saisit les débours. L’état des
            débours reste téléchargeable.
          </p>
        )}
      </TexteAide>

      {erreur && (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-2 text-sm text-danger">
          {erreur}
        </p>
      )}

      {edition !== null && (
        <DeboursForm
          ticketId={ticketId}
          dossierId={dossierId}
          reference={reference}
          ligne={edition === 'nouveau' ? null : edition}
          onAnnuler={() => setEdition(null)}
          onEnregistre={async () => {
            setEdition(null);
            await charger();
          }}
        />
      )}

      {charge && items.length === 0 ? (
        <p className="text-sm text-fg-subtle">Aucun débours saisi pour ce ticket.</p>
      ) : (
        <table className="w-full text-sm">
          <caption className="sr-only">Débours du ticket {reference}</caption>
          <thead>
            <tr className="text-left text-xs text-fg-subtle">
              <th className="py-1 pr-2 font-medium">Date</th>
              <th className="py-1 pr-2 font-medium">Type</th>
              <th className="py-1 pr-2 font-medium">Libellé</th>
              <th className="py-1 pr-2 font-medium">Pièce justificative</th>
              <th className="py-1 pr-2 text-right font-medium">Montant</th>
              {modifiable && <th className="py-1 font-medium"><span className="sr-only">Actions</span></th>}
            </tr>
          </thead>
          <tbody>
            {items.map((d) => (
              <tr key={d.id} className="border-t border-border align-top">
                <td className="py-1.5 pr-2 text-fg-muted">{new Date(d.dateEngagement).toLocaleDateString('fr-FR')}</td>
                <td className="py-1.5 pr-2 text-fg-muted">
                  {CATEGORIE_LABELS[d.categorie] ?? d.categorie}
                  {d.categorieReprise && (
                    <span className="block text-xs text-warning" data-testid="categorie-reprise">
                      Reprise de « {ANCIENNES[d.categorieReprise] ?? d.categorieReprise} » : vérifiez le type.
                    </span>
                  )}
                </td>
                <td className="py-1.5 pr-2 text-fg">{d.libelle}</td>
                <td className="py-1.5 pr-2">
                  {d.pieceJointeFilename ? (
                    d.pieceDocumentId ? (
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 text-accent underline"
                        onClick={() =>
                          void dataroomService
                            .downloadDocument(d.pieceDocumentId as string, d.pieceJointeFilename as string)
                            .catch((e) => setErreur(extractError(e).message))
                        }
                      >
                        <Paperclip className="h-3.5 w-3.5" /> {d.pieceJointeFilename}
                      </button>
                    ) : (
                      <span className="text-fg-muted">{d.pieceJointeFilename}</span>
                    )
                  ) : (
                    <span className="text-xs text-warning">Pièce manquante</span>
                  )}
                </td>
                <td className="py-1.5 pr-2 text-right font-mono text-fg">{mad(d.montantMad)}</td>
                {modifiable && (
                  <td className="py-1.5 text-right whitespace-nowrap">
                    <button
                      type="button"
                      aria-label={`Modifier « ${d.libelle} »`}
                      onClick={() => setEdition(d)}
                      className="mr-2 text-fg-subtle hover:text-fg"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      aria-label={`Supprimer « ${d.libelle} »`}
                      onClick={() => setASupprimer(d)}
                      className="text-danger hover:opacity-80"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
          {items.length > 0 && (
            <tfoot>
              <tr className="border-t border-border font-medium">
                <td colSpan={4} className="py-1.5 pr-2 text-fg">Total</td>
                <td className="py-1.5 pr-2 text-right font-mono text-fg" data-testid="total-debours">{mad(total)}</td>
                {modifiable && <td />}
              </tr>
            </tfoot>
          )}
        </table>
      )}

      {sansPiece > 0 && (
        <p className="text-xs text-warning" data-testid="pieces-manquantes">
          {sansPiece === 1
            ? 'Une ligne n’a pas de pièce justificative.'
            : `${sansPiece} lignes n’ont pas de pièce justificative.`}
          {modifiable ? ' Modifiez la ligne pour la joindre.' : ''}
        </p>
      )}

      <ConfirmDialog
        open={aSupprimer !== null}
        onOpenChange={(o) => {
          if (!o) setASupprimer(null);
        }}
        title="Supprimer ce débours ?"
        description={
          aSupprimer
            ? `« ${aSupprimer.libelle} » (${mad(aSupprimer.montantMad)}) sera retiré de l’état des débours. La suppression est tracée ; la pièce reste dans la Data Room du dossier.`
            : ''
        }
        confirmLabel="Supprimer"
        variant="danger"
        loading={suppression}
        onConfirm={supprimer}
      />
    </section>
  );
}

interface FormProps {
  ticketId: string;
  dossierId: string | null;
  reference: string;
  ligne: Debours | null;
  onAnnuler: () => void;
  onEnregistre: () => Promise<void>;
}

function DeboursForm({ ticketId, dossierId, reference, ligne, onAnnuler, onEnregistre }: FormProps) {
  const [libelle, setLibelle] = useState(ligne?.libelle ?? '');
  const [categorie, setCategorie] = useState<DeboursCategorie>(ligne?.categorie ?? 'FRAIS_GREFFE');
  const [montant, setMontant] = useState(ligne ? String(ligne.montantMad) : '');
  const [date, setDate] = useState(ligne?.dateEngagement ?? new Date().toISOString().slice(0, 10));
  const [fichier, setFichier] = useState<File | null>(null);
  const [retirerPiece, setRetirerPiece] = useState(false);
  const [erreurs, setErreurs] = useState<Record<string, string>>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  async function enregistrer(e: React.FormEvent) {
    e.preventDefault();
    const n = Number(montant.replace(',', '.'));
    const errs: Record<string, string> = {};
    if (!libelle.trim()) errs.libelle = 'Indiquez le libellé du frais.';
    if (!montant.trim() || !Number.isFinite(n) || n <= 0) errs.montant = 'Indiquez un montant positif, en dirhams.';
    if (!date) errs.date = 'Indiquez la date du frais.';
    setErreurs(errs);
    if (Object.keys(errs).length > 0) return;
    setEnvoi(true);
    setErreur(null);
    try {
      let pieceDocumentId = retirerPiece ? null : (ligne?.pieceDocumentId ?? null);
      let pieceJointeFilename = retirerPiece ? null : (ligne?.pieceJointeFilename ?? null);
      if (fichier && dossierId) {
        // RG-DEB-01 : la pièce est déposée en Data Room du dossier, comme les autres pièces.
        const doc = await dataroomService.uploadJuridique(dossierId, {
          file: fichier,
          documentType: 'AUTRE',
          title: `Justificatif de débours — ${reference} — ${libelle.trim()} — ${fichier.name}`,
          ticketId,
          visibleClient: true,
        });
        pieceDocumentId = doc.id;
        pieceJointeFilename = fichier.name;
      }
      const payload = {
        libelle: libelle.trim(),
        categorie,
        montant: Math.round(n * 100) / 100,
        dateEngagement: date,
        pieceDocumentId,
        pieceJointeFilename,
      };
      if (ligne) await ticketService.updateDebours(ticketId, ligne.id, payload);
      else await ticketService.createDebours(ticketId, payload);
      await onEnregistre();
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <form
      onSubmit={enregistrer}
      noValidate
      className="grid gap-3 rounded-lg border border-border bg-bg-overlay p-3 md:grid-cols-2"
      aria-label={ligne ? 'Modifier un débours' : 'Ajouter un débours'}
    >
      <TextField label="Libellé" name="libelle" value={libelle} onChange={(e) => setLibelle(e.target.value)}
        error={erreurs.libelle} hint="Exemple : dépôt des statuts au greffe." required />
      <Select label="Type de frais" name="categorie" value={categorie}
        onChange={(e) => setCategorie(e.target.value as DeboursCategorie)} options={TYPES} />
      <TextField label="Montant (DH)" name="montant" inputMode="decimal" value={montant}
        onChange={(e) => setMontant(e.target.value)} error={erreurs.montant} hint="Centimes après la virgule : 1234,50." required />
      <TextField label="Date du frais" name="date" type="date" value={date}
        onChange={(e) => setDate(e.target.value)} error={erreurs.date} required />
      <div className="md:col-span-2 space-y-1">
        <div className="flex items-center gap-1">
          <label htmlFor="piece-debours" className="text-sm font-medium text-fg">
            Pièce justificative (reçu, quittance, facture)
          </label>
          <InfoBulle
            libelle="Où va la pièce justificative ?"
            texte="Le fichier est déposé dans la Data Room du dossier, rattaché à ce ticket, et visible du client si ses permissions le prévoient. Sans pièce, la ligne est enregistrée mais signalée comme incomplète."
          />
        </div>
        {dossierId ? (
          <input id="piece-debours" type="file" accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
            onChange={(e) => setFichier(e.target.files?.[0] ?? null)} className="block text-sm text-fg" />
        ) : (
          <p className="text-xs text-warning">
            Ce ticket n’est rattaché à aucun dossier : la pièce ne peut pas être déposée en Data Room.
          </p>
        )}
        {ligne?.pieceJointeFilename && !fichier && (
          <label className="flex items-center gap-2 text-xs text-fg-muted">
            <input type="checkbox" checked={retirerPiece} onChange={(e) => setRetirerPiece(e.target.checked)} />
            Retirer la pièce actuelle ({ligne.pieceJointeFilename}) de cette ligne
          </label>
        )}
      </div>
      {erreur && (
        <p role="alert" className="md:col-span-2 text-sm text-danger">{erreur}</p>
      )}
      <div className="md:col-span-2 flex justify-end gap-2">
        <Button type="button" size="sm" variant="ghost" onClick={onAnnuler}>Annuler</Button>
        <Button type="submit" size="sm" loading={envoi}>
          {ligne ? 'Enregistrer la modification' : 'Ajouter le débours'}
        </Button>
      </div>
    </form>
  );
}
