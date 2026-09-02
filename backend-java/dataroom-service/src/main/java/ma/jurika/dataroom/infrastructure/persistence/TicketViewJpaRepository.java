package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketViewJpaRepository
        extends JpaRepository<TicketViewEntity, UUID>,
                JpaSpecificationExecutor<TicketViewEntity> {
    List<TicketViewEntity> findAllByDossierIdAndStatutOrderByClotureAtDesc(UUID dossierId, String statut);

    /**
     * Lot DIVERS §A (2026-08-13) — lookup scope workspace, utilise par
     * {@code DossierArchiveGuard} pour verifier la derogation d'ecriture du
     * workflow LIQUIDATION sur un dossier dissous. Filtre {@code workspace_id}
     * explicite : la RLS n'est pas fiable (role applicatif BYPASSRLS).
     */
    Optional<TicketViewEntity> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    /**
     * Fiche client (2026-07-14) — toutes les operations d'un dossier SAUF les
     * tickets annules, pour la section « Operations juridiques subies ». Le
     * dossier est prealablement valide comme appartenant au workspace courant
     * (cf. DossierJuridiqueService), d'ou le filtrage par dossier_id seul, aligne
     * sur TicketSpecifications.byDossier.
     */
    List<TicketViewEntity> findAllByDossierIdAndStatutNot(UUID dossierId, String statut);
}
