package ma.jurika.auth.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Lot L6 (E5) : sans adresse d'expediteur configuree (SMTP_FROM), aucun courriel ne part et
 * l'erreur le dit. Avant, l'expediteur retombait sur noreply@jurika.ma, domaine non deploye
 * (refuse par le fournisseur, ou distribue sans authentification).
 */
class SmtpEmailSenderExpediteurTest {

    @Test
    void sans_expediteur_configure_rien_ne_part_et_l_erreur_le_dit() {
        JavaMailSender mail = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mail, mock(SpringTemplateEngine.class), "  ", "JURIKA", "");
        assertThatThrownBy(() -> sender.sendTemplated("client@example.com", "Sujet", "welcome", Map.of()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("SMTP_FROM");
        verifyNoInteractions(mail);
    }
}
