package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.models.Historique;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.HistoriqueRepository;
import fr.maghreb.gje.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final HistoriqueRepository historiqueRepository;
    private final QuotaService quotaService;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public User createUser(User.Role role, String email, String fullName, UUID workspaceId, UUID superviseurId) {
        TenantContext.setTenantId(workspaceId.toString());
        entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + workspaceId.toString() + "'")
                .executeUpdate();

        // Verify user quota before insertion
        quotaService.checkUserQuota(workspaceId);

        // Generation password temp (placeholder for real auth flow)
        String tempPassword = "TEMP-" + UUID.randomUUID().toString().substring(0, 8);

        User newUser = User.builder()
                .workspaceId(workspaceId)
                .email(email)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .fullName(fullName)
                .role(role)
                .isActive(true)
                .build();

        newUser = userRepository.save(newUser);

        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .userId(superviseurId)
                .action("USER_CREATED")
                .nouvelleValeur(email + " (" + role.name() + ")")
                .build());

        return newUser;
    }
}
