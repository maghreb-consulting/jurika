#!/usr/bin/env node
/**
 * scripts/smoke-test.mjs
 *
 * Sprint Beta (pricing-deploy) — TASK 9.
 *
 * Verifie en ~30 secondes que la stack DEMO est fonctionnelle :
 *   1. 10/10 services UP (healthcheck Docker + actuator)
 *   2. GET /api/v1/public/pricing renvoie les 4 forfaits du CDC 4.3 (Essentiel,
 *      Professionnel, Max, Sur Devis) avec leurs quotas et prix HT (lot L7)
 *   3. Inscription publique d'un cabinet de test (supprime en fin)
 *   4. Quota d'utilisateurs : non verifiable ici (pas de connexion au compte
 *      cree) -- SKIP explicite, jamais un PASS ; couvert par les tests d'integration
 *   5. Paiement par carte (CMI, lot L7) : liaison configuree ou NON, lue sur
 *      billing-service (GET /api/v1/billing/cmi/etat) ; non configuree = SKIP
 *      explicite (noms des parametres manquants), jamais un PASS
 *   6. Au moins 1 email envoye (MailHog UI :8025 ou Brevo log)
 *
 * Sortie : ANSI colore, PASS/FAIL par check, code exit 0 si tout OK.
 *
 * Usage :
 *   node scripts/smoke-test.mjs                  # full
 *   node scripts/smoke-test.mjs --base=http://192.168.1.42
 *   node scripts/smoke-test.mjs --skip-cmi       # sans controle du CMI
 *   node scripts/smoke-test.mjs --skip-cleanup   # garde le workspace test
 */

import { spawnSync } from 'node:child_process';
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');

const COLORS = {
    pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
    dim: '\x1b[90m', reset: '\x1b[0m', bold: '\x1b[1m',
};

// ─── Args ──────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const opt = (k, d) => {
    const m = args.find(a => a.startsWith(`--${k}=`));
    return m ? m.split('=')[1] : d;
};
const flag = (k) => args.includes(`--${k}`);

const BASE = opt('base', null);
const SKIP_CMI = flag('skip-cmi');
const SKIP_CLEANUP = flag('skip-cleanup');

// ─── Charge env ────────────────────────────────────────────────────────
async function loadEnv() {
    for (const f of [join(PROJECT_ROOT, '.env.local'), join(PROJECT_ROOT, '.env')]) {
        try {
            await access(f, fsConstants.R_OK);
            const raw = await readFile(f, 'utf8');
            for (const line of raw.split(/\r?\n/)) {
                const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
                if (m && !process.env[m[1]]) {
                    process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
                }
            }
        } catch { /* ok */ }
    }
}

// ─── Resultat ──────────────────────────────────────────────────────────
const results = [];
function record(name, status, detail = '') {
    results.push({ name, status, detail });
    const color = status === 'PASS' ? COLORS.pass : status === 'FAIL' ? COLORS.fail : COLORS.skip;
    console.log(`${color}[${status}]${COLORS.reset} ${name}${detail ? '  ' + COLORS.dim + detail + COLORS.reset : ''}`);
}

function passOrFail(cond, name, okDetail = '', koDetail = '') {
    record(name, cond ? 'PASS' : 'FAIL', cond ? okDetail : koDetail);
    return cond;
}

