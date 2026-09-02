#!/usr/bin/env node
/**
 * scripts/e2e-fix-workflows-dataroom-2026-06-07.mjs
 *
 * E2E dedie a la verif des 6 fix 2026-06-07 :
 *
 *   BUG 1 — /start CREATION + IMPORT renvoie 201, jamais 500 (stack saine)
 *   BUG 2 — Double-submit ticket CREATION = 1 SEUL dossier (idempotence)
 *   BUG 3 — la forme juridique du ticket se retrouve sur le dossier
 *   BUG 5 — Import step 2 accepte payload "documents" SANS OCR obligatoire
 *   BUG 6 — DELETE /auth/dossiers/{id}/client detache le client (idempotent)
 *
 * BUG 4 (OCR CIN dirigeants/associes) est purement front -- pas couvert
 * par cet e2e API.
 *
 * Sortie : tableau OK/KO/SKIP, exit = nb FAIL.
 */
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';

const C = { pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
            dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m' };

const results = [];
function record(group, name, status, detail = '') {
  results.push({ group, name, status, detail });
  const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
  console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + detail.slice(0, 250) + C.reset : ''}`);
}
function section(t) { console.log(`\n${C.bold}${C.cyan}═══ ${t} ═══${C.reset}`); }

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

async function loginAs(wsCode, email, pwd) {
  const r = await http('POST', '/api/v1/auth/login',
      { body: { workspaceCode: wsCode, email, password: pwd } });
  return r.ok ? { token: r.body.accessToken, userId: r.body.userId, wsId: r.body.workspaceId } : null;
}

async function seed() {
  const seed = await http('POST', '/api/v1/test/seed/workspace?role=EMPLOYE', { body: {} });
  if (!seed.ok) { record('Setup', 'seed', 'FAIL', `${seed.status}`); return null; }
  const auth = await loginAs(seed.body.workspaceCode, seed.body.adminEmail, seed.body.adminPassword || 'DemoPwd2026!');
  if (!auth) { record('Setup', 'login seeded', 'FAIL'); return null; }
  record('Setup', 'seed + login', 'PASS', `ws=${seed.body.workspaceCode}`);
  return { ...seed.body, ...auth };
}

// ============================================================
//  BUG 1 — /start sain (201, pas 500)
// ============================================================
async function bug1(ctx) {
  section('BUG 1 — /start (CREATION + IMPORT) sain');
  const stamp = Date.now();
  const tk = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'B1 CREATION ' + stamp, type: 'CREATION', priorite: 'NORMALE',
      description: 'bug1', companyInfo: { raisonSociale: 'B1C ' + stamp, formeJuridique: 'SARL' },
    },
  });
  if (tk.status === 201) record('BUG1', 'ticket CREATION 201', 'PASS');
  else { record('BUG1', 'ticket CREATION 201', 'FAIL', `got ${tk.status}`); return; }
  const start = await http('POST', `/api/v1/workflows/${tk.body.id}/start`, { token: ctx.token, body: { type: 'CREATION' } });
  record('BUG1', '/start CREATION 201 (pas 500)', start.status === 201 ? 'PASS' : 'FAIL',
      `status=${start.status} step=${start.body?.currentStep}/${start.body?.totalSteps}`);
  // idempotence /start : 2e appel doit reutiliser le progress
  const start2 = await http('POST', `/api/v1/workflows/${tk.body.id}/start`, { token: ctx.token, body: { type: 'CREATION' } });
  record('BUG1', '/start CREATION idempotent (2nd call)',
      start2.status === 201 && start2.body?.id === start.body?.id ? 'PASS' : 'FAIL',
      `status=${start2.status}`);

  // IMPORT
  const tkI = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'B1 IMPORT ' + stamp, type: 'IMPORT', priorite: 'NORMALE',
      description: 'bug1', companyInfo: { raisonSociale: 'B1I ' + stamp, formeJuridique: 'SARL_AU' },
    },
  });
  if (tkI.status === 201) record('BUG1', 'ticket IMPORT 201', 'PASS');
  const startI = await http('POST', `/api/v1/workflows/${tkI.body.id}/start`, { token: ctx.token, body: { type: 'IMPORT' } });
  record('BUG1', '/start IMPORT 201 (pas 500)', startI.status === 201 ? 'PASS' : 'FAIL',
      `status=${startI.status} step=${startI.body?.currentStep}/${startI.body?.totalSteps}`);
}

// ============================================================
//  BUG 2 — Double-submit = 1 SEUL dossier
// ============================================================
async function bug2(ctx) {
  section('BUG 2 — double-submit idempotence dossier');
  const stamp = Date.now();
  const body = {
    titre: 'B2 ' + stamp, type: 'CREATION', priorite: 'NORMALE',
    description: 'b2', companyInfo: { raisonSociale: 'IDEMPOTENT ' + stamp, formeJuridique: 'SARL' },
  };
  const [a, b] = await Promise.all([
    http('POST', '/api/v1/tickets', { token: ctx.token, body }),
    http('POST', '/api/v1/tickets', { token: ctx.token, body }),
  ]);
  const okA = a.status === 201, okB = b.status === 201;
  record('BUG2', '2 POST en parallele OK', okA && okB ? 'PASS' : 'FAIL',
      `A=${a.status} B=${b.status}`);
  if (!okA || !okB) return;
  // Verifier qu'un seul dossier porte cette raison sociale
  const dossiers = await http('GET', '/api/v1/dataroom/dossiers', { token: ctx.token });
  const list = Array.isArray(dossiers.body) ? dossiers.body : (dossiers.body?.items || []);
  const matches = list.filter((d) => d.raisonSociale === body.companyInfo.raisonSociale);
  record('BUG2', '1 SEUL dossier cree (pas 2)', matches.length === 1 ? 'PASS' : 'FAIL',
      `count=${matches.length} ids=${matches.map((m) => m.id.slice(0,8)).join(',')}`);
  // Les 2 tickets doivent pointer sur le MEME dossierId
  record('BUG2', 'meme dossierId dans les 2 tickets',
      a.body?.dossierId === b.body?.dossierId ? 'PASS' : 'FAIL',
      `A=${a.body?.dossierId?.slice(0,8)} B=${b.body?.dossierId?.slice(0,8)}`);

  // Test ICE unique : 2 tickets IMPORT avec MEME ICE doivent partager le dossier
  const ts2 = Date.now() + 1;
  const ice = '00' + ts2.toString().slice(-13);
  const bodyI = {
    titre: 'B2-ICE ' + ts2, type: 'IMPORT', priorite: 'NORMALE',
    description: 'b2-ice', companyInfo: { raisonSociale: 'ICE_TEST_' + ts2, formeJuridique: 'SARL' },
  };
  const tIa = await http('POST', '/api/v1/tickets', { token: ctx.token, body: bodyI });
  record('BUG2', 'IMPORT 1er ticket cree', tIa.status === 201 ? 'PASS' : 'FAIL', `${tIa.status}`);
  // Sur le 2eme ticket IMPORT avec meme raison sociale => meme dossier
  const tIb = await http('POST', '/api/v1/tickets', { token: ctx.token, body: bodyI });
  record('BUG2', 'IMPORT 2nd ticket reuse meme dossier',
      tIb.status === 201 && tIa.body?.dossierId === tIb.body?.dossierId ? 'PASS' : 'FAIL',
      `Ia=${tIa.body?.dossierId?.slice(0,8)} Ib=${tIb.body?.dossierId?.slice(0,8)}`);
}

// ============================================================
//  BUG 3 — Forme juridique du ticket -> dossier
// ============================================================
async function bug3(ctx) {
  section('BUG 3 — forme juridique propagee du ticket vers le dossier');
  const stamp = Date.now();
  const tk = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'B3 ' + stamp, type: 'CREATION', priorite: 'NORMALE',
      description: 'b3', companyInfo: { raisonSociale: 'FORME ' + stamp, formeJuridique: 'SARL_AU' },
    },
  });
  if (tk.status !== 201) { record('BUG3', 'ticket cree', 'FAIL', `${tk.status}`); return; }
  record('BUG3', 'ticket SARL_AU cree', 'PASS');
  const dossiers = await http('GET', '/api/v1/dataroom/dossiers', { token: ctx.token });
  const list = Array.isArray(dossiers.body) ? dossiers.body : (dossiers.body?.items || []);
  const d = list.find((x) => x.id === tk.body.dossierId);
  record('BUG3', 'dossier rattache trouve', d ? 'PASS' : 'FAIL', d ? `id=${d.id.slice(0,8)}` : '');
  if (d) {
    record('BUG3', 'forme du dossier === SARL_AU (pas redemandee)',
        d.formeJuridique === 'SARL_AU' ? 'PASS' : 'FAIL', `forme=${d.formeJuridique}`);
  }
}

// ============================================================
//  BUG 5 — Import step 2 accepte documents SANS OCR obligatoire
// ============================================================
async function bug5(ctx) {
  section('BUG 5 — Import juridique sans OCR');
  const stamp = Date.now();
  const tk = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'B5 ' + stamp, type: 'IMPORT', priorite: 'NORMALE',
      description: 'b5', companyInfo: { raisonSociale: 'B5 ' + stamp, formeJuridique: 'SARL' },
    },
  });
  if (tk.status !== 201) { record('BUG5', 'ticket IMPORT cree', 'FAIL', `${tk.status}`); return; }
  const start = await http('POST', `/api/v1/workflows/${tk.body.id}/start`, { token: ctx.token, body: { type: 'IMPORT' } });
  if (start.status !== 201) { record('BUG5', '/start IMPORT', 'FAIL', `${start.status}`); return; }
  record('BUG5', 'IMPORT start ok', 'PASS');

  // Step 1 minimum legal
  const step1 = await http('POST', `/api/v1/workflows/${tk.body.id}/execute-step`, {
    token: ctx.token,
    body: {
      step: 1,
      payload: {
        raisonSociale: 'B5 ' + stamp,
        ice: '001234567890123', rcNumero: 'RC123', ifNumero: 'IF456',
        formeJuridique: 'SARL', capital: 100000, capitalLibere: 25000,
        gerants: [{ nom: 'Karim Bennani', cin: 'BE123456' }],
      },
    },
  });
  record('BUG5', 'IMPORT step1 (avec ICE / capital 25%) ok', step1.body?.advanced ? 'PASS' : 'FAIL',
      JSON.stringify(step1.body).slice(0, 150));

  // Step 2 — BUG 5 fix : payload "documents" A LA RACINE, PAS de fields OCR
  const step2 = await http('POST', `/api/v1/workflows/${tk.body.id}/execute-step`, {
    token: ctx.token,
    body: {
      step: 2,
      payload: {
        documents: [
          { type: 'STATUTS', filename: 'statuts.pdf', sizeBytes: 12345, uploaded: true },
          { type: 'CN', filename: 'cn.pdf', sizeBytes: 6789, uploaded: false },
        ],
        // Pas de extractedFields obligatoires -- OCR optionnelle.
      },
    },
  });
  record('BUG5', 'IMPORT step2 documents accepte (200 advanced)',
      step2.body?.advanced ? 'PASS' : 'FAIL',
      `status=${step2.status} ` + JSON.stringify(step2.body).slice(0, 150));

  // Step 2 sans documents = doit echouer 400 (RG-IM02)
  const step2bad = await http('POST', `/api/v1/workflows/${tk.body.id}/execute-step`, {
    token: ctx.token,
    body: { step: 2, payload: {} },
  });
  record('BUG5', 'IMPORT step2 SANS documents reste 400 (RG-IM02 respectee)',
      step2bad.status === 400 ? 'PASS' : 'FAIL', `status=${step2bad.status}`);
}

// ============================================================
//  BUG 6 — DELETE /auth/dossiers/{id}/client
// ============================================================
async function bug6(ctx) {
  section('BUG 6 — retrait acces client');
  const stamp = Date.now();
  // 1) cree un dossier via CREATION
  const tk = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'B6 ' + stamp, type: 'CREATION', priorite: 'NORMALE',
      description: 'b6', companyInfo: { raisonSociale: 'B6_' + stamp, formeJuridique: 'SARL' },
    },
  });
  const dossierId = tk.body?.dossierId;
  if (!dossierId) { record('BUG6', 'dossier cree', 'FAIL'); return; }
  record('BUG6', 'dossier cree', 'PASS', `id=${dossierId.slice(0,8)}`);

  // 2) invite un client
  const invite = await http('POST', '/api/v1/auth/invite-client', {
    token: ctx.token,
    body: {
      dossierId,
      email: `client.b6.${stamp}@example.com`,
      firstName: 'Client', lastName: 'B6',
    },
  });
  record('BUG6', 'client invite', invite.status === 200 ? 'PASS' : 'FAIL', `${invite.status}`);

  // 3) DELETE /auth/dossiers/{id}/client -> detache
  const del = await http('DELETE', `/api/v1/auth/dossiers/${dossierId}/client`, { token: ctx.token });
  record('BUG6', 'DELETE detache le client (200)',
      del.status === 200 && !del.body?.alreadyDetached ? 'PASS' : 'FAIL',
      `status=${del.status} alreadyDetached=${del.body?.alreadyDetached}`);

  // 4) 2eme DELETE = idempotent (alreadyDetached=true)
  const del2 = await http('DELETE', `/api/v1/auth/dossiers/${dossierId}/client`, { token: ctx.token });
  record('BUG6', '2eme DELETE idempotent (alreadyDetached=true)',
      del2.status === 200 && del2.body?.alreadyDetached === true ? 'PASS' : 'FAIL',
      `status=${del2.status} alreadyDetached=${del2.body?.alreadyDetached}`);

  // 5) DELETE sur dossier inconnu = 404
  const fakeId = '00000000-0000-0000-0000-000000000000';
  const delN = await http('DELETE', `/api/v1/auth/dossiers/${fakeId}/client`, { token: ctx.token });
  record('BUG6', 'DELETE dossier inconnu = 404', delN.status === 404 ? 'PASS' : 'FAIL', `${delN.status}`);
}

// ============================================================
async function main() {
  await loadEnv();
  console.log(`${C.bold}E2E Fix Workflows + Dataroom 2026-06-07${C.reset}`);
  console.log(`${C.dim}Base : ${BASE}${C.reset}\n`);
  section('Setup');
  const ctx = await seed();
  if (!ctx) process.exit(1);
  await bug1(ctx);
  await bug2(ctx);
  await bug3(ctx);
  await bug5(ctx);
  await bug6(ctx);

  section('Resume');
  const pass = results.filter((r) => r.status === 'PASS').length;
  const fail = results.filter((r) => r.status === 'FAIL').length;
  const skip = results.filter((r) => r.status === 'SKIP').length;
  console.log(`${C.bold}Total : ${C.pass}${pass} PASS${C.reset}${C.bold} / ${C.fail}${fail} FAIL${C.reset}${C.bold} / ${C.skip}${skip} SKIP${C.reset}`);
  if (fail > 0) {
    console.log('\nFAILED :');
    for (const r of results.filter((x) => x.status === 'FAIL')) {
      console.log(`  - [${r.group}] ${r.name}${r.detail ? '  ' + r.detail : ''}`);
    }
  }
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error(C.fail + 'Erreur fatale: ' + (e?.stack || e) + C.reset);
  process.exit(2);
});
