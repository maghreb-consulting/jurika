package ma.jurika.auth.infrastructure.messaging;

import ma.jurika.auth.application.EnvoyerCourrielClientUseCase;
import ma.jurika.common.courriel.CourrielClientEvenement;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Lot L5 (E9, RG-COM-09) : consomme les demandes de courriel au client publiees
 * par les autres services (dataroom) et les envoie par le port EmailSender.
 *
 * <p>Echec (refus du destinataire, relais SMTP injoignable...) : journalise en
 * ERROR, puis message rejete SANS remise en file -- il part dans la file
 * d'echecs ({@link CourrielsClientRabbitConfig#FILE_ECHECS}). Jamais avale en
 * silence, jamais rejoue en boucle.
 */
@Component
public class CourrielsClientListener {

    private static final Logger log = LoggerFactory.getLogger(CourrielsClientListener.class);

    private final EnvoyerCourrielClientUseCase useCase;

    public CourrielsClientListener(EnvoyerCourrielClientUseCase useCase) {
        this.useCase = useCase;
    }

    @RabbitListener(queues = CourrielsClientRabbitConfig.FILE)
    public void recevoir(CourrielClientEvenement evenement) {
        traiter(evenement);
    }

    /** Separe de l'annotation pour etre teste sans courtier. */
    public void traiter(CourrielClientEvenement evenement) {
        try {
            // Le workspace est pose AVANT la transaction du cas d'usage (RLS).
            TenantContext.set(evenement == null ? null : evenement.workspaceId());
            useCase.envoyer(evenement);
        } catch (Exception ex) {
            log.error("Courriel client NON envoye ({}) : {} -- message range dans {}",
                    EnvoyerCourrielClientUseCase.resume(evenement), ex.getMessage(),
                    CourrielsClientRabbitConfig.FILE_ECHECS, ex);
            throw new AmqpRejectAndDontRequeueException("Courriel client non envoye : " + ex.getMessage(), ex);
        } finally {
            TenantContext.clear();
        }
    }
}
