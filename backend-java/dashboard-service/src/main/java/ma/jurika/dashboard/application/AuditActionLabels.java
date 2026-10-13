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
            Map.entry("TICKET_CREATED", "Ticket cr\u00e9\u00e9"),
            Map.entry("TICKET_TRANSITIONED", "Statut du ticket modifi\u00e9"),
            Map.entry("TICKET_UPDATED", "Ticket modifi\u00e9"),
            Map.entry("TICKET_ASSIGNED", "Ticket r\u00e9assign\u00e9"),
            // Dossier
            Map.entry("DOSSIER_TRANSFERE", "Dossier transf\u00e9r\u00e9"),
            Map.entry("DATAROOM_DELETED", "Data Room supprim\u00e9e"),
            Map.entry("DATAROOM_SUSPENSION_TOGGLED", "Acc\u00e8s \u00e0 la Data Room suspendu ou r\u00e9tabli"),
            Map.entry("PERMISSIONS_CHANGED", "Droits du client modifi\u00e9s"),
            // Documents juridiques
            Map.entry("DOCUMENT_UPLOADED", "Document juridique d\u00e9pos\u00e9"),
            Map.entry("DOCUMENT_PREVIEWED", "Document consult\u00e9"),
            Map.entry("DOCUMENT_DOWNLOADED", "Document t\u00e9l\u00e9charg\u00e9"),
            Map.entry("DOCUMENT_DELETED", "Document supprim\u00e9"),
            Map.entry("DOCUMENTS_DELETED_BULK", "Documents supprim\u00e9s (en lot)"),
            Map.entry("DOCUMENT_VERSION_REPLACED", "Version de document remplac\u00e9e"),
            Map.entry("DOCUMENT_VERSION_RESTORED", "Version de document restaur\u00e9e"),
            // Comptable / fiscal
            Map.entry("COMPTABLE_UPLOADED", "Document comptable d\u00e9pos\u00e9"),
            Map.entry("FISCAL_UPLOADED", "Document fiscal d\u00e9pos\u00e9"),
            Map.entry("FISCAL_DELETED", "Document fiscal supprim\u00e9"),
            Map.entry("FISCAL_EXPORTED_ZIP", "Export fiscal (ZIP)"),
            Map.entry("EXERCICE_OPENED", "Exercice fiscal ouvert"),
            Map.entry("EXERCICE_CLOTURED", "Exercice fiscal cl\u00f4tur\u00e9"),
            Map.entry("EXERCICE_LOCKED", "Exercice fiscal verrouill\u00e9"),
            Map.entry("EXERCICE_UNLOCKED", "Exercice fiscal d\u00e9verrouill\u00e9"),
            Map.entry("ECHEANCE_MARQUEE_TRAITEE", "\u00c9ch\u00e9ance marqu\u00e9e comme trait\u00e9e"),
            // Workflow
            Map.entry("WORKFLOW_STARTED", "Parcours d\u00e9marr\u00e9"),
            Map.entry("WORKFLOW_STEP_EXECUTED", "\u00c9tape du parcours ex\u00e9cut\u00e9e"),
            // IA
            Map.entry("AI_DOCUMENT_GENERATED", "Document g\u00e9n\u00e9r\u00e9"),
            Map.entry("REPORT_JURIDIQUE_GENERATED", "Rapport juridique g\u00e9n\u00e9r\u00e9"),
            Map.entry("IDENTITY_EXTRACTED", "Pi\u00e8ce d\u2019identit\u00e9 lue automatiquement")
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
