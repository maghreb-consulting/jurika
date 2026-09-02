#!/usr/bin/env node
/**
 * scripts/e2e-workflows-succursales-pv-ago.mjs
 *
 * E2E API dedie aux 4 workflows refondus en UI dediee (RG transverse 2026-06-06) :
 *   - SUCCURSALE_MA (11 etapes)
 *   - SUCCURSALE_ETR (13 etapes, conformite bloquante step 2)
 *   - FERMETURE_SUCCURSALE (4 etapes)
 *   - PV_AGO (4 etapes)
 *
 * Couvre par workflow :
 *   - POST /workflows/{ticket}/start
 *   - chaque etape avec VALIDATION metier (cas erreur attendu 400 + fragment FR)
 *   - chaque etape en SUCCESS (parcours nominal)
 *   - finalisation -> transition ticket NOUVEAU/EN_COURS -> CLOTURE
 *
 * Pre-req : endpoint /api/v1/test/seed/workspace actif (profil !prod).
 * Sortie : tableau OK/KO/SKIP, exit = nb FAIL.
 */
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';
const SEED_PATH = '/api/v1/test/seed/workspace';

const C = { pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
            dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m' };

const results = [];
function record(group, name, status, detail = '') {
    results.push({ group, name, status, detail });
    const color = status === 'PASS' ? C.pass : status === 'FAIL' ? C.fail : C.skip;
    console.log(`  ${color}[${status}]${C.reset} ${name}${detail ? '  ' + C.dim + detail.slice(0, 220) + C.reset : ''}`);
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

function section(t) { console.log(`\n${C.bold}${C.cyan}═══ ${t} ═══${C.reset}`); }

async function loginAs(wsCode, email, pwd) {
    const r = await http('POST', '/api/v1/auth/login',
        { body: { workspaceCode: wsCode, email, password: pwd } });
    return r.ok ? { token: r.body.accessToken, userId: r.body.userId, wsId: r.body.workspaceId } : null;
}

async function seedEmployeeWorkspace() {
    const seed = await http('POST', `${SEED_PATH}?role=EMPLOYE`, { body: {} });
    if (!seed.ok) {
        record('Setup', 'seed workspace (EMPLOYE)', 'FAIL',
            `${seed.status} ${JSON.stringify(seed.body).slice(0, 200)}`);
        return null;
    }
    const auth = await loginAs(seed.body.workspaceCode, seed.body.adminEmail,
        seed.body.adminPassword || 'DemoPwd2026!');
    if (!auth) {
        record('Setup', 'login seeded admin', 'FAIL'); return null;
    }
    record('Setup', 'seed + login', 'PASS', `ws=${seed.body.workspaceCode}`);
    return { ...seed.body, ...auth };
}

async function pickDossier(token, expectedStatuts = ['ACTIVE', 'EN_CONSTITUTION']) {
    const d = await http('GET', '/api/v1/dataroom/dossiers', { token });
    if (!d.ok) return null;
    const list = Array.isArray(d.body) ? d.body : (d.body?.items || []);
    return list.find((x) => expectedStatuts.includes(String(x.statut).toUpperCase())) ?? list[0] ?? null;
}

async function createTicketAndStart(token, dossierId, type, titrePrefix) {
    const tk = await http('POST', '/api/v1/tickets', {
        token,
        body: {
            titre: `${titrePrefix} ${Date.now()}`,
            type,
            priorite: 'NORMALE',
            dossierId,
            description: `E2E succ/AGO ${type}`,
        },
    });
    if (!tk.ok) {
        record(type, 'POST /tickets', 'FAIL',
            `${tk.status} ${JSON.stringify(tk.body).slice(0, 200)}`);
        return null;
    }
    record(type, 'POST /tickets', 'PASS', `id=${tk.body.id?.slice(0, 8)}`);
    const start = await http('POST', `/api/v1/workflows/${tk.body.id}/start`, {
        token, body: { type },
    });
    if (!start.ok) {
        record(type, `POST /workflows/start ${type}`, 'FAIL',
            `${start.status} ${JSON.stringify(start.body).slice(0, 200)}`);
        return null;
    }
    record(type, `POST /workflows/start ${type}`, 'PASS',
        `step=${start.body?.currentStep}/${start.body?.totalSteps}`);
    return { ticketId: tk.body.id, progress: start.body };
}

async function expectStepError(token, ticketId, step, payload, label, expectedFragmentLc) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
        token, body: { step, payload },
    });
    if (r.status === 400) {
        const msg = (r.body?.message || r.body?.detail || JSON.stringify(r.body) || '').toLowerCase();
        // Le fragment attendu est mis en minuscules lui aussi : sinon un fragment ecrit
        // « RC » ou « TOTAL » ne peut JAMAIS correspondre au message deja abaisse, et le
        // test echoue alors que le backend s'est comporte correctement.
        const fragment = expectedFragmentLc ? expectedFragmentLc.toLowerCase() : null;
        const ok = !fragment || msg.includes(fragment);
        record('VALIDATION', label, ok ? 'PASS' : 'FAIL',
            ok ? `400 "${msg.slice(0, 80)}"` : `400 mais sans fragment "${fragment}" (msg="${msg.slice(0, 80)}")`);
        return ok;
    }
    record('VALIDATION', label, 'FAIL', `expected 400, got ${r.status}`);
    return false;
}

