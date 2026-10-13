/**
 * Etat et hook de la generation documentaire (lot L6) : separes du bloc visuel
 * (WorkflowDocumentBlock.tsx) pour que ce dernier n'exporte que des composants
 * (rafraichissement a chaud de Vite). Voir l'en-tete de WorkflowDocumentBlock.tsx.
 */
import { useCallback, useEffect } from 'react';
import {
  generateDocument,
  GenerationRefuseeError,
  type DonneeManquante,
  type DonneeNommee,
  type TemplateInfo,
} from '../../services/workflowDocumentService';
import { workflowService, type DonneeAttendue } from '../../services/workflow.service';
import { dataroomService } from '../../services/dataroom.service';
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
  /** Lot L3 : donnees externes manquantes de la derniere generation ("A OBTENIR"). */
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
        const msg = err instanceof Error ? err.message : 'erreur inconnue';
        updateDoc(tpl.code, {
          error: `Document validé, mais son dépôt dans la Data Room a échoué (${msg}). Régénérez-le puis validez-le de nouveau.`,
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
