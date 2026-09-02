#!/usr/bin/env node
/**
 * scripts/e2e-import-workflow.mjs
 *
 * E2E API dedie au workflow IMPORT (digitalisation d'une societe DEJA active).
 *
 * Refonte 2026-06-25 : l'IMPORT compte desormais 11 etapes (au lieu de 6) et
 * reutilise le MEME tronc de SAISIE que la CREATION SARL (denomination / siege /
 * capital / activite / dirigeants / associes), suivi de 3 uploads typed
 * (juridique / comptable / fiscal), d'un Suivi des exercices, puis d'une
 * Synthese qui consolide la fiche juridique COMPLETE.
 *
 * Couvre :
 *   - ouverture ticket IMPORT -> auto-creation dossier statut=ACTIVE (RG-IM01)
 *   - step 1  : Denomination — ICE 15 chiffres + RC + IF obligatoires (RG-C13)
 *   - step 2  : Siege (adresse / province / commune / code postal)
 *   - step 3  : Capital (apport numeraire + parts ; PAS de controle 25 %/depot)
 *   - step 4  : Activite (objet social + date debut exercice)
 *   - step 5  : Dirigeants (+ gouvernance gerance)
 *   - step 6  : Associes (somme des parts == total step3 ; SARL_AU = 1)
 *   - step 7  : Upload JURIDIQUE (typed, depose direct en Data Room)
 *   - step 8  : Upload COMPTABLE (optionnel, depose en Data Room)
 *   - step 9  : Upload FISCAL (optionnel)
 *   - step 10 : Suivi (regime TVA + annee exercice + annees anterieures)
 *   - step 11 : Synthese -> fiche juridique consolidee + importComplete=true
 *   - post : la consolidation fiche_structuree (source=IMPORT) est ecrite sur le
 *            dossier (ICE / RC / IF / capital / raison sociale propages)
 *   - post : ouverture exercice (front orchestration) -> echeances generees
 *            visibles via GET /dataroom/dossiers/{id}/echeances
 *
 * Pre-req :
 *   - Stack jurika up (gateway 8080 + auth + ticket + workflow + dataroom
 *     + ai-service).
 *   - Endpoint seed /api/v1/test/seed/workspace actif (profil !prod).
 *
 * Lancement :  node scripts/e2e-import-workflow.mjs
 * Sortie : tableau OK/KO, exit code = nb FAIL.
 */
import { readFile, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants as fsConstants } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '..');
const BASE = process.env.BASE || 'http://localhost:8080';
const SEED_PATH = '/api/v1/test/seed/workspace';

const C = {
    pass: '\x1b[32m', fail: '\x1b[31m', skip: '\x1b[33m',
    dim: '\x1b[90m', cyan: '\x1b[36m', reset: '\x1b[0m', bold: '\x1b[1m',
};

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
        } catch { }
    }
}

