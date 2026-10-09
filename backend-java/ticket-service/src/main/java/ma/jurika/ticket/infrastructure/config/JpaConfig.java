package ma.jurika.ticket.infrastructure.config;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.audit.JdbcAuditEventEmitter;
import ma.jurika.ticket.domain.service.DeadlineComputer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@Configuration
@EnableAsync
public class JpaConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public DeadlineComputer deadlineComputer(Clock clock) {
        return new DeadlineComputer(clock);
    }

    @Bean
    public AuditEventEmitter auditEventEmitter(JdbcTemplate jdbc) {
        return new JdbcAuditEventEmitter(jdbc);
    }
}
