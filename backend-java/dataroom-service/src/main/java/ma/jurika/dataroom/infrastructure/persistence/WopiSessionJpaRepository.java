package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WopiSessionJpaRepository extends JpaRepository<WopiSessionEntity, UUID> {

    /**
     * Le chemin chaud : à chaque requête WOPI. La recherche se fait sur
     * l'empreinte du jeton, jamais sur le jeton.
     */
    Optional<WopiSessionEntity> findByTokenHash(String tokenHash);

    /**
     * Purge des séances définitivement closes. On garde une marge après
     * l'expiration : une séance récemment échue reste utile au diagnostic
     * (« pourquoi ma dernière modification n'a pas été enregistrée ? »).
     */
    @Modifying
    @Query("DELETE FROM WopiSessionEntity s WHERE s.expiresAt < :avant")
    int purgerEchues(@Param("avant") Instant avant);
}
