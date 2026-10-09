package ma.jurika.billing.application;

import ma.jurika.billing.application.workspace.WorkspaceStatusUpdater;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L0, etape E17 (RG-PAY-02) : le super-admin valide le paiement hors ligne
 * d'un cabinet. Le paiement est retrouve par son identifiant, et c'est le
 * workspace DU PAIEMENT (le cabinet qui a paye) qui est active et recoit ses
 * identifiants -- jamais celui de l'appelant.
 */
class ValidatePaymentUseCaseTest {

    private final PaymentJpaRepository payments = mock(PaymentJpaRepository.class);
    private final SubscriptionJpaRepository subscriptions = mock(SubscriptionJpaRepository.class);
    private final WorkspaceStatusUpdater workspaces = mock(WorkspaceStatusUpdater.class);
    private final ValidatePaymentUseCase useCase = new ValidatePaymentUseCase(payments, subscriptions, workspaces);

    @Test
    void active_le_workspace_du_paiement() {
        UUID cabinet = UUID.randomUUID();
        PaymentEntity p = new PaymentEntity();
        p.setWorkspaceId(cabinet);
        p.setStatus("PENDING");
        p.setMethod("BANK_TRANSFER");
        p.setPlanCode("business");
        when(payments.findById(7L)).thenReturn(Optional.of(p));
        when(subscriptions.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(any(), anyString()))
                .thenReturn(Optional.empty());

        useCase.execute(7L, null);

        assertThat(p.getStatus()).isEqualTo("COMPLETED");
        verify(workspaces).activate(eq(cabinet), eq("business"), any());
        verify(workspaces).issueCredentials(eq(cabinet), anyString());
    }

    @Test
    void paiement_inconnu_introuvable() {
        when(payments.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(8L, null)).isInstanceOf(NotFoundException.class);
    }
}
