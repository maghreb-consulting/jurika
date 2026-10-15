package ma.jurika.auth.application.essai;

import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L7 (E4) : rappels J-3 et J-1 (RG-ESS-08), lecture seule et date de suppression (RG-ESS-10). */
class EtatEssaiTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-14T10:00:00Z");

    private static WorkspaceEntity essai(Duration resteAvantFin) {
        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setTrialStatus("TRIAL_ACTIVE");
        ws.setTrialStartedAt(MAINTENANT.plus(resteAvantFin).minus(Duration.ofDays(7)));
        ws.setTrialEndsAt(MAINTENANT.plus(resteAvantFin));
        return ws;
    }

    @Test
    void pas_de_rappel_au_debut_de_l_essai() {
        EtatEssai e = EtatEssai.de(essai(Duration.ofDays(6)), MAINTENANT);
        assertThat(e.enEssai()).isTrue();
        assertThat(e.joursRestants()).isEqualTo(6);
        assertThat(e.rappel()).isNull();
        assertThat(e.limites().dossiers()).isEqualTo(3);
        assertThat(e.limites().employes()).isEqualTo(1);
        assertThat(e.limites().stockageOctets()).isEqualTo(500L * 1024 * 1024);
        assertThat(e.limites().questionsChatbot()).isEqualTo(20);
        assertThat(e.limites().forfait()).isEqualTo("PROFESSIONNEL");
    }

    @Test
    void rappel_j3_puis_j1() {
        assertThat(EtatEssai.de(essai(Duration.ofDays(3)), MAINTENANT).rappel()).isEqualTo("J-3");
        assertThat(EtatEssai.de(essai(Duration.ofHours(49)), MAINTENANT).rappel()).isEqualTo("J-3");
        assertThat(EtatEssai.de(essai(Duration.ofHours(24)), MAINTENANT).rappel()).isEqualTo("J-1");
        assertThat(EtatEssai.de(essai(Duration.ofHours(2)), MAINTENANT).rappel()).isEqualTo("J-1");
        assertThat(EtatEssai.de(essai(Duration.ofDays(3).plusHours(1)), MAINTENANT).rappel()).isNull();
    }

    @Test
    void lecture_seule_porte_la_date_de_suppression() {
        WorkspaceEntity ws = essai(Duration.ofDays(-2));
        ws.setTrialStatus("TRIAL_EXPIRED");
        ws.setEssaiExpireLe(MAINTENANT.minus(Duration.ofDays(2)));
        EtatEssai e = EtatEssai.de(ws, MAINTENANT);
        assertThat(e.lectureSeule()).isTrue();
        assertThat(e.rappel()).isNull();
        assertThat(e.suppressionLe()).isEqualTo(MAINTENANT.plus(Duration.ofDays(28)));
    }

    @Test
    void cabinet_abonne_hors_essai() {
        WorkspaceEntity ws = essai(Duration.ofDays(2));
        ws.setTrialStatus("CONVERTED");
        EtatEssai e = EtatEssai.de(ws, MAINTENANT);
        assertThat(e.enEssai()).isFalse();
        assertThat(e.limites()).isNull();
        assertThat(e.rappel()).isNull();
    }
}
