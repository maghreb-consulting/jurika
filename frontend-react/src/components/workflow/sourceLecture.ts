/**
 * Lot L6b (E5, backlog L6) : libelle comprehensible de la source d'une lecture automatique.
 *
 * Le serveur renvoie un identifiant technique : "OCR_<moteur>" (ai-service, OcrService et
 * TesseractOcrService : OCR_TESSERACT, OCR_DOCTR...), "OCR_MANUAL_FALLBACK" / "OCR_EMPTY"
 * (aucune lecture), "LLM_<fournisseur>" / "LLM_DEGRADED" (GenericExtractionService) ; pour
 * les pieces d'identite, "kie" (modele d'extraction) ou "merged" (avec la bande MRZ).
 * Jamais affiche tel quel : le nom du moteur ne dit rien a l'utilisateur du cabinet.
 * Source absente ou inconnue : null (rien n'est affiche plutot qu'un "inconnu").
 */
export function libelleSourceLecture(source?: string | null): string | null {
  if (!source) return null;
  const s = source.trim().toUpperCase();
  if (s === 'OCR_MANUAL_FALLBACK' || s === 'OCR_EMPTY') return 'aucune lecture automatique';
  if (s === 'LLM_DEGRADED') return 'lecture automatique simplifiée';
  if (s.startsWith('LLM_')) return 'lecture assistée par l’IA';
  if (s.startsWith('OCR_')) return 'lecture automatique du document';
  if (s === 'MERGED') return 'lecture du document et de sa bande de lecture automatique (MRZ)';
  if (s === 'KIE') return 'lecture automatique du document';
  return null;
}
