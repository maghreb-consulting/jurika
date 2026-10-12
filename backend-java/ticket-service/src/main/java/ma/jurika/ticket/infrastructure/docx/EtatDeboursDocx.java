package ma.jurika.ticket.infrastructure.docx;

import ma.jurika.common.docx.ZipHorodatage;
import ma.jurika.common.format.NombreEnLettres;
import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.EtatDebours;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.EtatDeboursDocument;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lot L5 (E6, RG-DEB-02, P5) : gabarit Word de l'etat des debours, construit ici (le corpus
 * du cabinet n'en porte aucun). En-tete du cabinet (donnees du workspace), identification de
 * la societe et du ticket (references lisibles), tableau des frais, total en chiffres et en
 * lettres (dirhams et centimes), liste des pieces justificatives (une ligne sans piece est
 * nommee comme telle).
 *
 * <p>Deterministe : memes donnees, memes octets (date de creation du paquet = date de l'etat,
 * horodatage ZIP fige par {@link ZipHorodatage}). Aucune ligne n'est imprimee pour une donnee
 * absente (ni valeur de repli, ni balise).
 */
@Component
public class EtatDeboursDocx implements EtatDeboursDocument {

    private static final Logger log = LoggerFactory.getLogger(EtatDeboursDocx.class);

    private static final String POLICE = "Calibri";
    private static final String TIRET = " \u2014 ";
    private static final DateTimeFormatter MOIS_ANNEE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter COURTE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final Map<FormeJuridique, String> FORMES = new EnumMap<>(Map.of(
            FormeJuridique.SARL, "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e (SARL)",
            FormeJuridique.SARL_AU, "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e d'associ\u00e9 unique (SARL AU)",
            FormeJuridique.SA, "Soci\u00e9t\u00e9 anonyme (SA)",
            FormeJuridique.SAS, "Soci\u00e9t\u00e9 par actions simplifi\u00e9e (SAS)",
            FormeJuridique.SCS, "Soci\u00e9t\u00e9 en commandite simple (SCS)",
            FormeJuridique.GIE, "Groupement d'int\u00e9r\u00eat \u00e9conomique (GIE)"));

    static final Map<TicketType, String> TYPES = new EnumMap<>(Map.of(
            TicketType.CREATION, "Cr\u00e9ation de soci\u00e9t\u00e9",
            TicketType.IMPORT, "Reprise d'un dossier existant",
            TicketType.MODIFICATION, "Modification",
            TicketType.DISSOLUTION, "Dissolution",
            TicketType.LIQUIDATION, "Liquidation",
            TicketType.SUCCURSALE_MA, "Ouverture d'une succursale",
            TicketType.SUCCURSALE_ETR, "Succursale d'une soci\u00e9t\u00e9 \u00e9trang\u00e8re",
            TicketType.FERMETURE_SUCCURSALE, "Fermeture d'une succursale",
            TicketType.PV_AGO, "Assembl\u00e9e g\u00e9n\u00e9rale ordinaire"));

