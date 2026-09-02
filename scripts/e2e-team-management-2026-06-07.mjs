#!/usr/bin/env node
/**
 * scripts/e2e-team-management-2026-06-07.mjs
 *
 * BUG 6 (2026-06-07) — branche feat/team-management-2026-06-07.
 *
 * Couvre :
 *   1. Seed workspace + SUPERVISEUR + login (sans 2FA, mcp=false, r2s=false)
 *   2. GET  /api/v1/auth/users (initial = 1 SUPERVISEUR)
 *   3. POST /api/v1/auth/invite-employee  -> 201 + tempPassword + status=PENDING
 *   4. GET  /api/v1/auth/users -> 2 lignes, EMPLOYE PENDING
 *   5. Login en tant qu'invite avec MDP temp -> succes (mcp=true mais login OK)
 *      + verifie que LoginUseCase a transitionne PENDING -> ACTIVE
 *   6. PATCH .../status {active:false} sur l'invite -> INACTIVE
 *   7. Login de l'INACTIVE -> 401 "Compte desactive"
 *   8. PATCH .../status {active:true} -> ACTIVE
 *   9. PATCH .../status {active:false} sur soi-meme -> 403 (anti-lockout)
 *  10. Quota plan : invitations en boucle jusqu'a 402 PLAN_LIMIT_USERS
 *  11. Reactivation EMPLOYE quand quota plein -> 402 PLAN_LIMIT_USERS
 *  12. Cleanup
 *
 * Pre-requis : stack hote up + jurika.test.seed.enabled=true.
 */

import { spawnSync } from 'node:child_process';
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';
import { createHmac } from 'node:crypto';

