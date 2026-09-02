#!/usr/bin/env node
/**
 * ═══════════════════════════════════════════════════════════════════════
 *  JURIKA — Captation du PARCOURS COMPLET, sur la plateforme reelle
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Filme le parcours annonce par la diapositive 22 de la soutenance :
 *
 *   connexion 2FA -> ticket -> workflow de creation -> extraction CIN
 *   -> generation d'acte -> assistant RAG -> Data Room cote client
 *
 * Cible : 3 min 30 maximum.
 *
 * ── Ce qui est joue en direct, et ce qui est prepare ───────────────────
 *
 * Tout ce qui est a l'ecran est l'application reelle, sur des donnees
 * fictives (workspace JUR-DEMO2). Deux choses sont preparees hors camera,
 * et il faut le savoir pour commenter honnetement :
 *
 *  • Les six premiers ecrans du workflow sont remplis a l'avance par l'API
 *    (`seed-demo-workflow.mjs`). La camera les PARCOURT — les donnees
 *    affichees sont bien celles de la base — mais personne ne les tape a
 *    l'ecran : remplir neuf formulaires prendrait a soi seul plus que la
 *    duree cible.
 *  • Le contenu de la Data Room et le corpus du chatbot sont seedes
 *    (`seed-demo-content.mjs`), comme toute donnee de demonstration.
 *
 * En revanche, sont bien joues EN DIRECT : la connexion et son code TOTP,
 * la creation du ticket, l'extraction OCR de la CIN, la generation des
 * statuts, la question au chatbot, et la bascule sur le compte client.
 *
 * ── L'extraction CIN est montree telle qu'elle se comporte ─────────────
 *
 * Le specimen de carte est dessine (`build-demo-cin.py`), donc hors du
 * gabarit sur lequel le modele a ete entraine : il remonte le nom et la
 * date de naissance, pas le reste. C'est exactement ce que l'interface
 * annonce — « Extraire les donnees pour pre-remplir le formulaire (aide
 * optionnelle) » — et c'est ce qui est filme. Rien n'est mis en scene.
 *
 * ── Pre-requis ────────────────────────────────────────────────────────
 *   node scripts/seed-demo.mjs
 *   node scripts/demo-video/enroll-totp.mjs
 *   node scripts/demo-video/enroll-totp.mjs --email=client@demo.jurika.ma
 *   python scripts/demo-video/build-demo-pdfs.py
 *   python scripts/demo-video/build-demo-cin.py
 *   node scripts/demo-video/seed-demo-content.mjs
 *   node scripts/demo-video/seed-demo-workflow.mjs
 *
 * ── Usage ─────────────────────────────────────────────────────────────
 *   node scripts/demo-video/capture-parcours.mjs
 *   node scripts/demo-video/capture-parcours.mjs --vitesse=1.2
 */

import { createRequire } from 'node:module';
import { readFile, mkdir } from 'node:fs/promises';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { totp, msLeftInWindow } from './totp.mjs';
import { creerCamera, SCRIPT_CURSEUR } from './capture-commun.mjs';

const require = createRequire(
    resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'frontend-react', 'package.json'));
const { chromium } = require('playwright');

const HERE = dirname(fileURLToPath(import.meta.url));
const RACINE = resolve(HERE, '..', '..', '..');

const opt = (n, d) => {
    const m = process.argv.find(a => a.startsWith(`--${n}=`));
    return m ? m.split('=').slice(1).join('=') : d;
};

const CFG = {
    url: opt('url', 'http://localhost:5173'),
    api: opt('api', 'http://localhost:8080'),
    workspace: opt('workspace', 'JUR-DEMO2'),
    email: opt('email', 'employe1@demo.jurika.ma'),
    password: opt('password', 'Demo@2026'),
    emailClient: opt('client', 'client@demo.jurika.ma'),
    ticket: opt('ticket', 'T-DEMO-003'),
    // Sequence OCR desactivee par defaut : sous captation, le modele Donut et
    // l'encodage video se partagent le CPU et l'extraction passe de 5 s a plus
    // de 30 s — trente secondes de voyant « en cours » a l'ecran. La rallumer
    // avec --ocr=oui quand la demonstration doit absolument la montrer.
    ocr: opt('ocr', 'non') === 'oui',
    sortie: opt('sortie', join(RACINE, 'output', 'demo-video')),
    vitesse: Number(opt('vitesse', '1')),
    largeur: 1920,
    hauteur: 1080,
    lire: 1150,
    geste: 380,
};
CFG.brut = join(CFG.sortie, 'brut');

