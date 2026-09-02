#!/usr/bin/env node
/**
 * scripts/e2e-workflows-prod.mjs
 *
 * E2E API dedie aux 3 workflows PRODUCTION
 * MODIFICATION / DISSOLUTION / LIQUIDATION.
 *
 * Couvre par workflow :
 *   - start (POST /workflows/{ticket}/start)
 *   - chaque etape avec VALIDATION metier (cas erreur FR clair)
 *   - chaque etape en SUCCESS (parcours nominal)
 *   - navigation arriere (saveDraft + execute-step idempotent)
 *   - generation document (POST ai-service via workflow-document si dispo)
 *   - transitions ticket NOUVEAU -> EN_COURS -> CLOTURE
 *
 * Pre-req : seed endpoint /api/v1/test/seed/workspace actif (profil !prod).
 * Sortie : tableau OK/KO, exit = nb FAIL.
 */
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';
const SEED_PATH = '/api/v1/test/seed/workspace';

const C = { pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
            dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m' };

const results = [];
function record(group, name, status, detail = '') {
    results.push({ group, name, status, detail });
    const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
    console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + detail.slice(0, 220) + C.reset : ''}`);
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
        } catch {}
    }
}

async function http(method, path, { body = null, token = null } = {}) {
    const url = `${BASE}${path}`;
    const headers = { Accept: 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let bodyStr = null;
    if (body) { headers['Content-Type'] = 'application/json'; bodyStr = JSON.stringify(body); }
    let res;
    try { res = await fetch(url, { method, headers, body: bodyStr }); }
    catch (e) { return { ok: false, status: 0, body: null, networkError: e.message }; }
    let parsed = null;
    try { parsed = await res.json(); } catch {}
    return { ok: res.ok, status: res.status, body: parsed };
}

function section(t) { console.log(`\n${C.bold}${C.cyan}═══ ${t} ═══${C.reset}`); }

async function loginAs(wsCode, email, pwd) {
    const r = await http('POST', '/api/v1/auth/login',
        { body: { workspaceCode: wsCode, email, password: pwd } });
    return r.ok ? { token: r.body.accessToken, userId: r.body.userId, wsId: r.body.workspaceId } : null;
}

async function seedEmployeeWorkspace() {
    const seed = await http('POST', `${SEED_PATH}?role=EMPLOYE`, { body: {} });
    if (!seed.ok) {
        record('Setup', 'seed workspace (EMPLOYE)', 'FAIL',
            `${seed.status} ${JSON.stringify(seed.body).slice(0, 200)}`);
        return null;
    }
    const auth = await loginAs(seed.body.workspaceCode, seed.body.adminEmail,
        seed.body.adminPassword || 'DemoPwd2026!');
    if (!auth) {
        record('Setup', 'login seeded admin', 'FAIL'); return null;
    }
    record('Setup', 'seed + login', 'PASS', `ws=${seed.body.workspaceCode}`);
    return { ...seed.body, ...auth };
}

async function pickDossier(token, expectedStatuts = ['ACTIVE', 'EN_CONSTITUTION']) {
    const d = await http('GET', '/api/v1/dataroom/dossiers', { token });
    if (!d.ok) return null;
    const list = Array.isArray(d.body) ? d.body : (d.body?.items || []);
    return list.find((x) => expectedStatuts.includes(String(x.statut).toUpperCase())) ?? list[0] ?? null;
}

async function createTicketAndStart(token, dossierId, type, titrePrefix) {
    const tk = await http('POST', '/api/v1/tickets', {
        token,
        body: {
            titre: `${titrePrefix} ${Date.now()}`,
            type,
            priorite: 'NORMALE',
            dossierId,
            description: `E2E prod ${type}`,
        },
    });
    if (!tk.ok) {
        record(type, 'POST /tickets', 'FAIL',
            `${tk.status} ${JSON.stringify(tk.body).slice(0, 200)}`);
        return null;
    }
    record(type, 'POST /tickets', 'PASS', `id=${tk.body.id?.slice(0, 8)}`);
    const start = await http('POST', `/api/v1/workflows/${tk.body.id}/start`, {
        token, body: { type },
    });
    if (!start.ok) {
        record(type, `POST /workflows/start ${type}`, 'FAIL',
            `${start.status} ${JSON.stringify(start.body).slice(0, 200)}`);
        return null;
    }
    record(type, `POST /workflows/start ${type}`, 'PASS',
        `step=${start.body?.currentStep}/${start.body?.totalSteps}`);
    return { ticketId: tk.body.id, progress: start.body };
}

async function expectStepError(token, ticketId, step, payload, label, expectedFragmentLc) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
        token, body: { step, payload },
    });
    if (r.status === 400) {
        const msg = (r.body?.message || r.body?.detail || JSON.stringify(r.body) || '').toLowerCase();
        const ok = !expectedFragmentLc || msg.includes(expectedFragmentLc);
        record('VALIDATION', label, ok ? 'PASS' : 'FAIL',
            ok ? `400 "${msg.slice(0, 80)}"` : `400 mais sans fragment "${expectedFragmentLc}" (msg="${msg.slice(0, 80)}")`);
        return ok;
    }
    record('VALIDATION', label, 'FAIL', `expected 400, got ${r.status}`);
    return false;
}

async function expectStepOk(token, ticketId, step, payload, label) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
        token, body: { step, payload },
    });
    if (r.ok && r.body?.advanced) {
        record('NOMINAL', label, 'PASS', `advanced -> step ${r.body.progress?.currentStep}`);
        return r.body;
    }
    record('NOMINAL', label, 'FAIL',
        `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    return null;
}

