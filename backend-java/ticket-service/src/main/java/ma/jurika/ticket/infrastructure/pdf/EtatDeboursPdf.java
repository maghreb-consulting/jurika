package ma.jurika.ticket.infrastructure.pdf;

import ma.jurika.common.format.NombreEnLettres;
import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.pdf.JurikaPdfDocument;
import ma.jurika.common.pdf.JurikaPdfTheme;
import ma.jurika.common.pdf.PdfTable;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.EtatDebours;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.EtatDeboursDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot L5b (E3, RG-DEB-02, RG-FIC-08) : etat des debours au format PDF, a la charte JURIKA
 * (logo Moucharabieh, marine et or, decor de zellige et de moucharabieh), genere directement
 * par PDFBox (aucune conversion Word vers PDF). Remplace la sortie Word du lot L5 (E6).
 *
 * <p>Contenu : en-tete du cabinet (donnees du workspace), identification de la societe et du
 * ticket (references lisibles), tableau des frais, total en chiffres et en lettres (dirhams et
 * centimes), liste des pieces justificatives (une ligne sans piece est nommee comme telle).
 * Aucune ligne n'est imprimee pour une donnee absente (ni valeur de repli, ni balise).
 *
 * <p>Deterministe : memes donnees, memes octets (date d'edition = date de l'etat, proprietes et
 * identifiant du PDF figes).
 */
@Component
public class EtatDeboursPdf implements EtatDeboursDocument {

    public static final String MIME = "application/pdf";

    private static final String TIRET = " \u2014 ";
    private static final DateTimeFormatter MOIS_ANNEE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter COURTE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final Map<FormeJuridique, String> FORMES = new EnumMap<>(Map.of(
            FormeJuridique.SARL, "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e (SARL)",
            FormeJuridique.SARL_AU, "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e d\u2019associ\u00e9 unique (SARL AU)",
            FormeJuridique.SA, "Soci\u00e9t\u00e9 anonyme (SA)",
            FormeJuridique.SAS, "Soci\u00e9t\u00e9 par actions simplifi\u00e9e (SAS)",
            FormeJuridique.SCS, "Soci\u00e9t\u00e9 en commandite simple (SCS)",
            FormeJuridique.GIE, "Groupement d\u2019int\u00e9r\u00eat \u00e9conomique (GIE)"));

    static final Map<TicketType, String> TYPES = new EnumMap<>(Map.of(
            TicketType.CREATION, "Cr\u00e9ation de soci\u00e9t\u00e9",
            TicketType.IMPORT, "Reprise d\u2019un dossier existant",
            TicketType.MODIFICATION, "Modification",
            TicketType.DISSOLUTION, "Dissolution",
            TicketType.LIQUIDATION, "Liquidation",
            TicketType.SUCCURSALE_MA, "Ouverture d\u2019une succursale",
            TicketType.SUCCURSALE_ETR, "Succursale d\u2019une soci\u00e9t\u00e9 \u00e9trang\u00e8re",
            TicketType.FERMETURE_SUCCURSALE, "Fermeture d\u2019une succursale",
            TicketType.PV_AGO, "Assembl\u00e9e g\u00e9n\u00e9rale ordinaire"));

