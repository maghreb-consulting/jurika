package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContextHelper;
import fr.maghreb.gje.dto.fiche.AddEvenementRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import fr.maghreb.gje.dto.dossier.*;
import fr.maghreb.gje.models.*;
import java.time.LocalDate;
import fr.maghreb.gje.models.EntrepriseDossier.*;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DossierService {

    private final DossierRepository dossierRepository;
    private final TicketRepository ticketRepository;
    private final HistoriqueRepository historiqueRepository;
    private final UserRepository userRepository;
    private final QuotaService quotaService;
    private final FicheJuridiqueService ficheJuridiqueService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public EntrepriseDossier create(
            DossierCreateRequest request,
            UUID auteurId,
            UUID workspaceId) {

        TenantContextHelper.setTenant(workspaceId, entityManager);

        // Generate reference
        String reference = generateReference(workspaceId);

        // Verify dossier quota
        quotaService.checkDossierQuota(workspaceId);

        // Create dossier
        EntrepriseDossier dossier = EntrepriseDossier.builder()
                .workspaceId(workspaceId)
                .auteurId(auteurId)
                .reference(reference)
                .denomination(request.getDenomination())
                .formeJuridique(FormeJuridique.valueOf(
                    request.getFormeJuridique().toUpperCase()))
                .ice(request.getIce())
                .rcNumber(request.getRcNumber())
                .capitalSocial(request.getCapitalSocial())
                .siegeSocial(request.getSiegeSocial())
                .gerant(request.getGerant())
                .objetSocial(request.getObjetSocial())
                .dateEcheance(request.getDateEcheance())
                .source(request.getSource()) // <--- Set source
                .statut(StatutDossier.EN_COURS)
                .lectureSeule(false)
                .build();
        dossier = dossierRepository.save(dossier);

        boolean isImport = "IMPORT".equalsIgnoreCase(request.getSource());

        // Auto-create Fiche Juridique with CONSTITUTION or IMPORT event
        User auteur = userRepository.findById(auteurId)
                .orElseThrow(() -> new RuntimeException("Auteur introuvable"));
        
        ficheJuridiqueService.createFicheInitial(dossier, auteur.getFullName());

        if (isImport) {
            // Add specifically the IMPORT event as required by Module 3
            ficheJuridiqueService.addEvenement(dossier.getId(), workspaceId, auteur.getFullName(), 
                new AddEvenementRequest(LocalDate.now(), EvenementType.IMPORT_DOSSIER, "Importation initiale du dossier existant", null));
        }

        // Create ticket automatiquement
        Ticket.TicketType ticketType = isImport ? Ticket.TicketType.IMPORT : Ticket.TicketType.CREATION;
        String description = isImport ? "Importation dossier: " : "Création dossier: ";

        Ticket ticket = Ticket.builder()
                .workspaceId(workspaceId)
                .dossierId(dossier.getId())
                .auteurId(auteurId)
                .assignedTo(auteurId)
                .type(ticketType)
                .statut(Ticket.TicketStatut.OUVERT)
                .description(description + request.getDenomination())
                .build();
        ticketRepository.save(ticket);

        // Log historique
        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .dossierId(dossier.getId())
                .userId(auteurId)
                .action(isImport ? "DOSSIER_IMPORTE" : "DOSSIER_CREE")
                .nouvelleValeur(request.getDenomination())
                .build());

        return dossier;
    }

    @Transactional
    public List<EntrepriseDossier> getAll(
            UUID workspaceId,
            UUID userId,
            User.Role role) {
        
        TenantContextHelper.setTenant(workspaceId, entityManager);

        if (role == User.Role.EMPLOYE) {
            return dossierRepository
                .findByWorkspaceIdAndAuteurId(
                    workspaceId, userId);
        }
        return dossierRepository
            .findByWorkspaceId(workspaceId);
    }

    @Transactional
    public EntrepriseDossier getById(UUID id, UUID workspaceId) {
        TenantContextHelper.setTenant(workspaceId, entityManager);

        return dossierRepository.findById(id)
                .filter(d -> d.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> 
                    new RuntimeException("Dossier introuvable"));
    }

    @Transactional
    public EntrepriseDossier update(
            UUID id,
            DossierUpdateRequest request,
            UUID userId,
            UUID workspaceId) {

        TenantContextHelper.setTenant(workspaceId, entityManager);

        EntrepriseDossier dossier =
            getById(id, workspaceId);

        if (Boolean.TRUE.equals(
                dossier.getLectureSeule())) {
            throw new RuntimeException(
                "Dossier archivé — modification impossible");
        }

        String ancienneValeur =
            dossier.getDenomination();

        if (request.getDenomination() != null)
            dossier.setDenomination(
                request.getDenomination());
        if (request.getCapitalSocial() != null)
            dossier.setCapitalSocial(
                request.getCapitalSocial());
        if (request.getSiegeSocial() != null)
            dossier.setSiegeSocial(
                request.getSiegeSocial());
        if (request.getGerant() != null)
            dossier.setGerant(request.getGerant());
        if (request.getDateEcheance() != null)
            dossier.setDateEcheance(
                request.getDateEcheance());

        dossier.setUpdatedAt(LocalDateTime.now());
        dossier = dossierRepository.save(dossier);

        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .dossierId(id)
                .userId(userId)
                .action("DOSSIER_MODIFIE")
                .ancienneValeur(ancienneValeur)
                .nouvelleValeur(
                    dossier.getDenomination())
                .build());

        return dossier;
    }

    @Transactional
    public Map<String, Object> transferer(
            UUID id,
            TransfertRequest request,
            UUID superviseurId,
            UUID workspaceId) {

        TenantContextHelper.setTenant(workspaceId, entityManager);

        EntrepriseDossier dossier =
            getById(id, workspaceId);

        UUID ancienAuteurId = dossier.getAuteurId();

        // Verify new author exists in workspace
        userRepository.findById(
                request.getNouvelAuteurId())
            .filter(u -> u.getWorkspaceId()
                .equals(workspaceId))
            .orElseThrow(() ->
                new RuntimeException(
                    "Employé introuvable"));

        // Transfer dossier
        dossier.setAuteurId(
            request.getNouvelAuteurId());
        dossier.setUpdatedAt(LocalDateTime.now());
        dossierRepository.save(dossier);

        // Reassign all open tickets
        List<Ticket> tickets =
            ticketRepository.findByDossierId(id);
        tickets.stream()
            .filter(t -> t.getStatut() !=
                Ticket.TicketStatut.TERMINE)
            .forEach(t -> {
                t.setAssignedTo(
                    request.getNouvelAuteurId());
                t.setUpdatedAt(LocalDateTime.now());
                ticketRepository.save(t);
            });

        // Log
        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .dossierId(id)
                .userId(superviseurId)
                .action("DOSSIER_TRANSFERE")
                .ancienneValeur(
                    ancienAuteurId.toString())
                .nouvelleValeur(request
                    .getNouvelAuteurId().toString())
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Dossier transféré");
        result.put("dossier_id", id.toString());
        result.put("ancien_auteur",
            ancienAuteurId.toString());
        result.put("nouvel_auteur",
            request.getNouvelAuteurId().toString());
        result.put("tickets_reassigned",
            tickets.size());
        return result;
    }

    @Transactional
    public Map<String, Object> archiver(
            UUID id,
            UUID userId,
            UUID workspaceId) {

        TenantContextHelper.setTenant(workspaceId, entityManager);

        EntrepriseDossier dossier =
            getById(id, workspaceId);
        dossier.setLectureSeule(true);
        dossier.setStatut(StatutDossier.ARCHIVE);
        dossier.setUpdatedAt(LocalDateTime.now());
        dossierRepository.save(dossier);

        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .dossierId(id)
                .userId(userId)
                .action("DOSSIER_ARCHIVE")
                .nouvelleValeur("lecture_seule=true")
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("message",
            "Dossier archivé — lecture seule définitive");
        result.put("lecture_seule", true);
        result.put("statut", "ARCHIVE");
        return result;
    }

    private String generateReference(UUID workspaceId) {
        int year = LocalDateTime.now().getYear();
        long count = dossierRepository
            .countByWorkspaceId(workspaceId) + 1;
        return String.format("DOS-%d-%03d", year, count);
    }
}
