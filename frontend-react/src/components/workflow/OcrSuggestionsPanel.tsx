import { Check, FileText, Sparkles, X } from 'lucide-react';

/**
 * Panneau de suggestions OCR — composant generique reutilisable par toutes
 * les etapes qui consomment de l'OCR (Certificat Negatif Step1, CIN, etc).
 *
 * Principes (PARTIE B 2026-06-04) :
 *  - N'ECRASE JAMAIS un champ deja saisi par l'utilisateur. Chaque champ est
 *    propose en suggestion ; l'utilisateur clique "Appliquer" par champ ou
 *    "Tout appliquer (champs vides uniquement)".
 *  - Affiche source (provider OCR + mode : PDF texte / PDF rendu / image) +
 *    confidence en % + warnings -- transparence sur la fiabilite.
 *  - Si l'extraction est insuffisante, le user reste libre de tout saisir
 *    manuellement (zero blocage).
 */

export interface OcrSuggestion {
  /** Cle interne (ex : "ice"), doit matcher la cle du form de destination. */
  field: string;
  /** Libelle affiche au user (ex : "Numero ICE"). */
  label: string;
  /** Valeur proposee par l'OCR / LLM. */
  value: string;
}

export interface OcrSuggestionsPanelProps {
  /** Suggestions a afficher. Si vide, le panel rend un message dedie. */
  suggestions: OcrSuggestion[];
  /** Source OCR (provider) — "OCR_TESSERACT" | "OCR_MANUAL_FALLBACK" | "OCR_LLM_HYBRID" ... */
  source?: string;
  /** Mode technique : PDFBOX_TEXT (PDF numerique), PDFBOX_RENDER_TESSERACT (PDF scanne), TESSERACT_IMAGE, FALLBACK. */
  extractionMode?: string;
  /** Score [0..1], affiche en % */
  confidence?: number;
  warnings?: string[];
  /** Vrai si l'extraction a renvoye requiresManualEntry=true */
  manualFallback?: boolean;
  /** Vrai pendant l'extraction (UI non bloquante : on peut continuer la saisie). */
  loading?: boolean;
  /** Nom du fichier uploade -- affiche en en-tete. */
  filename?: string;
  /** Callback : applique la valeur du champ donne dans le form parent. */
  onApplyOne: (field: string, value: string) => void;
  /** Callback : applique TOUTES les suggestions DONT le champ destination est vide. */
  onApplyAllEmpty: () => void;
  /** Callback : ferme le panneau (supprime les suggestions). */
  onDismiss?: () => void;
}

function formatMode(mode?: string): string {
  switch (mode) {
    case 'PDFBOX_TEXT':
      return 'PDF numerique (couche texte)';
    case 'PDFBOX_RENDER_TESSERACT':
      return 'PDF scanne (OCR Tesseract)';
    case 'TESSERACT_IMAGE':
      return 'Image (OCR Tesseract)';
    case 'FALLBACK':
      return 'OCR indisponible (saisie manuelle)';
    default:
      return mode ?? 'mode inconnu';
  }
}

export function OcrSuggestionsPanel({
  suggestions,
  source,
  extractionMode,
  confidence,
  warnings,
  manualFallback,
  loading,
  filename,
  onApplyOne,
  onApplyAllEmpty,
  onDismiss,
}: OcrSuggestionsPanelProps) {
  if (loading) {
    return (
      <div className="rounded-xl border-2 border-indigo-200 bg-indigo-50 p-4">
        <div className="flex items-center gap-3">
          <Sparkles className="h-5 w-5 animate-pulse text-indigo-600" />
          <div className="flex-1">
            <p className="text-sm font-semibold text-indigo-900">Extraction IA en cours...</p>
            <p className="text-xs text-indigo-700">
              {filename ?? 'document'} — vous pouvez continuer la saisie manuelle pendant l'analyse.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!suggestions || suggestions.length === 0) {
    if (manualFallback) {
      return (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-4">
          <div className="flex items-start gap-3">
            <FileText className="mt-0.5 h-5 w-5 text-amber-600" />
            <div className="flex-1">
              <p className="text-sm font-semibold text-amber-900">
                Aucun champ extrait automatiquement
              </p>
              <p className="mt-1 text-xs text-amber-800">
                Mode : {formatMode(extractionMode)}.{' '}
                {warnings && warnings.length > 0 ? warnings[0] : 'Continuez en saisie manuelle.'}
              </p>
              {onDismiss && (
                <button
                  onClick={onDismiss}
                  className="mt-2 text-xs text-amber-700 underline hover:text-amber-900"
                >
                  Masquer
                </button>
              )}
            </div>
          </div>
        </div>
      );
    }
    return null;
  }

  const confidencePercent = confidence != null ? Math.round(confidence * 100) : null;
  const sourceLabel = source?.replace(/^OCR_/, '').replace(/_/g, ' ').toLowerCase() ?? 'inconnu';

  return (
    <div className="rounded-xl border-2 border-indigo-200 bg-indigo-50 p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Sparkles className="mt-0.5 h-5 w-5 text-indigo-600" />
          <div>
            <p className="text-sm font-semibold text-indigo-900">
              Suggestions extraites — {suggestions.length} champ
              {suggestions.length > 1 ? 's' : ''}
            </p>
            <p className="text-xs text-indigo-700">
              {filename ? `${filename} · ` : ''}
              {formatMode(extractionMode)}
              {confidencePercent != null && ` · confiance ${confidencePercent}%`}
              {sourceLabel && ` · source ${sourceLabel}`}
            </p>
          </div>
        </div>
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            className="rounded p-1 text-indigo-600 hover:bg-indigo-100"
            aria-label="Fermer les suggestions"
            title="Fermer (les valeurs deja appliquees restent)"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>

      {warnings && warnings.length > 0 && (
        <ul className="mb-3 ml-2 list-disc text-xs text-amber-800">
          {warnings.map((w, i) => (
            <li key={i}>{w}</li>
          ))}
        </ul>
      )}

      <div className="space-y-2">
        {suggestions.map((s) => (
          <div
            key={s.field}
            className="flex items-center gap-2 rounded-lg border border-indigo-100 bg-bg-raised px-3 py-2"
          >
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-indigo-900">{s.label}</p>
              <p className="truncate text-sm text-fg" title={s.value}>
                {s.value}
              </p>
            </div>
            <button
              type="button"
              onClick={() => onApplyOne(s.field, s.value)}
              className="flex h-8 items-center gap-1 rounded-md border border-indigo-300 bg-indigo-100 px-2.5 text-xs font-medium text-indigo-800 hover:bg-indigo-200"
            >
              <Check className="h-3.5 w-3.5" /> Appliquer
            </button>
          </div>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-indigo-200 pt-3">
        <p className="text-xs text-indigo-700">
          L'OCR ne remplit JAMAIS un champ deja saisi. Choisissez champ par champ.
        </p>
        <button
          type="button"
          onClick={onApplyAllEmpty}
          className="flex items-center gap-1 rounded-md bg-indigo-600 px-3 py-1.5 text-xs font-medium text-bg-raised hover:bg-indigo-700"
        >
          <Check className="h-3.5 w-3.5" /> Appliquer aux champs vides uniquement
        </button>
      </div>
    </div>
  );
}
