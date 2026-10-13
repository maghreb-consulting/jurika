package ma.jurika.dataroom.infrastructure.pdf;

import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.pdf.JurikaPdfDocument;
import ma.jurika.common.pdf.JurikaPdfTheme;
import ma.jurika.common.pdf.PdfTable;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheDossierView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identifiant;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import ma.jurika.dataroom.api.dto.FicheClientDtos.VersionTp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lot L5b (E3, RG-FIC-05 a 08) : fiche client et fiche dossier au format PDF, a la charte JURIKA
 * (logo Moucharabieh, marine et or, decor de zellige et de moucharabieh), generees directement
 * par PDFBox. Deux documents distincts :
 * <ul>
 *   <li>fiche client (RG-FIC-01 a 05) : UNIQUEMENT les identifiants de la societe, chacun avec
 *       sa provenance (RG-FIC-03), et toutes les versions de la taxe professionnelle, la
 *       version en vigueur signalee (RG-FIC-02). Aucun historique d'operations, aucune trace
 *       des modifications (consultable a l'ecran seulement) ;</li>
 *   <li>fiche dossier (RG-FIC-06, 07) : les memes identifiants et toutes les operations
 *       juridiques du dossier (type, reference, ouverture, cloture ou annulation, statut,
 *       responsable), a jour de la derniere cloture.</li>
 * </ul>
 * Deterministe : memes donnees et meme date d'edition, memes octets.
 */
public final class FichesPdf {

    public static final String MIME = "application/pdf";

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final String NON_RENSEIGNE = "Non renseign\u00e9";
    static final String NON_RENSEIGNEE = "Non renseign\u00e9e";

    private FichesPdf() {}

    public static byte[] ficheClient(CabinetIdentity cabinet, FicheClientView fiche, LocalDate dateEdition) {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument().dateEdition(dateEdition);
        doc.header(cabinet, "Fiche client", denomination(fiche),
                "Identifiants de la soci\u00e9t\u00e9 en vigueur au " + dateEdition.format(JJ_MM_AAAA));
        identifiants(doc, "1", fiche);
        versionsTp(doc, "2", fiche);
        return doc.finish();
    }

    public static byte[] ficheDossier(CabinetIdentity cabinet, FicheDossierView vue, LocalDate dateEdition) {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument().dateEdition(dateEdition);
        doc.header(cabinet, "Fiche dossier", denomination(vue.fiche()), aJour(vue.derniereCloture()));
        identifiants(doc, "1", vue.fiche());
        versionsTp(doc, "2", vue.fiche());
        operations(doc, "3", vue.operations());
        return doc.finish();
    }

    /** "A jour de la derniere cloture, le ..." (RG-FIC-07, RG-CLO-04). */
    public static String aJour(Instant derniereCloture) {
        return derniereCloture == null
                ? "Aucune op\u00e9ration cl\u00f4tur\u00e9e \u00e0 ce jour."
                : "\u00c0 jour de la derni\u00e8re cl\u00f4ture, le " + date(derniereCloture) + ".";
    }

    private static void identifiants(JurikaPdfDocument doc, String num, FicheClientView fiche) {
        doc.sectionTitle(num, "Identifiants de la soci\u00e9t\u00e9");
        List<String[]> rows = new ArrayList<>();
        for (Identifiant i : fiche.identifiants()) {
            if (i.valeur() == null) {
                rows.add(new String[]{i.libelle(), NON_RENSEIGNE, i.provenance()});
            } else {
                String prov = i.provenance() == null ? null
                        : i.provenance().startsWith("Provenance") ? i.provenance() : "Provenance : " + i.provenance();
                rows.add(new String[]{i.libelle(), i.valeur(), prov});
            }
        }
        doc.keyValues(rows);
    }

    private static void versionsTp(JurikaPdfDocument doc, String num, FicheClientView fiche) {
        doc.sectionTitle(num, "Taxe professionnelle (patente) : versions successives");
        if (fiche.versionsTp().isEmpty()) {
            doc.note("Aucune taxe professionnelle enregistr\u00e9e.");
            return;
        }
        PdfTable t = new PdfTable()
                .column("Num\u00e9ro", 2.4f, PdfTable.Align.LEFT)
                .column("Date de prise d'effet", 2f, PdfTable.Align.LEFT)
                .column("\u00c9tat", 1.6f, PdfTable.Align.LEFT);
        for (VersionTp v : fiche.versionsTp()) {
            t.row(PdfTable.Cell.of(v.numero(), v.enVigueur() ? PdfTable.Style.STRONG : PdfTable.Style.NORMAL),
                    PdfTable.Cell.of(v.dateEffet() == null ? NON_RENSEIGNEE : v.dateEffet().format(JJ_MM_AAAA)),
                    v.enVigueur() ? PdfTable.Cell.of("En vigueur", PdfTable.Style.ACCENT)
                            : PdfTable.Cell.of("Remplac\u00e9e", PdfTable.Style.MUTED));
        }
        doc.table(t);
    }

    private static void operations(JurikaPdfDocument doc, String num, List<Operation> operations) {
        doc.sectionTitle(num, "Op\u00e9rations juridiques du dossier");
        if (operations.isEmpty()) {
            doc.note("Aucune op\u00e9ration enregistr\u00e9e.");
            return;
        }
        PdfTable t = new PdfTable()
                .column("Op\u00e9ration", 2.2f, PdfTable.Align.LEFT)
                .column("R\u00e9f\u00e9rence", 1.2f, PdfTable.Align.LEFT)
                .column("Ouverture", 1.3f, PdfTable.Align.LEFT)
                .column("Cl\u00f4ture ou annulation", 1.7f, PdfTable.Align.LEFT)
                .column("Statut", 1.5f, PdfTable.Align.LEFT)
                .column("Responsable", 1.5f, PdfTable.Align.LEFT);
        for (Operation o : operations) {
            PdfTable.Cell type = PdfTable.Cell.of(o.typeLabel(), PdfTable.Style.STRONG);
            if (o.sousTypeLabel() != null) type.add(o.sousTypeLabel(), PdfTable.Style.MUTED);
            String fin = o.dateFin() == null ? "En cours"
                    : ("ANNULATION".equals(o.natureFin()) ? "Annul\u00e9 le " : "Cl\u00f4tur\u00e9 le ")
                    + date(o.dateFin());
            t.row(type,
                    PdfTable.Cell.of(o.reference() == null ? NON_RENSEIGNEE : o.reference()),
                    PdfTable.Cell.of(o.dateOuverture() == null ? NON_RENSEIGNEE : date(o.dateOuverture())),
                    PdfTable.Cell.of(fin),
                    PdfTable.Cell.of(o.statutLabel()),
                    PdfTable.Cell.of(o.responsable()));
        }
        doc.table(t);
    }

    private static String denomination(FicheClientView fiche) {
        return fiche.denomination() == null || fiche.denomination().isBlank() ? NON_RENSEIGNEE : fiche.denomination();
    }

    private static String date(Instant i) {
        return LocalDate.ofInstant(i, CASABLANCA).format(JJ_MM_AAAA);
    }
}
