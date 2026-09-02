# billing-service — JURIKA Sprint 12

> Microservice de facturation Stripe. Port `8090`. Base PG dédiée `jurika_billing`.
> Sprint 12 — TEST mode uniquement, swap vers production via simple bascule des
> 6 variables d'environnement `STRIPE_*`.

## Périmètre

- **Stripe Checkout (hosted)** : `POST /api/v1/billing/checkout-session` → URL Stripe à laquelle rediriger.
- **Stripe Customer Portal** : `POST /api/v1/billing/customer-portal` → URL Stripe pour update card / cancel / resume.
- **Webhooks Stripe** : `POST /api/v1/billing/webhook/stripe` avec signature HMAC obligatoire (RG-BL18).
- **Lecture** : `GET /api/v1/billing/subscription`, `GET /api/v1/billing/invoices`, `GET /api/v1/billing/invoices/{id}/download`.
- **Enterprise hors Stripe** : `POST /api/v1/billing/contact-sales` (mail à l'équipe ventes — RG-BL10).
- **TVA Maroc 20%** : calculée backend (pas Stripe Tax — risque archi #4). HT = TTC / 1.20.
- **Pattern composition** : `SubscribeUseCase` compose `CreateStripeCustomerUseCase` + `CreateCheckoutSessionUseCase` + `RecordSubscriptionUseCase` (RG-BL17).

## Démarrage local

### 1. Pré-requis Stripe TEST mode

```bash
# Compte Stripe : https://dashboard.stripe.com/register (gratuit, 5 min)
# Bascule le toggle "Test mode" en haut à gauche.
# Crée 2 produits dans Products :
#   - JURIKA Starter   — récurrent mensuel — 49900 centimes MAD
#   - JURIKA Business  — récurrent mensuel — 129900 centimes MAD
# Récupère les Price IDs (price_xxx) et la Secret/Publishable key.
# Stripe CLI pour forward webhooks :
#   stripe listen --forward-to localhost:8090/api/v1/billing/webhook/stripe
# Récupère le whsec_xxx renvoyé.
```

### 2. `.env.local` (jamais commit, gitignored)

```bash
STRIPE_PUBLISHABLE_KEY=pk_test_xxxxxxxx
STRIPE_SECRET_KEY=sk_test_xxxxxxxx
STRIPE_PRICE_STARTER=price_xxxxxxxx
STRIPE_PRICE_BUSINESS=price_xxxxxxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxx
BILLING_DB_NAME=jurika_billing
```

> ⚠️ **Garde-fou** : `StripeConfig.initStripeSdk` détecte les clés `sk_live_*` et logue un WARN tonitruant.
> Sprint 12 doit rester en TEST mode (cf. PLAN contraintes absolues).

### 3. Base PostgreSQL

```sql
-- Sous l'utilisateur postgres (one-shot)
CREATE DATABASE jurika_billing OWNER jurika_user;
```

Les migrations Flyway (`V1__init_billing_schema.sql`) sont jouées au démarrage (4 tables : `subscriptions`, `invoices`, `payment_methods`, `webhook_events`).

### 4. Lancer

```bash
# Depuis projet/backend-java
mvn -pl billing-service spring-boot:run

# Tests
mvn -pl billing-service test

# Coverage (JaCoCo)
mvn -pl billing-service verify
open billing-service/target/site/jacoco/index.html
```

## Endpoints (7)

| Méthode | Path | Rôle requis | RG | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/billing/checkout-session` | `ADMIN_CABINET` | RG-BL03 | Crée Stripe Checkout Session, retourne `{checkoutUrl}`. Front redirige `window.location`. |
| `GET` | `/api/v1/billing/subscription` | `ADMIN_CABINET` | RG-BL08 | Souscription courante + payment method masqué. `404` si pas encore souscrit. |
| `GET` | `/api/v1/billing/invoices?page&size` | `ADMIN_CABINET` | RG-BL08 | Page de factures (par défaut 20, capped 100). |
| `GET` | `/api/v1/billing/invoices/{id}/download` | `ADMIN_CABINET` | RG-BL08 | 302 vers PDF hébergé Stripe. |
| `POST` | `/api/v1/billing/customer-portal` | `ADMIN_CABINET` | RG-BL09 | URL Stripe Customer Portal (update card, cancel, resume). |
| `POST` | `/api/v1/billing/contact-sales` | `ADMIN_CABINET` | RG-BL10 | Lead Enterprise par mail (`BILLING_SALES_EMAIL`). |
| `POST` | `/api/v1/billing/webhook/stripe` | (signature HMAC) | RG-BL18 | Webhook Stripe : signature → persist → dispatch 5 event handlers. |

## Architecture (PLAN §2)

- **Microservice dédié** (pas dans auth-service) — isolation financière + audit + conformité PCI/PSD2 (décision archi #4).
- **Base PG séparée** `jurika_billing` — table flyway `flyway_history_billing` isolée.
- **Stripe Checkout hosted** (pas Elements) — gain 3 jours dev (décision #2).
- **Stripe Customer Portal** hosted pour update card / cancel — gain 1 sem dev (décision #3).
- **Locale `fr`** sur Checkout + Customer Portal (RG-BL16, risque archi #5).
- **TVA backend** : on calcule HT/TVA depuis le TTC reçu de Stripe (RG-BL12).
- **Idempotency keys** sur toutes les mutations Stripe (RG-BL18) — préfixe `jurika_<op>_<sha256_hex16>`.
- **Webhook store-and-forward** : `webhook_events` table avec `UNIQUE stripe_event_id`. 10× même event = 1 invoice (décision #6).
- **Composition** : `SubscribeUseCase` orchestre 3 étapes conceptuelles (`findOrCreateCustomer` + `createCheckoutSession` + `recordPendingSubscription`) — pattern `SignupCabinetUseCase` Sprint 11 (RG-BL17).

## 5 event handlers Stripe (T4 — Strategy pattern dans `StripeEventHandlers.java`)

| Event Stripe | Effet métier | RG |
|---|---|---|
| `checkout.session.completed` | Insert `subscription` row, workspace `active` | RG-BL04 |
| `invoice.payment_succeeded` | Insert `invoice` (TVA 20% backend) + email "facture-disponible" | RG-BL05/12 |
| `invoice.payment_failed` | Subscription `past_due` + email "paiement-echec" | RG-BL06 |
| `customer.subscription.updated` | Sync status + period + `cancel_at_period_end` | — |
| `customer.subscription.deleted` | Status `cancelled`, accès jusqu'à `current_period_end` | RG-BL07 |

## Connexion cross-service (T7)

- **5 services** (`dataroom`, `dashboard`, `supervision`, `ticket`, `workflow`) wirent `TrialSoftLockFilter` (jurika-common Sprint 11) via une **auto-configuration** `TrialRemoteAutoConfiguration` qui :
  - Détecte Feign + Caffeine au classpath
  - Wire un `RemoteTrialAccessChecker` (cache Caffeine 60s) qui appelle `auth-service` `GET /internal/workspaces/{id}/status`
  - Mapping : `cancelled` hors période → `TRIAL_EXPIRED` → filter renvoie `402 Payment Required`
- **billing-service** notifie les transitions via `AuthWorkspaceStatusUpdater` Feign (`PUT /internal/workspaces/{id}/status`).

## Tests

```bash
mvn -pl billing-service test
```

19 tests unit/integration :
- `IdempotencyKeysTest` (4) — stabilité par workspace, distinction par plan, hour bucket
- `StripeServiceTest` (5) — Mockito static mock du SDK, vérifie idempotency key + metadata, rejet enterprise
- `StripeWebhookDispatcherTest` (5) — 10× même event = 1 save + 1 handle, race conditions, handler exception
- `SubscribeUseCaseTest` (5) — composition, rejets enterprise/already-active, reuse customer

4 specs Playwright **skipables** via `E2E_BILLING_STRIPE_READY=true` (cf. `frontend-react/e2e/billing-*.spec.ts`).

## Variables d'environnement (`.env.example`)

| Variable | Défaut | Description |
|---|---|---|
| `STRIPE_PUBLISHABLE_KEY` | `pk_test_PLACEHOLDER` | Clé publique TEST |
| `STRIPE_SECRET_KEY` | `sk_test_PLACEHOLDER` | Clé secrète TEST (server-side) |
| `STRIPE_PRICE_STARTER` | `price_PLACEHOLDER_starter` | Price ID Stripe Starter |
| `STRIPE_PRICE_BUSINESS` | `price_PLACEHOLDER_business` | Price ID Stripe Business |
| `STRIPE_WEBHOOK_SECRET` | `whsec_PLACEHOLDER` | Signing secret (HMAC verification) |
| `VITE_STRIPE_PUBLISHABLE_KEY` | `pk_test_PLACEHOLDER` | Exposition côté frontend |
| `BILLING_DB_NAME` | `jurika_billing` | Base PG dédiée |
| `BILLING_SERVICE_PORT` | `8090` | Port HTTP |
| `STRIPE_CHECKOUT_LOCALE` | `fr` | Locale Checkout + Portal |
| `BILLING_TVA_RATE_PERCENT` | `20.00` | TVA Maroc (RG-BL12) |
| `BILLING_CHECKOUT_SUCCESS_URL` | `http://localhost:5173/billing/success?session_id={CHECKOUT_SESSION_ID}` | Redirect post-checkout |
| `BILLING_CHECKOUT_CANCEL_URL` | `http://localhost:5173/billing` | Redirect cancel |
| `BILLING_PORTAL_RETURN_URL` | `http://localhost:5173/billing` | Retour Customer Portal |
| `BILLING_SALES_EMAIL` | `contact@jurika.ai` | Destinataire leads Enterprise |

## Cartes de test (Stripe TEST mode)

| Numéro | Comportement |
|---|---|
| `4242 4242 4242 4242` | OK Visa |
| `4000 0000 0000 0002` | Refusée (RG-BL14 trial preservé) |
| `4000 0027 6000 3184` | 3DS requis (test SCA) |

Toutes : date d'expiration future quelconque, CVC 3 chiffres.

## Connu — alignement à faire (post-Sprint 12)

- **Mapping plan IDs** : `marketing-site/config/pricing.js` utilise `essentiel`/`professionnel`/`entreprise` (alignés avec Sprint 11 `SignupCabinetUseCase.ALLOWED_PLANS`). Le billing utilise `starter`/`business`/`enterprise` (alignés Stripe products). Un mapping unique côté `StripeProperties#resolvePriceId` ou côté frontend PlanPicker est requis Sprint 13.
- **PUT `/internal/workspaces/{id}/status`** en log uniquement V1 — Sprint 13 ajoutera les colonnes workspace `subscription_status` + `active_since` + `cancelled_at` (V20 évoquée PLAN §9).
- **Email triggers** : `BillingEmailService` est créé mais pas encore wire dans les `StripeEventHandlers` — à connecter Sprint 13 (sender injection dans les handlers).

## Refs

- `output/PLAN_SPRINT_12_BILLING.md` — plan canonique
- RG-BL01..18 — règles de gestion (PLAN §1)
- 10 décisions architecturales actées (PLAN §10)
