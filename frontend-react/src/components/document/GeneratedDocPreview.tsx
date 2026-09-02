import { useState } from 'react';
import { Eye, Maximize2, X } from 'lucide-react';
import { DocumentEditor } from './DocumentEditor';

/**
 * 2026-08-12 — Aperçu fidèle d'un document généré, réutilisable par tous les
 * blocs de génération documentaire (Step7 Création, WorkflowDocumentBlock partagé
 * Modification/Dissolution/Liquidation/Succursales, DocumentBlock local
 * Modification). Remplace l'ancien placeholder texte « Document binaire (DOCX)… ».
 *
 * Rendu FIDÈLE via {@link DocumentEditor} en lecture seule (docx-preview,
 * styles Word préservés) :
 *   - **mini-aperçu inline** (hauteur bornée, défilable) visible directement ;
 *   - bouton **« Plein écran »** ouvrant le même rendu en modal pleine largeur
 *     (`min(1200px,95vw)` × `90vh`, défilable).
 */
export interface GeneratedDocPreviewProps {
  /** Blob .docx à rendre (obligatoire — le caller gère l'absence de blob). */
  blob: Blob;
  /** Libellé « type » affiché en tête (ex. « Statuts », « PV — Modification »). */
  title: string;
  /** Nom de fichier convivial (aperçu/titre). */
  filename: string;
}

export function GeneratedDocPreview({ blob, title, filename }: GeneratedDocPreviewProps) {
  const [fullScreen, setFullScreen] = useState(false);
  return (
    <div className="rounded-lg border border-border bg-bg-overlay">
      <div className="flex items-center justify-between gap-2 px-3 py-2">
        <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-fg-subtle">
          <Eye className="h-3.5 w-3.5" /> Aperçu du document
        </span>
        <button
          type="button"
          onClick={() => setFullScreen(true)}
          title="Ouvrir l'aperçu en plein écran"
          className="inline-flex items-center gap-1 rounded border border-border px-1.5 py-0.5 text-[11px] text-fg-subtle hover:border-accent hover:text-fg"
        >
          <Maximize2 className="h-3 w-3" /> Plein écran
        </button>
      </div>
      <div className="max-h-[460px] overflow-auto border-t border-border">
        <DocumentEditor docxBlob={blob} filename={filename} title={title} readOnly />
      </div>

      {fullScreen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex flex-col bg-black/60 backdrop-blur-sm"
          onClick={(e) => {
            if (e.target === e.currentTarget) setFullScreen(false);
          }}
        >
          <div className="m-auto flex h-[90vh] w-[min(1200px,95vw)] flex-col overflow-hidden rounded-xl bg-bg-raised shadow-xl">
            <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-4 py-3">
              <div className="flex items-center gap-2">
                <Eye className="h-4 w-4 text-accent" />
                <h3 className="text-sm font-semibold text-fg">Aperçu — {title}</h3>
              </div>
              <button
                type="button"
                onClick={() => setFullScreen(false)}
                className="rounded p-1 text-fg-subtle hover:bg-border"
                aria-label="Fermer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="flex-1 overflow-auto p-4">
              <DocumentEditor docxBlob={blob} filename={filename} title={title} readOnly />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