async function autoTransitionsCheck(token, ticketId, label) {
    const t = await http('GET', `/api/v1/tickets/${ticketId}`, { token });
    const statut = t.body?.statut;
    record('TRANSITION', `${label} ticket statut`, statut === 'CLOTURE' ? 'PASS' : 'FAIL',
        `statut=${statut}`);
}

// ============================================================
//  MODIFICATION (4 steps) — parcours nominal + cas erreurs
// ============================================================
async function workflowModification(ctx) {
    section('MODIFICATION (RG-M01..M19)');
    const dossier = await pickDossier(ctx.token);
    if (!dossier) { record('MODIFICATION', 'pre-req dossier', 'SKIP', 'aucun dossier disponible'); return; }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'MODIFICATION', 'E2E MOD');
    if (!wf) return;

    // Step 1 erreurs
    await expectStepError(ctx.token, wf.ticketId, 1, { selectedTypes: [] },
        'step1 sans types', 'au moins');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { selectedTypes: ['TYPE_INCONNU_XYZ'], decisionType: 'AGE', datePV: '2026-06-01' },
        'step1 type inconnu', 'non reconnu');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { selectedTypes: ['CHANGEMENT_DENOMINATION'], decisionType: 'XX', datePV: '2026-06-01' },
        'step1 decisionType invalide', 'invalide');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { selectedTypes: ['CHANGEMENT_DENOMINATION'], decisionType: 'AGE', datePV: 'pas-une-date' },
        'step1 datePV format', 'invalide');

    // Step 1 OK (decisionType coherent : si dossier=SARL_AU => AU, sinon AGE)
    const forme = String(dossier.formeJuridique || '').toUpperCase();
    const decisionType = forme === 'SARL_AU' ? 'AU' : 'AGE';
    const step1 = await expectStepOk(ctx.token, wf.ticketId, 1, {
        selectedTypes: ['CHANGEMENT_DENOMINATION', 'TRANSFERT_SIEGE'],
        decisionType, datePV: '2026-06-01',
    }, 'step1 ok (CHANGEMENT_DENOM + TRANSFERT_SIEGE)');
    if (!step1) return;

    // Verifier les flags retournes par step1
    const s1Data = step1.progress?.data?.step1 || step1.stepData;
    const jalOk = s1Data?.jalRequis === true; // les 2 types declencheurs JAL
    record('MODIFICATION', 'step1 jalRequis=true', jalOk ? 'PASS' : 'FAIL',
        `jalRequis=${s1Data?.jalRequis}`);

    // Step 2 erreurs (denomination trop courte)
    await expectStepError(ctx.token, wf.ticketId, 2,
        { valeurs: { CHANGEMENT_DENOMINATION: { nouvelleDenomination: 'AB' }, TRANSFERT_SIEGE: {} } },
        'step2 denomination courte', 'denomination');

    // Step 2 OK
    await expectStepOk(ctx.token, wf.ticketId, 2, {
        valeurs: {
            CHANGEMENT_DENOMINATION: { nouvelleDenomination: 'SARL Nouvelle Denomination' },
            TRANSFERT_SIEGE: { nouvelleAdresse: '12 rue de la paix', nouvelleVille: 'Casablanca' },
        }
    }, 'step2 ok');

    // Navigation arriere : on retourne sur step 1, on resauvegarde, HWM ne regresse pas
    const back = await http('POST', `/api/v1/workflows/${wf.ticketId}/save`, {
        token: ctx.token,
        body: { currentStep: 1, data: { navTest: true } },
    });
    record('MODIFICATION', 'saveDraft retour step 1', back.ok ? 'PASS' : 'FAIL',
        `currentStep=${back.body?.currentStep}`);
    const hwmKept = back.body?.currentStep >= 3;
    record('MODIFICATION', 'HWM preserve apres goPrev', hwmKept ? 'PASS' : 'FAIL',
        `hwm=${back.body?.currentStep}`);

    // Step 3 OK (idempotent : on peut continuer apres save back)
    await expectStepOk(ctx.token, wf.ticketId, 3, {}, 'step3 ok (generation)');

    // Step 4 erreur (pvValide manquant)
    await expectStepError(ctx.token, wf.ticketId, 4, {},
        'step4 sans pvValide', 'pv');
    await expectStepError(ctx.token, wf.ticketId, 4, { pvValide: true },
        'step4 sans statutsValides', 'statut');
    // Step 4 erreur : jal requis
    if (jalOk) {
        await expectStepError(ctx.token, wf.ticketId, 4,
            { pvValide: true, statutsValides: true },
            'step4 jalRequis sans jalValide', 'annonce');
    }
    // Step 4 OK final
    const fin = await expectStepOk(ctx.token, wf.ticketId, 4,
        { pvValide: true, statutsValides: true, jalValide: true },
        'step4 finalise');
    if (fin) await autoTransitionsCheck(ctx.token, wf.ticketId, 'MODIFICATION');
}

