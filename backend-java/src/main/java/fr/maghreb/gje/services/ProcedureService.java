package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.procedure.DissolutionRequest;
import fr.maghreb.gje.dto.procedure.LiquidationRequest;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcedureService {

    private final DossierRepository dossierRepository;
    private final TicketRepository ticketRepository;
    private final HistoriqueRepository historiqueRepository;

    @Transactional
    public void startDissolution(UUID dossierId, DissolutionRequest request, UUID userId, UUID workspaceId) {
        log.info("Démarrage de la procédure de dissolution pour le dossier {}", dossierId);
        
        // 1. Changer le statut du dossier à DISSOLUTION
        EntrepriseDossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier introuvable"));
        
        String ancienStatut = dossier.getStatut().name();
        dossier.setStatut(EntrepriseDossier.StatutDossier.DISSOLUTION);
        dossierRepository.save(dossier);

        // 2. Créer ticket automatique DISSOLUTION
        Ticket ticket = Ticket.builder()
                .workspaceId(workspaceId)
                .dossierId(dossierId)
                .auteurId(userId)
                .assignedTo(userId)
                .type(Ticket.TicketType.DISSOLUTION)
                .statut(Ticket.TicketStatut.OUVERT)
                .description("Procédure: Dissolution - Dossier " + dossier.getDenomination())
                .build();
        ticketRepository.save(ticket);

        // 3. Logger action
        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .dossierId(dossierId)
                .userId(userId)
                .action("DISSOLUTION_DEMARREE")
                .ancienneValeur(ancienStatut)
                .nouvelleValeur("DISSOLUTION")
                .build());
    }

    @Transactional
    public void startLiquidation(UUID dossierId, LiquidationRequest request, UUID userId, UUID workspaceId) {
        log.info("Démarrage de la procédure de liquidation ({}) pour le dossier {}", request.getMode(), dossierId);
        
        // 1. Changer statut dossier à LIQUIDATION
        EntrepriseDossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier introuvable"));
        
        String ancienStatut = dossier.getStatut().name();
        dossier.setStatut(EntrepriseDossier.StatutDossier.LIQUIDATION);
        dossierRepository.save(dossier);

        // 2. Si JUDICIAIRE ou AMIABLE, créer ticket LIQUIDATION
        Ticket ticket = Ticket.builder()
                .workspaceId(workspaceId)
                .dossierId(dossierId)
                .auteurId(userId)
                .assignedTo(userId)
                .type("JUDICIAIRE".equalsIgnoreCase(request.getMode()) ? 
                      Ticket.TicketType.LIQUIDATION_JUDICIAIRE : Ticket.TicketType.AUTRE)
                .statut(Ticket.TicketStatut.OUVERT)
                .description("Procédure: Liquidation " + request.getMode() + " - Dossier " + dossier.getDenomination())
                .build();
        ticketRepository.save(ticket);

        // 3. Logger action
        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .dossierId(dossierId)
                .userId(userId)
                .action("LIQUIDATION_DEMARREE")
                .ancienneValeur(ancienStatut)
                .nouvelleValeur("LIQUIDATION_" + request.getMode().toUpperCase())
                .build());
    }

    public byte[] generateDocument(String type, UUID dossierId) {
        // ACTE_DISSOLUTION | RAPPORT_LIQUIDATION
        log.info("Génération du document {} pour le dossier {}", type, dossierId);
        return ("Document généré: " + type).getBytes();
    }
}
