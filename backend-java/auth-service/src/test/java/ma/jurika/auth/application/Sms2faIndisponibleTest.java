package ma.jurika.auth.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.SmsOtpCodeRepository;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.auth.infrastructure.sms.LoggerSmsSender;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.observability.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Recette R2 (2026-10-17) : en production sans fournisseur de SMS, l'option SMS est refusee par
 * le serveur a chaque entree (choix de la methode, envoi d'un code, verification d'un code),
 * avec un refus explicite (SMS_2FA_INDISPONIBLE) ; aucun SMS n'est simule, rien n'est ecrit en base.
 */
class Sms2faIndisponibleTest {

    private final SmsSender envoi = mock(SmsSender.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SmsOtpCodeRepository otps = mock(SmsOtpCodeRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private static MockEnvironment prod() {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles("prod");
        return e;
    }

    /** Production, propriete de simulation levee par erreur, envoi simule : indisponible. */
    private final DisponibiliteSms2fa refus = new DisponibiliteSms2fa(
            new LoggerSmsSender(new BusinessMetrics(new SimpleMeterRegistry())), prod(), true);

    @SuppressWarnings("unchecked")
    private SendSmsOtpUseCase envoiCode() {
        return new SendSmsOtpUseCase(users, otps, new VerificationTokenHasher(), envoi, audit, refus,
                mock(ControleMethodeActuelle.class), mock(ObjectProvider.class), 5, 5, 6);
    }

    @Test
    void le_choix_du_sms_est_refuse() {
        Choose2faMethodUseCase choix = new Choose2faMethodUseCase(audit, refus);
        assertThatThrownBy(() -> choix.execute(new Choose2faMethodUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), "SMS", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SMS_2FA_INDISPONIBLE");
        verifyNoInteractions(audit);
    }

    @Test
    void l_envoi_d_un_code_est_refuse_sans_rien_simuler() {
        for (String objet : new String[] {"2FA_SETUP", "2FA_LOGIN", "PHONE_VERIFICATION"}) {
            assertThatThrownBy(() -> envoiCode().execute(new SendSmsOtpUseCase.Command(
                    UUID.randomUUID(), UUID.randomUUID(), objet, "+212611223344", null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "SMS_2FA_INDISPONIBLE");
        }
        verify(envoi, never()).send(anyString(), anyString());
        verifyNoInteractions(otps);
    }

    @Test
    void la_verification_d_un_code_est_refusee() {
        VerifySmsOtpUseCase verif = new VerifySmsOtpUseCase(users, otps, new VerificationTokenHasher(), audit,
                mock(TokenIssuer.class), mock(RefreshTokenRepository.class), mock(EventPublisher.class), refus, 5, 15);
        assertThatThrownBy(() -> verif.execute(new VerifySmsOtpUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), "2FA_SETUP", "123456", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SMS_2FA_INDISPONIBLE");
        verifyNoInteractions(otps);
        verify(users, never()).updateTwofaMethod(any(), any());
    }
}
