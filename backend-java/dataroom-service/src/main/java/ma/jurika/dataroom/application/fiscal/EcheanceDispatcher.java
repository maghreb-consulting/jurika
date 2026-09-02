package ma.jurika.dataroom.application.fiscal;

import ma.jurika.dataroom.domain.port.AccountantNotifier;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * RG-DF20 -- dispatch d'une echeance J-15 : email comptable + WebSocket realtime.
 * Best-effort, ne bloque jamais le scheduler.
 */
@Component
public class EcheanceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EcheanceDispatcher.class);

    private final SettingsJpaRepository settings;
    private final DossierViewJpaRepository dossiers;
    private final AccountantNotifier notifier;
    private final DataroomEventPublisher publisher;

    public EcheanceDispatcher(SettingsJpaRepository settings,
                               DossierViewJpaRepository dossiers,
                               AccountantNotifier notifier,
                               DataroomEventPublisher publisher) {
        this.settings = settings;
        this.dossiers = dossiers;
        this.notifier = notifier;
        this.publisher = publisher;
    }

    public void dispatch(AlerteEcheanceEntity a) {
        try {
            var s = settings.findById(a.getDossierId()).orElse(null);
            String raisonSociale = dossiers.findById(a.getDossierId())
                    .map(d -> d.getRaisonSociale()).orElse("dossier " + a.getDossierId());
            if (s != null && s.getAccountantEmail() != null && !s.getAccountantEmail().isBlank()
                    && s.isNotifyAccountantOnUpload()) {
                // RG-DF21 reutilise la prefixation FISCAL: pour identifier l alerte
                notifier.notifyFiscalUpload(s.getAccountantEmail(), a.getDossierId(), raisonSociale,
                        (short) a.getDateEcheance().getYear(),
                        "ECHEANCE", a.getTypeEcheance(),
                        "alerte_J-15_" + a.getDateEcheance(),
                        null);
            }
        } catch (Exception ex) {
            log.warn("Dispatch echeance {} email echec : {}", a.getId(), ex.getMessage());
        }
        try {
            if (publisher != null) {
                publisher.publish(new DataroomEventPublisher.DataroomDocumentEvent(
                        DataroomEventPublisher.Kind.UPLOADED,
                        a.getWorkspaceId(), a.getDossierId(), a.getId(),
                        "FISCAL_ECHEANCE",
                        a.getTypeEcheance() + " due " + a.getDateEcheance(),
                        null, Instant.now()));
            }
        } catch (Exception ex) {
            log.warn("Dispatch echeance {} websocket echec : {}", a.getId(), ex.getMessage());
        }
    }
}
