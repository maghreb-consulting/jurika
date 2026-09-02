package ma.jurika.dataroom.application.access;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 2026-07-03 -- Executor dedie et BORNE pour le tracage d'acces client.
 *
 * <p>Sans ce bean, {@code @Async} retombe sur le {@code SimpleAsyncTaskExecutor}
 * implicite qui cree un thread NEUF a chaque appel (non borne) -- inadapte a un
 * flux potentiellement frequent (chaque preview/download/view client). On borne
 * ici le pool et on rejette proprement en cas de saturation : le tracage est
 * best-effort, il ne doit jamais bloquer ni epuiser les threads du service.
 */
@Configuration
public class ClientAccessLogAsyncConfig {

    @Bean("clientAccessLogExecutor")
    public TaskExecutor clientAccessLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("client-access-log-");
        // File pleine -> on jette la tache silencieusement (best-effort, jamais
        // de blocage du thread requete via CallerRunsPolicy).
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
