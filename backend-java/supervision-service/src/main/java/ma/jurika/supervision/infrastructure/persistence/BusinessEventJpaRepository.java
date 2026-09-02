package ma.jurika.supervision.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BusinessEventJpaRepository extends JpaRepository<BusinessEventEntity, Long> {

    /**
     * Sprint 11 TASK 6 — Funnel aggregate par event_type sur une periode donnee.
     * Retourne lignes : [eventType (String), count (Long)].
     */
    @Query("""
        SELECT e.eventType, COUNT(e)
        FROM BusinessEventEntity e
        WHERE e.occurredAt BETWEEN :from AND :to
        GROUP BY e.eventType
        ORDER BY e.eventType
        """)
    List<Object[]> funnelByEventType(@Param("from") Instant from, @Param("to") Instant to);
}
