package ma.jurika.dataroom.infrastructure.pdf;

import ma.jurika.common.pdf.JurikaPdfDocument;
import ma.jurika.common.pdf.JurikaPdfTheme;
import ma.jurika.common.pdf.PdfTable;
import ma.jurika.common.pdf.PdfTable.Align;
import ma.jurika.common.pdf.PdfTable.Cell;
import ma.jurika.common.pdf.PdfTable.Style;
import ma.jurika.dataroom.api.dto.FicheClientDtos.DocumentEnVigueur;
import ma.jurika.dataroom.api.dto.FicheClientDtos.DocumentHistoryEntry;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identity;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Générateur PDF de la « Fiche client » à la charte JURIKA.
 *
 * <p>Migration 2026-07-14 : rendu porté sur Apache PDFBox via le thème partagé
 * {@link ma.jurika.common.pdf.JurikaPdfTheme} (remplace iText/AGPL). La mise en
 * page (4 sections) reste fidèle à l'exemplaire validé. Cette classe ne fait plus
 * que de l'assemblage métier : identité, opérations, documents, historique.
 */
public class FicheClientPdf {

    private static final DateTimeFormatter D_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter D_INSTANT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private static final Map<String, String> STATUT_LABELS = Map.of(
            "EN_CONSTITUTION", "En constitution",
            "ACTIVE", "Active",
            "DISSOUTE", "Dissoute",
            "EN_LIQUIDATION", "En liquidation",
            "LIQUIDEE", "Liquidée",
            "RADIE", "Radiée");

    /** Types de documents techniques -> libellé lisible (acronymes préservés). */
    private static final Map<String, String> DOC_TYPE_LABELS = Map.ofEntries(
            Map.entry("STATUTS", "Statuts"),
            Map.entry("PV_AGE", "PV AGE"),
            Map.entry("PV_AGO", "PV AGO"),
            Map.entry("PV_MODIFICATION", "PV de modification"),
            Map.entry("PV_DISSOLUTION", "PV de dissolution"),
            Map.entry("PV_LIQUIDATION", "PV de liquidation"),
            Map.entry("ACTE_NOMINATION", "Acte de nomination"),
            Map.entry("CONTRAT_BAIL", "Contrat de bail"),
            Map.entry("CNIE_GERANT", "CNIE du gérant"),
            Map.entry("ANNONCE_JAL", "Annonce JAL"),
            Map.entry("RC", "Registre du commerce"),
            Map.entry("ICE", "ICE"),
            Map.entry("TP", "Taxe professionnelle"),
            Map.entry("CNSS", "CNSS"),
            Map.entry("APOSTILLE", "Apostille"),
            Map.entry("CIN_NOUVELLE", "CIN (nouvelle)"),
            Map.entry("CIN_ANCIENNE", "CIN (ancienne)"),
            Map.entry("CN", "Certificat négatif"),
            Map.entry("AUTRE", "Autre"));

    private final FicheClientView view;

    public FicheClientPdf(FicheClientView view) {
        this.view = view;
    }

    public byte[] generate() {
        Identity id = view.identity();
        try (JurikaPdfDocument pdf = JurikaPdfTheme.newDocument()) {
            pdf.header(view.cabinet(), "Fiche client", safe(id.raisonSociale()),
                    formeLabel(id.formeJuridique()) + "   ·   " + statutLabel(id.statut()));

            sectionIdentity(pdf, id);
            sectionOperations(pdf);
            sectionDocumentsEnVigueur(pdf);
            sectionHistorique(pdf);

            return pdf.finish();
        }
    }

    // ── Section 1 : identifiants ──────────────────────────────────────────────

    private void sectionIdentity(JurikaPdfDocument pdf, Identity id) {
        pdf.sectionTitle("1", "Identifiants de la société");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Raison sociale", value(id.raisonSociale(), id.statut())});
        rows.add(new String[]{"Forme juridique", formeLabel(id.formeJuridique())});
        rows.add(new String[]{"Statut du dossier", statutLabel(id.statut())});
        rows.add(new String[]{"ICE", value(id.ice(), id.statut())});
        rows.add(new String[]{"Registre du commerce", rcValue(id)});
        rows.add(new String[]{"Identifiant fiscal", value(id.identifiantFiscal(), id.statut())});
        rows.add(new String[]{"Taxe professionnelle (patente)", value(id.taxeProfessionnelle(), id.statut())});
        rows.add(new String[]{"CNSS", value(id.cnss(), id.statut())});
        rows.add(new String[]{"Adresse du siège", value(id.adresseSiege(), id.statut())});
        rows.add(new String[]{"Ville", value(id.ville(), id.statut())});
        rows.add(new String[]{"Capital social", capitalValue(id)});
        rows.add(new String[]{"Date de constitution", dateValue(id.dateConstitution(), id.statut())});
        pdf.keyValues(rows);
    }

    // ── Section 2 : opérations juridiques ─────────────────────────────────────