// ─── 1) 10/10 healthy via Docker inspect (+ fallback actuator pour le mode hote) ───
async function check10Healthy() {
    // L'infra reste forcement en Docker (postgres / redis / rabbit / minio / mailhog),
    // mais les microservices Java peuvent tourner soit en conteneurs ("jurika-auth"...)
    // soit en JVM sur l'hote (start-all.ps1 / start-local.sh). On accepte les deux.
    const dockerOnly = [
        'jurika-postgres', 'jurika-redis', 'jurika-rabbitmq',
    ];
    // Lot L0 (E16e) : les ports des services ne sont publies que sur 127.0.0.1
    // (IPv4). `localhost` peut se resoudre en ::1 d'abord (fetch de Node >= 17) :
    // on vise 127.0.0.1 explicitement. Passerelle et front restent publies partout.
    const services = [
        { name: 'discovery',   url: `http://127.0.0.1:${process.env.DISCOVERY_PORT || 8761}/actuator/health` },
        { name: 'gateway',     url: `http://localhost:${process.env.GATEWAY_PORT   || 8080}/actuator/health` },
        { name: 'auth',        url: `http://127.0.0.1:${process.env.AUTH_SERVICE_PORT        || 8081}/actuator/health` },
        { name: 'ticket',      url: `http://127.0.0.1:${process.env.TICKET_SERVICE_PORT      || 8082}/actuator/health` },
        { name: 'workflow',    url: `http://127.0.0.1:${process.env.WORKFLOW_SERVICE_PORT    || 8083}/actuator/health` },
        { name: 'dataroom',    url: `http://127.0.0.1:${process.env.DATAROOM_SERVICE_PORT    || 8084}/actuator/health` },
        { name: 'supervision', url: `http://127.0.0.1:${process.env.SUPERVISION_SERVICE_PORT || 8086}/actuator/health` },
        { name: 'dashboard',   url: `http://127.0.0.1:${process.env.DASHBOARD_SERVICE_PORT   || 8087}/actuator/health` },
        { name: 'billing',     url: `http://127.0.0.1:${process.env.BILLING_SERVICE_PORT     || 8090}/actuator/health` },
        // FRONTEND_PORT vaut 80 en prod nginx mais 5173 sur Vite dev — on accepte les deux.
        { name: 'frontend',    url: `http://localhost:5173/`, fallbackUrl: `http://localhost:${process.env.FRONTEND_PORT || 80}/`, expectStatus: 200 },
    ];
    let healthy = 0, missing = [];
    const total = dockerOnly.length + services.length;

    // 1a) Infra : exige Docker container = healthy.
    for (const c of dockerOnly) {
        const r = spawnSync('docker', ['inspect', '--format={{.State.Health.Status}}', c],
                            { encoding: 'utf8' });
        const state = (r.stdout || '').trim();
        if (state === 'healthy') healthy++;
        else missing.push(`${c}=${state || 'missing'}`);
    }

    // 1b) Microservices : Docker container healthy OU HTTP actuator/health -> UP.
    for (const s of services) {
        const containerName = `jurika-${s.name}`;
        const dInspect = spawnSync('docker', ['inspect', '--format={{.State.Health.Status}}', containerName],
                                    { encoding: 'utf8' });
        const dockerState = (dInspect.stdout || '').trim();
        if (dockerState === 'healthy') { healthy++; continue; }

        const urlsToTry = [s.url, s.fallbackUrl].filter(Boolean);
        let matched = false, lastStatus = null;
        for (const u of urlsToTry) {
            try {
                const res = await fetch(u, { signal: AbortSignal.timeout(3000) });
                const ok = s.expectStatus
                    ? res.status === s.expectStatus
                    : res.ok && (await res.json()).status === 'UP';
                if (ok) { matched = true; break; }
                lastStatus = res.status;
            } catch (e) {
                lastStatus = lastStatus || 'unreachable';
            }
        }
        if (matched) { healthy++; continue; }
        missing.push(`${s.name}=${dockerState || 'no-docker'}+${lastStatus}`);
    }

    passOrFail(
        healthy === total,
        `Healthchecks (${healthy}/${total})`,
        `${healthy} composants UP (docker + actuator combine)`,
        missing.join(', '));
    return healthy === total;
}

