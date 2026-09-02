package fr.maghreb.gje.repositories;
import fr.maghreb.gje.models.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SubscriptionRepository
    extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByWorkspaceId(UUID workspaceId);
}
