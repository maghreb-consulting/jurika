package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndWorkspaceId(String email, UUID workspaceId);

    boolean existsByEmailAndWorkspaceId(String email, UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    @Modifying
    @Query("UPDATE User u SET u.failedAttempts = :attempts WHERE u.id = :id")
    void updateFailedAttempts(int attempts, UUID id);

    @Query("SELECT u FROM User u WHERE u.workspaceId = :workspaceId")
    List<User> findAllByWorkspaceId(UUID workspaceId);

    List<User> findByWorkspaceIdAndRole(UUID workspaceId, User.Role role);

    List<User> findByRole(User.Role role);
}
