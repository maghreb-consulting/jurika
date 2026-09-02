import { Pencil, X } from 'lucide-react';
import { DocumentEditor } from './DocumentEditor';
import { documentService } from '../../services/document.service';

/**
 * 2026-08-12 — Modal d'édition WYSIWYG d'un document généré, réutilisable par
 * tous les blocs de génération (aligné sur l'« Éditer » de Step7 Création).
 *
 * Ouvre le .docx dans {@link DocumentEditor} en mode édition ; à la sauvegarde,
 * reconvertit le HTML édité en .docx (`documentService.convertHtmlToDocx`) et
 * remonte le nouveau blob via `onSaved` (le caller remplace le blob en mémoire
 * et repasse le document en non-validé pour forcer un nouveau dépôt Data Room).
 */
export interface DocumentEditModalProps {
  blob: Blob;
  /** Libellé « type » affiché en tête (ex. « Statuts », « PV — Modification »). */
  title: string;
  /** Nom de fichier convivial conservé pour le blob réécrit. */
  filename: string;
  onClose: () => void;
  onSaved: (blob: Blob, filename: string) => void;
}

export function DocumentEditModal({ blob, title, filename, onClose, onSaved }: DocumentEditModalProps) {
  const handleSave = async (editedHtml: string) => {
    const newBlob = await documentService.convertHtmlToDocx(editedHtml, filename, title);
    onSaved(newBlob, filename);
    onClose();
  };
  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex flex-col bg-black/60 backdrop-blur-sm"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="m-auto flex h-[90vh] w-[min(1200px,95vw)] flex-col overflow-hidden rounded-xl bg-bg-raised shadow-xl">
        <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-4 py-3">
          <div className="flex items-center gap-2">
            <Pencil className="h-4 w-4 text-accent" />
            <h3 className="text-sm font-semibold text-fg">Édition — {title}</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-fg-subtle hover:bg-border"
            aria-label="Fermer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-auto p-4">
          <DocumentEditor docxBlob={blob} filename={filename} title={title} onSave={handleSave} />
        </div>
      </div>
    </div>
  );
}
