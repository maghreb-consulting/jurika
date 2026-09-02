package ma.jurika.dataroom.domain.port;

/**
 * Lot AB (2026-07-05) — Port de conversion bureautique -> PDF, delegue a
 * ai-service (LibreOffice headless) via un endpoint interne. Utilise UNIQUEMENT
 * pour l'apercu inline des Word/Excel ; le download reste inchange.
 */
public interface AiPdfConversionClient {

    /**
     * Convertit un document .docx/.xlsx en PDF.
     *
     * @param source   octets du document source (non vide)
     * @param filename nom d'origine (sert a deriver l'extension source)
     * @return octets du PDF
     * @throws AiPdfConversionUnavailableException si ai-service est injoignable ou
     *         LibreOffice indisponible (le caller doit alors retomber sur l'original)
     */
    byte[] convertToPdf(byte[] source, String filename);

    /** Levee quand la conversion ne peut pas aboutir (503, injoignable, PDF vide...). */
    class AiPdfConversionUnavailableException extends RuntimeException {
        public AiPdfConversionUnavailableException(String message) { super(message); }
        public AiPdfConversionUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
