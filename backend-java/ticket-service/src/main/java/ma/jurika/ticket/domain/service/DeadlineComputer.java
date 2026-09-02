package ma.jurika.ticket.domain.service;

import ma.jurika.ticket.domain.model.Deadline;
import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.model.DeadlineSeverity;
import ma.jurika.ticket.domain.model.DeadlineSource;
import ma.jurika.ticket.domain.model.DeadlineStatut;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Calcule la date d'echeance pour une regle donnee a partir d'une date d'ancrage.
 * <p>
 * Service de domaine pur, sans effet de bord. Le moteur d'orchestration
 * ({@code ComputeDeadlinesUseCase}) appelle ce composant puis persiste via {@code DeadlineRepository}.
 */
public final class DeadlineComputer {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private final Clock clock;

    public DeadlineComputer(Clock clock) {
        this.clock = clock;
    }

    public Deadline build(UUID workspaceId, UUID ticketId, UUID dossierId,
                          DeadlineRule rule, Instant anchor, UUID creeParId) {
        return build(workspaceId, ticketId, dossierId, rule, anchor, creeParId, null);
    }

    public Deadline build(UUID workspaceId, UUID ticketId, UUID dossierId,
                          DeadlineRule rule, Instant anchor, UUID creeParId,
                          Map<String, Object> metadata) {
        Instant due = computeDueAt(anchor, rule);
        Instant now = Instant.now(clock);
        DeadlineSeverity severity = adjustSeverity(due, now, rule.defaultSeverity());
        return new Deadline(
                null,
                workspaceId,
                ticketId,
                dossierId,
                rule.defaultTitle(),
                rule.defaultDescription(),
                due,
                severity,
                DeadlineSource.AUTO,
                rule,
                DeadlineStatut.OUVERTE,
                null,
                creeParId,
                null,
                null,
                metadata,
                now,
                now);
    }

    public Instant computeDueAt(Instant anchor, DeadlineRule rule) {
        ZonedDateTime anchored = anchor.atZone(CASABLANCA);
        return anchored.plus(rule.offset()).toInstant();
    }

    /**
     * Promeut la severite vers CRITICAL si l'echeance est proche (< 3 jours).
     */
    private DeadlineSeverity adjustSeverity(Instant due, Instant now, DeadlineSeverity base) {
        long daysUntil = (due.getEpochSecond() - now.getEpochSecond()) / 86_400L;
        if (daysUntil < 0) return DeadlineSeverity.CRITICAL;
        if (daysUntil <= 3 && base == DeadlineSeverity.WARNING) return DeadlineSeverity.CRITICAL;
        return base;
    }
}
