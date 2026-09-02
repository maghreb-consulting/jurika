package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.dto.admin.QuotaStatusDTO;
import fr.maghreb.gje.dto.admin.WorkspaceQuotaStatusDTO;
import fr.maghreb.gje.events.WorkspaceNear90PercentEvent;
import fr.maghreb.gje.exceptions.QuotaExceededException;
import fr.maghreb.gje.models.FormatPlan;
import fr.maghreb.gje.models.Subscription;
import fr.maghreb.gje.models.Workspace;
import fr.maghreb.gje.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final SubscriptionRepository subscriptionRepository;
    private final DossierRepository dossierRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    private Subscription getActiveSubscription(UUID workspaceId) {
        return subscriptionRepository.findByWorkspaceId(workspaceId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Aucun abonnement actif trouvé pour ce workspace"));
    }

    @Transactional(readOnly = true)
    public void checkUserQuota(UUID workspaceId) {
        Subscription subscription = getActiveSubscription(workspaceId);
        
        if (subscription.getMaxUsers() != -1) { // -1 means unlimited
            long currentUserCount = userRepository.countByWorkspaceId(workspaceId);
            if (currentUserCount >= subscription.getMaxUsers()) {
                throw new QuotaExceededException("user", currentUserCount, subscription.getMaxUsers());
            }
        }
    }

    @Transactional(readOnly = true)
    public void checkDossierQuota(UUID workspaceId) {
        Subscription subscription = getActiveSubscription(workspaceId);
        FormatPlan plan = subscription.getType();

        if (plan.getMaxDossiers() != null && plan.getMaxDossiers() != -1) {
            long currentDossierCount = dossierRepository.countByWorkspaceId(workspaceId);
            if (currentDossierCount >= plan.getMaxDossiers()) {
                throw new QuotaExceededException("dossier", currentDossierCount, plan.getMaxDossiers());
            }
        }
    }

    @Transactional(readOnly = true)
    public long getCurrentStorageUsage(UUID workspaceId) {
        // Compute sum of file_size_bytes
        TenantContext.setTenantId(workspaceId.toString());
        // Run native query securely since RLS requires current_tenant_id config
        Number sum = (Number) entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(file_size_bytes), 0) FROM documents WHERE workspace_id = :wsId"
        ).setParameter("wsId", workspaceId).getSingleResult();
        
        return sum != null ? sum.longValue() : 0L;
    }

    @Transactional
    public void checkStorageQuota(UUID workspaceId, long additionalBytes) {
        Subscription subscription = getActiveSubscription(workspaceId);
        
        long currentUsageBytes = getCurrentStorageUsage(workspaceId);
        long projectedUsageBytes = currentUsageBytes + additionalBytes;
        
        // Convert GB limit to bytes
        BigDecimal limitGb = subscription.getStorageLimitGb();
        long limitBytes = limitGb.multiply(new BigDecimal(1024L * 1024L * 1024L)).longValue();
        
        if (projectedUsageBytes > limitBytes) {
            throw new QuotaExceededException("storage", projectedUsageBytes, limitBytes);
        }

        double percentage = (double) projectedUsageBytes / limitBytes;
        if (percentage >= 0.90) {
            eventPublisher.publishEvent(new WorkspaceNear90PercentEvent(
                this, workspaceId, projectedUsageBytes, limitBytes, percentage));
        }
    }

    @Transactional(readOnly = true)
    public WorkspaceQuotaStatusDTO getDetailedQuotaStatus(UUID workspaceId) {
        Subscription subscription = getActiveSubscription(workspaceId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace introuvable"));
                
        // 1. Storage Info
        long usedBytes = getCurrentStorageUsage(workspaceId);
        BigDecimal limitGb = subscription.getStorageLimitGb();
        long limitBytes = limitGb.multiply(new BigDecimal(1024L * 1024L * 1024L)).longValue();
        double storagePct = limitBytes == 0 ? 0 : (double) usedBytes / limitBytes;

        // 2. Dossiers Info
        long currentDossiers = dossierRepository.countByWorkspaceId(workspaceId);
        Integer limitDossiers = subscription.getType().getMaxDossiers();
        double dossierPct = (limitDossiers == null || limitDossiers == -1) ? 0 : 
                            (double) currentDossiers / limitDossiers;

        // 3. Users Info
        long currentUsers = userRepository.countByWorkspaceId(workspaceId);
        Integer limitUsers = subscription.getMaxUsers();
        double userPct = (limitUsers == null || limitUsers == -1) ? 0 : 
                            (double) currentUsers / limitUsers;

        return WorkspaceQuotaStatusDTO.builder()
                .workspaceId(workspaceId)
                .workspaceName(workspace.getName())
                .storage(WorkspaceQuotaStatusDTO.StorageQuotaInfo.builder()
                        .usedBytes(usedBytes)
                        .limitBytes(limitBytes)
                        .usedGb((double) usedBytes / (1024 * 1024 * 1024))
                        .limitGb(limitGb.doubleValue())
                        .percentage(storagePct * 100)
                        .build())
                .dossiers(WorkspaceQuotaStatusDTO.ResourceQuotaInfo.builder()
                        .used(currentDossiers)
                        .limit(limitDossiers != null ? limitDossiers.longValue() : -1L)
                        .percentage(dossierPct * 100)
                        .build())
                .users(WorkspaceQuotaStatusDTO.ResourceQuotaInfo.builder()
                        .used(currentUsers)
                        .limit(limitUsers != null ? limitUsers.longValue() : -1L)
                        .percentage(userPct * 100)
                        .build())
                .alerts(WorkspaceQuotaStatusDTO.QuotaAlerts.builder()
                        .near90(storagePct >= 0.90)
                        .exceeded(storagePct >= 1.0)
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public QuotaStatusDTO getQuotaStatus(UUID workspaceId) {
        Subscription subscription = getActiveSubscription(workspaceId);
        
        long currentUsageBytes = getCurrentStorageUsage(workspaceId);
        
        BigDecimal usedMg = new BigDecimal(currentUsageBytes).divide(new BigDecimal(1024 * 1024), 2, RoundingMode.HALF_UP);
        BigDecimal limitMg = subscription.getStorageLimitGb().multiply(new BigDecimal(1024));
        
        double percentage = limitMg.compareTo(BigDecimal.ZERO) == 0 ? 0 : 
                            usedMg.divide(limitMg, 4, RoundingMode.HALF_UP).doubleValue();

        return QuotaStatusDTO.builder()
                .used(usedMg)
                .limit(limitMg)
                .percentage(percentage * 100)
                .isNear90Percent(percentage >= 0.90)
                .build();
    }
}
