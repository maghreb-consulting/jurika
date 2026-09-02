package ma.jurika.dataroom.domain.port;

import java.io.InputStream;
import java.util.UUID;

/**
 * Sprint 7 / TASK 3.2 -- Port domain pour le watermark PDF.
 *
 * Decorator pattern (cf. CLAUDE.md "8 Design Patterns") sur ObjectStorage.download :
 * la classe consommatrice (DataroomJuridiqueService.loadForPreview) demande au
 * service de potentiellement reecrire le flux PDF avec un watermark, puis le
 * stream resultant est envoye au navigateur en application/pdf inline.
 *
 * Implementations :
 *   - NoopPdfWatermarkService : passe le flux tel quel (defaut quand
 *     jurika.dataroom.watermark.enabled=false ou role != CLIENT)
 *   - PdfBoxPdfWatermarkService : a brancher avec Apache PDFBox (TASK 3.2
 *     follow-up, dep non installee pour cette PR pour eviter scope creep
 *     watermark sur tous les flux de download/preview)
 *
 * Le contexte de watermark est volontairement minimal (text libre, rendu
 * uniforme). L'impl peut composer le texte a partir du dossier/raison sociale
 * du context applicatif, mais l'interface reste agnostique pour permettre
 * d'autres impl (HTML, image embed, audit trail PDF tag, ...).
 */
public interface PdfWatermarkService {

    /**
     * @param source flux PDF brut depuis le storage
     * @param watermarkText texte libre (ex: "JURIKA -- SARL TEST -- 2026-05-22")
     * @param documentId id du document (utilise pour audit/log)
     * @return flux PDF potentiellement modifie (le caller doit fermer)
     */
    InputStream watermark(InputStream source, String watermarkText, UUID documentId);

    /**
     * Permet a l'application d'esquiver totalement le pipeline watermark
     * quand le feature flag est OFF, sans avoir a connaitre la config.
     */
    boolean isEnabled();
}
