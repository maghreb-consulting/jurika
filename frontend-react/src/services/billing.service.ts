import { api } from '../lib/api';
import type {
  BillingPeriod,
  ChangePlanRequest,
  ChangePlanResponse,
  ChangePlanPreviewResponse,
  CheckoutSessionRequest,
  CheckoutSessionResponse,
  ContactSalesRequest,
  ContactSalesResponse,
  CustomerPortalDto,
  InvoicesPageDto,
  PaymentMethod,
  PaymentsListDto,
  PreparePaymentRequest,
  PreparePaymentResponse,
  PaymentDto,
  PricingResponse,
  SubscriptionDto,
  UsageSnapshot,
} from '../types/billing';

/**
 * Sprint 12 — client billing-service.
 *
 * <p>Endpoints (gateway -> billing-service port 8090) :
 *  - GET    /api/v1/billing/subscription
 *  - GET    /api/v1/billing/invoices?page&size
 *  - POST   /api/v1/billing/checkout-session
 *  - POST   /api/v1/billing/customer-portal
 *  - POST   /api/v1/billing/contact-sales
 *
 * <p>404 sur /subscription = workspace n'a pas encore souscrit (cas trial).
 */
export const billingService = {
  async getSubscription(): Promise<SubscriptionDto | null> {
    try {
      const { data } = await api.get<SubscriptionDto>('/billing/subscription');
      return data;
    } catch (err) {
      if (isStatus(err, 404)) return null;
      throw err;
    }
  },

  async listInvoices(page = 0, size = 20): Promise<InvoicesPageDto> {
    const { data } = await api.get<InvoicesPageDto>('/billing/invoices', {
      params: { page, size },
    });
    return data;
  },

  invoiceDownloadUrl(invoiceId: number): string {
    return `${api.defaults.baseURL ?? ''}/billing/invoices/${invoiceId}/download`;
  },

  async createCheckoutSession(req: CheckoutSessionRequest): Promise<CheckoutSessionResponse> {
    const { data } = await api.post<CheckoutSessionResponse>('/billing/checkout-session', req);
    return data;
  },

  async createCustomerPortal(): Promise<CustomerPortalDto> {
    const { data } = await api.post<CustomerPortalDto>('/billing/customer-portal', {});
    return data;
  },

  async contactSales(req: ContactSalesRequest): Promise<ContactSalesResponse> {
    const { data } = await api.post<ContactSalesResponse>('/billing/contact-sales', req);
    return data;
  },

  /**
   * Sprint Beta (pricing-deploy) — Recupere le catalogue plans public
   * (Essentiel/Business/Entreprise + quotas + lookup_keys Stripe — spec
   * directeur 2026-06-02). Endpoint non-authentifie servi par auth-service
   * depuis PlanCatalog (source unique de verite).
   */
  async getPricing(): Promise<PricingResponse> {
    const { data } = await api.get<PricingResponse>('/public/pricing');
    return data;
  },

  /**
   * Sprint Beta (pricing-deploy) — Snapshot consommation plan
   * (utilisateurs, dossiers, stockage). 204 si enforcement coupe.
   */
  async getUsage(): Promise<UsageSnapshot | null> {
    try {
      const { data, status } = await api.get<UsageSnapshot>('/workspace/usage');
      if (status === 204) return null;
      return data;
    } catch (err) {
      if (isStatus(err, 404) || isStatus(err, 204)) return null;
      throw err;
    }
  },

  // ─── BUG 8 (2026-06-07) — Change plan ────────────────────────────────
  async changePlan(req: ChangePlanRequest): Promise<ChangePlanResponse> {
    const { data } = await api.post<ChangePlanResponse>('/billing/change-plan', req);
    return data;
  },

  async previewChangePlan(req: ChangePlanRequest): Promise<ChangePlanPreviewResponse> {
    const { data } = await api.post<ChangePlanPreviewResponse>('/billing/change-plan/preview', req);
    return data;
  },

  // ─── BUG 14 (2026-06-07) — Multi-method payments ────────────────────
  async preparePayment(req: PreparePaymentRequest): Promise<PreparePaymentResponse> {
    const { data } = await api.post<PreparePaymentResponse>('/billing/prepare-payment', req);
    return data;
  },

  async listPayments(): Promise<PaymentsListDto> {
    const { data } = await api.get<PaymentsListDto>('/billing/payments');
    return data;
  },

  async validatePayment(paymentId: number, notes?: string): Promise<PaymentDto> {
    const { data } = await api.patch<PaymentDto>(`/billing/payments/${paymentId}/validate`,
      { notes: notes ?? null });
    return data;
  },
};

// Re-export pour le compose helper
export type { BillingPeriod, PaymentMethod };

function isStatus(err: unknown, status: number): boolean {
  const e = err as { response?: { status?: number } };
  return e?.response?.status === status;
}
