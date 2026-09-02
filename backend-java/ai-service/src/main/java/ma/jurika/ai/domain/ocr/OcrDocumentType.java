package ma.jurika.ai.domain.ocr;

/**
 * Type de document soumis à l'OCR.
 * <p>
 * Permet aux implémentations OCR d'adapter le parsing (regex / heuristiques)
 * en fonction du type attendu.
 */
public enum OcrDocumentType {
    /** Carte d'Identité Nationale Marocaine — face recto (nom, prénom, CIN, date de naissance). */
    CIN_RECTO,
    /** Carte d'Identité Nationale Marocaine — face verso (adresse, date de validité). */
    CIN_VERSO,
    /** Passeport (national ou étranger). */
    PASSPORT,
    /**
     * Certificat Négatif délivré par l'OMPIC (PDF numérique ou scanné).
     * Parsing dédié : ICE 15 chiffres, dénomination / raison sociale, N° CN,
     * date de délivrance, activité, bénéficiaire. Clés en lowercase pour
     * alignement avec le contrat frontend (Step1Denomination).
     */
    CERTIFICAT_NEGATIF,
    /** Autre document — heuristique générique sans extraction structurée. */
    OTHER
}
