package ma.jurika.auth.infrastructure.donneesdetest;

import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recette R1 : capture des messages pour les parcours. Le message part toujours (meme s'il est
 * retenu) ; seuls les courriels du domaine de test sont retenus ; la route ne rend un SMS que pour
 * le telephone d'une demande d'essai faite avec l'adresse de test demandee.
 */
class CaptureMessagesDeTestTest {

    private final CaptureMessagesDeTest capture = new CaptureMessagesDeTest();

    @Test
    void le_courriel_part_et_seul_le_domaine_de_test_est_retenu() {
        EmailSender reel = mock(EmailSender.class);
        EmailSender enveloppe = (EmailSender) capture.postProcessAfterInitialization(reel, "email");
        Map<String, Object> vars = new HashMap<>();
        vars.put("workspaceCode", "JUR-ABCDE");
        vars.put("temporaryPassword", "Aa9$kL2p#Mq8");
        vars.put("objet", new Object());
        enveloppe.sendTemplated("Essai-1@Jurika.test", "Bienvenue", "welcome", vars);
        enveloppe.sendTemplated("karim@cabinet.ma", "Bienvenue", "welcome", vars);

        verify(reel).sendTemplated(eq("Essai-1@Jurika.test"), anyString(), eq("welcome"), eq(vars));
        verify(reel).sendTemplated(eq("karim@cabinet.ma"), anyString(), eq("welcome"), eq(vars));
        List<CaptureMessagesDeTest.Courriel> retenus = capture.courrielsPour("essai-1@jurika.test");
        assertThat(retenus).hasSize(1);
        assertThat(retenus.get(0).variables()).containsEntry("workspaceCode", "JUR-ABCDE")
                .containsEntry("temporaryPassword", "Aa9$kL2p#Mq8").doesNotContainKey("objet");
        assertThat(capture.courrielsPour("karim@cabinet.ma")).isEmpty();
    }

    @Test
    void le_sms_part_et_reste_lisible_meme_si_l_envoi_echoue() {
        SmsSender enPanne = (t, b) -> { throw new IllegalStateException("fournisseur injoignable"); };
        SmsSender enveloppe = (SmsSender) capture.postProcessAfterInitialization(enPanne, "sms");
        assertThatThrownBy(() -> enveloppe.send("+212600000002", "code 654321"))
                .hasMessageContaining("injoignable");
        assertThat(capture.smsPour("+212600000002")).extracting(CaptureMessagesDeTest.Sms::texte)
                .containsExactly("code 654321");
    }

    @Test
    void la_route_refuse_une_adresse_reelle_et_rend_les_sms_de_la_demande() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("essai-2@jurika.test")))
                .thenReturn(List.of("+212600000003"));
        MessagesDeTestController route = new MessagesDeTestController(capture, jdbc);
        ((SmsSender) capture.postProcessAfterInitialization((SmsSender) (t, b) -> { }, "sms"))
                .send("+212600000003", "code 111222");
        ((SmsSender) capture.postProcessAfterInitialization((SmsSender) (t, b) -> { }, "sms"))
                .send("+212600000004", "code 999888");

        assertThatThrownBy(() -> route.messages("abdel@gmail.com")).isInstanceOf(ValidationException.class);
        Map<String, Object> r = route.messages("Essai-2@jurika.test");
        assertThat((List<?>) r.get("sms")).extracting(s -> ((CaptureMessagesDeTest.Sms) s).texte())
                .containsExactly("code 111222");
    }

    /**
     * Recette (2026-10-17) : l'enveloppe de capture cachait le caractere simule de l'envoi
     * (SmsSender.simule() par defaut = faux) : sur la pile de test, la double authentification par
     * SMS se croyait branchee a un vrai fournisseur ("ouverte (fournisseur configure)") et l'ecran
     * ne disait plus que le SMS est simule.
     */
    @Test
    void l_enveloppe_garde_le_caractere_simule_de_l_envoi() {
        SmsSender simule = mock(SmsSender.class);
        when(simule.simule()).thenReturn(true);
        SmsSender reel = mock(SmsSender.class);
        when(reel.simule()).thenReturn(false);
        assertThat(((SmsSender) capture.postProcessAfterInitialization(simule, "sms")).simule()).isTrue();
        assertThat(((SmsSender) capture.postProcessAfterInitialization(reel, "sms2")).simule()).isFalse();
    }
}
