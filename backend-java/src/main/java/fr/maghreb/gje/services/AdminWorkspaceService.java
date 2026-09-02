package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.dto.admin.CreateWorkspaceRequest;
import fr.maghreb.gje.dto.admin.SubscriptionDTO;
import fr.maghreb.gje.dto.admin.WorkspaceDetailDTO;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.models.FormatPlan;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminWorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final HistoriqueRepository historiqueRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Map<String, Object> createWorkspace(
            CreateWorkspaceRequest request) {

        // Generate unique code
        String code = generateCode(request.getName());

        // Create workspace
        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .codeWorkspace(code)
                .isActive(true)
                .build();
        workspace = workspaceRepository.save(workspace);

        // Set tenant context
        TenantContext.setTenantId(workspace.getId().toString());

        // Create subscription
        FormatPlan forfait = FormatPlan.valueOf(
            request.getForfait().toUpperCase());
            
        Integer maxUsers = request.getMaxUsers() != null ? 
            request.getMaxUsers() : forfait.getMaxUsers();
            
        java.math.BigDecimal storageLimit = request.getStorageLimitGb() != null ? 
            request.getStorageLimitGb() : forfait.getStorageLimitGb();
            
        Subscription subscription = Subscription.builder()
                .workspaceId(workspace.getId())
                .type(forfait)
                .maxUsers(maxUsers)
                .storageLimitGb(storageLimit)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(
                    request.getDureeMois()))
                .build();
        subscriptionRepository.save(subscription);

        // Generate temp password
        String tempPassword = generateTempPassword();

        // Create superviseur
        User superviseur = User.builder()
                .workspaceId(workspace.getId())
                .email(request.getEmailSuperviseur())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .fullName(request.getFullNameSuperviseur())
                .role(User.Role.SUPERVISEUR)
                .isActive(true)
                .build();
        userRepository.save(superviseur);

        // Log creation
        historiqueRepository.save(Historique.builder()
                .workspaceId(workspace.getId())
                .userId(superviseur.getId())
                .action("WORKSPACE_CREATED")
                .nouvelleValeur(workspace.getName())
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("workspace_id", workspace.getId().toString());
        result.put("code", code);
        result.put("superviseur_email",
            request.getEmailSuperviseur());
        result.put("temp_password", tempPassword);
        result.put("forfait", forfait.name());
        result.put("message",
            "Workspace créé avec succès");
        return result;
    }

    public Map<String, Object> getStats() {
        List<Workspace> workspaces =
            workspaceRepository.findAll();
        List<Map<String, Object>> stats = new ArrayList<>();

        for (Workspace ws : workspaces) {
            TenantContext.setTenantId(ws.getId().toString());
            Map<String, Object> wsStat = new HashMap<>();
            wsStat.put("id", ws.getId().toString());
            wsStat.put("code", ws.getCodeWorkspace());
            wsStat.put("name", ws.getName());
            wsStat.put("is_active", ws.getIsActive());
            wsStat.put("nb_users",
                userRepository.findAllByWorkspaceId(
                    ws.getId()).size());
            stats.add(wsStat);
            TenantContext.clear();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total_workspaces", workspaces.size());
        result.put("active_workspaces",
            workspaces.stream()
                .filter(Workspace::getIsActive).count());
        result.put("workspaces", stats);
        return result;
    }

    @Transactional(readOnly = true)
    public WorkspaceDetailDTO getWorkspaceDetail(UUID id) {
        Workspace ws = workspaceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Workspace introuvable"));
            
        Subscription sub = subscriptionRepository.findByWorkspaceId(id)
            .orElseThrow(() -> new RuntimeException("Abonnement introuvable"));

        SubscriptionDTO subDTO = SubscriptionDTO.builder()
            .type(sub.getType().name())
            .maxUsers(sub.getMaxUsers())
            .storageGB(sub.getStorageLimitGb().intValue())
            .maxDossiers(sub.getType().getMaxDossiers())
            .startDate(sub.getStartDate())
            .endDate(sub.getEndDate())
            .build();

        return WorkspaceDetailDTO.builder()
            .id(ws.getId())
            .name(ws.getName())
            .code(ws.getCodeWorkspace())
            .isActive(ws.getIsActive())
            .subscription(subDTO)
            .build();
    }

    @Transactional(readOnly = true)
    public UUID getSecondEmployeeId() {
        return userRepository.findByEmail("employe2@test.com")
            .map(User::getId)
            .orElseThrow(() -> new RuntimeException("Second employé de test non trouvé"));
    }

    @Transactional
    public Map<String, Object> toggleWorkspace(
            UUID id, boolean active, String raison) {
        Workspace workspace = workspaceRepository
            .findById(id)
            .orElseThrow(() ->
                new RuntimeException("Workspace introuvable"));
        workspace.setIsActive(active);
        workspaceRepository.save(workspace);

        Map<String, Object> result = new HashMap<>();
        result.put("workspace_id", id.toString());
        result.put("is_active", active);
        result.put("message", active ?
            "Workspace réactivé" : "Workspace suspendu");
        result.put("raison", raison);
        return result;
    }

    private String generateCode(String name) {
        String prefix = name.substring(0,
            Math.min(2, name.length())).toUpperCase();
        String year = String.valueOf(
            LocalDate.now().getYear());
        String code = prefix + "-" + year;
        int counter = 1;
        while (workspaceRepository.existsByCodeWorkspace(code)) {
            code = prefix + "-" + year + "-" + counter++;
        }
        return code;
    }

    private String generateTempPassword() {
        return "GJE-" + UUID.randomUUID()
            .toString().substring(0, 8).toUpperCase();
    }
}