// ============================================================
//  DISSOLUTION (3 steps)
// ============================================================
async function workflowDissolution(ctx) {
    section('DISSOLUTION (RG-DI01..05)');
    const dossier = await pickDossier(ctx.token, ['ACTIVE', 'EN_CONSTITUTION']);
    if (!dossier) { record('DISSOLUTION', 'pre-req dossier ACTIVE', 'SKIP'); return; }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'DISSOLUTION', 'E2E DISS');
    if (!wf) return;

    // Step 1 erreurs
    await expectStepError(ctx.token, wf.ticketId, 1, {},
        'step1 sans dossier', 'societe');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { dossierId: 'pas-un-uuid', motifDissolution: 'x'.repeat(25), dateAGE: '2026-06-01' },
        'step1 dossier invalide', 'invalide');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { dossierId: dossier.id, motifDissolution: 'court', dateAGE: '2026-06-01' },
        'step1 motif court', 'court');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { dossierId: dossier.id, motifDissolution: 'x'.repeat(25), dateAGE: '2050-01-01' },
        'step1 date AGE futur', 'futur');

    // Step 1 OK
    const motif = 'Cessation totale d\'activite suite a la perte de marche principal cabinet 2026';
    const step1 = await expectStepOk(ctx.token, wf.ticketId, 1,
        { dossierId: dossier.id, motifDissolution: motif, dateAGE: '2026-06-01' },
        'step1 ok');
    if (!step1) return;

    // Step 2 erreur
    await expectStepError(ctx.token, wf.ticketId, 2, { pvValide: false },
        'step2 pvValide=false', 'pv');
    // Step 2 OK
    await expectStepOk(ctx.token, wf.ticketId, 2, { pvValide: true }, 'step2 ok');
    // Step 3 OK final
    const fin = await expectStepOk(ctx.token, wf.ticketId, 3, {}, 'step3 finalise');
    if (fin) await autoTransitionsCheck(ctx.token, wf.ticketId, 'DISSOLUTION');
}

