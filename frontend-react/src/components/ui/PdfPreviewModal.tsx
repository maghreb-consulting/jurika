import { useEffect, useRef, useState } from 'react';
import { Download, FileText, Loader2, Printer, X } from 'lucide-react';
import { Button } from './Button';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';

/**
 * Sprint 7 / TASK 3.3 -- Modal d'apercu PDF reutilisable.
 *
 * Strategie : fetch du PDF via axios (avec JWT), conversion Blob -> object URL,
 * affichage dans un <iframe>. Le browser natif rend le PDF (zoom, pages,
 * recherche disponibles via la toolbar du navigateur).
 *
 * Permissions :
 *   - Bouton "Telecharger" affiche si canDownload (= setting permDownload pour
 *     CLIENT, true par defaut pour EMPLOYE/+)
 *   - Bouton "Imprimer" affiche si canPrint
 *   - Si canDownload=false : le boutonn'est juste pas rendu, l'URL Blob reste
 *     techniquement accessible (un user motive peut toujours screenshot). C'est
 *     un soft-control (l'enforcement strict est cote backend).
 *
 * UX :
 *   - Close ESC + click outside backdrop
 *   - Focus trap basique (focus l'iframe a l'ouverture)
 *   - Erreur affichee dans un encart si le fetch echoue (SUSPENDED, 404, ...)
 *   - Object URL revoque a la fermeture pour eviter la fuite memoire
 */
interface Props {
  open: boolean;
  documentId: string | null;
  filename: string;
  canDownload?: boolean;
  canPrint?: boolean;
  onClose: () => void;
  onDownload?: () => void; // callback delegue au caller (reuse downloadDocument)
  /**
   * Lot V -- fetch du blob d'apercu personnalisable. Defaut =
   * dataroomService.previewDocument (juridique, comportement historique). La vue
   * Depots passe previewDepot ; le rendu image/PDF est deja gere ci-dessous.
   */
  fetchPreview?: (id: string) => Promise<{ url: string; contentType: string }>;
}

export function PdfPreviewModal({
  open,
  documentId,
  filename,
  canDownload = false,
  canPrint = false,
  onClose,
  onDownload,
  fetchPreview,
}: Props) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [contentType, setContentType] = useState<string>('application/pdf');
  const iframeRef = useRef<HTMLIFrameElement | null>(null);

  // Effet : fetch du Blob a chaque ouverture pour un nouveau documentId
  useEffect(() => {
    if (!open || !documentId) return;

    let cancelled = false;
    let currentUrl: string | null = null;
    setLoading(true);
    setError(null);

    const doFetch = fetchPreview ?? dataroomService.previewDocument;
    doFetch(documentId)
      .then(({ url, contentType: ct }) => {
        if (cancelled) {
          // Caller a ferme entre temps -> revoke immediat
          window.URL.revokeObjectURL(url);
          return;
        }
        currentUrl = url;
        setObjectUrl(url);
        setContentType(ct);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(extractError(err).message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      if (currentUrl) window.URL.revokeObjectURL(currentUrl);
      setObjectUrl(null);
    };
  }, [open, documentId]);

  // ESC pour fermer
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  // Focus iframe a l'ouverture (basique focus trap)
  useEffect(() => {
    if (open && objectUrl && iframeRef.current) {
      iframeRef.current.focus();
    }
  }, [open, objectUrl]);

  function handlePrint() {
    // Lot X -- tracer l'impression dans l'"Activite client" (best-effort,
    // fire-and-forget : ne bloque jamais l'impression, ne l'attend pas).
    if (documentId) void dataroomService.logPrint(documentId);
    // Image : pas d'iframe rendu -> on ouvre l'URL dans un onglet pour imprimer.
    if (!iframeRef.current) {
      if (objectUrl) window.open(objectUrl, '_blank', 'noopener');
      return;
    }
    // Strategie navigateur natif : ouvrir window.print sur l'iframe.
    // contentWindow peut etre null si le PDF n'est pas charge -> guard.
    const w = iframeRef.current.contentWindow;
    try {
      w?.focus();
      w?.print();
    } catch {
      // Quelques navigateurs bloquent print sur cross-origin iframe.
      // Fallback : ouvrir l'URL dans nouvel onglet, l'utilisateur peut imprimer.
      if (objectUrl) window.open(objectUrl, '_blank', 'noopener');
    }
  }

  if (!open) return null;

  const ctLower = contentType.toLowerCase();
  const isPdf = ctLower.includes('pdf');
  // 2026-07-04 — les images (PNG/JPG/GIF/WebP/SVG...) sont previsualisables
  // directement via <img>. Avant, tout ce qui n'etait pas PDF tombait sur le
  // message "Apercu non disponible" (les pieces scannees en image restaient
  // invisibles). Le back streame deja INLINE avec le bon content-type.
  const isImage = ctLower.startsWith('image/');

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-fg/60 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label={`Apercu de ${filename}`}
      onClick={(e) => {
        // Click outside = close (mais pas clicks sur l'iframe ou le header)
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="m-4 flex flex-1 flex-col overflow-hidden rounded-xl bg-bg-raised shadow-2xl md:m-6">
        <header className="flex items-center justify-between border-b border-border px-5 py-3">
          <div className="flex min-w-0 items-center gap-2">
            <FileText className="h-5 w-5 shrink-0 text-accent" />
            <h3 className="truncate text-sm font-semibold text-fg">
              {filename}
            </h3>
          </div>
          <div className="flex items-center gap-2">
            {canDownload && onDownload && (
              <Button variant="secondary" size="sm" onClick={onDownload}>
                <Download className="mr-1 h-4 w-4" />
                Telecharger
              </Button>
            )}
            {canPrint && (isPdf || isImage) && objectUrl && (
              <Button variant="secondary" size="sm" onClick={handlePrint}>
                <Printer className="mr-1 h-4 w-4" />
                Imprimer
              </Button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="rounded-full p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
              aria-label="Fermer l'apercu"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </header>

        <div className="flex flex-1 items-center justify-center overflow-hidden bg-bg-overlay">
          {loading && (
            <div className="flex flex-col items-center gap-2 text-fg-subtle">
              <Loader2 className="h-8 w-8 animate-spin text-accent" />
              <p className="text-sm">Chargement de l'apercu...</p>
            </div>
          )}
          {!loading && error && (
            <div className="max-w-md rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-center text-sm text-danger">
              <p className="font-semibold">Apercu indisponible</p>
              <p className="mt-1 text-xs">{error}</p>
            </div>
          )}
          {!loading && !error && objectUrl && isPdf && (
            <iframe
              ref={iframeRef}
              src={objectUrl}
              title={`Apercu PDF -- ${filename}`}
              className="h-full w-full border-0"
            />
          )}
          {!loading && !error && objectUrl && isImage && (
            <div className="flex h-full w-full items-center justify-center overflow-auto p-4">
              <img
                src={objectUrl}
                alt={`Apercu -- ${filename}`}
                className="max-h-full max-w-full object-contain"
              />
            </div>
          )}
          {!loading && !error && objectUrl && !isPdf && !isImage && (
            <div className="max-w-md rounded-lg border border-amber-200 bg-warning/10 px-4 py-3 text-center text-sm text-warning">
              <p className="font-semibold">Apercu non disponible pour ce type</p>
              <p className="mt-1 text-xs">
                Format detecte : <strong>{contentType}</strong>. Telechargez le
                fichier pour l'ouvrir avec une application adaptee.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
