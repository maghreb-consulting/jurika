/**
 * RG transverse 2026-06-06 — Bloc unitaire de generation documentaire.
 *
 * Pattern extrait depuis ModificationWorkflowPage et partage avec
 * DissolutionWorkflowPage + LiquidationWorkflowPage.
 *
 * Cycle utilisateur : Generer -> (Apercu/Telecharger/Modifier) -> Valider
 *   - Generer : appel `generateDocument(workflowCode, templateCode, payload)` (DOCX)
 *   - Telecharger : declenche un download du blob
 *   - Modifier : regenere (apres mise a jour des valeurs amont)
 *   - Valider : verrouille + depot auto dataroom (best-effort, idempotent)
 *
 * Volontairement decouple : la page hote fournit la liste des templates,
 * le payload, le mapping `mapTemplateToDocumentType` et le dossierId.
 */
import { useState } from 'react';
import {
  CheckCircle,
  Download,
  Eye,
  FileText,
  Loader,
  Pencil,
  RefreshCcw,
  Sparkles,
} from 'lucide-react';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import { RetourGeneration } from './RetourGeneration';
import { DonneesAttendues } from './DonneesAttendues';
import { ClausesLibresPanel } from './ClausesLibresPanel';
import { GeneratedDocPreview } from '../document/GeneratedDocPreview';
import { DocumentEditModal } from '../document/DocumentEditModal';
import type { DocState } from './documentBlocks';
import { useEnEssai } from '../../store/essaiStore';
import { AideApercuEssai, ApercuPagesView } from '../document/ApercuPages';

export type { DocState, UseDocumentBlocksParams } from './documentBlocks';

// ============================================================================
// Bloc visuel — utilise par les pages workflow MODIFICATION/DISSOLUTION/LIQUIDATION
// ============================================================================
export interface WorkflowDocumentBlockProps {
  tpl: TemplateInfo;
  state: DocState;
  onGenerate: () => void;
  onDownload: () => void;
  onRegenerate: () => void;
  onValidate: () => void;
  onTogglePreview: () => void;
  /**
   * 2026-08-12 — Édition WYSIWYG (aligné sur « Éditer » de Step7 Création).
   * Optionnel : quand fourni, un bouton « Éditer » ouvre le document dans
   * l'éditeur ; à la sauvegarde, `onEdited(blob, filename)` remonte le nouveau
   * .docx pour remplacer le blob en mémoire (le caller repasse en non-validé).
   * Sans ce prop, le bouton n'apparaît pas (rétro-compatible).
   */
  onEdited?: (blob: Blob, filename: string) => void;
  /** Lot L3 (RG-GEN-05) : ticket et parcours, pour proposer les clauses libres du document. */
  ticketId?: string | null;
  workflowCode?: string;
}