// ============================================================
//  LIQUIDATION (3 steps)
// ============================================================
async function workflowLiquidation(ctx) {
    section('LIQUIDATION (RG-LI01..06)');
    const dossier = await pickDossier(ctx.token, ['DISSOUTE', 'EN_LIQUIDATION', 'ACTIVE']);
    // Tolerant si pas de DISSOUTE seedee : on prend un ACTIVE et le check serveur acceptera
    // mais en mode strict, le test sera SKIP si on n'a pas un dossier eligible.
    if (!dossier) { record('LIQUIDATION', 'pre-req dossier', 'SKIP'); return; }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'LIQUIDATION', 'E2E LIQ');
    if (!wf) return;

    // Step 1 erreurs
    await expectStepError(ctx.token, wf.ticketId, 1, {},
        'step1 sans dossier', 'societe');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { dossierId: dossier.id, dateDissolution: 'bad' },
        'step1 date format', 'invalide');
    await expectStepError(ctx.token, wf.ticketId, 1,
        { dossierId: dossier.id, dateDissolution: '2099-01-01' },
        'step1 date future', 'futur');

    // Step 1 OK (date dissolution = il y a 20 jours -> delai 16j respecte)
    const dt = new Date(Date.now() - 20 * 24 * 3600 * 1000).toISOString().slice(0, 10);
    const step1 = await expectStepOk(ctx.token, wf.ticketId, 1,
        { dossierId: dossier.id, dateDissolution: dt },
        'step1 ok (delai 16j respecte)');
    if (!step1) {
        // Le serveur peut bloquer si dossier !DISSOUTE — on log et on quitte le scenario
        record('LIQUIDATION', 'step1 dossier eligibility (server-side)', 'SKIP',
            'dossier ne pas DISSOUTE — workflow demo limite');
        return;
    }

    // Step 2 erreurs structurees
    await expectStepError(ctx.token, wf.ticketId, 2, {},
        'step2 sans liquidateur', 'liquidateur');
    await expectStepError(ctx.token, wf.ticketId, 2,
        {
            liquidateur: { nom: 'X', cin: 'A1234', nationalite: 'Marocaine', adresse: 'Casa', accepteMandat: true },
        },
        'step2 sans comptes', 'comptes');
    await expectStepError(ctx.token, wf.ticketId, 2,
        {
            liquidateur: { nom: 'X', cin: 'A1234', nationalite: 'Marocaine', adresse: 'Casa', accepteMandat: false },
            comptesFinaux: { totalActif: 100000, totalPassif: 50000 },
            pvValide: true, rapportValide: true,
        },
        'step2 mandat non accepte', 'mandat');

    // Step 2 OK complet
    await expectStepOk(ctx.token, wf.ticketId, 2, {
        liquidateur: {
            nom: 'KARIM EL ALAOUI', cin: 'BE123456',
            nationalite: 'Marocaine', adresse: '20 bd Anfa, Casablanca',
            dateNomination: dt, accepteMandat: true,
        },
        comptesFinaux: { totalActif: 250000, totalPassif: 120000, devise: 'MAD' },
        pvValide: true, rapportValide: true,
    }, 'step2 ok complet');

    // Step 3 OK
    const fin = await expectStepOk(ctx.token, wf.ticketId, 3, {}, 'step3 finalise');
    if (fin) await autoTransitionsCheck(ctx.token, wf.ticketId, 'LIQUIDATION');
}

// ============================================================
async function main() {
    await loadEnv();
    console.log(`\n${C.bold}E2E PROD — Workflows MODIFICATION / DISSOLUTION / LIQUIDATION${C.reset}`);
    console.log(`Base: ${BASE}\n`);

    const ctx = await seedEmployeeWorkspace();
    if (!ctx) { console.error('FATAL seed/login KO'); process.exit(1); }

    await workflowModification(ctx);
    await workflowDissolution(ctx);
    await workflowLiquidation(ctx);

    const pass = results.filter((r) => r.status === 'PASS').length;
    const fail = results.filter((r) => r.status === 'FAIL').length;
    const skip = results.filter((r) => r.status === 'SKIP').length;
    console.log(`\n${C.bold}═══ TOTAL ═══${C.reset}`);
    console.log(`  ${C.pass}${pass} PASS${C.reset}  ${C.fail}${fail} FAIL${C.reset}  ${C.skip}${skip} SKIP${C.reset}`);
    process.exit(fail);
}
main().catch((e) => { console.error(e); process.exit(99); });
