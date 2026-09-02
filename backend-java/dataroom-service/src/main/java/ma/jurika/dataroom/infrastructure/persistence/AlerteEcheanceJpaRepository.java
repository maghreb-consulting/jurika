package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AlerteEcheanceJpaRepository extends JpaRepository<AlerteEcheanceEntity, UUID> {

    @Query("""
            SELECT a FROM AlerteEcheanceEntity a
            WHERE a.dateAlerte = :date AND a.statut = :statut
            """)
    List<AlerteEcheanceEntity> findByDateAlerteAndStatut(@Param("date") LocalDate date,
                                                         @Param("statut") String statut);

    /**
     * Lot DIVERS §A (2026-08-13) — meme requete, mais en EXCLUANT les societes qui
     * ne declarent plus (dissoutes / en liquidation / liquidees / radiees). Les
     * echeances fiscales de ces dossiers sont ainsi <b>suspendues</b> : elles
     * restent en base et consultables, mais ne sont plus depilees ni notifiees.
     *
     * <p>La suspension est <b>derivee</b> du statut du dossier, jamais persistee :
     * si la societe redevient active, ses alertes repartent d'elles-memes.
     */
    @Query("""
            SELECT a FROM AlerteEcheanceEntity a
            WHERE a.dateAlerte = :date AND a.statut = :statut
              AND NOT EXISTS (
                    SELECT 1 FROM DossierViewEntity d
                    WHERE d.id = a.dossierId AND d.statut IN :archivedStatus)
            """)
    List<AlerteEcheanceEntity> findDueForActiveDossiers(@Param("date") LocalDate date,
                                                        @Param("statut") String statut,
                                                        @Param("archivedStatus") Collection<String> archivedStatus);

    @Query("""
            SELECT a FROM AlerteEcheanceEntity a
            WHERE a.dossierId = :dossierId
              AND a.dateEcheance BETWEEN :from AND :to
            ORDER BY a.dateEcheance ASC
            """)
    List<AlerteEcheanceEntity> findByDossierBetween(@Param("dossierId") UUID dossierId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    @Query("""
            SELECT a FROM AlerteEcheanceEntity a
            WHERE a.dossierId = :dossierId
              AND a.statut = :statut
            ORDER BY a.dateEcheance ASC
            """)
    List<AlerteEcheanceEntity> findByDossierAndStatut(@Param("dossierId") UUID dossierId,
                                                      @Param("statut") String statut);

    List<AlerteEcheanceEntity> findByExerciceFiscalId(UUID exerciceFiscalId);
}
