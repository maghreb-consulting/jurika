package ma.jurika.dataroom.infrastructure.pdf;

import ma.jurika.dataroom.domain.port.PdfWatermarkService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Sprint 7 / TASK 3.2 -- Implementation par defaut "no-op" du watermark PDF.
 *
 * Active quand :
 *   - jurika.dataroom.watermark.enabled=false (defaut, voir application.yml)
 *   - OU aucune autre impl PdfWatermarkService n'est dans le contexte
 *
 * Reecrit zero octet : retourne le flux source tel quel. Cout : pratiquement
 * nul (aucun parsing PDF). isEnabled() retourne false pour permettre aux
 * appelants de skipper proprement la lecture du PDF.
 */
@Service
@ConditionalOnMissingBean(name = "pdfBoxPdfWatermarkService")
public class NoopPdfWatermarkService implements PdfWatermarkService {

    @Override
    public InputStream watermark(InputStream source, String watermarkText, UUID documentId) {
        return source;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}

/**
 * Sprint 7 / TASK 3.2 -- Stub config pour le futur PdfBoxPdfWatermarkService.
 *
 * Aujourd'hui : aucun bean cree. Demain (dep PDFBox installee + flag true) :
 *   @Bean
 *   @ConditionalOnProperty(prefix = "jurika.dataroom.watermark", name = "enabled",
 *                          havingValue = "true")
 *   public PdfWatermarkService pdfBoxPdfWatermarkService(...) {
 *       return new PdfBoxPdfWatermarkService(...);
 *   }
 *
 * Le ConditionalOnMissingBean ci-dessus garantira alors la priorite a PdfBox.
 */
@Configuration
class PdfWatermarkConfig {
    // Placeholder volontaire -- voir javadoc.
}
