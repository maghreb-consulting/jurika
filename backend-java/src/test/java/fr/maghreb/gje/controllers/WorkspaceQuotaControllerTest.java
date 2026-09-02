package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.admin.WorkspaceQuotaStatusDTO;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.services.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkspaceQuotaControllerTest {

    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private WorkspaceController workspaceController;

    private User supervisor;
    private User superAdmin;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        supervisor = User.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .role(User.Role.SUPERVISEUR)
                .build();
        
        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .role(User.Role.SUPER_ADMIN)
                .build();
    }

    @Test
    void testGetQuotaStatus_Supervisor() {
        // Given
        WorkspaceQuotaStatusDTO mockDto = WorkspaceQuotaStatusDTO.builder()
                .workspaceId(workspaceId)
                .workspaceName("Test WS")
                .build();
        when(quotaService.getDetailedQuotaStatus(workspaceId)).thenReturn(mockDto);

        // When
        ResponseEntity<WorkspaceQuotaStatusDTO> response = workspaceController.getQuotaStatus(null, supervisor);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertEquals(workspaceId, response.getBody().getWorkspaceId());
        verify(quotaService).getDetailedQuotaStatus(workspaceId);
    }

    @Test
    void testGetQuotaStatus_SuperAdmin_WithParam() {
        // Given
        WorkspaceQuotaStatusDTO mockDto = WorkspaceQuotaStatusDTO.builder()
                .workspaceId(workspaceId)
                .workspaceName("Test WS")
                .build();
        when(quotaService.getDetailedQuotaStatus(workspaceId)).thenReturn(mockDto);

        // When
        ResponseEntity<WorkspaceQuotaStatusDTO> response = workspaceController.getQuotaStatus(workspaceId, superAdmin);

        // Then
        assertEquals(200, response.getStatusCode().value());
        verify(quotaService).getDetailedQuotaStatus(workspaceId);
    }

    @Test
    void testGetQuotaStatus_SuperAdmin_MissingParam_Throws() {
        assertThrows(RuntimeException.class, () -> 
            workspaceController.getQuotaStatus(null, superAdmin));
    }
}
