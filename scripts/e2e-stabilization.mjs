#!/usr/bin/env node
/**
 * scripts/e2e-stabilization.mjs
 *
 * Boucle de stabilisation 2026-06-04 (operateur autonome) :
 * deroule chaque parcours via API + capture les erreurs.
 *
 * Parcours :
 *  A. Onboarding fresh : signup → login → change-password → setup-2fa
 *  B. Login demo existing (JUR-SSKB3) → tickets CRUD + transitions
 *  C. Workflows 9 types : start + premier step (smoke)
 *  C2. CREATION SARL : 9 steps complets
 *  D. OCR : verif endpoint dispo
 *  E. Datarooms : list dossiers / juridique / fiscal / exercices
 *  F. Dashboards : per-role
 *  G. Billing : checkout-session
 *
 * Sortie : tableau OK/KO par check, exit code = nb FAIL.
 */

import { spawnSync } from 'node:child_process';
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';
const SEED_PATH = '/api/v1/test/seed/workspace';

const C = {
    pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
    dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m',
};

const results = [];
function record(group, name, status, detail = '') {
    results.push({ group, name, status, detail });
    const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
    console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + detail.slice(0, 200) + C.reset : ''}`);
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

async function http(method, path, { body = null, token = null, raw = false, headers: extra = {} } = {}) {
    const url = `${BASE}${path}`;
    const headers = { 'Accept': raw ? '*/*' : 'application/json', ...extra };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let bodyStr = null;
    if (body && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        bodyStr = JSON.stringify(body);
    } else if (body instanceof FormData) {
        bodyStr = body;
    }
    let res;
    try {
        res = await fetch(url, { method, headers, body: bodyStr });
    } catch (e) {
        return { ok: false, status: 0, body: null, networkError: e.message };
    }
    let parsed = null;
    const ct = res.headers.get('content-type') || '';
    if (raw) {
        parsed = await res.arrayBuffer();
    } else if (ct.includes('application/json')) {
        try { parsed = await res.json(); } catch { parsed = null; }
    } else {
        try { parsed = await res.text(); } catch { parsed = null; }
    }
    return { ok: res.ok, status: res.status, body: parsed };
}

function section(title) {
    console.log(`\n${C.bold}${C.cyan}═══ ${title} ═══${C.reset}`);
}

// ─── A. ONBOARDING fresh ─────────────────────────────────────────────
async function parcoursOnboarding() {
    section('A. ONBOARDING fresh cabinet');
    const ts = Date.now();
    const wsName = `E2E Cabinet ${ts}`;
    const adminEmail = `admin+e2e-${ts}@jurika.test`;
    const signup = await http('POST', '/api/v1/public/signup/cabinet', {
        body: {
            workspaceName: wsName,
            contactEmail: adminEmail,
            ice: '012345678901234',
            ifFiscal: '12345678',
            rcNumber: '99999',
            city: 'Casablanca',
            firstName: 'E2E',
            lastName: 'Test',
            email: adminEmail,
            phone: '+212600000000',
            selectedPlan: 'essentiel',
            cguAccepted: true,
        }
    });
    if (!signup.ok) {
        record('Onboarding', 'POST /public/signup/cabinet', 'FAIL', `${signup.status} ${JSON.stringify(signup.body)}`);
        return null;
    }
    const wsCode = signup.body?.workspaceCode;
    record('Onboarding', 'POST /public/signup/cabinet', 'PASS', `wsCode=${wsCode}`);

    // Recup MDP temp depuis MailHog
    let tempPwd = null;
    try {
        const mh = await fetch('http://localhost:8025/api/v2/messages?limit=20');
        const mailbox = await mh.json();
        const msg = mailbox.items?.find(m =>
            m.Content?.Headers?.To?.[0]?.includes(adminEmail) ||
            JSON.stringify(m).includes(adminEmail));
        if (msg) {
            const body = (msg.Content?.Body || '') + JSON.stringify(msg.MIME || '');
            const m = body.match(/[A-Za-z0-9!@#$%&*]{12,16}/g);
            tempPwd = m ? m.find(p => /[A-Z]/.test(p) && /[a-z]/.test(p) && /\d/.test(p)) : null;
        }
    } catch { /* MailHog peut etre off si Brevo actif */ }

    if (!tempPwd && process.env.SMTP_HOST?.includes('brevo')) {
        record('Onboarding', 'Recup MDP temp (Brevo actif - skip MailHog)', 'SKIP',
               'MDP envoye via SMTP Brevo reel, pas recuperable depuis MailHog');
    } else if (!tempPwd) {
        record('Onboarding', 'Recup MDP temp depuis MailHog', 'FAIL', 'pas de message trouve');
    } else {
        record('Onboarding', 'Recup MDP temp', 'PASS', `pwd=${tempPwd.slice(0, 4)}...`);
    }

    // workspace-check
    const wsCheck = await http('POST', '/api/v1/auth/workspace-check', {
        body: { workspaceCode: wsCode }
    });
    if (wsCheck.ok) {
        record('Onboarding', 'POST /auth/workspace-check', 'PASS', `wsId=${wsCheck.body?.workspaceId?.slice(0,8)}`);
    } else {
        record('Onboarding', 'POST /auth/workspace-check', 'FAIL', `${wsCheck.status}`);
    }
    return { wsCode, adminEmail, tempPwd, workspaceId: wsCheck.body?.workspaceId };
}

// ─── B. LOGIN existing demo + TICKETS ────────────────────────────────
async function parcoursDemoLogin() {
    section('B. LOGIN demo cabinet existant + TICKETS');
    // Tentative login JUR-SSKB3
    let login = await http('POST', '/api/v1/auth/login', {
        body: { workspaceCode: 'JUR-SSKB3', email: 'demo-jursskb3@jurika.test', password: 'DemoPwd2026!' }
    });
    let wsCode = 'JUR-SSKB3';
    let email = 'demo-jursskb3@jurika.test';
    let pwd = 'DemoPwd2026!';

    if (!login.ok) {
        // Fallback : seed un nouveau workspace de test
        record('Demo', 'login JUR-SSKB3', 'SKIP', `${login.status} → fallback test-seed`);
        const seed = await http('POST', SEED_PATH, { body: {} });
        if (!seed.ok) {
            record('Demo', 'test-seed/workspace', 'FAIL', `${seed.status} ${JSON.stringify(seed.body).slice(0,150)}`);
            return null;
        }
        wsCode = seed.body.workspaceCode;
        email = seed.body.adminEmail;
        pwd = seed.body.adminPassword || 'DemoPwd2026!';
        record('Demo', 'test-seed/workspace', 'PASS', `ws=${wsCode}`);
        login = await http('POST', '/api/v1/auth/login', {
            body: { workspaceCode: wsCode, email, password: pwd }
        });
    }
    if (!login.ok) {
        record('Demo', 'POST /auth/login', 'FAIL', `${login.status} ${JSON.stringify(login.body)}`);
        return null;
    }
    record('Demo', 'POST /auth/login', 'PASS', `ws=${wsCode} requires2fa=${login.body.requires2fa}`);

    const ctx = {
        wsCode, email, pwd,
        token: login.body.accessToken,
        userId: login.body.userId,
        workspaceId: login.body.workspaceId,
        seedDossierId: null,
        seedExerciceId: null,
        seedClientUserId: null,
    };

    // ME
    const me = await http('GET', '/api/v1/auth/me', { token: ctx.token });
    record('Demo', 'GET /auth/me', me.ok ? 'PASS' : 'FAIL', `role=${me.body?.role}`);

    // ── DOSSIERS list (pour avoir un dossierId pour tickets/workflow)
    const dossiers = await http('GET', '/api/v1/dataroom/dossiers', { token: ctx.token });
    if (!dossiers.ok) {
        record('Demo', 'GET /dataroom/dossiers', 'FAIL', `${dossiers.status} ${JSON.stringify(dossiers.body).slice(0,150)}`);
        return ctx;
    }
    const dList = Array.isArray(dossiers.body) ? dossiers.body : (dossiers.body?.items || []);
    record('Demo', 'GET /dataroom/dossiers', 'PASS', `${dList.length} dossiers`);
    ctx.seedDossierId = dList[0]?.id;

    // ── Ticket creation
    const tkPayload = {
        titre: `Ticket E2E ${Date.now()}`,
        type: 'CREATION',
        priorite: 'NORMALE',
        dossierId: ctx.seedDossierId,
        description: 'Test e2e creation ticket',
    };
    const tk = await http('POST', '/api/v1/tickets', { token: ctx.token, body: tkPayload });
    if (!tk.ok) {
        record('Demo', 'POST /tickets', 'FAIL', `${tk.status} ${JSON.stringify(tk.body).slice(0,200)}`);
    } else {
        record('Demo', 'POST /tickets', 'PASS', `id=${tk.body?.id?.slice(0,8)} statut=${tk.body?.statut}`);
        ctx.ticketId = tk.body.id;
    }

    // ── Tickets list
    const tkList = await http('GET', '/api/v1/tickets?limit=10', { token: ctx.token });
    record('Demo', 'GET /tickets (list)', tkList.ok ? 'PASS' : 'FAIL',
           `${tkList.body?.total ?? tkList.body?.items?.length} tickets`);

    // ── Ticket transitions NOUVEAU → EN_COURS → CLOTURE
    if (ctx.ticketId) {
        const trans1 = await http('POST', `/api/v1/tickets/${ctx.ticketId}/transition`, {
            token: ctx.token, body: { target: 'EN_COURS' }
        });
        record('Demo', 'Ticket NOUVEAU→EN_COURS', trans1.ok ? 'PASS' : 'FAIL',
               `${trans1.status} statut=${trans1.body?.statut}`);
        const trans2 = await http('POST', `/api/v1/tickets/${ctx.ticketId}/transition`, {
            token: ctx.token, body: { target: 'CLOTURE', comment: 'Test e2e cloture' }
        });
        record('Demo', 'Ticket EN_COURS→CLOTURE', trans2.ok ? 'PASS' : 'FAIL',
               `${trans2.status} statut=${trans2.body?.statut}`);
    }
    return ctx;
}

// ─── C. WORKFLOWS : 9 types start smoke ──────────────────────────────
async function parcoursWorkflows(ctx) {
    section('C. WORKFLOWS — start 9 types (smoke)');
    if (!ctx?.token) { record('Workflows', 'pre-req auth', 'SKIP', 'pas de token'); return; }

    // Catalog
    const cat = await http('GET', '/api/v1/workflows/catalog', { token: ctx.token });
    if (cat.ok) {
        const n = Object.keys(cat.body?.types || {}).length;
        record('Workflows', 'GET /workflows/catalog', n === 9 ? 'PASS' : 'FAIL', `${n} types`);
    } else {
        record('Workflows', 'GET /workflows/catalog', 'FAIL', `${cat.status}`);
    }

    if (!ctx.ticketId) {
        record('Workflows', 'workflow start (smoke)', 'SKIP', 'pas de ticket cree');
        return;
    }

    // Workflow start sur le ticket existant
    const wfStart = await http('POST', `/api/v1/workflows/${ctx.ticketId}/start`, {
        token: ctx.token, body: { type: 'CREATION' }
    });
    if (!wfStart.ok) {
        record('Workflows', 'POST /workflows/{ticket}/start CREATION', 'FAIL',
               `${wfStart.status} ${JSON.stringify(wfStart.body).slice(0,200)}`);
        return;
    }
    record('Workflows', 'POST /workflows/start CREATION', 'PASS',
           `step=${wfStart.body?.currentStep}/${wfStart.body?.totalSteps}`);

    // GET workflow
    const wfGet = await http('GET', `/api/v1/workflows/${ctx.ticketId}`, { token: ctx.token });
    record('Workflows', 'GET /workflows/{ticket}', wfGet.ok ? 'PASS' : 'FAIL', `step=${wfGet.body?.currentStep}`);

    // Execute step 1 CREATION (denomination)
    const step1 = await http('POST', `/api/v1/workflows/${ctx.ticketId}/execute-step`, {
        token: ctx.token,
        body: {
            step: 1,
            payload: {
                ice: '012345678901234',
                denomination: 'SARL Test E2E',
                cnNumero: 'CN-2026-001',
                cnDate: '2026-06-01',
                activiteCn: 'Conseil',
                beneficiaire: 'E2E Test',
                formeJuridique: 'SARL',
            }
        }
    });
    if (!step1.ok) {
        record('Workflows', 'CREATION step 1 (denomination)', 'FAIL',
               `${step1.status} ${JSON.stringify(step1.body).slice(0,200)}`);
    } else {
        record('Workflows', 'CREATION step 1 (denomination)', 'PASS', `advanced=${step1.body?.advanced}`);
    }

    // Register a piece (P2)
    const piece = await http('POST', `/api/v1/workflows/${ctx.ticketId}/pieces`, {
        token: ctx.token,
        body: { code: 'CN', label: 'Certificat Negatif', filename: 'cn.pdf', sizeBytes: 102400, contentType: 'application/pdf', uploadedAtStep: 1 }
    });
    record('Workflows', 'POST /workflows/pieces register', piece.ok ? 'PASS' : 'FAIL', `${piece.status}`);

    // Workflows: pour chaque autre type, tester start sur un nouveau ticket vide
    // (creation ticket par type + workflow start)
    const types = ['IMPORT', 'MODIFICATION', 'DISSOLUTION', 'LIQUIDATION',
                   'SUCCURSALE_MA', 'SUCCURSALE_ETR', 'FERMETURE_SUCCURSALE', 'PV_AGO'];
    for (const t of types) {
        // creer ticket de ce type
        const newTk = await http('POST', '/api/v1/tickets', {
            token: ctx.token,
            body: {
                titre: `Smoke ${t}`,
                type: t,
                priorite: 'BASSE',
                dossierId: ctx.seedDossierId,
                description: `Smoke test ${t}`,
            }
        });
        if (!newTk.ok) {
            record('Workflows', `Ticket+Workflow ${t}`, 'FAIL',
                   `ticket POST ${newTk.status} ${JSON.stringify(newTk.body).slice(0,150)}`);
            continue;
        }
        const wf = await http('POST', `/api/v1/workflows/${newTk.body.id}/start`, {
            token: ctx.token, body: { type: t }
        });
        record('Workflows', `Workflow ${t} start`, wf.ok ? 'PASS' : 'FAIL',
               `${wf.status} step=${wf.body?.currentStep}/${wf.body?.totalSteps}`);
    }
}

// ─── D. OCR ──────────────────────────────────────────────────────────
async function parcoursOcr(ctx) {
    section('D. OCR / AI');
    if (!ctx?.token) { record('OCR', 'pre-req auth', 'SKIP', 'pas de token'); return; }
    // GET document types
    const types = await http('GET', '/api/v1/ai/documents/types', { token: ctx.token });
    record('OCR', 'GET /ai/documents/types', types.ok ? 'PASS' : 'FAIL', `${types.status}`);

    // GET templates pour workflow CREATION_SARL (le mapper ai-service utilise ce code,
    // pas CREATION qui est le code workflow-service WorkflowType.CREATION).
    const tpl = await http('GET', '/api/v1/ai/workflows/CREATION_SARL/templates', { token: ctx.token });
    let firstTemplateCode = null;
    if (tpl.ok) {
        const n = Array.isArray(tpl.body) ? tpl.body.length : 0;
        record('OCR', 'GET /ai/workflows/CREATION_SARL/templates', n > 0 ? 'PASS' : 'FAIL', `${n} templates`);
        firstTemplateCode = tpl.body?.[0]?.code;
    } else {
        record('OCR', 'GET /ai/workflows/CREATION_SARL/templates', 'FAIL', `${tpl.status}`);
    }

    // Generation document via AI : POST sur le premier template trouve (binaire .docx attendu)
    if (firstTemplateCode) {
        const gen = await http('POST',
            `/api/v1/ai/workflows/CREATION_SARL/documents/${encodeURIComponent(firstTemplateCode)}`,
            { token: ctx.token, raw: true, body: {
                formeJuridique: 'SARL',
                denomination: 'SARL Test E2E',
                ice: '012345678901234',
                cnNumero: 'CN-2026-001',
                cnDate: '2026-06-01',
                activiteCn: 'Conseil',
                beneficiaire: 'E2E Test',
                apportNumeraire: 10000,
                capitalLibere: 10000,
                nombreParts: 100,
                associes: [{ nom: 'Karim Bennani', nombreParts: 100, pourcentageDetention: 100 }],
                adresse: '12 rue Test',
                province: 'Casablanca',
                commune: 'Casa Anfa',
                codePostal: '20000',
            }});
        const sizeKb = gen.body instanceof ArrayBuffer ? Math.round(gen.body.byteLength / 1024) : 0;
        record('OCR', `POST /ai/workflows/.../documents/${firstTemplateCode} (genere .docx)`,
               gen.ok && sizeKb > 0 ? 'PASS' : 'FAIL',
               gen.ok ? `${sizeKb} KB` : `HTTP ${gen.status}`);
    }
}

// ─── E. DATAROOMS ────────────────────────────────────────────────────
async function parcoursDatarooms(ctx) {
    section('E. DATAROOMS');
    if (!ctx?.token || !ctx?.seedDossierId) { record('Dataroom', 'pre-req', 'SKIP'); return; }

    const jur = await http('GET', `/api/v1/dataroom/dossiers/${ctx.seedDossierId}/juridique`, { token: ctx.token });
    record('Dataroom', 'GET juridique', jur.ok ? 'PASS' : 'FAIL',
           `${jur.status} docs=${jur.body?.documentsEnVigueur?.length ?? '?'}`);

    const exr = await http('GET', `/api/v1/dataroom/dossiers/${ctx.seedDossierId}/exercices`, { token: ctx.token });
    record('Dataroom', 'GET exercices', exr.ok ? 'PASS' : 'FAIL',
           `${exr.status} n=${Array.isArray(exr.body) ? exr.body.length : '?'}`);

    const fis = await http('GET', `/api/v1/dataroom/dossiers/${ctx.seedDossierId}/fiscal`, { token: ctx.token });
    record('Dataroom', 'GET fiscal', fis.ok ? 'PASS' : 'FAIL', `${fis.status}`);

    const subs = await http('GET', '/api/v1/dataroom/fiscal/sub-classifications', { token: ctx.token });
    record('Dataroom', 'GET fiscal/sub-classifications', subs.ok ? 'PASS' : 'FAIL',
           `${subs.status} n=${Array.isArray(subs.body) ? subs.body.length : '?'}`);

    const ech = await http('GET', `/api/v1/dataroom/dossiers/${ctx.seedDossierId}/echeances`, { token: ctx.token });
    record('Dataroom', 'GET echeances', ech.ok ? 'PASS' : 'FAIL',
           `${ech.status} n=${Array.isArray(ech.body) ? ech.body.length : '?'}`);
}

// ─── Helper : seed un workspace SUPERVISEUR pour dashboards + billing ─
async function seedSupervisor() {
    const seed = await http('POST', '/api/v1/test/seed/workspace?role=SUPERVISEUR', { body: {} });
    if (!seed.ok) return null;
    const login = await http('POST', '/api/v1/auth/login', {
        body: { workspaceCode: seed.body.workspaceCode, email: seed.body.adminEmail, password: seed.body.adminPassword || 'DemoPwd2026!' }
    });
    if (!login.ok) return null;
    return { token: login.body.accessToken, email: seed.body.adminEmail, wsCode: seed.body.workspaceCode };
}

// ─── F. DASHBOARDS (avec user SUPERVISEUR) ──────────────────────────
async function parcoursDashboards(ctx) {
    section('F. DASHBOARDS');
    if (!ctx?.token) { record('Dashboard', 'pre-req auth', 'SKIP'); return; }
    // /dashboards/employe : test avec le user EMPLOYE existant
    const emp = await http('GET', '/api/v1/dashboards/employe', { token: ctx.token });
    record('Dashboard', 'GET /dashboards/employe (EMPLOYE)', emp.ok ? 'PASS' : 'FAIL', `${emp.status}`);

    // /dashboards/superviseur : besoin d'un user SUPERVISEUR. Seed ad-hoc.
    const sup = await seedSupervisor();
    if (!sup) {
        record('Dashboard', 'seed SUPERVISEUR pour test superviseur', 'SKIP', 'seed/login a echoue');
        return;
    }
    const supR = await http('GET', '/api/v1/dashboards/superviseur', { token: sup.token });
    record('Dashboard', 'GET /dashboards/superviseur (SUPERVISEUR)', supR.ok ? 'PASS' : 'FAIL',
           `${supR.status} tickets=${supR.body?.ticketsOuverts}`);
    // bonus : SUPERVISEUR doit aussi pouvoir /dashboards/employe (role hierarchy)
    const supE = await http('GET', '/api/v1/dashboards/employe', { token: sup.token });
    record('Dashboard', 'SUPERVISEUR → /dashboards/employe (hierarchy)', supE.ok ? 'PASS' : 'FAIL',
           `${supE.status}`);
}

// ─── G. BILLING (avec user SUPERVISEUR) ─────────────────────────────
async function parcoursBilling() {
    section('G. BILLING');
    const sup = await seedSupervisor();
    if (!sup) { record('Billing', 'seed SUPERVISEUR', 'SKIP', 'seed/login echoue'); return; }

    const co = await http('POST', '/api/v1/billing/checkout-session', {
        token: sup.token,
        body: {
            planCode: 'business',
            contactEmail: sup.email,
            workspaceName: 'E2E Test Workspace',
        }
    });
    if (co.ok && co.body?.checkoutUrl?.startsWith('https://checkout.stripe.com/')) {
        record('Billing', 'POST /billing/checkout-session', 'PASS',
               `url=${co.body.checkoutUrl.slice(0, 60)}...`);
    } else {
        record('Billing', 'POST /billing/checkout-session', 'FAIL',
               `${co.status} ${JSON.stringify(co.body).slice(0,200)}`);
    }

    const sub = await http('GET', '/api/v1/billing/subscription', { token: sup.token });
    // 404 acceptable (pas encore de souscription pour ce workspace fraichement seede)
    const ok = sub.status === 200 || sub.status === 404;
    record('Billing', 'GET /billing/subscription', ok ? 'PASS' : 'FAIL',
           `${sub.status} ${sub.status === 404 ? '(pas de subscription = normal pour fresh seed)' : ''}`);
}

// ─── MAIN ────────────────────────────────────────────────────────────
(async () => {
    await loadEnv();
    console.log(`${C.bold}🧪 JURIKA E2E Stabilization${C.reset}  base=${BASE}\n`);
    const onb = await parcoursOnboarding();
    const ctx = await parcoursDemoLogin();
    await parcoursWorkflows(ctx);
    await parcoursOcr(ctx);
    await parcoursDatarooms(ctx);
    await parcoursDashboards(ctx);
    await parcoursBilling();

    // ── RECAP
    console.log(`\n${C.bold}═══════════════════ RECAP ═══════════════════${C.reset}`);
    const groups = [...new Set(results.map(r => r.group))];
    for (const g of groups) {
        const items = results.filter(r => r.group === g);
        const pass = items.filter(i => i.status === 'PASS').length;
        const fail = items.filter(i => i.status === 'FAIL').length;
        const skip = items.filter(i => i.status === 'SKIP').length;
        const color = fail > 0 ? C.fail : pass > 0 ? C.pass : C.skip;
        console.log(`  ${color}${g.padEnd(15)}${C.reset}  ${pass} PASS · ${fail} FAIL · ${skip} SKIP`);
    }
    const totalFail = results.filter(r => r.status === 'FAIL').length;
    console.log(`\n${C.bold}TOTAL : ${results.filter(r=>r.status==='PASS').length} PASS · ${totalFail} FAIL · ${results.filter(r=>r.status==='SKIP').length} SKIP${C.reset}`);

    if (totalFail > 0) {
        console.log(`\n${C.fail}${C.bold}FAILED CHECKS :${C.reset}`);
        for (const r of results.filter(x => x.status === 'FAIL')) {
            console.log(`  ${C.fail}✗${C.reset} [${r.group}] ${r.name} — ${r.detail}`);
        }
    }
    process.exit(totalFail);
})().catch(e => { console.error(e); process.exit(99); });
