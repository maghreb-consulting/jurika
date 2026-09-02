#!/usr/bin/env node
/**
 * Inscrit la 2FA TOTP du compte de demonstration, une fois pour toutes.
 *
 * Pourquoi : le backend impose RG-AU30 (« 2FA obligatoire au 1er login »).
 * Un compte seede par scripts/seed-demo.mjs a twofa_method = NULL, donc
 * LoginUseCase renvoie requires2faSetup = true et le front redirige vers
 * /account/2fa-choose au lieu de /dashboard. Plutot que de bricoler la base
 * pour contourner la regle, on la RESPECTE : on enrole reellement le TOTP
 * via les endpoints publics, et le script de captation saisira un vrai code
 * a 6 chiffres au moment du login.
 *
 * Le secret est ecrit dans scripts/demo-video/demo-secrets.json (gitignore).
 *
 * Usage :
 *   node scripts/demo-video/enroll-totp.mjs
 *   node scripts/demo-video/enroll-totp.mjs --email=superviseur@demo.jurika.ma
 */
import { writeFile, readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { totp, msLeftInWindow } from './totp.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const SECRETS = join(HERE, 'demo-secrets.json');

const arg = (n, d) => {
    const m = process.argv.find(a => a.startsWith(`--${n}=`));
    return m ? m.split('=').slice(1).join('=') : d;
};

const API = arg('api', 'http://localhost:8080');
const WORKSPACE = arg('workspace', 'JUR-DEMO2');
const EMAIL = arg('email', 'employe1@demo.jurika.ma');
const PASSWORD = arg('password', 'Demo@2026');

async function jsonFetch(url, opts = {}) {
    const res = await fetch(url, opts);
    const text = await res.text();
    let body;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!res.ok) {
        throw new Error(`${opts.method ?? 'GET'} ${url} -> ${res.status} ${JSON.stringify(body)}`);
    }
    return body;
}

const H = (token) => ({
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
});

async function main() {
    console.log(`> login ${EMAIL} @ ${WORKSPACE}`);
    const login = await jsonFetch(`${API}/api/v1/auth/login`, {
        method: 'POST',
        headers: H(),
        body: JSON.stringify({ workspaceCode: WORKSPACE, email: EMAIL, password: PASSWORD }),
    });

    if (login.requires2fa) {
        console.log('  = 2FA deja active sur ce compte, rien a faire.');
        console.log('    (si le secret est perdu : desactiver la 2FA puis relancer)');
        return;
    }
    if (!login.requires2faSetup) {
        console.log('  = ce compte n\'exige pas de setup 2FA, rien a faire.');
        return;
    }

    const token = login.accessToken;
    console.log('> POST /auth/setup-2fa (initiate TOTP)');
    const setup = await jsonFetch(`${API}/api/v1/auth/setup-2fa`, { method: 'POST', headers: H(token) });
    const secret = setup.secret;
    if (!secret) throw new Error(`pas de secret dans la reponse : ${JSON.stringify(setup)}`);

    // Ne jamais confirmer a cheval sur deux fenetres TOTP.
    const left = msLeftInWindow();
    if (left < 3000) {
        console.log(`  … attente ${Math.ceil(left / 1000)} s (fin de fenetre TOTP)`);
        await new Promise(r => setTimeout(r, left + 500));
    }

    const code = totp(secret);
    console.log(`> POST /auth/setup-2fa/confirm (code ${code})`);
    await jsonFetch(`${API}/api/v1/auth/setup-2fa/confirm`, {
        method: 'POST',
        headers: H(token),
        body: JSON.stringify({ code: Number(code) }),
    });

    let store = {};
    try { store = JSON.parse(await readFile(SECRETS, 'utf8')); } catch { /* premier passage */ }
    store[`${WORKSPACE}:${EMAIL}`] = { secret, enrolledAt: new Date().toISOString() };
    await writeFile(SECRETS, JSON.stringify(store, null, 2) + '\n', 'utf8');

    console.log(`\n✅ TOTP enrole. Secret ecrit dans ${SECRETS}`);
    console.log(`   ${WORKSPACE} / ${EMAIL} -> ${secret}`);
}

main().catch(e => { console.error('\n❌', e.message); process.exit(1); });