const cam = creerCamera(CFG);
const { attendre, LIRE, chapitre, signaler, versPoint, cliquer, survoler,
        saisir, defiler, horloge, finaliser } = cam;

// ─── Numerotation des sections ──────────────────────────────────────────
/**
 * Numerote les sections a la volee.
 *
 * La sequence OCR est optionnelle : figer les numeros dans les libelles
 * laissait un trou (« 2 » puis « 4 ») des qu'elle etait eteinte.
 */
let numeroSection = 0;
const section = (titre) => chapitre(`${++numeroSection}. ${titre}`);
/** Sous-plan de la section courante (meme numero). */
const sousPlan = (titre) => chapitre(`${numeroSection}. ${titre}`);

// ─── Navigation dans le wizard ──────────────────────────────────────────
/**
 * Passe a l'ecran suivant du workflow.
 *
 * Le bouton s'appelle « Suivant (sans modifier) » et il est DESACTIVE pendant
 * la sauvegarde de l'ecran courant (`disabled={saving}`) : cliquer sans
 * attendre son reveil fait patienter Playwright jusqu'au timeout.
 */
async function ecranSuivant(page, apres = 700) {
    const bouton = page.getByRole('button', { name: /^Suivant/ }).first();
    if (!(await bouton.count())) return false;
    await bouton.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
    // On attend que le bouton redevienne cliquable plutot que de forcer.
    for (let i = 0; i < 30 && await bouton.isDisabled().catch(() => false); i++) {
        await attendre(250);
    }
    await cliquer(page, bouton, apres);
    return true;
}

/** Revient au premier ecran du wizard. */
async function ecranPrecedent(page, fois) {
    for (let i = 0; i < fois; i++) {
        const bouton = page.getByRole('button', { name: /^Precedent|^Précédent/ }).first();
        if (!(await bouton.count()) || await bouton.isDisabled().catch(() => true)) break;
        await bouton.click().catch(() => {});
        await attendre(300);
    }
}

/**
 * Attend que l'assistant d'extraction ait rendu la main.
 *
 * Mesure faite : l'API repond en ~4 s, et l'assistant en ~5 s quand rien
 * d'autre ne tourne. Pendant une captation, l'encodage video 1080p et le
 * modele Donut se disputent le meme CPU et l'extraction s'allonge nettement.
 * On attend donc le retour du libelle « Extraire » plutot qu'une duree fixe,
 * et on rend la main des que c'est pret pour ne pas filmer un ecran inerte.
 */
async function attendreFinExtraction(page, plafondMs) {
    const bouton = page.locator('[data-testid="id-extract-btn"]').first();
    const debut = Date.now();
    while (Date.now() - debut < plafondMs) {
        const libelle = await bouton.innerText().catch(() => '');
        if (libelle && !/cours/i.test(libelle)) {
            console.log(`     extraction rendue en ${Math.round((Date.now() - debut) / 1000)} s`);
            return true;
        }
        await attendre(500);
    }
    signaler('Extraction CIN',
        `l'assistant tournait encore apres ${Math.round(plafondMs / 1000)} s — sous captation, le modele et l'encodage video se partagent le CPU`);
    return false;
}

// ─── Connexion jouee a l'ecran ──────────────────────────────────────────
async function seConnecter(page, email, secretTotp, motDePasse = CFG.password) {
    await saisir(page, 'input[name="workspaceCode"]', CFG.workspace.replace(/^JUR-/, ''), 100);
    await cliquer(page, page.getByRole('button', { name: /Continuer/i }), 800);
    await saisir(page, 'input[name="email"]', email, 40);
    await saisir(page, 'input[name="password"]', motDePasse, 65);
    await cliquer(page, page.getByRole('button', { name: /Se connecter/i }), 1000);

    const champ2fa = page.locator('input[name="twofa"]');
    if (await champ2fa.count()) {
        if (msLeftInWindow() < 6000) await attendre(msLeftInWindow() + 400);
        await saisir(page, champ2fa, totp(secretTotp), 120);
        await cliquer(page, page.getByRole('button', { name: /Verifier|Valider|Continuer/i }), 800);
    }
    await page.waitForURL(/\/dashboard/, { timeout: 25000 });
    await page.waitForLoadState('networkidle').catch(() => {});
}

