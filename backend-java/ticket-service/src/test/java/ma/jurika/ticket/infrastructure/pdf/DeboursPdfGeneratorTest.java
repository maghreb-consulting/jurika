package ma.jurika.ticket.infrastructure.pdf;

import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rendu de l'État des débours (PDFBox, charte JURIKA) : magic bytes, total,
 * cas 0/1/N lignes, pagination + répétition de l'en-tête sur un tableau long.
 */
class DeboursPdfGeneratorTest {

    private final DeboursPdfGenerator gen = new DeboursPdfGenerator();

    private Ticket ticket() {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), "TCK-2026-014",
                "Constitution SARL ACME", TicketType.CREATION, TicketStatut.CLOTURE,
                null, null, null, null, null, null, null, null, null, Instant.now());
    }

    private Debours debours(String libelle, DeboursCategorie cat, String montant, boolean pj) {
        return new Debours(UUID.randomUUID(), null, null, libelle, cat, new BigDecimal(montant),
                LocalDate.of(2026, 3, 10), null, pj ? "scan.pdf" : null, null, null, Instant.now());
    }

    private String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private int pages(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { n++; i += needle.length(); }
        return n;
    }

    @Test
    void zero_debours_rend_un_pdf_avec_total_zero() throws Exception {
        byte[] pdf = gen.generate(ticket(), List.of(), "ACME SARL");
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");
        String t = text(pdf);
        assertThat(t).contains("État des débours");
        assertThat(t).contains("Aucun débours");
        assertThat(t).contains("0,00 MAD");
        assertThat(t).contains("Page 1 /");
    }

    @Test
    void une_ligne_rend_le_detail_et_le_total() throws Exception {
        byte[] pdf = gen.generate(ticket(),
                List.of(debours("Frais de greffe", DeboursCategorie.FRAIS_TRIBUNAL, "1234.50", true)),
                "ACME SARL");
        String t = text(pdf);
        assertThat(t).contains("Frais de greffe");
        assertThat(t).contains("FRAIS_TRIBUNAL");
        assertThat(t).contains("1234,50");
        assertThat(t).contains("Oui");
        assertThat(t).contains("1234,50 MAD"); // total
    }

    @Test
    void le_total_est_la_somme_des_montants() throws Exception {
        List<Debours> items = List.of(
                debours("A", DeboursCategorie.HONORAIRES, "100.00", false),
                debours("B", DeboursCategorie.TRANSPORT, "200.00", false),
                debours("C", DeboursCategorie.AUTRE, "300.50", true));
        byte[] pdf = gen.generate(ticket(), items, null);
        assertThat(text(pdf)).contains("600,50 MAD");
    }

    @Test
    void tableau_long_pagine_et_repete_l_entete() throws Exception {
        List<Debours> items = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            items.add(debours("Débours numéro " + i + " avec une description assez longue "
                    + "pour occuper la cellule", DeboursCategorie.HONORAIRES, "150.00", i % 2 == 0));
        }
        byte[] pdf = gen.generate(ticket(), items, "ACME SARL");
        assertThat(pages(pdf)).isGreaterThanOrEqualTo(2);
        String t = text(pdf);
        // En-tete du tableau repete sur chaque page.
        assertThat(count(t, "MONTANT (MAD)")).isGreaterThanOrEqualTo(2);
        assertThat(t).contains("Page 2 /");
        // Total = 45 * 150 = 6750,00.
        assertThat(t).contains("6750,00 MAD");
    }

    @Test
    void entete_affiche_le_nom_du_cabinet_transmis() throws Exception {
        byte[] pdf = gen.generate(ticket(),
                List.of(debours("Frais", DeboursCategorie.HONORAIRES, "100.00", false)),
                "ACME SARL", CabinetIdentity.ofName(null, "Cabinet Alaoui & Associés"));
        assertThat(text(pdf)).contains("Cabinet Alaoui");
    }

    @Test
    void entete_affiche_coordonnees_et_mentions_legales() throws Exception {
        CabinetIdentity id = CabinetIdentity.resolve(null, "Cabinet Alaoui & Associés",
                null, null, "12 rue des Consultants, Casablanca", "+212 5 22 00 00 00",
                "contact@cabinet.ma", "www.cabinet.ma", "002345678000089", "RC-45219", "12345678");
        byte[] pdf = gen.generate(ticket(),
                List.of(debours("Frais", DeboursCategorie.HONORAIRES, "100.00", false)),
                "ACME SARL", id);
        String t = text(pdf);
        assertThat(t).contains("12 rue des Consultants");     // adresse (en-tete)
        assertThat(t).contains("contact@cabinet.ma");         // coordonnees (en-tete)
        assertThat(t).contains("ICE : 002345678000089");      // mentions legales (pied)
        assertThat(t).contains("RC : RC-45219");
    }

    @Test
    void entete_repli_par_defaut_si_cabinet_absent() throws Exception {
        // Surcharge 3-args (cabinet absent) -> repli « Cabinet », mise en page intacte.
        byte[] pdf = gen.generate(ticket(), List.of(), "ACME SARL");
        assertThat(text(pdf)).contains("Cabinet");
        assertThat(text(pdf)).contains("État des débours");
    }

    @Test
    void ecrit_deux_exemplaires_pour_validation_visuelle() throws Exception {
        List<Debours> items = List.of(
                debours("Frais de greffe (dépôt statuts)", DeboursCategorie.FRAIS_TRIBUNAL, "300.00", true),
                debours("Publication annonce légale", DeboursCategorie.PUBLICATION_JAL, "450.00", true),
                debours("Enregistrement des actes", DeboursCategorie.FRAIS_ENREGISTREMENT, "200.00", false),
                debours("Honoraires de constitution", DeboursCategorie.HONORAIRES, "2500.00", false));

        // 1) Papier à en-tête complet : logo + coordonnées + mentions légales.
        CabinetIdentity full = CabinetIdentity.resolve(null, "Cabinet Alaoui & Associés",
                samplePng(), "image/png",
                "12, rue des Consultants, Quartier Maârif, Casablanca", "+212 5 22 00 11 22",
                "contact@cabinet-alaoui.ma", "www.cabinet-alaoui.ma",
                "002345678000089", "RC 45219 (Casablanca)", "12345678");
        writeSample("Etat_Debours_SAMPLE.pdf", gen.generate(ticket(), items, "ACME CONSEIL SARL", full));

        // 2) Aucun champ renseigné : seul le nom reste, mise en page intacte.
        CabinetIdentity empty = CabinetIdentity.ofName(null, "Cabinet Test");
        writeSample("Etat_Debours_SAMPLE_vide.pdf", gen.generate(ticket(), items, null, empty));
    }

    private static void writeSample(String filename, byte[] pdf) {
        assertThat(pdf).isNotEmpty();
        try {
            Path out = Path.of("C:", "dev", "JURIKA", "output", filename);
            Files.createDirectories(out.getParent());
            Files.write(out, pdf);
        } catch (Exception ignore) {
            // best-effort : la génération d'un exemplaire ne doit pas faire échouer le test.
        }
    }

    /** Petit logo PNG généré en mémoire pour l'exemplaire visuel. */
    private static byte[] samplePng() throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(170, 60, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x1c, 0x34, 0x61));
        g.fillRoundRect(0, 0, 170, 60, 12, 12);
        g.setColor(new java.awt.Color(0xa8, 0x85, 0x3d));
        g.fillOval(10, 14, 32, 32);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 17));
        g.drawString("CABINET", 52, 30);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        g.drawString("Alaoui & Associés", 52, 46);
        g.dispose();
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", b);
        return b.toByteArray();
    }
}
