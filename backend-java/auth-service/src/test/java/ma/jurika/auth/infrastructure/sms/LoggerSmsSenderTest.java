package ma.jurika.auth.infrastructure.sms;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.common.observability.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette R2 (2026-10-17) : l'envoi simule n'envoie rien ; le SMS (et donc le code) est ecrit
 * dans le journal d'auth avec la marque SMS-DEV-STUB, et l'implementation se declare simulee
 * (DisponibiliteSms2fa ne l'admet qu'en environnement de test).
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggerSmsSenderTest {

    @Test
    void ecrit_le_sms_dans_le_journal_avec_la_marque_et_se_declare_simule(CapturedOutput sortie) {
        LoggerSmsSender envoi = new LoggerSmsSender(new BusinessMetrics(new SimpleMeterRegistry()));
        envoi.send("+212611223344", "JURIKA : votre code de verification est 123456. Valable 5 minutes.");
        assertThat(sortie.getAll()).contains("[SMS-DEV-STUB] -> +212611223344 : JURIKA : votre code de verification est 123456");
        assertThat(envoi.simule()).isTrue();
    }
}
