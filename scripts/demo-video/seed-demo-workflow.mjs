#!/usr/bin/env node
/**
 * Prepare un workflow de CREATION SARL au 7e ecran, pret a etre filme.
 *
 * Pourquoi : le workspace de demonstration ne contient AUCUN workflow demarre
 * (`workflow_progress` vide pour JUR-DEMO2) — les 10 tickets ont ete poses
 * directement en base avec leur statut, sans parcours derriere. Filmer « le
 * workflow etape par etape » supposait donc de remplir neuf formulaires a
 * l'ecran, ce qui prendrait a soi seul plus que la duree cible de la video.
 *
 * On remplit donc les six premiers ecrans par l'API reelle
 * (`POST /api/v1/workflows/{ticketId}/save`), exactement comme le fait le
 * front a chaque « Suivant ». La camera parcourt ensuite ces ecrans — les
 * donnees affichees sont bien celles de la base — puis declenche la
 * GENERATION a l'ecran 7, qui est le moment interessant et qui, lui, est joue
 * en direct.
 *
 * Toutes les donnees sont FICTIVES et coherentes avec le ticket de demo
 * « Constitution Maroc Telecom Services » (T-DEMO-003).
 *
 * Usage : node scripts/demo-video/seed-demo-workflow.mjs
 *         node scripts/demo-video/seed-demo-workflow.mjs --ticket=T-DEMO-003
 */
import { readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { totp, msLeftInWindow } from './totp.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));

const arg = (n, d) => {
    const m = process.argv.find(a => a.startsWith(`--${n}=`));
    return m ? m.split('=').slice(1).join('=') : d;
};

const API = arg('api', 'http://localhost:8080');
const WORKSPACE = arg('workspace', 'JUR-DEMO2');
const EMAIL = arg('email', 'employe1@demo.jurika.ma');
const PASSWORD = arg('password', 'Demo@2026');
const REFERENCE = arg('ticket', 'T-DEMO-003');
/** Ecran sur lequel on laisse le parcours (7 = generation, jouee en direct). */
const ETAPE = Number(arg('etape', '7'));

async function call(path, { method = 'GET', token, json } = {}) {
    const res = await fetch(`${API}${path}`, {
        method,
        headers: {
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...(json ? { 'Content-Type': 'application/json' } : {}),
        },
        body: json ? JSON.stringify(json) : undefined,
    });
    const text = await res.text();
    let body; try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${JSON.stringify(body).slice(0, 300)}`);
    return body;
}

async function login() {
    const secrets = JSON.parse(await readFile(join(HERE, 'demo-secrets.json'), 'utf8'));
    const cle = secrets[`${WORKSPACE}:${EMAIL}`]?.secret;
    const r1 = await call('/api/v1/auth/login', {
        method: 'POST',
        json: { workspaceCode: WORKSPACE, email: EMAIL, password: PASSWORD },
    });
    if (!r1.requires2fa) return r1.accessToken;
    if (!cle) throw new Error(`2FA active mais pas de secret pour ${WORKSPACE}:${EMAIL}`);
    if (msLeftInWindow() < 4000) await new Promise(r => setTimeout(r, msLeftInWindow() + 400));
    const r2 = await call('/api/v1/auth/verify-2fa', {
        method: 'POST',
        // Code en CHAINE : un code commencant par zero serait tronque en nombre.
        json: { userId: r1.userId, workspaceId: r1.workspaceId, code: totp(cle) },
    });
    return r2.accessToken;
}

// ─── Le dossier fictif que le parcours constitue ────────────────────────
const GERANT_ID = randomUUID();
const ASSOCIE_1 = randomUUID();
const ASSOCIE_2 = randomUUID();

function donnees(dossierId) {
    return {
        dossier: {
            dossierId,
            denomination: 'MAROC TELECOM SERVICES',
            formeJuridique: 'SARL',
            statut: 'EN_CONSTITUTION',
        },
        step1: {
            formeJuridique: 'SARL',
            denomination: {
                denomination: 'MAROC TELECOM SERVICES',
                formeJuridique: 'SARL',
                ice: '003456789000063',
                icenumero: '003456789000063',
                cnNumero: 'CN-2026-04871',
                cnDate: '2026-01-19',
                activiteCn: 'Services de telecommunications et maintenance de reseaux',
                beneficiaire: 'RACHID EL AMRANI',
            },
        },
        step2: {
            siege: {
                adresse: '12, avenue Mohammed V, 3e etage',
                commune: 'Agdal',
                province: 'Rabat',
                codePostal: '10000',
                tribunal: 'Tribunal de Commerce - Rabat',
                justificatifType: 'BAIL',
                dateDebut: '2026-01-05',
                dateFin: '2029-01-04',
                mainDocName: 'Contrat_bail_Agdal.pdf',
                attestationDocName: null,
            },
        },
        step3: {
            capital: {
                capitalSocialMad: 250000.0,
                capitalLibere: 250000,
                pourcentageLibere: 100.0,
                nombreParts: 2500,
                valeurNominale: 100,
                valeurNominaleMad: 100.0,
                hasNumeraire: true,
                apportNumeraire: 250000,
                hasNature: false,
                apportNature: 0,
                descriptionNature: '',
                hasIndustrie: false,
                apportIndustrie: 0,
                descriptionIndustrie: '',
                dureeAnnees: 99,
                dateCommencement: '2026-02-02',
                dateFin: '2125-02-02',
            },
        },
        step4: {
            activite: {
                secteur: 'Services',
                categorieOna: 'Information et communication',
                description: 'Installation, exploitation et maintenance de reseaux de telecommunications',
                activiteReglementee: false,
                dateDebutExercice: '2026-02-02',
            },
        },
        step5: {
            dirigeants: [
                {
                    id: GERANT_ID,
                    civilite: 'M',
                    // Fiche d'identite VOLONTAIREMENT VIDE : c'est l'extraction OCR
                    // filmee a l'ecran 5 qui la pre-remplit, puis l'employe complete
                    // ce que le modele n'a pas lu. Pre-remplir ici donnerait a la
                    // camera un formulaire deja rempli AVANT l'extraction — le jury
                    // croirait que l'OCR a tout trouve.
                    nom: '',
                    prenom: '',
                    fonction: 'GERANT',
                    nationalite: 'Marocaine',
                    cinNumero: '',
                    dateNaissance: '',
                    adresse: '8, rue Oued Ziz, Agdal, Rabat',
                    isStatutaire: true,
                    isAssociate: true,
                    typePersonne: 'PHYSIQUE',
                    lieuNaissance: '',
                    // Mandat et remuneration : l'ecran 7 REFUSE de generer tant que
                    // ces deux valeurs ne sont pas choisies (politique « valeur reelle
                    // ou saisie forcee »). Rien n'est devine par defaut.
                    dureeMandatType: 'illimitee',
                    dureeAnnees: 0,
                    remunerationMode: 'non_remunere',
                    remunerationMontant: 0,
                    // CIN volontairement NON archivee : Step5 ne rend l'extracteur
                    // que si `!cinUploaded || !cinFileName`. La sequence OCR de la
                    // video a donc besoin que ce dirigeant arrive « CIN vide ».
                    cinUploaded: false,
                    cinFileName: '',
                    extracting: false,
                },
            ],
            // Le bag global `gerance` reste la source lue par le pre-vol de
            // l'ecran 7 (`data.step5.gerance.dureeMandat` / `.remunerationMode`),
            // alors que la saisie a migre par dirigeant le 2026-08-18. Sans lui,
            // la generation reste bloquee meme si chaque dirigeant est complet.
            gerance: {
                dureeMandat: 'illimitée',
                dureeGerance: 'illimitée',
                remunerationMode: 'non rémunéré',
                remunerationModeKey: 'non_remunere',
                remunerationMontant: null,
                gerantModeDesignation: 'statutaire',
                limitationPouvoirs: '',
                modeSignature: 'séparée',
                signaturePlafond: 0,
                signatureMandataire: false,
                mandataireNom: '',
                mandataireActeDelegation: '',
            },
        },
        step6: {
            formeJuridique: 'SARL',
            associes: [
                {
                    id: ASSOCIE_1,
                    civilite: 'M',
                    nom: 'EL AMRANI',
                    prenom: 'Rachid',
                    cin: 'A741852',
                    // Sans ces trois champs, l'acte genere sort « ne le a,
                    // demeurant a » — le gabarit interpole des valeurs vides.
                    nationalite: 'Marocaine',
                    dateNaissance: '1981-04-17',
                    lieuNaissance: 'Rabat',
                    adresse: '8, rue Oued Ziz, Agdal, Rabat',
                    typeApport: 'NUMERAIRE',
                    montantApport: 150000,
                    nombreParts: 1500,
                    pourcentageDetention: 60,
                    cinUploaded: true,
                    cinFileName: 'CIN_El_Amrani.png',
                    extracting: false,
                },
                {
                    id: ASSOCIE_2,
                    civilite: 'MME',
                    nom: 'BERRADA',
                    prenom: 'Nawal',
                    cin: 'AB238914',
                    nationalite: 'Marocaine',
                    dateNaissance: '1986-09-23',
                    lieuNaissance: 'Fes',
                    adresse: '31, avenue Hassan II, Hay Riad, Rabat',
                    typeApport: 'NUMERAIRE',
                    montantApport: 100000,
                    nombreParts: 1000,
                    pourcentageDetention: 40,
                    cinUploaded: true,
                    cinFileName: 'CIN_Berrada.png',
                    extracting: false,
                },
            ],
        },
    };
}

async function main() {
    console.log(`> connexion ${EMAIL} @ ${WORKSPACE}`);
    const token = await login();

    const tickets = await call('/api/v1/tickets?limit=200', { token });
    const liste = tickets.items ?? tickets;
    const ticket = liste.find(t => t.reference === REFERENCE);
    if (!ticket) throw new Error(`Ticket ${REFERENCE} introuvable`);
    console.log(`> ticket ${ticket.reference} — ${ticket.titre ?? ''} (${ticket.id})`);

    const dossiers = await call('/api/v1/dataroom/dossiers', { token });
    const dossier = dossiers.find(d => d.raisonSociale === 'Maroc Telecom Services SARL');
    if (!dossier) throw new Error('Dossier « Maroc Telecom Services SARL » introuvable');

    // 1. Demarrer (idempotent : startOrResume)
    await call(`/api/v1/workflows/${ticket.id}/start`, {
        method: 'POST', token, json: { type: 'CREATION' },
    }).catch(e => { if (!/409|deja/i.test(e.message)) throw e; });
    console.log('  ✓ workflow CREATION demarre (ou repris)');

    // 2. Remplir les six premiers ecrans, comme le fait le front a chaque « Suivant »
    const progress = await call(`/api/v1/workflows/${ticket.id}/save`, {
        method: 'POST', token,
        json: { currentStep: ETAPE, data: donnees(dossier.id) },
    });
    console.log(`  ✓ ecrans 1 a 6 remplis, parcours positionne sur l'ecran ${progress.currentStep}/${progress.totalSteps}`);
    console.log('\n✅ Workflow pret a filmer.');
}

main().catch(e => { console.error('\n❌', e.message); process.exit(1); });
