package ma.jurika.dataroom.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DataroomDtos {

    private DataroomDtos() {}

    public record DocumentSummary(UUID id, UUID dossierId, UUID ticketId,
                                   String documentType, String title,
                                   short version, boolean current,
                                   String filename, String contentType, long sizeBytes,
                                   Instant createdAt, Instant replacedAt,
                                   String motif) {}

    public record TicketHistoryEntry(UUID ticketId, String reference, String titre, String type,
                                      Instant clotureAt, String description,
                                      List<DocumentSummary> replacedDocuments,
                                      List<DocumentSummary> generatedDocuments) {}

    /**
     * Vue du Dossier Juridique. Enrichie 2026-07-14 (Fiche client) avec le bloc
     * d'identite complet + statut : permet au front de pre-remplir le formulaire
     * « Identifiants de la societe » et de calculer le preflight (champs manquants
     * pertinents selon le statut) sans endpoint supplementaire.
     */
    public record DossierJuridiqueView(UUID dossierId, String raisonSociale, String formeJuridique,
                                        String ice, String rcNumero, String rcTribunal,
                                        String identifiantFiscal, String taxeProfessionnelle,
                                        String cnss, String adresseSiege, String ville,
                                        java.math.BigDecimal capitalSocialMad,
                                        java.time.LocalDate dateConstitution, String statut,
                                        List<DocumentSummary> documentsEnVigueur,
                                        List<TicketHistoryEntry> historiqueOperations) {}

    public record ComptableDocumentSummary(UUID id, short annee, String categorie, String title,
                                            String filename, String contentType, long sizeBytes,
                                            Instant createdAt) {}

    public record YearCount(short annee, long total) {}

    public record CategoryCount(String categorie, long total) {}

    public record DossierComptableView(UUID dossierId, List<Short> annees, short anneeCourante,
                                        List<CategoryCount> totauxParCategorie) {}

    public record UploadJuridiqueRequest(
            @NotBlank @Size(max = 40) String documentType,
            @NotBlank @Size(max = 200) String title,
            UUID ticketId) {}

    public record UploadComptableRequest(
            @Min(2000) short annee,
            @NotBlank @Size(max = 20) String categorie,
            @NotBlank @Size(max = 200) String title) {}

    public record CreateDemandeRequest(
            @NotBlank @Size(max = 200) String sujet,
            // Description facultative (le client peut n'envoyer qu'un sujet).
            @Size(max = 4000) String description,
            UUID dossierId) {}

    public record UpdateDemandeStatusRequest(
            @NotBlank String statut,
            @Size(max = 4000) String noteInterne,
            UUID ticketId) {}

    // Lot AG -- « Requetes au client » (direction EMPLOYE_TO_CLIENT).
    /** Creation d'une requete de l'employe vers le client (statut initial OUVERTE). */
    public record CreateRequeteRequest(
            @NotBlank @Size(max = 200) String sujet,
            @Size(max = 4000) String description,
            UUID dossierId,
            @NotBlank String typeRequete) {}   // PIECE | INFO | SIGNATURE

    /** Reponse du CLIENT a une requete (OUVERTE|A_COMPLETER -> REPONDUE). */
    public record RepondreRequest(@Size(max = 4000) String noteClient) {}

    /** Demande de complement de l'employe (REPONDUE -> A_COMPLETER). */
    public record ComplementRequest(@NotBlank @Size(max = 4000) String note) {}

    public record DemandeSummary(UUID id, UUID dossierId, UUID soumisPar, String sujet,
                                  String description, String statut, UUID ticketId,
                                  UUID prisEnChargePar, String noteInterne,
                                  Instant traiteAt, Instant createdAt,
                                  String direction, String typeRequete,
                                  Instant reponduAt, Instant clotureAt, String noteClient) {}

    /**
     * Vue SUPERVISEUR enrichie (workspace-wide) d'une demande ou requete : le
     * nom du dataroom ({@code raisonSociale}) et l'employe responsable/concerne
     * ({@code responsableNom}) sont resolus par jointure (jamais d'UUID brut a
     * l'ecran). Lecture seule, reservee SUPERVISEUR / SUPER_ADMIN.
     */
    public record DemandeSupervisionRow(UUID id, UUID dossierId, String raisonSociale,
                                        UUID responsableId, String responsableNom,
                                        String sujet, String description, String statut,
                                        String direction, String typeRequete,
                                        Instant createdAt) {}

    public record SettingsView(UUID dossierId, String accessStatus, boolean permDownload,
                                boolean permPrint, boolean permDepot, UUID clientLinkToken, int accessCount,
                                Instant lastAccessedAt,
                                String accountantEmail, boolean notifyAccountantOnUpload) {}

    public record UpdatePermissionsRequest(boolean permDownload, boolean permPrint, boolean permDepot) {}

    /**
     * Vue allegee des permissions exposee AU CLIENT (lecture seule). Ne contient
     * que ce dont le client a besoin pour afficher l'etat reel de ses droits et
     * activer/desactiver ses boutons -- jamais le token de lien, l'email
     * comptable ni les compteurs internes (reserves a {@link SettingsView}).
     */
    public record ClientPermissionsView(UUID dossierId, String accessStatus,
                                        boolean permDownload, boolean permPrint,
                                        boolean permDepot) {}

    public record ToggleSuspensionRequest(boolean suspended) {}

    /** RG-DC27 : config notif comptable. */
    public record UpdateAccountantNotifRequest(String accountantEmail, boolean enabled) {}

    public record ClientLinkResponse(String url, UUID token) {}

    // ================================================================
    // Sprint 7 / TASK 1 -- Recherche FTS + filtres avances Juridique
    // ================================================================

    /**
     * RG-DR-FTS : input du use case recherche juridique.
     * - q : terme libre, FTS PostgreSQL via websearch_to_tsquery('french', ...). Null/blank = pas de FTS.
     * - types : filtre liste de document_type (multi-select). Null/empty = tous types.
     * - from/to : bornes createdAt (inclusives). Null = pas de borne.
     * - versionScope : CURRENT (defaut, is_current=true) / OLD (is_current=false) / ALL.
     * - limit/offset : pagination (defauts limit=20, offset=0).
     */
    public enum VersionScope { CURRENT, OLD, ALL }

    public record SearchJuridiqueInput(
            UUID dossierId,
            String q,
            List<String> types,
            Instant from,
            Instant to,
            VersionScope versionScope,
            int limit,
            int offset) {
        public SearchJuridiqueInput {
            if (versionScope == null) versionScope = VersionScope.CURRENT;
            if (limit <= 0) limit = 20;
            if (limit > 100) limit = 100;
            if (offset < 0) offset = 0;
        }
    }

    public record SearchJuridiqueOutput(List<DocumentSummary> items, long total) {}

    // ================================================================
    // Sprint 7 / TASK 4 -- Bulk actions + export PDF historique
    // ================================================================

    public record BulkDeleteRequest(
            @jakarta.validation.constraints.NotEmpty List<UUID> documentIds) {}

    public record BulkExportZipRequest(
            @jakarta.validation.constraints.NotEmpty List<UUID> documentIds,
            boolean includeOldVersions) {}

    // ================================================================
    // Sprint 7 / TASK 5 -- Logs acces client (admin view)
    // ================================================================

    public record AccessLogEntry(
            UUID id,
            UUID dossierId,
            UUID userId,
            UUID documentId,
            String action,
            String ipAddress,
            String userAgent,
            Instant createdAt) {}

    public record AccessLogPage(List<AccessLogEntry> items, long total) {}

    // ================================================================
    // Sprint 7 / TASK 6.1 -- Dossier Fiscal placeholder (Sprint 8 base)
    // ================================================================

    public record ExerciceFiscalSummary(
            UUID id,
            short annee,
            String dateDebut, // ISO LocalDate
            String dateFin,
            String statut,    // OUVERT / CLOTURE / VERROUILLE
            Instant dateOuverture,
            Instant dateCloture) {}

    /**
     * Vue placeholder du Dossier Fiscal (Sprint 7). Sprint 8 ajoutera la
     * pagination par exercice + 7 categories CGI + sous-classifications.
     *
     * @param exercices liste des exercices detectes (V12 + backfill annee en cours)
     * @param categoriesCgi liste annoncee des futures categories (constante)
     * @param message message UI explicite Sprint 8
     */
    public record DossierFiscalView(
            UUID dossierId,
            UUID exerciceCourant,
            List<ExerciceFiscalSummary> exercices,
            List<String> categoriesCgi,
            String message) {}

    // ================================================================
    // Sprint 7 / TASK 6.2 -- Brief dossier (extrait de DataroomController
    // pour reutilisation cross-controllers apres le refactor split)
    // ================================================================

    public record DossierBrief(UUID id, String raisonSociale, String formeJuridique,
                                String ice, String ville, String statut,
                                /* Fix 2026-06-08 — Etat d'acces du dataroom :
                                   ACTIVE | SUSPENDED. Sert au front a classer
                                   les dossiers entre "Actifs" et "Suspendus". */
                                String accessStatus,
                                /* Lot W2 (2026-07-04) — date d'effet de la
                                   dissolution (ISO AAAA-MM-JJ) ou null. Permet
                                   au front d'afficher le badge « delai 16 j »
                                   (RG-LI03) et de pre-remplir la LIQUIDATION. */
                                String dateDissolution,
                                /* Lot DIVERS §C (2026-08-13) — MAROCAINE (defaut)
                                   | ETRANGERE. Une societe mere etrangere est un
                                   dossier a part entiere (donc porteuse de sa
                                   Data Room) cree par le workflow SUCCURSALE_ETR.
                                   Sert au front a proposer la SELECTION d'une mere
                                   deja enregistree au lieu de la re-saisir. */
                                String origine,
                                /* Pays du siege de la mere etrangere (null sinon). */
                                String pays,
                                /* Forme juridique REELLE du pays d'origine (Ltd, GmbH,
                                   BV, Inc...) : une societe etrangere n'est PAS une SARL.
                                   `formeJuridique` vaut alors 'ETRANGERE' et c'est cette
                                   valeur-ci qu'il faut afficher. Null pour un dossier
                                   marocain. */
                                String formeJuridiqueOrigine) {}

    // ================================================================
    // Sprint 8 -- Dossier Fiscal complet (7 categories CGI)
    // ================================================================

    /** RG-DF01..28 : resume document fiscal. */
    public record FiscalDocumentSummary(
            UUID id,
            UUID dossierId,
            UUID exerciceFiscalId,
            String categorie,
            String sousClassification,
            String title,
            String commentaire,
            String filename,
            String contentType,
            long sizeBytes,
            String tifMetadata,
            String numeroDeclaration,
            String periodeDeclaree,
            UUID comptableDocSource,
            String comptableDocSourceTitle,
            Instant createdAt,
            Instant deletedAt) {}

    public record FiscalCategoryCount(String categorie, long total) {}

    /** Vue du dossier fiscal pour un exercice donne (grille 7 cats + compteurs). */
    public record DossierFiscalDetailedView(
            UUID dossierId,
            UUID exerciceCourant,
            List<ExerciceFiscalSummary> exercices,
            List<FiscalCategoryCount> compteurs,
            List<String> categoriesCgi) {}

    public record UploadFiscalRequest(
            @NotBlank @Size(max = 20) String categorie,
            @NotBlank @Size(max = 40) String sousClassification,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String commentaire,
            @Size(max = 20) String tifMetadata,
            @Size(max = 60) String numeroDeclaration,
            @Size(max = 20) String periodeDeclaree,
            UUID comptableDocSource) {}

    public record SubClassificationDef(String categorie, List<String> values) {}

    // ----- Transitions exercice fiscal -----

    public record OpenExerciceRequest(
            @Min(2000) short annee,
            String dateDebut, // ISO LocalDate optional
            String dateFin,
            boolean regimeTvaMensuel,
            /* RG-DF03 (2026-06-24) : si true (finalisation import/création), l'ancre
               comptable de l'année est créée à la volée avant d'ouvrir le fiscal
               (jamais bloquant). Si false (ouverture manuelle onglet Fiscal), l'absence
               d'année comptable est rejetée — le fiscal doit être conforme au comptable. */
            boolean autoCreateComptable) {}

    public record UnlockExerciceRequest(
            @NotBlank @Size(min = 20, max = 4000) String motif) {}

    // ----- Echeances -----

    public record EcheanceSummary(
            UUID id,
            UUID exerciceFiscalId,
            String typeEcheance,
            String dateEcheance,
            String dateAlerte,
            String statut,
            UUID documentId,
            Instant sentAt,
            Instant traiteAt) {}

    public record MarquerEcheanceTraitee(UUID documentId, String note) {}

    // ================================================================
    // Lot V -- Espace « Depots » client (depot libre + consultation employe)
    // ================================================================

    /** Resume d'un depot libre (espace « Depots », sans categorie). */
    public record DepotSummary(
            UUID id,
            String title,
            String filename,
            String contentType,
            long sizeBytes,
            UUID uploadedBy,
            Instant createdAt) {}
}
