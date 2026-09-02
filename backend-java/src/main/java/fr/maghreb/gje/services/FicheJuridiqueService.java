package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.dto.fiche.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import fr.maghreb.gje.security.EncryptionUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FicheJuridiqueService {

    private final FicheJuridiqueRepository ficheJuridiqueRepository;
    private final EvenementJuridiqueRepository evenementRepository;
    private final HistoriqueRepository historiqueRepository;
    private final UserRepository userRepository;
    private final DossierRepository dossierRepository;
    private final EncryptionUtil encryptionUtil;
    private final HistoriqueService historiqueService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void createFicheInitial(EntrepriseDossier dossier, String auteurNom) {
        FicheJuridique fiche = FicheJuridique.builder()
                .workspaceId(dossier.getWorkspaceId())
                .dossierId(dossier.getId())
                .dateCreation(LocalDate.now())
                .statutJuridique(mapStatut(dossier.getStatut()))
                .build();
        
        fiche = ficheJuridiqueRepository.save(fiche);

        EvenementJuridique event = EvenementJuridique.builder()
                .ficheJuridique(fiche)
                .date(LocalDate.now())
                .typeEvenement(EvenementType.CONSTITUTION)
                .description("Création initiale du dossier : " + dossier.getDenomination())
                .auteurNom(auteurNom)
                .versionDossier(1)
                .build();
        
        evenementRepository.save(event);
    }

    private FicheJuridique.StatutJuridique mapStatut(EntrepriseDossier.StatutDossier status) {
        if (status == null) return FicheJuridique.StatutJuridique.ACTIVE;
        switch (status) {
            case EN_COURS: return FicheJuridique.StatutJuridique.ACTIVE;
            case BLOQUE: return FicheJuridique.StatutJuridique.ARCHIVEE; 
            case CLOTURE: return FicheJuridique.StatutJuridique.ARCHIVEE;
            case ARCHIVE: return FicheJuridique.StatutJuridique.ARCHIVEE;
            default: return FicheJuridique.StatutJuridique.ACTIVE;
        }
    }

    @Transactional(readOnly = true)
    public FicheJuridiqueDTO getFiche(UUID dossierId, UUID workspaceId, boolean isSuperviseur) {
        FicheJuridique fiche = ficheJuridiqueRepository.findByDossierIdAndWorkspaceId(dossierId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Fiche Juridique introuvable"));

        return mapToDTO(fiche, isSuperviseur);
    }

    @Transactional
    public FicheJuridiqueDTO updateFiche(UUID dossierId, UUID workspaceId, UUID userId, UpdateFicheJuridiqueRequest request, boolean isSuperviseur) {
        FicheJuridique fiche = ficheJuridiqueRepository.findByDossierIdAndWorkspaceId(dossierId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Fiche Juridique introuvable"));

        // Update Section 1
        fiche.setIce(request.getIce());
        fiche.setNumeroRC(request.getNumeroRC());
        fiche.setDateCreation(request.getDateCreation());
        fiche.setAbreviation(request.getAbreviation());
        fiche.setCapitalSocial(request.getCapitalSocial());
        fiche.setFormeJuridique(request.getFormeJuridique());
        fiche.setStatutJuridique(request.getStatutJuridique());

        // Update Section 2
        fiche.setActivitePrincipale(request.getActivitePrincipale());
        fiche.setActiviteReglementee(request.getActiviteReglementee());
        fiche.setAutorisationReglementee(request.getAutorisationReglementee());
        fiche.setCodeNaf(request.getCodeNaf());

        // Update Section 3
        fiche.setAdresseSiege(request.getAdresseSiege());
        fiche.setActiviteAuSiege(request.getActiviteAuSiege());
        fiche.setEnseigne(request.getEnseigne());

        // Handle Etablissements
        if (fiche.getEtablissementsSecondaires() != null) {
            fiche.getEtablissementsSecondaires().clear();
        } else {
            fiche.setEtablissementsSecondaires(new ArrayList<>());
        }
        
        if (request.getEtablissementsSecondaires() != null) {
            for (EtablissementSecondaireDTO dto : request.getEtablissementsSecondaires()) {
                fiche.getEtablissementsSecondaires().add(EtablissementSecondaire.builder()
                        .ficheJuridique(fiche)
                        .adresse(dto.getAdresse())
                        .activite(dto.getActivite())
                        .denomination(dto.getDenomination())
                        .build());
            }
        }

        // Handle Representants
        if (fiche.getRepresentants() != null) {
            fiche.getRepresentants().clear();
        } else {
            fiche.setRepresentants(new ArrayList<>());
        }

        if (request.getRepresentants() != null) {
            for (RepresentantDTO dto : request.getRepresentants()) {
                String encryptedCin = encryptionUtil.encrypt(dto.getCin());
                fiche.getRepresentants().add(Representant.builder()
                        .ficheJuridique(fiche)
                        .nomPrenom(dto.getNomPrenom())
                        .qualite(dto.getQualite())
                        .cinChiffre(encryptedCin)
                        .datePriseFonction(dto.getDatePriseFonction())
                        .build());
            }
        }

        fiche = ficheJuridiqueRepository.save(fiche);

        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .dossierId(dossierId)
                .action("FICHE_JURIDIQUE_MODIFIEE")
                .nouvelleValeur("Mise à jour des sections de la Fiche Légale")
                .build());

        return mapToDTO(fiche, isSuperviseur);
    }

    @Transactional(readOnly = true)
    public List<EvenementJuridiqueDTO> getTimeline(UUID dossierId, UUID workspaceId) {
        FicheJuridique fiche = ficheJuridiqueRepository.findByDossierIdAndWorkspaceId(dossierId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Fiche Juridique introuvable"));

        return evenementRepository.findByFicheJuridiqueIdOrderByDateDesc(fiche.getId())
                .stream().map(this::mapEventToDTO).collect(Collectors.toList());
    }

    @Transactional
    public EvenementJuridiqueDTO addEvenement(UUID dossierId, UUID workspaceId, String auteurNom, AddEvenementRequest request) {
        FicheJuridique fiche = ficheJuridiqueRepository.findByDossierIdAndWorkspaceId(dossierId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Fiche Juridique introuvable"));

        // Determine current version roughly
        int expectedVersion = 1; // Can hook into Document logic for exact current version mapping.

        EvenementJuridique event = EvenementJuridique.builder()
                .ficheJuridique(fiche)
                .date(request.getDate() != null ? request.getDate() : LocalDate.now())
                .typeEvenement(request.getTypeEvenement())
                .description(request.getDescription())
                .documentId(request.getDocumentId())
                .auteurNom(auteurNom)
                .versionDossier(expectedVersion)
                .build();
        
        event = evenementRepository.save(event);
        return mapEventToDTO(event);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportFiche(UUID dossierId, UUID workspaceId, User user, String ipAddress, boolean isSuperviseur) {
        try {
            FicheJuridiqueDTO ficheDto = getFiche(dossierId, workspaceId, isSuperviseur);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("cabinet", "Nom du Workspace"); // Could fetch workspace name
            metadata.put("dateExport", LocalDate.now());
            metadata.put("logoUrl", "/assets/logo.png"); // Extensible workspace logo map

            Map<String, Object> exportPayload = new HashMap<>();
            exportPayload.put("metadata", metadata);
            exportPayload.put("fiche", ficheDto);
            exportPayload.put("evenements", getTimeline(dossierId, workspaceId));

            return exportPayload;
        } finally {
            historiqueService.logDownload(
                workspaceId,
                user.getId(),
                user.getRole(),
                dossierId,
                null, 
                "Export Fiche Légale",
                null,
                ipAddress,
                "PDF"
            );
        }
    }

    private FicheJuridiqueDTO mapToDTO(FicheJuridique fiche, boolean isSuperviseur) {
        return FicheJuridiqueDTO.builder()
                .id(fiche.getId())
                .dossierId(fiche.getDossierId())
                .ice(fiche.getIce())
                .numeroRC(fiche.getNumeroRC())
                .dateCreation(fiche.getDateCreation())
                .abreviation(fiche.getAbreviation())
                .capitalSocial(fiche.getCapitalSocial())
                .formeJuridique(fiche.getFormeJuridique())
                .statutJuridique(fiche.getStatutJuridique())
                .activitePrincipale(fiche.getActivitePrincipale())
                .activiteReglementee(fiche.getActiviteReglementee())
                .autorisationReglementee(fiche.getAutorisationReglementee())
                .codeNaf(fiche.getCodeNaf())
                .adresseSiege(fiche.getAdresseSiege())
                .activiteAuSiege(fiche.getActiviteAuSiege())
                .enseigne(fiche.getEnseigne())
                .etablissementsSecondaires(fiche.getEtablissementsSecondaires() != null ? 
                        fiche.getEtablissementsSecondaires().stream().map(e -> EtablissementSecondaireDTO.builder()
                            .id(e.getId())
                            .adresse(e.getAdresse())
                            .activite(e.getActivite())
                            .denomination(e.getDenomination())
                            .build()).collect(Collectors.toList()) : new ArrayList<>())
                .representants(fiche.getRepresentants() != null ? 
                        fiche.getRepresentants().stream().map(r -> {
                            String decryptedCin = encryptionUtil.decrypt(r.getCinChiffre());
                            String cinDisplay = isSuperviseur ? decryptedCin : maskCin(decryptedCin);
                            return RepresentantDTO.builder()
                                .id(r.getId())
                                .nomPrenom(r.getNomPrenom())
                                .qualite(r.getQualite())
                                .datePriseFonction(r.getDatePriseFonction())
                                .cin(cinDisplay)
                                .build();
                        }).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private EvenementJuridiqueDTO mapEventToDTO(EvenementJuridique e) {
        return EvenementJuridiqueDTO.builder()
                .id(e.getId())
                .date(e.getDate())
                .typeEvenement(e.getTypeEvenement())
                .description(e.getDescription())
                .documentId(e.getDocumentId())
                .auteurNom(e.getAuteurNom())
                .versionDossier(e.getVersionDossier())
                .build();
    }

    private String maskCin(String cin) {
        if (cin == null) return null;
        if (cin.length() <= 4) return "****";
        return "****" + cin.substring(cin.length() - 4);
    }
}
