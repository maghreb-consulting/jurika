/**
 * Pièces jointes du workflow MODIFICATION (Phase 5, 2026-08-10).
 *
 * Fenêtre d'upload libre (statut légalisé, PV signé, CIN, modèle J…) — calquée sur
 * l'esprit de {@code Step8PiecesJointes} de la création : <b>AUCUNE pièce obligatoire</b>
 * (pas de garde-fou de complétude). Chaque fichier déposé part en Data Room
 * <b>versionné</b> (Phase 7 : ré-upload d'une même pièce = nouvelle version), avec le
 * nommage `type - dénomination - forme (- v<n>)` en conservant l'extension d'origine.
 */
import { useState } from 'react';
import { CheckCircle, FileUp, Loader, Paperclip, Plus, X } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import { buildDocFilename, extensionOf } from './workflowFilename';

/** Une pièce jointe déposée (persistée pour la synthèse). */
export interface PieceJointeEntry {
  id: string;
  label: string;
  filename: string;
  version?: number;
}

interface PieceSlot {
  id: string;
  label: string;
  /** true = ligne ajoutée par l'utilisateur (retirable tant que non déposée). */
  custom?: boolean;
}

const DEFAULT_SLOTS: PieceSlot[] = [
  { id: 'statuts-legalises', label: 'Statuts légalisés' },
  { id: 'pv-signe', label: 'PV signé et légalisé' },
  { id: 'cin-associes', label: 'CIN des associés / gérants' },
  { id: 'modele-j', label: 'Modèle J (registre du commerce)' },
];

export interface PiecesJointesPanelProps {
  dossierId?: string | null;
  ticketId?: string | null;
  denomination: string;
  forme: string;
  motif?: string;
  onDeposited?: (entry: PieceJointeEntry) => void;
}

export function PiecesJointesPanel({
  dossierId,
  ticketId,
  denomination,
  forme,
  motif,
  onDeposited,
}: PiecesJointesPanelProps) {
  const [open, setOpen] = useState(false);
  const [slots, setSlots] = useState<PieceSlot[]>(DEFAULT_SLOTS);
  const [uploading, setUploading] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [done, setDone] = useState<Record<string, PieceJointeEntry>>({});
  const [newLabel, setNewLabel] = useState('');

  const addSlot = () => {
    const label = newLabel.trim();
    if (!label) return;
    setSlots((prev) => [...prev, { id: `custom-${prev.length}-${label}`, label, custom: true }]);
    setNewLabel('');
  };
  const removeSlot = (id: string) =>
    setSlots((prev) => prev.filter((s) => s.id !== id || done[id]));

  async function deposit(slot: PieceSlot, file: File) {
    if (!dossierId) {
      setErrors((e) => ({ ...e, [slot.id]: 'Aucune société sélectionnée.' }));
      return;
    }
    setUploading((u) => ({ ...u, [slot.id]: true }));
    setErrors((e) => ({ ...e, [slot.id]: '' }));
    try {
      // Versioning : une pièce de même (type AUTRE + titre) déjà en vigueur est remplacée.
      let existingDocumentId: string | undefined;
      let matchedVersion: number | undefined;
      try {
        const view = await dataroomService.getJuridique(dossierId);
        const match = view.documentsEnVigueur.find(
          (d) => d.documentType === 'AUTRE' && d.title === slot.label,
        );
        existingDocumentId = match?.id;
        matchedVersion = match?.version;
      } catch {
        /* best-effort */
      }
      const nextVersion = existingDocumentId ? (matchedVersion ?? 1) + 1 : undefined;
      const filename = buildDocFilename(
        slot.label,
        denomination,
        forme,
        nextVersion,
        extensionOf(file.name),
      );
      await dataroomService.depositGeneratedDoc(dossierId, file, {
        documentType: 'AUTRE',
        title: slot.label,
        filename,
        ticketId: ticketId ?? undefined,
        motif,
        replacePrevious: !!existingDocumentId,
        existingDocumentId,
      });
      const entry: PieceJointeEntry = { id: slot.id, label: slot.label, filename, version: nextVersion };
      setDone((d) => ({ ...d, [slot.id]: entry }));
      onDeposited?.(entry);
    } catch (err) {
      setErrors((e) => ({
        ...e,
        [slot.id]: err instanceof Error ? err.message : 'Échec du dépôt.',
      }));
    } finally {
      setUploading((u) => ({ ...u, [slot.id]: false }));
    }
  }

  return (
    <section className="rounded-xl border border-border bg-bg-raised" data-testid="mod-pieces-jointes">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2 px-4 py-3 text-left"
        aria-expanded={open}
      >
        <Paperclip className="h-4 w-4 text-accent" />
        <span className="text-sm font-semibold text-fg">Pièces jointes (optionnelles)</span>
        <span className="ml-auto text-xs text-fg-subtle">
          {Object.keys(done).length > 0 ? `${Object.keys(done).length} déposée(s)` : 'Aucune obligatoire'}
        </span>
      </button>

      {open && (
        <div className="space-y-3 border-t border-border p-4">
          <p className="text-xs text-fg-subtle">
            Déposez les pièces au format PDF / image (statut légalisé, PV signé, CIN…). Aucune
            pièce n'est obligatoire. Chaque dépôt est versionné en Data Room.
          </p>

          <ul className="space-y-2">
            {slots.map((slot) => {
              const entry = done[slot.id];
              return (
                <li
                  key={slot.id}
                  className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-bg-overlay p-3"
                  data-testid={`piece-${slot.id}`}
                >
                  <span className="min-w-0 flex-1 text-sm font-medium text-fg">{slot.label}</span>
                  {entry ? (
                    <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
                      <CheckCircle className="h-3 w-3" /> Déposée{entry.version ? ` · v${entry.version}` : ''}
                    </span>
                  ) : (
                    <>
                      <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-border bg-bg-raised px-3 py-1.5 text-xs font-medium text-fg hover:border-accent">
                        {uploading[slot.id] ? (
                          <Loader className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <FileUp className="h-3.5 w-3.5" />
                        )}
                        {uploading[slot.id] ? 'Dépôt…' : 'Choisir un fichier'}
                        <input
                          type="file"
                          className="hidden"
                          data-testid={`piece-input-${slot.id}`}
                          disabled={uploading[slot.id]}
                          onChange={(e) => {
                            const f = e.target.files?.[0];
                            if (f) void deposit(slot, f);
                            e.target.value = '';
                          }}
                        />
                      </label>
                      {slot.custom && (
                        <button
                          type="button"
                          onClick={() => removeSlot(slot.id)}
                          className="rounded p-1 text-fg-subtle hover:bg-danger/10 hover:text-danger"
                          aria-label="Retirer"
                        >
                          <X className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </>
                  )}
                  {errors[slot.id] && (
                    <p className="w-full text-xs text-danger" role="alert">{errors[slot.id]}</p>
                  )}
                </li>
              );
            })}
          </ul>

          <div className="flex items-center gap-2">
            <input
              type="text"
              value={newLabel}
              onChange={(e) => setNewLabel(e.target.value)}
              placeholder="Ajouter une pièce (libellé)…"
              className="flex-1 rounded-lg border border-border-hi bg-bg-raised px-3 py-1.5 text-xs focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
            />
            <button
              type="button"
              onClick={addSlot}
              className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-fg hover:bg-bg-overlay"
            >
              <Plus className="h-3.5 w-3.5" /> Ajouter
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
