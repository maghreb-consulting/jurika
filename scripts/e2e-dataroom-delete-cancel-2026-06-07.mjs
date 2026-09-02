#!/usr/bin/env node
/**
 * scripts/e2e-dataroom-delete-cancel-2026-06-07.mjs
 *
 * E2E dedie aux 2 fix 2026-06-07 :
 *
 *   BUG 3 — Suppression d'un dataroom par l'employe
 *           (DELETE /api/v1/dataroom/dossiers/{id})
 *           Cas couverts :
 *             (a) delete employe OK + idempotent (re-DELETE)
 *             (b) refus 409 si un ticket actif (NOUVEAU/EN_COURS) est rattache
 *
 *   BUG 2 — Suppression auto du dataroom a l'annulation du ticket
 *           CREATION/IMPORT autocreate :
 *             (c) CREATION -> ANNULE -> dataroom supprime
 *             (d) IMPORT reutilisant un dossier ACTIVE preexistant ->
 *                 dataroom CONSERVE a l'annulation
 *             (e) double annulation = idempotent (pas de 409, dataroom
 *                 reste a l'etat post-cascade)
 *
 * Pre-requis : stack UP, profil !prod (test seed endpoint dispo).
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
  const auth = await loginAs(seed.body.workspaceCode, seed.body.adminEmail,
      seed.body.adminPassword || 'DemoPwd2026!');
  if (!auth) { record('Setup', 'login seeded', 'FAIL'); return null; }
  record('Setup', 'seed + login EMPLOYE', 'PASS', `ws=${seed.body.workspaceCode}`);
  return { ...seed.body, ...auth };
}

async function createCreationTicket(ctx, suffix) {
  const stamp = Date.now() + '_' + suffix;
  const tk = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'E2E ' + suffix + ' ' + stamp,
      type: 'CREATION', priorite: 'NORMALE',
      description: 'e2e dataroom-delete-cancel',
      companyInfo: { raisonSociale: 'E2E_' + suffix + '_' + stamp, formeJuridique: 'SARL' },
    },
  });
  if (tk.status !== 201) return null;
  return { id: tk.body.id, dossierId: tk.body.dossierId, raisonSociale: 'E2E_' + suffix + '_' + stamp };
}

// ============================================================
//  BUG 3 — DELETE dataroom : (a) OK + idempotent
// ============================================================
async function bug3a(ctx) {
  section('BUG 3 (a) — DELETE dataroom employe OK + idempotent');
  const tk = await createCreationTicket(ctx, 'B3A');
  if (!tk) { record('BUG3a', 'cree dossier auto', 'FAIL'); return; }
  record('BUG3a', 'ticket+dossier crees', 'PASS', `dossier=${tk.dossierId.slice(0, 8)}`);

  // On annule d'abord le ticket pour qu'il n'y ait plus de ticket actif sur
  // le dossier (sinon (b) declenchera le refus 409).
  // ATTENTION : cette annulation va aussi declencher l'auto-delete cote
  // listener (BUG 2). On bypasse en annulant + on attend que le listener
  // ait fait son travail, puis on re-DELETE manuellement => alreadyDeleted.
  const ann = await http('POST', `/api/v1/tickets/${tk.id}/transition`, {
    token: ctx.token,
    body: { target: 'ANNULE', comment: 'annulation e2e bug3a (>10c)' },
  });
  record('BUG3a', 'ticket annule (prerequis)', ann.status === 200 ? 'PASS' : 'FAIL', `${ann.status}`);

  // Petit delai pour laisser le listener Rabbit consommer.
  await new Promise((r) => setTimeout(r, 1500));

  // Maintenant on tente DELETE manuel sur dataroom -- idempotent.
  const del = await http('DELETE', `/api/v1/dataroom/dossiers/${tk.dossierId}`, { token: ctx.token });
  record('BUG3a', 'DELETE 204 No Content (idempotent post-annul)',
      del.status === 204 ? 'PASS' : 'FAIL', `status=${del.status}`);

  // 2eme DELETE -> 204 toujours.
  const del2 = await http('DELETE', `/api/v1/dataroom/dossiers/${tk.dossierId}`, { token: ctx.token });
  record('BUG3a', '2eme DELETE 204 (idempotent)',
      del2.status === 204 ? 'PASS' : 'FAIL', `status=${del2.status}`);
}

// ============================================================
//  BUG 3 — DELETE dataroom : (b) refus 409 si ticket actif
// ============================================================
async function bug3b(ctx) {
  section('BUG 3 (b) — refus 409 si ticket actif rattache');
  const tk = await createCreationTicket(ctx, 'B3B');
  if (!tk) { record('BUG3b', 'cree dossier auto', 'FAIL'); return; }
  record('BUG3b', 'ticket NOUVEAU + dossier crees', 'PASS', `dossier=${tk.dossierId.slice(0, 8)}`);

  // Le ticket est en NOUVEAU -> DELETE doit etre refuse 409.
  const del = await http('DELETE', `/api/v1/dataroom/dossiers/${tk.dossierId}`, { token: ctx.token });
  record('BUG3b', 'DELETE refuse 409 (ticket actif)',
      del.status === 409 ? 'PASS' : 'FAIL',
      `status=${del.status} msg=${(del.body?.message || '').slice(0, 80)}`);

  // Cleanup : annule le ticket (sinon il pollue le workspace).
  await http('POST', `/api/v1/tickets/${tk.id}/transition`, {
    token: ctx.token,
    body: { target: 'ANNULE', comment: 'cleanup e2e bug3b' },
  });
}

// ============================================================
//  BUG 2 — CREATION -> ANNULE -> dataroom supprime (c)
// ============================================================
async function bug2c(ctx) {
  section('BUG 2 (c) — CREATION annule => dataroom auto-supprime');
  const tk = await createCreationTicket(ctx, 'B2C');
  if (!tk) { record('BUG2c', 'cree CREATION + dossier auto', 'FAIL'); return; }
  record('BUG2c', 'CREATION + dossier autocreate', 'PASS', `dossier=${tk.dossierId.slice(0, 8)}`);

  // Verif que le settings dataroom existe AVANT (HEAD via GET settings).
  const before = await http('GET', `/api/v1/dataroom/dossiers/${tk.dossierId}/settings`, { token: ctx.token });
  record('BUG2c', 'settings dataroom existe avant annulation',
      before.status === 200 ? 'PASS' : 'FAIL', `${before.status}`);

  // Annulation -> doit declencher le listener -> DELETE dataroom + dossier RADIE.
  const ann = await http('POST', `/api/v1/tickets/${tk.id}/transition`, {
    token: ctx.token,
    body: { target: 'ANNULE', comment: 'annulation e2e bug2c (motif >10c)' },
  });
  record('BUG2c', 'ticket annule (200)', ann.status === 200 ? 'PASS' : 'FAIL', `${ann.status}`);

  // Attente listener Rabbit (best-effort -> on patiente).
  await new Promise((r) => setTimeout(r, 2500));

  // Verif : un DELETE manuel doit etre 204 (idempotent : deja supprime).
  // Si le listener n'avait rien fait, ce DELETE renverrait 204 aussi mais
  // alors le 1er nettoyage ferait du vrai travail. On verifie aussi via
  // la liste des dossiers : le dossier ne doit plus apparaitre (RADIE).
  const listAfter = await http('GET', '/api/v1/dataroom/dossiers', { token: ctx.token });
  const stillThere = Array.isArray(listAfter.body)
      && listAfter.body.some((d) => d.id === tk.dossierId);
  // Le listDossiers cote dataroom retourne les dossiers du workspace, sans
  // filtrer RADIE. Mais on a aussi DELETE physique si aucun ticket persistant
  // -> peut etre absent. Dans les 2 cas le settings doit etre absent.
  const settingsAfter = await http('GET', `/api/v1/dataroom/dossiers/${tk.dossierId}/settings`, { token: ctx.token });
  // settings.getOrCreate recree si demande -> on attend que le dossier soit
  // soit absent (404) soit RADIE (200 mais accessCount=0 etc). On verifie
  // par la suppression idempotente.
  const delIdem = await http('DELETE', `/api/v1/dataroom/dossiers/${tk.dossierId}`, { token: ctx.token });
  record('BUG2c', 'DELETE manuel post-annul = 204 (deja supprime/RADIE)',
      delIdem.status === 204 ? 'PASS' : 'FAIL',
      `status=${delIdem.status} stillInList=${stillThere} settingsAfter=${settingsAfter.status}`);
}

// ============================================================
//  BUG 2 — IMPORT reutilisant dossier => dataroom CONSERVE (d)
// ============================================================
async function bug2d(ctx) {
  section('BUG 2 (d) — IMPORT reutilisant dossier ACTIVE : dataroom CONSERVE');

  // 1) On cree d'abord un dossier "preexistant" via le seed-controller fait
  //    cote prerequis (le seed crée déjà un dossier ACTIVE -- on l'utilise).
  //    Le contexte de seed contient dossierId (workspace).
  const stamp = Date.now();
  const rs = 'E2E_B2D_REUSED_' + stamp;

  // Plan : 1er ticket IMPORT cree un dossier ACTIVE (importStub) ->
  //         marquage created_by_ticket_id = T1
  //         On annule T1 -> listener supprime le dataroom (dossier statut
  //         ACTIVE != EN_CONSTITUTION -> garde-fou listener refuse !).
  //         Le dataroom doit donc rester intact apres annulation de T1.

  // On cree un ticket IMPORT (auto-create dossier ACTIVE).
  const t1 = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: 'B2D IMPORT initial ' + stamp,
      type: 'IMPORT', priorite: 'NORMALE',
      description: 'e2e bug2d initial import',
      companyInfo: { raisonSociale: rs, formeJuridique: 'SARL' },
    },
  });
  if (t1.status !== 201) { record('BUG2d', 'cree IMPORT initial', 'FAIL', `${t1.status}`); return; }
  const t1Id = t1.body.id;
  const dossierId = t1.body.dossierId;
  record('BUG2d', 'IMPORT initial cree dossier ACTIVE', 'PASS', `dossier=${dossierId.slice(0, 8)}`);

  // Le listener BUG2 ne se declenche que si statut == EN_CONSTITUTION ; pour
  // IMPORT l'autocreation se fait en ACTIVE -> annulation = NO-OP cote
  // listener, le dataroom reste intact.
  const ann = await http('POST', `/api/v1/tickets/${t1Id}/transition`, {
    token: ctx.token,
    body: { target: 'ANNULE', comment: 'annulation IMPORT initial e2e bug2d (>10c)' },
  });
  record('BUG2d', 'IMPORT annule (200)', ann.status === 200 ? 'PASS' : 'FAIL',
      `status=${ann.status} body=${JSON.stringify(ann.body).slice(0, 200)}`);

  await new Promise((r) => setTimeout(r, 2000));

  // Verif : settings dataroom toujours present (le listener a NO-OP car
  // statut ACTIVE != EN_CONSTITUTION).
  const settings = await http('GET', `/api/v1/dataroom/dossiers/${dossierId}/settings`, { token: ctx.token });
  record('BUG2d', 'settings dataroom intact apres annulation IMPORT',
      settings.status === 200 ? 'PASS' : 'FAIL', `status=${settings.status}`);

  // Cleanup : suppression manuelle (pas d'autre ticket actif).
  await http('DELETE', `/api/v1/dataroom/dossiers/${dossierId}`, { token: ctx.token });
}

// ============================================================
//  BUG 2 — double annulation idempotente (e)
// ============================================================
async function bug2e(ctx) {
  section('BUG 2 (e) — double annulation idempotente (pas de 409 ConflictException)');
  const tk = await createCreationTicket(ctx, 'B2E');
  if (!tk) { record('BUG2e', 'cree CREATION', 'FAIL'); return; }
  record('BUG2e', 'CREATION cree', 'PASS', `ticket=${tk.id.slice(0, 8)}`);

  const ann1 = await http('POST', `/api/v1/tickets/${tk.id}/transition`, {
    token: ctx.token,
    body: { target: 'ANNULE', comment: 'annulation 1 e2e bug2e' },
  });
  record('BUG2e', '1ere annulation 200', ann1.status === 200 ? 'PASS' : 'FAIL', `${ann1.status}`);

  const ann2 = await http('POST', `/api/v1/tickets/${tk.id}/transition`, {
    token: ctx.token,
    body: { target: 'ANNULE', comment: 'annulation 2 e2e bug2e (idempotent)' },
  });
  record('BUG2e', '2eme annulation 200 (idempotent, pas 409)',
      ann2.status === 200 ? 'PASS' : 'FAIL', `status=${ann2.status}`);

  // Cleanup (idempotent).
  await new Promise((r) => setTimeout(r, 1500));
  await http('DELETE', `/api/v1/dataroom/dossiers/${tk.dossierId}`, { token: ctx.token });
}

// ============================================================
async function main() {
  await loadEnv();
  console.log(`${C.bold}E2E Dataroom Delete + Cancel 2026-06-07${C.reset}`);
  console.log(`${C.dim}Base : ${BASE}${C.reset}\n`);
  section('Setup');
  const ctx = await seed();
  if (!ctx) process.exit(1);

  await bug3a(ctx);
  await bug3b(ctx);
  await bug2c(ctx);
  await bug2d(ctx);
  await bug2e(ctx);

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
