package ma.jurika.dataroom.application;

import ma.jurika.dataroom.domain.port.AiPdfConversionClient;
import ma.jurika.dataroom.domain.port.AiPdfConversionClient.AiPdfConversionUnavailableException;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.domain.port.ObjectStorage.DownloadResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot AB — Tests unitaires de la conversion d'apercu Word/Excel -> PDF.
 * Couvre : detection Office, conversion + cache MinIO, fallback quand
 * LibreOffice/ai-service est indisponible, passthrough des PDF/images.
 */
class OfficePreviewSupportTest {

    private static final String DOCX_CT =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AiPdfConversionClient ai = mock(AiPdfConversionClient.class);
    private final OfficePreviewSupport support = new OfficePreviewSupport(storage, ai);

    private static byte[] read(InputStream in) throws IOException {
        try (in) { return in.readAllBytes(); }
    }

    // ---- detection ----

    @Test
    void isOffice_detecte_docx_et_xlsx_par_extension() {
        assertThat(support.isOffice(null, "statuts.docx")).isTrue();
        assertThat(support.isOffice(null, "bilan.XLSX")).isTrue();
        assertThat(support.isOffice(DOCX_CT, "sans-extension")).isTrue();
        assertThat(support.isOffice("application/pdf", "acte.pdf")).isFalse();
        assertThat(support.isOffice("image/png", "scan.png")).isFalse();
    }

    // ---- conversion OK + cache ----

    @Test
    void docx_est_converti_en_pdf_et_mis_en_cache() throws IOException {
        byte[] src = "DOCX-BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);

        // Cache absent -> download(cacheKey) echoue ; original present ; conversion OK.
        when(storage.download("k1.preview.pdf")).thenThrow(new RuntimeException("no such key"));
        when(storage.download("k1")).thenReturn(new DownloadResult(new ByteArrayInputStream(src), src.length, DOCX_CT));
        when(ai.convertToPdf(any(), eq("statuts.docx"))).thenReturn(pdf);

        OfficePreviewSupport.Rendered rd = support.render("k1", "statuts.docx", DOCX_CT);

        assertThat(rd.contentType()).isEqualTo("application/pdf");
        assertThat(rd.filename()).isEqualTo("statuts.pdf");
        assertThat(read(rd.stream())).isEqualTo(pdf);
        // PDF mis en cache sous la cle derivee.
        verify(storage).upload(eq("k1.preview.pdf"), any(InputStream.class), anyLong(), eq("application/pdf"));
    }

    @Test
    void docx_deja_en_cache_ne_reconvertit_pas() throws IOException {
        byte[] pdf = "%PDF cached".getBytes(StandardCharsets.UTF_8);
        when(storage.download("k2.preview.pdf"))
                .thenReturn(new DownloadResult(new ByteArrayInputStream(pdf), pdf.length, "application/pdf"));

        OfficePreviewSupport.Rendered rd = support.render("k2", "pv.docx", DOCX_CT);

        assertThat(rd.contentType()).isEqualTo("application/pdf");
        assertThat(read(rd.stream())).isEqualTo(pdf);
        verify(ai, never()).convertToPdf(any(), any());
        verify(storage, never()).download("k2"); // l'original n'est pas relu
    }

    // ---- fallback LibreOffice/ai indisponible ----

    @Test
    void docx_fallback_original_quand_conversion_indisponible() throws IOException {
        byte[] src = "DOCX-ORIGINAL".getBytes(StandardCharsets.UTF_8);
        when(storage.download("k3.preview.pdf")).thenThrow(new RuntimeException("no cache"));
        when(storage.download("k3")).thenReturn(new DownloadResult(new ByteArrayInputStream(src), src.length, DOCX_CT));
        when(ai.convertToPdf(any(), any()))
                .thenThrow(new AiPdfConversionUnavailableException("LibreOffice absent (503)"));

        OfficePreviewSupport.Rendered rd = support.render("k3", "statuts.docx", DOCX_CT);

        // Fallback : on renvoie l'ORIGINAL tel quel (le front proposera le telechargement).
        assertThat(rd.contentType()).isEqualTo(DOCX_CT);
        assertThat(read(rd.stream())).isEqualTo(src);
        assertThat(rd.filename()).isEqualTo("statuts.docx");
        verify(storage, never()).upload(any(), any(), anyLong(), any()); // rien mis en cache
    }

    // ---- passthrough PDF / image ----

    @Test
    void pdf_passe_inchange_sans_conversion() throws IOException {
        byte[] pdf = "%PDF native".getBytes(StandardCharsets.UTF_8);
        when(storage.download("k4")).thenReturn(new DownloadResult(new ByteArrayInputStream(pdf), pdf.length, "application/pdf"));

        OfficePreviewSupport.Rendered rd = support.render("k4", "acte.pdf", "application/pdf");

        assertThat(rd.contentType()).isEqualTo("application/pdf");
        assertThat(rd.filename()).isEqualTo("acte.pdf");
        assertThat(read(rd.stream())).isEqualTo(pdf);
        verify(ai, never()).convertToPdf(any(), any());
        verify(storage, times(1)).download("k4");
    }
}
