package ma.jurika.ticket.infrastructure.pdf;

import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.pdf.JurikaPdfDocument;
import ma.jurika.common.pdf.JurikaPdfTheme;
import ma.jurika.common.pdf.PdfTable;
import ma.jurika.common.pdf.PdfTable.Align;
import ma.jurika.common.pdf.PdfTable.Cell;
import ma.jurika.common.pdf.PdfTable.Style;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.Ticket;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genere un « Etat des débours » PDF pour un ticket donne (RG-T14).
 *
 * <p>Migration 2026-07-14 : rendu porté sur Apache PDFBox via le thème partagé
 * {@link JurikaPdfTheme} (remplace iText/AGPL) + mise à la charte JURIKA. Seul le
 * <b>rendu</b> change : la logique et les données (ticket, débours, total) sont
 * identiques. Document présentable au client ou joint à une facture.
 */
@Component
public class DeboursPdfGenerator {

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Surcharge de compat (ex. tests) — papier a en-tete minimal (nom « Cabinet »).
     * Les appelants reels passent l'identite du cabinet (cf. {@code DeboursController}).
     */
    public byte[] generate(Ticket ticket, List<Debours> items, String dossierLibelle) {
        return generate(ticket, items, dossierLibelle, CabinetIdentity.ofName(null, null));
    }

    public byte[] generate(Ticket ticket, List<Debours> items, String dossierLibelle,
                           CabinetIdentity cabinet) {
        CabinetIdentity id = cabinet != null ? cabinet : CabinetIdentity.ofName(null, null);
        try (JurikaPdfDocument pdf = JurikaPdfTheme.newDocument()) {
            String subject = "Ticket " + safe(ticket.reference());
            if (dossierLibelle != null && !dossierLibelle.isBlank()) {
                subject += "  —  " + dossierLibelle;
            }
            pdf.header(id, "État des débours", subject,
                    "Type : " + ticket.type() + "   ·   Statut : " + ticket.statut());

            // Contexte du ticket.
            pdf.sectionTitle("1", "Contexte");
            List<String[]> ctx = new ArrayList<>();
            ctx.add(new String[]{"Référence", safe(ticket.reference())});
            ctx.add(new String[]{"Titre", safe(ticket.titre())});
            ctx.add(new String[]{"Type d'opération", String.valueOf(ticket.type())});
            ctx.add(new String[]{"Statut", String.valueOf(ticket.statut())});
            if (dossierLibelle != null && !dossierLibelle.isBlank()) {
                ctx.add(new String[]{"Dossier / société", dossierLibelle});
            }
            ctx.add(new String[]{"Date d'édition", LocalDate.now().format(FR_DATE)});
            pdf.keyValues(ctx);

            // Détail des débours.
            pdf.sectionTitle("2", "Détail des débours");
            BigDecimal total = BigDecimal.ZERO;
            if (items.isEmpty()) {
                pdf.note("Aucun débours enregistré pour ce ticket.");
            } else {
                PdfTable t = new PdfTable()
                        .column("Date", 13, Align.CENTER)
                        .column("Catégorie", 24, Align.LEFT)
                        .column("Description", 35, Align.LEFT)
                        .column("Montant (MAD)", 18, Align.RIGHT)
                        .column("PJ", 10, Align.CENTER);
                for (Debours d : items) {
                    boolean hasPj = d.pieceJointeFilename() != null && !d.pieceJointeFilename().isBlank();
                    t.row(Cell.of(d.dateEngagement() != null ? d.dateEngagement().format(FR_DATE) : "—"),
                            Cell.of(String.valueOf(d.categorie())),
                            Cell.of(safe(d.libelle())),
                            Cell.of(formatMad(d.montantMad()), Style.STRONG),
                            Cell.of(hasPj ? "Oui" : "—", hasPj ? Style.SUCCESS : Style.MUTED));
                    if (d.montantMad() != null) {
                        total = total.add(d.montantMad());
                    }
                }
                pdf.table(t);
            }

            pdf.totalRow("TOTAL DES DÉBOURS (" + items.size() + ") :", formatMad(total) + " MAD");
            pdf.spacer(6f);
            pdf.caption("Conforme aux obligations comptables (Loi 9-88, Code Général des Impôts).");

            return pdf.finish();
        }
    }

    private static String formatMad(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
