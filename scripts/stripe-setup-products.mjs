#!/usr/bin/env node
/**
 * scripts/stripe-setup-products.mjs
 *
 * Sprint Beta (pricing-deploy) — TASK 2.
 *
 * Cree (ou met a jour) les produits + prix Stripe pour JURIKA en mode
 * idempotent. Source de verite : ma.jurika.common.billing.PlanCatalog
 * (backend Java). On duplique la table ici en JS pour eviter de devoir
 * embarquer une JVM dans le script. Si le catalogue change cote Java,
 * met aussi a jour CATALOG ci-dessous.
 *
 * Idempotence :
 *  - Cherche le produit existant par metadata.jurika_plan = <internalCode>.
 *  - Cherche le prix existant par lookup_key (unique cote Stripe).
 *  - Met a jour le produit (display name, description) si necessaire.
 *  - Ne RE-cree PAS un prix existant (Stripe interdit la mutation de
 *    montants ; il faudrait archiver l'ancien + creer un nouveau).
 *    Le script signale 'unchanged' / 'created' / 'updated' explicitement.
 *
 * Pre-requis :
 *  - Node 18+ (fetch natif).
 *  - STRIPE_SECRET_KEY dans l'env (lecture .env.local si present, fallback
 *    .env, ou variable shell). NE JAMAIS HARDCODER LA CLE.
 *
 * Sortie :
 *  - Affiche les 4 price IDs prets a coller dans .env.local.
 *  - Option --write-env : ecrit directement les valeurs dans .env.local
 *    (preserve les autres variables ; remplace les STRIPE_PRICE_*).
 *
 * Usage :
 *   node scripts/stripe-setup-products.mjs            # dry print
 *   node scripts/stripe-setup-products.mjs --write-env
 *   node scripts/stripe-setup-products.mjs --dry-run  # API calls but no mutation
 */

import { readFile, writeFile, access } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const ENV_LOCAL = join(PROJECT_ROOT, '.env.local');
const ENV_FILE = join(PROJECT_ROOT, '.env');

// ─── Catalogue (miroir de ma.jurika.common.billing.PlanCatalog) ─────────
// Spec directeur 2026-06-02 : codes canoniques essentiel / business / entreprise.
// Lookup_keys Stripe : essentiel_{monthly,yearly} + business_{monthly,yearly}.
// Entreprise est sur devis -- pas de produit Stripe (RG-BL10 /contact-sales).
const CATALOG = [
    {
        internalCode: 'essentiel',
        displayName: 'JURIKA Essentiel',
        description: 'Plateforme SaaS juridique pour petites structures. 2 utilisateurs, 30 dossiers, 5 Go.',
        prices: [
            {
                lookupKey: 'essentiel_monthly',
                nickname: 'Essentiel mensuel',
                unitAmount: 49900,  // 499.00 MAD en centimes
                interval: 'month',
                envVar: 'STRIPE_PRICE_ESSENTIEL_MONTHLY',
            },
            {
                lookupKey: 'essentiel_yearly',
                nickname: 'Essentiel annuel',
                unitAmount: 499900, // 4999.00 MAD en centimes
                interval: 'year',
                envVar: 'STRIPE_PRICE_ESSENTIEL_YEARLY',
            },
        ],
    },
    {
        internalCode: 'business',
        displayName: 'JURIKA Business',
        description: 'Plateforme SaaS juridique pour cabinets en croissance. 6 utilisateurs, 100 dossiers, 20 Go, chatbot RAG.',
        prices: [
            {
                lookupKey: 'business_monthly',
                nickname: 'Business mensuel',
                unitAmount: 119900, // 1199.00 MAD en centimes
                interval: 'month',
                envVar: 'STRIPE_PRICE_BUSINESS_MONTHLY',
            },
            {
                lookupKey: 'business_yearly',
                nickname: 'Business annuel',
                unitAmount: 1199900, // 11999.00 MAD en centimes
                interval: 'year',
                envVar: 'STRIPE_PRICE_BUSINESS_YEARLY',
            },
        ],
    },
];

const CURRENCY = 'mad';

