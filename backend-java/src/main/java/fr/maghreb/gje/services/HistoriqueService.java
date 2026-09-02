package fr.maghreb.gje.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.models.Historique;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.HistoriqueRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoriqueService {

    private final HistoriqueRepository historiqueRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void logDownload(
            UUID workspaceId,
            UUID userId,
            User.Role userRole,
            UUID dossierId,
            UUID documentId,
            String documentTitle,
            Integer documentVersion,
            String ipAddress,
            String format) {
        
        try {
            setTenant(workspaceId);

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DOCUMENT_TELECHARGE");
            payload.put("documentId", documentId);
            payload.put("documentTitle", documentTitle);
            payload.put("documentVersion", documentVersion);
            payload.put("userRole", userRole);
            payload.put("format", format);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            Historique entry = Historique.builder()
                    .workspaceId(workspaceId)
                    .dossierId(dossierId)
                    .userId(userId)
                    .action("DOCUMENT_TELECHARGE")
                    .nouvelleValeur(jsonPayload)
                    .ipAddress(ipAddress)
                    .build();

            historiqueRepository.save(entry);
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'historique de téléchargement", e);
            // Ne pas bloquer le flux principal
        }
    }

    private void setTenant(UUID workspaceId) {
        TenantContext.setTenantId(
            workspaceId.toString());
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '"
            + workspaceId.toString() + "'")
            .executeUpdate();
        entityManager.flush();
    }

    @Transactional
    public List<Historique> getByDossier(
            UUID dossierId, UUID workspaceId) {
        setTenant(workspaceId);
        return historiqueRepository
            .findByDossierIdOrderByTimestampDesc(
                dossierId);
    }

    @Transactional
    public List<Historique> getByWorkspace(
            UUID workspaceId) {
        setTenant(workspaceId);
        return historiqueRepository
            .findByWorkspaceIdOrderByTimestampDesc(
                workspaceId);
    }
}
