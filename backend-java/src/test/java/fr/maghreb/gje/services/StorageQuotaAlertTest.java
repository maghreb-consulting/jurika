package fr.maghreb.gje.services;

import fr.maghreb.gje.events.WorkspaceNear90PercentEvent;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StorageQuotaAlertTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private HistoriqueRepository historiqueRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StorageAlertService storageAlertService;

    private UUID workspaceId;
    private Workspace workspace;
    private User supervisor;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        workspace = Workspace.builder().id(workspaceId).name("Test WS").build();
        
        supervisor = User.builder().id(UUID.randomUUID()).workspaceId(workspaceId).role(User.Role.SUPERVISEUR).build();
        superAdmin = User.builder().id(UUID.randomUUID()).role(User.Role.SUPER_ADMIN).build();
    }

    @Test
    void testHandleAlert_Success() {
        // Given
        WorkspaceNear90PercentEvent event = new WorkspaceNear90PercentEvent(this, workspaceId, 95L * 1024 * 1024 * 1024, 100L * 1024 * 1024 * 1024, 0.95);
        
        when(notificationRepository.existsByWorkspaceIdAndTypeAndCreatedAtAfter(eq(workspaceId), anyString(), any())).thenReturn(false);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(userRepository.findByWorkspaceIdAndRole(workspaceId, User.Role.SUPERVISEUR)).thenReturn(List.of(supervisor));
        when(userRepository.findByRole(User.Role.SUPER_ADMIN)).thenReturn(List.of(superAdmin));

        // When
        storageAlertService.handleStorageAlert(event);

        // Then
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(historiqueRepository).save(any(Historique.class));
    }

    @Test
    void testHandleAlert_AntiSpam() {
        // Given
        WorkspaceNear90PercentEvent event = new WorkspaceNear90PercentEvent(this, workspaceId, 95L * 1024 * 1024 * 1024, 100L * 1024 * 1024 * 1024, 0.95);
        
        when(notificationRepository.existsByWorkspaceIdAndTypeAndCreatedAtAfter(eq(workspaceId), anyString(), any())).thenReturn(true);

        // When
        storageAlertService.handleStorageAlert(event);

        // Then
        verify(notificationRepository, never()).save(any());
        verify(historiqueRepository, never()).save(any());
    }
}
