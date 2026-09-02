package ma.jurika.dashboard.application;

import java.util.Map;

/**
 * Traduction des codes d'action audit_log en libelles FR lisibles (E2).
 * Source unique cote backend pour que le frontend affiche directement
 * "qui / action / quoi / quand" sans dupliquer la table de correspondance.
 */
public final class AuditActionLabels {

    private AuditActionLabels() {}

    private static final Map<String, String> LABELS = Map.ofEntries(
            // Tickets
            Map.entry("TICKET_CREATED", "Ticket cree"),
            Map.entry("TICKET_TRANSITIONED", "Statut du ticket modifie"),
            Map.entry("TICKET_UPDATED", "Ticket modifie"),
            Map.entry("TICKET_ASSIGNED", "Ticket reassigne"),
            // Dossier
            Map.entry("DOSSIER_TRANSFERE", "Dossier transfere"),
            Map.entry("DATAROOM_DELETED", "Data Room supprime"),
            Map.entry("DATAROOM_SUSPENSION_TOGGLED", "Acces Data Room suspendu/reactive"),
            Map.entry("PERMISSIONS_CHANGED", "Permissions client modifiees"),
            // Documents juridiques
            Map.entry("DOCUMENT_UPLOADED", "Document juridique depose"),
            Map.entry("DOCUMENT_PREVIEWED", "Document consulte"),
            Map.entry("DOCUMENT_DOWNLOADED", "Document telecharge"),
            Map.entry("DOCUMENT_DELETED", "Document supprime"),
            Map.entry("DOCUMENTS_DELETED_BULK", "Documents supprimes (lot)"),
            Map.entry("DOCUMENT_VERSION_REPLACED", "Version de document remplacee"),
            Map.entry("DOCUMENT_VERSION_RESTORED", "Version de document restauree"),
            // Comptable / fiscal
            Map.entry("COMPTABLE_UPLOADED", "Document comptable depose"),
            Map.entry("FISCAL_UPLOADED", "Document fiscal depose"),
            Map.entry("FISCAL_DELETED", "Document fiscal supprime"),
            Map.entry("FISCAL_EXPORTED_ZIP", "Export fiscal (ZIP)"),
            Map.entry("EXERCICE_OPENED", "Exercice fiscal ouvert"),
            Map.entry("EXERCICE_CLOTURED", "Exercice fiscal cloture"),
            Map.entry("EXERCICE_LOCKED", "Exercice fiscal verrouille"),
            Map.entry("EXERCICE_UNLOCKED", "Exercice fiscal deverrouille"),
            Map.entry("ECHEANCE_MARQUEE_TRAITEE", "Echeance marquee traitee"),
            // Workflow
            Map.entry("WORKFLOW_STARTED", "Workflow demarre"),
            Map.entry("WORKFLOW_STEP_EXECUTED", "Etape de workflow executee"),
            // IA
            Map.entry("AI_DOCUMENT_GENERATED", "Document genere par IA"),
            Map.entry("REPORT_JURIDIQUE_GENERATED", "Rapport juridique genere"),
            Map.entry("IDENTITY_EXTRACTED", "Piece d'identite extraite (OCR)")
    );

    /**
     * Libelle FR d'un code action. Si inconnu, humanise le code
     * ({@code FOO_BAR_DONE} -> {@code "Foo bar done"}).
     */
    public static String label(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        String mapped = LABELS.get(action);
        if (mapped != null) {
            return mapped;
        }
        String lower = action.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
