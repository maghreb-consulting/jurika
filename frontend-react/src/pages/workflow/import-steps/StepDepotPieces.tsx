import { useCallback, useState } from 'react';
import { useStepAutosave, type RegisterDirty } from '../useStepAutosave';
import { FolderUp, Loader2, Check, AlertTriangle } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';

/**
 * Etape 8 de l'IMPORT — depot des pieces comptables et fiscales.
 *
 * <p>Lot 1 (2026-09-04) : les dossiers comptable et fiscal sont sortis du
 * perimetre produit. Les pieces de cette nature ne sont plus classees par
 * categorie ni rattachees a un exercice : elles sont simplement deposees, telles
 * quelles, dans l'espace « Depot client » de la Data Room.
 *
 * <p>L'etape est OPTIONNELLE : un import peut se terminer sans aucun depot.
 */

interface PieceDeposee {
  nom: string;
  taille: number;
  etat: 'ENVOI' | 'DEPOSE' | 'ECHEC';
  erreur?: string;
}

interface Props {
  dossierId?: string | null;
  existing?: Record<string, unknown>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => void;
  /** Lot L7 (E2) : enregistrement automatique de la saisie (voir useStepAutosave). */
  registerDirty?: RegisterDirty;
}

export function StepDepotPieces({ dossierId, existing, saving, onSubmit, registerDirty }: Props) {
  const dejaDeposees = (existing?.pieces as PieceDeposee[] | undefined) ?? [];
  const [pieces, setPieces] = useState<PieceDeposee[]>(dejaDeposees);
  // Lot L7 (E2) : la liste des pieces deposees est enregistree sans clic.
  useStepAutosave(
    8,
    () => ({ pieces: pieces.filter((p) => p.etat === 'DEPOSE') }),
    registerDirty,
  );
  const [erreur, setErreur] = useState<string | null>(null);

  const deposer = useCallback(
    async (fichiers: FileList | null) => {
      if (!fichiers || fichiers.length === 0) return;
      if (!dossierId) {
        setErreur('Aucun dossier n’est rattaché à ce ticket : le dépôt est impossible. Revenez à la liste des tickets et rouvrez le parcours.');
        return;
      }
      setErreur(null);
      for (const fichier of Array.from(fichiers)) {
        setPieces((p) => [...p, { nom: fichier.name, taille: fichier.size, etat: 'ENVOI' }]);
        try {
          await dataroomService.uploadDepot(dossierId, fichier);
          setPieces((p) =>
            p.map((x) => (x.nom === fichier.name && x.etat === 'ENVOI'
              ? { ...x, etat: 'DEPOSE' as const }
              : x)),
          );
        } catch (err) {
          const { message } = extractError(err);
          setPieces((p) =>
            p.map((x) => (x.nom === fichier.name && x.etat === 'ENVOI'
              ? { ...x, etat: 'ECHEC' as const, erreur: message }
              : x)),
          );
        }
      }
    },
    [dossierId],
  );

  const deposees = pieces.filter((p) => p.etat === 'DEPOSE');

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-fg">Pièces comptables et fiscales</h2>
        <p className="mt-1 text-sm text-fg-subtle">
          Étape facultative. Les fichiers sont déposés tels quels dans l’espace « Dépôt client »
          de la Data Room, sans classement ni traitement.
        </p>
      </div>

      {!dossierId && (
        <p className="rounded-lg border border-warning/40 bg-warning-soft p-3 text-sm text-fg">
          Aucun dossier n’est rattaché à ce ticket : le dépôt est désactivé. Revenez à la liste des tickets et rouvrez le parcours.
        </p>
      )}

      <label
        className={`flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed
          border-border bg-bg-overlay px-6 py-8 text-center transition hover:border-border-hi
          has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus
          ${!dossierId ? 'pointer-events-none opacity-50' : ''}`}
      >
        <FolderUp className="h-6 w-6 text-fg-subtle" />
        <span className="text-sm font-medium text-fg">Choisir des fichiers</span>
        <span className="text-xs text-fg-subtle">Plusieurs fichiers à la fois, tous formats</span>
        <input
          type="file"
          multiple
          className="sr-only"
          disabled={!dossierId}
          onChange={(e) => {
            void deposer(e.target.files);
            e.target.value = '';
          }}
        />
      </label>

      {erreur && (
        <p className="text-sm text-danger" role="alert">
          {erreur}
        </p>
      )}

      {pieces.length > 0 && (
        <ul className="divide-y divide-border rounded-xl border border-border">
          {pieces.map((p, i) => (
            <li key={`${p.nom}-${i}`} className="flex items-center gap-3 px-4 py-2 text-sm">
              {p.etat === 'ENVOI' && <Loader2 className="h-4 w-4 animate-spin text-fg-subtle" />}
              {p.etat === 'DEPOSE' && <Check className="h-4 w-4 text-success" />}
              {p.etat === 'ECHEC' && <AlertTriangle className="h-4 w-4 text-danger" />}
              <span className="flex-1 truncate text-fg">{p.nom}</span>
              {p.etat === 'ECHEC' && (
                <span className="text-xs text-danger">{p.erreur ?? 'Échec du dépôt : réessayez.'}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="flex items-center justify-between pt-2">
        <p className="text-sm text-fg-subtle">
          {deposees.length === 0
            ? 'Aucune pièce déposée : vous pouvez continuer sans dépôt.'
            : `${deposees.length} pièce${deposees.length > 1 ? 's' : ''} déposée${deposees.length > 1 ? 's' : ''}`}
        </p>
        <Button
          disabled={saving}
          onClick={() => onSubmit({ pieces: deposees })}
        >
          Continuer
        </Button>
      </div>
    </div>
  );
}