    @Override
    public byte[] rendre(EtatDebours etat) {
        EntrepriseDossier d = etat.dossier();
        Ticket t = etat.ticket();
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument().dateEdition(etat.dateEtat());
        doc.header(etat.cabinet(), "\u00c9tat des d\u00e9bours", d.raisonSociale(),
                "\u00c9tabli le " + dateLongue(etat.dateEtat()));

        doc.sectionTitle("1", "Soci\u00e9t\u00e9");
        List<String[]> societe = new ArrayList<>();
        champ(societe, "D\u00e9nomination", d.raisonSociale());
        champ(societe, "Forme juridique", d.formeJuridique() == null ? null : FORMES.get(d.formeJuridique()));
        champ(societe, "Si\u00e8ge social", joindre(", ", d.adresseSiege(), d.ville()));
        String rc = vide(d.rcNumero()) ? null
                : d.rcNumero().trim() + (vide(d.rcTribunal()) ? "" : " (" + d.rcTribunal().trim() + ")");
        champ(societe, "Registre du commerce", rc);
        champ(societe, "ICE", d.ice());
        doc.keyValues(societe);

        doc.sectionTitle("2", "Dossier et ticket");
        List<String[]> dossier = new ArrayList<>();
        champ(dossier, "Dossier", etat.referenceDossier());
        champ(dossier, "Ticket", joindre(TIRET, t.reference(), t.type() == null ? null : TYPES.get(t.type())));
        champ(dossier, "Objet", t.titre());
        doc.keyValues(dossier);

        doc.sectionTitle("3", "Frais avanc\u00e9s pour le compte du client");
        PdfTable frais = new PdfTable()
                .column("N\u00b0", 0.5f, PdfTable.Align.LEFT)
                .column("Date", 1.1f, PdfTable.Align.LEFT)
                .column("Type de frais", 1.8f, PdfTable.Align.LEFT)
                .column("Libell\u00e9", 2.8f, PdfTable.Align.LEFT)
                .column("Montant (DH)", 1.4f, PdfTable.Align.RIGHT);
        List<Debours> lignes = etat.lignes();
        for (int n = 0; n < lignes.size(); n++) {
            Debours l = lignes.get(n);
            frais.row(PdfTable.Cell.of(String.valueOf(n + 1)),
                    PdfTable.Cell.of(l.dateEngagement() == null ? "" : COURTE.format(l.dateEngagement())),
                    PdfTable.Cell.of(l.categorie().libelle()),
                    PdfTable.Cell.of(l.libelle()),
                    PdfTable.Cell.of(montant(l.montantMad())));
        }
        doc.table(frais);
        doc.totalRow("Total des d\u00e9bours", montant(etat.total()) + " DH");
        doc.spacer(4f);
        doc.paragraph("Arr\u00eat\u00e9 le pr\u00e9sent \u00e9tat \u00e0 la somme de "
                + NombreEnLettres.montantDirhams(etat.total()) + " (" + montant(etat.total()) + " DH).");

        doc.sectionTitle("4", "Pi\u00e8ces justificatives");
        PdfTable pieces = new PdfTable()
                .column("Ligne", 0.6f, PdfTable.Align.LEFT)
                .column("Frais", 3.8f, PdfTable.Align.LEFT)
                .column("Pi\u00e8ce justificative", 2.8f, PdfTable.Align.LEFT);
        for (int n = 0; n < lignes.size(); n++) {
            Debours l = lignes.get(n);
            PdfTable.Cell piece = vide(l.pieceJointeFilename())
                    ? PdfTable.Cell.of("Aucune pi\u00e8ce justificative jointe.", PdfTable.Style.MUTED)
                    : PdfTable.Cell.of(l.pieceJointeFilename().trim()
                            + (l.pieceDocumentId() != null ? " (Data Room du dossier)" : ""));
            pieces.row(PdfTable.Cell.of("n\u00b0 " + (n + 1)),
                    PdfTable.Cell.of(l.categorie().libelle() + TIRET + l.libelle()),
                    piece);
        }
        doc.table(pieces);
        return doc.finish();
    }

    /** Ligne "libelle, valeur" ; aucune ligne si la valeur manque. */
    private static void champ(List<String[]> rows, String libelle, String valeur) {
        if (!vide(valeur)) rows.add(new String[]{libelle, valeur.trim()});
    }

    /** {@code 1234.5} donne {@code 1 234,50} (espace insecable). */
    static String montant(BigDecimal v) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRANCE);
        s.setGroupingSeparator('\u00a0');
        s.setDecimalSeparator(',');
        DecimalFormat f = new DecimalFormat("#,##0.00", s);
        return f.format(v.setScale(2, RoundingMode.HALF_UP));
    }

    /** {@code 1er octobre 2026}, {@code 12 octobre 2026}. */
    static String dateLongue(LocalDate d) {
        return (d.getDayOfMonth() == 1 ? "1er" : String.valueOf(d.getDayOfMonth())) + " " + MOIS_ANNEE.format(d);
    }

    private static String joindre(String sep, String... parties) {
        List<String> l = new ArrayList<>();
        for (String p : parties) if (!vide(p)) l.add(p.trim());
        return l.isEmpty() ? null : String.join(sep, l);
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