// ─── HTTP helper minimaliste avec fetch natif ─────────────────────────
async function http(method, path, { body = null, token = null, base = null } = {}) {
    const url = `${base || BASE_URL}${path}`;
    const headers = { 'Accept': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let bodyStr = null;
    if (body) {
        headers['Content-Type'] = 'application/json';
        bodyStr = JSON.stringify(body);
    }
    let res;
    try {
        res = await fetch(url, { method, headers, body: bodyStr });
    } catch (e) {
        return { ok: false, status: 0, body: null, networkError: e.message };
    }
    let json = null;
    try { json = await res.json(); } catch { /* not json */ }
    return { ok: res.ok, status: res.status, body: json };
}

// ─── 2) Pricing endpoint ──────────────────────────────────────────────
async function checkPricing() {
    const res = await http('GET', '/api/v1/public/pricing');
    if (!res.ok || !res.body) {
        record('GET /public/pricing', 'FAIL', `${res.status} ${res.networkError || ''}`);
        return false;
    }
    const tiers = res.body.tiers || [];
    const codes = tiers.map(t => t.code).sort();
    // Lot L7 (E5) : grille du CDC 4.3 (prix HT ; quotas : employes en plus du superviseur,
    // dossiers par mois d'abonnement, stockage).
    const expected = ['essentiel', 'max', 'professionnel', 'sur_devis'];
    const ok = JSON.stringify(codes) === JSON.stringify(expected);
    const grille = { essentiel: [1, 25, 5, 375, 3750], professionnel: [2, 50, 10, 750, 7500], max: [4, 100, 25, 1500, 15000] };
    const ecarts = Object.entries(grille).filter(([code, [u, d, go, m, a]]) => {
        const t = tiers.find(x => x.code === code);
        return !t || t.quotas.maxUsers !== u || t.quotas.maxDossiers !== d || t.quotas.maxStorageGb !== go
            || t.monthly?.priceMad !== m || t.yearly?.priceMad !== a;
    }).map(([code]) => code);
    return passOrFail(
        ok && ecarts.length === 0,
        '/api/v1/public/pricing (4 forfaits du CDC 4.3 : quotas et prix HT)',
        'Essentiel, Professionnel, Max, Sur Devis conformes',
        `tiers=${codes.join(',')} ecarts=${ecarts.join(',')}`);
}

// ─── 3+4) Signup -> trial + plan limit users ──────────────────────────
let TEST_WORKSPACE_CODE = null;
let TEST_ADMIN_TOKEN = null;
// Lot L5 (E3) : resultat REEL de l'envoi du courriel de bienvenue (reponse d'inscription).
let SIGNUP_EMAIL_DELIVERED = null;

async function checkSignupAndPlanLimit() {
    const ts = Math.floor(Date.now() / 1000);
    const wsName = `SmokeTest Cabinet ${ts}`;
    // Lot L5 (E3) : le courriel de bienvenue part a l'adresse `email` (le formulaire n'en saisit
    // plus qu'une). Destinataire reel facultatif : SMOKE_CONTACT_EMAIL (.env.local), sous-adresse
    // "+smoke-<horodatage>" ; a defaut, une adresse @jurika.test (acceptee par le relais, puis
    // rejetee par le destinataire).
    const reel = (process.env.SMOKE_CONTACT_EMAIL || '').match(/^([^@\s+]+)@([^@\s]+)$/);
    const adminEmail = reel ? `${reel[1]}+smoke-${ts}@${reel[2]}` : `admin+smoke-${ts}@jurika.test`;
    // Payload aligne sur SignupCabinetRequest.java (DTO sprint 11 wizard) :
    //   contactEmail (cabinet) != email (admin) ; phone +212[5-7]\d{8} ; rcNumber digits only ;
    //   cguAccepted (et pas consentTerms) ; pas de prefix adminX.
    const payload = {
        workspaceName: wsName,
        contactEmail: `contact+smoke-${ts}@jurika.test`,
        ice: '999999999000099',
        ifFiscal: '99999999',
        rcNumber: '12345678',  // 1-20 digits regex
        city: 'Casablanca',
        firstName: 'Smoke',
        lastName: 'Test',
        email: adminEmail,
        phone: '+212600000000',
        selectedPlan: 'essentiel', // Essentiel : 1 superviseur + 1 employe (CDC 4.3)
        cguAccepted: true,
    };
    // Le controller maps PostMapping("/cabinet") sous @RequestMapping("/api/v1/public/signup").
    const res = await http('POST', '/api/v1/public/signup/cabinet', { body: payload });
    if (!res.ok) {
        record('Inscription publique d\'un cabinet', 'FAIL', `${res.status} ${res.body?.message || ''}`);
        record('Quota d\'utilisateurs', 'SKIP', 'inscription en echec');
        return;
    }
    TEST_WORKSPACE_CODE = res.body?.workspaceCode;
    // La route publique d'inscription rend le resultat de l'envoi sous `verifyEmailSent`
    // (PublicSignupController) ; `emailDelivered` est le nom des autres routes d'auth.
    SIGNUP_EMAIL_DELIVERED = (res.body?.verifyEmailSent ?? res.body?.emailDelivered) === true;
    record('Inscription publique d\'un cabinet', 'PASS', `workspace=${TEST_WORKSPACE_CODE}`);

    // Pour le check plan-limit, il faut un login admin + ajouter des users.
    // Le signup Sprint 11 envoie le mot de passe temporaire par email. On
    // ne peut donc pas se logger sans access a la mailbox.
    // -> on triche : on appelle directement le check via la table users.
    // Comme on n'a pas non plus d'access DB depuis ce script (sauf si pg
    // installe), on se contente d'un check INDIRECT : verifier que /usage
    // est protege (401 anonymous) et que /public/pricing montre la limite
    // (PlanCatalog -> 2 users).
    const usageAnon = await http('GET', '/api/v1/workspace/usage');
    const cond401 = usageAnon.status === 401 || usageAnon.status === 403;
    record('/workspace/usage protege (auth requise)', cond401 ? 'PASS' : 'FAIL',
        `status=${usageAnon.status}`);

    // Lot L7 : le quota d'utilisateurs n'est PAS verifie ici (aucune connexion au compte cree).
    // L'ancien PASS ecrit en dur affirmait un controle qui ne s'executait pas (motif 9) :
    // SKIP explicite ; le refus est couvert par les tests d'integration (auth, PlanLimitsService).
    record('Quota d\'utilisateurs', 'SKIP', 'non verifiable sans connexion au compte cree (tests d\'integration)');
}

// ─── 5) Paiement par carte : CMI (lot L7) ─────────────────────────────────
// Controle HONNETE : la liaison est lue sur billing-service (et non deduite de variables
// locales). Non configuree : SKIP qui nomme les parametres manquants (le paiement par
// carte est alors refuse avec un message ; virement, cheque, especes restent possibles).
// Simulateur actif : signale (aucun paiement reel). Aucune valeur n'est affichee.
async function checkCmi() {
    if (SKIP_CMI) { record('Paiement par carte (CMI)', 'SKIP', '--skip-cmi'); }
    else {
        const etat = await http('GET', '/api/v1/billing/cmi/etat');
        if (!etat.ok || !etat.body) {
            record('Paiement par carte (CMI) : etat de la liaison', 'FAIL', `status=${etat.status} ${etat.networkError || ''}`);
        } else if (!etat.body.configure) {
            record('Paiement par carte (CMI) : non configure', 'SKIP',
                `parametres manquants : ${(etat.body.manquants || []).join(', ')}`);
        } else {
            record(etat.body.simulateur ? 'Paiement par carte : SIMULATEUR du CMI (aucun paiement reel)'
                : 'Paiement par carte : CMI configure', 'PASS', 'valeurs non affichees');
        }
    }

    // Test que billing-service repond en healthcheck. Lot L0 (E16e) : son port
    // n'est publie que sur 127.0.0.1 (comme les autres services, cf. checkHealth) :
    // le controle se fait depuis la machine, sur 127.0.0.1.
    const health = await http('GET', '/actuator/health',
        { base: process.env.BILLING_HEALTH_URL || `http://127.0.0.1:${process.env.BILLING_SERVICE_PORT || 8090}` });
    passOrFail(health.ok && health.body?.status === 'UP',
        'billing-service /actuator/health UP',
        '', `status=${health.status}`);
}

// ─── 6) Email ──────────────────────────────────────────────────────────
// Lot L5 (E3) : le controle lisait seulement SMTP_HOST et annoncait "envoi reel" alors
// qu'auth envoyait vers localhost:1025 (backlog L1, motif 9). Il juge desormais le
// resultat de l'envoi du courriel de bienvenue rendu par l'inscription.
async function checkEmail() {
    const relais = `${process.env.SMTP_HOST || '?'}:${process.env.SMTP_PORT || '?'}`;
    if (SIGNUP_EMAIL_DELIVERED === null) {
        record('Courriel de bienvenue envoye', 'SKIP', 'inscription KO, aucun envoi a juger');
    } else if (SIGNUP_EMAIL_DELIVERED) {
        record('Courriel de bienvenue envoye', 'PASS', `relais ${relais} (accepte par le relais)`);
    } else {
        record('Courriel de bienvenue envoye', 'FAIL',
            `emailDelivered=false (relais ${relais}) : voir docker logs jurika-auth | grep "Courriels :"`);
    }
}

// ─── 7) Cleanup (workspace test) ──────────────────────────────────────
// Lot L5 (E3) : le cabinet de test est supprime (scripts/supprimer-cabinets-de-test.sql,
// par l'entree standard de psql dans jurika-postgres ; garde-fou sur le nom
// "SmokeTest Cabinet <horodatage>"), puis son absence est verifiee.
function psql(sql, extraArgs = []) {
    const user = process.env.POSTGRES_USER || 'jurika_user';
    return spawnSync('docker', ['exec', '-i', 'jurika-postgres', 'psql', '-U', user, '-d', 'jurika_db',
                                '-v', 'ON_ERROR_STOP=1', '-tA', ...extraArgs],
                     { input: sql, encoding: 'utf8' });
}

async function cleanup() {
    if (!TEST_WORKSPACE_CODE) return;
    if (SKIP_CLEANUP) {
        record('Cabinet de test supprime', 'SKIP', `--skip-cleanup : ${TEST_WORKSPACE_CODE} conserve`);
        return;
    }
    if (!/^JUR-[A-Z0-9]+$/.test(TEST_WORKSPACE_CODE)) {
        record('Cabinet de test supprime', 'FAIL', `code inattendu : ${TEST_WORKSPACE_CODE}`);
        return;
    }
    const sql = await readFile(join(PROJECT_ROOT, 'scripts', 'supprimer-cabinets-de-test.sql'), 'utf8');
    const del = psql(sql, ['-v', `code=${TEST_WORKSPACE_CODE}`]);
    const reste = psql(`SELECT count(*) FROM workspaces WHERE code = '${TEST_WORKSPACE_CODE}';`);
    const n = (reste.stdout || '').trim();
    if (del.status === 0 && reste.status === 0 && n === '0') {
        record('Cabinet de test supprime', 'PASS', TEST_WORKSPACE_CODE);
    } else {
        const err = ((del.stderr || '') + (reste.stderr || '')).split('\n').filter(l => /ERROR|erreur/i.test(l)).join(' ');
        record('Cabinet de test supprime', 'FAIL',
            `${TEST_WORKSPACE_CODE} (reste=${n || '?'}) ${err}`.trim());
    }
}

// ─── Main ──────────────────────────────────────────────────────────────
let BASE_URL = '';
(async () => {
    await loadEnv();
    BASE_URL = BASE || `http://${process.env.JURIKA_LAN_HOST || 'localhost'}:${process.env.GATEWAY_PORT || 8080}`;
    console.log(`${COLORS.bold}🔥 JURIKA Smoke Test${COLORS.reset}  base=${BASE_URL}\n`);

    await check10Healthy();
    await checkPricing();
    await checkSignupAndPlanLimit();
    await checkCmi();
    await checkEmail();
    await cleanup();

    const failed = results.filter(r => r.status === 'FAIL').length;
    const skipped = results.filter(r => r.status === 'SKIP').length;
    const passed = results.filter(r => r.status === 'PASS').length;
    console.log(`\n${COLORS.bold}Resultat : ${COLORS.pass}${passed} PASS${COLORS.reset}${COLORS.bold}` +
                (failed ? `, ${COLORS.fail}${failed} FAIL${COLORS.reset}${COLORS.bold}` : '') +
                (skipped ? `, ${COLORS.skip}${skipped} SKIP${COLORS.reset}${COLORS.bold}` : '') +
                `${COLORS.reset}`);
    process.exit(failed > 0 ? 1 : 0);
})().catch(err => {
    console.error(`${COLORS.fail}ERROR${COLORS.reset}`, err.message);
    if (err.stack) console.error(err.stack);
    process.exit(2);
});
