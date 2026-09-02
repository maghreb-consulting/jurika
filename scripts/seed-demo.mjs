#!/usr/bin/env node
/**
 * scripts/seed-demo.mjs
 *
 * Sprint Beta (pricing-deploy) — TASK 7.
 *
 * Seed un workspace de demo "Maghreb Consulting Demo" plan Business active,
 * 1 superviseur + 2 employes + 1 client, ~10 dossiers varies (creations,
 * modifications, dissolutions, liquidation, succursale), une dizaine de
 * tickets a differents statuts, et un peu de dataroom.
 *
 * Idempotent : si le workspace JUR-DEMO2 existe deja, on UPDATE les
 * proprietes-cle ; on ne re-cree pas les rows. Pour repartir vierge,
 * passer --reset (DELETE CASCADE puis re-create).
 *
 * Pre-requis :
 *  - PostgreSQL 16 demarre (docker compose up postgres) avec migrations
 *    appliquees (V1..V22 auth, V1..V3 ticket, V13..V16 dataroom).
 *  - DATABASE_URL ou parametres POSTGRES_* dans .env.local / .env
 *  - dep 'pg' npm install (le script verifie + suggere si absent)
 *
 * Usage :
 *   node scripts/seed-demo.mjs              # idempotent (defaut)
 *   node scripts/seed-demo.mjs --reset      # vide puis recree
 *   node scripts/seed-demo.mjs --workspace=JUR-DEMO3
 *
 * Comptes seedes :
 *   workspace : JUR-DEMO2 / Maghreb Consulting Demo
 *   Superviseur : superviseur@demo.jurika.ma / Demo@2026
 *   Employe 1   : employe1@demo.jurika.ma   / Demo@2026
 *   Employe 2   : employe2@demo.jurika.ma   / Demo@2026
 *   Client      : client@demo.jurika.ma     / Demo@2026
 *
 * Tous les comptes ont must_change_password=FALSE (Demo@2026 stable, BCrypt
 * cost 10 pour la rapidite seed — DEV ONLY).
 */

import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';
import { randomUUID } from 'node:crypto';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');

