#!/usr/bin/env node
/**
 * e2e — CREATION SARL conditions/format Sprint 2026-06-12.
 *
 * 3 vérifications :
 *  (1) Invariant statique sur les 6 templates patchés : aucune annotation NL
 *      résiduelle (« (Si applicable) », « (Si {{x}} = oui) », « (Boucle) »,
 *      « (etc.) », « (Variante 1) », « (Option ...) ») dans le .docx source.
 *  (2) Symétrie des marqueurs machine ◇/◆ : chaque ouverture a sa fermeture.
 *  (3) Lance les tests Java ciblés (IT) qui font la génération réelle +
 *      les assertions zéro résidu + déterminisme + présence/absence acte.
 *
 * Aucun backend live requis — purement local.
 */
import {readFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {execSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const TPL_DIR = path.join(ROOT, 'backend-java', 'ai-service', 'src', 'main', 'resources', 'templates', 'docx');

const TEMPLATES = [
    'STATUTS_CONSTITUTIFS_SARL.docx',
    'STATUTS_CONSTITUTIFS_SARL_AU.docx',
    'ANNONCE_JAL_SARL.docx',
    'ANNONCE_JAL_SARL_AU.docx',
    'ACTE_NOMINATION_GERANT_SARL.docx',
    'ACTE_NOMINATION_GERANT_SARL_AU.docx',
];

const NL_PATTERNS = [
    {name: 'Si applicable', re: /\(\s*[Ss]i\s+applicable[^)]*\)/g},
    {name: 'Si {{flag}} = oui', re: /\(\s*[Ss]i\s+\{\{[^}]+\}\}[^)]*\)/g},
    {name: 'Boucle', re: /\(\s*[Bb]oucle\s*\)/g},
    {name: 'etc.', re: /\(\s*etc\.?\s*\)/g},
    {name: 'Variante N', re: /\(\s*Variante\s+\d/g},
    {name: 'Option tacite', re: /\(\s*Option\s+tacite[^)]*\)/g},
];

let totalErrors = 0;
const report = [];

function fmt(s, n = 110) {
    return s.length > n ? s.slice(0, n) + '…' : s;
}

async function readDocxText(zipPath) {
    // python-docx style approach using JSZip via dynamic import to avoid hard dep.
    const require = createRequire(import.meta.url);
    let JSZip;
    try {
        JSZip = require('jszip');
    } catch (e) {
        console.error('!! jszip non installé — `npm install jszip --no-save` puis réessaie');
        process.exit(2);
    }
    const buf = await readFile(zipPath);
    const zip = await JSZip.loadAsync(buf);
    const xml = await zip.file('word/document.xml').async('string');
    return xml
        .replace(/<\/w:p\s*>/g, '\n')
        .replace(/<w:br\/?>/g, '\n')
        .replace(/<[^>]+>/g, '')
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'");
}

async function checkTemplate(file) {
    const full = path.join(TPL_DIR, file);
    if (!existsSync(full)) {
        report.push(`!! ${file} : introuvable`);
        totalErrors++;
        return;
    }
    const txt = await readDocxText(full);

    // (1) Annotations NL
    const nlHits = [];
    for (const {name, re} of NL_PATTERNS) {
        const matches = [...txt.matchAll(re)];
        for (const m of matches) nlHits.push({pattern: name, hit: m[0]});
    }

    // (2) Symétrie ◇/◆
    const opens = [...txt.matchAll(/◇\s+([A-Z][A-Z0-9_]*)/g)].map(m => m[1]);
    const closes = [...txt.matchAll(/◆\s+([A-Z][A-Z0-9_]*)/g)].map(m => m[1]);
    const opensSet = new Set(opens);
    const closesSet = new Set(closes);
    const missingClose = [...opensSet].filter(x => !closesSet.has(x));
    const missingOpen = [...closesSet].filter(x => !opensSet.has(x));

    if (nlHits.length === 0 && missingClose.length === 0 && missingOpen.length === 0) {
        report.push(`OK  ${file} : 0 annotation NL · ${opens.length} blocs conditionnels symétriques`);
    } else {
        totalErrors++;
        report.push(`ERR ${file}`);
        for (const {pattern, hit} of nlHits) {
            report.push(`    NL résiduel [${pattern}] : ${fmt(hit)}`);
        }
        if (missingClose.length) report.push(`    ◇ sans ◆ : ${missingClose.join(', ')}`);
        if (missingOpen.length) report.push(`    ◆ sans ◇ : ${missingOpen.join(', ')}`);
    }
}

async function main() {
    console.log('== Vérification statique des 6 templates ==');
    for (const f of TEMPLATES) await checkTemplate(f);
    for (const line of report) console.log(line);

    if (totalErrors > 0) {
        console.log(`\n${totalErrors} template(s) invalide(s)`);
        process.exit(1);
    }

    console.log('\n== Lancement des IT Java ciblés ==');
    const mvnTests = 'CreationSarlConditionsGenerationIT,CreationSarlGenerationIT,DocxTemplateEngineV2Test';
    try {
        execSync(
            `mvn -pl ai-service test -Dtest='${mvnTests}' -q`,
            {cwd: path.join(ROOT, 'backend-java'), stdio: 'inherit'}
        );
    } catch (e) {
        console.error('!! IT en échec — voir surefire-reports');
        process.exit(3);
    }

    console.log('\n== TOUS LES CHECKS PASSENT ==');
}

main().catch(e => {
    console.error(e);
    process.exit(99);
});