export function WorkflowDocumentBlock({
  tpl,
  state,
  onGenerate,
  onDownload,
  onRegenerate,
  onValidate,
  onTogglePreview,
  onEdited,
  ticketId,
  workflowCode,
}: WorkflowDocumentBlockProps) {
  const title = tpl.documentKind || tpl.code;
  const [editing, setEditing] = useState(false);
  // Decision du 2026-10-14 (RG-ESS-05) : pendant l'essai, ni telechargement ni edition du fichier.
  const enEssai = useEnEssai();
  return (
    <div
      className={`overflow-hidden rounded-xl border bg-bg-raised shadow-sm ${
        state.validated ? 'border-success' : 'border-border'
      }`}
      data-testid={`doc-block-${tpl.code}`}
    >
      <div className="flex items-start justify-between gap-2 border-b border-border bg-bg-overlay px-5 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <FileText className="h-4 w-4 flex-shrink-0 text-accent" />
            <h4 className="truncate text-sm font-semibold text-fg" title={title}>
              {title}
            </h4>
          </div>
          <p className="mt-0.5 text-[11px] text-fg-subtle">
            {state.validated
              ? state.depositedToDataroom
                ? 'Document validé et déposé dans la Data Room du dossier.'
                : 'Document validé.'
              : state.generated
                ? 'Relisez l’aperçu, régénérez au besoin, puis validez.'
                : 'Générez le document ; s’il manque une donnée, elle vous sera nommée.'}
          </p>
        </div>
        {state.validated && (
          <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
            <CheckCircle className="h-3 w-3" /> Validé
            {state.depositedToDataroom && (
              <span className="text-[9px] font-normal opacity-80">
                · Data Room
              </span>
            )}
          </span>
        )}
      </div>

      <div className="space-y-3 p-5">
        <RetourGeneration erreur={state.error} refus={state.refus} aObtenir={state.aObtenir} />
        <DonneesAttendues
          attendues={state.attendues}
          enCours={state.generating}
          onRegenerer={state.generated ? onRegenerate : onGenerate}
          ticketId={ticketId}
        />
        {ticketId && workflowCode && (
          <ClausesLibresPanel
            ticketId={ticketId}
            workflowCode={workflowCode}
            templateCode={tpl.code}
            onEnregistre={state.generated ? onRegenerate : undefined}
          />
        )}

        {!state.generated ? (
          <button
            type="button"
            onClick={onGenerate}
            disabled={state.generating}
            data-testid={`generate-btn-${tpl.code}`}
            className={`flex h-10 w-full items-center justify-center gap-2 rounded-lg text-sm font-medium transition ${
              state.generating
                ? 'cursor-wait bg-border text-fg-subtle'
                : 'bg-accent text-bg-raised hover:bg-accent-hover'
            }`}
          >
            {state.generating ? (
              <>
                <Loader className="h-4 w-4 animate-spin" /> Génération…
              </>
            ) : (
              <>
                <Sparkles className="h-4 w-4" /> Générer
              </>
            )}
          </button>
        ) : (
          <>
            <div className="flex items-center gap-2 rounded-lg border border-success/30 bg-success/5 p-2 text-xs">
              <CheckCircle className="h-4 w-4 text-success" />
              <span className="flex-1 truncate text-fg">
                {state.filename ?? 'Document généré'}
              </span>
            </div>

            {/* 2026-08-12 — Aperçu FIDÈLE (docx-preview) inline + plein écran,
                remplace l'ancien placeholder texte. Après navigation le blob
                n'est plus en mémoire → régénérer pour réafficher l'aperçu. */}
            {state.previewOpen && state.blob && (
              <GeneratedDocPreview
                blob={state.blob}
                title={title}
                filename={state.filename ?? title}
              />
            )}
            {state.previewOpen && !state.blob && state.apercu && (
              <div className="max-h-[70vh] space-y-3 overflow-auto rounded-lg border border-border bg-bg-overlay p-3">
                {state.apercu.filigrane && <AideApercuEssai />}
                <ApercuPagesView apercu={state.apercu} titre={title} />
              </div>
            )}
            {state.previewOpen && !state.blob && !state.apercu && (
              <div className="rounded-lg border border-border bg-bg-overlay p-3 text-[11px] text-fg-subtle">
                <Eye className="mr-1 inline h-3.5 w-3.5 align-text-bottom" />
                Aperçu indisponible : régénérez le document pour l’afficher.
              </div>
            )}

            {/* 2026-08-12 — Jeu de boutons aligné sur Step7 Création :
                Aperçu / Régénérer / Éditer / .docx / Valider. « Éditer » n'apparaît
                que si `onEdited` est fourni (rétro-compatible pour les workflows
                qui ne l'ont pas encore câblé). */}
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={onTogglePreview}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
              >
                <Eye className="h-3.5 w-3.5" />
                {state.previewOpen ? 'Masquer l’aperçu' : 'Aperçu'}
              </button>
              <button
                type="button"
                onClick={onRegenerate}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
                title="Régénérer le document après avoir corrigé les données saisies"
              >
                <RefreshCcw className="h-3.5 w-3.5" /> Régénérer
              </button>
              {onEdited && enEssai === false && (
                <button
                  type="button"
                  onClick={() => setEditing(true)}
                  disabled={!state.blob}
                  className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-accent/30 bg-accent/5 text-xs font-medium text-accent hover:bg-accent/10 disabled:opacity-60"
                  title="Éditer le document (style Word)"
                >
                  <Pencil className="h-3.5 w-3.5" /> Éditer
                </button>
              )}
              {enEssai === false && (
              <button
                type="button"
                onClick={onDownload}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
              >
                <Download className="h-3.5 w-3.5" /> Télécharger (Word)
              </button>
              )}
              {!state.validated ? (
                <button
                  type="button"
                  onClick={onValidate}
                  data-testid={`validate-btn-${tpl.code}`}
                  className={`flex h-9 items-center justify-center gap-1.5 rounded-lg bg-success text-xs font-semibold text-bg-raised transition hover:bg-success/85 ${
                    onEdited || enEssai !== false ? 'col-span-2' : ''
                  }`}
                >
                  <CheckCircle className="h-3.5 w-3.5" /> Valider
                </button>
              ) : (
                <button
                  type="button"
                  onClick={onRegenerate}
                  className={`flex h-9 items-center justify-center gap-1.5 rounded-lg border border-success bg-success/10 text-xs text-success ${
                    onEdited || enEssai !== false ? 'col-span-2' : ''
                  }`}
                >
                  <RefreshCcw className="h-3.5 w-3.5" /> Régénérer
                </button>
              )}
            </div>

            {editing && onEdited && state.blob && (
              <DocumentEditModal
                blob={state.blob}
                title={title}
                filename={state.filename ?? title}
                onClose={() => setEditing(false)}
                onSaved={(blob, filename) => onEdited(blob, filename)}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}
