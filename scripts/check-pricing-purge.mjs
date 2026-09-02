#!/usr/bin/env node
/**
 * scripts/check-pricing-purge.mjs
 *
 * Garde-fou anti-regression (U8 de PROMPT_CLAUDE_CODE_UNIFY_TARIFS).
 *
 * Echoue si les anciens noms de plan (professionnel, starter) ou les
 * anciens prix (1299, 3499) reapparaissent dans le code applicatif.
 *
 * Spec : output/SPEC_TARIFS_CANONIQUE.md (2026-06-02)
 * Codes canoniques : essentiel | business | entreprise
 * Prix : 499 / 1 199 / sur devis  (annuels 4 999 / 11 999)
 *
 * Zones scannees :
 *   - frontend-react/src
 *   - frontend-react/e2e
 *   - frontend-mobile/app
 *   - marketing-site/src
 *   - backend-java *\/src  (les microservices, pas le monolithe legacy)
 *   - scripts/
 *
 * Zones IGNOREES (legitimes / hors purge) :
 *   - node_modules/, dist/, build/
 *   - docs/v1/, docs/v2/             (historique projet)
 *   - output/                          (artefacts cowork)
 *   - CODE_maquette_ts/, maquettes/    (templates Figma readonly)
 *   - .git/, .logs/, .tmp/, logs/
 *   - backend-java/src/main/java/fr/   (monolithe legacy non compile)
 *   - migrations Flyway V2..V22        (les V<=22 contiennent l'historique)
 *
 * Mots-cles surveilles (insensibles a la casse, mais avec word boundaries
 * pour eviter les faux positifs sur l'adjectif francais "professionnels"
 * ou "professionnelle") :
 *   - \bprofessionnel\b       (sans 's', sans 'le' -> code plan)
 *   - \bstarter\b
 *   - \b1299\b
 *   - \b3499\b
 *
 * Exit 0 si aucune occurrence (et que landing + signup exposent la meme
 * grille — verifie via comparaison FALLBACK / config). Exit 1 sinon.
 */

