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
import { useCallback, useEffect, useState } from 'react';
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
import {
  generateDocument,
  GenerationRefuseeError,
  type DonneeManquante,
  type DonneeNommee,
  type TemplateInfo,
} from '../../services/workflowDocumentService';
import { workflowService, type DonneeAttendue } from '../../services/workflow.service';
import { RetourGeneration } from './RetourGeneration';
import { DonneesAttendues } from './DonneesAttendues';
import { ClausesLibresPanel } from './ClausesLibresPanel';
import { dataroomService } from '../../services/dataroom.service';
import { GeneratedDocPreview } from '../document/GeneratedDocPreview';
import { DocumentEditModal } from '../document/DocumentEditModal';
import type { DocumentType } from '../../types/dataroom';

export interface DocState {
  generating: boolean;
  generated: boolean;
  filename?: string;
  blob?: Blob;
  validated: boolean;
  error: string | null;
  previewOpen: boolean;
  /** True quand le depot auto dataroom a reussi (sur Valider). */
  depositedToDataroom: boolean;
  /** Lot L3 : donnees internes manquantes nommees par le serveur (generation refusee). */
  refus?: DonneeManquante[];
  /** Lot L3 : donnees externes manquantes de la derniere generation (« À OBTENIR »). */
  aObtenir?: DonneeNommee[];
  /** Lot L3 : donnees externes reclamees pour ce document, et si elles sont arrivees. */
  attendues?: DonneeAttendue[];
}

export function freshDocState(): DocState {
  return {
    generating: false,
    generated: false,
    validated: false,
    error: null,
    previewOpen: false,
    depositedToDataroom: false,
  };
}

export function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

/**
 * Hook factorise — gere la generation, le telechargement, la validation
 * et le depot auto dataroom pour un ensemble de templates.
 *
 * @param workflowCode    code workflow back (DISSOLUTION / LIQUIDATION / MODIFICATION)
 * @param dossierId       UUID du dossier pour depot auto (optionnel — sans, le depot est skippe)
 * @param ticketId        UUID du ticket parent (optionnel)
 * @param mapType         fonction code template -> DocumentType juridique
 * @param docs / setDocs  state externalise (la page hote persiste les flags en saveDraft)
 * @param buildPayload    callback qui retourne le payload pour la generation
 */
export interface UseDocumentBlocksParams {
  workflowCode: string;
  dossierId: string | null | undefined;
  ticketId: string | null | undefined;
  mapType: (code: string) => DocumentType;
  docs: Record<string, DocState>;
  setDocs: React.Dispatch<React.SetStateAction<Record<string, DocState>>>;
  buildPayload: () => Record<string, unknown>;
  /**
   * Nom de fichier convivial. Optionnel : sans, on garde le nom renvoye par le back
   * (Content-Disposition). `opts.version` (present uniquement si un document de meme
   * type+nom existe deja) permet d'ajouter le suffixe « - v<n> » au depot (Phase 7).
   */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
  /**
   * Phase 7 — versioning opt-in du depot Data Room. Quand true, un document de meme
   * (type + titre) deja en vigueur est REMPLACE en NOUVELLE VERSION (l'ancien part en
   * historique) ; sinon creation classique. Par defaut false (retro-compatible : les
   * autres workflows ne versionnent pas).
   */
  versioned?: boolean;
  /** Motif tracant la cause du depot versionne (ex. « Modification : … »). */
  motif?: string;
  /**
   * Fix L3 (2026-08-16) — TITRE du document logique en Data Room.
   *
   * Le titre servait aussi de CLE d'identite (type + titre) : deux seances d'un
   * meme dossier produisant un document du meme type sous le meme titre etaient
   * confondues, et le second depot passait pour un doublon. Un appelant peut donc
   * desormais rendre le titre distinctif (workflow + date de seance).
   * Sans override, on garde `tpl.documentKind || tpl.code`.
   */
  titleFor?: (tpl: TemplateInfo) => string | undefined;
}