async function expectStepOk(token, ticketId, step, payload, label) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
        token, body: { step, payload },
    });
    if (r.ok && r.body?.advanced) {
        record('NOMINAL', label, 'PASS', `advanced -> step ${r.body.progress?.currentStep}`);
        return r.body;
    }
    record('NOMINAL', label, 'FAIL',
        `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    return null;
}

async function autoTransitionsCheck(token, ticketId, label) {
    const t = await http('GET', `/api/v1/tickets/${ticketId}`, { token });
    const statut = t.body?.statut;
    record('TRANSITION', `${label} ticket statut`, statut === 'CLOTURE' ? 'PASS' : 'FAIL',
        `statut=${statut}`);
}

// ============================================================
//  SUCCURSALE_MA (5 etapes — refonte lot DIVERS §B, 2026-08-13)
//    1 Societe mere + date/type d'assemblee + convocation optionnelle (16 j)
//    2 Saisie succursale (greffe propre, dotation, responsable)
//    3 Generation (PV + annonce legale d'ouverture)
//    4 Pieces jointes (OPTIONNELLE)
//    5 Synthese
// ============================================================
/** Date d'assemblee toujours dans la fenetre acceptee (J-2 ans .. J+30). */
function isoInDays(days) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
}

async function workflowSuccursaleMa(ctx) {
    section('SUCCURSALE_MA (RG-SM)');
    const dossier = await pickDossier(ctx.token);
    if (!dossier) {
        record('SUCCURSALE_MA', 'pre-req dossier mere', 'SKIP'); return;
    }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'SUCCURSALE_MA', 'E2E SM');
    if (!wf) return;
    const t = ctx.token, id = wf.ticketId;

    const dateAG = isoInDays(10);
    const convocationOk = isoInDays(10 - 16);   // 16 jours pile avant l'assemblee
    const convocationKo = isoInDays(10 - 11);   // 11 jours -> refuse

    // Step 1 — societe mere obligatoire
    await expectStepError(t, id, 1, { dateAG }, 'SM step1 sans societe mere', 'societe mere');
    // Type d'assemblee obligatoire
    await expectStepError(t, id, 1, { dossierId: dossier.id, dateAG },
        'SM step1 sans type assemblee', 'ordinaire');
    // Convocation : regle DURE des 16 jours (backend, pas seulement UI)
    await expectStepError(t, id, 1,
        { dossierId: dossier.id, dateAG, typeAssemblee: 'extraordinaire',
          convocation: { date: convocationKo } },
        'SM step1 convocation < 16 j', '16');
    await expectStepOk(t, id, 1,
        { dossierId: dossier.id, dateAG, typeAssemblee: 'extraordinaire',
          convocation: { date: convocationOk } },
        'SM step1 ok');

    // Step 2 — saisie succursale
    await expectStepError(t, id, 2, { succursale: { enseigne: 'ATLAS BRANCH' } },
        'SM step2 sans adresse', 'adresse');
    // Dotation annoncee sans montant -> refuse
    await expectStepError(t, id, 2, {
        succursale: {
            enseigne: 'ATLAS BRANCH', adresse: '12 rue Atlas', ville: 'Casablanca',
            activite: 'Vente de detail', dateOuverture: isoInDays(20),
            dotationPresente: true,
        },
    }, 'SM step2 dotation sans montant', 'montant');
    await expectStepOk(t, id, 2, {
        succursale: {
            enseigne: 'ATLAS BRANCH', adresse: '12 rue Atlas', ville: 'Casablanca',
            villeGreffe: 'CASABLANCA', activite: 'Vente de detail',
            dateOuverture: isoInDays(20),
            dotationPresente: true, dotationMontant: 250000,
            responsablePresent: false,
        },
        formalitesMandataireNom: 'M. Karim BENALI',
    }, 'SM step2 ok');

    // Step 3 — generation : PV ET annonce obligatoires
    await expectStepError(t, id, 3, {}, 'SM step3 sans pvValide', 'pv');
    await expectStepError(t, id, 3, { pvValide: true }, 'SM step3 sans annonce', 'annonce');
    await expectStepOk(t, id, 3, { pvValide: true, annonceValide: true }, 'SM step3 ok');

    // Step 4 — pieces jointes OPTIONNELLE (peut rester vide)
    await expectStepOk(t, id, 4, {}, 'SM step4 pieces jointes vides ok');

    // Step 5 — synthese
    const fin = await expectStepOk(t, id, 5, {}, 'SM step5 finalise');
    if (fin) await autoTransitionsCheck(t, id, 'SUCCURSALE_MA');
}

// ============================================================
//  SUCCURSALE_ETR (5 etapes — refonte lot DIVERS §C, 2026-08-13)
//    1 Societe mere etrangere (saisie ou selection) + decision + convocation 16 j
//      + 6 controles de conformite BLOQUANTS
//    2 Saisie succursale (noyau PARTAGE avec SUCCURSALE_MA)
//    3 Generation (PV + annonce legale, variante ETRANGERE derivee)
//    4 Pieces jointes (OPTIONNELLE)
//    5 Synthese
// ============================================================
const CONTROLES_ETR = {
    controleOffice: true, controleApostille: true, controleTraduction: true,
    controleProcuration: true, controleDomiciliation: true, controleConvention: true,
};

async function workflowSuccursaleEtr(ctx) {
    section('SUCCURSALE_ETR (RG-SE)');
    const dossier = await pickDossier(ctx.token);
    if (!dossier) { record('SUCCURSALE_ETR', 'pre-req dossier', 'SKIP'); return; }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'SUCCURSALE_ETR', 'E2E SE');
    if (!wf) return;
    const t = ctx.token, id = wf.ticketId;

    const dateAG = isoInDays(10);
    const mere = {
        denominationSocieteMere: 'ACME SAS',
        formeJuridiqueOrigine: 'SAS',
        paysOrigine: 'France',
        siege: '12 rue de Rivoli, Paris',
    };

    // Step 1 — societe mere obligatoire
    await expectStepError(t, id, 1, { dateAG, typeAssemblee: 'extraordinaire', ...CONTROLES_ETR },
        'SE step1 sans societe mere', 'societe mere');
    // Mentions publiees obligatoires (siege manquant)
    await expectStepError(t, id, 1, {
        denominationSocieteMere: 'ACME SAS', formeJuridiqueOrigine: 'SAS',
        paysOrigine: 'France', dateAG, typeAssemblee: 'extraordinaire', ...CONTROLES_ETR,
    }, 'SE step1 sans siege mere', 'annonce');
    // Conformite : 6/6 exiges (bloquant, conserve de l'ancien parcours)
    await expectStepError(t, id, 1, {
        ...mere, dateAG, typeAssemblee: 'extraordinaire',
        ...CONTROLES_ETR, controleConvention: false,
    }, 'SE step1 conformite incomplete', 'conformite');
    // Convocation : regle DURE des 16 jours
    await expectStepError(t, id, 1, {
        ...mere, dateAG, typeAssemblee: 'extraordinaire', ...CONTROLES_ETR,
        convocation: { date: isoInDays(10 - 11) },
    }, 'SE step1 convocation < 16 j', '16');
    await expectStepOk(t, id, 1, {
        ...mere, dateAG, typeAssemblee: 'extraordinaire', ...CONTROLES_ETR,
        organe: { competent: "le conseil d'administration" },
        convocation: { date: isoInDays(10 - 16) },
    }, 'SE step1 ok');

    // Step 2 — saisie succursale (memes regles que SUCCURSALE_MA)
    await expectStepError(t, id, 2, { succursale: { enseigne: 'ACME Maroc Branch' } },
        'SE step2 sans adresse', 'adresse');
    await expectStepOk(t, id, 2, {
        succursale: {
            enseigne: 'ACME Maroc Branch', adresse: '5 rue Mohammed V', ville: 'Casablanca',
            villeGreffe: 'CASABLANCA', activite: 'Services informatiques B2B',
            dateOuverture: isoInDays(20),
            dotationPresente: false,
            responsablePresent: true,
            responsable: {
                nom: 'TAZI', prenom: 'Yassine', adresse: '5 rue Mohammed V, Casablanca',
                pieceNumero: 'XB789012', pouvoirs: 'diriger la succursale',
            },
        },
    }, 'SE step2 ok');

    // Step 3 — generation : PV ET annonce obligatoires
    await expectStepError(t, id, 3, { pvValide: true }, 'SE step3 sans annonce', 'annonce');
    await expectStepOk(t, id, 3, { pvValide: true, annonceValide: true }, 'SE step3 ok');

    // Step 4 — pieces jointes OPTIONNELLE
    await expectStepOk(t, id, 4, {}, 'SE step4 pieces jointes vides ok');

    // Step 5 — synthese
    const fin = await expectStepOk(t, id, 5, {}, 'SE step5 finalise');
    if (fin) await autoTransitionsCheck(t, id, 'SUCCURSALE_ETR');
}

// ============================================================
//  FERMETURE_SUCCURSALE (4 etapes — refonte lot DIVERS §D, 2026-08-13)
//    1 Selection BD (succursale + RC repris) + date/type AG + convocation 16 j
//      + date d'effet + motif (PUBLIE dans l'annonce)
//    2 Generation (PV + annonce legale de fermeture)
//    3 Pieces jointes (OPTIONNELLE)
//    4 Synthese (succursale -> FERMEE + date + motif)
// ============================================================
async function workflowFermeture(ctx) {
    section('FERMETURE_SUCCURSALE (RG-FS)');
    const dossier = await pickDossier(ctx.token);
    if (!dossier) { record('FERMETURE_SUCCURSALE', 'pre-req dossier mere', 'SKIP'); return; }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'FERMETURE_SUCCURSALE', 'E2E FS');
    if (!wf) return;
    const t = ctx.token, id = wf.ticketId;

    const dateAG = isoInDays(10);
    const succursale = {
        enseigne: 'PARACOSME — Agence Casablanca',
        adresse: '12 rue Atlas',
        ville: 'Casablanca',
        villeGreffe: 'CASABLANCA',
        activite: 'Conseil',
        rcNumero: '78901',
        dateFermeture: isoInDays(5),
        motif: 'la reorganisation du reseau commercial du cabinet',
    };

    // Step 1 — succursale ni selectionnee, ni identifiable (pas d'enseigne/ville)
    await expectStepError(t, id, 1, { dossierId: dossier.id, dateAG },
        'FS step1 sans succursale', 'succursale a fermer');
    // Saisie manuelle : la ville designe le greffe de radiation, elle est exigee.
    await expectStepError(t, id, 1, {
        dossierId: dossier.id, dateAG, typeAssemblee: 'extraordinaire',
        succursale: { ...succursale, ville: undefined },
    }, 'FS step1 saisie manuelle sans ville', 'enseigne');
    // RC obligatoire (publie dans l'avis)
    await expectStepError(t, id, 1, {
        dossierId: dossier.id, dateAG,
        typeAssemblee: 'extraordinaire',
        succursale: { ...succursale, rcNumero: undefined },
    }, 'FS step1 sans RC succursale', 'RC');
    // Motif obligatoire et suffisamment precis
    await expectStepError(t, id, 1, {
        dossierId: dossier.id, dateAG,
        typeAssemblee: 'extraordinaire',
        succursale: { ...succursale, motif: 'bof' },
    }, 'FS step1 motif trop court', 'trop court');
    // Convocation : regle DURE des 16 jours
    await expectStepError(t, id, 1, {
        dossierId: dossier.id, dateAG,
        typeAssemblee: 'extraordinaire', succursale,
        convocation: { date: isoInDays(10 - 11) },
    }, 'FS step1 convocation < 16 j', '16');
    // Fin de la « fermeture fantome » : une succursale saisie a la main (absente du
    // referentiel) doit etre CREEE en base des cette etape, sinon le workflow produirait
    // PV et annonce sans qu'aucune succursale ne soit jamais marquee FERMEE.
    const s1 = await expectStepOk(t, id, 1, {
        dossierId: dossier.id, dateAG,
        typeAssemblee: 'extraordinaire', succursale,
        convocation: { date: isoInDays(10 - 16) },
    }, 'FS step1 ok (saisie manuelle)');
    const succDbId = s1?.progress?.data?.step1?.succursaleDbId;
    record('NOMINAL', 'FS step1 succursale creee en base', succDbId ? 'PASS' : 'FAIL',
        succDbId ? `succursaleDbId=${succDbId}` : 'aucun succursaleDbId injecte');
    // Re-validation de l'etape (navigation arriere) : PAS de doublon, meme identifiant.
    const s1bis = await expectStepOk(t, id, 1, {
        dossierId: dossier.id, dateAG,
        typeAssemblee: 'extraordinaire', succursale,
        convocation: { date: isoInDays(10 - 16) },
    }, 'FS step1 re-validation ok');
    const succDbIdBis = s1bis?.progress?.data?.step1?.succursaleDbId;
    record('NOMINAL', 'FS step1 idempotent (pas de doublon)',
        succDbId && succDbIdBis === succDbId ? 'PASS' : 'FAIL',
        `1er=${succDbId} 2e=${succDbIdBis}`);

    // Step 2 — generation : PV ET annonce obligatoires
    await expectStepError(t, id, 2, {}, 'FS step2 sans pvValide', 'pv');
    await expectStepError(t, id, 2, { pvValide: true }, 'FS step2 sans annonce', 'annonce');
    await expectStepOk(t, id, 2, { pvValide: true, annonceValide: true }, 'FS step2 ok');

    // Step 3 — pieces jointes OPTIONNELLE
    await expectStepOk(t, id, 3, {}, 'FS step3 pieces jointes vides ok');

    // Step 4 — synthese
    // AVANT la finalisation, la succursale creee a l'etape 1 doit etre proposee a la
    // fermeture (elle est ACTIVE).
    const listeAvant = succDbId
        ? await http('GET', `/api/v1/dossiers/${dossier.id}/succursales`, { token: t })
        : null;
    record('NOMINAL', 'FS succursale ACTIVE avant finalisation',
        (listeAvant?.body || []).some((s) => s.id === succDbId) ? 'PASS' : 'FAIL',
        `${(listeAvant?.body || []).length} succursale(s) listee(s)`);

    const fin = await expectStepOk(t, id, 4, {}, 'FS step4 finalise');
    if (fin) await autoTransitionsCheck(t, id, 'FERMETURE_SUCCURSALE');

    // La fermeture doit etre REELLE. L'endpoint ne renvoie QUE les succursales ACTIVE
    // (SuccursaleController) : la disparition de la liste est donc exactement la preuve
    // que la ligne est passee FERMEE. C'est ce que la « fermeture fantome » ne faisait
    // pas — PV et annonce produits, succursale toujours ACTIVE et re-proposee.
    if (fin && succDbId) {
        const r = await http('GET', `/api/v1/dossiers/${dossier.id}/succursales`, { token: t });
        const encoreActive = (r.body || []).some((s) => s.id === succDbId);
        record('NOMINAL', 'FS succursale reellement FERMEE en base',
            encoreActive ? 'FAIL' : 'PASS',
            encoreActive
                ? 'toujours listee ACTIVE -> fermeture fantome'
                : 'absente de la liste des ACTIVE');
    }
}

// ============================================================
//  PV_AGO (5 etapes — refonte lot DIVERS §E, 2026-08-13)
//    1 Societe BD + date d'AGO (ORDINAIRE par nature) + convocation 16 j + exercice clos
//    2 Donnees du PV (resultat, affectation soldee, dividendes, quitus, conventions)
//    3 Generation (PV obligatoire, rapport de gestion OPTIONNEL, AUCUNE annonce)
//    4 Pieces jointes (OPTIONNELLE)
//    5 Synthese
// ============================================================
async function workflowPvAgo(ctx) {
    section('PV_AGO (RG-AGO)');
    const dossier = await pickDossier(ctx.token, ['ACTIVE', 'EN_CONSTITUTION']);
    if (!dossier) { record('PV_AGO', 'pre-req dossier ACTIVE', 'SKIP'); return; }
    const wf = await createTicketAndStart(ctx.token, dossier.id, 'PV_AGO', 'E2E AGO');
    if (!wf) return;
    const t = ctx.token, id = wf.ticketId;

    const dateAGO = isoInDays(5);
    const exerciceClos = String(new Date().getFullYear() - 1);

    // Step 1 — societe obligatoire
    await expectStepError(t, id, 1, { exerciceClos, dateAGO },
        'AGO step1 sans dossierId', 'societe');
    // Exercice clos obligatoire
    await expectStepError(t, id, 1, { dossierId: dossier.id, dateAGO },
        'AGO step1 sans exercice clos', 'exercice clos');
    // Convocation : regle DURE des 16 jours (backend, pas seulement UI)
    await expectStepError(t, id, 1, {
        dossierId: dossier.id, exerciceClos, dateAGO,
        convocation: { date: isoInDays(5 - 11) },
    }, 'AGO step1 convocation < 16 j', '16');
    const step1 = await expectStepOk(t, id, 1, {
        dossierId: dossier.id, exerciceClos, dateAGO,
        convocation: { date: isoInDays(5 - 16) },
    }, 'AGO step1 ok');
    if (step1) {
        // L'approbation des comptes releve TOUJOURS de l'AGO : la nature est imposee.
        const nature = step1.progress?.data?.step1?.assembleeNature
            ?? step1.stepData?.assembleeNature;
        record('PV_AGO', 'AGO assemblee ORDINAIRE imposee',
            nature === 'ordinaire' ? 'PASS' : 'FAIL', `nature=${nature}`);
    }

    // Step 2 — donnees du PV
    const approbationBase = {
        exerciceClosDate: `${exerciceClos}-12-31`,
        resultatType: 'bénéfice',
        resultatNet: 150000,
    };
    // Affectation obligatoire
    await expectStepError(t, id, 2, { approbation: approbationBase },
        'AGO step2 sans affectation', 'affectation');
    // L'affectation doit SOLDER le resultat
    await expectStepError(t, id, 2, {
        approbation: {
            ...approbationBase,
            affectations: [{ libelle: 'Réserve légale', montant: 7500 }],
        },
    }, 'AGO step2 affectation desequilibree', 'ne solde pas');
    // Dividendes annonces : total + par part + date obligatoires
    await expectStepError(t, id, 2, {
        approbation: {
            ...approbationBase,
            affectations: [
                { libelle: 'Réserve légale', montant: 7500 },
                { libelle: 'Dividendes', montant: 142500 },
            ],
            dividendeDistribue: 'oui',
        },
    }, 'AGO step2 dividendes sans total', 'TOTAL');
    await expectStepOk(t, id, 2, {
        approbation: {
            ...approbationBase,
            affectations: [
                { libelle: 'Réserve légale', montant: 7500 },
                { libelle: 'Report à nouveau', montant: 42500 },
                { libelle: 'Dividendes', montant: 100000 },
            ],
            dividendeDistribue: 'oui',
            dividendeMontantTotal: 100000,
            dividendeParPart: 100,
            dividendeMiseEnPaiementDate: isoInDays(60),
            quitusGerance: 'oui',
            conventionsReglementees: 'non',
        },
    }, 'AGO step2 ok');

    // Step 3 — PV obligatoire, rapport de gestion OPTIONNEL, aucune annonce
    await expectStepError(t, id, 3, {}, 'AGO step3 sans pvValide', 'pv');
    const step3 = await expectStepOk(t, id, 3, { pvValide: true }, 'AGO step3 ok');
    if (step3) {
        const data = step3.progress?.data?.step3 ?? step3.stepData ?? {};
        record('PV_AGO', 'AGO aucune annonce legale requise',
            data.annonceRequise === false ? 'PASS' : 'FAIL',
            `annonceRequise=${data.annonceRequise}`);
    }

    // Step 4 — pieces jointes OPTIONNELLE
    await expectStepOk(t, id, 4, {}, 'AGO step4 pieces jointes vides ok');

    // Step 5 — synthese
    const fin = await expectStepOk(t, id, 5, {}, 'AGO step5 finalise');
    if (fin) await autoTransitionsCheck(t, id, 'PV_AGO');
}

// ============================================================
async function main() {
    await loadEnv();
    console.log(`${C.bold}E2E Workflows SUCCURSALES (MA/ETR) + FERMETURE + PV AGO${C.reset}`);
    console.log(`${C.dim}Base : ${BASE}${C.reset}\n`);

    section('Setup');
    const ctx = await seedEmployeeWorkspace();
    if (!ctx) {
        console.log(`${C.fail}Impossible d'initialiser le contexte. Verifiez que la stack est UP et que ${SEED_PATH} est actif.${C.reset}`);
        process.exit(1);
    }

    await workflowSuccursaleMa(ctx);
    await workflowSuccursaleEtr(ctx);
    await workflowFermeture(ctx);
    await workflowPvAgo(ctx);

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
