#!/usr/bin/env node
/**
 * E2E "generation creation SARL" — 2026-06-09.
 *
 * Lance le test d'integration Java `CreationSarlGenerationIT` qui :
 *   1. genere reellement les .docx (Statuts SARL/AU, JAL SARL/AU, Acte) avec deux
 *      payloads de test (SARL 3 associes dont 1 PM ; SARL_AU apport mixte) ;
 *   2. re-ouvre chaque .docx et assert AUCUN placeholder residuel `{{...}}` ou
 *      `${...}` ;
 *   3. verifie la regle metier : ACTE_NOMINATION_GERANT genere uniquement si au
 *      moins un gerant n'est pas statutaire ;
 *   4. ecrit un rapport markdown consommable par la direction sous
 *      `backend-java/ai-service/target/e2e-creation-sarl/`.
 *
 * Usage : `node scripts/e2e-creation-sarl-generation-2026-06-09.mjs`
 *
 * Pas de backend requis : tout est in-process via le moteur de templates.
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const projectRoot = resolve(dirname(__filename), '..');
const backendJava = resolve(projectRoot, 'backend-java');
const outBase = resolve(
  backendJava,
  'ai-service/target/e2e-creation-sarl',
);

const cases = [
  {
    label: 'SARL 3 associes (PP+PP+PM) + mix gerants',
    dir: resolve(outBase, 'SARL'),
    expected: [
      'STATUTS_CONSTITUTIFS_SARL.docx',
      'ANNONCE_JAL_SARL.docx',
      // 2026-06-09 (fix LLM-off) : nouvelle variante directeur
      // ACTE_NOMINATION_GERANT_SARL.docx (▶ ASSOCIES blocs) + alias deprecated
      // ACTE_NOMINATION_GERANT.docx pour compat ascendante.
      'ACTE_NOMINATION_GERANT_SARL.docx',
      'ACTE_NOMINATION_GERANT.docx',
      'DECLARATION_SOUSCRIPTION_VERSEMENT.docx',
    ],
    expectActe: true,
  },
  {
    label: 'SARL_AU + apport mixte + gerant non statutaire',
    dir: resolve(outBase, 'SARL_AU'),
    expected: [
      'STATUTS_CONSTITUTIFS_SARL_AU.docx',
      'ANNONCE_JAL_SARL_AU.docx',
      // 2026-06-09 (fix LLM-off) : variante SARL_AU dediee, scalaire
      // ASSOCIE_NOM (pas de bloc ▶ ASSOCIES — 1 seul associe).
      'ACTE_NOMINATION_GERANT_SARL_AU.docx',
    ],
    expectActe: true,
  },
];

console.log('=== e2e CREATION_SARL generation (2026-06-09) ===');
console.log('Module : backend-java/ai-service');
console.log('Test   : CreationSarlGenerationIT (2 cas)\n');

const res = spawnSync(
  'mvn -pl ai-service test -Dtest=CreationSarlGenerationIT -q',
  {
    cwd: backendJava,
    shell: true,
    encoding: 'utf8',
    maxBuffer: 32 * 1024 * 1024,
  },
);

if (res.error) {
  console.error('spawnSync error :', res.error.message);
}
if (res.signal) {
  console.error('Tue par signal :', res.signal);
}

const stdout = (res.stdout ?? '').toString();
const stderr = (res.stderr ?? '').toString();

let pass = 0;
let fail = 0;

if (res.status !== 0) {
  console.error('--- mvn stdout (tail) ---');
  console.error(stdout.split('\n').slice(-40).join('\n'));
  console.error('--- mvn stderr (tail) ---');
  console.error(stderr.split('\n').slice(-20).join('\n'));
  console.error('\n[FAIL] mvn test a echoue (exit ' + res.status + ').');
  process.exit(1);
}

console.log('[PASS] mvn test -Dtest=CreationSarlGenerationIT (exit 0)\n');

for (const c of cases) {
  console.log(`Cas : ${c.label}`);
  console.log(`  Dossier de sortie : ${c.dir}`);
  for (const f of c.expected) {
    const p = resolve(c.dir, f);
    if (existsSync(p)) {
      const size = readFileSync(p).length;
      console.log(`  [OK]   ${f} (${size} octets)`);
      pass++;
    } else {
      console.error(`  [FAIL] ${f} manquant`);
      fail++;
    }
  }
  const reportPath = resolve(c.dir, 'REPORT.md');
  if (existsSync(reportPath)) {
    const report = readFileSync(reportPath, 'utf8');
    const residualMatch = report.match(/Placeholders residuels : (\d+)/g) ?? [];
    const totalResiduals = residualMatch.reduce(
      (acc, m) => acc + parseInt(m.replace(/\D/g, ''), 10),
      0,
    );
    if (totalResiduals === 0) {
      console.log('  [OK]   Zero placeholder residuel sur tous les documents');
      pass++;
    } else {
      console.error(
        `  [FAIL] ${totalResiduals} placeholder(s) residuel(s) detecte(s)`,
      );
      fail++;
    }
    console.log(`  Rapport : ${reportPath}`);
  } else {
    console.error('  [FAIL] REPORT.md absent');
    fail++;
  }
  console.log('');
}

// Bilan
console.log('=== Bilan ===');
console.log(`PASS : ${pass}`);
console.log(`FAIL : ${fail}`);

if (fail > 0) {
  process.exit(1);
}
console.log('\n[SUCCESS] Tous les controles sont verts.');
process.exit(0);
