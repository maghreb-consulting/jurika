package ma.jurika.ticket.infrastructure.pdf;

import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.EtatDebours;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L5b (E3, RG-DEB-02, RG-FIC-08) : l'etat des debours au format PDF a la charte JURIKA, lu
 * dans le fichier produit (texte extrait par PDFBox ; pages rendues en PNG pour relecture dans
 * target/output/l5b-pdf). Remplace EtatDeboursDocxTest (sortie Word retiree).
 */
class EtatDeboursPdfTest {

    private static final UUID WS = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private final EtatDeboursPdf pdf = new EtatDeboursPdf();

    private static Debours ligne(int n, LocalDate date, DeboursCategorie c, String libelle, String montant,
                                 String piece) {
        return new Debours(UUID.nameUUIDFromBytes(("l" + n).getBytes()), WS, TICKET, libelle, c,
                new BigDecimal(montant), date, null, piece, null, UUID.nameUUIDFromBytes(("u" + n).getBytes()),
                Instant.parse("2026-10-0" + n + "T09:00:00Z"), null,
                piece == null ? null : UUID.nameUUIDFromBytes(("p" + n).getBytes()));
    }

    private static EtatDebours etat(CabinetIdentity cabinet, EntrepriseDossier dossier) {
        Ticket t = new Ticket(TICKET, WS, "T-2026-00012", "Cession de parts sociales", TicketType.MODIFICATION,
                TicketStatut.DEROULEMENT_DEMARCHE, TicketPriorite.NORMALE, DOSSIER, null, null, null, null, null,
                null, null, Instant.parse("2026-10-01T08:00:00Z"));
        List<Debours> lignes = List.of(
                ligne(1, LocalDate.of(2026, 10, 1), DeboursCategorie.FRAIS_GREFFE, "D\u00e9p\u00f4t au greffe",
                        "350.00", "quittance-greffe.pdf"),
                ligne(2, LocalDate.of(2026, 10, 2), DeboursCategorie.ANNONCE_LEGALE, "Annonce au journal",
                        "450.00", "facture-jal.pdf"),
                ligne(3, LocalDate.of(2026, 10, 3), DeboursCategorie.DROITS_ENREGISTREMENT,
                        "Enregistrement de l'acte de cession", "434.50", null));
        return new EtatDebours(cabinet, dossier, "2026-0007 \u2014 PARACOSME", t, LocalDate.of(2026, 10, 12),
                lignes, new BigDecimal("1234.50"));
    }

    private static CabinetIdentity cabinetComplet() {
        return new CabinetIdentity("Cabinet Atlas Conseil", null, null, "12, boulevard Zerktouni, Casablanca",
                "0522 00 00 00", "contact@atlas.ma", null, "001234567000089", "RC 4455", "IF 778899");
    }

    private static EntrepriseDossier dossier(String rc) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new EntrepriseDossier(DOSSIER, WS, "PARACOSME", FormeJuridique.SARL, "002233445000011", rc,
                rc == null ? null : "Tribunal de commerce de Casablanca", null, null, null,
                "45, rue Ibn Batouta", "Casablanca", null, null, DossierStatut.ACTIVE, null, null, now, now);
    }

    static String texte(byte[] bytes) throws IOException {
        try (PDDocument d = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(d);
        }
    }

    @Test
    void contenu_complet_total_en_chiffres_et_en_lettres_et_pieces() throws IOException {
        byte[] bytes = pdf.rendre(etat(cabinetComplet(), dossier("12345")));
        sortie("etat-des-debours", bytes);
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        String t = texte(bytes);

        assertThat(t).contains("Cabinet Atlas Conseil", "12, boulevard Zerktouni, Casablanca",
                "0522 00 00 00", "contact@atlas.ma",
                "ICE : 001234567000089", "RC : RC 4455", "IF : IF 778899");
        assertThat(t).contains("\u00c9tat des d\u00e9bours", "\u00c9tabli le 12 octobre 2026");
        assertThat(t).contains("D\u00c9NOMINATION", "PARACOSME",
                "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e (SARL)",
                "45, rue Ibn Batouta, Casablanca",
                "12345 (Tribunal de commerce de Casablanca)", "002233445000011");
        assertThat(t).contains("2026-0007 \u2014 PARACOSME", "T-2026-00012 \u2014 Modification",
                "Cession de parts sociales");
        assertThat(t).contains("01/10/2026", "Frais de greffe", "D\u00e9p\u00f4t au greffe", "350,00",
                "Droits d'enregistrement", "Enregistrement de l'acte de cession", "434,50");
        assertThat(t).contains("Total des d\u00e9bours", "1 234,50 DH");
        assertThat(t.replace('\n', ' ')).contains("Arr\u00eat\u00e9 le pr\u00e9sent \u00e9tat \u00e0 la somme de "
                + "MILLE DEUX CENT TRENTE-QUATRE DIRHAMS ET CINQUANTE CENTIMES (1 234,50 DH).");
        assertThat(t.replace('\n', ' ')).contains("Pi\u00e8ces justificatives", "quittance-greffe.pdf (Data Room du dossier)",
                "Aucune pi\u00e8ce justificative jointe.");
        assertThat(t).contains("\u00c9dit\u00e9 par JURIKA le 12/10/2026", "Page 1 / 1");
        assertThat(t).doesNotContain("$", "null", "{{", "Word");
    }

    @Test
    void donnee_absente_aucune_ligne_ni_valeur_de_repli() throws IOException {
        CabinetIdentity minimal = new CabinetIdentity("Cabinet Atlas Conseil", null, null, null, null, null, null,
                null, null, null);
        byte[] bytes = pdf.rendre(etat(minimal, dossier(null)));
        sortie("etat-des-debours-donnees-minimales", bytes);
        String t = texte(bytes);
        assertThat(t).doesNotContain("REGISTRE DU COMMERCE", "null", "IF :", "ICE : 0012");
        assertThat(t).contains("PARACOSME");
    }

    @Test
    void deterministe_octet_pour_octet() throws InterruptedException {
        byte[] a = pdf.rendre(etat(cabinetComplet(), dossier("12345")));
        Thread.sleep(1100); // une seconde de plus a l'horloge : aucun effet
        byte[] b = pdf.rendre(etat(cabinetComplet(), dossier("12345")));
        assertThat(b).isEqualTo(a);
    }

    @Test
    void formats() {
        assertThat(EtatDeboursPdf.montant(new BigDecimal("1234.5"))).isEqualTo("1\u00a0234,50");
        assertThat(EtatDeboursPdf.dateLongue(LocalDate.of(2026, 10, 1))).isEqualTo("1er octobre 2026");
    }

    /** Range le PDF et ses pages en PNG (target/output/l5b-pdf) pour relecture. */
    static List<File> sortie(String nom, byte[] bytes) throws IOException {
        File dir = new File("target/output/l5b-pdf");
        dir.mkdirs();
        List<File> out = new ArrayList<>();
        File f = new File(dir, nom + ".pdf");
        Files.write(f.toPath(), bytes);
        out.add(f);
        try (PDDocument d = Loader.loadPDF(bytes)) {
            PDFRenderer r = new PDFRenderer(d);
            for (int p = 0; p < d.getNumberOfPages(); p++) {
                File png = new File(dir, nom + "-p" + (p + 1) + ".png");
                ImageIO.write(r.renderImageWithDPI(p, 110, ImageType.RGB), "png", png);
                out.add(png);
            }
        }
        return out;
    }
}
