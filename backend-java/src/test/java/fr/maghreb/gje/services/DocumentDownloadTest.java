package fr.maghreb.gje.services;

import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentDownloadTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private HistoriqueService historiqueService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DocumentService documentService;

    private UUID workspaceId;
    private User employee;
    private User supervisor;
    private User superAdmin;
    private Document mockDocument;
    private EntrepriseDossier mockDossier;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        
        employee = new User();
        employee.setId(UUID.randomUUID());
        employee.setWorkspaceId(workspaceId);
        employee.setRole(User.Role.EMPLOYE);

        supervisor = new User();
        supervisor.setId(UUID.randomUUID());
        supervisor.setWorkspaceId(workspaceId);
        supervisor.setRole(User.Role.SUPERVISEUR);

        superAdmin = new User();
        superAdmin.setId(UUID.randomUUID());
        superAdmin.setWorkspaceId(workspaceId);
        superAdmin.setRole(User.Role.SUPER_ADMIN);

        mockDossier = EntrepriseDossier.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .auteurId(employee.getId())
                .build();

        mockDocument = Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .dossierId(mockDossier.getId())
                .title("Test Doc")
                .versionNumber(1)
                .build();

        ReflectionTestUtils.setField(documentService, "entityManager", entityManager);
        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
    }

    @Test
    void testEmployee_DownloadOwnDocument_Allowed() {
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));
        when(dossierRepository.findById(mockDossier.getId())).thenReturn(Optional.of(mockDossier));

        Document result = documentService.download(mockDocument.getId(), employee, "127.0.0.1");
        
        assertNotNull(result);
        verify(historiqueService).logDownload(eq(workspaceId), eq(employee.getId()), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testEmployee_DownloadOtherDocument_Forbidden() {
        mockDossier.setAuteurId(UUID.randomUUID()); // Other author
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));
        when(dossierRepository.findById(mockDossier.getId())).thenReturn(Optional.of(mockDossier));

        assertThrows(RuntimeException.class, () -> 
            documentService.download(mockDocument.getId(), employee, "127.0.0.1"));
    }

    @Test
    void testSupervisor_DownloadAnyDocument_Allowed() {
        mockDossier.setAuteurId(UUID.randomUUID()); // Other author
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));

        Document result = documentService.download(mockDocument.getId(), supervisor, "127.0.0.1");
        
        assertNotNull(result);
        verify(historiqueService).logDownload(eq(workspaceId), eq(supervisor.getId()), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testSuperAdmin_DownloadDocument_Forbidden() {
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));

        assertThrows(RuntimeException.class, () -> 
            documentService.download(mockDocument.getId(), superAdmin, "127.0.0.1"));
    }
}