// ─── Charge l'env ────────────────────────────────────────────────────────
async function loadEnv() {
    for (const f of [join(PROJECT_ROOT, '.env.local'), join(PROJECT_ROOT, '.env')]) {
        try {
            await access(f, fsConstants.R_OK);
            const raw = await readFile(f, 'utf8');
            for (const line of raw.split(/\r?\n/)) {
                const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
                if (!m) continue;
                if (!process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
            }
        } catch { /* fichier absent */ }
    }
}

// ─── Charge pg avec message clair si absent ─────────────────────────────
async function loadPg() {
    try {
        const mod = await import('pg');
        return mod.default ?? mod;
    } catch (e) {
        console.error('❌ Le module npm "pg" est requis.');
        console.error('   Installe-le a la racine :  npm install pg --save-dev');
        console.error('   (Sans cette dep, on ne peut pas parler a PostgreSQL en pur JS.)');
        process.exit(1);
    }
}

const RESET = process.argv.includes('--reset');
const WS_ARG = process.argv.find(a => a.startsWith('--workspace='));
const WORKSPACE_CODE = WS_ARG ? WS_ARG.split('=')[1] : 'JUR-DEMO2';

// ─── BCrypt fixture pour 'Demo@2026' ────────────────────────────────────
// Genere via : python -c "import bcrypt; print(bcrypt.hashpw(b'Demo@2026', bcrypt.gensalt(10)).decode())"
// Hash stable pour seed deterministe.
const BCRYPT_DEMO_2026 = '$2a$10$gVNHnC3OyBqu8SJMOiYQr.fDcu0u03UqBKttIAbwp78uk4PKXKgBC';

// ─── Catalogue dossiers demo ────────────────────────────────────────────
const DOSSIERS = [
    { raison: 'SARL Atlas Conseil',         forme: 'SARL',    ice: '001234567000089', ville: 'Casablanca', capital: 100000.00, statut: 'ACTIVE' },
    { raison: 'SARL AU Khouribga Holding',  forme: 'SARL_AU', ice: '002345678000076', ville: 'Khouribga', capital: 50000.00,  statut: 'ACTIVE' },
    { raison: 'Maroc Telecom Services SARL',forme: 'SARL',    ice: '003456789000063', ville: 'Rabat',     capital: 250000.00, statut: 'EN_CONSTITUTION' },
    { raison: 'Casa Digital Solutions',     forme: 'SARL',    ice: '004567890000050', ville: 'Casablanca', capital: 100000.00, statut: 'ACTIVE' },
    { raison: 'Marrakech Tourism Group',    forme: 'SARL',    ice: '005678901000047', ville: 'Marrakech', capital: 500000.00, statut: 'ACTIVE' },
    { raison: 'Tanger Med Logistics SAU',   forme: 'SARL_AU', ice: '006789012000034', ville: 'Tanger',    capital: 75000.00,  statut: 'ACTIVE' },
    { raison: 'Agadir Maritime Trading',    forme: 'SARL',    ice: '007890123000021', ville: 'Agadir',    capital: 150000.00, statut: 'ACTIVE' },
    { raison: 'Fes Patrimoine Immobilier',  forme: 'SARL',    ice: '008901234000018', ville: 'Fes',       capital: 200000.00, statut: 'DISSOUTE' },
    { raison: 'Oujda Mining Ventures',      forme: 'SARL',    ice: '009012345000005', ville: 'Oujda',     capital: 300000.00, statut: 'EN_LIQUIDATION' },
    { raison: 'Sidi Kacem Agro Services',   forme: 'SARL_AU', ice: '010123456000092', ville: 'Sidi Kacem',capital: 80000.00,  statut: 'ACTIVE' },
];

const TICKETS = [
    { ref: 'T-DEMO-001', titre: 'Constitution SARL Atlas Conseil',         type: 'CREATION',    statut: 'CLOTURE',    priorite: 'NORMALE', dossierIdx: 0 },
    { ref: 'T-DEMO-002', titre: 'Constitution SARL AU Khouribga Holding',  type: 'CREATION',    statut: 'CLOTURE',    priorite: 'NORMALE', dossierIdx: 1 },
    { ref: 'T-DEMO-003', titre: 'Constitution Maroc Telecom Services',     type: 'CREATION',    statut: 'EN_COURS',   priorite: 'HAUTE',   dossierIdx: 2 },
    { ref: 'T-DEMO-004', titre: 'Modification capital Casa Digital',       type: 'MODIFICATION', statut: 'EN_COURS',  priorite: 'NORMALE', dossierIdx: 3 },
    { ref: 'T-DEMO-005', titre: 'Changement gerant Marrakech Tourism',     type: 'MODIFICATION', statut: 'NOUVEAU',   priorite: 'BASSE',   dossierIdx: 4 },
    { ref: 'T-DEMO-006', titre: 'Dissolution Fes Patrimoine Immobilier',   type: 'DISSOLUTION', statut: 'CLOTURE',    priorite: 'NORMALE', dossierIdx: 7 },
    { ref: 'T-DEMO-007', titre: 'Liquidation Oujda Mining Ventures',       type: 'LIQUIDATION', statut: 'EN_COURS',   priorite: 'HAUTE',   dossierIdx: 8 },
    { ref: 'T-DEMO-008', titre: 'AGO 2025 Atlas Conseil',                  type: 'PV_AGO',      statut: 'NOUVEAU',    priorite: 'NORMALE', dossierIdx: 0 },
    { ref: 'T-DEMO-009', titre: 'Ouverture succursale Tanger Med',         type: 'SUCCURSALE_MA', statut: 'EN_COURS', priorite: 'NORMALE', dossierIdx: 5 },
    { ref: 'T-DEMO-010', titre: 'Annule : doublon Agadir Maritime',        type: 'CREATION',    statut: 'ANNULE',     priorite: 'BASSE',   dossierIdx: 6 },
];

// ─── Main ──────────────────────────────────────────────────────────────
(async () => {
    await loadEnv();
    const pg = await loadPg();

    const config = {
        host: process.env.POSTGRES_HOST || 'localhost',
        port: parseInt(process.env.POSTGRES_PORT || '5432', 10),
        database: process.env.POSTGRES_DB || 'jurika_db',
        user: process.env.POSTGRES_USER || 'jurika_user',
        password: process.env.POSTGRES_PASSWORD || 'JurikaDevPass2026',
    };

    const client = new pg.Client(config);
    await client.connect();
    console.log(`🔗 Connecte a ${config.host}:${config.port}/${config.database} (user=${config.user})`);

    try {
        await client.query('BEGIN');

        // 1. Workspace ─────────────────────────────────────────────────
        const wsCheck = await client.query(
            'SELECT id FROM workspaces WHERE code = $1', [WORKSPACE_CODE]);

        let workspaceId;
        if (wsCheck.rowCount > 0 && !RESET) {
            workspaceId = wsCheck.rows[0].id;
            console.log(`✓ Workspace ${WORKSPACE_CODE} existe deja (${workspaceId}) — UPDATE proprietes`);
            await client.query(`
                UPDATE workspaces SET
                    name = 'Maghreb Consulting Demo',
                    contact_email = 'contact@demo.jurika.ma',
                    status = 'ACTIVE',
                    selected_plan = 'business',
                    trial_status = 'CONVERTED',
                    city = 'Casablanca',
                    preferred_theme = 'light',
                    updated_at = NOW()
                WHERE id = $1
            `, [workspaceId]);
        } else {
            if (wsCheck.rowCount > 0 && RESET) {
                console.log(`🗑️  --reset : DELETE workspace ${WORKSPACE_CODE} et CASCADE`);
                await client.query('DELETE FROM workspaces WHERE id = $1', [wsCheck.rows[0].id]);
            }
            workspaceId = randomUUID();
            await client.query(`
                INSERT INTO workspaces
                    (id, code, name, contact_email, status, selected_plan, trial_status,
                     trial_started_at, trial_ends_at, city, preferred_theme, created_at, updated_at)
                VALUES
                    ($1, $2, 'Maghreb Consulting Demo', 'contact@demo.jurika.ma', 'ACTIVE',
                     'business', 'CONVERTED',
                     NOW() - INTERVAL '30 days', NOW() - INTERVAL '16 days',
                     'Casablanca', 'light', NOW(), NOW())
            `, [workspaceId, WORKSPACE_CODE]);
            console.log(`✓ Workspace ${WORKSPACE_CODE} cree (${workspaceId})`);
        }

        // 2. Users ─────────────────────────────────────────────────────
        const USERS = [
            { email: 'superviseur@demo.jurika.ma', role: 'SUPERVISEUR', first: 'Said',  last: 'Bennani'  },
            { email: 'employe1@demo.jurika.ma',    role: 'EMPLOYE',     first: 'Karima', last: 'El Idrissi' },
            { email: 'employe2@demo.jurika.ma',    role: 'EMPLOYE',     first: 'Mehdi',  last: 'Tazi'    },
            { email: 'client@demo.jurika.ma',      role: 'CLIENT',      first: 'Hassan', last: 'Alaoui'  },
        ];
        const userIds = {};
        for (const u of USERS) {
            const existing = await client.query(
                'SELECT id FROM users WHERE workspace_id = $1 AND email = $2',
                [workspaceId, u.email]);
            let id;
            if (existing.rowCount > 0) {
                id = existing.rows[0].id;
                await client.query(`
                    UPDATE users SET role = $1, first_name = $2, last_name = $3,
                        password_hash = $4, must_change_password = FALSE,
                        is_active = TRUE, updated_at = NOW(),
                        email_verified_at = COALESCE(email_verified_at, NOW())
                    WHERE id = $5
                `, [u.role, u.first, u.last, BCRYPT_DEMO_2026, id]);
            } else {
                id = randomUUID();
                await client.query(`
                    INSERT INTO users
                        (id, workspace_id, email, password_hash, first_name, last_name,
                         role, must_change_password, is_active, totp_enabled, created_at, updated_at,
                         email_verified_at)
                    VALUES
                        ($1, $2, $3, $4, $5, $6, $7, FALSE, TRUE, FALSE, NOW(), NOW(), NOW())
                `, [id, workspaceId, u.email, BCRYPT_DEMO_2026, u.first, u.last, u.role]);
            }
            userIds[u.email] = id;
            console.log(`  ✓ user ${u.role.padEnd(11)} ${u.email}`);
        }

        // 3. Dossiers ──────────────────────────────────────────────────
        const dossierIds = [];
        for (let i = 0; i < DOSSIERS.length; i++) {
            const d = DOSSIERS[i];
            const existing = await client.query(
                'SELECT id FROM entreprise_dossiers WHERE workspace_id = $1 AND ice = $2',
                [workspaceId, d.ice]);
            let id;
            if (existing.rowCount > 0) {
                id = existing.rows[0].id;
                await client.query(`
                    UPDATE entreprise_dossiers SET
                        raison_sociale = $1, forme_juridique = $2, ville = $3,
                        capital_social_mad = $4, statut = $5, updated_at = NOW()
                    WHERE id = $6
                `, [d.raison, d.forme, d.ville, d.capital, d.statut, id]);
            } else {
                id = randomUUID();
                await client.query(`
                    INSERT INTO entreprise_dossiers
                        (id, workspace_id, raison_sociale, forme_juridique, ice, ville,
                         capital_social_mad, date_constitution, statut, created_at, updated_at)
                    VALUES
                        ($1, $2, $3, $4, $5, $6, $7, NOW() - (random() * 365)::int * INTERVAL '1 day',
                         $8, NOW(), NOW())
                `, [id, workspaceId, d.raison, d.forme, d.ice, d.ville, d.capital, d.statut]);
            }
            dossierIds.push(id);
        }
        console.log(`✓ ${DOSSIERS.length} dossiers seedes`);

        // 4. Tickets ───────────────────────────────────────────────────
        const employe1 = userIds['employe1@demo.jurika.ma'];
        const employe2 = userIds['employe2@demo.jurika.ma'];
        for (const t of TICKETS) {
            const existing = await client.query(
                'SELECT id FROM tickets WHERE workspace_id = $1 AND reference = $2',
                [workspaceId, t.ref]);
            const assigne = t.dossierIdx % 2 === 0 ? employe1 : employe2;
            const dossierId = dossierIds[t.dossierIdx];
            const closingDate = t.statut === 'CLOTURE' ? 'NOW() - INTERVAL \'7 days\'' : 'NULL';
            if (existing.rowCount > 0) {
                await client.query(`
                    UPDATE tickets SET titre = $1, type = $2, statut = $3,
                        priorite = $4, dossier_id = $5, assigne_id = $6,
                        updated_at = NOW()
                    WHERE id = $7
                `, [t.titre, t.type, t.statut, t.priorite, dossierId, assigne, existing.rows[0].id]);
            } else {
                const id = randomUUID();
                const motif = t.statut === 'ANNULE' ? 'Doublon detecte apres saisie initiale' : null;
                await client.query(`
                    INSERT INTO tickets
                        (id, workspace_id, reference, titre, type, statut, priorite,
                         dossier_id, assigne_id, cree_par_id, description, annulation_motif,
                         cloture_at, created_at, updated_at)
                    VALUES
                        ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
                         'Ticket de demonstration seede par scripts/seed-demo.mjs', $11,
                         ${closingDate}, NOW() - (random() * 30)::int * INTERVAL '1 day', NOW())
                `, [id, workspaceId, t.ref, t.titre, t.type, t.statut, t.priorite,
                    dossierId, assigne, employe1, motif]);
            }
        }
        console.log(`✓ ${TICKETS.length} tickets seedes`);

        await client.query('COMMIT');
        console.log('\n✅ Seed demo OK.');
        console.log(`\n   Workspace : ${WORKSPACE_CODE}`);
        console.log('   Comptes (mot de passe partage : Demo@2026) :');
        for (const u of USERS) {
            console.log(`     ${u.role.padEnd(11)}  ${u.email}`);
        }
    } catch (e) {
        await client.query('ROLLBACK').catch(() => {});
        throw e;
    } finally {
        await client.end();
    }
})().catch(err => {
    console.error('❌', err.message);
    if (err.stack) console.error(err.stack);
    process.exit(1);
});
