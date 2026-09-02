package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.succursale.SuccursaleRequest;
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
public class SuccursaleService {

    private final SuccursaleRepository succursaleRepository;
    private final TicketService ticketService;

    @Transactional
    public Succursale attachSuccursale(UUID dossierId, SuccursaleRequest request, UUID userId, UUID workspaceId) {
        log.info("Rattachement d'une succursale {} pour le dossier {}", request.getDenomination(), dossierId);
        
        Succursale succursale = Succursale.builder()
                .parentDossierId(dossierId)
                .workspaceId(workspaceId)
                .denomination(request.getDenomination())
                .adresse(request.getAdresse())
                .rcNumber(request.getRcNumber())
                .build();
        
        Succursale saved = succursaleRepository.save(succursale);

        // Créer ticket automatique AUTRE
        // TODO: ticketService.createAutoTicket(dossierId, Ticket.TicketType.AUTRE, "Succursale rattachée: " + request.getDenomination(), userId, workspaceId);

        return saved;
    }

    public List<Succursale> getByDossierId(UUID dossierId) {
        return succursaleRepository.findByParentDossierId(dossierId);
    }
}
