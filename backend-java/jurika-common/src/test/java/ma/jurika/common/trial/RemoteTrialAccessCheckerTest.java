package ma.jurika.common.trial;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lot L7 (E4) : etat d'essai distant, cache de 60 s, aucune supposition en cas de panne. */
class RemoteTrialAccessCheckerTest {

    private static final UUID WS = UUID.randomUUID();

    private static WorkspaceStatusFeignClient.WorkspaceStatusResponse reponse(String essai, String abonnement) {
        return new WorkspaceStatusFeignClient.WorkspaceStatusResponse(WS, essai, 2, Instant.now(), null,
                abonnement, null);
    }

    static final class Horloge extends Clock {
        final AtomicReference<Instant> t = new AtomicReference<>(Instant.parse("2026-10-14T10:00:00Z"));
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return t.get(); }
    }

    @Test
    void etat_mis_en_cache_60_secondes() {
        AtomicInteger appels = new AtomicInteger();
        Horloge h = new Horloge();
        RemoteTrialAccessChecker c = new RemoteTrialAccessChecker(ws -> {
            appels.incrementAndGet();
            return Optional.of(reponse("TRIAL_ACTIVE", null));
        }, h);
        assertThat(c.getState(WS)).get().extracting(TrialAccessChecker.TrialState::enEssai).isEqualTo(true);
        c.getState(WS);
        assertThat(appels).hasValue(1);
        h.t.updateAndGet(i -> i.plus(Duration.ofSeconds(61)));
        c.getState(WS);
        assertThat(appels).hasValue(2);
    }

    @Test
    void panne_d_auth_leve_une_exception_jamais_mise_en_cache() {
        AtomicInteger appels = new AtomicInteger();
        RemoteTrialAccessChecker c = new RemoteTrialAccessChecker(ws -> {
            if (appels.incrementAndGet() == 1) throw new EtatEssaiIndisponibleException("panne", null);
            return Optional.of(reponse("TRIAL_EXPIRED", null));
        }, new Horloge());
        assertThatThrownBy(() -> c.getState(WS)).isInstanceOf(EtatEssaiIndisponibleException.class);
        assertThat(c.getState(WS)).get().extracting(TrialAccessChecker.TrialState::isExpired).isEqualTo(true);
    }

    @Test
    void abonnement_actif_leve_l_essai_et_cabinet_sans_essai() {
        assertThat(RemoteTrialAccessChecker.mapStatus(reponse("TRIAL_EXPIRED", "active"))).isEqualTo("CONVERTED");
        assertThat(RemoteTrialAccessChecker.mapStatus(reponse("TRIAL_ACTIVE", null))).isEqualTo("TRIAL_ACTIVE");
        assertThat(RemoteTrialAccessChecker.mapStatus(reponse(null, null))).isNull();
        RemoteTrialAccessChecker c = new RemoteTrialAccessChecker(ws -> Optional.of(reponse(null, null)), new Horloge());
        assertThat(c.getState(WS)).get().extracting(TrialAccessChecker.TrialState::enEssai).isEqualTo(false);
        RemoteTrialAccessChecker inconnu = new RemoteTrialAccessChecker(ws -> Optional.empty(), new Horloge());
        assertThat(inconnu.getState(WS)).isEmpty();
    }
}
