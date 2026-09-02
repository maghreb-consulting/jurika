import { useState } from 'react';
import { FileText, Upload, X, CheckCircle } from 'lucide-react';
import { workflowService } from '../../services/workflow.service';
import type { WorkflowProgress } from '../../types/workflow';

/**
 * P2 2026-06-04 — Panneau generique "Pieces jointes du dossier".
 *
 * Affiche le registre des pieces persistantes (data.pieces de WorkflowProgress)
 * et permet d'en ajouter / retirer depuis n'importe quelle etape du wizard.
 * Une piece uploadee a l'etape 1 reste visible / reutilisable a l'etape 5+
 * sans re-upload — c'est tout l'objectif du registre cross-step.
 *
 * Le stockage du fichier physique reste a charge de dataroom-service /
 * MinIO (out of scope V1 — ce panel n'expose que les metadonnees).
 */

interface RegisteredPiece {
  code: string;
  label: string;
  filename?: string;
  sizeBytes?: number;
  contentType?: string;
  uploadedAt?: string;
  uploadedAtStep?: number;
}

interface Props {
  ticketId: string;
  progress: WorkflowProgress;
  currentStep: number;
  onProgressUpdate?: (p: WorkflowProgress) => void;
  /** Catalogue suggere : codes connus (CN, JUSTIFICATIF_SIEGE, CIN_DIRIGEANTS,...).
   *  Si fourni, on propose des boutons "Importer" pour les codes pas encore presents. */
  knownPieces?: Array<{ code: string; label: string; obligatoire?: boolean }>;
}

function humanSize(b?: number): string {
  if (!b) return '';
  if (b < 1024) return `${b} o`;
  if (b < 1024 * 1024) return `${Math.round(b / 1024)} Ko`;
  return `${(b / (1024 * 1024)).toFixed(1)} Mo`;
}

export function WorkflowPiecesPanel({
  ticketId,
  progress,
  currentStep,
  onProgressUpdate,
  knownPieces,
}: Props) {
  const piecesMap = (progress.data?.pieces as Record<string, RegisteredPiece>) ?? {};
  const registered: RegisteredPiece[] = Object.values(piecesMap);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleUpload(code: string, label: string, file: File) {
    setBusy(code);
    setError(null);
    try {
      // V1 : on persiste UNIQUEMENT les metadonnees (filename, sizeBytes, ...).
      // Le fichier physique reste cote client tant qu'un endpoint de stockage
      // n'est pas cable. C'est suffisant pour l'UX "deja deposee a l'etape X".
      const updated = await workflowService.registerPiece(ticketId, {
        code,
        label,
        filename: file.name,
        sizeBytes: file.size,
        contentType: file.type || 'application/octet-stream',
        uploadedAtStep: currentStep,
      });
      onProgressUpdate?.(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Echec enregistrement piece');
    } finally {
      setBusy(null);
    }
  }

  async function handleRemove(code: string) {
    setBusy(code);
    setError(null);
    try {
      const updated = await workflowService.unregisterPiece(ticketId, code);
      onProgressUpdate?.(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Echec suppression piece');
    } finally {
      setBusy(null);
    }
  }

  const registeredCodes = new Set(registered.map((p) => p.code));
  const missingKnown = (knownPieces ?? []).filter((k) => !registeredCodes.has(k.code));

  return (
    <section className="rounded-2xl border border-border bg-bg-raised p-4">
      <header className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-fg">
          Pieces jointes du dossier
          <span className="ml-2 text-xs font-normal text-fg-subtle">
            {registered.length} deposee{registered.length > 1 ? 's' : ''}
          </span>
        </h3>
        <p className="text-xs text-fg-subtle">
          Une piece uploadee reste disponible dans toutes les etapes suivantes.
        </p>
      </header>

      {error && (
        <div className="mb-3 rounded-md border border-danger/30 bg-danger/10 px-3 py-1.5 text-xs text-danger">
          {error}
        </div>
      )}

      {registered.length === 0 && missingKnown.length === 0 && (
        <p className="text-xs text-fg-subtle">Aucune piece deposee pour l'instant.</p>
      )}

      <ul className="space-y-2">
        {registered.map((p) => (
          <li
            key={p.code}
            className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2"
          >
            <CheckCircle className="h-4 w-4 flex-shrink-0 text-emerald-700" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-fg">{p.label}</p>
              <p className="truncate text-xs text-fg-subtle">
                {p.filename ?? '—'}
                {p.sizeBytes ? ` · ${humanSize(p.sizeBytes)}` : ''}
                {p.uploadedAtStep ? ` · etape ${p.uploadedAtStep}` : ''}
              </p>
            </div>
            <button
              type="button"
              onClick={() => handleRemove(p.code)}
              disabled={busy === p.code}
              className="rounded p-1 text-fg-subtle hover:bg-danger/10 hover:text-danger disabled:opacity-50"
              aria-label={`Retirer ${p.label}`}
              title="Retirer cette piece"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </li>
        ))}
        {missingKnown.map((k) => (
          <li
            key={k.code}
            className="flex items-center gap-2 rounded-lg border border-dashed border-border px-3 py-2"
          >
            <FileText className="h-4 w-4 flex-shrink-0 text-fg-subtle" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm text-fg">
                {k.label}
                {k.obligatoire && (
                  <span className="ml-2 rounded bg-danger/10 px-1.5 py-0.5 text-[10px] font-bold uppercase text-danger">
                    obligatoire
                  </span>
                )}
              </p>
              <p className="text-xs text-fg-subtle">A deposer</p>
            </div>
            <label className="flex cursor-pointer items-center gap-1 rounded-md border border-accent/40 bg-accent/10 px-2 py-1 text-xs text-accent hover:bg-accent/20">
              <Upload className="h-3 w-3" />
              {busy === k.code ? 'Envoi...' : 'Importer'}
              <input
                type="file"
                accept="application/pdf,image/*"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) void handleUpload(k.code, k.label, f);
                }}
              />
            </label>
          </li>
        ))}
      </ul>
    </section>
  );
}
