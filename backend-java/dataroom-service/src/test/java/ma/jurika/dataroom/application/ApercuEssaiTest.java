package ma.jurika.dataroom.application;

import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.trial.TrialAccessChecker;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lot L7 (E4) : apercu pendant l'essai (RG-ESS-05, RG-ESS-06). */
class ApercuEssaiTest {

    private static final UUID WS = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private static ApercuEssai apercu(String statut) {
        TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws, statut, 2, Instant.now(), null));
        ObjectProvider<TrialAccessChecker> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(checker);
        return new ApercuEssai(p);
    }

    private static byte[] pdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static OfficePreviewSupport.Rendered rendu(byte[] octets, String type) {
        return new OfficePreviewSupport.Rendered(new ByteArrayInputStream(octets), octets.length, type, "acte");
    }

    @Test
    void pdf_affiche_pendant_l_essai_porte_le_filigrane() throws Exception {
        OfficePreviewSupport.Rendered r = apercu("TRIAL_ACTIVE").adapter(rendu(pdf(), "application/pdf"), WS);
        byte[] octets = r.stream().readAllBytes();
        assertThat(r.size()).isEqualTo(octets.length);
        try (PDDocument doc = Loader.loadPDF(octets)) {
            assertThat(new PDFTextStripper().getText(doc).replaceAll("\\s+", "")).contains("SANSVALEURJURIDIQUE");
        }
    }

    @Test
    void original_word_non_converti_refuse_pendant_l_essai() {
        byte[] docx = "PK docx".getBytes();
        assertThatThrownBy(() -> apercu("TRIAL_EXPIRED").adapter(rendu(docx,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), WS))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("Aper\u00e7u indisponible");
    }

    @Test
    void image_inchangee_et_cabinet_abonne_inchange() throws Exception {
        byte[] png = {1, 2, 3};
        OfficePreviewSupport.Rendered img = rendu(png, "image/png");
        assertThat(apercu("TRIAL_ACTIVE").adapter(img, WS)).isSameAs(img);
        OfficePreviewSupport.Rendered p = rendu(pdf(), "application/pdf");
        assertThat(apercu("CONVERTED").adapter(p, WS)).isSameAs(p);
    }
}
