package ma.jurika.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"ma.jurika.ticket", "ma.jurika.common"})
@EnableDiscoveryClient
// 2026-07-02 fix — scanne aussi ma.jurika.common.trial pour enregistrer
// WorkspaceStatusFeignClient, requis par FeignMemberDirectory (transfert de
// dossier). Ce client n'etait sinon publie que par TrialRemoteAutoConfiguration
// (jurika-common), inactive dans ticket-service faute de Caffeine au classpath
// (concept trial retire de ce service). On l'enregistre donc directement ici.
@EnableFeignClients(basePackages = {"ma.jurika.ticket", "ma.jurika.common.trial"})
// Lot L5 (E9, RG-COM-08) : detection planifiee des delais a echeance (AlertesDelaisJob).
@EnableScheduling
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