    private void sectionOperations(JurikaPdfDocument pdf) {
        pdf.sectionTitle("2", "Opérations juridiques subies par la société");
        if (view.operations().isEmpty()) {
            pdf.note("Aucune opération juridique enregistrée à ce jour.");
            return;
        }
        PdfTable t = new PdfTable()
                .column("Opération", 42, Align.LEFT)
                .column("Ouverture", 15, Align.CENTER)
                .column("Finalisation", 15, Align.CENTER)
                .column("Date de l'acte", 14, Align.CENTER)
                .column("Statut", 14, Align.CENTER);
        for (Operation op : view.operations()) {
            Cell opCell = new Cell().add(safe(op.typeLabel()), Style.STRONG);
            if (op.sousTypeLabel() != null && !op.sousTypeLabel().isBlank()) {
                opCell.add("— " + op.sousTypeLabel(), Style.NORMAL);
            }
            if (op.reference() != null && !op.reference().isBlank()) {
                opCell.add("Réf. " + op.reference(), Style.MUTED);
            }
            Cell statut = new Cell().add(op.finalisee() ? "Finalisée" : "En cours",
                    op.finalisee() ? Style.SUCCESS : Style.ACCENT);
            t.row(opCell,
                    Cell.of(fmt(op.dateDebut())),
                    Cell.of(op.dateFinalisation() != null ? fmt(op.dateFinalisation()) : "—"),
                    Cell.of(op.dateActe() != null ? op.dateActe().format(D_DATE) : "—"),
                    statut);
        }
        pdf.table(t);
    }

    // ── Section 3 : documents en vigueur ──────────────────────────────────────

    private void sectionDocumentsEnVigueur(JurikaPdfDocument pdf) {
        pdf.sectionTitle("3", "Documents en vigueur");
        if (view.documentsEnVigueur().isEmpty()) {
            pdf.note("Aucun document en vigueur pour ce dossier.");
            return;
        }
        PdfTable t = new PdfTable()
                .column("Document", 46, Align.LEFT)
                .column("Type", 24, Align.LEFT)
                .column("Établi le", 18, Align.CENTER)
                .column("Version", 12, Align.CENTER);
        for (DocumentEnVigueur d : view.documentsEnVigueur()) {
            t.row(Cell.of(safe(d.title())),
                    Cell.of(typeLabel(d.documentType())),
                    Cell.of(fmt(d.dateEtablissement())),
                    Cell.of("v" + d.version()));
        }
        pdf.table(t);
    }

    // ── Section 4 : historique documentaire ───────────────────────────────────

    private void sectionHistorique(JurikaPdfDocument pdf) {
        pdf.sectionTitle("4", "Historique des documents (ajouts / remplacements)");
        if (view.historiqueDocuments().isEmpty()) {
            pdf.note("Aucun mouvement documentaire enregistré.");
            return;
        }
        PdfTable t = new PdfTable()
                .column("Date", 16, Align.CENTER)
                .column("Action", 18, Align.LEFT)
                .column("Document", 40, Align.LEFT)
                .column("Auteur", 26, Align.LEFT);
        for (DocumentHistoryEntry h : view.historiqueDocuments()) {
            Cell docCell = new Cell().add(safe(h.title()) + "  (v" + h.version() + ")", Style.NORMAL);
            if (h.motif() != null && !h.motif().isBlank()) {
                docCell.add(h.motif(), Style.MUTED);
            }
            Cell action = new Cell().add(actionLabel(h.action()),
                    "AJOUT".equals(h.action()) ? Style.SUCCESS : Style.ACCENT);
            t.row(Cell.of(fmt(h.date())), action, docCell, Cell.of(safe(h.auteur())));
        }
        pdf.table(t);
    }

    // ── Valeurs / formatage ───────────────────────────────────────────────────

    /**
     * « Jamais de blanc » : selon le statut, un champ vide devient « En cours
     * d'attribution » (EN_CONSTITUTION) ou « — Non renseigné — » (autres statuts).
     */
    private static String value(String raw, String statut) {
        if (raw != null && !raw.isBlank()) return raw;
        return "EN_CONSTITUTION".equals(statut) ? "En cours d'attribution" : "— Non renseigné —";
    }

    private static String rcValue(Identity id) {
        String num = id.rcNumero();
        if (num == null || num.isBlank()) return value(null, id.statut());
        String trib = id.rcTribunal();
        return (trib == null || trib.isBlank()) ? num : num + " — " + trib;
    }

    private static String capitalValue(Identity id) {
        BigDecimal cap = id.capitalSocialMad();
        if (cap == null) return value(null, id.statut());
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("fr-MA"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(cap) + " MAD";
    }

    private static String dateValue(LocalDate d, String statut) {
        return d == null ? value(null, statut) : d.format(D_DATE);
    }

    private static String formeLabel(String forme) {
        if (forme == null || forme.isBlank()) return "—";
        return switch (forme) {
            case "SARL_AU" -> "SARL à associé unique (SARL AU)";
            case "SARL" -> "Société à responsabilité limitée (SARL)";
            default -> forme;
        };
    }

    private static String statutLabel(String statut) {
        if (statut == null) return "—";
        return STATUT_LABELS.getOrDefault(statut, statut);
    }

    private static String actionLabel(String action) {
        return switch (action == null ? "" : action) {
            case "AJOUT" -> "Ajout";
            case "REMPLACEMENT" -> "Remplacement";
            case "RESTAURATION" -> "Restauration";
            default -> safe(action);
        };
    }

    private static String typeLabel(String type) {
        if (type == null || type.isBlank()) return "—";
        String mapped = DOC_TYPE_LABELS.get(type);
        if (mapped != null) return mapped;
        String base = type.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private static String fmt(Instant instant) {
        return instant == null ? "—" : D_INSTANT.format(instant);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
