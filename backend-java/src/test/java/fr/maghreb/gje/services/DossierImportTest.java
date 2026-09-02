package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.dossier.DossierCreateRequest;
import fr.maghreb.gje.dto.fiche.AddEvenementRequest;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DossierImportTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private HistoriqueRepository historiqueRepository;
    @Mock private UserRepository userRepository;
    @Mock private QuotaService quotaService;
    @Mock private FicheJuridiqueService ficheJuridiqueService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private DossierService dossierService;

    private UUID workspaceId;
    private UUID auteurId;
    private User auteur;

    @BeforeEach
    void setUp() throws Exception {
        workspaceId = UUID.randomUUID();
        auteurId = UUID.randomUUID();
        auteur = User.builder().id(auteurId).fullName("Test Auteur").workspaceId(workspaceId).build();
        
        // Manual injection to be sure
        java.lang.reflect.Field field = DossierService.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(dossierService, entityManager);

        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(mock(Query.class));
        lenient().when(userRepository.findById(auteurId)).thenReturn(Optional.of(auteur));
    }

    @Test
    void testCreateDossier_WithImportSource() {
        // Given
        DossierCreateRequest request = new DossierCreateRequest();
        request.setDenomination("Emp Import Test");
        request.setFormeJuridique("SARL");
        request.setSource("IMPORT");

        when(dossierRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        EntrepriseDossier result = dossierService.create(request, auteurId, workspaceId);

        // Then
        assertEquals("IMPORT", result.getSource());
        
        // Verify IMPORT ticket created
        verify(ticketRepository).save(argThat(t -> t.getType() == Ticket.TicketType.IMPORT));
        
        // Verify IMPORT_DOSSIER legal event added
        verify(ficheJuridiqueService).addEvenement(any(), eq(workspaceId), eq("Test Auteur"), 
            argThat(e -> e.getTypeEvenement() == EvenementType.IMPORT_DOSSIER));
            
        // Verify history log
        verify(historiqueRepository).save(argThat(h -> "DOSSIER_IMPORTE".equals(h.getAction())));
    }

    @Test
    void testCreateDossier_RegularCreation() {
        // Given
        DossierCreateRequest request = new DossierCreateRequest();
        request.setDenomination("Normal WS");
        request.setFormeJuridique("SA");
        request.setSource("CREATION");

        when(dossierRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        dossierService.create(request, auteurId, workspaceId);

        // Then
        verify(ticketRepository).save(argThat(t -> t.getType() == Ticket.TicketType.CREATION));
        verify(ficheJuridiqueService, never()).addEvenement(any(), any(), any(), 
            argThat(e -> e != null && e.getTypeEvenement() == EvenementType.IMPORT_DOSSIER));
    }
}
