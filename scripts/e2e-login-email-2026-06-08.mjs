#!/usr/bin/env node
/**
 * scripts/e2e-login-email-2026-06-08.mjs
 *
 * BUG 7 (2026-06-08) — branche feat/login-email-2026-06-08.
 *
 * Verifie la dissociation login_email (identifiant @jurika.ma) vs
 * contact_email (notifs). Pre-requis :
 *   - stack hote up
 *   - jurika.test.seed.enabled=true sur dataroom-service
 *   - jurika.auth.login-email-enabled=true sur auth-service (defaut prod)
 *
 * Scenarios :
 *  1. Seed cabinet (SUPERVISEUR) + login
 *  2. Inviter EMPLOYE (email perso oussama.test@gmail.com)
 *     -> reponse contient loginEmail = oussama.test@jurika.ma
 *     -> contact_email = oussama.test@gmail.com (DB check via /auth/users)
 *  3. Login en tant que cet invite avec loginEmail @jurika.ma -> 200
 *  4. Login avec contact_email perso -> 401 (refuse, flag on)
 *  5. /me retourne loginEmail + contactEmail distincts
 *  6. Collision intra-workspace : inviter un 2e "Oussama Test" -> suffixe
 *     oussama.test2@jurika.ma
 *  7. Inviter CLIENT (via dataroom flow) — repond aussi loginEmail
 *  8. Cleanup
 */

import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';
import { Buffer } from 'node:buffer';

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