import { readFile, readdir, stat } from 'node:fs/promises';
import { join, dirname, sep, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');

// ─── Configuration ────────────────────────────────────────────────────
const SCAN_PATHS = [
    'frontend-react/src',
    'frontend-react/e2e',
    'frontend-mobile/app',
    'marketing-site/src',
    'marketing-site/index.html',
    'backend-java/auth-service/src',
    'backend-java/billing-service/src',
    'backend-java/dataroom-service/src',
    'backend-java/dashboard-service/src',
    'backend-java/jurika-common/src',
    'backend-java/ticket-service/src',
    'backend-java/workflow-service/src',
    'backend-java/supervision-service/src',
    'backend-java/ai-service/src',
    'backend-java/gateway-service/src',
    'backend-java/discovery-service/src',
    'scripts',
    '.env.example',
];

const IGNORE_DIR_NAMES = new Set([
    'node_modules', '.git', '.logs', '.tmp', 'logs', 'target', 'dist',
    'build', 'out', '.next', '.vite', 'coverage', '__snapshots__',
]);

// Migrations Flyway V<=22 (contiennent l'historique des codes pre-V23).
// V23 (auth) et V3 (billing) elles-memes contiennent les UPDATE pour
// renommer professionnel -> business -- legitimement le mot apparait.
const IGNORE_FILE_PATTERNS = [
    /V\d+__.*\.sql$/i, // toutes les migrations Flyway (historique)
    /scripts[\\/]check-pricing-purge\.mjs$/i, // self : on definit les patrons ici
];

// Marqueur a poser EN LIGNE pour blanchir une occurrence legitime
// (ex. case "starter" -> normalize). Le scanner cherche cette chaine.
const ALLOW_MARKER = 'pricing-purge:ok';

// Patrons interdits — regex avec word boundaries pour limiter aux usages
// "code de plan" et pas a la prose francaise.
const FORBIDDEN_PATTERNS = [
    { name: 'professionnel (code plan)', regex: /\bprofessionnel\b/g, allow: /professionnelle|professionnels/ },
    { name: 'starter (code plan)',       regex: /\bstarter\b/gi,      allow: null },
    { name: '1299 (ancien prix)',        regex: /\b1299\b/g,          allow: null },
    { name: '3499 (ancien prix)',        regex: /\b3499\b/g,          allow: null },
];

const SCAN_EXTENSIONS = new Set([
    '.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs',
    '.java', '.yml', '.yaml', '.properties',
    '.html', '.md', '.json',
]);

// ─── Walker ───────────────────────────────────────────────────────────
async function* walk(p) {
    let s;
    try { s = await stat(p); } catch { return; }
    if (s.isFile()) { yield p; return; }
    if (!s.isDirectory()) return;
    const entries = await readdir(p, { withFileTypes: true });
    for (const e of entries) {
        if (IGNORE_DIR_NAMES.has(e.name)) continue;
        const child = join(p, e.name);
        if (e.isDirectory()) {
            yield* walk(child);
        } else if (e.isFile()) {
            yield child;
        }
    }
}

function shouldScan(filePath) {
    if (IGNORE_FILE_PATTERNS.some(re => re.test(filePath))) return false;
    const dot = filePath.lastIndexOf('.');
    if (dot < 0) return false;
    return SCAN_EXTENSIONS.has(filePath.slice(dot));
}

// ─── Scan ─────────────────────────────────────────────────────────────
const violations = [];

for (const rel of SCAN_PATHS) {
    const abs = join(ROOT, rel);
    try {
        await stat(abs);
    } catch {
        // path inexistant : on saute silencieusement (le projet peut evoluer)
        continue;
    }
    for await (const file of walk(abs)) {
        if (!shouldScan(file)) continue;
        let content;
        try { content = await readFile(file, 'utf8'); } catch { continue; }
        const lines = content.split(/\r?\n/);
        for (const pattern of FORBIDDEN_PATTERNS) {
            lines.forEach((line, idx) => {
                pattern.regex.lastIndex = 0;
                if (!pattern.regex.test(line)) return;
                // Marqueur inline pour cas legitime (ex. case d'alias normalize)
                if (line.includes(ALLOW_MARKER)) return;
                // Skip si la ligne contient un autre token autorise voisin
                if (pattern.allow && pattern.allow.test(line) && !pattern.regex.test(line.replace(pattern.allow, ''))) {
                    return;
                }
                violations.push({
                    file: relative(ROOT, file).split(sep).join('/'),
                    line: idx + 1,
                    pattern: pattern.name,
                    snippet: line.trim().slice(0, 160),
                });
            });
        }
    }
}

// ─── Coherence landing / marketing-site / backend ─────────────────────
async function readPricing(path) {
    try {
        const raw = await readFile(join(ROOT, path), 'utf8');
        // Extrait les triplets (id, price, annualPrice) pour comparaison.
        const tiers = [];
        const matches = raw.matchAll(/id:\s*['"]([^'"]+)['"][\s\S]*?price:\s*(null|\d+)[\s\S]*?annualPrice:\s*(null|\d+)/g);
        for (const m of matches) {
            tiers.push({ id: m[1], price: m[2] === 'null' ? null : Number(m[2]), annualPrice: m[3] === 'null' ? null : Number(m[3]) });
        }
        return tiers;
    } catch {
        return null;
    }
}

const landing = await readPricing('frontend-react/src/pages/landing/_marketing/config/pricing.js');
const marketing = await readPricing('marketing-site/src/config/pricing.js');

const coherenceErrors = [];
if (landing && marketing) {
    const byId = (arr) => Object.fromEntries(arr.map(t => [t.id, t]));
    const a = byId(landing), b = byId(marketing);
    const expected = ['essentiel', 'business', 'entreprise'];
    for (const id of expected) {
        if (!a[id]) coherenceErrors.push(`landing manque tier '${id}'`);
        if (!b[id]) coherenceErrors.push(`marketing-site manque tier '${id}'`);
        if (a[id] && b[id]) {
            if (a[id].price !== b[id].price) {
                coherenceErrors.push(`tier '${id}' price diverge : landing=${a[id].price} vs marketing=${b[id].price}`);
            }
            if (a[id].annualPrice !== b[id].annualPrice) {
                coherenceErrors.push(`tier '${id}' annualPrice diverge : landing=${a[id].annualPrice} vs marketing=${b[id].annualPrice}`);
            }
        }
    }
}

// ─── Report ───────────────────────────────────────────────────────────
console.log('🔍 Anti-regression pricing — spec directeur 2026-06-02');
console.log(`   Zones scannees : ${SCAN_PATHS.length} chemins.\n`);

if (violations.length === 0 && coherenceErrors.length === 0) {
    console.log('✅ Aucune occurrence interdite. Landing == marketing-site sur prix.');
    console.log('   Codes canoniques : essentiel | business | entreprise');
    console.log('   Prix : 499 / 1 199 / sur devis (MAD HT mensuel)');
    process.exit(0);
}

if (violations.length > 0) {
    console.error(`❌ ${violations.length} occurrence(s) interdite(s) :\n`);
    for (const v of violations) {
        console.error(`   ${v.file}:${v.line}  [${v.pattern}]`);
        console.error(`     ${v.snippet}`);
    }
}

if (coherenceErrors.length > 0) {
    console.error(`\n❌ Divergences landing <-> marketing-site (${coherenceErrors.length}) :\n`);
    for (const e of coherenceErrors) {
        console.error(`   - ${e}`);
    }
}

process.exit(1);