export function useDocumentBlocks(params: UseDocumentBlocksParams) {
  const { workflowCode, dossierId, ticketId, mapType, setDocs, buildPayload, versioned, motif } =
    params;

  const updateDoc = useCallback(
    (code: string, patch: Partial<DocState>) => {
      setDocs((prev) => ({
        ...prev,
        [code]: { ...(prev[code] ?? freshDocState()), ...patch },
      }));
    },
    [setDocs],
  );

  /**
   * Lot L3 : donnees externes reclamees pour les documents du ticket, reparties par
   * document. Relues au montage et apres chaque generation.
   */
  const rafraichirAttendues = useCallback(async () => {
    if (!ticketId) return;
    try {
      const liste = await workflowService.donneesAttendues(ticketId);
      setDocs((prev) => {
        const next = { ...prev };
        const codes = new Set([...Object.keys(prev), ...liste.map((a) => a.templateCode)]);
        for (const code of codes) {
          next[code] = { ...(prev[code] ?? freshDocState()), attendues: liste.filter((a) => a.templateCode === code) };
        }
        return next;
      });
    } catch {
      // Lecture seulement indicative : l'echec n'empeche pas de generer ; la reclamation
      // elle-meme est faite par le serveur a chaque generation.
    }
  }, [ticketId, setDocs]);

  useEffect(() => {
    void rafraichirAttendues();
  }, [rafraichirAttendues]);

  const depositGeneratedToDataroom = useCallback(
    async (tpl: TemplateInfo, st: DocState) => {
      if (!dossierId || !st.blob || !st.filename) return;
      if (st.depositedToDataroom) return;
      const targetType = mapType(tpl.code);
      // Fix L3 — titre distinctif quand l'appelant en fournit un (cf. titleFor).
      const targetTitle = params.titleFor?.(tpl) || tpl.documentKind || tpl.code;
      try {
        // Phase 7 — versioning opt-in : on cherche un document logique existant de
        // meme (type + titre) pour le remplacer en NOUVELLE version ; le n° de version
        // alimente aussi le suffixe « - v<n> » du nom de fichier.
        let existingDocumentId: string | undefined;
        let matchedVersion: number | undefined;
        if (versioned) {
          try {
            const view = await dataroomService.getJuridique(dossierId);
            const match = view.documentsEnVigueur.find(
              (d) => d.documentType === targetType && d.title === targetTitle,
            );
            existingDocumentId = match?.id;
            matchedVersion = match?.version;
          } catch {
            /* best-effort : sans la vue, on retombe sur une creation classique */
          }
        }
        const nextVersion = existingDocumentId ? (matchedVersion ?? 1) + 1 : undefined;
        const filename = params.filenameFor?.(tpl, { version: nextVersion }) || st.filename;
        await dataroomService.depositGeneratedDoc(dossierId, st.blob, {
          documentType: targetType,
          title: targetTitle,
          filename,
          ticketId: ticketId ?? undefined,
          motif: versioned ? motif : undefined,
          replacePrevious: !!existingDocumentId,
          existingDocumentId,
        });
        updateDoc(tpl.code, { depositedToDataroom: true });
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Echec depot Dataroom';
        updateDoc(tpl.code, {
          error: `Genere/valide OK. Depot Dataroom : ${msg}`,
        });
      }
    },
    [dossierId, ticketId, mapType, updateDoc, versioned, motif, params],
  );

  const generateOne = useCallback(
    async (tpl: TemplateInfo) => {
      updateDoc(tpl.code, { generating: true, error: null, refus: [], validated: false });
      try {
        // dossierId permet au backend d'enrichir l'identite societe depuis la BD
        // (capital, siege, RC, ville du greffe, parts...) — en-tete PV complet meme
        // quand le formulaire ne porte que la denomination. Le payload metier reste
        // prioritaire pour tout le reste ; la BD gagne uniquement sur l'identite.
        const payload = buildPayload();
        if (dossierId && payload.dossierId == null) payload.dossierId = dossierId;
        // Lot L3 : le ticket permet au serveur de reclamer les donnees a obtenir.
        if (ticketId && payload.ticketId == null) payload.ticketId = ticketId;
        const { blob, filename, donneesAObtenir } = await generateDocument(
          workflowCode,
          tpl.code,
          payload,
        );
        const friendly = params.filenameFor?.(tpl);
        updateDoc(tpl.code, {
          generating: false,
          generated: true,
          blob,
          filename: friendly || filename,
          previewOpen: true,
          depositedToDataroom: false,
          aObtenir: donneesAObtenir,
        });
        await rafraichirAttendues();
      } catch (err) {
        updateDoc(tpl.code, {
          generating: false,
          error: err instanceof Error ? err.message : 'Génération impossible.',
          refus: err instanceof GenerationRefuseeError ? err.donneesManquantes : [],
        });
      }
    },
    [workflowCode, dossierId, ticketId, buildPayload, updateDoc, params, rafraichirAttendues],
  );

  const downloadOne = useCallback(
    (tpl: TemplateInfo) => {
      const st = params.docs[tpl.code];
      if (st?.blob && st.filename) triggerBrowserDownload(st.blob, st.filename);
    },
    [params.docs],
  );

  const validateOne = useCallback(
    async (tpl: TemplateInfo) => {
      const st = params.docs[tpl.code];
      if (!st?.generated) return;
      updateDoc(tpl.code, { validated: true });
      await depositGeneratedToDataroom(tpl, { ...st, validated: true });
    },
    [params.docs, updateDoc, depositGeneratedToDataroom],
  );

  return { updateDoc, generateOne, downloadOne, validateOne };
}

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
            <h4 className="truncate text-sm font-semibold text-fg" title={tpl.code}>
              {title}
            </h4>
          </div>
          <p className="mt-0.5 truncate text-[11px] text-fg-subtle">{tpl.code}</p>
        </div>
        {state.validated && (
          <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
            <CheckCircle className="h-3 w-3" /> Valide
            {state.depositedToDataroom && (
              <span className="text-[9px] font-normal opacity-80">
                · Dataroom
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
                <Loader className="h-4 w-4 animate-spin" /> Generation…
              </>
            ) : (
              <>
                <Sparkles className="h-4 w-4" /> Generer
              </>
            )}
          </button>
        ) : (
          <>
            <div className="flex items-center gap-2 rounded-lg border border-success/30 bg-success/5 p-2 text-xs">
              <CheckCircle className="h-4 w-4 text-success" />
              <span className="flex-1 truncate text-fg">
                {state.filename ?? 'Document genere'}
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
            {state.previewOpen && !state.blob && (
              <div className="rounded-lg border border-border bg-bg-overlay p-3 text-[11px] text-fg-subtle">
                <Eye className="mr-1 inline h-3.5 w-3.5 align-text-bottom" />
                Aperçu indisponible : régénérez le document pour l'afficher.
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
                {state.previewOpen ? 'Masquer apercu' : 'Aperçu'}
              </button>
              <button
                type="button"
                onClick={onRegenerate}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
                title="Régénérer le document après mise à jour des valeurs amont"
              >
                <RefreshCcw className="h-3.5 w-3.5" /> Régénérer
              </button>
              {onEdited && (
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
              <button
                type="button"
                onClick={onDownload}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
              >
                <Download className="h-3.5 w-3.5" /> .docx
              </button>
              {!state.validated ? (
                <button
                  type="button"
                  onClick={onValidate}
                  data-testid={`validate-btn-${tpl.code}`}
                  className={`flex h-9 items-center justify-center gap-1.5 rounded-lg bg-success text-xs font-semibold text-bg-raised transition hover:bg-success/85 ${
                    onEdited ? 'col-span-2' : ''
                  }`}
                >
                  <CheckCircle className="h-3.5 w-3.5" /> Valider
                </button>
              ) : (
                <button
                  type="button"
                  onClick={onRegenerate}
                  className={`flex h-9 items-center justify-center gap-1.5 rounded-lg border border-success bg-success/10 text-xs text-success ${
                    onEdited ? 'col-span-2' : ''
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
