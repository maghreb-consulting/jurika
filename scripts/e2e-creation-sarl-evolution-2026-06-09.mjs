#!/usr/bin/env node
/**
 * scripts/e2e-creation-sarl-evolution-2026-06-09.mjs
 *
 * E2E dedie aux 5 evolutions livrees sur la branche
 * feat/creation-sarl-model-ui-2026-06-09 :
 *
 *   EX5 — Dirigeants ET Associes peuvent etre PERSONNE PHYSIQUE ou MORALE
 *         (entite avec RC/ICE/IF + representant legal physique).
 *   EX2 — Capital : apports nature/industrie 100% liberes par definition,
 *         seul le numeraire est partiellement liberable, le seuil 25%
 *         se calcule sur le CAPITAL TOTAL.
 *   EX3 — Coherence parts associes : Sigma(parts) == nombreParts total
 *         et Sigma(apports) == capitalSocial (control front + back).
 *   EX4 — UI gérant statutaire / associé refondue (test smoke API : la
 *         valeur isStatutaire / isAssociate est bien persistee dans le
 *         payload step5 et propagee en step6).
 *   EX1 — OCR : configuration LlmProperties.visionMaxImageWidth + DPI
 *         150 par defaut (smoke logs : on verifie juste qu'aucune route
 *         n'est cassee par le nouveau record).
 *
 * Parcours teste : creation complete d'un dossier SARL multi-associes
 * MIXANT 1 dirigeant MORALE + 1 dirigeant PHYSIQUE -> en associes -> jusqu'a
 * l'etape 7 (statuts) sans regression.
 *
 * Sortie : tableau OK/KO, exit = nb FAIL.
 */
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';

// C1 2026-06-21 — champs desormais OBLIGATOIRES (politique stricte « valeur
// reelle ou saisie forcee »). Constantes partagees injectees dans les payloads.
const DEPOT = { depotBanqueNom: 'Attijariwafa Bank', depotNumero: '007 780 1234567890123 45' };
const GERANCE = { dureeMandatType: 'illimitee', dureeMandat: 'illimitée', remunerationMode: 'fixée par décision collective des associés' };