// ─── Parse env locale (.env.local prevaut sur .env) ─────────────────────
async function loadEnv() {
    for (const f of [ENV_LOCAL, ENV_FILE]) {
        try {
            await access(f, fsConstants.R_OK);
            const raw = await readFile(f, 'utf8');
            for (const line of raw.split(/\r?\n/)) {
                const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
                if (!m) continue;
                if (!process.env[m[1]]) {
                    process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
                }
            }
        } catch { /* fichier absent — pas grave */ }
    }
}

// ─── Wrapper Stripe API minimaliste (pas de dep, fetch natif) ───────────
async function stripeApi(method, path, params = null, apiKey) {
    const url = `https://api.stripe.com/v1${path}`;
    const body = params ? encodeForm(params) : null;
    const res = await fetch(url, {
        method,
        headers: {
            'Authorization': `Bearer ${apiKey}`,
            'Stripe-Version': '2024-12-18.acacia',
            ...(body ? { 'Content-Type': 'application/x-www-form-urlencoded' } : {}),
        },
        body,
    });
    const json = await res.json();
    if (!res.ok) {
        const msg = json?.error?.message || `${res.status} ${res.statusText}`;
        const code = json?.error?.code || 'stripe_error';
        throw Object.assign(new Error(`Stripe ${method} ${path} -> ${code}: ${msg}`), { json });
    }
    return json;
}

/**
 * Encode params en x-www-form-urlencoded a la maniere Stripe (cles nested
 * via crochets : metadata[jurika_plan]=essentiel, recurring[interval]=month).
 */
