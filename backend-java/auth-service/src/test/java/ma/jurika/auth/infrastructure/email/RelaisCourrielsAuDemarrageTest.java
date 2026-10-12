package ma.jurika.auth.infrastructure.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelaisCourrielsAuDemarrageTest {

    @Test
    void le_resume_nomme_le_relais_sans_identifiant() {
        String r = RelaisCourrielsAuDemarrage.resume("smtp", "smtp-relay.brevo.com", 587, true, true, "noreply@jurika.ma");
        assertThat(r).isEqualTo("Courriels : relais SMTP smtp-relay.brevo.com:587, authentifie, STARTTLS, expediteur noreply@jurika.ma");
    }

    @Test
    void un_relais_local_est_signale() {
        assertThat(RelaisCourrielsAuDemarrage.relaisLocal("smtp", "localhost")).isTrue();
        assertThat(RelaisCourrielsAuDemarrage.relaisLocal("smtp", "")).isTrue();
        assertThat(RelaisCourrielsAuDemarrage.relaisLocal("smtp", "mailhog")).isFalse();
        assertThat(RelaisCourrielsAuDemarrage.relaisLocal("smtp", "email-smtp.eu-west-3.amazonaws.com")).isFalse();
        assertThat(RelaisCourrielsAuDemarrage.relaisLocal("log", "localhost")).isFalse();
    }

    @Test
    void le_fournisseur_log_est_dit_sans_envoi() {
        assertThat(RelaisCourrielsAuDemarrage.resume("log", "localhost", 1025, false, false, "noreply@jurika.ma"))
                .contains("aucun envoi reel");
    }
}
