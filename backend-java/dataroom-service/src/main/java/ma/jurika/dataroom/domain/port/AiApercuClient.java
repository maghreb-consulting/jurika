package ma.jurika.dataroom.domain.port;

import ma.jurika.common.trial.ApercuPages;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : mise en page d'un document Word (.docx) en pages
 * PNG d'apercu par ai-service ({@code POST /internal/documents/apercu}), sans LibreOffice.
 */
public interface AiApercuClient {

    /**
     * @throws ma.jurika.common.trial.RenduApercu.ApercuImpossibleException format sans apercu ou
     *         document illisible (refus nomme par ai-service)
     * @throws ma.jurika.common.trial.RenduApercu.ApercuIndisponibleException ai-service injoignable
     */
    ApercuPages apercu(byte[] octets, String filename, String contentType, boolean filigrane);
}