async function http(method, path, { body = null, token = null, raw = false } = {}) {
    const url = `${BASE}${path}`;
    const headers = { Accept: 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let bodyStr = null;
    if (body && !raw) {
        headers['Content-Type'] = 'application/json';
        bodyStr = JSON.stringify(body);
    } else if (raw) {
        bodyStr = body;
    }
    let res;
    try { res = await fetch(url, { method, headers, body: bodyStr }); }
    catch (e) { return { ok: false, status: 0, body: null, networkError: e.message }; }
    let parsed = null;
    try { parsed = await res.json(); } catch { }
    return { ok: res.ok, status: res.status, body: parsed };
}

async function uploadMultipart(token, path, fields, fileField, fileName, fileBlob) {
    const url = `${BASE}${path}`;
    const form = new FormData();
    for (const [k, v] of Object.entries(fields)) form.append(k, v);
    form.append(fileField, fileBlob, fileName);
    const headers = { Accept: 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let res;
    try { res = await fetch(url, { method: 'POST', headers, body: form }); }
    catch (e) { return { ok: false, status: 0, body: null, networkError: e.message }; }
    let parsed = null;
    try { parsed = await res.json(); } catch { }
    return { ok: res.ok, status: res.status, body: parsed };
}

function section(t) { console.log(`\n${C.bold}${C.cyan}═══ ${t} ═══${C.reset}`); }

async function loginAs(wsCode, email, pwd) {
    const r = await http('POST', '/api/v1/auth/login',
        { body: { workspaceCode: wsCode, email, password: pwd } });
    return r.ok ? { token: r.body.accessToken, userId: r.body.userId, wsId: r.body.workspaceId } : null;
}

async function seed() {
    const r = await http('POST', `${SEED_PATH}?role=EMPLOYE`, { body: {} });
    if (!r.ok) {
        record('Setup', 'seed workspace EMPLOYE', 'FAIL',
            `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
        return null;
    }
    const auth = await loginAs(r.body.workspaceCode, r.body.adminEmail,
        r.body.adminPassword || 'DemoPwd2026!');
    if (!auth) { record('Setup', 'login seeded admin', 'FAIL'); return null; }
    record('Setup', 'seed + login', 'PASS', `ws=${r.body.workspaceCode}`);
    return { ...r.body, ...auth };
}

/** Mini-PDF binaire valide -- evite la dependance reportlab dans le script. */
function tinyPdf(title) {
    const head = `%PDF-1.4\n%\xff\xff\xff\xff\n`;
    const body =
        `1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n` +
        `2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n` +
        `3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n` +
        `4 0 obj << /Length 70 >> stream\nBT /F1 12 Tf 50 800 Td (${title}) Tj ET\nendstream endobj\n` +
        `5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n`;
    const xref = `xref\n0 6\n0000000000 65535 f \n0000000010 00000 n \n0000000060 00000 n \n0000000110 00000 n \n0000000220 00000 n \n0000000320 00000 n \n`;
    const trailer = `trailer << /Size 6 /Root 1 0 R >>\nstartxref\n400\n%%EOF\n`;
    return new Blob([head + body + xref + trailer], { type: 'application/pdf' });
}

async function createImportTicket(token, raisonSociale) {
    const r = await http('POST', '/api/v1/tickets', {
        token,
        body: {
            titre: `E2E IMPORT ${Date.now()}`,
            type: 'IMPORT',
            priorite: 'NORMALE',
            description: 'E2E IMPORT flow',
            companyInfo: { raisonSociale, formeJuridique: 'SARL' },
        },
    });
    if (!r.ok) {
        record('IMPORT', 'POST /tickets IMPORT', 'FAIL',
            `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
        return null;
    }
    record('IMPORT', 'POST /tickets IMPORT', 'PASS',
        `id=${r.body.id?.slice(0, 8)} dossier=${r.body.dossierId?.slice(0, 8)}`);
    if (!r.body.dossierId) {
        record('IMPORT', 'dossier auto-cree', 'FAIL', 'pas de dossierId sur le ticket');
        return null;
    }
    return r.body;
}

async function startWf(token, ticketId) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/start`, {
        token, body: { type: 'IMPORT' },
    });
    if (!r.ok) {
        record('IMPORT', 'POST /workflows/start IMPORT', 'FAIL',
            `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
        return null;
    }
    record('IMPORT', 'POST /workflows/start IMPORT', 'PASS',
        `step=${r.body.currentStep}/${r.body.totalSteps}`);
    return r.body;
}

async function executeStep(token, ticketId, step, payload, label) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
        token, body: { step, payload },
    });
    if (r.ok && r.body?.advanced) {
        record('IMPORT', label, 'PASS', `step ${step} -> ${r.body.progress?.currentStep}`);
        return r.body;
    }
    record('IMPORT', label, 'FAIL',
        `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    return null;
}

async function uploadJuridique(token, dossierId, documentType, filename, blob) {
    const r = await uploadMultipart(
        token,
        `/api/v1/dataroom/dossiers/${dossierId}/juridique/upload`,
        { documentType, title: filename.replace(/\.[^.]+$/, '') },
        'file',
        filename,
        blob,
    );
    if (r.ok) {
        record('IMPORT', `upload juridique ${documentType}`, 'PASS', filename);
        return true;
    }
    record('IMPORT', `upload juridique ${documentType}`, 'FAIL',
        `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    return false;
}