async function main() {
    await loadEnv();
    console.log(`${C.bold}${C.cyan}E2E BUG 7 — login_email vs contact_email${C.reset}`);
    console.log(`  ${C.dim}BASE = ${BASE}${C.reset}\n`);

    // ───────── 1. Seed cabinet + login ─────────
    console.log(`${C.bold}1. Seed cabinet (SUPERVISEUR) + login${C.reset}`);
    let seed;
    try {
        const r = await http('POST', '/api/v1/test/seed/workspace?role=SUPERVISEUR&selectedPlan=essentiel', {
            expect2xx: true,
        });
        seed = r.json;
        record('seed', 'POST /test/seed/workspace', 'PASS',
            `code=${seed.workspaceCode} email=${seed.adminEmail}`);
    } catch (e) {
        record('seed', 'POST /test/seed/workspace', 'FAIL', e.message);
        return done();
    }

    let supervisorToken;
    let supervisorUserId;
    try {
        // Pour le seed (TestSeedController), login_email = email = adminEmail.
        // Donc on peut se connecter avec adminEmail directement (chemin legacy
        // qui marche grace au backfill 1:1 dans le seed).
        const r = await http('POST', '/api/v1/auth/login', {
            body: {
                workspaceCode: seed.workspaceCode,
                email: seed.adminEmail,
                password: seed.adminPassword,
            },
        });
        if (r.status !== 200 || !r.json.accessToken) {
            throw new Error(`login HTTP ${r.status} ${r.text.slice(0, 200)}`);
        }
        supervisorToken = r.json.accessToken;
        supervisorUserId = r.json.userId;
        record('seed', 'login SUPERVISEUR (login_email = adminEmail dans seed)', 'PASS');
    } catch (e) {
        record('seed', 'login SUPERVISEUR', 'FAIL', e.message);
        return done();
    }

    // ───────── 2. Invite EMPLOYE et verif loginEmail genere ─────────
    console.log(`\n${C.bold}2. Invite EMPLOYE -> login_email genere${C.reset}`);
    const inviteeContact = `oussama.test+${Date.now()}@gmail.com`;
    let inviteeUserId;
    let inviteeLoginEmail;
    let inviteeTempPwd;
    try {
        const r = await http('POST', '/api/v1/auth/invite-employee', {
            token: supervisorToken,
            body: {
                email: inviteeContact,
                firstName: 'Oussama',
                lastName: 'Test',
                role: 'EMPLOYE',
            },
        });
        if (r.status !== 201) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
        if (!r.json.loginEmail) throw new Error('reponse sans loginEmail');
        if (!r.json.contactEmail) throw new Error('reponse sans contactEmail');
        if (!r.json.loginEmail.endsWith('@jurika.ma')) {
            throw new Error(`loginEmail attendu en @jurika.ma, recu ${r.json.loginEmail}`);
        }
        if (r.json.contactEmail !== inviteeContact.toLowerCase()) {
            throw new Error(`contactEmail attendu = ${inviteeContact.toLowerCase()}, recu ${r.json.contactEmail}`);
        }
        inviteeUserId = r.json.userId;
        inviteeLoginEmail = r.json.loginEmail;
        inviteeTempPwd = r.json.tempPassword;
        record('invite', 'POST /auth/invite-employee', 'PASS',
            `loginEmail=${inviteeLoginEmail} contactEmail=${r.json.contactEmail}`);
    } catch (e) {
        record('invite', 'POST /auth/invite-employee', 'FAIL', e.message);
        return done();
    }

    // ───────── 3. Login via login_email -> 200 ─────────
    console.log(`\n${C.bold}3. Login via login_email @jurika.ma -> succes${C.reset}`);
    try {
        const r = await http('POST', '/api/v1/auth/login', {
            body: {
                workspaceCode: seed.workspaceCode,
                email: inviteeLoginEmail,
                password: inviteeTempPwd,
            },
        });
        if (r.status !== 200) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
        if (!r.json.accessToken) throw new Error('pas de accessToken');
        record('login', 'login(workspaceCode, loginEmail @jurika.ma, tempPwd)', 'PASS');
    } catch (e) {
        record('login', 'login via loginEmail', 'FAIL', e.message);
    }

    // ───────── 4. Login via contact_email perso -> 401 ─────────
    console.log(`\n${C.bold}4. Login via contact_email perso -> 401${C.reset}`);
    try {
        const r = await http('POST', '/api/v1/auth/login', {
            body: {
                workspaceCode: seed.workspaceCode,
                email: inviteeContact, // l'email perso, PAS l'identifiant
                password: inviteeTempPwd,
            },
        });
        if (r.status !== 401) {
            throw new Error(`attendait 401, recu ${r.status} body=${r.text.slice(0, 200)}`);
        }
        record('login', 'login(contact_email perso) -> 401 Identifiants invalides', 'PASS');
    } catch (e) {
        record('login', 'login via contact_email refuse', 'FAIL', e.message);
    }

    // ───────── 5. /me expose loginEmail + contactEmail distincts ─────────
    console.log(`\n${C.bold}5. GET /auth/me distingue loginEmail vs contactEmail${C.reset}`);
    try {
        // Reconnect en tant qu'invitee
        const lr = await http('POST', '/api/v1/auth/login', {
            body: {
                workspaceCode: seed.workspaceCode,
                email: inviteeLoginEmail,
                password: inviteeTempPwd,
            },
        });
        if (lr.status !== 200) throw new Error(`relogin HTTP ${lr.status}`);
        const inviteeToken = lr.json.accessToken;

        const r = await http('GET', '/api/v1/auth/me', { token: inviteeToken, expect2xx: true });
        if (r.json.loginEmail !== inviteeLoginEmail) {
            throw new Error(`me.loginEmail = ${r.json.loginEmail}, attendu ${inviteeLoginEmail}`);
        }
        if (r.json.contactEmail !== inviteeContact.toLowerCase()) {
            throw new Error(`me.contactEmail = ${r.json.contactEmail}, attendu ${inviteeContact.toLowerCase()}`);
        }
        if (r.json.loginEmail === r.json.contactEmail) {
            throw new Error('loginEmail et contactEmail devraient differer pour cet invite');
        }
        record('me', 'GET /auth/me expose loginEmail !== contactEmail', 'PASS');
    } catch (e) {
        record('me', 'GET /auth/me distincts', 'FAIL', e.message);
    }

    // ───────── 6. Collision -> suffixe ─────────
    console.log(`\n${C.bold}6. Collision login_email intra-workspace -> suffixe${C.reset}`);
    try {
        const r = await http('POST', '/api/v1/auth/invite-employee', {
            token: supervisorToken,
            body: {
                email: `oussama2.test+${Date.now()}@gmail.com`,
                firstName: 'Oussama',
                lastName: 'Test', // meme nom -> meme base "oussama.test"
                role: 'EMPLOYE',
            },
        });
        if (r.status !== 201) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
        if (!r.json.loginEmail) throw new Error('reponse sans loginEmail');
        // Le 1er Oussama Test a oussama.test@jurika.ma, le 2e doit avoir oussama.test2@jurika.ma
        if (!/^oussama\.test2@jurika\.ma$/.test(r.json.loginEmail)) {
            throw new Error(`suffixe attendu : oussama.test2@jurika.ma, recu ${r.json.loginEmail}`);
        }
        record('collision', 'suffixe 2 ajoute pour homonyme', 'PASS', r.json.loginEmail);
    } catch (e) {
        record('collision', 'suffixe homonyme', 'FAIL', e.message);
    }

    // ───────── 7. Caracteres speciaux dans nom ─────────
    console.log(`\n${C.bold}7. Normalisation accents + caracteres speciaux${C.reset}`);
    try {
        // Role SUPERVISEUR pour ne pas saturer le quota EMPLOYE essentiel(2)
        // deja consomme par les invites des tests 2 et 6.
        const r = await http('POST', '/api/v1/auth/invite-employee', {
            token: supervisorToken,
            body: {
                email: `idrissi+${Date.now()}@example.com`,
                firstName: "L'Hassan",
                lastName: 'El-Idrïssi',
                role: 'SUPERVISEUR',
            },
        });
        if (r.status !== 201) throw new Error(`HTTP ${r.status} body=${r.text.slice(0, 200)}`);
        // L'Hassan El-Idrïssi -> lhassan.elidrissi (apostrophe + tiret + diacritics retires)
        if (!/^lhassan\.elidrissi(\d+)?@jurika\.ma$/.test(r.json.loginEmail)) {
            throw new Error(`format attendu lhassan.elidrissi(N)?@jurika.ma, recu ${r.json.loginEmail}`);
        }
        record('normalize', 'accents + apostrophe + tiret normalises', 'PASS', r.json.loginEmail);
    } catch (e) {
        record('normalize', 'accents + apostrophe + tiret', 'FAIL', e.message);
    }

    // ───────── 8. GET /auth/users expose login_email + contact_email ─────────
    console.log(`\n${C.bold}8. GET /auth/users -> WorkspaceUser inclut email = loginEmail${C.reset}`);
    try {
        const r = await http('GET', '/api/v1/auth/users', {
            token: supervisorToken, expect2xx: true,
        });
        const u = r.json.find(x => x.userId === inviteeUserId);
        if (!u) throw new Error('invitee absent de la liste');
        if (u.email !== inviteeLoginEmail) {
            throw new Error(`WorkspaceUser.email = ${u.email}, attendu loginEmail ${inviteeLoginEmail}`);
        }
        record('list', 'WorkspaceUser.email = loginEmail (= identifiant)', 'PASS');
    } catch (e) {
        record('list', 'WorkspaceUser.email = loginEmail', 'FAIL', e.message);
    }

    // ───────── 9. Chore : claim JWT email = loginEmail (jamais contact) ─────────
    console.log(`\n${C.bold}9. Claim JWT email = loginEmail (chore audit BUG7)${C.reset}`);
    try {
        const lr = await http('POST', '/api/v1/auth/login', {
            body: {
                workspaceCode: seed.workspaceCode,
                email: inviteeLoginEmail,
                password: inviteeTempPwd,
            },
            expect2xx: true,
        });
        const claims = decodeJwtPayload(lr.json.accessToken);
        if (claims.email !== inviteeLoginEmail) {
            throw new Error(`claim email = ${claims.email}, attendu loginEmail ${inviteeLoginEmail}`);
        }
        if (claims.login_email !== inviteeLoginEmail) {
            throw new Error(`claim login_email = ${claims.login_email}`);
        }
        if (claims.contact_email !== inviteeContact.toLowerCase()) {
            throw new Error(`claim contact_email = ${claims.contact_email}, attendu ${inviteeContact.toLowerCase()}`);
        }
        if (claims.contact_email === claims.email) {
            throw new Error('claim email ne doit JAMAIS etre contact_email -- chore audit BUG7');
        }
        record('jwt', 'JWT claim email = loginEmail (jamais contact)', 'PASS',
            `email=${claims.email} contact_email=${claims.contact_email}`);
    } catch (e) {
        record('jwt', 'JWT claim email = loginEmail', 'FAIL', e.message);
    }

    // ───────── 10. Chore : PATCH /me/contact-email (sur le SUPERVISEUR seed, deja onboarde) ─────────
    // L'invitee fraichement cree a encore mcp=true -> ChangePasswordEnforcer
    // 403 sur tous les endpoints non whiteliste. On utilise donc le SUPERVISEUR
    // du seed (mcp=false, r2s=false via le bypass TestSeedController).
    console.log(`\n${C.bold}10. PATCH /me/contact-email (chore profil, via SUPERVISEUR)${C.reset}`);
    const newContact = `oussama.notify+${Date.now()}@gmail.com`;
    // 10a. PATCH normal -> changed=true
    try {
        const r = await http('PATCH', '/api/v1/auth/me/contact-email', {
            token: supervisorToken,
            body: { contactEmail: newContact },
            expect2xx: true,
        });
        if (!r.json.changed) throw new Error(`changed devrait etre true, recu ${r.json.changed}`);
        if (r.json.contactEmail !== newContact.toLowerCase()) {
            throw new Error(`contactEmail = ${r.json.contactEmail}, attendu ${newContact.toLowerCase()}`);
        }
        if (r.json.loginEmail !== seed.adminEmail.toLowerCase()) {
            throw new Error(`loginEmail change : ${r.json.loginEmail} (BUG : immuable, attendu ${seed.adminEmail})`);
        }
        record('profile', 'PATCH /me/contact-email -> changed OK + loginEmail immuable', 'PASS');
    } catch (e) {
        record('profile', 'PATCH /me/contact-email normal', 'FAIL', e.message);
    }

    // 10b. PATCH idempotent (meme valeur) -> changed=false
    try {
        const r = await http('PATCH', '/api/v1/auth/me/contact-email', {
            token: supervisorToken,
            body: { contactEmail: newContact },
            expect2xx: true,
        });
        if (r.json.changed !== false) throw new Error(`changed devrait etre false (idempotent)`);
        record('profile', 'PATCH idempotent meme valeur -> changed=false', 'PASS');
    } catch (e) {
        record('profile', 'PATCH idempotent', 'FAIL', e.message);
    }

    // 10c. PATCH contactEmail = loginEmail -> 409
    try {
        const r = await http('PATCH', '/api/v1/auth/me/contact-email', {
            token: supervisorToken,
            body: { contactEmail: seed.adminEmail }, // = loginEmail du SUPERVISEUR
        });
        if (r.status !== 409) {
            throw new Error(`attendait 409 (contact = login interdit), recu ${r.status} body=${r.text.slice(0, 200)}`);
        }
        record('profile', 'PATCH contactEmail = loginEmail -> 409 CONFLICT', 'PASS');
    } catch (e) {
        record('profile', 'PATCH contactEmail = loginEmail interdit', 'FAIL', e.message);
    }

    // 10d. GET /me reflete le nouveau contactEmail + loginEmail stable
    try {
        const r = await http('GET', '/api/v1/auth/me', { token: supervisorToken, expect2xx: true });
        if (r.json.contactEmail !== newContact.toLowerCase()) {
            throw new Error(`/me.contactEmail = ${r.json.contactEmail}, attendu ${newContact.toLowerCase()}`);
        }
        if (r.json.loginEmail !== seed.adminEmail.toLowerCase()) {
            throw new Error(`/me.loginEmail change : ${r.json.loginEmail}`);
        }
        record('profile', '/me reflete le nouveau contactEmail + loginEmail stable', 'PASS');
    } catch (e) {
        record('profile', '/me apres PATCH', 'FAIL', e.message);
    }

    // ───────── 11. Cleanup ─────────
    console.log(`\n${C.bold}11. Cleanup${C.reset}`);
    try {
        const r = await http('DELETE', `/api/v1/test/cleanup/${seed.workspaceId}`);
        if (r.status >= 200 && r.status < 300) {
            record('cleanup', 'DELETE workspace seed', 'PASS');
        } else {
            record('cleanup', 'DELETE workspace seed', 'SKIP', `HTTP ${r.status}`);
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
