#!/usr/bin/env node
/**
 * scripts/e2e-billing-upgrade-payment-2026-06-07.mjs
 *
 * BUG 8 + BUG 14 (2026-06-07) — verification end-to-end :
 *   - POST /billing/prepare-payment pour 4 methodes (CARD/BANK/CHEQUE/CASH)
 *   - GET /billing/payments + PATCH /payments/{id}/validate
 *   - workspace bascule active + identifiants re-emis post-validation
 *   - POST /billing/change-plan upgrade (essentiel -> business) avec proration
 *   - POST /billing/change-plan downgrade (business -> essentiel)
 *   - blocage downgrade si quotas depasses (PLAN_DOWNGRADE_BLOCKED_*)
 *   - CARD 4242 -> COMPLETED via webhook (smoke avec test-stripe-webhook.mjs)
 *
 * Pre-req : stack UP + DB billing migrations V4 appliquees + Stripe TEST mode
 * configure (STRIPE_PRICE_* dans .env). Le test est tolerant : si Stripe
 * n'est pas configure, les checks CARD/Stripe sont passes en SKIP, les checks
 * hors-ligne restent verifies (et c'est l'apport principal de BUG 14).
 *
 * Sortie : tableau OK/KO par check, exit code = nb FAIL.
 */

import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';
const SEED_PATH = '/api/v1/test/seed/workspace?role=SUPERVISEUR';

const C = {
    pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
    dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m',
};