async function uploadComptable(token, dossierId, annee, categorie, filename, blob) {
    const r = await uploadMultipart(
        token,
        `/api/v1/dataroom/dossiers/${dossierId}/comptable/upload`,
        { annee: String(annee), categorie, title: filename.replace(/\.[^.]+$/, '') },
        'file',
        filename,
        blob,
    );
    if (r.ok) {
        record('IMPORT', `upload comptable ${categorie}`, 'PASS', `${annee} ${filename}`);
        return true;
    }
    record('IMPORT', `upload comptable ${categorie}`, 'FAIL',
        `${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    return false;
}

async function expectError(token, ticketId, step, payload, label, expectedFragment) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`, {
        token, body: { step, payload },
    });
    if (r.status === 400) {
        const msg = (r.body?.message || r.body?.detail || JSON.stringify(r.body) || '').toLowerCase();
        const ok = !expectedFragment || msg.includes(expectedFragment);
        record('VALIDATION', label, ok ? 'PASS' : 'FAIL',
            ok ? `400 "${msg.slice(0, 80)}"` : `400 mais sans fragment "${expectedFragment}" (msg="${msg.slice(0, 80)}")`);
        return ok;
    }
    record('VALIDATION', label, 'FAIL', `expected 400, got ${r.status}`);
    return false;
}

