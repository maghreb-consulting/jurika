package ma.jurika.dataroom.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DataroomDtos {

    private DataroomDtos() {}

    /**
     * @param editeManuellementAt lot 3 — date de la derniere edition dans
     *        l'editeur bureautique, ou {@code null} si le document est tel que
     *        genere. L'interface s'en sert pour NOMMER ce qu'une regeneration
     *        ferait perdre : un avertissement generique se clique sans se lire.
     */
    public record DocumentSummary(UUID id, UUID dossierId, UUID ticketId,
                                   String documentType, String title,
                                   short version, boolean current,
                                   String filename, String contentType, long sizeBytes,
                                   Instant createdAt, Instant replacedAt,
                                   String motif, Instant editeManuellementAt) {
        /** Surcharge de compatibilite : documents non issus d'une edition manuelle. */
        public DocumentSummary(UUID id, UUID dossierId, UUID ticketId,
                                String documentType, String title,
                                short version, boolean current,
                                String filename, String contentType, long sizeBytes,
                                Instant createdAt, Instant replacedAt, String motif) {
            this(id, dossierId, ticketId, documentType, title, version, current,
                    filename, contentType, sizeBytes, createdAt, replacedAt, motif, null);
        }
    }

    /**
     * Un groupe de documents dans le dossier d'un ticket : actes generes,
     * justificatifs administratifs, pieces client.
     *
     * @param code identifiant stable pour l'interface (ACTES_GENERES, ...)
     */
    public record GroupeDocuments(String code, String libelle, List<DocumentSummary> documents) {}

    /**
     * Le dossier juridique s'organise par TICKET : chaque operation forme un
     * dossier, dont le libelle est CALCULE (jamais saisi).
     *
     * <p>{@code ticketId} vaut null pour le regroupement « Hors ticket », qui
     * recueille les documents anterieurs a ce lot ou deposes hors workflow. Ils
     * doivent rester accessibles : un document mal classe se retrouve, un
     * document invisible est perdu.
     */
    public record DossierTicket(UUID ticketId, String libelle, String reference, String type,
                                 String statut, Instant ouvertLe,
                                 List<GroupeDocuments> groupes, int totalDocuments) {}

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
                                        List<TicketHistoryEntry> historiqueOperations,
                                        /* Lot 1 (2026-09-04) — le dossier juridique organise par
                                           ticket. Contient TOUS les documents du dossier, versions
                                           historiques comprises : rien ne doit disparaitre. */
                                        List<DossierTicket> dossiersParTicket) {}

    public record UploadJuridiqueRequest(
            @NotBlank @Size(max = 40) String documentType,
            @NotBlank @Size(max = 200) String title,
            UUID ticketId) {}

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
                                Instant lastAccessedAt) {}

    public record UpdatePermissionsRequest(boolean permDownload, boolean permPrint, boolean permDepot) {}

    /**
     * Vue allegee des permissions exposee AU CLIENT (lecture seule). Ne contient
     * que ce dont le client a besoin pour afficher l'etat reel de ses droits et
     * activer/desactiver ses boutons -- jamais le token de lien, l'email
     * ni les compteurs internes (reserves a {@link SettingsView}).
     */
    public record ClientPermissionsView(UUID dossierId, String accessStatus,
                                        boolean permDownload, boolean permPrint,
                                        boolean permDepot) {}

    public record ToggleSuspensionRequest(boolean suspended) {}

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
