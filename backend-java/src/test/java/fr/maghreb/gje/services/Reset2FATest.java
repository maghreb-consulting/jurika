package fr.maghreb.gje.services;

import fr.maghreb.gje.models.Historique;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.HistoriqueRepository;
import fr.maghreb.gje.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class Reset2FATest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HistoriqueRepository historiqueRepository;

    @Mock
    private TokenSessionService tokenSessionService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AuthService authService;

    private User superviseur;
    private User employe;
    private User superAdmin;
    private User employeAutreWorkspace;
    
    private final UUID workspaceA = UUID.randomUUID();
    private final UUID workspaceB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        superviseur = new User();
        superviseur.setId(UUID.randomUUID());
        superviseur.setWorkspaceId(workspaceA);
        superviseur.setRole(User.Role.SUPERVISEUR);

        employe = new User();
        employe.setId(UUID.randomUUID());
        employe.setWorkspaceId(workspaceA);
        employe.setRole(User.Role.EMPLOYE);
        employe.setTotpEnabled(true);
        employe.setTotpSecret("SECRET");

        employeAutreWorkspace = new User();
        employeAutreWorkspace.setId(UUID.randomUUID());
        employeAutreWorkspace.setWorkspaceId(workspaceB);
        employeAutreWorkspace.setRole(User.Role.EMPLOYE);

        superAdmin = new User();
        superAdmin.setId(UUID.randomUUID());
        superAdmin.setRole(User.Role.SUPER_ADMIN);

        ReflectionTestUtils.setField(authService, "entityManager", entityManager);
    }

    @Test
    void testSuperviseurResetEmploye_Success() {
        Mockito.when(userRepository.findById(employe.getId())).thenReturn(Optional.of(employe));
        
        Query mockQuery = Mockito.mock(Query.class);
        Mockito.when(entityManager.createNativeQuery(Mockito.anyString())).thenReturn(mockQuery);

        var response = authService.reset2FA(employe.getId(), superviseur, "127.0.0.1");

        assertTrue((Boolean) response.get("success"));
        assertNull(employe.getTotpSecret());
        assertFalse(employe.getTotpEnabled());
        
        Mockito.verify(tokenSessionService).invalidateSession(employe.getId().toString());
        Mockito.verify(historiqueRepository).save(any(Historique.class));
    }

    @Test
    void testSuperviseurResetEmployeAutreWorkspace_Fails403() {
        Mockito.when(userRepository.findById(employeAutreWorkspace.getId())).thenReturn(Optional.of(employeAutreWorkspace));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> 
            authService.reset2FA(employeAutreWorkspace.getId(), superviseur, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("FORBIDDEN:Vous ne pouvez réinitialiser que les employés de votre workspace"));
    }

    @Test
    void testSelfReset_Fails403() {
        Mockito.when(userRepository.findById(superviseur.getId())).thenReturn(Optional.of(superviseur));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> 
            authService.reset2FA(superviseur.getId(), superviseur, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("FORBIDDEN:Vous ne pouvez pas réinitialiser votre propre 2FA"));
    }
}
