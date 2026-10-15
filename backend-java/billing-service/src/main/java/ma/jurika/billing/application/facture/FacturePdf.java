package ma.jurika.billing.application.facture;

import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.pdf.JurikaPdfDocument;
import ma.jurika.common.pdf.JurikaPdfTheme;
import ma.jurika.common.pdf.PdfTable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lot L7 (E5), RG-PAY-03 : PDF de la facture, a la charte JURIKA (JurikaPdfDocument : logo
 * Moucharabieh, polices Inter embarquees, pied de page avec ICE, RC et IF de l'emetteur). Rendu
 * deterministe : la date d'edition est la date d'emission, jamais l'horloge.
 */
@Component
public class FacturePdf {

    public record Donnees(String numero, LocalDate dateEmission, FactureService.Emetteur emetteur,
                          FactureService.Client client, String designation, LocalDate periodeDebut,
                          LocalDate periodeFin, String modePaiement, long htCentimes, BigDecimal tauxTva,
                          long tvaCentimes, long ttcCentimes, String reference) {}

    public byte[] rendre(Donnees d) {
        FactureService.Emetteur e = d.emetteur();
        CabinetIdentity enTete = CabinetIdentity.resolve(e.raisonSociale(), e.raisonSociale(), null, null,
                adresse(e.siegeSocial(), e.ville()), null, null, null, e.ice(), e.rcNumero(), e.identifiantFiscal());
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument()
                .dateEdition(d.dateEmission())
                .header(enTete, "Facture", "N\u00b0 " + d.numero(), "Date d\u2019\u00e9mission : " + FactureService.date(d.dateEmission()));

        doc.sectionTitle("1", "\u00c9metteur");
        List<String[]> emetteur = new ArrayList<>();
        emetteur.add(new String[]{"Raison sociale", joindre(e.raisonSociale(), e.formeJuridique())});
        if (present(e.capitalSocial())) emetteur.add(new String[]{"Capital social", e.capitalSocial()});
        emetteur.add(new String[]{"Si\u00e8ge social", adresse(e.siegeSocial(), e.ville())});
        emetteur.add(new String[]{"ICE", e.ice()});
        emetteur.add(new String[]{"Identifiant fiscal (IF)", e.identifiantFiscal()});
        emetteur.add(new String[]{"Registre du commerce (RC)",
                e.rcNumero() + (present(e.rcTribunal()) ? " \u2014 " + e.rcTribunal() : "")});
        emetteur.add(new String[]{"Taxe professionnelle (TP)", e.taxeProfessionnelle()});
        doc.keyValues(emetteur);

        doc.sectionTitle("2", "Client");
        FactureService.Client c = d.client();
        List<String[]> client = new ArrayList<>();
        client.add(new String[]{"D\u00e9nomination", c.denomination()});
        if (present(c.adresse()) || present(c.ville())) client.add(new String[]{"Adresse", adresse(c.adresse(), c.ville())});
        client.add(new String[]{"ICE", c.ice()});
        if (present(c.identifiantFiscal())) client.add(new String[]{"Identifiant fiscal (IF)", c.identifiantFiscal()});
        if (present(c.rc())) client.add(new String[]{"Registre du commerce (RC)", c.rc()});
        doc.keyValues(client);

        doc.sectionTitle("3", "D\u00e9signation");
        PdfTable t = new PdfTable()
                .column("D\u00e9signation", 5f, PdfTable.Align.LEFT)
                .column("Montant HT", 1.6f, PdfTable.Align.RIGHT);
        t.row(PdfTable.Cell.of(d.designation()), PdfTable.Cell.of(dirhams(d.htCentimes())).align(PdfTable.Align.RIGHT));
        doc.table(t);
        doc.totalRow("Total hors taxes", dirhams(d.htCentimes()));
        doc.totalRow("TVA au taux de " + taux(d.tauxTva()) + " %", dirhams(d.tvaCentimes()));
        doc.totalRow("Total TTC", dirhams(d.ttcCentimes()));

        doc.sectionTitle("4", "R\u00e8glement");
        List<String[]> reglement = new ArrayList<>();
        reglement.add(new String[]{"Mode de paiement", d.modePaiement() + " \u2014 facture acquitt\u00e9e, en dirhams marocains"});
        if (present(d.reference())) reglement.add(new String[]{"R\u00e9f\u00e9rence", d.reference()});
        doc.keyValues(reglement);
        return doc.finish();
    }

    /** 1234567 centimes -> "12 345,67 DH". */
    public static String dirhams(long centimes) {
        long entier = Math.abs(centimes) / 100;
        long cts = Math.abs(centimes) % 100;
        String groupe = String.format(Locale.ROOT, "%,d", entier).replace(',', ' ');
        return (centimes < 0 ? "-" : "") + groupe + "," + String.format(Locale.ROOT, "%02d", cts) + " DH";
    }

    static String taux(BigDecimal t) {
        BigDecimal s = t.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString().replace('.', ',');
    }

    private static String adresse(String a, String ville) {
        return joindre(a, ville);
    }

    private static String joindre(String a, String b) {
        if (!present(a)) return present(b) ? b : "";
        return present(b) ? a + ", " + b : a;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