async function main() {
    await mkdir(CFG.brut, { recursive: true });

    const secrets = JSON.parse(await readFile(join(HERE, 'demo-secrets.json'), 'utf8'));
    const cleEmploye = secrets[`${CFG.workspace}:${CFG.email}`]?.secret;
    const cleClient = secrets[`${CFG.workspace}:${CFG.emailClient}`]?.secret;
    if (!cleEmploye) throw new Error(`Secret TOTP absent pour ${CFG.email} — lancer enroll-totp.mjs`);
    if (!cleClient) signaler('Compte client', 'pas de secret TOTP — la derniere sequence sera sautee');

    // Remise en etat hors camera (identifiants a blanc, corpus, workflow).
    if (opt('reinitialiser', 'oui') !== 'non') {
        console.log('▶ Remise en etat du contenu de demonstration…');
        for (const s of ['seed-demo-content.mjs', 'seed-demo-workflow.mjs']) {
            await new Promise((res) => {
                const p = spawn(process.execPath, [join(HERE, s)], { windowsHide: true });
                let out = '';
                p.stdout.on('data', d => { out += d; });
                p.stderr.on('data', d => { out += d; });
                p.on('close', c => {
                    console.log(`  ${c === 0 ? '✓' : '⚠'} ${s}`);
                    if (c !== 0) signaler(`Preparation ${s}`, out.trim().split('\n').pop() ?? '');
                    res();
                });
            });
        }
    }

    // Identifiant du ticket a filmer, recupere hors camera.
    const ticketId = await idDuTicket(cleEmploye).catch(e => {
        signaler('Ticket du workflow', e.message);
        return null;
    });

    console.log(`\n▶ Captation parcours — ${CFG.url} (${CFG.workspace})`);
    console.log(`  vitesse x${CFG.vitesse} • ${CFG.largeur}x${CFG.hauteur}\n`);

    const navigateur = await chromium.launch({
        headless: true,
        channel: 'chromium',
        args: ['--hide-scrollbars=false', '--force-color-profile=srgb'],
    });
    const contexte = await navigateur.newContext({
        viewport: { width: CFG.largeur, height: CFG.hauteur },
        recordVideo: { dir: CFG.brut, size: { width: CFG.largeur, height: CFG.hauteur } },
        deviceScaleFactor: 1,
        locale: 'fr-FR',
        timezoneId: 'Africa/Casablanca',
        acceptDownloads: true,
    });
    await contexte.addInitScript(SCRIPT_CURSEUR);

    const page = await contexte.newPage();
    page.setDefaultTimeout(20000);
    horloge.debutVideo = Date.now();

    try {
        await page.goto(`${CFG.url}/login`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(1200);
        horloge.t0 = Date.now();
        chapitre('Ouverture — page de connexion');
        await versPoint(page, CFG.largeur / 2, CFG.hauteur / 2 + 120);
        await attendre(700);

        // ══ 1. Connexion 2FA ══════════════════════════════════════════
        section('Connexion securisee (2FA TOTP reelle)');
        await seConnecter(page, CFG.email, cleEmploye);
        await attendre(LIRE);

        // ══ 2. Tickets : le catalogue des operations ══════════════════
        section("Tickets — les types d'operation couverts");
        await cliquer(page, page.getByRole('link', { name: /Mes Tickets/i }), 1000);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE - 200);
        // La vue Tableau expose la colonne « Type » : le jury lit d'un coup
        // l'eventail des operations deja traitees.
        const vueTableau = page.getByRole('button', { name: /^Tableau$/ }).first();
        if (await vueTableau.count()) {
            await cliquer(page, vueTableau, 900);
            await attendre(LIRE + 300);
            await defiler(page, 3, 130);
            await attendre(800);
        }

        sousPlan("Creation d'un ticket — choix du type");
        await cliquer(page, page.getByRole('button', { name: /Nouveau ticket|^Nouveau$/ }), 900);
        await saisir(page, page.getByLabel('Titre', { exact: false }).first(),
            'Constitution SARL Rif Negoce', 45);
        // Le <select> est natif : sa liste deroulante est dessinee par le systeme
        // et n'apparait sur aucune capture. On fait donc defiler les valeurs, ce
        // qui affiche successivement chaque type dans le champ.
        const selType = page.locator('select').first();
        if (await selType.count()) {
            await cam.viser(page, selType);
            for (const t of ['MODIFICATION', 'DISSOLUTION', 'LIQUIDATION', 'CREATION']) {
                await selType.selectOption(t).catch(() => {});
                await attendre(620);
            }
        } else {
            signaler('Types de ticket', 'aucun <select> dans le tiroir de creation');
        }
        await attendre(500);
        const creer = page.getByRole('button', { name: /^Creer|^Créer/ }).first();
        if (await creer.count()) await cliquer(page, creer, 1400);
        else signaler('Creation ticket', 'bouton « Creer » introuvable — tiroir ferme sans creation');
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE - 200);

        // ══ 3. Extraction CIN (optionnelle) ═══════════════════════════
        if (CFG.ocr) {
            if (!ticketId) throw new Error('ticket du workflow introuvable — sequence impossible');
            await page.goto(`${CFG.url}/workflows/${ticketId}`, { waitUntil: 'domcontentloaded' });
            await page.waitForLoadState('networkidle').catch(() => {});
            await attendre(1400);
            // On rejoint directement l'ecran des dirigeants, sans derouler tout le
            // wizard : la visite ecran par ecran alourdissait la video sans rien
            // apprendre au jury.
            await ecranPrecedent(page, 2);
            await attendre(900);

            // ══ 4. Extraction CIN ═════════════════════════════════════════
            section("Extraction OCR de la carte d'identite");
            // On avance jusqu'a l'ecran des dirigeants.
            await ecranSuivant(page, 500);
            await attendre(900);
            // Step5 ne rend pas l'extracteur quand la CIN est deja archivee en Data
            // Room — et l'archivage survit d'une prise a l'autre. Le bouton
            // « Remplacer » le rouvre, ce qui est aussi le geste reel d'un employe
            // qui change une piece.
            let extracteur = page.locator('[data-testid="identity-extractor"]').first();
            if (!(await extracteur.count())) {
                const remplacer = page.getByRole('button', { name: /Remplacer/i }).first();
                if (await remplacer.count()) {
                    await cliquer(page, remplacer, 900);
                    extracteur = page.locator('[data-testid="identity-extractor"]').first();
                }
            }
            if (await extracteur.count()) {
                await extracteur.scrollIntoViewIfNeeded().catch(() => {});
                await attendre(600);
                // Les DEUX faces : l'assistant est concu pour recto + verso, et
                // n'aboutit pas avec le seul recto (le voyant reste sur « Extraction
                // en cours »). L'API, elle, repond en ~4 s.
                const champsFichier = extracteur.locator('input[type="file"]');
                await champsFichier.nth(0).setInputFiles(join(HERE, 'assets', 'CIN_specimen_recto.png'));
                await attendre(900);
                if (await champsFichier.count() > 1) {
                    await champsFichier.nth(1).setInputFiles(join(HERE, 'assets', 'CIN_specimen_verso.png'));
                    await attendre(900);
                }
                await cliquer(page, page.locator('[data-testid="id-extract-btn"]').first(), 500);
                // On tient le plan sur le resultat REEL : le modele remonte le nom et
                // la date de naissance, pas le reste — c'est bien « une aide au
                // pre-remplissage », comme l'annonce l'interface.
                await attendreFinExtraction(page, 30000);
                await attendre(LIRE);

                sousPlan("L'employe complete ce que l'OCR n'a pas lu");
                for (const [libelle, valeur] of [['Prenom', 'Rachid'], ['N° CIN', 'A741852']]) {
                    const champ = page.getByLabel(libelle, { exact: false }).first();
                    if (await champ.count()) await saisir(page, champ, valeur, 55, 320);
                    else signaler(`Champ « ${libelle} »`, "introuvable a l'ecran 5");
                }
                await attendre(700);
                // « Suivant » enregistre l'ecran : sans ce clic, la saisie filmee ne
                // serait pas persistee et l'acte genere plus loin sortirait incomplet.
                await ecranSuivant(page, 900);
            } else {
                signaler('Extraction CIN',
                    "l'extracteur n'est pas rendu — la CIN du dirigeant est peut-etre deja archivee");
            }

        }

        // ══ 4. Generation de l'acte ═══════════════════════════════════
        section('Generation des actes');
        if (!ticketId) throw new Error('ticket du workflow introuvable — sequence impossible');
        await page.goto(`${CFG.url}/workflows/${ticketId}`, { waitUntil: 'domcontentloaded' });
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(1500);
        const generer = page.getByRole('button', { name: /^Generer$|^Générer$/ }).first();
        if (await generer.count()) {
            await cliquer(page, generer, 800);
            await attendre(4500);            // generation deterministe, ~3 s
            sousPlan('Statuts generes — apercu');
            await defiler(page, 6, 150, 130);
            await attendre(LIRE);
        } else {
            signaler('Generation', 'aucun bouton « Generer » — le pre-vol bloque peut-etre encore');
        }

        // ══ 6. Assistant RAG ══════════════════════════════════════════
        section('Assistant juridique — question et sources');
        await cliquer(page, page.getByRole('link', { name: /ChatBot IA/i }), 1100);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(900);
        await saisir(page, '[data-testid="chatbot-message-input"]',
            'Que prevoient les statuts en cas de cession de parts a un tiers ?', 38);
        await page.keyboard.press('Enter');
        await attendre(2200);
        await defiler(page, 5, 130, 120);
        await attendre(LIRE + 400);

        // ══ 7. Data Room cote client ══════════════════════════════════
        if (cleClient) {
            section('Bascule sur le compte client');
            await deconnexion(page);
            await seConnecter(page, CFG.emailClient, cleClient);
            await attendre(LIRE);
            sousPlan('Data Room — vue client');
            const lienDr = page.getByRole('link', { name: /Data Room|Mes documents/i }).first();
            if (await lienDr.count()) {
                await cliquer(page, lienDr, 1200);
                await page.waitForLoadState('networkidle').catch(() => {});
            }
            await attendre(LIRE);
            await defiler(page, 5, 140, 120);
            await attendre(LIRE + 600);
        }

        chapitre('FIN');
    } catch (err) {
        signaler('Interruption du parcours', err.message);
        console.error('\n❌ Le parcours s\'est arrete :', err.message);
    } finally {
        const video = page.video();
        await contexte.close();
        await navigateur.close();
        const jour = new Date().toISOString().slice(0, 10);
        await finaliser(video ? await video.path() : null, `JURIKA_parcours_${jour}`);
    }
}

