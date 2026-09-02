package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarketingLeadJpaRepository extends JpaRepository<MarketingLeadEntity, UUID> {
    long countByEmailIgnoreCase(String email);
}
