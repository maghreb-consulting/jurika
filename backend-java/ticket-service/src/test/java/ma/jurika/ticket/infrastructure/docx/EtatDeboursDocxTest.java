package ma.jurika.ticket.infrastructure.docx;

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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L5 (E6, RG-DEB-02) : l'etat des debours au format Word, lu dans le fichier produit.
 * Remplace DeboursPdfGeneratorTest (sortie PDF retiree, P5), qui avalait ses erreurs d'ecriture.
 */
class EtatDeboursDocxTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final String NBSP = "\u00a0";

    private final EtatDeboursDocx docx = new EtatDeboursDocx();

    private static Debours ligne(int n, LocalDate date, DeboursCategorie c, String libelle, String montant,
                                 String piece) {
        return new Debours(UUID.nameUUIDFromBytes(("l" + n).getBytes()), WS, TICKET, libelle, c,
                new BigDecimal(montant), date, null, piece, null, UUID.randomUUID(),
                Instant.parse("2026-10-0" + n + "T09:00:00Z"), null, piece == null ? null : UUID.randomUUID());
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
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> l = new ArrayList<>();
            for (var e : d.getBodyElements()) {
                if (e instanceof XWPFParagraph p) l.add(p.getText());
                if (e instanceof XWPFTable t) {
                    for (XWPFTableRow r : t.getRows()) {
                        l.add(r.getTableCells().stream().map(c -> c.getText()).collect(Collectors.joining(" | ")));
                    }
                }
            }
            return String.join("\n", l);
        }
    }

    @Test
    void contenu_complet_total_en_chiffres_et_en_lettres() throws IOException {
        byte[] bytes = docx.rendre(etat(cabinetComplet(), dossier("12345")));
        Path sortie = Path.of("target", "etat-debours-test.docx");
        Files.createDirectories(sortie.getParent());
        Files.write(sortie, bytes);
        String t = texte(bytes);

        assertThat(t).contains("Cabinet Atlas Conseil", "12, boulevard Zerktouni, Casablanca",
                "T\u00e9l. : 0522 00 00 00 \u2014 Courriel : contact@atlas.ma",
                "ICE : 001234567000089 \u2014 RC : RC 4455 \u2014 IF : IF 778899");
        assertThat(t).contains("\u00c9TAT DES D\u00c9BOURS", "\u00c9tabli le 12 octobre 2026");
        assertThat(t).contains("D\u00e9nomination : PARACOSME",
                "Forme juridique : Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e (SARL)",
                "Si\u00e8ge social : 45, rue Ibn Batouta, Casablanca",
                "Registre du commerce : 12345 (Tribunal de commerce de Casablanca)", "ICE : 002233445000011");
        assertThat(t).contains("Dossier : 2026-0007 \u2014 PARACOSME",
                "Ticket : T-2026-00012 \u2014 Modification", "Objet : Cession de parts sociales");
        assertThat(t).contains("1 | 01/10/2026 | Frais de greffe | D\u00e9p\u00f4t au greffe | 350,00",
                "3 | 03/10/2026 | Droits d'enregistrement | Enregistrement de l'acte de cession | 434,50");
        assertThat(t).contains("Total des d\u00e9bours : 1" + NBSP + "234,50 DH");
        assertThat(t).contains("Arr\u00eat\u00e9 le pr\u00e9sent \u00e9tat \u00e0 la somme de "
                + "MILLE DEUX CENT TRENTE-QUATRE DIRHAMS ET CINQUANTE CENTIMES (1" + NBSP + "234,50 DH).");
        assertThat(t).contains(
                "Ligne n\u00b0 1 \u2014 Frais de greffe \u2014 D\u00e9p\u00f4t au greffe : quittance-greffe.pdf (Data Room du dossier).",
                "Ligne n\u00b0 3 \u2014 Droits d'enregistrement \u2014 Enregistrement de l'acte de cession : "
                        + "aucune pi\u00e8ce justificative jointe.");
        assertThat(t).doesNotContain("$", "null", "{{", "PDF");
    }

    @Test
    void donnee_absente_aucune_ligne_ni_valeur_de_repli() throws IOException {
        CabinetIdentity minimal = new CabinetIdentity("Cabinet Atlas Conseil", null, null, null, null, null, null,
                null, null, null);
        String t = texte(docx.rendre(etat(minimal, dossier(null))));
        assertThat(t).doesNotContain("T\u00e9l.", "Courriel", "Registre du commerce", "null", "IF :");
        assertThat(t).contains("D\u00e9nomination : PARACOSME");
        for (String ligne : t.split("\n")) {
            assertThat(ligne).as("ligne sans valeur").doesNotEndWith(": ");
        }
    }

    @Test
    void deterministe_octet_pour_octet() throws InterruptedException {
        byte[] a = docx.rendre(etat(cabinetComplet(), dossier("12345")));
        Thread.sleep(2100); // franchit la resolution de 2 s de l'horodatage ZIP
        byte[] b = docx.rendre(etat(cabinetComplet(), dossier("12345")));
        assertThat(b).isEqualTo(a);
    }

    @Test
    void formats() {
        assertThat(EtatDeboursDocx.montant(new BigDecimal("1234.5"))).isEqualTo("1" + NBSP + "234,50");
        assertThat(EtatDeboursDocx.dateLongue(LocalDate.of(2026, 10, 1))).isEqualTo("1er octobre 2026");
    }
}