// ─── TOTP RFC 6238 (HMAC-SHA1, 6 digits, 30s window) ────────────────────
// Necessaire pour confirmer le setup 2FA cote backend sans Google Authenticator.
function base32Decode(b32) {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    let bits = '';
    for (const c of b32.toUpperCase().replace(/=+$/, '')) {
        const v = alphabet.indexOf(c);
        if (v < 0) continue;
        bits += v.toString(2).padStart(5, '0');
    }
    const bytes = [];
    for (let i = 0; i + 8 <= bits.length; i += 8) {
        bytes.push(parseInt(bits.slice(i, i + 8), 2));
    }
    return Buffer.from(bytes);
}
function totpCode(secretB32, atMs = Date.now()) {
    const counter = Math.floor(atMs / 1000 / 30);
    const buf = Buffer.alloc(8);
    buf.writeBigUInt64BE(BigInt(counter));
    const hmac = createHmac('sha1', base32Decode(secretB32)).update(buf).digest();
    const offset = hmac[hmac.length - 1] & 0x0f;
    const binCode = ((hmac[offset] & 0x7f) << 24)
        | ((hmac[offset + 1] & 0xff) << 16)
        | ((hmac[offset + 2] & 0xff) << 8)
        | (hmac[offset + 3] & 0xff);
    return binCode % 1_000_000;
}
function decodeJwtPayload(jwt) {
    const part = jwt.split('.')[1];
    const padded = part + '='.repeat((4 - (part.length % 4)) % 4);
    return JSON.parse(Buffer.from(padded.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';

const C = {
    pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
    dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m',
};

const results = [];
function record(group, name, status, detail = '') {
    results.push({ group, name, status, detail });
    const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
    console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + String(detail).slice(0, 250) + C.reset : ''}`);
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
        } catch { /* ok */ }
    }
}

async function http(method, path, { body = null, token = null, expect2xx = false } = {}) {
    const url = `${BASE}${path}`;
    const headers = { 'Accept': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    if (body !== null) headers['Content-Type'] = 'application/json';
    const res = await fetch(url, {
        method,
        headers,
        body: body !== null ? JSON.stringify(body) : null,
    });
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { /* keep null */ }
    if (expect2xx && (res.status < 200 || res.status >= 300)) {
        throw new Error(`HTTP ${res.status} ${path} -> ${text.slice(0, 250)}`);
    }
    return { status: res.status, json, text };
}

async function login(workspaceCode, email, password) {
    const r = await http('POST', '/api/v1/auth/login', {
        body: { workspaceCode, email, password },
    });
    return r;
}

async function main() {
    await loadEnv();
    console.log(`${C.bold}${C.cyan}E2E BUG 6 — gestion equipe + flux activation${C.reset}`);
    console.log(`  ${C.dim}BASE = ${BASE}${C.reset}\n`);

    // ───────── 1. Seed workspace + SUPERVISEUR ─────────
    console.log(`${C.bold}1. Seed cabinet (SUPERVISEUR) + login${C.reset}`);
    let seed;
    try {
        const r = await http('POST', '/api/v1/test/seed/workspace?role=SUPERVISEUR&selectedPlan=essentiel', {
            expect2xx: true,
        });
        seed = r.json;
        record('seed', 'POST /test/seed/workspace?role=SUPERVISEUR', 'PASS',
            `code=${seed.workspaceCode} email=${seed.adminEmail}`);
    } catch (e) {
        record('seed', 'POST /test/seed/workspace', 'FAIL', e.message);
        return done();
    }

    let supervisorToken;
    let supervisorUserId;
    try {
        const r = await login(seed.workspaceCode, seed.adminEmail, seed.adminPassword);
        if (r.status !== 200) throw new Error(`login HTTP ${r.status} ${r.text.slice(0, 200)}`);
        if (!r.json.accessToken) throw new Error('login OK mais pas de accessToken');
        supervisorToken = r.json.accessToken;
        supervisorUserId = r.json.userId;
        record('seed', 'login SUPERVISEUR', 'PASS', `userId=${supervisorUserId}`);
    } catch (e) {
        record('seed', 'login SUPERVISEUR', 'FAIL', e.message);
        return done();
    }

    // ───────── 2. GET initial users ─────────
    console.log(`\n${C.bold}2. Liste initiale equipe${C.reset}`);
    try {
        const r = await http('GET', '/api/v1/auth/users', {
            token: supervisorToken, expect2xx: true,
        });
        const sup = r.json.filter(u => u.role === 'SUPERVISEUR');
        const emp = r.json.filter(u => u.role === 'EMPLOYE');
        const cli = r.json.filter(u => u.role === 'CLIENT');
        if (sup.length !== 1) throw new Error(`attendait 1 SUPERVISEUR, recu ${sup.length}`);
        if (emp.length !== 0) throw new Error(`attendait 0 EMPLOYE, recu ${emp.length}`);
        if (cli.length !== 0) throw new Error(`attendait 0 CLIENT (filtre roles internes), recu ${cli.length}`);
        if (sup[0].status !== 'ACTIVE') throw new Error(`SUPERVISEUR doit etre ACTIVE, recu ${sup[0].status}`);
        record('list', 'GET /auth/users initial', 'PASS', `1 SUPERVISEUR ACTIVE, 0 EMPLOYE, 0 CLIENT (filtre)`);
    } catch (e) {
        record('list', 'GET /auth/users initial', 'FAIL', e.message);
    }

    // ───────── 3. Invite employe ─────────
    console.log(`\n${C.bold}3. Invitation employe -> PENDING${C.reset}`);
    // BUG 7 (2026-06-08) — l'email perso devient contact_email ; le backend
    // genere un identifiant @jurika.ma qu'on recupere via la reponse pour les
    // logins suivants. Le nom firstName/lastName est unique pour eviter les
    // collisions avec les autres tests en parallele.
    const inviteSuffix = String(Date.now()).slice(-6);
    const inviteeEmail = `karim.invitee+${Date.now()}@${seed.workspaceCode.toLowerCase()}.jurika.test`;
    let inviteeUserId;
    let inviteeTempPassword;
    let inviteeLoginEmail; // BUG 7 — identifiant @jurika.ma genere
    try {
        const r = await http('POST', '/api/v1/auth/invite-employee', {
            token: supervisorToken,
            body: {
                email: inviteeEmail,
                // BUG 7 — firstName/lastName unique pour identifiant @jurika.ma sans collision
                firstName: 'Karim' + inviteSuffix,
                lastName: 'Invitee',
                phone: '+212600000001',
                role: 'EMPLOYE',
            },
        });
        if (r.status !== 201) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 250)}`);
        if (!r.json.tempPassword) throw new Error('reponse sans tempPassword');
        inviteeUserId = r.json.userId;
        inviteeTempPassword = r.json.tempPassword;
        inviteeLoginEmail = r.json.loginEmail || inviteeEmail; // fallback si flag off
        record('invite', 'POST /auth/invite-employee', 'PASS',
            `userId=${inviteeUserId} emailDelivered=${r.json.emailDelivered} loginEmail=${inviteeLoginEmail}`);
    } catch (e) {
        record('invite', 'POST /auth/invite-employee', 'FAIL', e.message);
        return done();
    }

    try {
        const r = await http('GET', '/api/v1/auth/users', {
            token: supervisorToken, expect2xx: true,
        });
        const newUser = r.json.find(u => u.userId === inviteeUserId);
        if (!newUser) throw new Error('invitee absent de la liste');
        if (newUser.status !== 'PENDING') throw new Error(`statut attendu PENDING, recu ${newUser.status}`);
        if (newUser.lastLoginAt) throw new Error(`lastLoginAt doit etre null, recu ${newUser.lastLoginAt}`);
        record('invite', 'invitee est PENDING + jamais connecte', 'PASS');
    } catch (e) {
        record('invite', 'invitee est PENDING', 'FAIL', e.message);
    }

    // ───────── 4. Onboarding complet : 1er login + change-MDP + 2FA -> ACTIVE ─────────
    // Decision 2026-06-08 chore : la transition PENDING -> ACTIVE ne se fait
    // PLUS au 1er login mais a la fin de l'onboarding (confirm-2fa). On verifie
    // donc explicitement que le statut reste PENDING apres login + apres
    // change-MDP, et ne devient ACTIVE qu'apres confirm-2fa TOTP.
    console.log(`\n${C.bold}4. Onboarding complet -> PENDING -> ACTIVE seulement apres 2FA${C.reset}`);
    let inviteeTokenAfterLogin;
    let inviteeTokenAfterChangePwd;
    let inviteeTokenAfterConfirm2fa;
    const inviteeNewPwd = 'NewStrong@2026!';
    try {
        const r = await login(seed.workspaceCode, inviteeLoginEmail, inviteeTempPassword);
        if (r.status !== 200) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
        if (!r.json.mustChangePassword) throw new Error('mustChangePassword devrait etre true au 1er login');
        if (!r.json.accessToken) throw new Error('pas de accessToken');
        inviteeTokenAfterLogin = r.json.accessToken;
        record('onboarding', '1er login OK (mcp=true, r2s=true)', 'PASS');
    } catch (e) {
        record('onboarding', '1er login invitee', 'FAIL', e.message);
        return done();
    }

    // STILL PENDING after login
    try {
        const r = await http('GET', '/api/v1/auth/users', {
            token: supervisorToken, expect2xx: true,
        });
        const u = r.json.find(u => u.userId === inviteeUserId);
        if (!u) throw new Error('invitee absent');
        if (u.status !== 'PENDING') {
            throw new Error(`statut apres 1er login attendu PENDING (transition reportee a 2FA), recu ${u.status}`);
        }
        if (!u.lastLoginAt) throw new Error(`lastLoginAt doit etre renseigne apres login`);
        record('onboarding', 'statut reste PENDING apres 1er login', 'PASS');
    } catch (e) {
        record('onboarding', 'statut reste PENDING apres 1er login', 'FAIL', e.message);
    }

    // change-password
    try {
        const r = await http('POST', '/api/v1/auth/change-password', {
            token: inviteeTokenAfterLogin,
            body: {
                oldPassword: inviteeTempPassword,
                newPassword: inviteeNewPwd,
                confirmPassword: inviteeNewPwd,
            },
            expect2xx: true,
        });
        if (!r.json.accessToken) throw new Error('change-password sans nouveau token');
        inviteeTokenAfterChangePwd = r.json.accessToken;
        record('onboarding', '/auth/change-password -> nouveaux tokens (mcp=false)', 'PASS');
    } catch (e) {
        record('onboarding', '/auth/change-password', 'FAIL', e.message);
        return done();
    }

    // STILL PENDING after change-password (2FA pas encore configure)
    try {
        const r = await http('GET', '/api/v1/auth/users', {
            token: supervisorToken, expect2xx: true,
        });
        const u = r.json.find(u => u.userId === inviteeUserId);
        if (u.status !== 'PENDING') {
            throw new Error(`statut apres change-MDP attendu PENDING (2FA pas faite), recu ${u.status}`);
        }
        record('onboarding', 'statut reste PENDING apres change-MDP (2FA non faite)', 'PASS');
    } catch (e) {
        record('onboarding', 'statut reste PENDING apres change-MDP', 'FAIL', e.message);
    }

    // setup-2fa -> recupere le secret TOTP
    let totpSecret;
    try {
        const r = await http('POST', '/api/v1/auth/setup-2fa', {
            token: inviteeTokenAfterChangePwd, expect2xx: true,
        });
        if (!r.json.secret) throw new Error('setup-2fa sans secret');
        totpSecret = r.json.secret;
        record('onboarding', '/auth/setup-2fa -> secret TOTP recu', 'PASS');
    } catch (e) {
        record('onboarding', '/auth/setup-2fa', 'FAIL', e.message);
        return done();
    }

    // confirm-2fa avec code TOTP
    try {
        const code = totpCode(totpSecret);
        const r = await http('POST', '/api/v1/auth/setup-2fa/confirm', {
            token: inviteeTokenAfterChangePwd,
            body: { code },
            expect2xx: true,
        });
        if (!r.json.accessToken) throw new Error('confirm-2fa sans nouveau token');
        inviteeTokenAfterConfirm2fa = r.json.accessToken;
        record('onboarding', '/auth/setup-2fa/confirm avec code TOTP -> nouveaux tokens (r2s=false)', 'PASS');
    } catch (e) {
        record('onboarding', '/auth/setup-2fa/confirm', 'FAIL', e.message);
        return done();
    }

    // ENFIN ACTIVE apres confirm-2fa
    try {
        const r = await http('GET', '/api/v1/auth/users', {
            token: supervisorToken, expect2xx: true,
        });
        const u = r.json.find(u => u.userId === inviteeUserId);
        if (u.status !== 'ACTIVE') {
            throw new Error(`statut apres confirm-2fa attendu ACTIVE, recu ${u.status}`);
        }
        record('onboarding', 'transition PENDING -> ACTIVE par Setup2faUseCase.confirm()', 'PASS');
    } catch (e) {
        record('onboarding', 'transition PENDING -> ACTIVE post-2FA', 'FAIL', e.message);
    }

    // ───────── 5. Desactiver invitee ─────────
    console.log(`\n${C.bold}5. Desactivation par superviseur${C.reset}`);
    try {
        const r = await http('PATCH', `/api/v1/auth/users/${inviteeUserId}/status`, {
            token: supervisorToken,
            body: { active: false },
            expect2xx: true,
        });
        if (r.json.status !== 'INACTIVE') throw new Error(`statut attendu INACTIVE, recu ${r.json.status}`);
        if (r.json.previousStatus !== 'ACTIVE') throw new Error(`previousStatus attendu ACTIVE, recu ${r.json.previousStatus}`);
        if (!r.json.changed) throw new Error(`changed devrait etre true`);
        record('deactivate', 'PATCH /users/{id}/status {active:false}', 'PASS', `prev=${r.json.previousStatus} -> ${r.json.status}`);
    } catch (e) {
        record('deactivate', 'PATCH /users/{id}/status {active:false}', 'FAIL', e.message);
    }

    try {
        // BUG 7 — login via l'identifiant @jurika.ma genere + nouveau MDP (change-MDP a eu lieu en section 4)
        const r = await login(seed.workspaceCode, inviteeLoginEmail, inviteeNewPwd);
        if (r.status !== 401) throw new Error(`attendait 401, recu ${r.status} body=${r.text.slice(0, 200)}`);
        record('deactivate', 'login INACTIVE refuse 401', 'PASS', r.text.slice(0, 100));
    } catch (e) {
        record('deactivate', 'login INACTIVE refuse 401', 'FAIL', e.message);
    }

    // ───────── 6. Reactiver invitee ─────────
    console.log(`\n${C.bold}6. Reactivation par superviseur${C.reset}`);
    try {
        const r = await http('PATCH', `/api/v1/auth/users/${inviteeUserId}/status`, {
            token: supervisorToken,
            body: { active: true },
            expect2xx: true,
        });
        if (r.json.status !== 'ACTIVE') throw new Error(`statut attendu ACTIVE, recu ${r.json.status}`);
        record('reactivate', 'PATCH /users/{id}/status {active:true}', 'PASS');
    } catch (e) {
        record('reactivate', 'PATCH /users/{id}/status {active:true}', 'FAIL', e.message);
    }

    // Idempotence : repasser ACTIVE -> no-op
    try {
        const r = await http('PATCH', `/api/v1/auth/users/${inviteeUserId}/status`, {
            token: supervisorToken,
            body: { active: true },
            expect2xx: true,
        });
        if (r.json.changed !== false) throw new Error(`changed attendu false (idempotent), recu ${r.json.changed}`);
        record('reactivate', 'idempotence PATCH meme valeur -> changed=false', 'PASS');
    } catch (e) {
        record('reactivate', 'idempotence', 'FAIL', e.message);
    }

    // ───────── 7. Auto-desactivation refusee ─────────
    console.log(`\n${C.bold}7. Auto-desactivation interdite (anti-lockout)${C.reset}`);
    try {
        const r = await http('PATCH', `/api/v1/auth/users/${supervisorUserId}/status`, {
            token: supervisorToken,
            body: { active: false },
        });
        if (r.status !== 403) throw new Error(`attendait 403, recu ${r.status} body=${r.text.slice(0, 200)}`);
        record('self', 'PATCH self/status -> 403 FORBIDDEN', 'PASS');
    } catch (e) {
        record('self', 'PATCH self/status -> 403', 'FAIL', e.message);
    }

    // ───────── 8. Quota plan essentiel (2 EMPLOYE max) ─────────
    console.log(`\n${C.bold}8. Quota plan essentiel (max 2 EMPLOYE actifs)${C.reset}`);
    // Avant : 1 invitee deja ACTIVE. On invite un 2e -> OK. Un 3e -> 402.
    let secondInviteeId;
    try {
        const r = await http('POST', '/api/v1/auth/invite-employee', {
            token: supervisorToken,
            body: {
                email: `second+${Date.now()}@${seed.workspaceCode.toLowerCase()}.jurika.test`,
                firstName: 'Second', lastName: 'Invitee',
                role: 'EMPLOYE',
            },
        });
        if (r.status !== 201) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
        secondInviteeId = r.json.userId;
        record('quota', 'invite EMPLOYE n°2 dans plan essentiel(2)', 'PASS');
    } catch (e) {
        record('quota', 'invite EMPLOYE n°2', 'FAIL', e.message);
    }

    try {
        const r = await http('POST', '/api/v1/auth/invite-employee', {
            token: supervisorToken,
            body: {
                email: `third+${Date.now()}@${seed.workspaceCode.toLowerCase()}.jurika.test`,
                firstName: 'Third', lastName: 'Invitee',
                role: 'EMPLOYE',
            },
        });
        if (r.status !== 402) throw new Error(`attendait 402 PLAN_LIMIT_USERS, recu ${r.status} body=${r.text.slice(0, 200)}`);
        record('quota', 'invite EMPLOYE n°3 (plein) -> 402 PLAN_LIMIT_USERS', 'PASS', r.text.slice(0, 120));
    } catch (e) {
        record('quota', 'invite EMPLOYE n°3 -> 402', 'FAIL', e.message);
    }

    // Desactiver le 1er + invite le 3e -> doit passer (siege libere)
    if (secondInviteeId) {
        try {
            await http('PATCH', `/api/v1/auth/users/${inviteeUserId}/status`, {
                token: supervisorToken, body: { active: false }, expect2xx: true,
            });
            const r = await http('POST', '/api/v1/auth/invite-employee', {
                token: supervisorToken,
                body: {
                    email: `third-bis+${Date.now()}@${seed.workspaceCode.toLowerCase()}.jurika.test`,
                    firstName: 'Third', lastName: 'Bis',
                    role: 'EMPLOYE',
                },
            });
            if (r.status !== 201) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
            record('quota', 'siege libere par desactivation -> invite OK', 'PASS');
        } catch (e) {
            record('quota', 'siege libere par desactivation', 'FAIL', e.message);
        }

        // Tentative de reactivation du 1er -> 402 (quota plein a nouveau)
        try {
            const r = await http('PATCH', `/api/v1/auth/users/${inviteeUserId}/status`, {
                token: supervisorToken, body: { active: true },
            });
            if (r.status !== 402) throw new Error(`attendait 402, recu ${r.status} body=${r.text.slice(0, 200)}`);
            record('quota', 'reactivation EMPLOYE quand plein -> 402', 'PASS');
        } catch (e) {
            record('quota', 'reactivation EMPLOYE quand plein -> 402', 'FAIL', e.message);
        }
    }

    // ───────── 9. CLIENT refuse via cet endpoint ─────────
    console.log(`\n${C.bold}9. Endpoint refuse les CLIENT (gere via dataroom)${C.reset}`);
    try {
        const r = await http('PATCH', `/api/v1/auth/users/${seed.clientUserId}/status`, {
            token: supervisorToken,
            body: { active: false },
        });
        if (r.status !== 409) throw new Error(`attendait 409 (CLIENT non gere), recu ${r.status} body=${r.text.slice(0, 200)}`);
        record('client-guard', 'PATCH CLIENT/status -> 409 CONFLICT', 'PASS');
    } catch (e) {
        record('client-guard', 'PATCH CLIENT/status -> 409', 'FAIL', e.message);
    }

    // ───────── 10. Cleanup ─────────
    console.log(`\n${C.bold}10. Cleanup${C.reset}`);
    try {
        const r = await http('DELETE', `/api/v1/test/cleanup/${seed.workspaceId}`);
        if (r.status >= 200 && r.status < 300) {
            record('cleanup', 'DELETE workspace seed', 'PASS');
        } else {
            record('cleanup', 'DELETE workspace seed', 'SKIP',
                `HTTP ${r.status} (le ON DELETE CASCADE peut echouer si demandes/refresh tokens lies)`);
        }
    } catch (e) {
        record('cleanup', 'DELETE workspace seed', 'SKIP', e.message);
    }

    done();
}

function done() {
    const total = results.length;
    const pass = results.filter(r => r.status === 'PASS').length;
    const fail = results.filter(r => r.status === 'FAIL').length;
    const skip = results.filter(r => r.status === 'SKIP').length;
    console.log(`\n${C.bold}═══ RESULTATS ═══${C.reset}`);
    console.log(`  ${C.pass}${pass} PASS${C.reset}   ${C.fail}${fail} FAIL${C.reset}   ${C.skip}${skip} SKIP${C.reset}   (${total} total)`);
    if (fail > 0) {
        console.log(`\n${C.fail}${C.bold}ECHECS :${C.reset}`);
        results.filter(r => r.status === 'FAIL').forEach(r =>
            console.log(`  ${C.fail}- [${r.group}] ${r.name}${C.reset}  ${C.dim}${r.detail}${C.reset}`));
    }
    process.exit(fail > 0 ? 1 : 0);
}

main().catch(e => {
    console.error(`${C.fail}${C.bold}FATAL${C.reset} :`, e);
    process.exit(2);
});
