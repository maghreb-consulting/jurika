/**
 * Sprint 12 — types billing (miroir des records Java BillingDtos).
 */
export type SubscriptionStatus =
  | 'active'
  | 'past_due'
  | 'cancelled'
  | 'trialing'
  | 'incomplete'
  | 'incomplete_expired'
  | 'unpaid';

export interface PaymentMethodDto {
  brand: string | null;
  last4: string | null;
  expMonth: number | null;
  expYear: number | null;
}

export interface SubscriptionDto {
  planCode: string;
  status: SubscriptionStatus;
  currentPeriodStart: string; // ISO
  currentPeriodEnd: string;
  cancelAtPeriodEnd: boolean;
  cancelledAt: string | null;
  paymentMethod: PaymentMethodDto | null;
}

export interface InvoiceDto {
  id: number;
  number: string;
  amountTtcCents: number;
  amountHtCents: number;
  amountTvaCents: number;
  currency: string;
  status: 'paid' | 'open' | 'void' | 'uncollectible' | 'draft';
  issuedAt: string;
  paidAt: string | null;
  invoicePdfUrl: string | null;
  hostedInvoiceUrl: string | null;
}

export interface InvoicesPageDto {
  items: InvoiceDto[];
  total: number;
  page: number;
  size: number;
}

/**
 * Plan codes canoniques (spec directeur 2026-06-02). Alignes avec
 * `workspace.selected_plan` post-migration auth V23 / billing V3
 * (CHECK: 'essentiel' | 'business' | 'entreprise').
 *
 * Les codes pre-2026-06-02 sont rabattus en DB par les migrations
 * auth V23 / billing V3 et toleres cote backend via PlanCatalog.normalize.
 * Le frontend ne doit jamais en produire.
 *
 * `entreprise` est rejete au checkout (RG-BL10 — utilise /contact-sales).
 */
export type PlanCode = 'essentiel' | 'business' | 'entreprise';
export type CheckoutPlanCode = Exclude<PlanCode, 'entreprise'>;

/**
 * Sprint Beta (pricing-deploy) — periode de facturation pour le checkout.
 * 'monthly' par defaut (retro-compat Sprint 12).
 */
export type BillingPeriod = 'monthly' | 'yearly';

export interface CheckoutSessionRequest {
  planCode: CheckoutPlanCode;
  contactEmail: string;
  workspaceName: string;
  billingPeriod?: BillingPeriod;
}

export interface CheckoutSessionResponse {
  checkoutSessionId: string;
  checkoutUrl: string;
  planCode: string;
  billingPeriod?: BillingPeriod;
}

/**
 * Sprint Beta — DTO renvoye par GET /api/v1/public/pricing.
 * Source : auth-service PublicPricingController qui lit PlanCatalog.
 */
export interface PricingQuotas {
  maxUsers: number;
  maxDossiers: number;
  maxStorageGb: number;
  usersUnlimited: boolean;
  dossiersUnlimited: boolean;
  storageUnlimited: boolean;
}

export interface PricingPeriodSlice {
  priceMad: number;
  stripeLookupKey: string;
}

export interface PricingTier {
  id: string;            // workspace.selected_plan
  code: string;
  label: string;         // 'Essentiel' | 'Business' | 'Entreprise' (spec 2026-06-02)
  target: string;
  currency: 'MAD';
  price: number | null;        // legacy mensuel
  annualPrice: number | null;  // legacy annuel
  period: string;
  monthly?: PricingPeriodSlice;
  yearly?: PricingPeriodSlice;
  quotas: PricingQuotas;
  features: string[];
  featured: boolean;
  ribbon?: string;
  quoteOnly: boolean;
}

export interface PricingResponse {
  tiers: PricingTier[];
  currency: 'MAD';
  trialDays: number;
}

export interface CustomerPortalDto {
  url: string;
}

export interface ContactSalesRequest {
  workspaceName: string;
  contactEmail: string;
  contactName: string;
  phone: string;
  message: string;
}

export interface ContactSalesResponse {
  accepted: boolean;
  message: string;
}

/**
 * Sprint Beta (pricing-deploy) — Reponse de GET /api/v1/workspace/usage.
 * Sert au composant <PlanUsageBadge>.
 */
export interface UsageSnapshot {
  planCode: string;
  planLabel: string;
  users: number;
  maxUsers: number;
  usersUnlimited: boolean;
  dossiers: number;
  maxDossiers: number;
  dossiersUnlimited: boolean;
  storageBytes: number;
  maxStorageGb: number;
  storageUnlimited: boolean;
}

/** Format un montant en centimes MAD vers une chaine "1 299,00 MAD". */
export function formatMadCents(cents: number): string {
  const mad = cents / 100;
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'MAD',
    minimumFractionDigits: 2,
  }).format(mad);
}

// ────────────────────────────────────────────────────────────────────────
// BUG 8 (2026-06-07) — Change plan
// ────────────────────────────────────────────────────────────────────────
export interface ChangePlanRequest {
  targetPlanCode: PlanCode;
  billingPeriod: BillingPeriod;
}

export interface ChangePlanResponse {
  previousPlanCode: string;
  newPlanCode: string;
  billingPeriod: string;
  stripeSubscriptionId: string;
  currentPeriodEnd: string;
}

export interface ChangePlanPreviewResponse {
  fromPlan: string;
  toPlan: string;
  billingPeriod: string;
  isUpgrade: boolean;
  isDowngrade: boolean;
  downgradeAllowed: boolean;
  blockedReason: string | null;
  fromPriceMad: number | null;
  toPriceMad: number | null;
}

// ────────────────────────────────────────────────────────────────────────
// BUG 14 (2026-06-07) — Multi-method payments
// ────────────────────────────────────────────────────────────────────────
export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'CHEQUE' | 'CASH';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface PreparePaymentRequest {
  planCode: CheckoutPlanCode;
  billingPeriod: BillingPeriod;
  method: PaymentMethod;
  contactEmail: string;
  workspaceName: string;
  reference?: string;
}

export interface PaymentInstructionDto {
  key: string;
  value: string;
}

export interface PreparePaymentResponse {
  paymentId: number;
  method: PaymentMethod;
  status: PaymentStatus;
  planCode: string;
  billingPeriod: string;
  amountMadCents: number;
  currency: string;
  checkoutUrl: string | null;
  instructions: PaymentInstructionDto[];
  userMessage: string;
}

export interface PaymentDto {
  id: number;
  planCode: string;
  billingPeriod: string;
  amountMadCents: number;
  currency: string;
  method: PaymentMethod;
  status: PaymentStatus;
  stripePaymentId: string | null;
  bankReference: string | null;
  chequeNumber: string | null;
  chequeDate: string | null;
  proofUrl: string | null;
  createdAt: string;
  completedAt: string | null;
  validatedBy: string | null;
  notes: string | null;
}

export interface PaymentsListDto {
  items: PaymentDto[];
}
