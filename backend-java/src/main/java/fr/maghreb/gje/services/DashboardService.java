package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.dashboard.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final DossierRepository dossierRepository;
    private final TicketRepository ticketRepository;
    private final QuotaService quotaService;

    public DashboardKpiDTO getGlobalKpis(UUID workspaceId) {
        log.info("Récupération des KPIs pour le workspace {}", workspaceId);
        
        // TODO: Implémenter les comptes réels via repositories
        return DashboardKpiDTO.builder()
                .totalDossiers(120L)
                .ticketsEnRevision(15L)
                .ticketsBloques48h(3L)
                .storageUsedPercentage(65.0)
                .storageLimitGb(10.0)
                .storageUsedGb(6.5)
                .build();
    }

    public List<EmployeePerformanceDTO> getEmployeePerformances(UUID workspaceId) {
        log.info("Récupération des performances employés pour le workspace {}", workspaceId);
        
        // TODO: Agrégation par utilisateur
        return Collections.singletonList(
            EmployeePerformanceDTO.builder()
                .fullName("Jean Dupont")
                .dossiersTraites(45)
                .ticketsTermines(150)
                .ticketsBloques(2)
                .respectDelaisPercentage(98.5)
                .build()
        );
    }

    public List<MonthlyActivityDTO> getMonthlyActivity(UUID workspaceId) {
        log.info("Récupération de l'activité mensuelle pour le workspace {}", workspaceId);
        
        // TODO: GroupBy month sur les 12 derniers mois
        return Arrays.asList(
            MonthlyActivityDTO.builder().month("2024-01").dossiersCrees(10).ticketsCloturés(30).build(),
            MonthlyActivityDTO.builder().month("2024-02").dossiersCrees(14).ticketsCloturés(42).build()
        );
    }

    public List<DashboardAlerteDTO> getAlertes(UUID workspaceId) {
        log.info("Récupération des alertes critiques pour le workspace {}", workspaceId);
        
        // TODO: Filtrer dossiers en retard et tickets bloqués
        return Collections.singletonList(
            DashboardAlerteDTO.builder()
                .dossierId(UUID.randomUUID())
                .denomination("SARL EXEMPLE")
                .alerteType("EN_RETARD")
                .message("Date d'échéance dépassée de 5 jours")
                .build()
        );
    }
}
