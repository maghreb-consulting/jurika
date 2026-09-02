package ma.jurika.dashboard.application;

import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.dashboard.api.dto.AgentDtos.AgentBriefing;
import ma.jurika.dashboard.api.dto.AgentDtos.AgentCounts;
import ma.jurika.dashboard.api.dto.AgentDtos.AgentSignals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lot IA-1 — Preuve par test du job matinal 08:00 (§3).
 *
 * <p>{@code runDaily()} doit : (a) enumerer les employes ACTIFS, (b) generer le
 * briefing de chacun via {@link AgentCopiloteService#generateBriefing}, (c) pousser
 * une notification persistante {@code AGENT_BRIEFING} par employe. Le cron
 * {@code 0 0 8 * * *} zone Africa/Casablanca n'est pas re-teste ici (config Spring).
 */
class AgentCopiloteSchedulerTest {

    private JdbcTemplate jdbc;
    private AgentCopiloteService copilote;
    private NotificationPublisher notifier;
    private AgentCopiloteScheduler scheduler;

    private final UUID ws1 = UUID.randomUUID();
    private final UUID emp1 = UUID.randomUUID();
    private final UUID ws2 = UUID.randomUUID();
    private final UUID emp2 = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        copilote = mock(AgentCopiloteService.class);
        notifier = mock(NotificationPublisher.class);
        scheduler = new AgentCopiloteScheduler(jdbc, copilote, notifier);

        // (a) deux employes actifs enumeres en SQL.
        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(new UUID[]{ws1, emp1}, new UUID[]{ws2, emp2}));

        // (b) chaque briefing genere expose des counts (pour la meta de notif).
        when(copilote.generateBriefing(ws1, emp1)).thenReturn(briefing(ws1, emp1, "Resume 1"));
        when(copilote.generateBriefing(ws2, emp2)).thenReturn(briefing(ws2, emp2, "Resume 2"));
    }

    @Test
    void runDailyGenereEtNotifieChaqueEmployeActif() {
        scheduler.runDaily();

        // (b) briefing genere pour chaque employe actif.
        verify(copilote).generateBriefing(ws1, emp1);
        verify(copilote).generateBriefing(ws2, emp2);

        // (c) une notif persistante AGENT_BRIEFING par employe, ciblee + scopee workspace.
        verify(notifier).notifyUser(eq(emp1), eq(ws1), eq("AGENT_BRIEFING"),
                eq("Votre briefing du jour"), eq("Resume 1"), eq("/dashboard#copilote"), anyMap());
        verify(notifier).notifyUser(eq(emp2), eq(ws2), eq("AGENT_BRIEFING"),
                eq("Votre briefing du jour"), eq("Resume 2"), eq("/dashboard#copilote"), anyMap());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void unEchecParEmployeNInterrompsPasLesAutres() {
        when(copilote.generateBriefing(ws1, emp1)).thenThrow(new RuntimeException("boom"));

        scheduler.runDaily();

        // emp1 echoue silencieusement, emp2 est quand meme notifie (best-effort).
        verify(notifier, never()).notifyUser(eq(emp1), any(), any(), any(), any(), any(), anyMap());
        verify(notifier).notifyUser(eq(emp2), eq(ws2), eq("AGENT_BRIEFING"),
                any(), eq("Resume 2"), any(), anyMap());
    }

    // ------------------------------------------------------------------
    private AgentBriefing briefing(UUID ws, UUID emp, String summary) {
        AgentSignals signals = new AgentSignals(List.of(), List.of(), new AgentCounts(1, 0, 2, 1));
        return new AgentBriefing(UUID.randomUUID(), ws, emp, summary,
                null, false, List.of(), signals, Instant.now(), null);
    }
}
