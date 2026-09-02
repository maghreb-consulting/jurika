package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserViewJpaRepository extends JpaRepository<UserViewEntity, UUID> {

    /**
     * Resolution batch des auteurs (uploaded_by) en « Prenom Nom », scopee
     * workspace (defense-in-depth : jurika_user a BYPASSRLS sous le conteneur
     * officiel, la RLS ne suffit pas).
     */
    List<UserViewEntity> findAllByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);
}
