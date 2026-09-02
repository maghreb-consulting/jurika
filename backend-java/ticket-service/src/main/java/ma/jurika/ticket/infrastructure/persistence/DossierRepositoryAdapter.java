package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.port.DossierRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DossierRepositoryAdapter implements DossierRepository {

    private final DossierJpaRepository jpa;

    public DossierRepositoryAdapter(DossierJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EntrepriseDossier save(EntrepriseDossier d) {
        DossierEntity e = (d.id() != null)
                ? jpa.findById(d.id()).orElseGet(DossierEntity::new)
                : new DossierEntity();
        e.setWorkspaceId(d.workspaceId());
        e.setRaisonSociale(d.raisonSociale());
        e.setFormeJuridique(d.formeJuridique().name());
        e.setIce(d.ice());
        e.setRcNumero(d.rcNumero());
        e.setRcTribunal(d.rcTribunal());
        e.setIdentifiantFiscal(d.identifiantFiscal());
        e.setTaxeProfessionnelle(d.taxeProfessionnelle());
        e.setCnss(d.cnss());
        e.setAdresseSiege(d.adresseSiege());
        e.setVille(d.ville());
        e.setCapitalSocialMad(d.capitalSocialMad() == null ? null : BigDecimal.valueOf(d.capitalSocialMad()));
        e.setDateConstitution(d.dateConstitution());
        e.setStatut(d.statut().name());
        e.setClientId(d.clientId());
        // Owner durable (V9) : renseigne a la creation (employe createur) ;
        // les transferts ulterieurs passent par updateResponsable(). On evite
        // d'ecraser un responsable existant par un null lors d'un re-save.
        if (d.responsableId() != null) {
            e.setResponsableId(d.responsableId());
        }
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<EntrepriseDossier> findById(UUID workspaceId, UUID id) {
        return jpa.findById(id)
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .map(this::toDomain);
    }

    @Override
    public boolean markCreatedByTicket(UUID workspaceId, UUID dossierId, UUID ticketId) {
        return jpa.markCreatedByTicket(workspaceId, dossierId, ticketId) > 0;
    }

    @Override
    public Optional<EntrepriseDossier> findAliveByRaisonSociale(UUID workspaceId, String raisonSociale) {
        if (raisonSociale == null || raisonSociale.isBlank()) {
            return Optional.empty();
        }
        return jpa.findFirstAliveByWorkspaceAndRaison(workspaceId, raisonSociale.trim())
                .map(this::toDomain);
    }

    private EntrepriseDossier toDomain(DossierEntity e) {
        return new EntrepriseDossier(
                e.getId(),
                e.getWorkspaceId(),
                e.getRaisonSociale(),
                FormeJuridique.valueOf(e.getFormeJuridique()),
                e.getIce(),
                e.getRcNumero(),
                e.getRcTribunal(),
                e.getIdentifiantFiscal(),
                e.getTaxeProfessionnelle(),
                e.getCnss(),
                e.getAdresseSiege(),
                e.getVille(),
                e.getCapitalSocialMad() == null ? null : e.getCapitalSocialMad().doubleValue(),
                e.getDateConstitution(),
                DossierStatut.valueOf(e.getStatut()),
                e.getClientId(),
                e.getResponsableId(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    @Override
    public boolean updateResponsable(UUID workspaceId, UUID dossierId, UUID responsableId) {
        return jpa.updateResponsable(workspaceId, dossierId, responsableId) > 0;
    }
}
