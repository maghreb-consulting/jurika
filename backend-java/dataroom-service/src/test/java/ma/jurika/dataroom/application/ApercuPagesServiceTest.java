package ma.jurika.dataroom.application;

import ma.jurika.common.trial.ApercuPages;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.trial.EtatEssaiIndisponibleException;
import ma.jurika.common.trial.RenduApercu;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.dataroom.domain.port.AiApercuClient;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision du 2026-10-14 (remplace la decision 23 du lot L7 ; RG-ESS-05, RG-ESS-06) : l'apercu
 * d'un document de la Data Room est un affichage en pages PNG produites par le serveur ; la
 * mention de l'essai y est incrustee pendant l'essai ; un format sans apercu est refuse, jamais
 * servi tel quel ; hors essai, aucune mention.
 */
class ApercuPagesServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AiApercuClient ai = mock(AiApercuClient.class);
    private RuntimeException panne;

    @SuppressWarnings("unchecked")
    private ApercuPagesService service(String statut) {
        TrialAccessChecker c = ws -> {
            if (panne != null) throw panne;
            return Optional.of(new TrialAccessChecker.TrialState(ws, statut, 3, Instant.now(), null));
        };
        ObjectProvider<TrialAccessChecker> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(c);
        return new ApercuPagesService(storage, ai, p);
    }

    private void stocke(byte[] octets) {
        when(storage.download(anyString())).thenAnswer(inv ->
                new ObjectStorage.DownloadResult(new ByteArrayInputStream(octets), octets.length, "x"));
    }

    private static byte[] pdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static long rouges(ApercuPages.Page p) throws Exception {
        byte[] png = Base64.getDecoder().decode(p.image().substring("data:image/png;base64,".length()));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() - c.getGreen() > 25 && c.getRed() - c.getBlue() > 25) n++;
            }
        }
        return n;
    }

    @Test
    void essai_pdf_rendu_en_pages_avec_la_mention_sur_chaque_page() throws Exception {
        byte[] source = pdf();
        stocke(source);
        ApercuPages a = service("TRIAL_ACTIVE").rendre("ws/a.pdf", "acte.pdf", "application/pdf", WS);
        assertThat(a.filigrane()).isTrue();
        assertThat(a.mention()).isEqualTo(EssaiRegles.MENTION_FILIGRANE);
        assertThat(a.pages()).hasSize(2);
        for (ApercuPages.Page p : a.pages()) assertThat(rouges(p)).isGreaterThan(5_000);
        // Aucun octet du PDF d'origine dans l'apercu.
        assertThat(a.pages().get(0).image()).doesNotContain(Base64.getEncoder().encodeToString(source));
        Path sortie = Path.of("target", "apercu-essai");
        Files.createDirectories(sortie);
        byte[] png = Base64.getDecoder().decode(a.pages().get(0).image().substring(22));
        Files.write(sortie.resolve("dataroom-pdf-page1-essai.png"), png);
    }

    @Test
    void lecture_seule_aussi_filigranee() throws Exception {
        stocke(pdf());
        assertThat(service("TRIAL_EXPIRED").rendre("k", "acte.pdf", "application/pdf", WS).filigrane()).isTrue();
    }

    @Test
    void etat_illisible_traite_comme_un_essai() throws Exception {
        stocke(pdf());
        panne = new EtatEssaiIndisponibleException("auth injoignable", null);
        assertThat(service("CONVERTED").rendre("k", "acte.pdf", "application/pdf", WS).filigrane()).isTrue();
    }

    @Test
    void cabinet_abonne_pages_sans_mention() throws Exception {
        stocke(pdf());
        ApercuPages a = service("CONVERTED").rendre("k", "acte.pdf", "application/pdf", WS);
        assertThat(a.filigrane()).isFalse();
        assertThat(rouges(a.pages().get(0))).isZero();
    }

    @Test
    void docx_mis_en_page_par_ai_service_avec_le_drapeau_de_l_essai() {
        stocke("PK\u0003\u0004docx".getBytes());
        ApercuPages attendu = new ApercuPages(true, EssaiRegles.MENTION_FILIGRANE, 1, false,
                List.of(new ApercuPages.Page(1, 10, 10, "data:image/png;base64,AA==")));
        when(ai.apercu(any(), eq("Statuts.docx"), anyString(), eq(true))).thenReturn(attendu);
        assertThat(service("TRIAL_ACTIVE").rendre("k", "Statuts.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", WS)).isEqualTo(attendu);
    }

    @Test
    void format_sans_apercu_refuse_et_jamais_rendu_tel_quel() {
        stocke("PK\u0003\u0004xlsx".getBytes());
        assertThatThrownBy(() -> service("TRIAL_ACTIVE").rendre("k", "budget.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", WS))
                .isInstanceOf(RenduApercu.ApercuImpossibleException.class)
                .hasMessageContaining("Aper\u00e7u indisponible pour ce format");
        verify(ai, never()).apercu(any(), any(), any(), anyBoolean());
    }
}