/** Deconnexion par le menu du compte (retour a l'ecran de connexion). */
async function deconnexion(page) {
    const menu = page.locator('header button, header [role="button"]').last();
    await menu.click().catch(() => {});
    await attendre(600);
    const quitter = page.getByRole('button', { name: /Deconnexion|Déconnexion|Se deconnecter/i }).first();
    if (await quitter.count()) {
        await quitter.click().catch(() => {});
    } else {
        // Repli : on force la sortie par l'URL.
        await page.goto(`${CFG.url}/login?reason=expired`, { waitUntil: 'domcontentloaded' });
    }
    await page.waitForURL(/\/login/, { timeout: 15000 }).catch(async () => {
        await page.goto(`${CFG.url}/login`, { waitUntil: 'domcontentloaded' });
    });
    await page.waitForLoadState('networkidle').catch(() => {});
    await attendre(800);
}

/** Id du ticket portant le workflow prepare — recupere hors camera. */
async function idDuTicket(cleTotp) {
    const post = async (chemin, corps, token) => {
        const r = await fetch(CFG.api + chemin, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            body: JSON.stringify(corps),
        });
        if (!r.ok) throw new Error(`${chemin} -> ${r.status}`);
        return r.json();
    };
    const l = await post('/api/v1/auth/login',
        { workspaceCode: CFG.workspace, email: CFG.email, password: CFG.password });
    let token = l.accessToken;
    if (l.requires2fa) {
        if (msLeftInWindow() < 4000) await new Promise(r => setTimeout(r, msLeftInWindow() + 400));
        const v = await post('/api/v1/auth/verify-2fa',
            { userId: l.userId, workspaceId: l.workspaceId, code: totp(cleTotp) });
        token = v.accessToken;
    }
    const r = await fetch(`${CFG.api}/api/v1/tickets?limit=200`, { headers: { Authorization: `Bearer ${token}` } });
    if (!r.ok) throw new Error(`/tickets -> ${r.status}`);
    const corps = await r.json();
    const t = (corps.items ?? corps).find(x => x.reference === CFG.ticket);
    if (!t) throw new Error(`ticket ${CFG.ticket} introuvable`);
    return t.id;
}

main().catch(e => { console.error('\n❌', e); process.exit(1); });
