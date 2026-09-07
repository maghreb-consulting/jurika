package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WopiVerrouJpaRepository extends JpaRepository<WopiVerrouEntity, UUID> {

    /**
     * Le verrou d'un document, dans SON workspace.
     *
     * <p>Le cloisonnement est vérifié explicitement, comme partout ailleurs sur
     * ce chemin : la RLS ne filtre rien ici, {@code jurika_user} a BYPASSRLS.
     */
    Optional<WopiVerrouEntity> findByWorkspaceIdAndDocumentId(UUID workspaceId, UUID documentId);

    /** Le verrou tenu par une séance donnée — relâché à sa fermeture. */
    Optional<WopiVerrouEntity> findBySessionId(UUID sessionId);

    /**
     * Purge des verrous échus. Opportuniste : ils sont déjà traités comme
     * absents à la lecture, ceci ne fait que ne pas laisser la table croître.
     */
    @Modifying
    @Query("DELETE FROM WopiVerrouEntity v WHERE v.expireAt < :avant")
    int purgerEchus(@Param("avant") Instant avant);
}
