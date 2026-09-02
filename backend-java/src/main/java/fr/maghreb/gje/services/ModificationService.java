package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.modification.ModificationRequest;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModificationService {

    private final ModificationDossierRepository modificationRepository;
    private final TicketService ticketService;
    private final HistoriqueService historiqueService;
    private final DossierRepository dossierRepository;

    @Transactional
    public ModificationDossier createModification(UUID dossierId, ModificationRequest request, UUID userId, UUID workspaceId) {
        log.info("Création d'une modification de type {} pour le dossier {}", request.getType(), dossierId);
        
        // 1. Enregistrer la modification en base
        ModificationDossier modif = ModificationDossier.builder()
                .dossierId(dossierId)
                .workspaceId(workspaceId)
                .type(request.getType())
                .description(request.getDescription())
                .ancienneValeur(request.getAncienneValeur())
                .nouvelleValeur(request.getNouvelleValeur())
                .build();
        
        ModificationDossier saved = modificationRepository.save(modif);

        // 2. Créer automatiquement un ticket de type MODIFICATION
        // TODO: ticketService.createAutoTicket(dossierId, Ticket.TicketType.MODIFICATION, "Modification: " + request.getType(), userId, workspaceId);

        // 3. Logger dans l'historique
        // TODO: historiqueService.log("MODIFICATION_CREEE", dossierId, userId, workspaceId, null, request.getType().name());

        return saved;
    }

    public List<ModificationDossier> getHistory(UUID dossierId) {
        return modificationRepository.findByDossierIdOrderByCreatedAtDesc(dossierId);
    }

    public byte[] generatePV(UUID modifId) {
        // TODO: Appel IA pour générer le PV de modification basé sur le type et les valeurs
        log.info("Génération du PV pour la modification {}", modifId);
        return "Contenu du PV généré par IA".getBytes();
    }
}
