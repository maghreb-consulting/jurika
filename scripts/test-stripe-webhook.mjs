#!/usr/bin/env node
/**
 * scripts/test-stripe-webhook.mjs (2026-06-04 — chantier 3 e2e)
 *
 * Construit et signe un event Stripe `checkout.session.completed`
 * (mode=subscription) puis le POST sur le webhook billing-service.
 * Sert à l'e2e pre-prod : `stripe trigger` ne sait pas générer un
 * session subscription-mode complet, ce script comble le trou.
 *
 * Lit STRIPE_WEBHOOK_SECRET et STRIPE_SECRET_KEY depuis .env.local.
 *
 * Usage :
 *   node scripts/test-stripe-webhook.mjs <workspaceUUID> [planCode]
 *     planCode defaut = essentiel
 *
 * Sortie : code HTTP + corps. Exit 0 si 200, 1 sinon.
 */

import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');

function loadEnv(path) {
  const raw = readFileSync(path, 'utf8');
  const env = {};
  for (const line of raw.split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const eq = t.indexOf('=');
    if (eq < 0) continue;
    const k = t.slice(0, eq).trim();
    let v = t.slice(eq + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    env[k] = v;
  }
  return env;
}

const env = loadEnv(resolve(root, '.env.local'));
const whsec = env.STRIPE_WEBHOOK_SECRET;
if (!whsec || !whsec.startsWith('whsec_')) {
  console.error('STRIPE_WEBHOOK_SECRET introuvable / invalide dans .env.local');
  process.exit(2);
}

const workspaceId = process.argv[2];
const planCode = process.argv[3] || 'essentiel';
if (!workspaceId || !/^[0-9a-f-]{36}$/i.test(workspaceId)) {
  console.error('Usage: node scripts/test-stripe-webhook.mjs <workspaceUUID> [planCode]');
  process.exit(2);
}

// Construit un event minimal-mais-realiste checkout.session.completed mode=subscription.
// Les IDs sont en TEST mode, donc le format de prefixe est respecte (cs_test_, sub_, cus_).
const eventId = 'evt_test_' + crypto.randomBytes(8).toString('hex');
const sessionId = 'cs_test_' + crypto.randomBytes(12).toString('hex');
const subId = 'sub_' + crypto.randomBytes(12).toString('hex');
const customerId = 'cus_' + crypto.randomBytes(10).toString('hex');
const tsSec = Math.floor(Date.now() / 1000);

const event = {
  id: eventId,
  object: 'event',
  // doit matcher la version bundleee par stripe-java 28.4.0
  // (Stripe.API_VERSION) sinon EventDataObjectDeserializer.getObject()
  // renvoie Optional.empty() et session = null.
  api_version: '2025-02-24.acacia',
  created: tsSec,
  type: 'checkout.session.completed',
  livemode: false,
  pending_webhooks: 0,
  request: { id: null, idempotency_key: null },
  data: {
    object: {
      id: sessionId,
      object: 'checkout.session',
      amount_subtotal: 49900,
      amount_total: 49900,
      currency: 'mad',
      customer: customerId,
      customer_details: {
        email: 'demo-checkout@jurika.test',
        name: 'Test Customer'
      },
      mode: 'subscription',
      payment_status: 'paid',
      status: 'complete',
      // Stripe Java SDK ExpandableField : envoyer la forme expanded
      // (objet) pour que session.getSubscription() resolve l'ID. La forme
      // string fonctionne theoriquement aussi mais le deserializer GSON
      // est plus indulgent avec l'objet complet.
      subscription: {
        id: subId,
        object: 'subscription',
        status: 'active',
        current_period_start: tsSec,
        current_period_end: tsSec + 30 * 86400,
        customer: customerId
      },
      success_url: 'http://localhost:5173/billing/success?session_id={CHECKOUT_SESSION_ID}',
      cancel_url: 'http://localhost:5173/billing',
      metadata: {
        workspace_id: workspaceId,
        plan_code: planCode
      },
      created: tsSec - 60,
      expires_at: tsSec + 86400,
      livemode: false
    }
  }
};

const payload = JSON.stringify(event);
const signedPayload = tsSec + '.' + payload;
const sig = crypto.createHmac('sha256', whsec).update(signedPayload).digest('hex');
const stripeSignature = 't=' + tsSec + ',v1=' + sig;

console.log('Posting checkout.session.completed for workspace=' + workspaceId + ' plan=' + planCode);
console.log('  event_id=' + eventId + ' sub_id=' + subId);

const res = await fetch('http://localhost:8090/api/v1/billing/webhook/stripe', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Stripe-Signature': stripeSignature
  },
  body: payload
});
const txt = await res.text();
console.log('  HTTP ' + res.status + (txt ? ' — ' + txt.slice(0, 200) : ''));
process.exit(res.ok ? 0 : 1);