async function main() {
    await loadEnv();
    section('Setup');
    const ctx = await seed();
    if (!ctx) return process.exit(1);

    section('IMPORT — workflow complet (11 etapes)');
    const ticket = await createImportTicket(ctx.token, 'ATLAS IMPORT SARL');
    if (!ticket) return process.exit(1);
    const dossierId = ticket.dossierId;
    const wf = await startWf(ctx.token, ticket.id);
    if (!wf) return process.exit(1);
    record('IMPORT', 'workflow IMPORT compte 11 etapes',
        wf.totalSteps === 11 ? 'PASS' : 'FAIL', `totalSteps=${wf.totalSteps}`);

    // Verification dossier ACTIVE immediatement (importStub)
    const dRes = await http('GET', '/api/v1/dataroom/dossiers', { token: ctx.token });
    const dossierBefore = (dRes.body || []).find((d) => d.id === dossierId);
    record('IMPORT', 'dossier statut=ACTIVE des creation',
        dossierBefore?.statut === 'ACTIVE' ? 'PASS' : 'FAIL',
        `statut=${dossierBefore?.statut}`);

    const currentYear = new Date().getFullYear();
    const year = currentYear - 1;

    // ---- step 1 : Denomination — erreurs ----
    await expectError(ctx.token, ticket.id, 1,
        { denomination: 'X', ice: '123', rcNumero: '1', ifNumero: '1', formeJuridique: 'SARL' },
        'step1 ICE 3 chiffres rejete (RG-C13)', 'ice');
    await expectError(ctx.token, ticket.id, 1,
        { denomination: 'X', ice: '001234567890123', ifNumero: '12345678', formeJuridique: 'SARL' },
        'step1 RC manquant rejete', 'rc');

    // ---- step 1 : Denomination — succes ----
    const step1Payload = {
        denomination: 'ATLAS IMPORT SARL',
        ice: '001234567890123',
        rcNumero: '987654',
        ifNumero: '12345678',
        formeJuridique: 'SARL',
    };
    if (!await executeStep(ctx.token, ticket.id, 1, step1Payload, 'step1 OK (denomination + immatriculation)')) {
        return process.exit(1);
    }

    // ---- step 2 : Siege ----
    const step2Payload = {
        adresse: '12 rue des Cedres, Maarif',
        province: 'Casablanca-Anfa',
        commune: 'Casablanca',
        codePostal: '20000',
    };
    if (!await executeStep(ctx.token, ticket.id, 2, step2Payload, 'step2 OK (siege social)')) {
        return process.exit(1);
    }

    // ---- step 3 : Capital — erreur (parts manquantes) puis succes ----
    await expectError(ctx.token, ticket.id, 3,
        { apportNumeraire: 300000 },
        'step3 nombre de parts manquant rejete', 'parts');
    const step3Payload = { apportNumeraire: 300000, nombreParts: 3000 };
    if (!await executeStep(ctx.token, ticket.id, 3, step3Payload, 'step3 OK (capital + parts, sans depot/25%)')) {
        return process.exit(1);
    }

    // ---- step 4 : Activite ----
    const step4Payload = {
        description: 'Conseil juridique et fiscal aux entreprises',
        dateDebutExercice: `${year}-01-01`,
    };
    if (!await executeStep(ctx.token, ticket.id, 4, step4Payload, 'step4 OK (objet social + debut exercice)')) {
        return process.exit(1);
    }

    // ---- step 5 : Dirigeants + gerance ----
    const step5Payload = {
        dirigeants: [
            { typePersonne: 'PHYSIQUE', nom: 'BENATIK', prenom: 'Oussama', cinNumero: 'BK123456' },
        ],
        gerance: { dureeMandat: 'ILLIMITEE', remunerationMode: 'GRATUIT' },
    };
    if (!await executeStep(ctx.token, ticket.id, 5, step5Payload, 'step5 OK (dirigeants + gouvernance)')) {
        return process.exit(1);
    }

    // ---- step 6 : Associes — erreur (somme parts != total) puis succes ----
    await expectError(ctx.token, ticket.id, 6,
        {
            associes: [
                { typePersonne: 'PHYSIQUE', nom: 'BENATIK', prenom: 'Oussama', cin: 'BK123456', nombreParts: 1000 },
            ],
            formeJuridique: 'SARL',
        },
        'step6 somme parts != total step3 rejete', 'somme parts');
    const step6Payload = {
        associes: [
            { typePersonne: 'PHYSIQUE', nom: 'BENATIK', prenom: 'Oussama', cin: 'BK123456', nombreParts: 1800, pourcentageDetention: 60 },
            { typePersonne: 'PHYSIQUE', nom: 'ALAOUI', prenom: 'Salma', cin: 'AL654321', nombreParts: 1200, pourcentageDetention: 40 },
        ],
        formeJuridique: 'SARL',
    };
    if (!await executeStep(ctx.token, ticket.id, 6, step6Payload, 'step6 OK (associes, somme parts == total)')) {
        return process.exit(1);
    }

    // ---- step 7 : Upload JURIDIQUE (depot direct Data Room + persiste la liste typee) ----
    const statutsPdf = tinyPdf('Statuts ATLAS SARL');
    const rcPdf = tinyPdf('Extrait RC ATLAS');
    await uploadJuridique(ctx.token, dossierId, 'STATUTS', 'statuts.pdf', statutsPdf);
    await uploadJuridique(ctx.token, dossierId, 'RC', 'rc.pdf', rcPdf);
    await expectError(ctx.token, ticket.id, 7,
        { documents: [] },
        'step7 sans document juridique rejete', 'document juridique');
    const step7Payload = {
        documents: [
            { type: 'STATUTS', filename: 'statuts.pdf', sizeBytes: 1024, uploaded: true },
            { type: 'RC', filename: 'rc.pdf', sizeBytes: 1024, uploaded: true },
        ],
    };
    if (!await executeStep(ctx.token, ticket.id, 7, step7Payload, 'step7 OK (documents juridiques typed)')) {
        return process.exit(1);
    }

    // ---- step 8 : Upload COMPTABLE (optionnel) ----
    const bilanPdf = tinyPdf(`Bilan ATLAS ${year}`);
    await uploadComptable(ctx.token, dossierId, year, 'VENTES', `bilan_${year}.pdf`, bilanPdf);
    const step8Payload = {
        documentsFinanciers: [
            { id: '1', annee: year, categorie: 'VENTES', label: 'Bilan annuel', filename: `bilan_${year}.pdf`, sizeBytes: 1024, uploaded: true },
        ],
    };
    if (!await executeStep(ctx.token, ticket.id, 8, step8Payload, 'step8 OK (historique comptable, optionnel)')) {
        return process.exit(1);
    }

    // ---- step 9 : Upload FISCAL (optionnel) ----
    if (!await executeStep(ctx.token, ticket.id, 9, { documentsFiscaux: [] }, 'step9 OK (historique fiscal, optionnel)')) {
        return process.exit(1);
    }

    // ---- step 10 : Suivi (regime TVA + annee exercice + annees anterieures) ----
    const step10Payload = {
        regimeTvaMensuel: true,
        anneeExercice: currentYear,
        anneesAnterieuresSelectionnees: [year],
    };
    const step10 = await executeStep(ctx.token, ticket.id, 10, step10Payload, 'step10 OK (suivi: regime TVA + annee exercice)');
    if (!step10) return process.exit(1);
    const suiviPersist = step10.progress?.data?.step10?.suivi;
    record('IMPORT', 'suivi : annees anterieures filtrees (< annee exercice)',
        Array.isArray(suiviPersist?.anneesAnterieuresSelectionnees)
            && suiviPersist.anneesAnterieuresSelectionnees.length === 1 ? 'PASS' : 'FAIL',
        `anterieures=${JSON.stringify(suiviPersist?.anneesAnterieuresSelectionnees)}`);

    // ---- step 11 : Synthese -> fiche juridique consolidee ----
    await expectError(ctx.token, ticket.id, 11,
        { validated: false },
        'step11 sans validation finale rejete', 'validated');
    const step11 = await executeStep(ctx.token, ticket.id, 11, { validated: true },
        'step11 OK (synthese + fiche consolidee)');
    if (!step11) return process.exit(1);

    // ====================================================================
    //  Consolidation fiche structuree (mirroir des assertions de
    //  ImportWorkflowTest.step11SyntheseComplete) — la Synthese assemble la
    //  fiche juridique COMPLETE depuis les step bags 1-6 + Suivi (10).
    // ====================================================================
    const synthese = step11.progress?.data?.step11?.synthese;
    const fiche = synthese?.ficheJuridique;
    record('IMPORT', 'synthese : importComplete=true',
        synthese?.importComplete === true ? 'PASS' : 'FAIL',
        `value=${synthese?.importComplete}`);
    record('IMPORT', 'fiche : denomination / raison sociale',
        fiche?.raisonSociale === 'ATLAS IMPORT SARL' && fiche?.denomination === 'ATLAS IMPORT SARL'
            ? 'PASS' : 'FAIL',
        `rs=${fiche?.raisonSociale}`);
    record('IMPORT', 'fiche : ICE normalise (15 chiffres)',
        fiche?.ice === '001234567890123' ? 'PASS' : 'FAIL', `ice=${fiche?.ice}`);
    record('IMPORT', 'fiche : RC (societe immatriculee)',
        fiche?.rcNumero === '987654' ? 'PASS' : 'FAIL', `rc=${fiche?.rcNumero}`);
    record('IMPORT', 'fiche : IF / identifiant fiscal',
        String(fiche?.ifNumero) === '12345678' ? 'PASS' : 'FAIL', `if=${fiche?.ifNumero}`);
    record('IMPORT', 'fiche : capital social consolide',
        Number(fiche?.capitalSocial) === 300000 ? 'PASS' : 'FAIL', `capital=${fiche?.capitalSocial}`);
    record('IMPORT', 'fiche : nombre de parts',
        Number(fiche?.nombreParts) === 3000 ? 'PASS' : 'FAIL', `parts=${fiche?.nombreParts}`);
    record('IMPORT', 'fiche : objet social',
        fiche?.objetSocial === 'Conseil juridique et fiscal aux entreprises' ? 'PASS' : 'FAIL',
        `objet=${fiche?.objetSocial}`);
    record('IMPORT', 'fiche : siege (adresse + ville)',
        fiche?.siegeAdresse === '12 rue des Cedres, Maarif' && fiche?.siegeVille === 'Casablanca'
            ? 'PASS' : 'FAIL', `siege=${fiche?.siegeAdresse} / ${fiche?.siegeVille}`);
    record('IMPORT', 'fiche : dirigeants + alias gerants',
        Array.isArray(fiche?.dirigeants) && fiche.dirigeants.length === 1
            && Array.isArray(fiche?.gerants) && fiche.gerants.length === 1 ? 'PASS' : 'FAIL',
        `dirigeants=${fiche?.dirigeants?.length} gerants=${fiche?.gerants?.length}`);
    record('IMPORT', 'fiche : associes consolides (2)',
        Array.isArray(fiche?.associes) && fiche.associes.length === 2 ? 'PASS' : 'FAIL',
        `nb=${fiche?.associes?.length}`);
    record('IMPORT', 'synthese : suivi reporte (annee exercice)',
        Number(synthese?.suivi?.anneeExercice) === currentYear ? 'PASS' : 'FAIL',
        `annee=${synthese?.suivi?.anneeExercice}`);

    // ---- post-completion : fiche_structuree (source=IMPORT) propagee au dossier ----
    // applyImportConsolidation ecrit les scalaires + fiche_structuree sur le
    // dossier auto-cree. La liste /dossiers expose ICE / raison sociale / statut.
    const dAfter = await http('GET', '/api/v1/dataroom/dossiers', { token: ctx.token });
    const dossierAfter = (dAfter.body || []).find((d) => d.id === dossierId);
    record('IMPORT', 'dossier propage ICE (consolidation)',
        dossierAfter?.ice === '001234567890123' ? 'PASS' : 'FAIL',
        `ice=${dossierAfter?.ice}`);
    record('IMPORT', 'dossier propage raisonSociale (consolidation)',
        dossierAfter?.raisonSociale === 'ATLAS IMPORT SARL' ? 'PASS' : 'FAIL',
        `rs=${dossierAfter?.raisonSociale}`);
    record('IMPORT', 'dossier reste ACTIVE post-completion',
        dossierAfter?.statut === 'ACTIVE' ? 'PASS' : 'FAIL',
        `statut=${dossierAfter?.statut}`);

    // ---- dataroom contient bien les documents ----
    const jur = await http('GET', `/api/v1/dataroom/dossiers/${dossierId}/juridique`, { token: ctx.token });
    const nbJur = (jur.body?.documentsEnVigueur || []).length;
    record('IMPORT', 'dataroom juridique peuplee',
        nbJur >= 2 ? 'PASS' : 'FAIL',
        `nb=${nbJur}`);
    const compt = await http('GET',
        `/api/v1/dataroom/dossiers/${dossierId}/comptable/documents?annee=${year}&categorie=VENTES`,
        { token: ctx.token });
    const nbComp = Array.isArray(compt.body) ? compt.body.length : 0;
    record('IMPORT', 'dataroom comptable peuplee',
        nbComp >= 1 ? 'PASS' : 'FAIL',
        `nb=${nbComp}`);

    // ---- ticket cloture ----
    const t = await http('GET', `/api/v1/tickets/${ticket.id}`, { token: ctx.token });
    record('IMPORT', 'ticket auto-transitionne en CLOTURE',
        t.body?.statut === 'CLOTURE' ? 'PASS' : 'FAIL',
        `statut=${t.body?.statut}`);

    // ---- SUIVI : ouverture exercice (orchestration front) -> echeances generees ----
    // Le front (ImportWorkflowPage.finalizeImport) ouvre l'exercice a la finalisation.
    // On reproduit l'appel ici pour valider la chaine echeances de bout en bout.
    section('IMPORT — exercice + echeances (suivi)');
    // autoCreateComptable=true : a la finalisation import/creation, l'ancre
    // comptable de l'annee est creee a la volee (RG-DF03) — mirroir exact de
    // ImportWorkflowPage.finalizeImport (dataroomService.openExercice).
    const exo = await http('POST', `/api/v1/dataroom/dossiers/${dossierId}/exercices`,
        { token: ctx.token, body: { annee: currentYear, regimeTvaMensuel: true, autoCreateComptable: true } });
    record('IMPORT', 'ouverture exercice courant',
        (exo.ok || exo.status === 409) ? 'PASS' : 'FAIL',
        `status=${exo.status}`);
    // Idempotence : 2e ouverture -> rejet EXERCICE_EXISTS (RG-DF20), ne doit pas
    // planter. Le handler global mappe la BusinessException en 400 (code metier
    // EXERCICE_EXISTS) ; on accepte aussi 409/200 selon l'evolution du mapping.
    const exo2 = await http('POST', `/api/v1/dataroom/dossiers/${dossierId}/exercices`,
        { token: ctx.token, body: { annee: currentYear, regimeTvaMensuel: true, autoCreateComptable: true } });
    const exo2Idempotent = exo2.ok || exo2.status === 409
        || (exo2.status === 400 && exo2.body?.code === 'EXERCICE_EXISTS');
    record('IMPORT', 'ouverture exercice idempotente (EXERCICE_EXISTS)',
        exo2Idempotent ? 'PASS' : 'FAIL',
        `status=${exo2.status} code=${exo2.body?.code}`);
    const ech = await http('GET', `/api/v1/dataroom/dossiers/${dossierId}/echeances`,
        { token: ctx.token });
    const nbEch = Array.isArray(ech.body) ? ech.body.length : 0;
    record('IMPORT', 'echeances generees (calendrier non vide)',
        nbEch > 0 ? 'PASS' : 'FAIL',
        `nb=${nbEch}`);

    // ---- summary ----
    section('Summary');
    const pass = results.filter((r) => r.status === 'PASS').length;
    const fail = results.filter((r) => r.status === 'FAIL').length;
    const skip = results.filter((r) => r.status === 'SKIP').length;
    console.log(`\n${C.bold}${pass} PASS · ${fail} FAIL · ${skip} SKIP${C.reset}\n`);
    process.exit(fail);
}

main().catch((e) => {
    console.error(`${C.fail}Fatal: ${e.message}${C.reset}`);
    process.exit(1);
});