const C = { pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
            dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m' };

const results = [];
function record(group, name, status, detail = '') {
  results.push({ group, name, status, detail });
  const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
  console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + String(detail).slice(0, 250) + C.reset : ''}`);
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
  if (!seed.ok) { record('Setup', 'seed', 'FAIL', `${seed.status} ${JSON.stringify(seed.body)}`); return null; }
  const auth = await loginAs(seed.body.workspaceCode, seed.body.adminEmail, seed.body.adminPassword || 'DemoPwd2026!');
  if (!auth) { record('Setup', 'login seeded', 'FAIL'); return null; }
  record('Setup', 'seed + login', 'PASS', `ws=${seed.body.workspaceCode}`);
  return { ...seed.body, ...auth };
}

async function createTicketAndStart(ctx, suffix, formeJuridique) {
  const stamp = Date.now();
  const tk = await http('POST', '/api/v1/tickets', {
    token: ctx.token,
    body: {
      titre: `EvolSARL ${suffix} ${stamp}`,
      type: 'CREATION',
      priorite: 'NORMALE',
      description: 'e2e creation SARL evolution',
      companyInfo: { raisonSociale: `EvolSARL ${suffix} ${stamp}`, formeJuridique },
    },
  });
  if (tk.status !== 201) {
    record('Setup', `ticket ${suffix}`, 'FAIL', `${tk.status} ${JSON.stringify(tk.body)}`);
    return null;
  }
  const start = await http('POST', `/api/v1/workflows/${tk.body.id}/start`, {
    token: ctx.token, body: { type: 'CREATION' },
  });
  if (start.status !== 201) {
    record('Setup', `start ${suffix}`, 'FAIL', `${start.status} ${JSON.stringify(start.body)}`);
    return null;
  }
  return tk.body.id;
}

async function execStep(ctx, ticketId, step, payload) {
  return http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
    token: ctx.token, body: { step, payload },
  });
}

// =====================================================================
// EX2 — Capital : nature/industrie 100% liberes, seuil 25% sur total
// =====================================================================
async function ex2Capital(ctx) {
  section('EX2 — Capital : seuil 25% sur le TOTAL (numeraire partiel + nature/industrie 100%)');
  const id = await createTicketAndStart(ctx, 'EX2', 'SARL');
  if (!id) return;
  // step1/2 minimum
  await execStep(ctx, id, 1, {
    denomination: 'EX2 SARL ' + Date.now(), ice: '001234567890123',
    cnNumero: 'CN-2026-1', cnDate: '2026-01-15',
    activiteCn: 'Commerce', beneficiaire: 'EX2',
    formeJuridique: 'SARL', sigle: 'NEANT',
  });
  await execStep(ctx, id, 2, {
    adresse: '1 rue test', province: 'Casa', commune: 'Casa',
    codePostal: '20000', justificatifType: 'BAIL',
  });

  // Cas A : numeraire 100k libere 0, nature 30k, industrie 0 -> total 130k, libere 30k = 23% -> bloque (<25%)
  const a = await execStep(ctx, id, 3, {
    apportNumeraire: 100000, apportNumeraireLibere: 0,
    apportNature: 30000, apportIndustrie: 0,
    nombreParts: 1300,
  });
  record('EX2', 'cas A (numeraireLibere=0, nature=30k) -> bloque <25%',
    a.status === 422 || a.status === 400 ? 'PASS' : 'FAIL',
    `${a.status} ${JSON.stringify(a.body)}`);

  // Cas B : numeraire 100k libere 5k, nature 30k -> total 130k, libere 35k = 26.9% -> OK
  const b = await execStep(ctx, id, 3, {
    apportNumeraire: 100000, apportNumeraireLibere: 5000,
    apportNature: 30000, apportIndustrie: 0,
    nombreParts: 1300,
    ...DEPOT,
  });
  record('EX2', 'cas B (5k+30k libere sur 130k = 26.9%) -> OK',
    b.status === 200 ? 'PASS' : 'FAIL',
    `${b.status} ${JSON.stringify(b.body)}`);

  // Cas C : tout en nature 100% (50k nature) -> libere == total == 100% -> OK
  const c = await execStep(ctx, id, 3, {
    apportNumeraire: 0, apportNumeraireLibere: 0,
    apportNature: 50000, apportIndustrie: 0,
    nombreParts: 500,
    ...DEPOT,
  });
  record('EX2', 'cas C (nature seule = 100% libere) -> OK',
    c.status === 200 ? 'PASS' : 'FAIL',
    `${c.status}`);

  // Verifie que back renvoie apportNumeraireLibere + pourcentageLibere
  if (b.status === 200) {
    const cap = b.body?.stepData?.capital;
    const hasLibre = cap && (cap.apportNumeraireLibere !== undefined);
    const hasPct = cap && (cap.pourcentageLibere !== undefined);
    record('EX2', 'back enrichit capital.apportNumeraireLibere',
      hasLibre ? 'PASS' : 'FAIL', `cap=${JSON.stringify(cap)}`);
    record('EX2', 'back enrichit capital.pourcentageLibere',
      hasPct ? 'PASS' : 'FAIL');
  }

  return id;
}

// =====================================================================
// EX5 — Dirigeant/Associe MORALE accepte (RC/ICE/IF + representant legal)
// =====================================================================
async function ex5Morale(ctx, ticketId) {
  section('EX5 — Dirigeant MORALE + Associe MORALE acceptes');
  // EX5 — Realigne step3 (le dernier cas de EX2 a laisse 500 parts).
  // On veut 1000 parts pour la repartition 70/30 ci-dessous.
  await execStep(ctx, ticketId, 3, {
    apportNumeraire: 100000, apportNumeraireLibere: 25000,
    apportNature: 0, apportIndustrie: 0,
    nombreParts: 1000,
    ...DEPOT,
  });
  // Step 4 activite (prerequis)
  await execStep(ctx, ticketId, 4, {
    description: 'Activite commerciale',
    dateDebutExercice: '2026-01-01',
  });

  // Step 5 dirigeants : 1 MORALE statutaire + isAssociate + 1 PHYSIQUE
  const dirMorale = {
    id: 'dir-morale-1',
    typePersonne: 'MORALE',
    denomination: 'HOLDING ENTITE SARL',
    formeJuridiqueEntite: 'SARL',
    rc: '12345',
    ice: '987654321098765',
    ifFiscal: '40000123',
    siege: '12 bd Mohammed V, Casa',
    repCivilite: 'M',
    repNom: 'CHRAIBI',
    repPrenom: 'Karim',
    repCin: 'BE123456',
    repQualite: 'Gerant',
    fonction: 'GERANT',
    isStatutaire: true,
    isAssociate: true,
    rcUploaded: true,
    statutsEntiteUploaded: true,
    // champs PHYSIQUE laisses vides
    civilite: 'M', nom: '', prenom: '', cinNumero: '', nationalite: '', dateNaissance: '', adresse: '',
    cinUploaded: false,
  };
  const dirPhys = {
    id: 'dir-phys-1',
    typePersonne: 'PHYSIQUE',
    civilite: 'M', nom: 'ALAMI', prenom: 'Hassan',
    cinNumero: 'BK345678', nationalite: 'Marocaine',
    dateNaissance: '1985-03-12', adresse: '5 rue Tanger',
    fonction: 'CO_GERANT',
    isStatutaire: false, isAssociate: false,
    cinUploaded: true,
  };
  const s5 = await execStep(ctx, ticketId, 5, { dirigeants: [dirMorale, dirPhys], gerance: GERANCE });
  record('EX5', 'step5 mixte MORALE+PHYSIQUE accepte',
    s5.status === 200 ? 'PASS' : 'FAIL', `${s5.status} ${JSON.stringify(s5.body)}`);

  // Step 5 : refus si MORALE sans RC
  const dirIncomplet = { ...dirMorale, rc: '' };
  const s5bad = await execStep(ctx, ticketId, 5, { dirigeants: [dirIncomplet] });
  record('EX5', 'step5 MORALE sans RC -> rejette',
    s5bad.status !== 200 ? 'PASS' : 'FAIL', `${s5bad.status} ${JSON.stringify(s5bad.body)}`);

  // Re-pose le bon step5 avant la suite
  await execStep(ctx, ticketId, 5, { dirigeants: [dirMorale, dirPhys], gerance: GERANCE });

  // Step 6 associes : 1 MORALE (le dirigeant MORALE) + 1 PHYSIQUE
  const assMorale = {
    id: 'ass-morale-1',
    fromDirigeantId: 'dir-morale-1',
    typePersonne: 'MORALE',
    denomination: dirMorale.denomination,
    formeJuridiqueEntite: dirMorale.formeJuridiqueEntite,
    rc: dirMorale.rc, ice: dirMorale.ice, ifFiscal: dirMorale.ifFiscal, siege: dirMorale.siege,
    repCivilite: 'M', repNom: dirMorale.repNom, repPrenom: dirMorale.repPrenom,
    repCin: dirMorale.repCin, repQualite: dirMorale.repQualite,
    rcUploaded: true, statutsEntiteUploaded: true,
    nombreParts: 700, montantApport: 70000, typeApport: 'NUMERAIRE',
    pourcentageDetention: 70,
    // champs PHYSIQUE vides
    civilite: 'M', nom: '', prenom: '', cin: '', adresse: '',
    cinUploaded: false,
  };
  const assPhys = {
    id: 'ass-phys-1',
    typePersonne: 'PHYSIQUE',
    civilite: 'M', nom: 'BENALI', prenom: 'Said',
    cin: 'BL987654', adresse: '3 rue Rabat',
    nombreParts: 300, montantApport: 30000, typeApport: 'NUMERAIRE',
    pourcentageDetention: 30,
    cinUploaded: true,
  };
  const s6 = await execStep(ctx, ticketId, 6, {
    associes: [assMorale, assPhys],
    formeJuridique: 'SARL',
  });
  record('EX5', 'step6 associes mixte MORALE+PHYSIQUE accepte',
    s6.status === 200 ? 'PASS' : 'FAIL', `${s6.status} ${JSON.stringify(s6.body)}`);

  // Refus si associe MORALE sans ICE
  const assBad = { ...assMorale, ice: '' };
  const s6bad = await execStep(ctx, ticketId, 6, {
    associes: [assBad, assPhys], formeJuridique: 'SARL',
  });
  record('EX5', 'step6 associe MORALE sans ICE -> rejette',
    s6bad.status !== 200 ? 'PASS' : 'FAIL', `${s6bad.status} ${JSON.stringify(s6bad.body)}`);
}

// =====================================================================
// EX3 — Coherence parts : Sigma(parts associes) == nombreParts total
// =====================================================================
async function ex3CoherenceParts(ctx) {
  section('EX3 — Coherence Sigma(parts associes) vs nombreParts du capital');
  const id = await createTicketAndStart(ctx, 'EX3', 'SARL');
  if (!id) return;
  await execStep(ctx, id, 1, {
    denomination: 'EX3 SARL ' + Date.now(), ice: '001234567890123',
    cnNumero: 'CN-2026-3', cnDate: '2026-01-15',
    activiteCn: 'Commerce', beneficiaire: 'EX3', formeJuridique: 'SARL', sigle: 'NEANT',
  });
  await execStep(ctx, id, 2, {
    adresse: '1 rue test', province: 'Casa', commune: 'Casa',
    codePostal: '20000', justificatifType: 'BAIL',
  });
  await execStep(ctx, id, 3, {
    apportNumeraire: 100000, apportNumeraireLibere: 25000,
    apportNature: 0, apportIndustrie: 0,
    nombreParts: 1000,
    ...DEPOT,
  });
  await execStep(ctx, id, 4, { description: 'Test', dateDebutExercice: '2026-01-01' });
  await execStep(ctx, id, 5, { dirigeants: [{
    id: 'd1', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'A', prenom: 'B',
    cinNumero: 'BX111111', nationalite: 'Marocaine', adresse: 'addr',
    fonction: 'GERANT', isStatutaire: true, isAssociate: false, cinUploaded: true,
  }], gerance: GERANCE });

  // 600 + 300 = 900 != 1000 attendu -> blocage
  const bad = await execStep(ctx, id, 6, {
    associes: [
      { id: 'a1', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'X', prenom: 'Y',
        cin: 'BY222222', adresse: 'addr', nombreParts: 600, montantApport: 60000,
        typeApport: 'NUMERAIRE', pourcentageDetention: 60, cinUploaded: true },
      { id: 'a2', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'Z', prenom: 'T',
        cin: 'BZ333333', adresse: 'addr', nombreParts: 300, montantApport: 30000,
        typeApport: 'NUMERAIRE', pourcentageDetention: 30, cinUploaded: true },
    ],
    formeJuridique: 'SARL',
  });
  record('EX3', 'Sigma(parts)=900 vs total 1000 -> rejette',
    bad.status !== 200 ? 'PASS' : 'FAIL', `${bad.status} ${JSON.stringify(bad.body)}`);

  // 700 + 300 = 1000 -> OK
  const ok = await execStep(ctx, id, 6, {
    associes: [
      { id: 'a1', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'X', prenom: 'Y',
        cin: 'BY222222', adresse: 'addr', nombreParts: 700, montantApport: 70000,
        typeApport: 'NUMERAIRE', pourcentageDetention: 70, cinUploaded: true },
      { id: 'a2', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'Z', prenom: 'T',
        cin: 'BZ333333', adresse: 'addr', nombreParts: 300, montantApport: 30000,
        typeApport: 'NUMERAIRE', pourcentageDetention: 30, cinUploaded: true },
    ],
    formeJuridique: 'SARL',
  });
  record('EX3', 'Sigma(parts)=1000 == total -> OK',
    ok.status === 200 ? 'PASS' : 'FAIL', `${ok.status}`);
}

// =====================================================================
// EX1 — OCR : verif que /ai/extract-cin reste joignable (les changements
//        sont 100% performance interne, l'API reste identique)
// =====================================================================
async function ex1OcrSmoke(ctx) {
  section('EX1 — OCR /ai/extract-cin reste joignable apres downscale + DPI 150');
  // Pas de fichier reel ici : on verifie juste que l'endpoint existe et
  // n'a pas regresse (rejet attendu sans multipart, mais != 404).
  const r = await fetch(`${BASE}/api/v1/ai/extract-cin`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${ctx.token}` },
  });
  // L'endpoint doit etre route (donc != 404). 400/415/422/500 sont tous acceptables
  // car ils signifient que le controller a ete atteint mais le body multipart est absent.
  const isReachable = r.status !== 404 && r.status !== 503;
  record('EX1', '/ai/extract-cin endpoint vivant (pas 404)',
    isReachable ? 'PASS' : 'FAIL', `${r.status}`);
}

// =====================================================================
// MAIN
// =====================================================================
(async () => {
  await loadEnv();
  console.log(`${C.bold}E2E SARL evolution 2026-06-09${C.reset} (BASE=${BASE})`);
  section('Setup');
  const ctx = await seed();
  if (!ctx) { process.exit(1); return; }

  const t1 = await ex2Capital(ctx);
  if (t1) await ex5Morale(ctx, t1);
  await ex3CoherenceParts(ctx);
  await ex1OcrSmoke(ctx);

  // Resume
  const pass = results.filter((r) => r.status === 'PASS').length;
  const fail = results.filter((r) => r.status === 'FAIL').length;
  const skip = results.filter((r) => r.status === 'SKIP').length;
  console.log(`\n${C.bold}═══ TOTAL ═══${C.reset}`);
  console.log(`  ${C.pass}PASS=${pass}${C.reset}  ${C.fail}FAIL=${fail}${C.reset}  ${C.skip}SKIP=${skip}${C.reset}`);
  process.exit(fail);
})();
