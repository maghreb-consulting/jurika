package ma.jurika.dataroom.infrastructure.messaging;

import ma.jurika.common.courriel.CourrielClientEvenement;
import ma.jurika.dataroom.domain.port.CourrielsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lot L5 (E9, RG-COM-09) : publie la demande de courriel au client sur
 * l'echange {@code jurika.events} (cle {@code courriel.client.<type>}),
 * consommee par auth-service.
 *
 * <p>Publiee APRES la validation de la transaction metier : un courriel
 * n'annonce jamais une requete ou un document que l'annulation de la
 * transaction aurait fait disparaitre. Un echec de publication est journalise
 * en ERROR (jamais avale en silence) ; il ne defait pas le geste metier.
 */
@Component
@ConditionalOnBean(RabbitTemplate.class)
@Primary
public class RabbitCourrielsClient implements CourrielsClient {

    private static final Logger log = LoggerFactory.getLogger(RabbitCourrielsClient.class);

    private final RabbitTemplate template;

    public RabbitCourrielsClient(@Qualifier("dataroomEventsRabbitTemplate") RabbitTemplate template) {
        this.template = template;
    }

    @Override
    public void demander(CourrielClientEvenement evenement) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publier(evenement);
                }
            });
        } else {
            publier(evenement);
        }
    }

    void publier(CourrielClientEvenement evenement) {
        try {
            template.convertAndSend(CourrielClientEvenement.ECHANGE, evenement.routage(), evenement);
        } catch (AmqpException ex) {
            log.error("Courriel client NON demande ({} cabinet={} client={} dossier={}) : {}",
                    evenement.type(), evenement.workspaceId(), evenement.clientId(), evenement.dossierId(),
                    ex.getMessage(), ex);
        }
    }
}