    @Override
    public byte[] rendre(EtatDebours etat) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            mettreEnPage(doc);
            proprietes(doc, etat);
            enTeteCabinet(doc, etat.cabinet());
            titre(doc, etat.dateEtat());
            societe(doc, etat.dossier());
            dossierEtTicket(doc, etat.referenceDossier(), etat.ticket());
            tableau(doc, etat.lignes());
            total(doc, etat.total());
            pieces(doc, etat.lignes());
            doc.write(out);
            return ZipHorodatage.figer(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Etat des debours : ecriture du document impossible", e);
        }
    }

    // --- Parties du document -------------------------------------------------------------

    private static void enTeteCabinet(XWPFDocument doc, CabinetIdentity c) {
        if (c.hasLogo()) logo(doc, c);
        XWPFParagraph nom = paragraphe(doc, ParagraphAlignment.LEFT, 0);
        texte(nom, c.name(), true, 14);
        ligneSiPresente(doc, c.adresse());
        ligneSiPresente(doc, joindre(
                prefixe("T\u00e9l. : ", c.telephone()),
                prefixe("Courriel : ", c.email()),
                prefixe("Site : ", c.siteWeb())));
        ligneSiPresente(doc, joindre(
                prefixe("ICE : ", c.ice()),
                prefixe("RC : ", c.rc()),
                prefixe("IF : ", c.iff())));
    }

    private static void titre(XWPFDocument doc, LocalDate date) {
        XWPFParagraph t = paragraphe(doc, ParagraphAlignment.CENTER, 240);
        texte(t, "\u00c9TAT DES D\u00c9BOURS", true, 16);
        XWPFParagraph d = paragraphe(doc, ParagraphAlignment.CENTER, 0);
        texte(d, "\u00c9tabli le " + dateLongue(date), false, 11).setItalic(true);
    }

    private static void societe(XWPFDocument doc, EntrepriseDossier d) {
        rubrique(doc, "Soci\u00e9t\u00e9");
        champ(doc, "D\u00e9nomination", d.raisonSociale());
        champ(doc, "Forme juridique", d.formeJuridique() == null ? null : FORMES.get(d.formeJuridique()));
        champ(doc, "Si\u00e8ge social", joindreVirgule(d.adresseSiege(), d.ville()));
        String rc = vide(d.rcNumero()) ? null
                : d.rcNumero().trim() + (vide(d.rcTribunal()) ? "" : " (" + d.rcTribunal().trim() + ")");
        champ(doc, "Registre du commerce", rc);
        champ(doc, "ICE", d.ice());
    }

    private static void dossierEtTicket(XWPFDocument doc, String referenceDossier, Ticket t) {
        rubrique(doc, "Dossier et ticket");
        champ(doc, "Dossier", referenceDossier);
        champ(doc, "Ticket", joindre(t.reference(), t.type() == null ? null : TYPES.get(t.type())));
        champ(doc, "Objet", t.titre());
    }

    private static void tableau(XWPFDocument doc, List<Debours> lignes) {
        rubrique(doc, "Frais avanc\u00e9s pour le compte du client");
        XWPFTable table = doc.createTable(lignes.size() + 1, 5);
        table.setWidth("100%");
        String[] entetes = {"N\u00b0", "Date", "Type de frais", "Libell\u00e9", "Montant (DH)"};
        String[] largeurs = {"7%", "14%", "24%", "37%", "18%"};
        XWPFTableRow tete = table.getRow(0);
        tete.setRepeatHeader(true);
        for (int i = 0; i < entetes.length; i++) {
            cellule(tete.getCell(i), entetes[i], true, i == 4 ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT,
                    largeurs[i]);
        }
        for (int n = 0; n < lignes.size(); n++) {
            Debours d = lignes.get(n);
            XWPFTableRow r = table.getRow(n + 1);
            cellule(r.getCell(0), String.valueOf(n + 1), false, ParagraphAlignment.LEFT, largeurs[0]);
            cellule(r.getCell(1), d.dateEngagement() == null ? "" : COURTE.format(d.dateEngagement()), false,
                    ParagraphAlignment.LEFT, largeurs[1]);
            cellule(r.getCell(2), d.categorie().libelle(), false, ParagraphAlignment.LEFT, largeurs[2]);
            cellule(r.getCell(3), d.libelle(), false, ParagraphAlignment.LEFT, largeurs[3]);
            cellule(r.getCell(4), montant(d.montantMad()), false, ParagraphAlignment.RIGHT, largeurs[4]);
        }
    }

    private static void total(XWPFDocument doc, BigDecimal total) {
        XWPFParagraph p = paragraphe(doc, ParagraphAlignment.RIGHT, 160);
        texte(p, "Total des d\u00e9bours : ", false, 11);
        texte(p, montant(total) + " DH", true, 11);
        XWPFParagraph l = paragraphe(doc, ParagraphAlignment.BOTH, 120);
        texte(l, "Arr\u00eat\u00e9 le pr\u00e9sent \u00e9tat \u00e0 la somme de ", false, 11);
        texte(l, NombreEnLettres.montantDirhams(total), true, 11);
        texte(l, " (" + montant(total) + " DH).", false, 11);
    }

    private static void pieces(XWPFDocument doc, List<Debours> lignes) {
        rubrique(doc, "Pi\u00e8ces justificatives");
        for (int n = 0; n < lignes.size(); n++) {
            Debours d = lignes.get(n);
            XWPFParagraph p = paragraphe(doc, ParagraphAlignment.LEFT, 0);
            texte(p, "Ligne n\u00b0 " + (n + 1) + TIRET + d.categorie().libelle() + TIRET + d.libelle() + " : ",
                    false, 11);
            if (vide(d.pieceJointeFilename())) {
                texte(p, "aucune pi\u00e8ce justificative jointe.", false, 11).setItalic(true);
            } else {
                texte(p, d.pieceJointeFilename().trim(), true, 11);
                if (d.pieceDocumentId() != null) {
                    texte(p, " (Data Room du dossier).", false, 11);
                }
            }
        }
    }

    // --- Outils --------------------------------------------------------------------------

    /** A4 portrait, marges de 2 cm. */
    private static void mettreEnPage(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
        CTPageSz taille = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        taille.setW(BigInteger.valueOf(11906));
        taille.setH(BigInteger.valueOf(16838));
        CTPageMar marges = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        BigInteger deuxCm = BigInteger.valueOf(1134);
        marges.setTop(deuxCm);
        marges.setBottom(deuxCm);
        marges.setLeft(deuxCm);
        marges.setRight(deuxCm);
    }

    /** Proprietes du paquet figees sur la date de l'etat (sinon : heure de generation). */
    private static void proprietes(XWPFDocument doc, EtatDebours etat) {
        var core = doc.getProperties().getCoreProperties();
        Date jour = Date.from(etat.dateEtat().atStartOfDay(ZoneOffset.UTC).toInstant());
        core.setCreated(Optional.of(jour));
        core.setModified(Optional.of(jour));
        core.setCreator("JURIKA");
        core.setTitle("\u00c9tat des d\u00e9bours" + TIRET + etat.ticket().reference());
    }

    private static void logo(XWPFDocument doc, CabinetIdentity c) {
        String type = c.logoContentType() == null ? "" : c.logoContentType().toLowerCase(Locale.ROOT);
        int format = type.contains("png") ? Document.PICTURE_TYPE_PNG
                : (type.contains("jpeg") || type.contains("jpg")) ? Document.PICTURE_TYPE_JPEG : -1;
        if (format < 0) {
            log.warn("Etat des debours : logo du cabinet ignore (format {} non pris en charge)", type);
            return;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(c.logo()));
            if (img == null || img.getHeight() == 0) {
                log.warn("Etat des debours : logo du cabinet illisible, ignore");
                return;
            }
            double hauteur = 48;
            double largeur = Math.min(180, hauteur * img.getWidth() / img.getHeight());
            XWPFRun r = paragraphe(doc, ParagraphAlignment.LEFT, 0).createRun();
            r.addPicture(new ByteArrayInputStream(c.logo()), format, "logo", Units.toEMU(largeur),
                    Units.toEMU(largeur * img.getHeight() / img.getWidth()));
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            log.warn("Etat des debours : logo du cabinet ignore ({})", e.getMessage());
        }
    }

    private static void rubrique(XWPFDocument doc, String titre) {
        XWPFParagraph p = paragraphe(doc, ParagraphAlignment.LEFT, 240);
        texte(p, titre, true, 12).setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
    }

    /** Ligne "Libelle : valeur" ; aucune ligne si la valeur manque. */
    private static void champ(XWPFDocument doc, String libelle, String valeur) {
        if (vide(valeur)) return;
        XWPFParagraph p = paragraphe(doc, ParagraphAlignment.LEFT, 0);
        texte(p, libelle + " : ", false, 11);
        texte(p, valeur.trim(), true, 11);
    }

    private static void ligneSiPresente(XWPFDocument doc, String valeur) {
        if (vide(valeur)) return;
        texte(paragraphe(doc, ParagraphAlignment.LEFT, 0), valeur.trim(), false, 10);
    }

    private static void cellule(XWPFTableCell cell, String valeur, boolean gras, ParagraphAlignment alignement,
                                String largeur) {
        cell.setWidth(largeur);
        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setAlignment(alignement);
        texte(p, valeur, gras, 10);
    }

    private static XWPFParagraph paragraphe(XWPFDocument doc, ParagraphAlignment alignement, int avant) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alignement);
        p.setSpacingBefore(avant);
        p.setSpacingAfter(60);
        return p;
    }

    private static XWPFRun texte(XWPFParagraph p, String valeur, boolean gras, int taille) {
        XWPFRun r = p.createRun();
        r.setFontFamily(POLICE);
        r.setFontSize(taille);
        r.setBold(gras);
        r.setText(valeur);
        return r;
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

    private static String prefixe(String prefixe, String valeur) {
        return vide(valeur) ? null : prefixe + valeur.trim();
    }

    private static String joindre(String... parties) {
        List<String> l = new ArrayList<>();
        for (String p : parties) if (!vide(p)) l.add(p.trim());
        return l.isEmpty() ? null : String.join(TIRET, l);
    }

    private static String joindreVirgule(String... parties) {
        List<String> l = new ArrayList<>();
        for (String p : parties) if (!vide(p)) l.add(p.trim());
        return l.isEmpty() ? null : String.join(", ", l);
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
