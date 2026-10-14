package ma.jurika.billing.application;

import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Lot L7 : la demande de devis (Sur Devis) ne part jamais vers un domaine non deploye
 * (contact@jurika.ai etait le defaut). Destinataire : BILLING_SALES_EMAIL, a defaut l'adresse
 * d'expedition verifiee (SMTP_FROM) ; sans l'une ni l'autre, rien ne part et la reponse le dit.
 */
class ContactSalesDestinataireTest {

    private static BillingDtos.ContactSalesRequest demande() {
        return new BillingDtos.ContactSalesRequest("Cabinet Atlas", "contact@atlas.example", "Salma", "0522000000", "Devis");
    }

    @Test
    void aucune_adresse_commerciale_par_defaut() {
        assertThat(new BillingProperties().getSalesEmail()).isEmpty();
    }

    @Test
    void sans_adresse_commerciale_la_demande_part_a_l_expediteur_verifie() {
        JavaMailSender mail = mock(JavaMailSender.class);
        BillingProperties props = new BillingProperties();
        props.setFromEmail("expediteur-verifie@example.com");
        BillingDtos.ContactSalesResponse r = new ContactSalesUseCase(mail, props).send(UUID.randomUUID(), demande());
        ArgumentCaptor<SimpleMailMessage> m = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(m.capture());
        assertThat(m.getValue().getTo()).containsExactly("expediteur-verifie@example.com");
        assertThat(r.accepted()).isTrue();
    }

    @Test
    void sans_aucune_adresse_rien_ne_part_et_la_reponse_le_dit() {
        JavaMailSender mail = mock(JavaMailSender.class);
        BillingDtos.ContactSalesResponse r = new ContactSalesUseCase(mail, new BillingProperties()).send(UUID.randomUUID(), demande());
        verifyNoInteractions(mail);
        assertThat(r.accepted()).isFalse();
        assertThat(r.message()).doesNotContain("@");
    }
}
