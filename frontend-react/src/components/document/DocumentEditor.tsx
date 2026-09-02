import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Table } from '@tiptap/extension-table';
import { TableRow } from '@tiptap/extension-table-row';
import { TableCell } from '@tiptap/extension-table-cell';
import { TableHeader } from '@tiptap/extension-table-header';
import {
  Bold,
  Italic,
  Underline as UnderlineIcon,
  Strikethrough,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  Quote,
  Undo2,
  Redo2,
  Table as TableIcon,
  Save,
  FileText,
  Download,
  Eye,
  Pencil,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { documentService } from '../../services/document.service';

/**
 * Editeur WYSIWYG inline pour les documents générés (Sprint 2026-06-12).
 *
 * Flow :
 *  1) Le composant reçoit un Blob .docx (généré par le backend)
 *  2) Mammoth.js convertit le DOCX en HTML côté navigateur
 *  3) TipTap édite le HTML, avec barre d'outils
 *  4) Sauvegarder → POST vers le backend HtmlToDocxConverter → écrasement dataroom
 *  5) Boutons : Télécharger DOCX / Télécharger PDF
 *
 * Mode contrôlé : si `value` (HTML) est fourni, l'éditeur l'utilise directement
 * et ignore le docxBlob.
 */
export interface DocumentEditorProps {
  /** Blob du .docx à ouvrir. */
  docxBlob?: Blob;
  /** Ou bien HTML déjà extrait — prioritaire si présent. */
  value?: string;
  /** Nom de fichier de base (sans extension OU avec .docx). */
  filename: string;
  /** Titre affiché en tête PDF. */
  title?: string;
  /** Callback déclenché à la sauvegarde réussie (HTML édité). */
  onSave?: (html: string) => void | Promise<void>;
  /** Lecture seule (preview). */
  readOnly?: boolean;
}

export function DocumentEditor({
  docxBlob,
  value,
  filename,
  title,
  onSave,
  readOnly = false,
}: DocumentEditorProps) {
  const [initialHtml, setInitialHtml] = useState<string | null>(value ?? null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState<'docx' | 'pdf' | null>(null);
  // BLOC B 2026-06-21 — Mode d'affichage. Par defaut = "fidele" : rendu
  // docx-preview qui preserve les styles Word (polices, marges, tableaux,
  // numerotation, styles JurikaTitreArticle/SousTitre). L'utilisateur bascule
  // vers "edition" pour modifier librement via TipTap.
  const [mode, setMode] = useState<'fidele' | 'edition'>(
    docxBlob && !value ? 'fidele' : 'edition',
  );
  const fidelityRef = useRef<HTMLDivElement | null>(null);
  const [fidelityError, setFidelityError] = useState<string | null>(null);

  const editor = useEditor(
    {
      extensions: [
        StarterKit,
        Table.configure({ resizable: false }),
        TableRow,
        TableHeader,
        TableCell,
      ],
      content: initialHtml ?? '',
      editable: !readOnly,
      editorProps: {
        attributes: {
          class:
            'prose prose-sm sm:prose-base lg:prose-lg max-w-none p-4 min-h-[400px] focus:outline-none',
        },
      },
    },
    [initialHtml, readOnly],
  );

  // BLOC B 2026-06-21 — Apercu FIDELE via docx-preview : on rend le .docx
  // directement dans un container, ce qui preserve polices/marges/styles Word.
  // Lazy-import du paquet pour ne pas charger ~300 Ko si l'utilisateur reste
  // en mode "edition".
  useEffect(() => {
    if (mode !== 'fidele' || !docxBlob || !fidelityRef.current) return;
    let cancelled = false;
    const container = fidelityRef.current;
    container.innerHTML = '';
    (async () => {
      try {
        const docxPreview = await import('docx-preview');
        if (cancelled) return;
        await docxPreview.renderAsync(docxBlob, container, undefined, {
          className: 'docx-fidele',
          inWrapper: true,
          ignoreWidth: false,
          ignoreHeight: false,
          ignoreFonts: false,
          breakPages: true,
          useBase64URL: true,
        });
      } catch (e) {
        if (!cancelled) setFidelityError((e as Error).message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [mode, docxBlob]);

  // Charge le HTML pour le mode EDITION (lazy : seulement quand l'utilisateur
  // bascule). Conserve Mammoth comme back-end de conversion .docx -> HTML
  // (TipTap a besoin d'un HTML pour ouvrir le doc en edition).
  useEffect(() => {
    if (mode !== 'edition') return;
    if (initialHtml !== null || !docxBlob) return;
    let cancelled = false;
    (async () => {
      try {
        const arrayBuffer = await docxBlob.arrayBuffer();
        const mammoth = await import('mammoth');
        const result = await mammoth.convertToHtml({ arrayBuffer });
        if (!cancelled) setInitialHtml(result.value);
      } catch (e) {
        if (!cancelled) setLoadError((e as Error).message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [mode, docxBlob, initialHtml]);

  if (loadError) {
    return (
      <div className="border border-red-300 bg-red-50 p-4 rounded text-sm text-red-800">
        Erreur de chargement du document : {loadError}
      </div>
    );
  }

  // En mode "fidele", on a juste besoin du blob — pas du tiptap.
  if (mode === 'edition' && !editor) {
    return (
      <div className="border border-slate-200 bg-slate-50 p-4 rounded text-sm text-slate-600">
        Chargement de l'éditeur…
      </div>
    );
  }

  const handleSave = async () => {
    if (!onSave) return;
    setSaving(true);
    try {
      await onSave(editor.getHTML());
    } finally {
      setSaving(false);
    }
  };

  const handleExportDocx = async () => {
    if (!editor) return;
    setExporting('docx');
    try {
      await documentService.exportHtmlAsDocx(editor.getHTML(), ensureExt(filename, 'docx'), title);
    } finally {
      setExporting(null);
    }
  };

  const handleExportPdf = async () => {
    if (!editor) return;
    setExporting('pdf');
    try {
      await documentService.exportHtmlAsPdf(editor.getHTML(), ensureExt(filename, 'pdf'), title);
    } finally {
      setExporting(null);
    }
  };

  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden bg-white">
      {/* BLOC B 2026-06-21 — Toggle Aperçu fidèle / Édition libre. Mode fidèle
          = docx-preview (styles Word préservés) ; édition = TipTap. */}
      {docxBlob && !readOnly && (
        <div className="flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-3 py-2 text-xs">
          <button
            type="button"
            onClick={() => setMode('fidele')}
            className={`inline-flex items-center gap-1 rounded px-2 py-1 font-medium ${
              mode === 'fidele'
                ? 'bg-slate-900 text-white'
                : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            <Eye className="h-3.5 w-3.5" /> Aperçu fidèle
          </button>
          <button
            type="button"
            onClick={() => setMode('edition')}
            className={`inline-flex items-center gap-1 rounded px-2 py-1 font-medium ${
              mode === 'edition'
                ? 'bg-slate-900 text-white'
                : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            <Pencil className="h-3.5 w-3.5" /> Édition libre
          </button>
          <span className="ml-auto text-[11px] text-slate-500">
            {mode === 'fidele'
              ? 'Styles Word préservés (polices, marges, hiérarchie).'
              : 'Modifications libres — l\'export reflète ce qui est affiché.'}
          </span>
        </div>
      )}
      {!readOnly && mode === 'edition' && editor && (
        <div className="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-slate-50 p-2">
          <ToolbarBtn
            active={editor.isActive('bold')}
            onClick={() => editor.chain().focus().toggleBold().run()}
            title="Gras"
          >
            <Bold className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('italic')}
            onClick={() => editor.chain().focus().toggleItalic().run()}
            title="Italique"
          >
            <Italic className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('underline')}
            onClick={() => editor.chain().focus().toggleUnderline().run()}
            title="Souligné"
          >
            <UnderlineIcon className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('strike')}
            onClick={() => editor.chain().focus().toggleStrike().run()}
            title="Barré"
          >
            <Strikethrough className="h-4 w-4" />
          </ToolbarBtn>

          <span className="mx-1 h-6 w-px bg-slate-300" />

          <ToolbarBtn
            active={editor.isActive('heading', { level: 1 })}
            onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
            title="Titre 1"
          >
            <Heading1 className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('heading', { level: 2 })}
            onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
            title="Titre 2"
          >
            <Heading2 className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('heading', { level: 3 })}
            onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
            title="Titre 3"
          >
            <Heading3 className="h-4 w-4" />
          </ToolbarBtn>

          <span className="mx-1 h-6 w-px bg-slate-300" />

          <ToolbarBtn
            active={editor.isActive('bulletList')}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
            title="Liste à puces"
          >
            <List className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('orderedList')}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
            title="Liste numérotée"
          >
            <ListOrdered className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            active={editor.isActive('blockquote')}
            onClick={() => editor.chain().focus().toggleBlockquote().run()}
            title="Citation"
          >
            <Quote className="h-4 w-4" />
          </ToolbarBtn>

          <span className="mx-1 h-6 w-px bg-slate-300" />

          <ToolbarBtn
            onClick={() =>
              editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
            }
            title="Insérer un tableau"
          >
            <TableIcon className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().undo().run()}
            disabled={!editor.can().undo()}
            title="Annuler"
          >
            <Undo2 className="h-4 w-4" />
          </ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().redo().run()}
            disabled={!editor.can().redo()}
            title="Rétablir"
          >
            <Redo2 className="h-4 w-4" />
          </ToolbarBtn>

          <div className="ml-auto flex items-center gap-1">
            {onSave && (
              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="inline-flex items-center gap-1 rounded bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                <Save className="h-4 w-4" />
                {saving ? 'Enregistrement…' : 'Enregistrer'}
              </button>
            )}
            <button
              type="button"
              onClick={handleExportDocx}
              disabled={exporting !== null}
              className="inline-flex items-center gap-1 rounded border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
              title="Télécharger en .docx"
            >
              <FileText className="h-4 w-4" />
              {exporting === 'docx' ? 'Export…' : '.docx'}
            </button>
            <button
              type="button"
              onClick={handleExportPdf}
              disabled={exporting !== null}
              className="inline-flex items-center gap-1 rounded border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
              title="Télécharger en .pdf"
            >
              <Download className="h-4 w-4" />
              {exporting === 'pdf' ? 'Export…' : '.pdf'}
            </button>
          </div>
        </div>
      )}

      {/* BLOC B 2026-06-21 — Rendu fidele (docx-preview) ou edition (TipTap). */}
      {mode === 'fidele' && docxBlob ? (
        <div className="overflow-auto bg-slate-100 p-4">
          {fidelityError ? (
            <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
              Aperçu fidèle indisponible : {fidelityError}. Basculez en
              «&nbsp;Édition libre&nbsp;» pour visualiser le document.
            </div>
          ) : (
            <div
              ref={fidelityRef}
              className="mx-auto max-w-full"
              aria-label="Aperçu fidèle du document Word"
            />
          )}
        </div>
      ) : (
        <EditorContent editor={editor} />
      )}
    </div>
  );
}

function ToolbarBtn(props: {
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  title: string;
  children: React.ReactNode;
}) {
  const { active, disabled, onClick, title, children } = props;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={
        'rounded p-1.5 text-slate-600 hover:bg-slate-200 disabled:opacity-40 disabled:hover:bg-transparent ' +
        (active ? 'bg-slate-300 text-slate-900' : '')
      }
    >
      {children}
    </button>
  );
}

function ensureExt(name: string, ext: 'docx' | 'pdf'): string {
  const clean = name.trim() || 'document';
  if (clean.toLowerCase().endsWith('.' + ext)) return clean;
  return clean.replace(/\.[^.]+$/, '') + '.' + ext;
}
