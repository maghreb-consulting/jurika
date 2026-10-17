import { Check, FileText, Sparkles, X } from 'lucide-react';
import { Button } from '../ui/Button';
import { libelleSourceLecture } from './sourceLecture';

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
      return 'PDF numérique (couche texte)';
    case 'PDFBOX_RENDER_TESSERACT':
      return 'PDF scanné (lecture automatique)';
    case 'TESSERACT_IMAGE':
      return 'Image (lecture automatique)';
    case 'FALLBACK':
      return 'Lecture automatique indisponible (saisie manuelle)';
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
      <div role="status" className="rounded-xl border border-info/30 bg-info-soft p-4">
        <div className="flex items-center gap-3">
          <Sparkles className="h-5 w-5 text-info motion-safe:animate-pulse" aria-hidden="true" />
          <div className="flex-1">
            <p className="text-sm font-semibold text-fg">Lecture automatique en cours…</p>
            <p className="text-xs text-fg-muted">
              {filename ?? 'document'} — vous pouvez continuer la saisie manuelle pendant l’analyse.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!suggestions || suggestions.length === 0) {
    if (manualFallback) {
      return (
        <div className="rounded-xl border border-warning/40 bg-warning-soft p-4">
          <div className="flex items-start gap-3">
            <FileText className="mt-0.5 h-5 w-5 text-warning" aria-hidden="true" />
            <div className="flex-1">
              <p className="text-sm font-semibold text-fg">Aucun champ extrait automatiquement</p>
              <p className="mt-1 text-xs text-fg-muted">
                Mode : {formatMode(extractionMode)}.{' '}
                {warnings && warnings.length > 0 ? warnings[0] : 'Continuez en saisie manuelle.'}
              </p>
              {onDismiss && (
                <Button type="button" variant="secondary" size="sm" className="mt-2" onClick={onDismiss}>
                  Masquer
                </Button>
              )}
            </div>
          </div>
        </div>
      );
    }
    return null;
  }

  const confidencePercent = confidence != null ? Math.round(confidence * 100) : null;
  // Lot L6b (E5, backlog L6) : la source se dit en francais, jamais par le nom du moteur.
  const sourceLabel = libelleSourceLecture(source);

  return (
    <section aria-label="Suggestions de la lecture automatique" className="rounded-xl border border-info/30 bg-info-soft p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Sparkles className="mt-0.5 h-5 w-5 text-info" aria-hidden="true" />
          <div>
            <p className="text-sm font-semibold text-fg">
              Suggestions extraites — {suggestions.length} champ
              {suggestions.length > 1 ? 's' : ''}
            </p>
            <p className="text-xs text-fg-muted">
              {filename ? `${filename} · ` : ''}
              {formatMode(extractionMode)}
              {confidencePercent != null && ` · confiance ${confidencePercent}%`}
              {sourceLabel && ` · source : ${sourceLabel}`}
            </p>
          </div>
        </div>
        {onDismiss && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            iconOnly
            icon={<X />}
            onClick={onDismiss}
            aria-label="Fermer les suggestions"
            title="Fermer (les valeurs déjà appliquées restent)"
          />
        )}
      </div>

      {warnings && warnings.length > 0 && (
        <ul className="mb-3 ml-5 list-disc text-xs text-fg">
          {warnings.map((w, i) => (
            <li key={i}>{w}</li>
          ))}
        </ul>
      )}

      <ul className="space-y-2">
        {suggestions.map((s) => (
          <li key={s.field} className="flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-fg-muted">{s.label}</p>
              <p className="truncate text-sm text-fg" title={s.value}>
                {s.value}
              </p>
            </div>
            <Button type="button" variant="secondary" size="sm" icon={<Check />} onClick={() => onApplyOne(s.field, s.value)}>
              Appliquer
            </Button>
          </li>
        ))}
      </ul>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-info/30 pt-3">
        <p className="text-xs text-fg-muted">
          La lecture automatique ne remplit jamais un champ déjà saisi. Choisissez champ par champ.
        </p>
        <Button type="button" variant="secondary" size="sm" icon={<Check />} onClick={onApplyAllEmpty}>
          Appliquer aux champs vides uniquement
        </Button>
      </div>
    </section>
  );
}