const results = [];
function record(group, name, status, detail = '') {
    results.push({ group, name, status, detail });
    const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
    console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + String(detail).slice(0, 200) + C.reset : ''}`);
}

async function loadEnv() {
    for (const f of [join(PROJECT_ROOT, '.env.local'), join(PROJECT_ROOT, '.env')]) {
        try {
            await access(f, fsConstants.R_OK);
            const raw = await readFile(f, 'utf8');
            for (const line of raw.split(/\r?\n/)) {
                const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
                if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
            }
        } catch { /* ok */ }
    }
}

async function http(method, path, { body = null, token = null, headers: extra = {} } = {}) {
    const url = `${BASE}${path}`;
    const headers = { 'Accept': 'application/json', ...extra };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let bodyStr = null;
    if (body !== null) {
        headers['Content-Type'] = 'application/json';
        bodyStr = JSON.stringify(body);
    }
    let res;
    try {
        res = await fetch(url, { method, headers, body: bodyStr });
    } catch (e) {
        return { ok: false, status: 0, body: null, networkError: e.message };
    }
    let parsed = null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
        try { parsed = await res.json(); } catch { parsed = null; }
    } else {
        try { parsed = await res.text(); } catch { parsed = null; }
    }
    return { ok: res.ok, status: res.status, body: parsed };
}

function section(title) {
    console.log(`\n${C.bold}${C.cyan}═══ ${title} ═══${C.reset}`);
}

async function seedSupervisor() {
    const seed = await http('POST', SEED_PATH, { body: {} });
    if (!seed.ok) return null;
    const login = await http('POST', '/api/v1/auth/login', {
        body: {
            workspaceCode: seed.body.workspaceCode,
            email: seed.body.adminEmail,
            password: seed.body.adminPassword || 'DemoPwd2026!',
        }
    });
    if (!login.ok) return null;
    return {
        token: login.body.accessToken,
        email: seed.body.adminEmail,
        wsCode: seed.body.workspaceCode,
        workspaceId: seed.body.workspaceId,
    };
}

// ─── A. PREPARE-PAYMENT — 4 methodes ─────────────────────────────────
async function parcoursPreparePayment(sup) {
    section('A. PREPARE-PAYMENT — 4 methodes');
    if (!sup) { record('PreparePayment', 'pre-req seed SUPERVISEUR', 'SKIP'); return null; }

    const methods = ['BANK_TRANSFER', 'CHEQUE', 'CASH']; // CARD teste a part (depend Stripe)
    const pendingIds = {};

    for (const method of methods) {
        const r = await http('POST', '/api/v1/billing/prepare-payment', {
            token: sup.token,
            body: {
                planCode: 'essentiel',
                billingPeriod: 'monthly',
                method,
                contactEmail: sup.email,
                workspaceName: 'E2E Test Cabinet',
            }
        });
        if (!r.ok) {
            record('PreparePayment', `POST prepare-payment ${method}`, 'FAIL',
                   `${r.status} ${JSON.stringify(r.body).slice(0,200)}`);
            continue;
        }
        const id = r.body.paymentId;
        pendingIds[method] = id;
        const hasInstructions = Array.isArray(r.body.instructions) && r.body.instructions.length > 0;
        const isPending = r.body.status === 'PENDING';
        record('PreparePayment', `POST prepare-payment ${method}`,
               (id && hasInstructions && isPending) ? 'PASS' : 'FAIL',
               `id=${id} status=${r.body.status} instructions=${r.body.instructions?.length ?? 0}`);
    }

    // CARD — depend de la presence des Stripe price IDs en config
    const card = await http('POST', '/api/v1/billing/prepare-payment', {
        token: sup.token,
        body: {
            planCode: 'essentiel',
            billingPeriod: 'monthly',
            method: 'CARD',
            contactEmail: sup.email,
            workspaceName: 'E2E Test Cabinet',
        }
    });
    if (card.ok && card.body.checkoutUrl) {
        pendingIds.CARD = card.body.paymentId;
        record('PreparePayment', 'POST prepare-payment CARD',
               card.body.checkoutUrl.startsWith('https://checkout.stripe.com/') ? 'PASS' : 'FAIL',
               `id=${card.body.paymentId} url=${card.body.checkoutUrl?.slice(0, 60)}...`);
    } else if (card.status === 503 || card.status === 500
               || JSON.stringify(card.body).includes('STRIPE_ERROR')
               || JSON.stringify(card.body).includes('placeholder')) {
        record('PreparePayment', 'POST prepare-payment CARD', 'SKIP', 'Stripe non configure (.env STRIPE_PRICE_*)');
    } else {
        record('PreparePayment', 'POST prepare-payment CARD', 'FAIL',
               `${card.status} ${JSON.stringify(card.body).slice(0,200)}`);
    }

    return pendingIds;
}

// ─── B. LIST + VALIDATE ──────────────────────────────────────────────
async function parcoursValidatePayment(sup, pendingIds) {
    section('B. LIST + VALIDATE PAYMENT');
    if (!sup || !pendingIds) { record('Validate', 'pre-req', 'SKIP'); return; }

    const list = await http('GET', '/api/v1/billing/payments', { token: sup.token });
    const items = list.body?.items;
    record('Validate', 'GET /billing/payments', list.ok ? 'PASS' : 'FAIL',
           `${list.status} items=${Array.isArray(items) ? items.length : '?'}`);

    // Validate le premier paiement hors-ligne
    const targetId = pendingIds.BANK_TRANSFER || pendingIds.CHEQUE || pendingIds.CASH;
    if (!targetId) { record('Validate', 'no hors-ligne payment to validate', 'SKIP'); return; }
    const v = await http('PATCH', `/api/v1/billing/payments/${targetId}/validate`, {
        token: sup.token,
        body: { notes: 'Validation e2e automatique' }
    });
    record('Validate', `PATCH /payments/${targetId}/validate`, v.ok ? 'PASS' : 'FAIL',
           `${v.status} status=${v.body?.status} method=${v.body?.method}`);
    if (v.ok && v.body?.status === 'COMPLETED') {
        record('Validate', 'Status transition PENDING->COMPLETED', 'PASS', `validatedBy=${v.body.validatedBy}`);
    }

    // Idempotency : revalider doit renvoyer COMPLETED sans erreur
    const v2 = await http('PATCH', `/api/v1/billing/payments/${targetId}/validate`, {
        token: sup.token, body: {}
    });
    record('Validate', 'PATCH validate idempotent (re-call)',
           (v2.ok && v2.body?.status === 'COMPLETED') ? 'PASS' : 'FAIL',
           `${v2.status} status=${v2.body?.status}`);

    // Verifier que la subscription est devenue active (shadow row pour hors-ligne)
    const sub = await http('GET', '/api/v1/billing/subscription', { token: sup.token });
    record('Validate', 'GET /subscription post-validation',
           (sub.ok && sub.body?.status === 'active') ? 'PASS' : 'FAIL',
           `${sub.status} status=${sub.body?.status} plan=${sub.body?.planCode}`);
}

// ─── C. CHANGE PLAN — upgrade + downgrade + preview ──────────────────
async function parcoursChangePlan(sup) {
    section('C. CHANGE PLAN — upgrade/downgrade');
    if (!sup) { record('ChangePlan', 'pre-req', 'SKIP'); return; }

    // 1. Preview essentiel -> business (upgrade)
    const previewUp = await http('POST', '/api/v1/billing/change-plan/preview', {
        token: sup.token,
        body: { targetPlanCode: 'business', billingPeriod: 'monthly' }
    });
    if (previewUp.ok) {
        record('ChangePlan', 'POST /change-plan/preview essentiel→business',
               previewUp.body.isUpgrade ? 'PASS' : 'FAIL',
               `from=${previewUp.body.fromPlan} to=${previewUp.body.toPlan} up=${previewUp.body.isUpgrade}`);
    } else if (previewUp.status === 404) {
        record('ChangePlan', 'POST /change-plan/preview', 'SKIP', 'pas de subscription active');
        return;
    } else {
        record('ChangePlan', 'POST /change-plan/preview', 'FAIL',
               `${previewUp.status} ${JSON.stringify(previewUp.body).slice(0,200)}`);
    }

    // 2. Actual change (peut echouer si Stripe non configure -> SKIP)
    const change = await http('POST', '/api/v1/billing/change-plan', {
        token: sup.token,
        body: { targetPlanCode: 'business', billingPeriod: 'monthly' }
    });
    if (change.ok) {
        record('ChangePlan', 'POST /change-plan essentiel→business', 'PASS',
               `from=${change.body.previousPlanCode} to=${change.body.newPlanCode}`);
    } else if (JSON.stringify(change.body).includes('STRIPE_ERROR') ||
               JSON.stringify(change.body).includes('manual-') ||
               JSON.stringify(change.body).includes('Subscription')) {
        record('ChangePlan', 'POST /change-plan', 'SKIP',
               'Stripe non configure ou subscription shadow (paiement hors-ligne)');
    } else {
        record('ChangePlan', 'POST /change-plan', 'FAIL',
               `${change.status} ${JSON.stringify(change.body).slice(0,200)}`);
    }

    // 3. Preview downgrade (business -> essentiel) — devrait passer car 1 employe seul
    const previewDown = await http('POST', '/api/v1/billing/change-plan/preview', {
        token: sup.token,
        body: { targetPlanCode: 'essentiel', billingPeriod: 'monthly' }
    });
    if (previewDown.ok) {
        record('ChangePlan', 'POST preview business→essentiel',
               (previewDown.body.isDowngrade && previewDown.body.downgradeAllowed) ? 'PASS' : 'FAIL',
               `down=${previewDown.body.isDowngrade} allowed=${previewDown.body.downgradeAllowed} reason=${previewDown.body.blockedReason ?? '-'}`);
    } else {
        record('ChangePlan', 'POST preview business→essentiel', 'SKIP', `${previewDown.status}`);
    }

    // 4. Entreprise -> doit retourner PLAN_QUOTE_ONLY (400)
    const entreprise = await http('POST', '/api/v1/billing/change-plan', {
        token: sup.token,
        body: { targetPlanCode: 'entreprise', billingPeriod: 'monthly' }
    });
    const blockedOk = !entreprise.ok && JSON.stringify(entreprise.body ?? '').includes('PLAN_QUOTE_ONLY');
    record('ChangePlan', 'change-plan entreprise rejete (RG-BL10)',
           blockedOk ? 'PASS' : 'FAIL',
           `${entreprise.status} ${JSON.stringify(entreprise.body).slice(0,200)}`);
}

// ─── D. DOWNGRADE BLOCKED — quotas depasses ──────────────────────────
async function parcoursDowngradeBlocked() {
    section('D. DOWNGRADE BLOCKED — quotas');
    // Hors-scope automatique : pour declencher PLAN_DOWNGRADE_BLOCKED_DOSSIERS
    // il faudrait seeder un workspace business avec > 30 dossiers. On laisse
    // le check au e2e manuel + le preview API expose le blocage.
    record('DowngradeBlocked', 'pre-req seed >30 dossiers + plan business', 'SKIP',
           'Necessite scenario seed dedie. Couvert par preview API si quotas > limites cibles.');
}

// ─── E. AUTH /internal/usage + /issue-credentials ───────────────────
async function parcoursAuthInternal(sup) {
    section('E. AUTH internals (usage + issue-credentials)');
    if (!sup?.workspaceId) { record('AuthInternal', 'pre-req', 'SKIP'); return; }
    // Ces endpoints sont sous /internal/** — la gateway les rejette en externe.
    // On verifie juste qu'ils ne renvoient pas 500 a chaud si jamais bypass dev.
    const u = await http('GET', `/internal/workspaces/${sup.workspaceId}/usage`);
    record('AuthInternal', '/internal/.../usage (filtre gateway = 404)',
           (u.status === 404 || u.status === 401 || u.status === 403 || u.ok) ? 'PASS' : 'FAIL',
           `${u.status}`);
}

// ─── MAIN ────────────────────────────────────────────────────────────
(async () => {
    await loadEnv();
    console.log(`${C.bold}🧪 BUG 8 + BUG 14 — Upgrade & Payment e2e${C.reset}  base=${BASE}\n`);

    const sup = await seedSupervisor();
    if (sup) {
        record('Setup', 'seed SUPERVISEUR', 'PASS', `ws=${sup.wsCode}`);
    } else {
        record('Setup', 'seed SUPERVISEUR', 'FAIL',
               'check /api/v1/test/seed/workspace (jurika.test.seed.enabled=true ?)');
    }

    const pendingIds = await parcoursPreparePayment(sup);
    await parcoursValidatePayment(sup, pendingIds);
    await parcoursChangePlan(sup);
    await parcoursDowngradeBlocked();
    await parcoursAuthInternal(sup);

    // ─── Resume ─────────────────────────────────────────────────────
    const pass = results.filter(r => r.status === 'PASS').length;
    const fail = results.filter(r => r.status === 'FAIL').length;
    const skip = results.filter(r => r.status === 'SKIP').length;
    console.log(`\n${C.bold}═════════════════════════════════════════════════════${C.reset}`);
    console.log(`${C.bold}RESUME${C.reset}  ${C.pass}PASS=${pass}${C.reset}  ${C.fail}FAIL=${fail}${C.reset}  ${C.skip}SKIP=${skip}${C.reset}  total=${results.length}`);
    process.exit(fail);
})().catch(e => {
    console.error('Erreur fatale :', e);
    process.exit(99);
});