function encodeForm(obj, prefix = '') {
    const pairs = [];
    for (const [k, v] of Object.entries(obj)) {
        const key = prefix ? `${prefix}[${k}]` : k;
        if (v === null || v === undefined) continue;
        if (typeof v === 'object' && !Array.isArray(v)) {
            pairs.push(encodeForm(v, key));
        } else {
            pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(v)}`);
        }
    }
    return pairs.join('&');
}

// ─── Logique idempotente ────────────────────────────────────────────────
async function ensureProduct(plan, apiKey, dryRun) {
    // Cherche par metadata.jurika_plan (Stripe ne search pas les metadata
    // directement sans Search API — on liste tous les produits actifs et
    // on filtre. Volume reste minuscule, OK pour 2-3 produits).
    const list = await stripeApi('GET', '/products?limit=100&active=true', null, apiKey);
    const existing = list.data.find(p => p.metadata?.jurika_plan === plan.internalCode);

    const desired = {
        name: plan.displayName,
        description: plan.description,
        metadata: { jurika_plan: plan.internalCode },
    };

    if (existing) {
        // Aligne nom/description si derive
        const needsUpdate = existing.name !== desired.name
            || existing.description !== desired.description;
        if (needsUpdate && !dryRun) {
            await stripeApi('POST', `/products/${existing.id}`, desired, apiKey);
        }
        return { product: existing, action: needsUpdate ? 'updated' : 'unchanged' };
    }
    if (dryRun) return { product: { id: '<dry-run>' }, action: 'would-create' };
    const created = await stripeApi('POST', '/products', desired, apiKey);
    return { product: created, action: 'created' };
}

async function ensurePrice(plan, price, productId, apiKey, dryRun) {
    // Lookup par lookup_key (unique cote Stripe).
    const list = await stripeApi(
        'GET',
        `/prices?lookup_keys[]=${encodeURIComponent(price.lookupKey)}&active=true&limit=10`,
        null,
        apiKey
    );
    const existing = list.data.find(p => p.lookup_key === price.lookupKey);
    if (existing) {
        const sameAmount = existing.unit_amount === price.unitAmount;
        const sameInterval = existing.recurring?.interval === price.interval;
        const sameCurrency = existing.currency === CURRENCY;
        if (sameAmount && sameInterval && sameCurrency && existing.product === productId) {
            return { price: existing, action: 'unchanged' };
        }
        // Stripe interdit la mutation des montants. On signale l'incoherence.
        console.error(`  ⚠️  ${price.lookupKey} existe mais montant/intervalle/produit divergent. Le script ne mute pas.`);
        console.error(`      existing: amount=${existing.unit_amount} interval=${existing.recurring?.interval} product=${existing.product}`);
        console.error(`      desired:  amount=${price.unitAmount} interval=${price.interval} product=${productId}`);
        return { price: existing, action: 'divergent' };
    }
    const payload = {
        product: productId,
        unit_amount: price.unitAmount,
        currency: CURRENCY,
        recurring: { interval: price.interval },
        lookup_key: price.lookupKey,
        nickname: price.nickname,
        tax_behavior: 'exclusive', // HT — TVA calculee separement
        metadata: { jurika_plan: plan.internalCode },
    };
    if (dryRun) return { price: { id: '<dry-run>' }, action: 'would-create' };
    const created = await stripeApi('POST', '/prices', payload, apiKey);
    return { price: created, action: 'created' };
}

async function writeEnvVars(updates) {
    let raw = '';
    if (existsSync(ENV_LOCAL)) {
        raw = await readFile(ENV_LOCAL, 'utf8');
    } else {
        raw = '# .env.local genere par stripe-setup-products.mjs\n';
    }
    for (const [k, v] of Object.entries(updates)) {
        const re = new RegExp(`^${k}=.*$`, 'm');
        if (re.test(raw)) {
            raw = raw.replace(re, `${k}=${v}`);
        } else {
            raw += `\n${k}=${v}`;
        }
    }
    await writeFile(ENV_LOCAL, raw, 'utf8');
}

// ─── Main ───────────────────────────────────────────────────────────────
(async () => {
    await loadEnv();
    const apiKey = process.env.STRIPE_SECRET_KEY;
    if (!apiKey || !apiKey.startsWith('sk_')) {
        console.error('❌ STRIPE_SECRET_KEY absente ou invalide.');
        console.error('   Renseigne-la dans .env.local (sk_test_* en TEST mode).');
        process.exit(1);
    }
    if (apiKey.startsWith('sk_live_')) {
        const force = process.argv.includes('--allow-live');
        if (!force) {
            console.error('❌ Cle LIVE detectee. Refus par defaut.');
            console.error('   Relance avec --allow-live si tu confirmes vouloir creer des produits live.');
            process.exit(1);
        }
    }
    const dryRun = process.argv.includes('--dry-run');
    const writeEnv = process.argv.includes('--write-env');

    console.log(`🔧 Stripe setup ${dryRun ? '(DRY-RUN)' : ''} — currency=${CURRENCY.toUpperCase()}`);
    console.log(`   API key: ${apiKey.slice(0, 12)}... (${apiKey.startsWith('sk_test_') ? 'TEST' : 'LIVE'} mode)`);

    const envUpdates = {};
    for (const plan of CATALOG) {
        console.log(`\n📦 ${plan.displayName} (${plan.internalCode})`);
        const { product, action: pAction } = await ensureProduct(plan, apiKey, dryRun);
        console.log(`   product: ${product.id}  [${pAction}]`);
        for (const price of plan.prices) {
            const { price: createdPrice, action } = await ensurePrice(plan, price, product.id, apiKey, dryRun);
            const amount = (price.unitAmount / 100).toFixed(2);
            console.log(`   ├─ ${price.lookupKey.padEnd(20)} ${createdPrice.id}  [${action}]  ${amount} MAD/${price.interval}`);
            envUpdates[price.envVar] = createdPrice.id;
        }
    }

    console.log('\n✅ Synchronisation terminee. Valeurs .env.local :');
    for (const [k, v] of Object.entries(envUpdates)) {
        console.log(`   ${k}=${v}`);
    }

    if (writeEnv && !dryRun) {
        // Alias retro-compat (Sprint 12 = mensuel).
        envUpdates.STRIPE_PRICE_ESSENTIEL = envUpdates.STRIPE_PRICE_ESSENTIEL_MONTHLY;
        envUpdates.STRIPE_PRICE_BUSINESS = envUpdates.STRIPE_PRICE_BUSINESS_MONTHLY;
        await writeEnvVars(envUpdates);
        console.log(`\n📝 ${ENV_LOCAL} mis a jour (aliases retro-compat inclus).`);
    } else if (!dryRun) {
        console.log('\nℹ️  Relance avec --write-env pour patcher .env.local automatiquement.');
    }
})().catch(err => {
    console.error('❌', err.message);
    if (err.json) console.error(JSON.stringify(err.json.error, null, 2));
    process.exit(1);
});
