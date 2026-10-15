package ma.jurika.dashboard.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sprint 10 -- DTOs miroir backend pour les 4 scopes de dashboard. */
public final class DashboardDtos {

    private DashboardDtos() {}

    // ----- Communs -----
    public record DailyCount(LocalDate day, long count) {}
    public record CategoryCount(String label, long count) {}
    /** Serie mensuelle simple (YYYY-MM -> count). */
    public record MonthlyCount(String month, long count) {}
    /** Serie mensuelle double (tickets crees vs clotures par mois). */
    public record MonthlyFlow(String month, long crees, long clotures) {}

    // ----- SUPER_ADMIN -----
    public record WorkspaceUsage(UUID workspaceId, String name, long eventsLast7d) {}
    public record AuditEventSummary(UUID id, String action, UUID userId,
                                      UUID workspaceId, String entityType,
                                      Instant createdAt) {}
    public record StorageSummary(long totalBytes, long fileCount) {}

    public record SuperAdminDashboardDto(
            long activeWorkspaces30d,
            long totalWorkspaces,
            long totalUsers,
            List<DailyCount> signups30d,
            List<WorkspaceUsage> topWorkspacesByUsage,
            List<AuditEventSummary> criticalAuditEvents24h,
            Map<String, String> servicesHealth,
            StorageSummary storage,
            List<CategoryCount> workspacesParForfait,
            List<CategoryCount> workspacesParStatut,
            List<MonthlyCount> signupsMensuels,
            Instant generatedAt) {}

    // ----- SUPERVISEUR -----
    public record EmployeeLoad(UUID userId, String email, long ticketsOuverts, long echeancesAssignees) {}
    public record EcheanceBucket(String label, List<EcheanceItem> items) {}
    /**
     * Une echeance legale du parcours : le delai du guide attache a une demarche
     * restant a accomplir. Remplace les anciennes alertes fiscales.
     *
     * @param severite DEPASSE | CRITIQUE (J-3) | APPROCHE. L ancien champ
     *                 `statut` valait toujours « PLANIFIEE » : il ne portait
     *                 aucune information.
     */
    public record EcheanceItem(UUID id, UUID dossierId, String libelle, LocalDate dateEcheance,
                                String severite) {}
    public record DossierRisk(UUID dossierId, String raisonSociale, String motif) {}

    public record SuperviseurDashboardDto(
            long ticketsOuverts,
            long ticketsClos30d,
            double tempsMoyenClotureHeures,
            List<DailyCount> evolutionTickets30j,
            List<CategoryCount> topWorkflows,
            List<EmployeeLoad> chargeParEmploye,
            List<EcheanceBucket> echeancesJ30J15J3,
            List<DossierRisk> dossiersARisque,
            List<CategoryCount> ticketsParStatut,
            List<MonthlyFlow> evolutionMensuelle,
            List<CategoryCount> demandesParStatut,
            Instant generatedAt,
            /*
             * Lot L7 (E5), CDC 4.5 : tableaux de bord de productivite (forfaits Max et Sur Devis).
             * Faux : le temps moyen de cloture, l'evolution des tickets, les parcours les plus
             * frequents et l'evolution mensuelle ne sont PAS rendus (vides, et non "zero activite") ;
             * l'ecran l'explique. La supervision (charge par employe, echeances, dossiers a risque,
             * RG-SUP-01) reste rendue a tous les forfaits.
             */
            boolean productiviteIncluse) {

        /** Copie marquee "productivite incluse" (une entree de cache anterieure au lot n'a pas la marque). */
        public SuperviseurDashboardDto avecProductivite() {
            return productiviteIncluse ? this : new SuperviseurDashboardDto(ticketsOuverts, ticketsClos30d,
                    tempsMoyenClotureHeures, evolutionTickets30j, topWorkflows, chargeParEmploye, echeancesJ30J15J3,
                    dossiersARisque, ticketsParStatut, evolutionMensuelle, demandesParStatut, generatedAt, true);
        }

        /** Copie sans les indicateurs de productivite (forfait qui ne les inclut pas). */
        public SuperviseurDashboardDto sansProductivite() {
            return new SuperviseurDashboardDto(ticketsOuverts, ticketsClos30d, 0d, List.of(), List.of(),
                    chargeParEmploye, echeancesJ30J15J3, dossiersARisque, ticketsParStatut, List.of(),
                    demandesParStatut, generatedAt, false);
        }
    }

    // ----- EMPLOYE -----
    public record TicketLite(UUID id, String reference, String titre, String statut,
                              String priorite, Instant createdAt) {}
    public record DayLoad(LocalDate day, long charge) {}
    public record OperationLite(String type, String label, Instant at) {}

    public record EmployeDashboardDto(
            long mesTicketsOuverts,
            List<TicketLite> derniersTickets,
            List<DayLoad> maChargeSemaine,
            List<EcheanceItem> mesEcheancesAssignees,
            List<OperationLite> dernieresOperationsDataroom,
            Instant generatedAt) {}

    // ----- CLIENT -----
    public record DossierClientLite(UUID id, String raisonSociale, String statut,
                                      Instant lastEventAt, String lastEventLabel) {}
    public record DocumentLite(UUID id, String title, String filename, Instant createdAt) {}

    public record ClientDashboardDto(
            List<DossierClientLite> mesDossiers,
            List<TicketLite> mesTicketsEnCours,
            List<DocumentLite> mesDocumentsRecents,
            List<EcheanceItem> mesEcheancesAVenir,
            Instant generatedAt) {}

    // ----- Lock anti-stampede (interne) -----
    public record CacheLockRelease(String key, boolean released) {}
}
