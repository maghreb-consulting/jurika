#!/usr/bin/env node
/**
 * Remplit la Data Room de demonstration (JUR-DEMO2) et le corpus du chatbot,
 * en passant UNIQUEMENT par les API reelles de la plateforme.
 *
 * Pourquoi : scripts/seed-demo.mjs cree le workspace, les comptes, 10 dossiers
 * et 10 tickets, mais laisse la Data Room VIDE (0 document) et le corpus RAG
 * vide. Une video de demonstration sur des ecrans vides n'a aucune valeur.
 *
 * Ce que fait ce script, sur le dossier « SARL Atlas Conseil » :
 *   1. remet a blanc les identifiants post-immatriculation (RC / IF / TP / CNSS
 *      / adresse) pour que le pre-vol de la Fiche client ait quelque chose a
 *      signaler pendant la demo ;
 *   2. depose 5 documents juridiques types (statuts, PV AGO, PV AGE, annonce
 *      legale, bail) via POST /juridique/upload ;
 *   3. cree une 2e VERSION des statuts via POST /documents/{id}/versions, avec
 *      motif — c'est ce qui alimente le menu « versions » de la Data Room ;
 *   4. ouvre l'exercice fiscal 2025 et depose 2 documents fiscaux
 *      (etat d'imposition + declaration TVA) ;
 *   5. depose 1 document comptable (extrait de grand livre) ;
 *   6. ingere les statuts refondus dans le corpus du chatbot
 *      (POST /api/v1/chatbot/sources) pour que le RAG ait de quoi repondre.
 *
 * Tout le contenu est FICTIF (voir build-demo-pdfs.py). Idempotent : relancer
 * ne cree pas de doublons (les documents deja presents sont sautes).
 *
 * Pre-requis : stack UP, `node scripts/seed-demo.mjs` deja passe, puis
 *              `node scripts/demo-video/enroll-totp.mjs`, puis
 *              `python scripts/demo-video/build-demo-pdfs.py`.
 *
 * Usage : node scripts/demo-video/seed-demo-content.mjs
 */
import { readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { totp, msLeftInWindow } from './totp.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ASSETS = join(HERE, 'assets');
const CORPUS = join(HERE, 'corpus');
const PROJECT_ROOT = join(HERE, '..', '..');

const arg = (n, d) => {
    const m = process.argv.find(a => a.startsWith(`--${n}=`));
    return m ? m.split('=').slice(1).join('=') : d;
};

const API = arg('api', 'http://localhost:8080');
const WORKSPACE = arg('workspace', 'JUR-DEMO2');
const EMAIL = arg('email', 'employe1@demo.jurika.ma');
const PASSWORD = arg('password', 'Demo@2026');
const DOSSIER = arg('dossier', 'SARL Atlas Conseil');

// ─── HTTP ───────────────────────────────────────────────────────────────
async function call(path, { method = 'GET', token, json, form } = {}) {
    const headers = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    if (json) headers['Content-Type'] = 'application/json';
    const res = await fetch(`${API}${path}`, {
        method,
        headers,
        body: json ? JSON.stringify(json) : form,
    });
    const text = await res.text();
    let body;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${JSON.stringify(body).slice(0, 300)}`);
    return body;
}

async function pdfPart(nom) {
    const bytes = await readFile(join(ASSETS, nom));
    return new File([bytes], nom, { type: 'application/pdf' });
}

// ─── Connexion (mot de passe + TOTP reel) ───────────────────────────────
async function login() {
    const secrets = JSON.parse(await readFile(join(HERE, 'demo-secrets.json'), 'utf8'));
    const entry = secrets[`${WORKSPACE}:${EMAIL}`];
    if (!entry) throw new Error(`Pas de secret TOTP pour ${WORKSPACE}:${EMAIL} — lancer enroll-totp.mjs`);

    const r1 = await call('/api/v1/auth/login', {
        json: { workspaceCode: WORKSPACE, email: EMAIL, password: PASSWORD },
        method: 'POST',
    });
    if (!r1.requires2fa) return r1.accessToken;

    if (msLeftInWindow() < 3000) await new Promise(r => setTimeout(r, msLeftInWindow() + 500));
    const r2 = await call('/api/v1/auth/verify-2fa', {
        method: 'POST',
        // Code en CHAINE : un code commencant par zero serait tronque en nombre.
        json: { userId: r1.userId, workspaceId: r1.workspaceId, code: totp(entry.secret) },
    });
    return r2.accessToken;
}

/** Jeton du compte CLIENT de demonstration (2FA enrolee par enroll-totp). */
async function loginClient() {
    const secrets = JSON.parse(await readFile(join(HERE, 'demo-secrets.json'), 'utf8'));
    const email = 'client@demo.jurika.ma';
    const entry = secrets[`${WORKSPACE}:${email}`];
    if (!entry) throw new Error('pas de secret TOTP pour le compte client');
    const r1 = await call('/api/v1/auth/login', {
        method: 'POST', json: { workspaceCode: WORKSPACE, email, password: PASSWORD },
    });
    if (!r1.requires2fa) return r1.accessToken;
    if (msLeftInWindow() < 3000) await new Promise(r => setTimeout(r, msLeftInWindow() + 500));
    const r2 = await call('/api/v1/auth/verify-2fa', {
        method: 'POST',
        json: { userId: r1.userId, workspaceId: r1.workspaceId, code: totp(entry.secret) },
    });
    return r2.accessToken;
}

// ─── Remise a blanc des identifiants (acces direct base) ────────────────
async function resetIdentifiants(dossierId) {
    const { default: pg } = await import('pg');
    const raw = await readFile(join(PROJECT_ROOT, '.env'), 'utf8');
    const env = Object.fromEntries(raw.split(/\r?\n/)
        .map(l => l.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/))
        .filter(Boolean)
        .map(m => [m[1], m[2].replace(/^["']|["']$/g, '')]));

    const client = new pg.Client({
        host: 'localhost', port: 5432,
        user: env.POSTGRES_USER, password: env.POSTGRES_PASSWORD,
        database: env.POSTGRES_DB,
    });
    await client.connect();
    await client.query(`
        UPDATE entreprise_dossiers
           SET rc_numero = NULL, rc_tribunal = NULL, identifiant_fiscal = NULL,
               taxe_professionnelle = NULL, cnss = NULL, adresse_siege = NULL
         WHERE id = $1`, [dossierId]);
    console.log('  ✓ identifiants post-immatriculation remis a blanc (pour la demo du pre-vol)');

    // Les conversations du workspace de demo sont aussi remises a zero : chaque
    // captation envoie un message, et au bout de trois prises la messagerie
    // affichait trois fois la meme phrase — ce qui se voit a l'ecran.
    const conv = await client.query(`
        DELETE FROM chat_conversations
         WHERE workspace_id = (SELECT workspace_id FROM entreprise_dossiers WHERE id = $1)
     RETURNING id`, [dossierId]);
    if (conv.rowCount) console.log(`  ✓ ${conv.rowCount} conversation(s) de demonstration purgee(s)`);

    // Le panneau « Activite du dossier » lit audit_log. Chaque captation y laisse
    // « Fiche client generated » et « Dossier identifiants updated » : au bout de
    // huit prises, le panneau n'affiche plus que la trace de mes essais, ce qui
    // se voit a l'ecran. On ne garde que l'historique anterieur aux repetitions.
    const audit = await client.query(`
        DELETE FROM audit_log
         WHERE entity_id = $1
           AND action IN ('FICHE_CLIENT_GENERATED', 'DOSSIER_IDENTIFIANTS_UPDATED')
     RETURNING id`, [dossierId]).catch(() => ({ rowCount: 0 }));
    if (audit.rowCount) console.log(`  ✓ ${audit.rowCount} trace(s) d'essai purgee(s) de l'activite du dossier`);

    await client.end();
}

// ─── Programme ──────────────────────────────────────────────────────────
const JURIDIQUE = [
    { fichier: 'Statuts_Atlas_Conseil_v1.pdf', type: 'STATUTS', titre: 'Statuts constitutifs' },
    { fichier: 'PV_AGO_Atlas_Conseil_2025.pdf', type: 'PV_AGO', titre: "PV d'assemblee generale ordinaire — exercice 2025" },
    { fichier: 'PV_AGE_Augmentation_Capital.pdf', type: 'PV_AGE', titre: "PV d'AGE — augmentation de capital" },
    { fichier: 'Annonce_Legale_Augmentation_Capital.pdf', type: 'ANNONCE_JAL', titre: 'Annonce legale — augmentation de capital' },
    { fichier: 'Contrat_Bail_Siege_Social.pdf', type: 'CONTRAT_BAIL', titre: 'Contrat de bail — siege social' },
];

async function main() {
    console.log(`> connexion ${EMAIL} @ ${WORKSPACE}`);
    const token = await login();

    const dossiers = await call('/api/v1/dataroom/dossiers', { token });
    const cible = dossiers.find(d => d.raisonSociale === DOSSIER);
    if (!cible) throw new Error(`Dossier « ${DOSSIER} » introuvable dans ${WORKSPACE}`);
    console.log(`> dossier cible : ${cible.raisonSociale} (${cible.id})`);

    await resetIdentifiants(cible.id);

    // 1. Documents juridiques ------------------------------------------------
    const vue = await call(`/api/v1/dataroom/dossiers/${cible.id}/juridique`, { token });
    const dejaLa = new Set((vue.documentsEnVigueur ?? []).map(d => d.title));

    let statutsId = (vue.documentsEnVigueur ?? []).find(d => d.documentType === 'STATUTS')?.id ?? null;

    for (const doc of JURIDIQUE) {
        if (dejaLa.has(doc.titre)) { console.log(`  = deja present : ${doc.titre}`); continue; }
        const form = new FormData();
        form.append('file', await pdfPart(doc.fichier));
        form.append('documentType', doc.type);
        form.append('title', doc.titre);
        const created = await call(`/api/v1/dataroom/dossiers/${cible.id}/juridique/upload`,
            { method: 'POST', token, form });
        if (doc.type === 'STATUTS') statutsId = created.id;
        console.log(`  ✓ ${doc.type.padEnd(13)} ${doc.titre}`);
    }

    // 2. Deuxieme version des statuts ---------------------------------------
    if (statutsId) {
        const versions = await call(`/api/v1/dataroom/documents/${statutsId}/versions`, { token });
        if (versions.length <= 1) {
            const form = new FormData();
            form.append('file', await pdfPart('Statuts_Atlas_Conseil_v2.pdf'));
            form.append('motif', "Statuts refondus apres l'augmentation de capital du 12 mars 2026");
            await call(`/api/v1/dataroom/documents/${statutsId}/versions`, { method: 'POST', token, form });
            console.log('  ✓ statuts — version 2 creee (v1 basculee en historique)');
        } else {
            console.log(`  = statuts : ${versions.length} versions deja presentes`);
        }
    }

    // 3. Exercice fiscal + documents fiscaux --------------------------------
    let exercices = await call(`/api/v1/dataroom/dossiers/${cible.id}/exercices`, { token });
    let ex2025 = exercices.find(e => Number(e.annee) === 2025);
    if (!ex2025) {
        ex2025 = await call(`/api/v1/dataroom/dossiers/${cible.id}/exercices`, {
            method: 'POST', token,
            json: { annee: 2025, dateDebut: '2025-01-01', dateFin: '2025-12-31',
                    regimeTvaMensuel: false, autoCreateComptable: true },
        });
        console.log('  ✓ exercice fiscal 2025 ouvert (echeances DGI generees)');
    } else {
        console.log('  = exercice fiscal 2025 deja ouvert');
    }

    const fiscaux = await call(`/api/v1/dataroom/dossiers/${cible.id}/fiscal/documents?exerciceId=${ex2025.id}`, { token })
        .catch(() => []);
    const titresFiscaux = new Set((Array.isArray(fiscaux) ? fiscaux : fiscaux?.content ?? []).map(d => d.title));

    const FISCAUX = [
        { fichier: 'Etat_Imposition_2025.pdf', categorie: 'ATTESTATIONS', sous: 'ETAT_IMPOSITION',
          titre: "Etat d'imposition — exercice 2025" },
        { fichier: 'Declaration_TVA_T4_2025.pdf', categorie: 'TVA', sous: 'DECLARATION_TRIMESTRIELLE',
          titre: 'Declaration TVA — 4e trimestre 2025' },
    ];
    for (const f of FISCAUX) {
        if (titresFiscaux.has(f.titre)) { console.log(`  = deja present (fiscal) : ${f.titre}`); continue; }
        const form = new FormData();
        form.append('file', await pdfPart(f.fichier));
        form.append('exerciceId', ex2025.id);
        form.append('categorie', f.categorie);
        form.append('sousClassification', f.sous);
        form.append('title', f.titre);
        await call(`/api/v1/dataroom/dossiers/${cible.id}/fiscal/upload`, { method: 'POST', token, form });
        console.log(`  ✓ fiscal ${f.categorie}/${f.sous} — ${f.titre}`);
    }

    // 4. Document comptable --------------------------------------------------
    // `categorie` est OBLIGATOIRE sur cet endpoint (sinon 400) : l'omettre faisait
    // echouer le controle d'idempotence et re-deposait un doublon a chaque passage.
    const comptables = await call(
        `/api/v1/dataroom/dossiers/${cible.id}/comptable/documents?annee=2025&categorie=AUTRE`,
        { token }).catch(() => []);
    const listeComptable = Array.isArray(comptables) ? comptables : comptables?.content ?? [];
    // L'onglet Comptable s'ouvre sur l'annee COURANTE : n'alimenter que 2025
    // laissait six categories a zero a l'ecran. On depose donc aussi une piece
    // sur l'exercice en cours.
    const anneeCourante = String(new Date().getFullYear());
    const courants = await call(
        `/api/v1/dataroom/dossiers/${cible.id}/comptable/documents?annee=${anneeCourante}&categorie=ACHATS`,
        { token }).catch(() => []);
    if (!(Array.isArray(courants) ? courants : []).some(d => d.title?.startsWith('Factures achats'))) {
        const form = new FormData();
        form.append('file', await pdfPart('Grand_Livre_2025.pdf'));
        form.append('annee', anneeCourante);
        form.append('categorie', 'ACHATS');
        form.append('title', `Factures achats — ${anneeCourante}`);
        await call(`/api/v1/dataroom/dossiers/${cible.id}/comptable/upload`, { method: 'POST', token, form });
        console.log(`  ✓ comptable ACHATS — Factures achats — ${anneeCourante}`);
    } else {
        console.log(`  = deja present (comptable ${anneeCourante})`);
    }

    if (!listeComptable.some(d => d.title === 'Grand livre — extrait 2025')) {
        const form = new FormData();
        form.append('file', await pdfPart('Grand_Livre_2025.pdf'));
        form.append('annee', '2025');
        form.append('categorie', 'AUTRE');
        form.append('title', 'Grand livre — extrait 2025');
        await call(`/api/v1/dataroom/dossiers/${cible.id}/comptable/upload`, { method: 'POST', token, form });
        console.log('  ✓ comptable AUTRE — Grand livre — extrait 2025');
    } else {
        console.log('  = deja present (comptable) : Grand livre — extrait 2025');
    }

    // 5. Corpus du chatbot ---------------------------------------------------
    // On ingere la version TEXTE (un article par paragraphe) et non le PDF :
    // TextChunker decoupe sur les lignes vides et coupe en dur a 1200 caracteres
    // sinon (pas de recouvrement). Extrait d'un PDF, le texte arrive en un seul
    // bloc et l'article 7 se retrouve coupe au milieu d'une phrase — le chatbot
    // repond alors en signalant que son contexte est tronque.
    // Deux sources, pour deux usages :
    //  - les statuts du dossier : questions portant sur CE client ;
    //  - un memo de droit des societes : questions generales, dont les QUATRE
    //    suggestions affichees par la page ChatBot (creation de SARL, capital
    //    minimum, dissolution, succursale etrangere). Sans lui, une suggestion
    //    proposee par le produit ne retrouve aucun passage.
    const SOURCES = [
        { fichier: join(ASSETS, 'Statuts_Atlas_Conseil_v2_corpus.txt'),
          titre: 'Statuts refondus - SARL Atlas Conseil', reconnu: 'Statuts' },
        { fichier: join(CORPUS, 'Memo_Droit_Societes_Maroc.txt'),
          titre: 'Memo de droit des societes au Maroc', reconnu: 'Memo' },
    ];

    // `--corpus=refaire` purge d'abord : indispensable apres un changement de
    // decoupage ou de modele d'embedding, sinon les anciens passages (sans
    // recouvrement, sans vecteur) restent en base et le RAG ne bouge pas.
    if (arg('corpus', '') === 'refaire') {
        const avant = await call('/api/v1/chatbot/sources', { token }).catch(() => []);
        for (const s of (Array.isArray(avant) ? avant : [])) {
            await call(`/api/v1/chatbot/sources/${s.doc_id}`, { method: 'DELETE', token }).catch(() => {});
        }
        console.log(`  ✓ corpus chatbot purge (${Array.isArray(avant) ? avant.length : 0} source(s))`);
    }

    const sources = await call('/api/v1/chatbot/sources', { token }).catch(() => []);
    const listeSources = Array.isArray(sources) ? sources : sources?.sources ?? [];
    for (const src of SOURCES) {
        if (listeSources.some(s => (s.source ?? s.title ?? '').includes(src.reconnu))) {
            console.log(`  = corpus chatbot : « ${src.titre} » deja ingere`);
            continue;
        }
        const form = new FormData();
        form.append('text', await readFile(src.fichier, 'utf8'));
        form.append('title', src.titre);
        const r = await call('/api/v1/chatbot/sources', { method: 'POST', token, form });
        console.log(`  ✓ corpus chatbot — « ${src.titre} » (${r?.chunks ?? '?'} passages)`);
    }

    // 6. Demandes du client et requetes au client ---------------------------
    // Les deux sens de l'echange cabinet <-> client vivent dans la meme table,
    // distingues par leur direction. Sans eux, l'onglet « Demandes » du dossier
    // est vide et la video n'a rien a montrer de cette partie du produit.
    const echanges = await call(`/api/v1/dataroom/dossiers/${cible.id}/demandes`, { token })
        .catch(() => []);
    if ((Array.isArray(echanges) ? echanges : []).length === 0) {
        const REQUETES = [
            { sujet: 'Attestation bancaire de blocage du capital',
              description: "Merci de nous transmettre l'attestation de blocage delivree par votre banque pour la liberation du capital.",
              typeRequete: 'PIECE' },
            { sujet: "Confirmation de l'adresse du siege",
              description: "Pouvez-vous confirmer que le siege reste au 45, boulevard Zerktouni avant le depot de l'annonce ?",
              typeRequete: 'INFO' },
        ];
        for (const r of REQUETES) {
            await call('/api/v1/dataroom/requetes', {
                method: 'POST', token, json: { ...r, dossierId: cible.id },
            });
            console.log(`  ✓ requete au client — ${r.sujet}`);
        }

        const jetonClient = await loginClient().catch(e => {
            console.log(`  = demandes client sautees (${e.message})`);
            return null;
        });
        if (jetonClient) {
            const DEMANDES = [
                { sujet: 'Copie des statuts a jour',
                  description: 'Bonjour, pourriez-vous me transmettre la derniere version des statuts pour ma banque ?' },
                { sujet: "Ou en est l'assemblee generale ordinaire ?",
                  description: "Je souhaite connaitre la date prevue pour l'approbation des comptes 2025." },
            ];
            for (const d of DEMANDES) {
                await call('/api/v1/dataroom/demandes', {
                    method: 'POST', token: jetonClient, json: { ...d, dossierId: cible.id },
                });
                console.log(`  ✓ demande client — ${d.sujet}`);
            }
        }
    } else {
        console.log(`  = ${echanges.length} demande(s)/requete(s) deja presentes`);
    }

    console.log('\n✅ Contenu de demonstration en place.');
}

main().catch(e => { console.error('\n❌', e.message); process.exit(1); });
